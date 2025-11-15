package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Constructor;
import java.util.*;

public class BlockAnimation {
    private final ServerWorld world;
    private final BlockBounds bounds;
    private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
    private final List<AnimatedBlock> animatedBlocks = new ArrayList<>();
    private final List<BlockPos> pendingBlocks; // 待生成的方块列表
    private int tickCounter = 0;
    private AnimationPhase phase = AnimationPhase.WAITING;
    private int blocksPerTick = 2; // 每tick生成的方块数量

    private enum AnimationPhase {
        WAITING, DESTROYING, GENERATING, FLYING, REBUILDING, FINISHED
    }

    public BlockAnimation(ServerWorld world, BlockBounds bounds) {
        this.world = world;
        this.bounds = bounds;

        // 初始化待生成方块列表
        List<BlockPos> positions = new ArrayList<>(bounds.getAllBlockPositions());
        Collections.shuffle(positions); // 随机化顺序
        pendingBlocks = new ArrayList<>();

        // 只添加非空气方块
        for (BlockPos pos : positions) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                pendingBlocks.add(pos);
                originalBlocks.put(pos, state);
            }
        }
    }

    public void start() {
        phase = AnimationPhase.DESTROYING;
        destroyBlocks();
    }

    public void tick() {
        tickCounter++;

        switch (phase) {
            case DESTROYING:
                if (tickCounter >= 20) { // 等待1秒后开始生成动画
                    phase = AnimationPhase.GENERATING;
                    tickCounter = 0;
                }
                break;

            case GENERATING:
                // 逐渐生成方块实体
                generateBlocksGradually();
                if (pendingBlocks.isEmpty()) {
                    phase = AnimationPhase.FLYING;
                    tickCounter = 0;
                }
                break;

            case FLYING:
                // 更新所有动画方块的位置
                updateBlockAnimations();
                if (tickCounter >= 100) { // 飞行5秒后开始重建
                    startRebuilding();
                    phase = AnimationPhase.REBUILDING;
                    tickCounter = 0;
                }
                break;

            case REBUILDING:
                if (tickCounter >= 20) { // 重建完成后结束
                    phase = AnimationPhase.FINISHED;
                }
                break;
        }
    }

    private void destroyBlocks() {
        // 清除区域内的所有方块
        for (BlockPos pos : originalBlocks.keySet()) {
            world.breakBlock(pos, false);
        }
    }

    private void generateBlocksGradually() {
        if (pendingBlocks.isEmpty()) {
            return;
        }

        // 每tick生成指定数量的方块
        int blocksToGenerate = Math.min(blocksPerTick, pendingBlocks.size());

        for (int i = 0; i < blocksToGenerate; i++) {
            // 从列表开头取出位置（先进先出）
            BlockPos pos = pendingBlocks.remove(0);
            BlockState state = originalBlocks.get(pos);
            createAnimatedBlock(pos, state);

            if (pendingBlocks.isEmpty()) {
                break;
            }
        }
    }

    private void createAnimatedBlock(BlockPos targetPos, BlockState state) {
        try {
            BlockPos center = bounds.getCenter();
            Vec3d startPos = new Vec3d(center.getX() + 0.5, center.getY() + 15, center.getZ() + 0.5);
            Vec3d target = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

            // 创建临时方块并生成下落实体
            BlockPos tempPos = new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z);
            world.setBlockState(tempPos, state);
            FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, tempPos, state);

            // 立即移除临时方块
            world.breakBlock(tempPos, false);

            // 配置实体属性
            fallingBlock.setPosition(startPos);
            fallingBlock.setNoGravity(true);
            fallingBlock.setHurtEntities(0.0f, 0);

            // 创建动画数据
            AnimatedBlock animatedBlock = new AnimatedBlock(fallingBlock, targetPos, target, tickCounter);
            animatedBlocks.add(animatedBlock);

        } catch (Exception e) {
            System.err.println("Failed to create animated block: " + e.getMessage());
            // 如果创建失败，将这个方块重新添加到待处理列表
            pendingBlocks.add(targetPos);
        }
    }

    private void updateBlockAnimations() {
        Iterator<AnimatedBlock> iterator = animatedBlocks.iterator();
        while (iterator.hasNext()) {
            AnimatedBlock animatedBlock = iterator.next();

            if (animatedBlock.update(world, tickCounter)) {
                // 动画完成，移除实体
                animatedBlock.entity.discard();
                iterator.remove();
            }
        }
    }

    private void startRebuilding() {
        // 移除所有下落方块实体
        for (AnimatedBlock animatedBlock : animatedBlocks) {
            animatedBlock.entity.discard();
        }
        animatedBlocks.clear();

        // 恢复原始方块
        for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
            world.setBlockState(entry.getKey(), entry.getValue());
        }
    }

    public boolean isFinished() {
        return phase == AnimationPhase.FINISHED;
    }

    // 内部类：管理单个方块的动画
    private static class AnimatedBlock {
        public final FallingBlockEntity entity;
        public final BlockPos targetPos;
        public final Vec3d target;
        public final int startTick;
        public final double randomOffset;

        public AnimatedBlock(FallingBlockEntity entity, BlockPos targetPos, Vec3d target, int startTick) {
            this.entity = entity;
            this.targetPos = targetPos;
            this.target = target;
            this.startTick = startTick;
            this.randomOffset = Math.random() * Math.PI * 2; // 随机相位偏移
        }

        public boolean update(ServerWorld world, int currentTick) {
            int elapsed = currentTick - startTick;
            if (elapsed < 0) return false;

            // 使用贝塞尔曲线计算位置
            float progress = Math.min(elapsed / 100.0f, 1.0f); // 100 tick 完成动画
            Vec3d newPosition = calculateBezierPosition(progress);

            // 设置新位置
            entity.setPosition(newPosition);

            // 添加旋转效果
            float rotation = (float) (progress * 4 * Math.PI + randomOffset);
            entity.setYaw(rotation * 57.2958f); // 弧度转角度

            // 当接近目标时，逐渐减速
            if (progress > 0.8f) {
                double distance = newPosition.distanceTo(target);
                if (distance < 0.5) {
                    return true; // 动画完成
                }
            }

            return false;
        }

        private Vec3d calculateBezierPosition(float t) {
            Vec3d start = entity.getPos();

            // 三次贝塞尔曲线的控制点
            Vec3d control1, control2;

            // 根据目标位置和起始位置计算控制点，形成弧形路径
            Vec3d direction = target.subtract(start);
            double height = 8.0; // 弧线高度

            // 第一个控制点：起始点上方
            control1 = new Vec3d(
                    start.x + direction.x * 0.3,
                    start.y + height,
                    start.z + direction.z * 0.3
            );

            // 第二个控制点：目标点上方
            control2 = new Vec3d(
                    start.x + direction.x * 0.7,
                    start.y + height,
                    start.z + direction.z * 0.7
            );

            // 三次贝塞尔曲线公式
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double uuu = uu * u;
            double ttt = tt * t;

            Vec3d point = start.multiply(uuu); // (1-t)^3 * P0
            point = point.add(control1.multiply(3 * uu * t)); // + 3*(1-t)^2*t * P1
            point = point.add(control2.multiply(3 * u * tt)); // + 3*(1-t)*t^2 * P2
            point = point.add(target.multiply(ttt)); // + t^3 * P3

            return point;
        }
    }
}