package com.puzzle_rain;

import com.puzzle_rain.command.PuzzleRainCommand;
import com.puzzle_rain.entity.FlyingBlockEntity;
import com.puzzle_rain.entity.ModEntities;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class PuzzleRain implements ModInitializer {
	public static final String MOD_ID = "puzzle-rain";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 添加静态实例
	private static PuzzleRain instance;

	public static ModConfig config;
	private final AnimationTaskManager animationTaskManager = new AnimationTaskManager();

	// Flying block animation fields
	private final Queue<AnimationRequest> pendingAnimations = new LinkedList<>();
	private final ArrayList<FlyingBlockAnimation> activeAnimations = new ArrayList<>();

	// 配置参数
	private static final int MAX_CONCURRENT_FLYING_BLOCKS = 4000; // 最大同时飞行方块数
	private static final int MAX_SPAWN_PER_TICK = 400; // 每帧最多生成新方块数

	public static ConfigHolder ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
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



		GuiRegistry registry = AutoConfig.getGuiRegistry(ModConfig.class);
		KeyBindings.register();
		config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KeyBindings.openConfigKey.wasPressed()) {
				RegionManager.getInstance().setFirstPosition(client.player,new BlockPos(config.startPosX,config.startPosY,config.startPosZ));
				RegionManager.getInstance().setSecondPosition(client.player,new BlockPos(config.endPosX,config.endPosY,config.endPosZ));
				if (client.player != null) {
					client.setScreen(AutoConfig.getConfigScreen(ModConfig.class, client.currentScreen).get());
				}
			}
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
			this.animationDuration = (int) Math.max(40, totalDistance * 8*PuzzleRain.config.factor); // 动画时间基于距离
			//this.entity.blockState11=13;
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

			switch (config.controlType){
				case Velocity_roll -> velocityControl();

				default -> defaultControl();
			}

			return true;
		}

		private void velocityControl() {
			if (entity.getPos().isWithinRangeOf(targetPos, 0.01, 0.01)) {
				entity.setVelocity(0, 0, 0);
				return;
			}

			// 计算剩余距离
			double remainingDistance = entity.getPos().distanceTo(targetPos);

			// 计算剩余时间（基于总持续时间和当前进度）
			int remainingTicks = animationDuration - currentTick;

			// 如果已经接近终点或剩余时间很少，直接设置到目标位置
			if (remainingDistance < 0.1 || remainingTicks <= 1) {
				entity.setPosition(targetPos);
				entity.setVelocity(0, 0, 0);
				return;
			}

			// 计算主运动方向（指向目标）
			Vec3d toTarget = targetPos.subtract(entity.getPos());
			Vec3d mainDirection = toTarget.normalize();

			// 计算主速度（距离/时间）
			double mainSpeed = remainingDistance / remainingTicks;

			// 计算螺旋运动参数
			double progress = (double) currentTick / animationDuration;

			// 螺旋半径随时间减小（开始时最大，结束时为0）
			double maxRadius = config.maxRadius; // 最大螺旋半径
			double currentRadius = maxRadius * (1 - progress);

			// 螺旋角速度（可以调整这个值来改变螺旋的紧密程度）
			double angularSpeed = 2.0 * Math.PI * config.omega; // 每秒2圈

			// 计算法向速度方向
			// 需要一个垂直于主方向的向量作为基准
			Vec3d normalBase;
			if (Math.abs(mainDirection.y) < 0.9) {
				// 如果主方向不是垂直的，使用与主方向和垂直方向都垂直的向量
				normalBase = mainDirection.crossProduct(new Vec3d(0, 1, 0)).normalize();
			} else {
				// 如果主方向接近垂直，使用水平方向作为基准
				normalBase = new Vec3d(1, 0, 0);
			}

			// 计算当前的法向方向（随时间旋转）
			double angle = angularSpeed * progress;
			Vec3d normalDirection = normalBase.rotateY((float) angle);

			// 确保法向方向与主方向垂直
			normalDirection = normalDirection.subtract(mainDirection.multiply(normalDirection.dotProduct(mainDirection))).normalize();

			// 计算法向速度大小（与半径和角速度相关）
			double normalSpeed = currentRadius * angularSpeed / animationDuration;

			// 合成速度 = 主速度 + 法向速度
			Vec3d mainVelocity = mainDirection.multiply(mainSpeed);
			Vec3d normalVelocity = normalDirection.multiply(normalSpeed);
			Vec3d totalVelocity = mainVelocity.add(normalVelocity);

			// 设置速度
			entity.setVelocity(totalVelocity);

			// 可选：添加旋转效果
			entity.setYaw(entity.getYaw() + 10.0f);
		}

		private void defaultControl() {
			double progress = (double) currentTick / animationDuration;

			// 使用缓动函数让移动更平滑
			double easedProgress = applyEasing(progress);

			// 计算贝塞尔曲线位置
			Vec3d newPosition = calculateCubicBezier(easedProgress);

			// 直接设置位置（自定义实体不需要速度）
			entity.setPosition(newPosition);

			// 平滑旋转
			entity.setYaw(entity.getYaw() + 6.0f);
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

		public boolean isCompleted() {
			return hasReached;
		}
	}

	// 动画请求类
		private record AnimationRequest(ServerWorld world, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
	}

	public void addFlyingAnimation(ServerWorld world, Vec3d startPos, Vec3d targetPos, BlockState blockState) {
		// 将动画请求加入待处理队列
		pendingAnimations.offer(new AnimationRequest(world, startPos, targetPos, blockState));
		LOGGER.debug("Queued flying block animation from {} to {}", startPos, targetPos);
	}

	public void tickFlyingAnimations() {
		// 1. 先更新所有活跃的动画，移除已完成的
		Iterator<FlyingBlockAnimation> iterator = activeAnimations.iterator();
		while (iterator.hasNext()) {
			FlyingBlockAnimation animation = iterator.next();
			if (!animation.update()) {
				// 动画完成，移除
				iterator.remove();
				LOGGER.debug("Animation completed, active count: {}", activeAnimations.size());
			}
		}

		// 2. 如果当前活跃动画数量小于上限，从待处理队列中生成新的动画
		int availableSlots = MAX_CONCURRENT_FLYING_BLOCKS - activeAnimations.size();
		if (availableSlots > 0 && !pendingAnimations.isEmpty()) {
			int spawnCount = Math.min(Math.min(availableSlots, MAX_SPAWN_PER_TICK), pendingAnimations.size());

			for (int i = 0; i < spawnCount; i++) {
				AnimationRequest request = pendingAnimations.poll();
				if (request == null) break;

				createFlyingBlockAnimation(request);
			}
		}

		// 调试信息
		if (!pendingAnimations.isEmpty()) {
			LOGGER.debug("Pending animations: {}, Active animations: {}", pendingAnimations.size(), activeAnimations.size());
		}
	}

	private void createFlyingBlockAnimation(AnimationRequest request) {
		try {
			// 创建自定义飞行方块实体
			FlyingBlockEntity flyingBlock = new FlyingBlockEntity(request.world,
					new BlockPos((int)request.startPos.x, (int)request.startPos.y, (int)request.startPos.z), request.blockState);

			// 设置初始位置
			flyingBlock.setPosition(request.startPos);
			flyingBlock.setBlockState(request.blockState);
			flyingBlock.getDataTracker().set(FlyingBlockEntity.BLOCK_STATE_ID, Block.getRawIdFromState(request.blockState));

			// 生成实体到世界
			request.world.spawnEntity(flyingBlock);
			flyingBlock.setBlockState(request.blockState);

			// 创建并添加动画
			FlyingBlockAnimation animation = new FlyingBlockAnimation(request.world, flyingBlock, request.startPos, request.targetPos, request.blockState);
			activeAnimations.add(animation);

			LOGGER.debug("Created flying block animation from {} to {}", request.startPos, request.targetPos);
		} catch (Exception e) {
			LOGGER.error("Failed to create flying block animation", e);
		}
	}

	public int getFlyingAnimationCount() {
		return activeAnimations.size();
	}

	public int getPendingAnimationCount() {
		return pendingAnimations.size();
	}

	public void clearFlyingAnimations() {
		for (FlyingBlockAnimation animation : activeAnimations) {
			if (!animation.getEntity().isRemoved()) {
				animation.getEntity().discard();
			}
		}
		activeAnimations.clear();
		pendingAnimations.clear();
		LOGGER.info("Cleared all flying animations");
	}

	// 获取配置参数的方法（可选，用于命令或其他地方）
	public static int getMaxConcurrentFlyingBlocks() {
		return MAX_CONCURRENT_FLYING_BLOCKS;
	}

	public static int getMaxSpawnPerTick() {
		return MAX_SPAWN_PER_TICK;
	}
}