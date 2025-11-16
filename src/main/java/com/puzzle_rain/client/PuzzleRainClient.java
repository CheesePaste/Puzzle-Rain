package com.puzzle_rain.client;

import com.puzzle_rain.entity.FlyingBlockEntityRenderer;
import com.puzzle_rain.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class PuzzleRainClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册飞行方块实体的渲染器
        EntityRendererRegistry.register(ModEntities.FLYING_BLOCK_ENTITY,
                FlyingBlockEntityRenderer::new);
    }
}
