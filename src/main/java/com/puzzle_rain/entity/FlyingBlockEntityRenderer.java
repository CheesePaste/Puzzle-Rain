package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.entity.FlyingBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

public class FlyingBlockEntityRenderer extends EntityRenderer<FlyingBlockEntity> {
    public FlyingBlockEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(FlyingBlockEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        BlockState blockState = entity.getBlockState();
        light = 0xF000F0;
        if (blockState == null || blockState.getRenderType() != BlockRenderType.MODEL) {
            return;
        }

        matrices.push();

        // 应用旋转动画
        //matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYaw(tickDelta)));

        // 应用缩放（可选）
        //matrices.translate(-0.5, 0.0, -0.5);
        renderTrail(entity,matrices,vertexConsumers,tickDelta);
        matrices.push();
        //renderBlockTrail(entity,blockState,matrices,vertexConsumers,tickDelta,light);
        //renderEnergyField(entity,matrices,vertexConsumers,tickDelta);
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        matrices.push();
        if(PuzzleRain.config.useEnergyField) renderEnergyField(entity,matrices,vertexConsumers,tickDelta,light);
        //matrices.translate(-0.5, 0.0, -0.5);
        blockRenderManager.renderBlockAsEntity(blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        matrices.pop();
        matrices.pop();

        // 3. 渲染能量场 (解除注释)
        // 传入 light 变量
        //renderEnergyField(entity, matrices, vertexConsumers, tickDelta, light);

        //matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    // 渲染球形能量场
    private void renderEnergyField(FlyingBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
        // 注意：RenderLayer.getEnergySwirl 需要纹理，如果纹理路径不对也会紫黑或者不显示
        Identifier texture = Identifier.of("textures/entity/beacon_beam.png");
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEnergySwirl(texture,
                (entity.age + tickDelta) * 0.01f, (entity.age + tickDelta) * 0.01f));

        matrices.push();
        float scale = (2.0f + (float) Math.sin((entity.age + tickDelta) * 0.1f) * 0.3f)* PuzzleRain.config.spherefactor;
        matrices.scale(scale, scale, scale);

        // 球体计算是围绕 0,0,0 的，不需要 -0.5 偏移，除非你想偏心
        // matrices.translate(-0.5, -0.5, -0.5);

        // 这里的 light 建议使用最大亮度 0xF000F0 让能量场发光，或者使用传入的 light 受环境影响
        renderSimpleSphere(vertexConsumer, matrices, 0.3f, 93f/255, 45f/255, 204f/255, 0.25f, 0xF000F0);

        matrices.pop();
    }

    // 需要传递 light 和 overlay 参数
    private void renderSimpleSphere(VertexConsumer consumer, MatrixStack matrices, float radius, float r, float g, float b, float a, int light) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // 获取法线矩阵，用于光照计算正确
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        int segments = 50;
        int stacks = 6;

        for (int i = 0; i < stacks; i++) {
            float phi1 = (float) (Math.PI * i / stacks);
            float phi2 = (float) (Math.PI * (i + 1) / stacks);

            for (int j = 0; j < segments; j++) {
                float theta1 = (float) (2 * Math.PI * j / segments);
                float theta2 = (float) (2 * Math.PI * (j + 1) / segments);

                // 计算四个点的坐标... (你的原始数学逻辑没问题，此处省略坐标计算以节省篇幅，使用你的 x1, y1... 等)
                // ... (x1, y1, z1) 到 (x4, y4, z4) 的计算保持不变 ...
                // 四个顶点
                float x1 = (float) (Math.sin(phi1) * Math.cos(theta1)) * radius;
                float y1 = (float) Math.cos(phi1) * radius;
                float z1 = (float) (Math.sin(phi1) * Math.sin(theta1)) * radius;

                float x2 = (float) (Math.sin(phi1) * Math.cos(theta2)) * radius;
                float y2 = (float) Math.cos(phi1) * radius;
                float z2 = (float) (Math.sin(phi1) * Math.sin(theta2)) * radius;

                float x3 = (float) (Math.sin(phi2) * Math.cos(theta2)) * radius;
                float y3 = (float) Math.cos(phi2) * radius;
                float z3 = (float) (Math.sin(phi2) * Math.sin(theta2)) * radius;

                float x4 = (float) (Math.sin(phi2) * Math.cos(theta1)) * radius;
                float y4 = (float) Math.cos(phi2) * radius;
                float z4 = (float) (Math.sin(phi2) * Math.sin(theta1)) * radius;

                // 辅助方法：绘制完整的顶点
                putVertex(consumer, matrix, matrices.peek(), x1, y1, z1, r, g, b, a, light);
                putVertex(consumer, matrix, matrices.peek(), x2, y2, z2, r, g, b, a, light);
                putVertex(consumer, matrix, matrices.peek(), x3, y3, z3, r, g, b, a, light);

                putVertex(consumer, matrix, matrices.peek(), x1, y1, z1, r, g, b, a, light);
                putVertex(consumer, matrix, matrices.peek(), x3, y3, z3, r, g, b, a, light);
                putVertex(consumer, matrix, matrices.peek(), x4, y4, z4, r, g, b, a, light);
            }
        }
    }

