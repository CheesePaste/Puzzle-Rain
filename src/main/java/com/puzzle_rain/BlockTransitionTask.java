package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class BlockTransitionTask {

    private final ServerWorld world;
    private final BlockBounds bounds;
    private final List<BlockPos> allPositions;
    private final List<BlockPos> nonAirPositions;

    private BlockPos center;
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
        int count = 0;
        double avgX = 0, avgY = 0, avgZ = 0;

        for (BlockPos pos : allPositions) {
            if (PuzzleRain.config.ignoreBlocks.stream().anyMatch(
                    i -> world.getBlockState(pos).getBlock() == Registries.BLOCK.get(Identifier.of(i)))) {
                continue;
            }

            count++;
            // 增量更新平均值: new_avg = old_avg + (new_value - old_avg) / count
            avgX += (pos.getX() - avgX) / count;
            avgY += (pos.getY() - avgY) / count;
            avgZ += (pos.getZ() - avgZ) / count;

            nonAirPositions.add(pos);
        }

        center = count > 0 ?
                new BlockPos((int)avgX, (int)avgY, (int)avgZ) :
                BlockPos.ORIGIN;
    }

    public static BlockTransitionTask create(ServerWorld world, BlockBounds bounds, String taskId) {
        return new BlockTransitionTask(world, bounds, taskId);
    }

    public CompletableFuture<Void> execute() {
        return CompletableFuture.runAsync(this::performAnimation);
    }

