package com.puzzle_rain;

import com.puzzle_rain.command.PuzzleRainCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PuzzleRain implements ModInitializer {
	public static final String MOD_ID = "puzzle-rain";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 添加静态实例
	private static PuzzleRain instance;

	private final AnimationTaskManager animationTaskManager = new AnimationTaskManager();

	// Flying block animation fields
	private final List<FlyingBlockAnimation> flyingAnimations = new ArrayList<>();

	@Override
	public void onInitialize() {
		LOGGER.info("Puzzle Rain mod initialized!");
		instance = this; // 设置实例

		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			PuzzleRainCommand.register(dispatcher);
		});

		// 注册tick事件来更新飞行动画
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickFlyingAnimations();
		});
	}

	// 添加获取实例的方法
	public static PuzzleRain getInstance() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public AnimationTaskManager getAnimationTaskManager() {
		return animationTaskManager;
	}

	// Flying Block Animation System
	public class FlyingBlockAnimation {
		private final ServerWorld world;
		private final FallingBlockEntity entity;
		private final Vec3d startPos;
		private final Vec3d targetPos;
		private final BlockState blockState;
		private double progress;
		private final double totalDistance;
		private final double animationDuration; // In ticks
		private int currentTick;

		public FlyingBlockAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
			this.world = world;
			this.entity = entity;
			this.startPos = startPos;
			this.targetPos = targetPos;
			this.blockState = blockState;
			this.totalDistance = startPos.distanceTo(targetPos);
			this.animationDuration = Math.max(40, totalDistance * 4); // 40-200+ ticks depending on distance
			this.progress = 0.0;
			this.currentTick = 0;

			// Add the entity to the world
			world.spawnEntity(entity);
		}

		public boolean update() {
			if (entity.isRemoved()) {
				return false;
			}

			currentTick++;
			progress = Math.min(1.0, (double) currentTick / animationDuration);

			// Apply easing function for more natural movement (ease-in-out)
			double easedProgress = easeInOutCubic(progress);

			// Calculate new position based on eased progress
			double x = startPos.x + (targetPos.x - startPos.x) * easedProgress;
			double y = startPos.y + (targetPos.y - startPos.y) * easedProgress;
			double z = startPos.z + (targetPos.z - startPos.z) * easedProgress;

			entity.setPosition(x, y, z);

			// Check if we've reached the destination
			boolean reached = progress >= 1.0;
			if (reached) {
				// Place the block at the target position
				int targetX = (int) Math.floor(targetPos.x);
				int targetY = (int) Math.floor(targetPos.y);
				int targetZ = (int) Math.floor(targetPos.z);

				world.setBlockState(new BlockPos(targetX, targetY, targetZ), blockState);

				// Remove the falling block entity
				entity.discard();
			}

			return !reached;
		}

		// Easing function for smooth movement
		private double easeInOutCubic(double t) {
			return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
		}

		public FallingBlockEntity getEntity() {
			return entity;
		}
	}

	public void addFlyingAnimation(ServerWorld world, FallingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
		FlyingBlockAnimation animation = new FlyingBlockAnimation(world, entity, startPos, targetPos, blockState);
		flyingAnimations.add(animation);
	}

	public void tickFlyingAnimations() {
		Iterator<FlyingBlockAnimation> iterator = flyingAnimations.iterator();
		while (iterator.hasNext()) {
			FlyingBlockAnimation animation = iterator.next();
			if (!animation.update()) {
				iterator.remove();
			}
		}
	}

	public int getFlyingAnimationCount() {
		return flyingAnimations.size();
	}

	public void clearFlyingAnimations() {
		for (FlyingBlockAnimation animation : flyingAnimations) {
			if (!animation.getEntity().isRemoved()) {
				animation.getEntity().discard();
			}
		}
		flyingAnimations.clear();
	}
}