    // 补全数据的辅助方法
    private void putVertex(VertexConsumer consumer, Matrix4f matrix, MatrixStack.Entry normalMatrix, float x, float y, float z, float r, float g, float b, float a, int light) {
        consumer.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(0, 0) // 必须提供 UV，哪怕是 0,0
                .overlay(OverlayTexture.DEFAULT_UV) // 必须提供 Overlay
                .light(light) // 必须提供 Light
                .normal(normalMatrix, x, y, z);
    }

    // 渲染移动轨迹
    private void renderTrail(FlyingBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<Vec3d> trail = entity.getTrailPositions().subList(0,Math.max(0,entity.getTrailPositions().size()-10));
        if (trail.size() < 2) return;

        // 使用 LINES 模式 (LineStrip 在某些版本很难直接获取，LINES 更通用)
        // 注意：Minecraft 原版渲染线段通常使用 debug lines，宽度固定。
        // 如果想要很酷的拖尾，通常需要画成面（Quad）而不是线。这里先修复线段的显示。
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix(); // 获取法线矩阵

        // 获取实体当前的插值位置 (用于将世界坐标转换为相对坐标)
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);

            // 关键步骤：转为局部坐标
            float x1 = (float) (startWorld.x - entityX);
            float y1 = (float) (startWorld.y - entityY);
            float z1 = (float) (startWorld.z - entityZ);

            float x2 = (float) (endWorld.x - entityX);
            float y2 = (float) (endWorld.y - entityY);
            float z2 = (float) (endWorld.z - entityZ);

            float alpha = (float) i / trail.size();

            // LINES buffer 只接受 Position, Color, Normal (通常)
            // 必须成对绘制
            vertexConsumer.vertex(matrix, x1, y1, z1)
                    .color(1f, 0.8f, 0.2f, alpha)
                    .normal(matrices.peek(), 0, 1, 0);

            vertexConsumer.vertex(matrix, x2, y2, z2)
                    .color(1f, 0.8f, 0.2f, alpha)
                    .normal(matrices.peek(), 0, 1, 0);
        }
    }

    // 渲染方块残影轨迹
