package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FlyingBlockAnimationManager {
    // Inner class to represent a flying block animation
    private static class FlyingBlockAnimation {
        final FallingBlockEntity entity;
        final Vec3d startPos;
        final Vec3d targetPos;
        final BlockState blockState;
        final ServerWorld world;
        double progress; // 0.0 to 1.0
        int ticksExisted;

        FlyingBlockAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
            this.world = world;
            this.entity = entity;
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.blockState = blockState;
            this.progress = 0.0;
            this.ticksExisted = 0;
            
            // Add the entity to the world
            world.spawnEntity(entity);
        }

        void update() {
            ticksExisted++;
            
            // Smooth animation using cubic easing
            progress = Math.min(1.0, progress + 0.05); // Adjust speed as needed
            double easedProgress = easeInOutCubic(progress);
            
            // Calculate new position
            double x = startPos.x + (targetPos.x - startPos.x) * easedProgress;
            double y = startPos.y + (targetPos.y - startPos.y) * easedProgress + Math.sin(easedProgress * Math.PI) * 2.0; // Arc motion
            double z = startPos.z + (targetPos.z - startPos.z) * easedProgress;
            
            entity.setPosition(x, y, z);
            
            // When animation is complete, place the block and remove the entity
            if (progress >= 1.0) {
                // Place the block at the target position
                int targetX = (int) Math.floor(targetPos.x);
                int targetY = (int) Math.floor(targetPos.y);
                int targetZ = (int) Math.floor(targetPos.z);
                
                world.setBlockState(new net.minecraft.util.math.BlockPos(targetX, targetY, targetZ), blockState);
                
                // Remove the falling block entity
                entity.discard();
            }
        }
        
        private double easeInOutCubic(double t) {
            return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
        }
        
        boolean isComplete() {
            return progress >= 1.0;
        }
    }

    private final Map<UUID, FlyingBlockAnimation> activeAnimations = new ConcurrentHashMap<>();

    public void addAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
        FlyingBlockAnimation animation = new FlyingBlockAnimation(world, entity, startPos, targetPos, blockState);
        activeAnimations.put(entity.getUuid(), animation);
    }

    public void tick() {
        // Process all active animations
        Iterator<Map.Entry<UUID, FlyingBlockAnimation>> iterator = activeAnimations.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, FlyingBlockAnimation> entry = iterator.next();
            FlyingBlockAnimation animation = entry.getValue();
            
            animation.update();
            
            if (animation.isComplete()) {
                iterator.remove();
            }
        }
    }

    public int getActiveAnimationCount() {
        // Clean up any completed animations first
        activeAnimations.entrySet().removeIf(entry -> entry.getValue().isComplete());
        return activeAnimations.size();
    }

    public boolean hasActiveAnimations() {
        return getActiveAnimationCount() > 0;
    }

    public void clearAllAnimations() {
        for (FlyingBlockAnimation animation : activeAnimations.values()) {
            if (!animation.entity.isRemoved()) {
                animation.entity.discard();
            }
        }
        activeAnimations.clear();
    }
}