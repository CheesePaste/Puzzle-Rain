package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * 跟随实体类
 * 跟随目标实体移动的方块实体
 */
public class FollowingEntity extends BaseBlockEntity implements Targetable {

    // 配置参数
    private static final float CLOSE_DISTANCE = 2.0f;
    private static final float HORIZONTAL_SPEED = 0.15f;
    private static final float JUMP_STRENGTH = 0.5f;
    private static final int JUMP_COOLDOWN = 20;
    private static final float MAX_SPEED = 0.5f;
    private static final float AIR_RESISTANCE = 0.98f;
    private static final float ROTATION_SPEED = 10.0f; // 旋转速度（度/秒）
    private static final float ROTATION_INTERPOLATION_FACTOR = 0.2f; // 旋转插值因子

    // 操纵模式参数
    private static final float CONTROL_DISTANCE = 5.0f; // 操纵时的默认距离
    private static final float CONTROL_SPEED = 0.3f; // 操纵移动速度
    private static final float CONTROL_SMOOTHING = 0.2f; // 操纵平滑因子
    private static final float LAUNCH_SPEED = 20.0f; // 发射速度

    // 数据跟踪
    private static final TrackedData<Optional<UUID>> TARGET_UUID =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Boolean> IS_CONTROLLED =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> CONTROLLING_PLAYER_UUID =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    // 状态字段
    @Nullable
    private Entity target;
    private int jumpTimer = 0;
    private boolean shouldJump = false;
    private int targetLostCounter = 0;
    private static final int MAX_TARGET_LOST_TICKS = 200; // 10秒后放弃跟随

    // 操纵模式状态
    @Nullable
    private UUID controllingPlayerId;



    // 控制状态
    private Vec3d targetControlPos; // 控制目标位置
    private boolean justLaunched = false; // 刚刚被发射
    private int launchCooldown = 0; // 发射冷却

    // ================= 构造方法 =================

    void Colli(){
        enableCollision = true;
        attackable = false;
        fireImmune = true;
        aliveCheckFromRemoved = true;
        pushOutOfBlocksEnabled = true;
        onBlockCollisionEnabled = true;
        collidesWithOtherEntities = true;
        moveVoluntarily = true;
        collidesWithBlockStates = true;
        checkBlockCollisionEnabled = true;
        adjustForPistonEnabled = true;
        adjustForSneakingEnabled = true;
        hittable = true;
        pushAwayEnabled = true;
        noGravity = false;
        collidable = true;
        pushable = true;
    }

