package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.ControlEnum;
import com.puzzle_rain.GravitationalDistortionShader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class FlyingBlockEntity extends Entity {
    // 运动状态枚举
    public enum MovementState {
        IMPLOSION,    // 向内收缩阶段
        EXPLOSION,    // 向外爆炸阶段
        FLOATING,     // 自由漂浮阶段
        STASIS        // 时间凝滞状态
    }

    // 数据跟踪器
    public static final TrackedData<Integer> BLOCK_STATE_ID = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MOVEMENT_STATE = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> ROTATION_SPEED_X = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> ROTATION_SPEED_Y = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> OPACITY = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);

    // 实例变量
    private int blockStateId;
    int age = 0;
    private int maxAge = 20000; // 防止内存泄漏
    private final List<Vec3d> trailPositions = new ArrayList<>();
    private static final int MAX_TRAIL_LENGTH = 25;

    // 内爆目标点
    private Vec3d implosionTarget;

    // 重力相关
    private double gravityRadius = 3.0;
    private boolean isCreatingGravity = true;

    // 物理参数
    private float rotationX = 0;
    private float rotationY = 0;
    private float targetScale = 1.0f;
    private float scaleChangeSpeed = 0.05f;

    // 消失效果
    private boolean isFading = false;
    private int fadeStartTime = 0;
    private static final int FADE_DURATION = 20;

    public FlyingBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true; // 始终渲染，即使不在视锥体内
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, Block.getRawIdFromState(net.minecraft.block.Blocks.STONE.getDefaultState()));
        builder.add(MOVEMENT_STATE, MovementState.FLOATING.ordinal());
        builder.add(SCALE, 1.0f);
        builder.add(ROTATION_SPEED_X, 0.0f);
        builder.add(ROTATION_SPEED_Y, 0.0f);
        builder.add(OPACITY, 1.0f);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data.equals(BLOCK_STATE_ID)) {
            this.blockStateId = this.dataTracker.get(BLOCK_STATE_ID);
        }
    }

    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {
        // 留空，不执行推离方块逻辑
    }

    @Override
    protected void onBlockCollision(BlockState state) {
        // 留空，不执行方块碰撞逻辑
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false; // 不与其他实体碰撞
    }

    @Override
    public boolean canMoveVoluntarily() {
        return true;
    }

    @Override
    public boolean collidesWithStateAtPos(BlockPos pos, BlockState state) {
        return false; // 不与任何方块状态碰撞
    }

    public FlyingBlockEntity(World world, BlockPos pos, BlockState blockState) {
        super(ModEntities.FLYING_BLOCK_ENTITY, world);
        this.setPosition(Vec3d.ofCenter(pos));
        this.blockStateId = Block.getRawIdFromState(blockState);
        this.dataTracker.set(BLOCK_STATE_ID, blockStateId);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;

        // 初始化随机旋转和缩放
        this.rotationX = (float) (Math.random() * 360);
        this.rotationY = (float) (Math.random() * 360);

        // 随机旋转速度
        float rotSpeedX = (float) (Math.random() - 0.5) * 10f;
        float rotSpeedY = (float) (Math.random() - 0.5) * 10f;
        this.dataTracker.set(ROTATION_SPEED_X, rotSpeedX);
        this.dataTracker.set(ROTATION_SPEED_Y, rotSpeedY);

        // 随机初始缩放
        float initialScale = 0.8f + (float) Math.random() * 0.4f;
        this.dataTracker.set(SCALE, initialScale);
        this.targetScale = initialScale;

        // 设置不透明度
        this.dataTracker.set(OPACITY, 1.0f);

        // 初始运动状态为漂浮
        this.setMovementState(MovementState.FLOATING);
    }

    public void setBlockState(BlockState blockState) {
        this.blockStateId = Block.getRawIdFromState(blockState);
        if (!this.getWorld().isClient()) {
            this.dataTracker.set(BLOCK_STATE_ID, blockStateId);
        }
    }

    @Override
    protected void checkBlockCollision() {
        // 留空，不执行方块碰撞检测
    }

    @Override
    protected Vec3d adjustMovementForPiston(Vec3d movement) {
        return movement;
    }

    @Override
    protected Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type) {
        return movement;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("BlockState", this.blockStateId);
        nbt.putInt("Age", this.age);
        nbt.putInt("MovementState", this.getMovementState().ordinal());
        nbt.putFloat("Scale", this.dataTracker.get(SCALE));
        nbt.putFloat("RotationX", this.rotationX);
        nbt.putFloat("RotationY", this.rotationY);
        nbt.putFloat("Opacity", this.dataTracker.get(OPACITY));

        // 保存内爆目标
        if (this.implosionTarget != null) {
            nbt.putDouble("ImplosionTargetX", this.implosionTarget.x);
            nbt.putDouble("ImplosionTargetY", this.implosionTarget.y);
            nbt.putDouble("ImplosionTargetZ", this.implosionTarget.z);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("BlockState")) {
            this.blockStateId = nbt.getInt("BlockState");
            this.dataTracker.set(BLOCK_STATE_ID, blockStateId);
        }
        if (nbt.contains("Age")) {
            this.age = nbt.getInt("Age");
        }
        if (nbt.contains("MovementState")) {
            this.setMovementState(MovementState.values()[nbt.getInt("MovementState")]);
        }
        if (nbt.contains("Scale")) {
            this.dataTracker.set(SCALE, nbt.getFloat("Scale"));
            this.targetScale = nbt.getFloat("Scale");
        }
        if (nbt.contains("RotationX")) {
            this.rotationX = nbt.getFloat("RotationX");
        }
        if (nbt.contains("RotationY")) {
            this.rotationY = nbt.getFloat("RotationY");
        }
        if (nbt.contains("Opacity")) {
            this.dataTracker.set(OPACITY, nbt.getFloat("Opacity"));
        }

        // 读取内爆目标
        if (nbt.contains("ImplosionTargetX")) {
            this.implosionTarget = new Vec3d(
                    nbt.getDouble("ImplosionTargetX"),
                    nbt.getDouble("ImplosionTargetY"),
                    nbt.getDouble("ImplosionTargetZ")
            );
        }
    }

    public BlockState getBlockState() {
        return Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
    }

    public MovementState getMovementState() {
        int stateOrdinal = this.dataTracker.get(MOVEMENT_STATE);
        return MovementState.values()[stateOrdinal];
    }

    public void setMovementState(MovementState state) {
        this.dataTracker.set(MOVEMENT_STATE, state.ordinal());

        // 根据状态调整物理参数
        switch (state) {
            case IMPLOSION:
                this.setNoGravity(true);
                this.targetScale = 0.7f + (float) Math.random() * 0.6f;
                break;
            case EXPLOSION:
                this.setNoGravity(false); // 爆炸后受重力影响
                this.targetScale = 1.2f + (float) Math.random() * 0.6f;
                break;
            case STASIS:
                this.setNoGravity(true);
                this.setVelocity(Vec3d.ZERO);
                break;
            case FLOATING:
                this.setNoGravity(true);
                this.targetScale = 0.9f + (float) Math.random() * 0.2f;
                break;
        }
    }

    public void setImplosionTarget(Vec3d target) {
        this.implosionTarget = target;
    }

    public float getScale() {
        return this.dataTracker.get(SCALE);
    }

    public void setScale(float scale) {
        this.dataTracker.set(SCALE, scale);
        this.targetScale = scale;
    }

    public float getOpacity() {
        return this.dataTracker.get(OPACITY);
    }

    public void setOpacity(float opacity) {
        this.dataTracker.set(OPACITY, opacity);
    }

    public float getRotationX() {
        return this.rotationX;
    }

    public float getRotationY() {
        return this.rotationY;
    }

    public List<Vec3d> getTrailPositions() {
        return trailPositions;
    }

    // 基于速度预测轨迹
    public List<Vec3d> getPredictedTrail(float deltaTime, int steps) {
        List<Vec3d> predicted = new ArrayList<>();
        Vec3d currentPos = this.getPos();
        Vec3d velocity = this.getVelocity();

        for (int i = 0; i < steps; i++) {
            float time = i * deltaTime;
            Vec3d predictedPos = currentPos.add(velocity.multiply(time));
            predicted.add(predictedPos);
        }

        return predicted;
    }

    private void updateTrail() {
        trailPositions.add(this.getPos());
        if (trailPositions.size() > MAX_TRAIL_LENGTH) {
            trailPositions.remove(0);
        }
    }

    private void updateScale() {
        float currentScale = this.dataTracker.get(SCALE);
        if (Math.abs(currentScale - targetScale) > 0.01f) {
            float newScale = currentScale + (targetScale - currentScale) * scaleChangeSpeed;
            this.dataTracker.set(SCALE, newScale);
        }
    }

    private void updateImplosionMovement() {
        if (implosionTarget != null) {
            Vec3d currentPos = this.getPos();
            Vec3d direction = implosionTarget.subtract(currentPos);
            double distance = direction.length();

            if (distance > 0.5) {
                // 归一化方向并应用速度
                direction = direction.normalize();

                // 距离越近速度越快，模拟引力增强
                double speed = 0.2 + (1.0 - Math.min(distance / 10.0, 1.0)) * 0.8;
                this.setVelocity(direction.multiply(speed));

                // 添加随机摆动，使运动更自然
                if (Math.random() < 0.3) {
                    Vec3d randomOffset = new Vec3d(
                            Math.random() - 0.5,
                            Math.random() - 0.5,
                            Math.random() - 0.5
                    ).multiply(0.1);
                    this.addVelocity(randomOffset);
                }
            } else {
                // 到达目标点，准备爆炸
                this.setVelocity(Vec3d.ZERO);
                this.setMovementState(MovementState.EXPLOSION);

                // 播放到达音效
                if (!this.getWorld().isClient()) {
                    this.getWorld().playSound(null, this.getBlockPos(),
                            SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS,
                            0.5f, 1.5f);
                }
            }
        }
    }

    private void updateExplosionMovement() {
        // 爆炸后逐渐减速
        Vec3d velocity = this.getVelocity();
        this.setVelocity(velocity.multiply(0.97));

        // 添加随机旋转变化
        float rotSpeedX = this.dataTracker.get(ROTATION_SPEED_X);
        float rotSpeedY = this.dataTracker.get(ROTATION_SPEED_Y);
        this.dataTracker.set(ROTATION_SPEED_X, rotSpeedX * 0.99f);
        this.dataTracker.set(ROTATION_SPEED_Y, rotSpeedY * 0.99f);

        // 爆炸后逐渐缩小
        if (this.targetScale > 0.5f) {
            this.targetScale *= 0.995f;
        }

        // 在爆炸一段时间后开始淡出
        if (this.age > 100 && !this.isFading) {
            this.isFading = true;
            this.fadeStartTime = this.age;
        }
    }

    private void updateFloatingMovement() {
        // 缓慢减速
        if (this.getVelocity().lengthSquared() > 0.001) {
            this.setVelocity(this.getVelocity().multiply(0.98));
        }

        // 添加轻微的漂浮运动
        if (Math.random() < 0.1) {
            Vec3d floatMotion = new Vec3d(
                    (Math.random() - 0.5) * 0.02,
                    (Math.random() - 0.5) * 0.02,
                    (Math.random() - 0.5) * 0.02
            );
            this.addVelocity(floatMotion);
        }

        // 一段时间后开始淡出
        if (this.age > 200 && !this.isFading) {
            this.isFading = true;
            this.fadeStartTime = this.age;
        }
    }

    private void updateStasisMovement() {
        // 时间凝滞状态，完全静止
        this.setVelocity(Vec3d.ZERO);
    }

    private void updateFadeEffect() {
        if (this.isFading) {
            float fadeProgress = (float) (this.age - this.fadeStartTime) / FADE_DURATION;
            float opacity = Math.max(0, 1 - fadeProgress);
            this.setOpacity(opacity);

            // 完全淡出后移除实体
            if (fadeProgress >= 1.0f) {
                this.spawnDisappearParticles();
                this.discard();
            }
        }
    }

    private void spawnDisappearParticles() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            BlockState blockState = this.getBlockState();
            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                    this.getX(), this.getY(), this.getZ(),
                    10, 0.2, 0.2, 0.2, 0.05
            );
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        updateTrail();
        updateScale();

        // 更新旋转
        float rotSpeedX = this.dataTracker.get(ROTATION_SPEED_X);
        float rotSpeedY = this.dataTracker.get(ROTATION_SPEED_Y);
        this.rotationX += rotSpeedX;
        this.rotationY += rotSpeedY;

        // 保持旋转在0-360度范围内
        this.rotationX %= 360;
        this.rotationY %= 360;

        // 根据运动状态更新物理行为
        MovementState state = this.getMovementState();
        switch (state) {
            case IMPLOSION:
                updateImplosionMovement();
                break;
            case EXPLOSION:
                updateExplosionMovement();
                break;
            case STASIS:
                updateStasisMovement();
                break;
            case FLOATING:
                updateFloatingMovement();
                break;
        }

        // 更新淡出效果
        updateFadeEffect();

        // 自动移除旧实体防止内存泄漏
        if (this.age > maxAge) {
            this.discard();
            return;
        }

        // 更新位置（基于速度）
        if (this.getVelocity().lengthSquared() > 0.001) {
            this.move(MovementType.SELF, this.getVelocity());
        }

        // 客户端引力场效果
        if (isCreatingGravity && this.getWorld().isClient() && state == MovementState.IMPLOSION) {
            GravitationalDistortionShader.addGravityCenter(this.getPos(), gravityRadius);
        }
    }

    // 触发爆炸效果
    public void triggerExplosion(float power) {
        this.setMovementState(MovementState.EXPLOSION);

        // 随机爆炸方向
        Vec3d randomDir = new Vec3d(
                Math.random() - 0.5,
                Math.random() - 0.5,
                Math.random() - 0.5
        ).normalize().multiply(power * (0.8 + Math.random() * 0.4));

        this.setVelocity(randomDir);

        // 增加旋转速度
        float currentRotSpeedX = this.dataTracker.get(ROTATION_SPEED_X);
        float currentRotSpeedY = this.dataTracker.get(ROTATION_SPEED_Y);
        this.dataTracker.set(ROTATION_SPEED_X, currentRotSpeedX * 2f);
        this.dataTracker.set(ROTATION_SPEED_Y, currentRotSpeedY * 2f);
    }

    // 触发时间凝滞
    public void triggerStasis() {
        this.setMovementState(MovementState.STASIS);
        this.targetScale = 1.5f; // 凝滞时稍微放大
        this.scaleChangeSpeed = 0.1f; // 快速缩放
    }

    // 释放时间凝滞
    public void releaseStasis() {
        this.setMovementState(MovementState.FLOATING);
        this.targetScale = 1.0f;
        this.scaleChangeSpeed = 0.05f;
    }

    @Override
    public boolean canHit() {
        return false; // 不能被击中
    }

    @Override
    public boolean isCollidable() {
        return false; // 没有碰撞
    }

    @Override
    public void pushAwayFrom(Entity entity) {
        // 留空不执行任何推开逻辑
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    public boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        return true; // 不与其他任何东西碰撞
    }

    @Override
    public boolean isAttackable() {
        return false; // 不能被攻击
    }

    @Override
    public boolean isFireImmune() {
        return true; // 免疫火焰
    }

    @Override
    public boolean hasNoGravity() {
        return this.getMovementState() != MovementState.EXPLOSION; // 只有爆炸阶段受重力
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved();
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // 实体移除时清理引力场
        if (this.getWorld().isClient()) {
            GravitationalDistortionShader.removeGravityCenter(this.getPos());
        }
    }


    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.blockStateId = this.dataTracker.get(BLOCK_STATE_ID);
    }
}