package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FlyingBlockAnimationManager {
    private final List<FlyingBlockAnimation> animations = new ArrayList<>();

    public static class FlyingBlockAnimation {
        private final ServerWorld world;
        private final FallingBlockEntity entity;
        private final Vec3d startPos;
        private final Vec3d targetPos;
        private final BlockState blockState;
        private double progress;
        private final double animationSpeed;

        public FlyingBlockAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
            this.world = world;
            this.entity = entity;
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.blockState = blockState;
            this.progress = 0.0;
            // Calculate animation speed based on distance (blocks per tick)
            double distance = startPos.distanceTo(targetPos);
            this.animationSpeed = Math.max(0.1, distance / 20.0); // Adjust the divisor to control speed

            // Add the entity to the world
            world.spawnEntity(entity);
        }

        public boolean update() {
            if (entity.isRemoved()) {
                return false;
            }

            progress += animationSpeed;
            if (progress >= 1.0) {
                progress = 1.0;
            }

            // Calculate new position based on progress
            double x = startPos.x + (targetPos.x - startPos.x) * progress;
            double y = startPos.y + (targetPos.y - startPos.y) * progress;
            double z = startPos.z + (targetPos.z - startPos.z) * progress;

            entity.setPosition(x, y, z);

            // Check if we've reached the destination
            boolean reached = progress >= 1.0;
            if (reached) {
                // Place the block at the target position
                int targetX = (int) Math.floor(targetPos.x);
                int targetY = (int) Math.floor(targetPos.y);
                int targetZ = (int) Math.floor(targetPos.z);
                
                world.setBlockState(new net.minecraft.util.math.BlockPos(targetX, targetY, targetZ), blockState);
                
                // Remove the falling block entity
                entity.discard();
            }

            return !reached;
        }

        public FallingBlockEntity getEntity() {
            return entity;
        }
    }

    public void addAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
        FlyingBlockAnimation animation = new FlyingBlockAnimation(world, entity, startPos, targetPos, blockState);
        animations.add(animation);
    }

    public void tick() {
        Iterator<FlyingBlockAnimation> iterator = animations.iterator();
        while (iterator.hasNext()) {
            FlyingBlockAnimation animation = iterator.next();
            if (!animation.update()) {
                iterator.remove();
            }
        }
    }

    public int getAnimationCount() {
        return animations.size();
    }

    public void clearAnimations() {
        for (FlyingBlockAnimation animation : animations) {
            if (!animation.getEntity().isRemoved()) {
                animation.getEntity().discard();
            }
        }
        animations.clear();
    }
}