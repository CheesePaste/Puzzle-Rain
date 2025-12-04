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
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public class FollowingEntity extends BaseBlockEntity implements Targetable {

    // 定义一个追踪数据，用来存目标的 Entity ID (Integer)
    // 这里的 0 是默认值，代表没有目标
    private static final TrackedData<Integer> TARGET_ID = DataTracker.registerData(FollowingEntity.class, TrackedDataHandlerRegistry.INTEGER);

    // 辅助变量，用于在加载NBT时临时存储UUID，直到世界加载完毕找到实体
    private UUID targetUuid;

    float closeDistance = 1;

    public FollowingEntity(EntityType<?> type, World world, Entity target, BlockPos pos, BlockState state) {
        super(type, world, pos, state);
        this.setTarget(target);
    }

    public FollowingEntity(EntityType<FollowingEntity> followingEntityEntityType, World world) {
        super(followingEntityEntityType, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        // 初始化数据，默认为 -1 或 0 (代表无目标)
        builder.add(TARGET_ID, 0);
    }

    // 设置目标的辅助方法
    public void setTarget(Entity entity) {
        if (entity != null) {
            this.dataTracker.set(TARGET_ID, entity.getId());
            this.targetUuid = entity.getUuid(); // 同时记录UUID用于保存
        }
    }

    // 获取目标的方法
    public Entity getTarget() {
        int id = this.dataTracker.get(TARGET_ID);
        if (id == 0) return null;
        return this.getWorld().getEntityById(id);
    }

    @Override
    public void tick() {
        // 如果是从磁盘加载的，且有UUID但还没有找到实体ID（因为世界刚加载），尝试通过UUID找回实体
        if (!this.getWorld().isClient && this.targetUuid != null && this.dataTracker.get(TARGET_ID) == 0) {
            if (this.getWorld() instanceof ServerWorld serverWorld) {
                Entity e = serverWorld.getEntity(this.targetUuid);
                if (e != null) {
                    this.setTarget(e);
                }
            }
        }

        Entity target = getTarget(); // 使用 getter 获取同步后的目标

        if (target != null) {
            // 服务器端逻辑
            if (!this.getWorld().isClient) {
                this.addVelocity(this.getDir().multiply(0.1));
                this.move(MovementType.SELF, this.getDir());
            }
            // 客户端也可以获取到 target 引用进行渲染插值（如果需要）
        } else {
            // 只有在真的没有目标时才会进入这里
            PuzzleRain.LOGGER.info("No Target");
        }

        super.tick();
    }

    @Override
    public Vec3d getDir() {
        Entity target = getTarget();
        if (target != null) {
            return target.getPos().subtract(this.getPos()).normalize();
        }
        return Vec3d.ZERO; // 避免返回 null 导致崩溃
    }

    @Override
    public boolean isClose() {
        Entity target = getTarget();
        if (target != null && this.getPos().isWithinRangeOf(target.getPos(), closeDistance, closeDistance)) {
            return true;
        }
        // 不要在这里抛出 RuntimeException，游戏里任何情况都可能发生（比如目标掉线/死亡），直接返回 false 更安全
        return false;
    }

    // --- NBT 保存与读取 (持久化) ---

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        // 保存目标的 UUID，因为 Entity ID 重启后会变，UUID 不会变
        if (this.targetUuid != null) {
            nbt.putUuid("TargetUuid", this.targetUuid);
        } else {
            // 如果当前有这种逻辑，也可以尝试从 getTarget() 获取 UUID
            Entity t = getTarget();
            if (t != null) {
                nbt.putUuid("TargetUuid", t.getUuid());
            }
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("TargetUuid")) {
            this.targetUuid = nbt.getUuid("TargetUuid");
        }
    }
}