//    private void renderBlockTrail(FlyingBlockEntity entity, BlockState blockState, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
//        List<Vec3d> trail = entity.getTrailPositions();
//        if (trail.isEmpty() || blockState == null) return;
//
//        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
//
//        // 1. 获取实体当前的插值位置 (用于将轨迹的世界坐标转换为相对坐标)
//        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
//        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
//        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
//
//        // 2. 遍历轨迹点
//        // 为了性能，如果轨迹很长，建议可以 i+=2 跳过一些点，或者限制只渲染最近的10个点
//        for (int i = 0; i < trail.size(); i++) {
//            Vec3d trailPos = trail.get(i);
//
//            matrices.push();
//
//            // 3. 计算相对位移： 轨迹点世界坐标 - 实体当前坐标
//            double dx = trailPos.x - entityX;
//            double dy = trailPos.y - entityY;
//            double dz = trailPos.z - entityZ;
//
//            matrices.translate(dx, dy, dz);
//
//            // 4. 动态缩放效果 (可选)
//            // i = 0 是最旧的点（尾巴）， i = size-1 是最近的点（头）
//            // 让尾巴变小：scale 从 0.0 到 1.0
//            float scale = (float) i / trail.size();
//
//            // 稍微限制一下最小尺寸，防止完全消失或渲染错误，也可以让靠近实体的部分接近原大小
//            scale = 0.2f + (scale * 0.8f);
//
//            // 从中心缩放：先移到中心，缩放，再移回去（或者直接缩放后修正偏移）
//            // 这里结合方块渲染的偏移一起处理
//            matrices.scale(scale, scale, scale);
//
//            // 5. 修正方块中心点
//            // renderBlockAsEntity 默认是从 (0,0,0) 画到 (1,1,1)
//            // 我们通常希望实体的中心在 (0.5, 0.5, 0.5)，所以需要偏移 -0.5
//            matrices.translate(-0.5, 0.0, -0.5); // Y轴通常对齐底部，如果想中心对齐Y轴改为 -0.5
//
//            // 6. 渲染方块
//            // 注意：这里使用原本的 blockRenderManager
//            // light 参数直接使用实体的亮度，或者你可以重新计算该位置的亮度
//            blockRenderManager.renderBlockAsEntity(blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
//
//            matrices.pop();
//        }
//    }

    private void renderBlockTrail(FlyingBlockEntity entity, BlockState blockState, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
        List<Vec3d> trail = entity.getTrailPositions();
        if (trail.isEmpty() || blockState == null) return;

        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();

        // 获取实体当前的插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        // 设置插值密度（每两个轨迹点之间插入的点数）
        int interpolationSteps = PuzzleRain.config.interpolationSteps; // 可以调整这个值来增加/减少插值密度

        // 渲染所有轨迹点（包括插值点）
        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startPos = trail.get(i);
            Vec3d endPos = trail.get(i + 1);

            // 渲染原始轨迹点
            renderTrailBlock(entityX, entityY, entityZ, startPos, i, trail.size(),
                    blockState, matrices, vertexConsumers, light, blockRenderManager);

            // 在两点之间插入额外点
            for (int step = 1; step <= interpolationSteps; step++) {
                float t = (float) step / (interpolationSteps + 1);
                Vec3d interpolatedPos = interpolatePosition(startPos, endPos, t);

                // 计算插值点在列表中的虚拟索引（用于缩放）
                float virtualIndex = i + t;

                renderTrailBlock(entityX, entityY, entityZ, interpolatedPos, virtualIndex, trail.size(),
                        blockState, matrices, vertexConsumers, light, blockRenderManager);
            }
        }

        // 渲染最后一个点
        if (trail.size() > 0) {
            Vec3d lastPos = trail.get(trail.size() - 1);
            renderTrailBlock(entityX, entityY, entityZ, lastPos, trail.size() - 1, trail.size(),
                    blockState, matrices, vertexConsumers, light, blockRenderManager);
        }
    }

    // 线性插值方法
    private Vec3d interpolatePosition(Vec3d start, Vec3d end, float t) {
        return new Vec3d(
                MathHelper.lerp(t, start.x, end.x),
                MathHelper.lerp(t, start.y, end.y),
                MathHelper.lerp(t, start.z, end.z)
        );
    }

    // 渲染单个轨迹方块
    private void renderTrailBlock(double entityX, double entityY, double entityZ,
                                  Vec3d trailPos, float index, int totalSize,
                                  BlockState blockState, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light,
                                  BlockRenderManager blockRenderManager) {
        matrices.push();

        // 计算相对位移
        double dx = trailPos.x - entityX;
        double dy = trailPos.y - entityY;
        double dz = trailPos.z - entityZ;

        matrices.translate(dx, dy, dz);

        // 动态缩放效果 - 基于索引位置
        // index 从 0（最旧）到 totalSize-1（最新）
        float scaleProgress = 1-( index / totalSize); // 0.0 到 1.0
        float scale = 0.2f + (scaleProgress * 0.8f); // 从 0.2 到 1.0

        // 可选：添加脉动效果
        float pulse = (float) Math.sin((System.currentTimeMillis() * 0.01f) + index) * 0.05f + 1.0f;
        scale *= pulse;

        matrices.scale(scale, scale, scale);

        // 透明度效果（如果支持）
        float alphaProgress = 1.0f - scaleProgress; // 最新的点最不透明
        // 注意：方块渲染通常不支持透明度，除非使用特殊渲染层

        // 修正方块中心点
        matrices.translate(-0.5, 0.0, -0.5);

        // 渲染方块
        blockRenderManager.renderBlockAsEntity(blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);

        matrices.pop();
    }

    @Override
    public Identifier getTexture(FlyingBlockEntity entity) {
        return null; // 我们使用方块渲染，不需要实体纹理
    }
}
