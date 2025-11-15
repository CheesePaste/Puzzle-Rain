package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class BlockTransitionTask {
    private final ServerWorld world;
    private final BlockBounds bounds;
    private final List<BlockPos> allPositions;
    private final List<BlockPos> nonAirPositions;

    private final String taskId;

    private BlockTransitionTask(ServerWorld world, BlockBounds bounds, String taskId) {
        this.world = world;
        this.bounds = bounds;
        this.taskId = taskId;

        // 获取所有位置
        this.allPositions = new ArrayList<>(bounds.getAllBlockPositions());
        Collections.shuffle(allPositions); // 随机化顺序

        // 获取非空气方块位置
        this.nonAirPositions = new ArrayList<>();
        for (BlockPos pos : allPositions) {
            if (!world.getBlockState(pos).isAir()) {
                nonAirPositions.add(pos);
            }
        }
    }

    public static BlockTransitionTask create(ServerWorld world, BlockBounds bounds, String taskId) {
        return new BlockTransitionTask(world, bounds, taskId);
    }

    public CompletableFuture<Void> execute() {
        return CompletableFuture.runAsync(this::performAnimation);
    }

    private void performAnimation() {
        try {
            // Phase 1: Store original blocks and break them
            List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
            List<BlockState> originalStates = new ArrayList<>();

            for (BlockPos pos : originalPositions) {
                BlockState state = world.getBlockState(pos);
                originalStates.add(state);
                // Break the block but don't drop items
                world.breakBlock(pos, false);
            }

            // Phase 2: Create flying block entities and register them with the animation system
            PuzzleRain puzzleRain = PuzzleRain.getInstance();

            for (int i = 0; i < originalPositions.size(); i++) {
                BlockPos targetPos = originalPositions.get(i);
                BlockState state = originalStates.get(i);

                // Calculate start position above the center of the bounds
                Vec3d startPos = new Vec3d(
                    bounds.getCenter().getX() + world.random.nextInt(5) - 2, // Random offset around center
                    bounds.getCenter().getY() + 15,
                    bounds.getCenter().getZ() + world.random.nextInt(5) - 2
                );

                Vec3d targetVec = new Vec3d(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5
                );

                // Create a temporary block to spawn the falling block entity
                BlockPos tempPos = new BlockPos((int) startPos.x, (int) startPos.y, (int) startPos.z);
                world.setBlockState(tempPos, state);

                FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, tempPos, state);

                // Remove the temporary block
                world.breakBlock(tempPos, false);

                // Configure the entity
                fallingBlock.setPosition(startPos);
                fallingBlock.setNoGravity(true);
                fallingBlock.setVelocity(0, 0, 0);
                fallingBlock.setHurtEntities(0.0f, 0);

                // Register the animation with the main puzzle rain instance
                puzzleRain.addFlyingAnimation(world, fallingBlock, startPos, targetVec, state);
            }

        } catch (Exception e) {
            throw new CompletionException("Animation failed", e);
        }
    }



}