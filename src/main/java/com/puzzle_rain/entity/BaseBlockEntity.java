package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.Block;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 基础方块实体类
 * 提供方块状态存储和轨迹跟踪功能
 */
public abstract class BaseBlockEntity extends Entity {

    // 常量
    protected static final int MAX_TRAIL_LENGTH = 25;
    public static final TrackedData<Integer> BLOCK_STATE_ID =
            DataTracker.registerData(BaseBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // 字段
    protected int blockStateId;
    protected final List<Vec3d> trailPositions = new ArrayList<>();
    private int debugTickCounter = 0;
    private static final int DEBUG_LOG_INTERVAL = 100; // 每100 tick记录一次调试信息

    // ================= 构造方法 =================

    protected BaseBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        //.info("BaseBlockEntity created with default constructor");
    }

    protected BaseBlockEntity(EntityType<?> type, World world, BlockPos pos, BlockState blockState) {
        this(type, world);
        this.setPosition(Vec3d.ofCenter(pos));
        this.setBlockState(blockState);
        //.info("BaseBlockEntity created at {} with block state: {}", pos, blockState);
    }

    // ================= 数据跟踪 =================

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, 0);
        //.info("Data tracker initialized");
    }

    // ================= 方块状态管理 =================

    public void setBlockState(@NotNull BlockState blockState) {
        int oldId = this.blockStateId;
        this.blockStateId = Block.getRawIdFromState(blockState);

        if (!this.getWorld().isClient()) {
            this.dataTracker.set(BLOCK_STATE_ID, blockStateId);
            //.info("Block state updated: {} -> {} (raw id: {})",
                    //Block.getStateFromRawId(oldId), blockState, blockStateId);
        }
    }

    public @NotNull BlockState getBlockState() {
        BlockState state = Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
        if (state == null) {
            //.info("Failed to get block state from raw id: {}", this.dataTracker.get(BLOCK_STATE_ID));
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return state;
    }

    public int getBlockStateId() {
        return this.blockStateId;
    }

    // ================= 轨迹管理 =================

    public void updateTrail() {
        Vec3d currentPos = this.getPos();
        trailPositions.add(0, currentPos);

        while (trailPositions.size() > MAX_TRAIL_LENGTH) {
            trailPositions.remove(trailPositions.size() - 1);
        }

        if (this.age % 100 == 0) {
            //.info("Trail updated. Current size: {}/{}", trailPositions.size(), MAX_TRAIL_LENGTH);
        }
    }

    public @NotNull List<Vec3d> getTrailPositions() {
        return new ArrayList<>(trailPositions);
    }

    public @NotNull List<Vec3d> getPredictedTrail(float deltaTime, int steps) {
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

    // ================= Entity方法重写 =================

    @Override
    public void tick() {
        super.tick();
        this.age++;

        // 更新轨迹
        updateTrail();

        // 定期记录调试信息
        debugTickCounter++;
        if (debugTickCounter >= DEBUG_LOG_INTERVAL) {
            debugTickCounter = 0;
        }
    }

    @Override
    public void writeCustomDataToNbt(@NotNull NbtCompound nbt) {
        nbt.putInt("BlockState", this.blockStateId);
        nbt.putInt("Age", this.age);

        // 保存轨迹数据（可选）
        if (!trailPositions.isEmpty()) {
            NbtCompound trailNbt = new NbtCompound();
            trailNbt.putInt("Size", trailPositions.size());
            for (int i = 0; i < trailPositions.size(); i++) {
                Vec3d pos = trailPositions.get(i);
                trailNbt.putDouble("X" + i, pos.x);
                trailNbt.putDouble("Y" + i, pos.y);
                trailNbt.putDouble("Z" + i, pos.z);
            }
            nbt.put("Trail", trailNbt);
        }

        //.info("Data written to NBT. BlockStateId: {}, Age: {}", blockStateId, age);
    }

    @Override
    public void readCustomDataFromNbt(@NotNull NbtCompound nbt) {
        if (nbt.contains("BlockState", NbtElement.INT_TYPE)) {
            this.blockStateId = nbt.getInt("BlockState");
            this.dataTracker.set(BLOCK_STATE_ID, this.blockStateId);
        }

        if (nbt.contains("Age", NbtElement.INT_TYPE)) {
            this.age = nbt.getInt("Age");
        }

        // 读取轨迹数据（可选）
        if (nbt.contains("Trail", NbtElement.COMPOUND_TYPE)) {
            NbtCompound trailNbt = nbt.getCompound("Trail");
            int size = trailNbt.getInt("Size");
            trailPositions.clear();
            for (int i = 0; i < size; i++) {
                double x = trailNbt.getDouble("X" + i);
                double y = trailNbt.getDouble("Y" + i);
                double z = trailNbt.getDouble("Z" + i);
                trailPositions.add(new Vec3d(x, y, z));
            }
        }

    }

    // ================= 碰撞相关方法 =================

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
    public boolean isAlive() {
        return !this.isRemoved();
    }

    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {
        // 空实现
    }

    @Override
    protected void onBlockCollision(BlockState state) {
        // 空实现
    }

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
    protected void checkBlockCollision() {
        // 空实现
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
    public boolean canHit() {
        return true;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
        // 空实现
    }

    @Override
    public boolean hasNoGravity() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

}