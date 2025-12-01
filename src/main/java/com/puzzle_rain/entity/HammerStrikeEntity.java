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
import java.util.Collections;
import java.util.List;

public class HammerStrikeEntity extends Entity {
    private BlockPos centerPos;
    private float radius;
    private int age = 0;
    private int phase = 0; // 0:冲击波, 1:内爆与连续爆炸
    private List<FlyingBlockEntity> flyingBlocks = new ArrayList<>();
    private List<BlockPos> affectedBlocks = new ArrayList<>();

    // 用于延迟处理的字段
    private List<BlockPos> remainingBlocksToProcess = new ArrayList<>();
    private static final int MAX_BLOCKS_PER_TICK = 200; // Increased to process more blocks per tick for larger radii

    // 用于控制爆炸的字段
    private boolean allBlocksProcessed = false;
    private static final double CONVERGENCE_THRESHOLD = 1.5; // Increased threshold for more lenient center detection

    // 动态区块检测相关
    private boolean initialCollectionDone = false; // 是否已完成初始收集
    private int radiusInt=40; // 预计算半径整数值

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
        this.radius = radius; // 不限制最大半径
        this.radiusInt = (int) Math.ceil(radius); // 预计算半径整数值
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

        // 收集范围内的方块 - 使用更精确的算法来 include all blocks within radius
        int radiusInt = (int) Math.ceil(radius);

        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distanceSquared = x * x + y * y + z * z;

