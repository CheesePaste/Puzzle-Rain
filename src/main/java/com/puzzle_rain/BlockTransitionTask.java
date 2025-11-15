package com.puzzle_rain;

import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
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

    private BlockTransitionTask(ServerWorld world, BlockBounds bounds) {
        this.world = world;
        this.bounds = bounds;

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

    public static BlockTransitionTask create(ServerWorld world, BlockBounds bounds) {
        return new BlockTransitionTask(world, bounds);
    }

    public CompletableFuture<Void> execute() {
        return CompletableFuture.runAsync(this::performAnimation);
    }

    private void performAnimation() {
        try {
            // Phase 1: Store original blocks
            List<BlockPos> originalPositions = new ArrayList<>(nonAirPositions);
            List<BlockState> originalStates = new ArrayList<>();

            for (BlockPos pos : originalPositions) {
                originalStates.add(world.getBlockState(pos));
            }

            // Phase 2: Destroy blocks temporarily
            for (BlockPos pos : originalPositions) {
                world.breakBlock(pos, false);
            }

            // Wait before starting the animation
            Thread.sleep(1000); // 1 second delay

            // Phase 3: Create particle animation and restore blocks gradually
            animateBlockRestoration(originalPositions, originalStates);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Animation was interrupted", e);
        } catch (Exception e) {
            throw new CompletionException("Animation failed", e);
        }
    }

    private void animateBlockRestoration(List<BlockPos> positions, List<BlockState> states) throws InterruptedException {
        // Process in chunks to avoid server lag
        final int CHUNK_SIZE = 8; // Process 8 blocks at a time to be more conservative
        final long DELAY_BETWEEN_CHUNKS = 100; // 100ms between chunks

        for (int i = 0; i < positions.size(); i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, positions.size());

            // Process chunk
            for (int j = i; j < end; j++) {
                BlockPos pos = positions.get(j);
                BlockState state = states.get(j);

                // Create particle effect from above with curved path
                spawnCurvedParticleEffect(pos, state);

                // Restore the block
                world.setBlockState(pos, state);
            }

            // Small delay between chunks to prevent server lag
            Thread.sleep(DELAY_BETWEEN_CHUNKS);
        }
    }

    private void spawnCurvedParticleEffect(BlockPos targetPos, BlockState state) {
        Vec3d startPos = new Vec3d(
            bounds.getCenter().getX() + 0.5,
            bounds.getCenter().getY() + 15,
            bounds.getCenter().getZ() + 0.5
        );

        Vec3d target = new Vec3d(
            targetPos.getX() + 0.5,
            targetPos.getY() + 0.5,
            targetPos.getZ() + 0.5
        );

        // Calculate a curved path using a quadratic Bezier curve
        // Control point is above and between start and target
        Vec3d control = new Vec3d(
            (startPos.x + target.x) / 2,
            Math.max(startPos.y, target.y) + 8, // Add height to make arc
            (startPos.z + target.z) / 2
        );

        // Spawn particles along the curved path
        for (ServerPlayerEntity player : world.getPlayers()) {
            // Create 20 points along the curve for smooth particle trail
            for (int step = 0; step <= 20; step++) {
                double t = step / 20.0; // Parameter from 0 to 1
                Vec3d position = calculateQuadraticBezierPoint(startPos, control, target, t);

                // Only spawn particles in the air to avoid server lag
                if (step % 2 == 0) { // Spawn every other point to reduce density
                    world.spawnParticles(
                        player, // recipient
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, state), // Parameters
                        true, // overrideLimiter (false to respect particle limits)
                        position.x, position.y, position.z, // x, y, z
                        1, // count
                        0.05, 0.05, 0.05, // deltaX, deltaY, deltaZ
                        0.0 // speed
                    );
                }
            }
        }
    }

    private Vec3d calculateQuadraticBezierPoint(Vec3d start, Vec3d control, Vec3d end, double t) {
        // Quadratic Bezier curve formula: B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
        double u = 1 - t;
        double tt = t * t;
        double uu = u * u;
        double ut2 = 2 * u * t;

        return new Vec3d(
            uu * start.x + ut2 * control.x + tt * end.x,
            uu * start.y + ut2 * control.y + tt * end.y,
            uu * start.z + ut2 * control.z + tt * end.z
        );
    }
}