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
            // Phase 1: 存储原始方块并破坏
            List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
            List<BlockState> originalStates = new ArrayList<>();

            for (BlockPos pos : originalPositions) {
                BlockState state = world.getBlockState(pos);
                originalStates.add(state);
                world.breakBlock(pos, false);
            }

            // 短暂延迟确保方块破坏完成
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Phase 2: 创建飞行动画
            PuzzleRain puzzleRain = PuzzleRain.getInstance();

            for (int i = 0; i < originalPositions.size(); i++) {
                BlockPos targetPos = originalPositions.get(i);
                BlockState state = originalStates.get(i);

                // 计算起始位置
                Vec3d boundsCenter = new Vec3d(
                        bounds.getCenter().getX() + 0.5,
                        bounds.getCenter().getY() + 0.5,
                        bounds.getCenter().getZ() + 0.5
                );

                int xSize = bounds.getMax().getX() - bounds.getMin().getX();
                int zSize = bounds.getMax().getZ() - bounds.getMin().getZ();
                double spread = Math.max(xSize, zSize) * 0.6;

                Vec3d startPos = boundsCenter.add(
                        world.random.nextDouble() * spread - spread * 0.5,
                        10 + world.random.nextDouble() * 8,
                        world.random.nextDouble() * spread - spread * 0.5
                );

                Vec3d targetVec = new Vec3d(
                        targetPos.getX() + 0.5,
                        targetPos.getY() + 0.5,
                        targetPos.getZ() + 0.5
                );

                // 使用自定义实体而不是 FallingBlockEntity
                puzzleRain.addFlyingAnimation(world, startPos, targetVec, state);

                // 分组延迟
                if (i % 4 == 0) {
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

        } catch (Exception e) {
            throw new CompletionException("Animation failed", e);
        }



    }




}