                    // Check if this position is within the sphere radius
                    if (distanceSquared <= radius * radius + 0.5) { // Add small buffer to ensure edge blocks aren't missed
                        BlockPos pos = centerPos.add(x, y, z);

                        // 检查位置是否有效（在世界边界内）和是否已加载
                        if (getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4) &&
                            getWorld().isInBuildLimit(pos)) {

                            if (!getWorld().isAir(pos)) {
                                affectedBlocks.add(pos.toImmutable()); // 使用不可变位置
                            }
                        }
                    }
                }
            }
        }

        PuzzleRain.LOGGER.info("Hammer strike initialized with {} blocks in range", affectedBlocks.size());

        // Initialize remaining blocks to process for delayed generation
        this.remainingBlocksToProcess = new ArrayList<>(affectedBlocks);
        Collections.shuffle(this.remainingBlocksToProcess); // 随机化处理顺序
        this.initialCollectionDone = true; // 标记初始收集已完成

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

        switch (phase) {
            case 0: // 冲击波阶段
                tickShockwave();
                break;
            case 1: // 内爆与连续爆炸阶段
                // Dynamically collect blocks from newly loaded chunks
                if (initialCollectionDone) {
                    collectNewlyLoadedBlocks();
                }
                processBlocksGradually();
                tickImplosion();
                checkAndExplodeBlocks(); // Continuously check for blocks to explode
                break;
            default:
                discard();
                break;
        }

        age++;

        // Transition to phase 1 (main phase) after shockwave
        if (phase == 0 && age >= SHOCKWAVE_DURATION) {
            phase = 1;
            age = 0;
        }

        // Discard when all blocks are processed and no flying blocks remain
        if (phase == 1 && allBlocksProcessed && flyingBlocks.isEmpty()) {
            PuzzleRain.LOGGER.info("Hammer strike completed successfully - all {} blocks processed and exploded", affectedBlocks.size());
            discard();
        }

        // Also discard if we've gone on too long to prevent infinite loops
        // Allow time based on the number of blocks to process (larger radii have more blocks)
        int maxProcessingTime = SHOCKWAVE_DURATION + (int)(IMPLOSION_DURATION * 5 + radius * 20); // Scale with radius more generously
        if (age > maxProcessingTime) {
            PuzzleRain.LOGGER.warn("Hammer strike timed out - {} blocks processed, {} flying blocks still active",
                affectedBlocks.size() - remainingBlocksToProcess.size(), flyingBlocks.size());
            discard();
        }
    }

    private void tickShockwave() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        double progress = (double) age / SHOCKWAVE_DURATION;
        double currentRadius = progress * radius;

        // 球状冲击波粒子 - optimized to reduce particle count for performance
        int particleCount = Math.max(5, (int)(15 * (radius / 5.0))); // Scale with radius but keep it reasonable

        for (int i = 0; i < particleCount; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double x = currentRadius * Math.sin(phi) * Math.cos(theta);
            double y = currentRadius * Math.sin(phi) * Math.sin(theta);
            double z = currentRadius * Math.cos(phi);

            BlockPos particlePos = centerPos.add((int) Math.round(x), (int) Math.round(y), (int) Math.round(z));

            // 检查位置是否有效 - only check every other particle to reduce load
            if (i % 2 == 0 && !serverWorld.isPosLoaded(particlePos.getX(), particlePos.getZ())) {
                continue;
            }

            BlockState blockState = serverWorld.getBlockState(particlePos.down().toImmutable());

            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState),
                    centerPos.getX() + x, centerPos.getY() + y, centerPos.getZ() + z,
                    1, 0.1, 0.1, 0.1, 0.05
            );
        }

        if (age >= SHOCKWAVE_DURATION) {
            phase = 1;
            age = 0;
            // In the new approach, the implosion phase starts automatically with the main tick loop
        }
    }


    // Process blocks gradually during tick
    private void processBlocksGradually() {
        if (getWorld().isClient() || centerPos == null) {
            return;
        }

        // Process remaining blocks if any
        if (!remainingBlocksToProcess.isEmpty()) {
            int blocksProcessedThisCall = 0;
            int attempts = 0;
            // Increase processing rate significantly to handle all blocks faster
            final int maxBlocksThisTick = Math.min(MAX_BLOCKS_PER_TICK * 4, remainingBlocksToProcess.size()); // Process more blocks per tick
            final int maxAttempts = maxBlocksThisTick * 3; // Limit attempts to prevent infinite loops

            // Continue processing until we've processed up to MAX_BLOCKS_PER_TICK blocks
            // or we've exhausted the remaining blocks
            while (blocksProcessedThisCall < maxBlocksThisTick
                   && !remainingBlocksToProcess.isEmpty()
                   && attempts < maxAttempts) {

                BlockPos pos = remainingBlocksToProcess.remove(0); // Remove from front of list
                attempts++;

                // 检查位置是否有效（现在使用更宽松的检查）
                if (!getWorld().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    // If chunk is not loaded, put it back at the end to try later
                    remainingBlocksToProcess.add(pos);
                    continue;
                }

                BlockState blockState = getWorld().getBlockState(pos);
                if (blockState.isAir()) {
                    // If block is now air, skip it (it might have been broken by something else)
                    blocksProcessedThisCall++; // Count as processed to avoid infinite loops
                    continue;
                }

                try {
                    getWorld().breakBlock(pos, false); // 移除原方块，不掉落物品

                    FlyingBlockEntity flyingBlock = new FlyingBlockEntity(getWorld(), pos, blockState);
                    flyingBlock.setImplosionTarget(Vec3d.ofCenter(centerPos));
                    flyingBlock.setMovementState(FlyingBlockEntity.MovementState.IMPLOSION);
                    getWorld().spawnEntity(flyingBlock);
                    flyingBlocks.add(flyingBlock);

                    blocksProcessedThisCall++; // Only increment when we actually process a block
                } catch (Exception e) {
                    PuzzleRain.LOGGER.error("Error creating flying block at " + pos, e);
                    blocksProcessedThisCall++; // Still count as processed to avoid endless retries
                }
            }

            // Log progress periodically
            if (age % 20 == 0) {
                PuzzleRain.LOGGER.info("Hammer strike progress: {} blocks remaining to process", remainingBlocksToProcess.size());
            }
        } else if (!allBlocksProcessed) {
            // All blocks have been processed and created as flying entities
            allBlocksProcessed = true;
            PuzzleRain.LOGGER.info("All {} blocks processed for hammer strike", affectedBlocks.size());
        }
    }

    private void tickImplosion() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        // 向内收缩的粒子效果 - optimized for performance
        if (age % 2 == 0) { // Only run every other tick to reduce load
            double progress = 1.0 - (double) age / IMPLOSION_DURATION;
            double currentRadius = progress * radius;

            int particleCount = Math.max(3, (int)(10 * (radius / 5.0))); // Scale with radius but keep it reasonable

            for (int i = 0; i < particleCount; i++) {
                double theta = Math.random() * Math.PI * 2;
                double x = currentRadius * Math.cos(theta);
                double z = currentRadius * Math.sin(theta);
                double y = (Math.random() * radius * 2 - radius) * 0.5; // Reduced vertical spread for efficiency

                serverWorld.spawnParticles(
                        ParticleTypes.SMOKE,
                        centerPos.getX() + x, centerPos.getY() + y, centerPos.getZ() + z,
                        1, 0.1, 0.1, 0.1, 0.05
                );
            }
        }
    }


    private int chunkCheckCounter = 0; // To limit how often we check chunks

    // Dynamically collect blocks from newly loaded chunks (more efficient approach)
    private void collectNewlyLoadedBlocks() {
        if (getWorld().isClient()) {
            return;
        }

        // Only run chunk loading checks every few ticks to reduce performance impact
        chunkCheckCounter++;
        if (chunkCheckCounter % 5 != 0) { // Check every 5 ticks
            return;
        }

        // Calculate the chunk range that could contain blocks in our radius
        int minChunkX = (centerPos.getX() - radiusInt) >> 4;
        int maxChunkX = (centerPos.getX() + radiusInt) >> 4;
        int minChunkZ = (centerPos.getZ() - radiusInt) >> 4;
        int maxChunkZ = (centerPos.getZ() + radiusInt) >> 4;

        // Check chunks in the range
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkPos = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);

                // Only process if chunk is loaded and we haven't checked it recently
                if (getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    // Process the chunk to look for unprocessed blocks
                    processChunkForNewBlocks(chunkX, chunkZ);
                }
            }
        }

        // Reset the allBlocksProcessed flag if we added new blocks
        if (!remainingBlocksToProcess.isEmpty()) {
            allBlocksProcessed = false;
        }
    }

    private void processChunkForNewBlocks(int chunkX, int chunkZ) {
        // Check all blocks in this chunk that are within the radius
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;

        // Calculate the valid Y range for this chunk based on the radius
        int minY = Math.max(getWorld().getBottomY(), centerPos.getY() - radiusInt);
        int maxY = Math.min(getWorld().getTopY(), centerPos.getY() + radiusInt);

        BlockPos center = this.centerPos;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) { // Only check Y range within radius
                    double dx = x - center.getX();
                    double dz = z - center.getZ();
                    double dy = y - center.getY();
                    double distanceSquared = dx * dx + dy * dy + dz * dz;

                    // Check if this position is within the sphere radius
                    if (distanceSquared <= radius * radius + 0.5) { // Add small buffer
                        BlockPos pos = new BlockPos(x, y, z);

                        // Check if this block is not air and not already in affectedBlocks
                        if (!getWorld().isAir(pos)) {
                            // Use a more efficient check for existing positions
                            boolean alreadyProcessed = false;
                            for (BlockPos existingPos : affectedBlocks) {
                                if (existingPos.equals(pos)) {
                                    alreadyProcessed = true;
                                    break;
                                }
                            }

                            if (!alreadyProcessed) {
                                // Add this newly discovered block to processing queue
                                affectedBlocks.add(pos.toImmutable());
                                remainingBlocksToProcess.add(pos.toImmutable());

                                PuzzleRain.LOGGER.debug("Discovered new block at {} during dynamic collection", pos);
                            }
                        }
                    }
                }
            }
        }
    }

    // Check for blocks that have reached the center and explode them
    private void checkAndExplodeBlocks() {
        if (flyingBlocks.isEmpty()) {
            return;
        }

        // Process all blocks that have reached the center (no limit)
        for (int i = flyingBlocks.size() - 1; i >= 0; i--) {
            FlyingBlockEntity flyingBlock = flyingBlocks.get(i);

            if (flyingBlock != null && !flyingBlock.isRemoved()) {
                double distanceToCenter = flyingBlock.getPos().distanceTo(centerPos.toCenterPos());

                if (distanceToCenter < CONVERGENCE_THRESHOLD) {
                    // Block has reached the center, make it explode
                    try {
                        // Position at exact center for consistent visual effect
                        flyingBlock.setPosition(centerPos.toCenterPos());
                        flyingBlock.setMovementState(FlyingBlockEntity.MovementState.EXPLOSION);

                        // Explosive velocity away from center
                        Vec3d randomDir = new Vec3d(
                                Math.random() * 2 - 1, // -1 to 1
                                Math.random() * 2 - 1, // -1 to 1
                                Math.random() * 2 - 1  // -1 to 1
                        ).normalize().multiply(3.0 + Math.random() * 4.0);

                        flyingBlock.setVelocity(randomDir);

                        // Remove from flying blocks list since it's now in explosion state
                        flyingBlocks.remove(i);

                    } catch (Exception e) {
                        PuzzleRain.LOGGER.error("Error triggering explosion for flying block", e);
                    }
                }
            }
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