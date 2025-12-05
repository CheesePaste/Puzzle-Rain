package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private static final float JUMP_STRENGTH = 0.4f;
    private static final int JUMP_COOLDOWN = 20;
    private static final float MAX_SPEED = 0.5f;
    private static final float AIR_RESISTANCE = 0.98f;

    // 数据跟踪
    private static final TrackedData<Optional<UUID>> TARGET_UUID =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);

    // 状态字段
    @Nullable
    private Entity target;
    private boolean isOnGround = false;
    private int jumpTimer = 0;
    private boolean shouldJump = false;
    private int targetLostCounter = 0;
    private static final int MAX_TARGET_LOST_TICKS = 200; // 10秒后放弃跟随

    // ================= 构造方法 =================

    public FollowingEntity(EntityType<?> type, World world, @Nullable Entity target,
                           @NotNull BlockPos pos, @NotNull BlockState state) {
        super(type, world, pos, state);
        this.target = target;
        this.setNoGravity(false);
        this.setOnGround(true);

    }

    public FollowingEntity(EntityType<FollowingEntity> type, World world) {
        super(type, world);
        this.setNoGravity(false);
        //.info("FollowingEntity created with default constructor");
    }

    // ================= 数据跟踪 =================

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TARGET_UUID, Optional.empty());
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
                } else if (targetLostCounter % 20 == 0) {
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

        //.info("FollowingEntity data loaded. JumpTimer: {}, ShouldJump: {}", jumpTimer, shouldJump);
    }

    // ================= 主逻辑 =================

    @Override
    public void tick() {
        super.tick();

        // 更新地面状态
        this.isOnGround = this.isOnGround();

        // 只在服务器端执行逻辑
        //if (!this.getWorld().isClient()) {
            // 确保目标是最新的
            if (this.target == null || !this.target.isAlive()) {
                refreshTarget();
            }

            // 如果没有目标，停止移动
            if (this.target == null) {
                if (this.getVelocity().lengthSquared() > 0.01) {
                    this.setVelocity(Vec3d.ZERO);
                }
                return;
            }

            // 计算到目标的距离
            double distanceToTarget = this.getPos().distanceTo(target.getPos());

            // 如果距离足够近，停止移动
            if (distanceToTarget <= CLOSE_DISTANCE) {
                if (this.getVelocity().lengthSquared() > 0.01) {
                    this.setVelocity(Vec3d.ZERO);
                    //.info("Reached target, stopping at distance: {}", distanceToTarget);
                }
                return;
            }

            // 执行移动逻辑
            processMovement(distanceToTarget);
        //}

        // 调试信息
    }

    private void processMovement(double distanceToTarget) {
        // 更新跳跃计时器
        if (jumpTimer > 0) {
            jumpTimer--;
        }

        // 如果在地面上且跳跃计时器为0，准备跳跃
        if (isOnGround && jumpTimer == 0 && distanceToTarget > CLOSE_DISTANCE * 2) {
            shouldJump = true;
            jumpTimer = JUMP_COOLDOWN;
            //.info("Preparing to jump towards target (distance: {})", distanceToTarget);
        }

        // 执行跳跃
        if (shouldJump && isOnGround) {
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
        if (target != null) {
            double distance = this.getPos().distanceTo(target.getPos());
            jumpMultiplier = (float) Math.min(1.5, Math.max(0.5, distance / 10.0));
        }

        this.setVelocity(
                direction.x * HORIZONTAL_SPEED * 2.0f * jumpMultiplier,
                JUMP_STRENGTH * jumpMultiplier,
                direction.z * HORIZONTAL_SPEED * 2.0f * jumpMultiplier
        );

        this.setOnGround(false);
        //.info("Jump executed with multiplier: {}", jumpMultiplier);
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
        // 应用空气阻力
        Vec3d velocity = this.getVelocity();
        this.setVelocity(new Vec3d(
                velocity.x * AIR_RESISTANCE,
                velocity.y, // Y轴不受空气阻力影响
                velocity.z * AIR_RESISTANCE
        ));

        // 限制最大速度
        velocity = this.getVelocity();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (horizontalSpeed > MAX_SPEED) {
            double scale = MAX_SPEED / horizontalSpeed;
            this.setVelocity(new Vec3d(
                    velocity.x * scale,
                    velocity.y,
                    velocity.z * scale
            ));
        }
    }

    // ================= Targetable接口实现 =================

    @Override
    @Nullable
    public Vec3d getDir() {
        if (target != null) {
            Vec3d dir = target.getPos().subtract(this.getPos()).normalize();
            return dir;
        }
        return null;
    }

    @Override
    public boolean isClose() {
        if (target != null) {
            boolean close = this.getPos().distanceTo(target.getPos()) <= CLOSE_DISTANCE;
            if (close) {
                //.info("Is close to target: {}", close);
            }
            return close;
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
            if (Math.abs(heightDifference) > 1.0) {
                //.info("Landed after fall of {}. Reset jump state.", heightDifference);
            }
        }
    }

    @Override
    public boolean hasNoGravity() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("FollowingEntity{id=%d, pos=%s, target=%s}",
                getId(), getPos(),
                target != null ? target.getName().getString() : "null");
    }
}