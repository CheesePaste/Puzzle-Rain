package com.puzzle_rain;

import com.puzzle_rain.command.PuzzleRainCommand;
import com.puzzle_rain.entity.FlyingBlockEntity;
import com.puzzle_rain.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
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

		// 注册实体
		ModEntities.initialize();

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

	// 优化的 FlyingBlockAnimation 类，使用自定义实体
	public static class FlyingBlockAnimation {
		private final ServerWorld world;
		private final FlyingBlockEntity entity;
		private final Vec3d startPos;
		private final Vec3d targetPos;
		private final BlockState blockState;
		private final double totalDistance;
		private final int animationDuration;
		private int currentTick;

		// 贝塞尔曲线控制点
		private final Vec3d controlPoint1;
		private final Vec3d controlPoint2;
		private boolean hasReached = false;

		public FlyingBlockAnimation(ServerWorld world, FlyingBlockEntity entity, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
			this.world = world;
			this.entity = entity;
			this.startPos = startPos;
			this.targetPos = targetPos;
			this.blockState = blockState;
			this.totalDistance = startPos.distanceTo(targetPos);
			this.animationDuration = (int) Math.max(40, totalDistance * 8); // 动画时间基于距离

			// 计算贝塞尔曲线控制点
			Vec3d direction = targetPos.subtract(startPos).normalize();
			double height = Math.max(5, totalDistance * 0.4);

			this.controlPoint1 = startPos.add(direction.multiply(totalDistance * 0.3)).add(0, height, 0);
			this.controlPoint2 = startPos.add(direction.multiply(totalDistance * 0.7)).add(0, height * 0.6, 0);

			this.currentTick = 0;
		}

		public boolean update() {
			if (entity.isRemoved() || hasReached) {
				return false;
			}

			currentTick++;

			if (currentTick > animationDuration) {
				completeAnimation();
				return false;
			}

			double progress = (double) currentTick / animationDuration;

			// 使用缓动函数让移动更平滑
			double easedProgress = applyEasing(progress);

			// 计算贝塞尔曲线位置
			Vec3d newPosition = calculateCubicBezier(easedProgress);

			// 直接设置位置（自定义实体不需要速度）
			entity.setPosition(newPosition);

			// 平滑旋转
			entity.setYaw(entity.getYaw() + 6.0f);

			return true;
		}

		private double applyEasing(double progress) {
			// 三次缓动函数 - 开始和结束都很平滑
			if (progress < 0.5) {
				return 4 * progress * progress * progress;
			} else {
				return 1 - Math.pow(-2 * progress + 2, 3) / 2;
			}
		}

		private Vec3d calculateCubicBezier(double t) {
			double oneMinusT = 1 - t;
			double oneMinusT2 = oneMinusT * oneMinusT;
			double oneMinusT3 = oneMinusT2 * oneMinusT;
			double t2 = t * t;
			double t3 = t2 * t;

			return startPos.multiply(oneMinusT3)
					.add(controlPoint1.multiply(3 * oneMinusT2 * t))
					.add(controlPoint2.multiply(3 * oneMinusT * t2))
					.add(targetPos.multiply(t3));
		}

		private void completeAnimation() {
			try {
				int targetX = (int) Math.floor(targetPos.x);
				int targetY = (int) Math.floor(targetPos.y);
				int targetZ = (int) Math.floor(targetPos.z);
				BlockPos targetBlockPos = new BlockPos(targetX, targetY, targetZ);

				// 确保目标位置可放置
				if (world.getBlockState(targetBlockPos).isAir()) {
					world.setBlockState(targetBlockPos, blockState);
				} else {
					// 尝试相邻位置
					for (BlockPos nearby : new BlockPos[]{
							targetBlockPos.up(), targetBlockPos.down(),
							targetBlockPos.north(), targetBlockPos.south(),
							targetBlockPos.east(), targetBlockPos.west()
					}) {
						if (world.getBlockState(nearby).isAir()) {
							world.setBlockState(nearby, blockState);
							break;
						}
					}
				}
			} catch (Exception e) {
				PuzzleRain.LOGGER.error("Failed to place block at completion", e);
			} finally {
				if (!entity.isRemoved()) {
					entity.discard();
				}
				hasReached = true;
			}
		}

		public FlyingBlockEntity getEntity() {
			return this.entity;
		}
	}

	public void addFlyingAnimation(ServerWorld world, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
		// 创建自定义飞行方块实体
		FlyingBlockEntity flyingBlock = new FlyingBlockEntity(world,
				new BlockPos((int)startPos.x, (int)startPos.y, (int)startPos.z), blockState);

		// 设置初始位置
		flyingBlock.setPosition(startPos);

		// 生成实体到世界
		world.spawnEntity(flyingBlock);

		// 创建并添加动画
		FlyingBlockAnimation animation = new FlyingBlockAnimation(world, flyingBlock, startPos, targetPos, blockState);
		flyingAnimations.add(animation);

		LOGGER.debug("Created flying block animation from {} to {}", startPos, targetPos);
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
		LOGGER.info("Cleared all flying animations");
	}
}