package com.puzzle_rain.entity;

import com.puzzle_rain.ControlEnum;
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
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FlyingBlockEntity extends Entity {
    public int blockState11=11;
    private int age = 0;
    private int maxAge = 20000; // 1000秒后自动消失，防止内存泄漏

    public static final TrackedData<Integer> BLOCK_STATE_ID = DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public FlyingBlockEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);

    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID,blockState11);
    }


    @Override
    public void onTrackedDataSet(TrackedData<?> data) {

    }



    @Override
    protected void pushOutOfBlocks(double x, double y, double z) {

    }

    @Override
    protected void onBlockCollision(BlockState state) {

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

    public FlyingBlockEntity(World world, BlockPos pos, BlockState blockState) {

        super(ModEntities.FLYING_BLOCK_ENTITY, world);
        this.setPosition(Vec3d.ofCenter(pos));
        this.blockState11 = Block.getRawIdFromState(blockState);
        this.dataTracker.set(BLOCK_STATE_ID,Block.getRawIdFromState(blockState));
        this.noClip=true;
    }

    public void setBlockState(BlockState blockState) {

        this.blockState11 = Block.getRawIdFromState(blockState);
        if (!this.getWorld().isClient()) {
            //this.dataTracker.set(BLOCK_STATE_ID, Block.getRawIdFromState(blockState));
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
            nbt.putInt("BlockState", this.blockState11);

    }
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("BlockState")) {
            this.blockState11 = nbt.getInt("BlockState");
        }
    }

    public BlockState getBlockState() {
        return Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;


        // 自动移除旧实体防止内存泄漏
        if (this.age > maxAge) {
            this.discard();
            return;
        }

        // 更新位置（基于速度）
        if (this.getVelocity().lengthSquared() > 0.001) {
            this.move(MovementType.SELF, this.getVelocity());
        }
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
        return true; // 没有重力
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved();
    }
}
