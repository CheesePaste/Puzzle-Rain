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
import net.minecraft.entity.effect.StatusEffects;
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

    // 数据跟踪
    private static final TrackedData<Optional<UUID>> TARGET_UUID =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Float> TARGET_YAW =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> CURRENT_YAW =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // 状态字段
    @Nullable
    private Entity target;
    private int jumpTimer = 0;
    private boolean shouldJump = false;
    private int targetLostCounter = 0;
    private static final int MAX_TARGET_LOST_TICKS = 200; // 10秒后放弃跟随

    // 旋转状态
    private float prevRenderYaw; // 用于渲染插值

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
        this.MAX_TRAIL_LENGTH=200;

        // 初始化旋转
        this.prevRenderYaw = 0;
    }

    public static DefaultAttributeContainer.Builder createFollowingAttributes() {
        return BaseBlockEntity.createBaseBlockAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0) // 更高的生命值
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25) // 移动速度
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0) // 完全抵抗击退
                .add(EntityAttributes.GENERIC_GRAVITY, 0.08) // 重力
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 0.0) // 可以走上1格高的方块
                .add(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 3.0) // 安全坠落距离
                .add(EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER, 0.0); // 无坠落伤害
    }

    public FollowingEntity(EntityType<FollowingEntity> type, World world) {
        super(type, world);
        this.setNoGravity(false);
        Colli();
        this.MAX_TRAIL_LENGTH=200;
        //.info("FollowingEntity created with default constructor");

        // 初始化旋转
        this.prevRenderYaw = 0;
    }

    // ================= 数据跟踪 =================

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TARGET_UUID, Optional.empty());
        builder.add(TARGET_YAW, 0f);
        builder.add(CURRENT_YAW, 0f);
        //.info("Data tracker initialized with TARGET_UUID");
    }

    // ================= 目标管理 =================

    public void setTarget(@Nullable Entity target) {
        Entity oldTarget = this.target;
        this.target = target;

        if (target != null) {
            this.dataTracker.set(TARGET_UUID, Optional.of(target.getUuid()));
            targetLostCounter = 0;
            //.info("Target set to {} (UUID: {})", target.getName().getString(), target.getUuid());
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
                //.info("Target refreshed: {}", entity.getName().getString());
            } else {
                targetLostCounter++;
                if (targetLostCounter > MAX_TARGET_LOST_TICKS) {
                    this.target = null;
                    this.dataTracker.set(TARGET_UUID, Optional.empty());
                }
            }
        }
    }

    // ================= NBT序列化 =================

    @Override
    public void writeCustomDataToNbt(@NotNull NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (this.target != null) {
            nbt.putUuid("Target", this.target.getUuid());
        }

        nbt.putInt("JumpTimer", this.jumpTimer);
        nbt.putBoolean("ShouldJump", this.shouldJump);
        nbt.putInt("TargetLostCounter", targetLostCounter);
        nbt.putFloat("TargetYaw", this.dataTracker.get(TARGET_YAW));
        nbt.putFloat("CurrentYaw", this.dataTracker.get(CURRENT_YAW));
    }

    @Override
    public void readCustomDataFromNbt(@NotNull NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("Target", NbtElement.INT_ARRAY_TYPE)) {
            UUID targetUuid = nbt.getUuid("Target");
            this.dataTracker.set(TARGET_UUID, Optional.of(targetUuid));

            // 延迟目标查找，因为世界可能还没加载完
            //.info("Target UUID loaded from NBT: {}", targetUuid);
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

        if (nbt.contains("TargetYaw", NbtElement.FLOAT_TYPE)) {
            this.dataTracker.set(TARGET_YAW, nbt.getFloat("TargetYaw"));
        }

        if (nbt.contains("CurrentYaw", NbtElement.FLOAT_TYPE)) {
            this.dataTracker.set(CURRENT_YAW, nbt.getFloat("CurrentYaw"));
        }

        // 同步当前yaw
        this.setYaw(this.dataTracker.get(CURRENT_YAW));
        this.prevRenderYaw = this.getYaw();
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

        // 更新数据跟踪器
        this.dataTracker.set(TARGET_YAW, targetYaw);
    }

    /**
     * 平滑插值旋转到目标角度
     */
    private void updateRotation(float tickDelta) {
        if (this.target == null) {
            return;
        }

        // 获取目标角度
        float targetYaw = this.dataTracker.get(TARGET_YAW);

        // 获取当前角度
        float currentYaw = this.dataTracker.get(CURRENT_YAW);

        // 保存之前的渲染角度用于插值
        this.prevRenderYaw = currentYaw;

        // 计算角度差，使用最短路径
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);

        // 应用旋转插值
        float rotationStep = ROTATION_SPEED * ROTATION_INTERPOLATION_FACTOR;

        // 限制最大旋转速度
        yawDiff = MathHelper.clamp(yawDiff, -rotationStep, rotationStep);

        // 应用旋转
        float newYaw = currentYaw + yawDiff;

        // 确保角度在有效范围内
        newYaw = MathHelper.wrapDegrees(newYaw);

        // 更新数据跟踪器和实体角度
        this.dataTracker.set(CURRENT_YAW, newYaw);

        // 同步到实体
        this.setYaw(newYaw);
        this.setHeadYaw(newYaw); // 设置头部朝向
        this.setBodyYaw(newYaw); // 设置身体朝向

        // 固定pitch为0，不抬头低头
        this.setPitch(0f);
    }

    /**
     * 获取用于渲染的插值yaw角度
     */
    public float getRenderYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevRenderYaw, this.getYaw());
    }

    // ================= 主逻辑 =================

    @Override
    public void tick() {
        // 先调用父类的tick，这会更新位置、速度、重力等
        super.tick();

        // 确保目标是最新的
        if (this.target == null || !this.target.isAlive()) {
            refreshTarget();
        }
        debugPhysics();

        // 更新旋转系统
        if (this.target != null) {
            calculateTargetRotation();
            updateRotation(1.0f); // 使用完整的tickDelta
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
        return null;
    }

    private void processMovement() {
        // 更新跳跃计时器
        if (jumpTimer > 0) {
            jumpTimer--;
        }

        // 添加调试日志查看状态
        if (this.age % 20 == 0) {
            System.out.println("isOnGround: " + this.isOnGround() + ", jumpTimer: " + jumpTimer + ", velocityY: " + this.getVelocity().y);
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

    private void debugPhysics() {
        if (this.age % 20 == 0) {  // 每秒一次
            System.out.println("=== Physics Debug ===");
            System.out.println("Position: " + this.getPos());
            System.out.println("Velocity: " + this.getVelocity());
            System.out.println("isOnGround: " + this.isOnGround());
            System.out.println("hasNoGravity: " + this.hasNoGravity());
            System.out.println("isOnGround (field): " + this.isOnGround());
            System.out.println("Distance to target: " + (this.target != null ? this.getPos().distanceTo(this.target.getPos()) : "No target"));
            System.out.println("Yaw: " + this.getYaw());
            System.out.println("Target Yaw: " + this.dataTracker.get(TARGET_YAW));
            System.out.println("==================");
        }
    }

    private void jumpTowardTarget() {
        Vec3d direction = getHorizontalDirection();
        System.out.println(direction);
        System.out.println(target);
        if (direction == null) return;

        // 计算跳跃力量（根据距离调整）
        float jumpMultiplier = 1.0f;

        this.setVelocity(
                direction.x * HORIZONTAL_SPEED * 2.0f * jumpMultiplier,
                JUMP_STRENGTH * jumpMultiplier+this.getVelocity().y,
                direction.z * HORIZONTAL_SPEED * 2.0f * jumpMultiplier
        );

        //this.setOnGround(false);
        //.info("Jump executed with multiplier: {}", jumpMultiplier);
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
            //.info("Landed after fall of {}. Reset jump state.", heightDifference);
        }
    }

    @Override
    public boolean hasNoGravity() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("FollowingEntity{id=%d, pos=%s, target=%s, yaw=%.2f}",
                getId(), getPos(),
                target != null ? target.getName().getString() : "null",
                this.getYaw());
    }
}