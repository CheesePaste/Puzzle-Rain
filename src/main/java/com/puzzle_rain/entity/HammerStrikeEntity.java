package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
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

public class HammerStrikeEntity extends Entity {
    private BlockPos centerPos;
    private float radius;
    private int age = 0;
    private int phase = 0; // 0:冲击波, 1:内爆, 2:爆炸
    private List<FlyingBlockEntity> flyingBlocks = new ArrayList<>();
    private List<BlockPos> affectedBlocks = new ArrayList<>();

    // 阶段时间配置
    private static final int SHOCKWAVE_DURATION = 10;
    private static final int IMPLOSION_DURATION = 20;
    private static final int EXPLOSION_DURATION = 10;

    public HammerStrikeEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        // 不需要数据跟踪器，因为这个实体主要是逻辑控制
    }

    public HammerStrikeEntity(World world, BlockPos center, float radius) {
        super(ModEntities.HAMMER_STRIKE_ENTITY, world);
        this.centerPos = center;
        this.radius = radius;
        this.setPosition(Vec3d.ofCenter(center));

        // 只在服务端初始化
        if (!world.isClient()) {
            initializeStrike();
        }
    }

    private void initializeStrike() {
        // 防御性检查
        if (centerPos == null || getWorld() == null) {
            PuzzleRain.LOGGER.error("HammerStrikeEntity initialized with null centerPos or world");
            this.discard();
            return;
        }

        // 收集范围内的方块
        int radiusInt = (int) Math.ceil(radius);
        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    BlockPos pos = centerPos.add(x, y, z);

                    // 检查位置是否有效
                    if (!getWorld().isPosLoaded(pos.getX(), pos.getZ())) {
                        continue;
                    }

                    double distance = Math.sqrt(x*x + y*y + z*z);
                    if (distance <= radius && !getWorld().isAir(pos)) {
                        affectedBlocks.add(pos.toImmutable()); // 使用不可变位置
                    }
                }
            }
        }

        // 只在服务端播放音效
        if (!getWorld().isClient()) {
            getWorld().playSound(null, centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0F, 0.8F);
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 防御性检查
        if (this.getWorld() == null || this.isRemoved()) {
            this.discard();
            return;
        }

        // 只在服务端执行逻辑
        if (this.getWorld().isClient()) {
            return;
        }

        age++;

        switch (phase) {
            case 0: // 冲击波阶段
                tickShockwave();
                break;
            case 1: // 内爆阶段
                tickImplosion();
                break;
            case 2: // 爆炸阶段
                tickExplosion();
                break;
            default:
                discard();
                break;
        }

        if (age > SHOCKWAVE_DURATION + IMPLOSION_DURATION + EXPLOSION_DURATION) {
            discard();
        }
    }

    private void tickShockwave() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        double progress = (double) age / SHOCKWAVE_DURATION;
        double currentRadius = progress * radius;

        // 球状冲击波粒子
        for (int i = 0; i < 50; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double x = currentRadius * Math.sin(phi) * Math.cos(theta);
            double y = currentRadius * Math.sin(phi) * Math.sin(theta);
            double z = currentRadius * Math.cos(phi);

            BlockPos particlePos = centerPos.add((int) x, (int) y, (int) z);

            // 检查位置是否有效
            if (!serverWorld.isPosLoaded(particlePos.getX(), particlePos.getZ())) {
                continue;
            }

            BlockState blockState = serverWorld.getBlockState(particlePos.down());

            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                    particlePos.getX(), particlePos.getY(), particlePos.getZ(),
                    1, 0.1, 0.1, 0.1, 0.05
            );
        }

        if (age >= SHOCKWAVE_DURATION) {
            phase = 1;
            age = 0;
            startImplosion();
        }
    }

    private void startImplosion() {
        if (getWorld().isClient() || centerPos == null) {
            return;
        }

        // 创建飞行方块实体
        for (BlockPos pos : affectedBlocks) {
            // 检查位置是否有效
            if (!getWorld().isPosLoaded(pos.getX(), pos.getZ())) {
                continue;
            }

            BlockState blockState = getWorld().getBlockState(pos);
            if (blockState.isAir()) {
                continue;
            }

            try {
                getWorld().breakBlock(pos, false); // 移除原方块，不掉落物品

                FlyingBlockEntity flyingBlock = new FlyingBlockEntity(getWorld(), pos, blockState);
                flyingBlock.setImplosionTarget(Vec3d.ofCenter(centerPos));
                flyingBlock.setMovementState(FlyingBlockEntity.MovementState.IMPLOSION);
                getWorld().spawnEntity(flyingBlock);
                flyingBlocks.add(flyingBlock);
            } catch (Exception e) {
                PuzzleRain.LOGGER.error("Error creating flying block at " + pos, e);
            }
        }

        // 播放内爆音效
        getWorld().playSound(null, centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                SoundEvents.BLOCK_CONDUIT_AMBIENT, SoundCategory.BLOCKS, 1.5F, 0.5F);
    }

    private void tickImplosion() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // 向内收缩的粒子效果
        double progress = 1.0 - (double) age / IMPLOSION_DURATION;
        double currentRadius = progress * radius;

        for (int i = 0; i < 30; i++) {
            double theta = Math.random() * Math.PI * 2;
            double x = currentRadius * Math.cos(theta);
            double z = currentRadius * Math.sin(theta);
            double y = Math.random() * radius * 2 - radius;

            serverWorld.spawnParticles(
                    ParticleTypes.SMOKE,
                    centerPos.getX() + x, centerPos.getY() + y, centerPos.getZ() + z,
                    1, 0.1, 0.1, 0.1, 0.05
            );
        }

        if (age >= IMPLOSION_DURATION) {
            phase = 2;
            age = 0;
            startExplosion();
        }
    }

    private void startExplosion() {
        if (getWorld().isClient()) {
            return;
        }

        // 触发核心爆炸
        for (FlyingBlockEntity flyingBlock : flyingBlocks) {
            if (flyingBlock != null && flyingBlock.isAlive()) {
                try {
                    flyingBlock.setMovementState(FlyingBlockEntity.MovementState.EXPLOSION);

                    // 随机爆炸方向
                    Vec3d randomDir = new Vec3d(
                            Math.random() - 0.5,
                            Math.random() - 0.5,
                            Math.random() - 0.5
                    ).normalize().multiply(2.0 + Math.random() * 3.0);

                    flyingBlock.setVelocity(randomDir);
                } catch (Exception e) {
                    PuzzleRain.LOGGER.error("Error triggering explosion for flying block", e);
                }
            }
        }

        // 播放爆炸音效
        getWorld().playSound(null, centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.0F, 1.2F);
    }

    private void tickExplosion() {
        if (!(getWorld() instanceof ServerWorld serverWorld) || age >= 5) {
            return;
        }

        // 核心闪光
        serverWorld.spawnParticles(
                ParticleTypes.FLASH,
                centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                5, 0.5, 0.5, 0.5, 0
        );

        // 冲击环
        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 0.5 + Math.random() * 0.5;
            serverWorld.spawnParticles(
                    ParticleTypes.CLOUD,
                    centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                    0, Math.cos(angle) * speed, 0.1, Math.sin(angle) * speed, 0.1
            );
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // 读取保存的数据
        if (nbt.contains("CenterX") && nbt.contains("CenterY") && nbt.contains("CenterZ")) {
            this.centerPos = new BlockPos(
                    nbt.getInt("CenterX"),
                    nbt.getInt("CenterY"),
                    nbt.getInt("CenterZ")
            );
        }
        this.radius = nbt.getFloat("Radius");
        this.age = nbt.getInt("Age");
        this.phase = nbt.getInt("Phase");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        // 保存必要的数据
        if (centerPos != null) {
            nbt.putInt("CenterX", centerPos.getX());
            nbt.putInt("CenterY", centerPos.getY());
            nbt.putInt("CenterZ", centerPos.getZ());
        }
        nbt.putFloat("Radius", radius);
        nbt.putInt("Age", age);
        nbt.putInt("Phase", phase);
    }
}