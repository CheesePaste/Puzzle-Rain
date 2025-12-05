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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FollowingEntity extends BaseBlockEntity implements Targetable {

    private static final TrackedData<Integer> TARGET_ID = DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // 跟随参数
    private float followSpeed = 0.3f;  // 增加跟随速度
    private float followDistance = 10.0f;  // 最大跟随距离
    private float minDistance = 2.0f;  // 最小停止距离
    private float acceleration = 0.1f;  // 加速度

    // 目标UUID，用于持久化
    private String targetUuid = "";

    // 上次记录的位置，用于平滑移动
    private Vec3d lastTargetPos = Vec3d.ZERO;
    private int noTargetTicks = 0;

    public FollowingEntity(EntityType<?> type, World world, Entity target, BlockPos pos, BlockState state) {
        super(type, world, pos, state);
        if (target != null) {
            setTarget(target);
        }
        // 初始化位置
        this.lastTargetPos = this.getPos();
    }

    public FollowingEntity(EntityType<FollowingEntity> followingEntityEntityType, World world) {
        super(followingEntityEntityType, world);
        this.lastTargetPos = this.getPos();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TARGET_ID, 0);
    }

    /**
     * 设置目标实体
     */
    public void setTarget(Entity entity) {
        if (entity != null) {
            this.dataTracker.set(TARGET_ID, entity.getId());
            this.targetUuid = entity.getUuid().toString();
            this.lastTargetPos = entity.getPos();
            PuzzleRain.LOGGER.info("设置目标：{} (ID: {})", entity.getName().getString(), entity.getId());
        } else {
            this.dataTracker.set(TARGET_ID, 0);
            this.targetUuid = "";
        }
    }

    /**
     * 获取目标实体
     */
    public Entity getTarget() {
        int targetId = this.dataTracker.get(TARGET_ID);

        // 优先使用ID获取实体
        if (targetId > 0) {
            Entity target = this.getWorld().getEntityById(targetId);
            if (target != null && target.isAlive()) {
                return target;
            } else {
                // 目标无效，清除ID
                this.dataTracker.set(TARGET_ID, 0);
            }
        }

        // 如果没有ID但有UUID，尝试通过UUID获取
        if (!this.targetUuid.isEmpty() && this.getWorld() instanceof ServerWorld) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(this.targetUuid);
                Entity target = ((ServerWorld) this.getWorld()).getEntity(uuid);
                if (target != null && target.isAlive()) {
                    this.dataTracker.set(TARGET_ID, target.getId());
                    return target;
                }
            } catch (IllegalArgumentException e) {
                // UUID格式无效
                this.targetUuid = "";
            }
        }

        return null;
    }

    @Override
    public void tick() {
        super.tick();

        Entity target = getTarget();

        if (target != null) {
            // 重置无目标计数器
            noTargetTicks = 0;

            // 获取目标位置和当前位置
            Vec3d targetPos = target.getPos();
            Vec3d currentPos = this.getPos();

            // 计算距离和方向
            Vec3d toTarget = targetPos.subtract(currentPos);
            double distance = toTarget.length();

            // 更新最后记录的目标位置
            lastTargetPos = targetPos;

            if (!this.getWorld().isClient) {
                // 服务器端移动逻辑
                if (distance > minDistance) {
                    // 计算移动方向
                    Vec3d direction = toTarget.normalize();

                    // 计算速度 - 距离越远速度越快，但不超过最大值
                    double speedMultiplier = Math.min(1.0, distance / 5.0);
                    double currentSpeed = Math.min(followSpeed * speedMultiplier, followSpeed);

                    // 应用加速度
                    Vec3d currentVelocity = this.getVelocity();
                    Vec3d desiredVelocity = direction.multiply(currentSpeed);
                    Vec3d newVelocity = currentVelocity.multiply(0.9).add(desiredVelocity.multiply(acceleration));

                    // 限制最大速度
                    if (newVelocity.length() > followSpeed) {
                        newVelocity = newVelocity.normalize().multiply(followSpeed);
                    }

                    // 应用重力，但减小重力影响
                    double gravity = this.hasNoGravity() ? 0 : -0.02;
                    newVelocity = new Vec3d(newVelocity.x, newVelocity.y + gravity, newVelocity.z);

                    // 设置速度
                    this.setVelocity(newVelocity);

                    // 应用移动
                    this.move(MovementType.SELF, this.getVelocity());

                    // 朝目标方向旋转
                    this.setYaw((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));

                    PuzzleRain.LOGGER.debug("跟随目标：距离={}, 速度={}, 方向={}",
                            String.format("%.2f", distance),
                            String.format("%.2f", currentSpeed),
                            direction);
                } else {
                    // 已经接近，减速停止
                    Vec3d currentVelocity = this.getVelocity();
                    Vec3d newVelocity = currentVelocity.multiply(0.8);
                    this.setVelocity(newVelocity);
                    this.move(MovementType.SELF, this.getVelocity());
                }
            }
        } else {
            // 没有目标时的逻辑
            noTargetTicks++;

            if (!this.getWorld().isClient) {
                // 缓慢下降
                Vec3d currentVelocity = this.getVelocity();
                double gravity = this.hasNoGravity() ? 0 : -0.01;
                Vec3d newVelocity = currentVelocity.multiply(0.95).add(0, gravity, 0);
                this.setVelocity(newVelocity);
                this.move(MovementType.SELF, this.getVelocity());

                // 长时间没有目标，考虑移除实体
                if (noTargetTicks > 600) { // 30秒后
                    this.discard();
                }
            }
        }

        // 限制垂直速度，防止飞得太高
        if (Math.abs(this.getVelocity().y) > 0.5) {
            this.setVelocity(this.getVelocity().x, this.getVelocity().y * 0.8, this.getVelocity().z);
        }
    }

    @Override
    public Vec3d getDir() {
        Entity target = getTarget();
        if (target != null) {
            Vec3d targetPos = target.getPos();
            Vec3d currentPos = this.getPos();
            return targetPos.subtract(currentPos).normalize();
        }

        // 如果没有目标，使用最后记录的位置
        return lastTargetPos.subtract(this.getPos()).normalize();
    }
    @Override
    public boolean hasNoGravity() {
        // 必须重写：返回false以启用重力及物理运动
        return false;
    }

    @Override
    public boolean isNoClip() {
        // 必须重写：返回false以确保速度能正常改变位置
        return false;
    }


    @Override
    public boolean isClose() {
        Entity target = getTarget();
        if (target != null) {
            double distance = this.getPos().distanceTo(target.getPos());
            return distance <= minDistance;
        }
        return false;
    }

    // --- NBT 保存与读取 ---

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        // 保存目标UUID
        if (!this.targetUuid.isEmpty()) {
            nbt.putString("TargetUuid", this.targetUuid);
        }

        // 保存跟随参数
        nbt.putFloat("FollowSpeed", followSpeed);
        nbt.putFloat("FollowDistance", followDistance);
        nbt.putFloat("MinDistance", minDistance);
        nbt.putFloat("Acceleration", acceleration);

        // 保存最后位置
        nbt.putDouble("LastTargetX", lastTargetPos.x);
        nbt.putDouble("LastTargetY", lastTargetPos.y);
        nbt.putDouble("LastTargetZ", lastTargetPos.z);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        // 读取目标UUID
        if (nbt.contains("TargetUuid")) {
            this.targetUuid = nbt.getString("TargetUuid");
        }

        // 读取跟随参数
        if (nbt.contains("FollowSpeed")) {
            followSpeed = nbt.getFloat("FollowSpeed");
        }
        if (nbt.contains("FollowDistance")) {
            followDistance = nbt.getFloat("FollowDistance");
        }
        if (nbt.contains("MinDistance")) {
            minDistance = nbt.getFloat("MinDistance");
        }
        if (nbt.contains("Acceleration")) {
            acceleration = nbt.getFloat("Acceleration");
        }

        // 读取最后位置
        if (nbt.contains("LastTargetX") && nbt.contains("LastTargetY") && nbt.contains("LastTargetZ")) {
            double x = nbt.getDouble("LastTargetX");
            double y = nbt.getDouble("LastTargetY");
            double z = nbt.getDouble("LastTargetZ");
            lastTargetPos = new Vec3d(x, y, z);
        }
    }
}