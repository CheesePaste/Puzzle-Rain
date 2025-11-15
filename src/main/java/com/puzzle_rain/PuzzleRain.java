package com.puzzle_rain;

import com.puzzle_rain.command.PuzzleRainCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PuzzleRain implements ModInitializer {
	public static final String MOD_ID = "puzzle-rain";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 添加静态实例
	private static PuzzleRain instance;

	private final BlockAnimationManager animationManager = new BlockAnimationManager();

	@Override
	public void onInitialize() {
		LOGGER.info("Puzzle Rain mod initialized!");
		instance = this; // 设置实例

		// 注册命令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			PuzzleRainCommand.register(dispatcher);
		});

		// 注册tick事件来更新动画
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			animationManager.tick();
		});
	}

	// 添加获取实例的方法
	public static PuzzleRain getInstance() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public BlockAnimationManager getAnimationManager() {
		return animationManager;
	}
}