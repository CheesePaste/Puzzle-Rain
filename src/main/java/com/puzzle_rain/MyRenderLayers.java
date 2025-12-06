package com.puzzle_rain;

import com.puzzle_rain.client.PuzzleRainClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

public class MyRenderLayers extends RenderLayer {
    public MyRenderLayers(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    public static final RenderLayer TRAIL_GLOW = RenderLayer.of(
            "worldreloader:trail_glow",
            VertexFormats.POSITION_TEXTURE_COLOR, // 我们需要 TEXTURE 格式来传递 UV
            VertexFormat.DrawMode.QUADS,
            256,
            false,
            true, // 半透明
            MultiPhaseParameters.builder()
                    .program(new ShaderProgram(PuzzleRainClient::getTrailGlowShader)) // 这里填你注册好的 Shader 获取方法
                    .transparency(LIGHTNING_TRANSPARENCY) // 关键：使用闪电的透明度模式 (Additive Blending)
                    .writeMaskState(COLOR_MASK) // 不写入深度，防止透明物体遮挡
                    .cull(DISABLE_CULLING) // 双面渲染
                    .build(false)
    );


    public static final RenderLayer CUSTOM_BLOCK = RenderLayer.of(
            "worldreloader:custom_block",
            VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS,
            256,
            false,  // useDelegate
            true,   // needsSorting
            MultiPhaseParameters.builder()
                    .program(RenderPhase.LIGHTNING_PROGRAM)  // 使用发光着色器
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .cull(RenderPhase.DISABLE_CULLING)        // 禁用面剔除
                    .lightmap(RenderPhase.ENABLE_LIGHTMAP)    // 启用光照贴图
                    .build(false)
    );
}
