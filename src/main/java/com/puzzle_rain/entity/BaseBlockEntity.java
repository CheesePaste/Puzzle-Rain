package com.puzzle_rain.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseBlockEntity extends Entity {
    // 公共字段
    protected int blockStateId;
    protected final List<Vec3d> trailPositions = new ArrayList<>();
    protected static final int MAX_TRAIL_LENGTH = 25;

    // 跟踪数据
    public static final TrackedData<Integer> BLOCK_STATE_ID =
            DataTracker.registerData(BaseBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // 构造函数
    protected BaseBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    protected BaseBlockEntity(EntityType<?> type, World world, BlockPos pos, BlockState blockState) {
        this(type, world);
        this.setPosition(Vec3d.ofCenter(pos));
        this.setBlockState(blockState);
        this.noClip = true;
    }

    // 公共方法
    public void updateTrail() {
        trailPositions.add(0, this.getPos());
        while (trailPositions.size() > MAX_TRAIL_LENGTH) {
            trailPositions.remove(trailPositions.size() - 1);
        }
    }

    public void setBlockState(BlockState blockState) {
        this.blockStateId = Block.getRawIdFromState(blockState);
        if (!this.getWorld().isClient()) {
            this.dataTracker.set(BLOCK_STATE_ID, blockStateId);
        }
    }

    public BlockState getBlockState() {
        return Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
    }

    public int getBlockStateId() {
        return this.blockStateId;
    }

    public List<Vec3d> getTrailPositions() {
        return trailPositions;
    }



    // 轨迹预测（可选）
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


    // Entity 方法重写
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, 0);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        updateTrail();


    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("BlockState", this.blockStateId);
        nbt.putInt("Age", this.age);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("BlockState")) {
            this.blockStateId = nbt.getInt("BlockState");
            this.dataTracker.set(BLOCK_STATE_ID, this.blockStateId);
        }
        if (nbt.contains("Age")) {
            this.age = nbt.getInt("Age");
        }
    }

    @Override
    public boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved();
    }

    // 碰撞相关
    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {}

    @Override
    protected void onBlockCollision(BlockState state) {}

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean canMoveVoluntarily() {
        return true;
    }

    @Override
    public boolean collidesWithStateAtPos(BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    protected void checkBlockCollision() {}

    @Override
    protected Vec3d adjustMovementForPiston(Vec3d movement) {
        return movement;
    }

    @Override
    protected Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type) {
        return movement;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {}

    @Override
    public boolean isPushable() {
        return false;
    }
}