//    private void performAnimation() {
//        try {
//            // Phase 1: 存储原始方块并破坏
//            List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
//            List<BlockState> originalStates = new ArrayList<>();
//
//            for (BlockPos pos : originalPositions) {
//                BlockState state = world.getBlockState(pos);
//                originalStates.add(state);
//                world.breakBlock(pos, false);
//            }
//
//            // 短暂延迟确保方块破坏完成
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                return;
//            }
//
//            // Phase 2: 创建飞行动画（现在会分批生成）
//            PuzzleRain puzzleRain = PuzzleRain.getInstance();
//
//            for (int i = 0; i < originalPositions.size(); i++) {
//                BlockPos targetPos = originalPositions.get(i);
//                BlockState state = originalStates.get(i);
//
//                // 计算起始位置
//                Vec3d boundsCenter = new Vec3d(
//                        bounds.getCenter().getX() + 0.5,
//                        bounds.getCenter().getY() + 0.5,
//                        bounds.getCenter().getZ() + 0.5
//                );
//
//                int xSize = bounds.getMax().getX() - bounds.getMin().getX();
//                int zSize = bounds.getMax().getZ() - bounds.getMin().getZ();
//                double spread = Math.max(xSize, zSize) * 0.6;
//
//                Random r = new Random();
//                Vec3d startPos = boundsCenter.add(
//                        r.nextDouble() * spread - spread * 0.5,
//                        10 + r.nextDouble() * 8,
//                        r.nextDouble() * spread - spread * 0.5
//                );
//
//                Vec3d targetVec = new Vec3d(
//                        targetPos.getX() + 0.5,
//                        targetPos.getY() + 0.5,
//                        targetPos.getZ() + 0.5
//                );
//
//                // 使用新的队列系统添加动画
//                puzzleRain.addFlyingAnimation(world, startPos, targetVec, state);
//
//                // 分组延迟，避免一次性生成太多请求
//                if (i % 4 == 0) {
//                    try {
//                        Thread.sleep(20); // 减少延迟时间，因为现在有队列控制
//                    } catch (InterruptedException e) {
//                        Thread.currentThread().interrupt();
//                        return;
//                    }
//                }
//            }
//
//            PuzzleRain.LOGGER.info("Animation task completed with {} flying block requests", originalPositions.size());
//
//        } catch (Exception e) {
//            throw new CompletionException("Animation failed", e);
//        }
//
//
//
//    }
//private void performAnimation() {
//    try {
//        // Phase 1: 存储原始方块并破坏
//        List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
//        List<BlockState> originalStates = new ArrayList<>();
//
//        for (BlockPos pos : originalPositions) {
//            BlockState state = world.getBlockState(pos);
//            originalStates.add(state);
//            world.breakBlock(pos, false);
//        }
//
//        // 短暂延迟确保方块破坏完成
//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return;
//        }
//
//        // Phase 2: 创建飞行动画（按照从低到高的顺序）
//        PuzzleRain puzzleRain = PuzzleRain.getInstance();
//
//        // 创建位置和状态的配对列表
//        List<BlockInfo> blockInfos = new ArrayList<>();
//        for (int i = 0; i < originalPositions.size(); i++) {
//            blockInfos.add(new BlockInfo(originalPositions.get(i), originalStates.get(i)));
//        }
//
//        // 按照Y坐标从低到高排序
//
//        blockInfos.sort(Comparator.comparingInt(info -> info.pos.getY()));
//
//        // 按排序后的顺序添加动画
//        for (int i = 0; i < blockInfos.size(); i++) {
//            BlockPos targetPos = blockInfos.get(i).pos;
//            BlockState state = blockInfos.get(i).state;
//
//            // 计算起始位置
//            Vec3d boundsCenter = new Vec3d(
//                    bounds.getCenter().getX() + 0.5,
//                    bounds.getCenter().getY() + 0.5,
//                    bounds.getCenter().getZ() + 0.5
//            );
//
//            int xSize = bounds.getMax().getX() - bounds.getMin().getX();
//            int zSize = bounds.getMax().getZ() - bounds.getMin().getZ();
//            double spread = Math.max(xSize, zSize) * 0.6;
//
//            Random r = new Random();
//            Vec3d startPos = boundsCenter.add(
//                    r.nextDouble() * spread - spread * 0.5,
//                    10 + r.nextDouble() * 8,
//                    r.nextDouble() * spread - spread * 0.5
//            );
//
//            Vec3d targetVec = new Vec3d(
//                    targetPos.getX() + 0.5,
//                    targetPos.getY() + 0.5,
//                    targetPos.getZ() + 0.5
//            );
//
//            // 使用新的队列系统添加动画
//            puzzleRain.addFlyingAnimation(world, startPos, targetVec, state);
//
//            // 分组延迟，避免一次性生成太多请求
//            if (i % 4 == 0) {
//                try {
//                    Thread.sleep(20); // 减少延迟时间，因为现在有队列控制
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    return;
//                }
//            }
//        }
//
//        PuzzleRain.LOGGER.info("Animation task completed with {} flying block requests", originalPositions.size());
//
//    } catch (Exception e) {
//        throw new CompletionException("Animation failed", e);
//    }
//}
//
//    // 辅助类用于存储方块位置和状态信息
//    private static class BlockInfo {
//        public final BlockPos pos;
//        public final BlockState state;
//
//        public BlockInfo(BlockPos pos, BlockState state) {
//            this.pos = pos;
//            this.state = state;
//        }
//    }
private void performAnimation() {
    try {
        // Phase 1: 存储原始方块并破坏
        List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
        List<BlockState> originalStates = new ArrayList<>();

        for (BlockPos pos : originalPositions) {
            BlockState state = world.getBlockState(pos);
            originalStates.add(state);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());

        }

        // 短暂延迟确保方块破坏完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        // Phase 2: 创建飞行动画（按照方向向量排序）
        PuzzleRain puzzleRain = PuzzleRain.getInstance();

        // 创建位置和状态的配对列表
        List<BlockInfo> blockInfos = new ArrayList<>();
        for (int i = 0; i < originalPositions.size(); i++) {
            blockInfos.add(new BlockInfo(originalPositions.get(i), originalStates.get(i)));
        }

        // 定义方向向量（例如：从西南向东北方向）
        Vec3d direction = PuzzleRain.config.Dir.ToVec3d().normalize(); // 可以修改这个向量来改变排序方向

        if(!PuzzleRain.config.useCenterPoint){
            // 按照方向向量的投影距离排序
            blockInfos.sort((info1, info2) -> {
                double proj1 = calculateProjection(info1.pos, direction);
                double proj2 = calculateProjection(info2.pos, direction);
                return PuzzleRain.config.useReverse?-1*Double.compare(proj1, proj2):Double.compare(proj1, proj2);
            });
        }else{
            //BlockPos center = new BlockPos(0,0,0);
            if(PuzzleRain.config.useAutoCenterPoint){

            }else{
                center = PuzzleRain.config.Dir.ToBP();
            }

            
            blockInfos.sort((i1, i2)->{
                double len1 = center.getSquaredDistance(i1.pos);
                double len2 = center.getSquaredDistance(i2.pos);
                return PuzzleRain.config.useReverse?Double.compare(len1,len2)*-1:Double.compare(len1,len2);
            });
        }

        // 按排序后的顺序添加动画
        Random r = new Random();
        for (int i = 0; i < blockInfos.size(); i++) {
            BlockPos targetPos = blockInfos.get(i).pos;
            BlockState state = blockInfos.get(i).state;

            // 计算起始位置
            Vec3d boundsCenter = new Vec3d(
                    bounds.getCenter().getX() + 0.5,
                    bounds.getCenter().getY() + 0.5,
                    bounds.getCenter().getZ() + 0.5
            );

            int xSize = bounds.getMax().getX() - bounds.getMin().getX();
            int zSize = bounds.getMax().getZ() - bounds.getMin().getZ();
            double spread = Math.max(xSize, zSize) * 0.6*PuzzleRain.config.factor_spread;


            Vec3d startPos;
            if(PuzzleRain.config.usespecificPos&& !PuzzleRain.config.EmitterPoints.isEmpty()){
                startPos= PuzzleRain.config.EmitterPoints.get(r.nextInt(PuzzleRain.config.EmitterPoints.size())).ToVec3d();
            }else{
                startPos = boundsCenter.add(
                        r.nextDouble() * spread - spread * 0.5,
                        10 + r.nextDouble() * 8,
                        r.nextDouble() * spread - spread * 0.5
                );
            }


            Vec3d targetVec = new Vec3d(
                    targetPos.getX() + 0.5,
                    targetPos.getY() + 0.5,
                    targetPos.getZ() + 0.5
            );

            // 使用新的队列系统添加动画
            puzzleRain.addFlyingAnimation(world, startPos, targetVec, state);

            // 分组延迟，避免一次性生成太多请求
            if (i % PuzzleRain.config.groupDelay == 0) {
                try {
                    Thread.sleep(20); // 减少延迟时间，因为现在有队列控制
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        PuzzleRain.LOGGER.info("Animation task completed with {} flying block requests, sorted by direction {}",
                originalPositions.size(), direction);

    } catch (Exception e) {
        throw new CompletionException("Animation failed", e);
    }
}

    // 计算位置在方向向量上的投影
    private double calculateProjection(BlockPos pos, Vec3d direction) {
        Vec3d posVec = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
        return posVec.dotProduct(direction);
    }

    // 辅助类用于存储方块位置和状态信息
    private static class BlockInfo {
        public final BlockPos pos;
        public final BlockState state;

        public BlockInfo(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }




}