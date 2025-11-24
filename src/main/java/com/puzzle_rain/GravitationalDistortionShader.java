package com.puzzle_rain;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GravitationalDistortionShader {
    private static ShaderProgram distortionShader;
    private static final List<Vec3d> gravityCenters = new ArrayList<>();
    private static boolean enabled = false;

    public static void initialize() {
        try {
            // 使用 Minecraft 的着色器系统
            distortionShader = new ShaderProgram(
                    MinecraftClient.getInstance().getResourceManager(),
                    "puzzle_rain_gravitational_distortion",
                    VertexFormats.POSITION_TEXTURE_COLOR_LIGHT
            );
        } catch (IOException e) {
            PuzzleRain.LOGGER.error("Failed to load gravitational distortion shader", e);
        }
    }

    public static void addGravityCenter(Vec3d center, double radius) {
        gravityCenters.add(new Vec3d(center.x, center.y, radius));
        enabled = true;
    }

    public static void removeGravityCenter(Vec3d center) {
        gravityCenters.removeIf(gravityCenter ->
                Math.abs(gravityCenter.x - center.x) < 0.1 &&
                        Math.abs(gravityCenter.y - center.y) < 0.1);

        if (gravityCenters.isEmpty()) {
            enabled = false;
        }
    }

    public static void clearAllGravityCenters() {
        gravityCenters.clear();
        enabled = false;
    }

    public static void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!enabled || distortionShader == null) return;

        RenderSystem.setShader(() -> distortionShader);
        distortionShader.bind();

        // 设置统一变量
        setupUniforms();

        // 渲染全屏四边形
        renderFullscreenQuad();

        distortionShader.unbind();
    }

    private static void setupUniforms() {
        if (distortionShader == null) return;

        // 设置引力中心数据
        GlUniform gravityCentersUniform = distortionShader.getUniform("GravityCenters");
        GlUniform gravityCountUniform = distortionShader.getUniform("GravityCenterCount");
        GlUniform timeUniform = distortionShader.getUniform("Time");
        GlUniform screenSizeUniform = distortionShader.getUniform("ScreenSize");

        if (gravityCentersUniform != null) {
            float[] centers = new float[30]; // 10个中心 * 3个值
            int index = 0;
            for (int i = 0; i < Math.min(gravityCenters.size(), 10); i++) {
                Vec3d center = gravityCenters.get(i);
                centers[index++] = (float) center.x;
                centers[index++] = (float) center.y;
                centers[index++] = (float) center.z;
            }
            gravityCentersUniform.set(centers);
        }

        if (gravityCountUniform != null) {
            gravityCountUniform.set(Math.min(gravityCenters.size(), 10));
        }

        if (timeUniform != null) {
            timeUniform.set((System.currentTimeMillis() % 100000) / 1000.0f);
        }

        if (screenSizeUniform != null) {
            Window window = MinecraftClient.getInstance().getWindow();
            screenSizeUniform.set((float)window.getFramebufferWidth(), (float)window.getFramebufferHeight());
        }
    }

    private static void renderFullscreenQuad() {
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);


        // 全屏四边形
        bufferBuilder.vertex(-1, -1, 0).texture(0, 0).color(255, 255, 255, 255);
        bufferBuilder.vertex(-1, 1, 0).texture(0, 1).color(255, 255, 255, 255);
        bufferBuilder.vertex(1, 1, 0).texture(1, 1).color(255, 255, 255, 255);
        bufferBuilder.vertex(1, -1, 0).texture(1, 0).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    public static void close() {
        if (distortionShader != null) {
            distortionShader.close();
        }
    }
}