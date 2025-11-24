package com.puzzle_rain.client;

import com.puzzle_rain.GravitationalDistortionShader;
import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.entity.FlyingBlockEntityRenderer;
import com.puzzle_rain.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public class PuzzleRainClient implements ClientModInitializer {
    public static ShaderProgram GRAVITY_WARP;
    @Override
    public void onInitializeClient() {
        GravitationalDistortionShader.initialize();
        // 注册飞行方块实体的渲染器
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(
                    Identifier.of(PuzzleRain.MOD_ID, "gravitational_distortion"),
                    VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, // 实体的标准顶点格式
                    program -> GRAVITY_WARP = program
            );
        });


        EntityRendererRegistry.register(ModEntities.FLYING_BLOCK_ENTITY,
                FlyingBlockEntityRenderer::new);
    }
}