    public FollowingEntity(EntityType<? extends BaseBlockEntity> type, World world, @Nullable Entity target,
                           @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, world, pos, state);
        this.target = target;
        Colli();
        this.MAX_TRAIL_LENGTH = 200;
        this.targetControlPos = this.getPos();

    }

    public static DefaultAttributeContainer.Builder createFollowingAttributes() {
        return BaseBlockEntity.createBaseBlockAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0) // 更高的生命值
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25) // 移动速度
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0) // 完全抵抗击退
                .add(EntityAttributes.GENERIC_GRAVITY, 0.08) // 重力
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0) // 可以走上1格高的方块
                .add(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0) // 安全坠落距离
                .add(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 0.0); // 无坠落伤害
    }

    public FollowingEntity(EntityType<FollowingEntity> type, World world) {
        super(type, world);
        this.setNoGravity(false);
        Colli();
        this.MAX_TRAIL_LENGTH = 200;
        this.targetControlPos = this.getPos();

    }

    // ================= 数据跟踪 =================

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TARGET_UUID, Optional.empty());

        builder.add(IS_CONTROLLED, false);
        builder.add(CONTROLLING_PLAYER_UUID, Optional.empty());
    }

    // ================= 目标管理 =================

    public void setTarget(@Nullable Entity target) {
        Entity oldTarget = this.target;
        this.target = target;

        if (target != null) {
            this.dataTracker.set(TARGET_UUID, Optional.of(target.getUuid()));
            targetLostCounter = 0;
        } else {
            this.dataTracker.set(TARGET_UUID, Optional.empty());
        }
    }

    @Nullable
    public Entity getTarget() {
        return target;
    }

    private void refreshTarget() {
        Optional<UUID> uuid = this.dataTracker.get(TARGET_UUID);

        if (uuid.isPresent()) {
            Entity entity = this.getWorld().getPlayerByUuid(uuid.get());
            if (entity == null) {
                // 如果不是玩家，尝试查找其他实体
                for (Entity e : this.getWorld().getPlayers()) {
                    if (e.getUuid().equals(uuid.get())) {
                        entity = e;
                        break;
                    }
                }
            }

            if (entity != null && entity.isAlive()) {
                this.target = entity;
                targetLostCounter = 0;
            } else {
                targetLostCounter++;
                if (targetLostCounter > MAX_TARGET_LOST_TICKS) {
                    this.target = null;
                    this.dataTracker.set(TARGET_UUID, Optional.empty());
                }
            }
        }
    }

    // ================= 操纵模式管理 =================

    public void setControllingPlayer(@Nullable PlayerEntity player) {
        if (player != null) {
            this.controllingPlayerId = player.getUuid();
            this.dataTracker.set(CONTROLLING_PLAYER_UUID, Optional.of(player.getUuid()));
            this.dataTracker.set(IS_CONTROLLED, true);

            // 启用发光效果
            setGlowing(true);

            // 在操纵模式下，取消跟随目标
            this.setTarget(null);

            // 设置无重力，便于操纵
            this.setNoGravity(true);

            // 初始位置：玩家面前一定距离
            Vec3d lookDirection = player.getRotationVec(1.0F);
            Vec3d eyePos = player.getEyePos();
            this.targetControlPos = eyePos.add(lookDirection.multiply(CONTROL_DISTANCE));

            // 平滑移动到目标位置
            Vec3d toTarget = targetControlPos.subtract(this.getPos());
            double distance = toTarget.length();
            if (distance > 0.1) {
                Vec3d velocity = toTarget.normalize().multiply(CONTROL_SPEED);
                this.setVelocity(velocity);
            } else {
                this.setVelocity(Vec3d.ZERO);
            }

            // 清除发射状态
            justLaunched = false;
            launchCooldown = 0;

        } else {
            this.controllingPlayerId = null;
            this.dataTracker.set(CONTROLLING_PLAYER_UUID, Optional.empty());
            this.dataTracker.set(IS_CONTROLLED, false);

            // 取消发光效果
            setGlowing(false);

            // 恢复重力
            this.setNoGravity(false);

            // 重置控制目标位置
            this.targetControlPos = this.getPos();
        }
    }

    public boolean isControlledBy(PlayerEntity player) {
        if (player == null || this.controllingPlayerId == null) {
            return false;
        }
        return player.getUuid().equals(this.controllingPlayerId);
    }

    public boolean isControlled() {
        return this.dataTracker.get(IS_CONTROLLED);
    }

    @Nullable
    public PlayerEntity getControllingPlayer() {
        if (this.controllingPlayerId == null) {
            return null;
        }
        return this.getWorld().getPlayerByUuid(this.controllingPlayerId);
    }

    // ================= 发射功能 =================

    public void launchEntity(Vec3d direction) {
        // 计算发射速度
        Vec3d launchVelocity = direction.multiply(LAUNCH_SPEED);

        // 应用速度
        this.setVelocity(launchVelocity);

        // 标记为刚刚发射
        justLaunched = true;
        launchCooldown = 10; // 10 tick的冷却，防止立即被重新控制

        // 取消控制
        setControllingPlayer(null);

        // 恢复重力
        this.setNoGravity(false);

        // 播放发射音效（可选）
        // this.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
    }





    // ================= NBT序列化 =================

    @Override
    public void writeCustomDataToNbt(@NotNull NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (this.target != null) {
            nbt.putUuid("Target", this.target.getUuid());
        }

        // 保存操纵状态
        if (this.controllingPlayerId != null) {
            nbt.putUuid("ControllingPlayer", this.controllingPlayerId);
        }
        nbt.putBoolean("IsControlled", this.isControlled());


        // 保存控制目标位置
        if (this.targetControlPos != null) {
            nbt.putDouble("ControlTargetX", this.targetControlPos.x);
            nbt.putDouble("ControlTargetY", this.targetControlPos.y);
            nbt.putDouble("ControlTargetZ", this.targetControlPos.z);
        }

        nbt.putBoolean("JustLaunched", justLaunched);
        nbt.putInt("LaunchCooldown", launchCooldown);

        nbt.putInt("JumpTimer", this.jumpTimer);
        nbt.putBoolean("ShouldJump", this.shouldJump);
        nbt.putInt("TargetLostCounter", targetLostCounter);

    }

    @Override
    public void readCustomDataFromNbt(@NotNull NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("Target", NbtElement.INT_ARRAY_TYPE)) {
            UUID targetUuid = nbt.getUuid("Target");
            this.dataTracker.set(TARGET_UUID, Optional.of(targetUuid));
        }

        // 加载操纵状态
        if (nbt.contains("ControllingPlayer", NbtElement.INT_ARRAY_TYPE)) {
            UUID controllingPlayerUuid = nbt.getUuid("ControllingPlayer");
            this.controllingPlayerId = controllingPlayerUuid;
            this.dataTracker.set(CONTROLLING_PLAYER_UUID, Optional.of(controllingPlayerUuid));
        }

        if (nbt.contains("IsControlled", NbtElement.BYTE_TYPE)) {
            boolean isControlled = nbt.getBoolean("IsControlled");
            this.dataTracker.set(IS_CONTROLLED, isControlled);
        }



        // 加载控制目标位置
        if (nbt.contains("ControlTargetX", NbtElement.DOUBLE_TYPE)) {
            double x = nbt.getDouble("ControlTargetX");
            double y = nbt.getDouble("ControlTargetY");
            double z = nbt.getDouble("ControlTargetZ");
            this.targetControlPos = new Vec3d(x, y, z);
        }

        if (nbt.contains("JustLaunched", NbtElement.BYTE_TYPE)) {
            justLaunched = nbt.getBoolean("JustLaunched");
        }

        if (nbt.contains("LaunchCooldown", NbtElement.INT_TYPE)) {
            launchCooldown = nbt.getInt("LaunchCooldown");
        }

        if (nbt.contains("JumpTimer", NbtElement.INT_TYPE)) {
            this.jumpTimer = nbt.getInt("JumpTimer");
        }

        if (nbt.contains("ShouldJump", NbtElement.BYTE_TYPE)) {
            this.shouldJump = nbt.getBoolean("ShouldJump");
        }

        if (nbt.contains("TargetLostCounter", NbtElement.INT_TYPE)) {
            this.targetLostCounter = nbt.getInt("TargetLostCounter");
        }

        //.info("FollowingEntity data loaded. JumpTimer: {}, ShouldJump: {}", jumpTimer, shouldJump);
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return Collections.singleton(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
        // 不穿戴装备
    }

    // ================= 旋转系统 =================

    /**
     * 计算看向目标所需的yaw
     */
    private void calculateTargetRotation() {
        if (this.target == null) {
            return;
        }

        // 计算看向目标的方向向量
        Vec3d toTarget = target.getPos().subtract(this.getPos());

        // 只计算水平旋转角度 (Yaw)
        double dx = toTarget.x;
        double dz = toTarget.z;

        // 计算yaw（水平旋转角度）
        float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        // 确保角度在有效范围内
        targetYaw = MathHelper.wrapDegrees(targetYaw);


    }

    /**
     * 基于Vec3d目标位置的版本
     * @param targetPos 目标位置
     * @param smoothFactor 平滑系数
     */
    private void smoothLookAtPosition(Vec3d targetPos, float smoothFactor) {
        // 计算方向向量
        double dx = targetPos.x - this.getX();
        double dz = targetPos.z - this.getZ();

        // 计算目标yaw角度
        double targetYaw = MathHelper.atan2(dz, dx) * (180.0 / Math.PI) - 90.0;

        float currentYaw = this.getYaw();
        currentYaw = MathHelper.wrapDegrees(currentYaw);
        targetYaw = MathHelper.wrapDegrees((float)targetYaw);

        // 角度插值
        float angleDiff = MathHelper.wrapDegrees((float)targetYaw - currentYaw);
        float newYaw = currentYaw + angleDiff * MathHelper.clamp(smoothFactor, 0, 1);

        this.setYaw(newYaw);
        this.setHeadYaw(newYaw);
    }

    /**
     * 简洁版本 - 直接更新yaw
     */
    private void lookAtTarget(Entity target) {
        // 计算看向目标所需的yaw角度
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();

        // 计算目标yaw角度
        double targetYaw = MathHelper.atan2(dz, dx) * (180.0 / Math.PI) - 90.0;

        // 直接设置（不插值）
        this.setYaw((float)targetYaw);
        this.setHeadYaw((float)targetYaw);
        this.setBodyYaw((float) targetYaw);
        PuzzleRain.LOGGER.info(String.valueOf(targetYaw));
    }

    @Override
    public void tick() {
        // 先调用父类的tick，这会更新位置、速度、重力等
        super.tick();

        // 更新发射冷却
        if (launchCooldown > 0) {
            launchCooldown--;
        }



        // 检查控制状态
        refreshControlState();

        // 如果被控制，执行控制逻辑
        if (isControlled()) {
            tickControlled();
            return;
        }

        // 确保目标是最新的
        if (this.target == null || !this.target.isAlive()) {
            refreshTarget();
        }


        // 如果没有目标，停止移动
        if (this.target == null) {
            // 只停止水平移动，不影响重力
            Vec3d currentVel = this.getVelocity();
            this.setVelocity(new Vec3d(0, currentVel.y, 0));
            applyMovement();
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }
        //smoothLookAtPosition(this.target.getPos(),0.5f);
        lookAtTarget(target);


        // 如果距离足够近，停止水平移动
        if (isClose()) {
            Vec3d currentVel = this.getVelocity();
            this.setVelocity(new Vec3d(0, currentVel.y, 0));
            applyMovement();
            this.move(MovementType.SELF, this.getVelocity());
            return;
        }
        processMovement();

    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    // ================= 控制逻辑 =================

    /**
     * 刷新控制状态
     */
    private void refreshControlState() {
        if (isControlled()) {
            Optional<UUID> uuid = this.dataTracker.get(CONTROLLING_PLAYER_UUID);
            if (uuid.isPresent()) {
                PlayerEntity player = this.getWorld().getPlayerByUuid(uuid.get());
                if (player != null && player.isAlive()) {
                    this.controllingPlayerId = player.getUuid();
                } else {
                    // 玩家不存在或已死亡，取消控制
                    setControllingPlayer(null);
                }
            } else {
                setControllingPlayer(null);
            }
        }
    }

    /**
     * 被控制时的逻辑
     */
    private void tickControlled() {
        PlayerEntity controller = getControllingPlayer();
        if (controller == null) {
            setControllingPlayer(null);
            return;
        }

        // 如果刚刚发射，跳过控制逻辑
        if (justLaunched && launchCooldown > 0) {
            return;
        }

        // 获取玩家准心指向的位置
        Vec3d lookDirection = controller.getRotationVec(1.0F);
        Vec3d eyePos = controller.getEyePos();

        // 计算目标位置：玩家面前一定距离
        float controlDistance = CONTROL_DISTANCE;
        Vec3d newTargetPos = eyePos.add(lookDirection.multiply(controlDistance));

        // 平滑更新目标位置
        Vec3d currentTargetPos = this.targetControlPos;
        Vec3d toNewTarget = newTargetPos.subtract(currentTargetPos);
        double distanceToNewTarget = toNewTarget.length();

        if (distanceToNewTarget > 0.1) {
            // 平滑移动目标位置
            Vec3d smoothedToTarget = toNewTarget.multiply(CONTROL_SMOOTHING);
            this.targetControlPos = currentTargetPos.add(smoothedToTarget);
        } else {
            this.targetControlPos = newTargetPos;
        }

        // 平滑移动到目标位置
        Vec3d currentPos = this.getPos();
        Vec3d toTarget = targetControlPos.subtract(currentPos);
        double distance = toTarget.length();

        if (distance > 0.1) {
            // 使用平滑移动
            double speed = Math.min(distance * CONTROL_SPEED, 1.0); // 限制最大速度
            Vec3d velocity = toTarget.normalize().multiply(speed);
            this.setVelocity(velocity);
        } else {
            this.setVelocity(Vec3d.ZERO);
        }

        // 设置实体的朝向
        float yaw = controller.getYaw();
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);

        // 应用移动
        this.move(MovementType.SELF, this.getVelocity());

    }

    // ================= 粒子效果 =================


    private void processMovement() {
        // 更新跳跃计时器
        if (jumpTimer > 0) {
            jumpTimer--;
        }

        // 如果在地面上且跳跃计时器为0，准备跳跃
        if (this.isOnGround() && jumpTimer <= 0) {
            shouldJump = true;
            jumpTimer = JUMP_COOLDOWN;
        }

        // 执行跳跃
        if (shouldJump && isOnGround()) {
            jumpTowardTarget();
            shouldJump = false;
        }

        // 应用水平移动
        Vec3d horizontalDir = getHorizontalDirection();
        if (horizontalDir != null) {
            applyHorizontalMovement(horizontalDir);
        }

        // 应用速度和限制
        applyMovement();
    }

    private void jumpTowardTarget() {
        Vec3d direction = getHorizontalDirection();
        if (direction == null) return;

        // 计算跳跃力量（根据距离调整）
        float jumpMultiplier = 1.0f;

        this.setVelocity(
                direction.x * HORIZONTAL_SPEED * 2.0f * jumpMultiplier,
                JUMP_STRENGTH * jumpMultiplier + this.getVelocity().y,
                direction.z * HORIZONTAL_SPEED * 2.0f * jumpMultiplier
        );
    }

    @Override
    protected double getGravity() {
        return 0.08;
    }

    @Nullable
    private Vec3d getHorizontalDirection() {
        if (target == null) return null;

        Vec3d toTarget = target.getPos().subtract(this.getPos());
        Vec3d horizontal = new Vec3d(toTarget.x, 0, toTarget.z);

        if (horizontal.lengthSquared() < 0.001) {
            return null;
        }

        return horizontal.normalize();
    }

    private void applyHorizontalMovement(@NotNull Vec3d direction) {
        Vec3d horizontalVelocity = direction.multiply(HORIZONTAL_SPEED);
        this.setVelocity(new Vec3d(
                horizontalVelocity.x,
                this.getVelocity().y,
                horizontalVelocity.z
        ));

        // 应用移动
        this.move(MovementType.SELF, this.getVelocity());
    }

    private void applyMovement() {
        double e = this.getVelocity().y;
        e -= this.getFinalGravity();

        this.setVelocity(this.getVelocity().x, e * (double)0.98F, this.getVelocity().z);
    }

    // ================= Targetable接口实现 =================

    @Override
    @Nullable
    public Vec3d getDir() {
        if (target != null) {
            return target.getPos().subtract(this.getPos()).normalize();
        }
        return null;
    }

    @Override
    public boolean isClose() {
        if (target != null) {
            return this.getPos().distanceTo(target.getPos()) <= CLOSE_DISTANCE;
        }
        return false;
    }

    // ================= 其他方法 =================

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        super.fall(heightDifference, onGround, state, landedPosition);

        if (onGround) {
            // 重置跳跃状态
            this.shouldJump = false;
            // 重置发射状态
            this.justLaunched = false;
        }
    }

    @Override
    public boolean hasNoGravity() {
        return this.isControlled(); // 被控制时无重力
    }

    @Override
    public String toString() {
        return String.format("FollowingEntity{id=%d, pos=%s, target=%s, yaw=%.2f, controlled=%s}",
                getId(), getPos(),
                target != null ? target.getName().getString() : "null",
                this.getYaw(),
                isControlled() ? "true" : "false");
    }
}