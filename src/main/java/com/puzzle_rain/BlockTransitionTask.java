package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class BlockTransitionTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockTransitionTask.class);

    private final ServerWorld world;
    private final List<BlockPos> allPositions;
    private final List<BlockPos> nonAirPositions;
    private final BlockBounds bounds;
    private final String taskId;

    public BlockTransitionTask(ServerWorld world, BlockBounds bounds, String taskId) {
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
            List<BlockPos> positions = new ArrayList<>(nonAirPositions);
            List<BlockState> states = new ArrayList<>();

            LOGGER.info("Starting puzzle rain animation for {} blocks", positions.size());

            // 破坏方块并记录状态
            for (BlockPos pos : positions) {
                BlockState state = world.getBlockState(pos);
                states.add(state);
                world.breakBlock(pos, false);
            }

            // 短暂延迟确保方块破坏完成
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 设置动画数据
            PuzzleRain.getInstance().setupAnimation(world, bounds, positions, states);

            LOGGER.info("Animation setup complete, {} blocks ready to fly", positions.size());

        } catch (Exception e) {
            throw new CompletionException("Animation failed", e);
        }
    }

    public int getTotalBlocks() {
        return nonAirPositions.size();
    }

    public int getRemainingBlocks() {
        return PuzzleRain.getInstance().getPendingBlockCount() + PuzzleRain.getInstance().getCurrentFlyingCount();
    }

    public boolean isComplete() {
        return PuzzleRain.getInstance().isAnimationComplete();
    }

    public float getProgress() {
        return PuzzleRain.getInstance().getAnimationProgress();
    }
}