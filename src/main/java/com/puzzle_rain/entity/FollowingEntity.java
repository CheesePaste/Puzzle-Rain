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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public class FollowingEntity extends BaseBlockEntity implements Targetable{
    Entity target;
    private static final TrackedData<Optional<UUID>> TARGET_UUID =
            DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    float closeDistance=1;
    public FollowingEntity(EntityType<?> type, World world, Entity target, BlockPos pos, BlockState state) {

        super(type, world,pos,state);
        this.target=target;
    }

    public FollowingEntity(EntityType<FollowingEntity> followingEntityEntityType, World world) {
        super(followingEntityEntityType,world);
    }


    @Override
    protected void initDataTracker( DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TARGET_UUID, Optional.empty());
    }

    // 添加设置目标的方法
    public void setTarget(Entity target) {
        this.target = target;
        if (target != null) {
            this.dataTracker.set(TARGET_UUID, Optional.of(target.getUuid()));
        } else {
            this.dataTracker.set(TARGET_UUID, Optional.empty());
        }
    }

    // 从数据追踪器重新获取目标
    private void refreshTarget() {
        //if (this.getWorld().isClient) return; // 只在服务器上执行

        Optional<UUID> uuid = this.dataTracker.get(TARGET_UUID);
        if (uuid.isPresent()) {
            Entity entity = this.getWorld().getPlayerByUuid(uuid.get());
            if (entity != null && entity.isAlive()) {
                this.target = entity;
            } else {
                this.target = null;
                this.dataTracker.set(TARGET_UUID, Optional.empty());
            }
        }
    }
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.target != null) {
            nbt.putUuid("Target", this.target.getUuid());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Target")) {
            UUID targetUuid = nbt.getUuid("Target");
            this.dataTracker.set(TARGET_UUID, Optional.of(targetUuid));

            // 服务器端尝试获取实体引用
            if (true) {
                Entity entity = this.getWorld().getPlayerByUuid(targetUuid);
                if (entity != null && entity.isAlive()) {
                    this.target = entity;
                }
            }
        }
    }

    @Override
    public void tick() {

        super.tick();
        // 只在服务器端执行逻辑
        //if (!this.getWorld().isClient) return;

        // 确保目标是最新的
        if (this.target == null || !this.target.isAlive()) {
            refreshTarget();
        }

        PuzzleRain.LOGGER.info("%s::::NULL:%s".formatted(this.getWorld().isClient,target==null));
        if (this.target != null) {
            PuzzleRain.LOGGER.info(target.getName().getString());
            this.addVelocity(this.getDir().multiply(1f));
            this.move(MovementType.SELF, this.getVelocity());
        }
        this.velocityDirty=true;
        //this.move(MovementType.SELF, new Vec3d(0,1,0));

    }

    @Override
    public boolean isNoClip() {
        return false;
    }

    @Override
    public Vec3d getDir() {
        if(target!=null){
            return target.getPos().subtract(this.getPos()).normalize();
        }
        return null;
    }

    @Override
    public boolean isClose() {
        if(target!=null&&this.getPos().isWithinRangeOf(target.getPos(),closeDistance,closeDistance)){
            return true;
        }
        if(target==null){
            throw new RuntimeException("Target is null");
        }
        return false;
    }
}
