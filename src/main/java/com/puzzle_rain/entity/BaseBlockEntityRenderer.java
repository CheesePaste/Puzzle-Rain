package com.puzzle_rain.entity;

import com.puzzle_rain.MyRenderLayers;
import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.entity.BaseBlockEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class BaseBlockEntityRenderer extends EntityRenderer<BaseBlockEntity> {
    public BaseBlockEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(BaseBlockEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        BlockState blockState = entity.getBlockState();
        light = 0xF000F0;
        if (blockState.getRenderType() != BlockRenderType.MODEL) {
            return;
        }
        renderEmojiOnFace(entity,matrices,vertexConsumers,tickDelta);

        matrices.push();

        // 应用旋转动画
        //matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.getYaw(tickDelta)));

        // 应用缩放（可选）
        //matrices.translate(-0.5, 0.0, -0.5);
        renderTrail3(entity,matrices,vertexConsumers,tickDelta);
        matrices.push();
        if(PuzzleRain.config.useBlockTrail) renderBlockTrail(entity,blockState,matrices,vertexConsumers,tickDelta,light);
        //renderEnergyField(entity,matrices,vertexConsumers,tickDelta);
        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        renderBlockFacingPlayer(blockState,entity,matrices,vertexConsumers,tickDelta,light);
        matrices.push();
        if(PuzzleRain.config.useEnergyField) renderEnergyField(entity,matrices,vertexConsumers,tickDelta,light);
        //matrices.translate(-0.5, 0.0, -0.5);
        //blockRenderManager.renderBlockAsEntity(blockState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);





        matrices.pop();
        matrices.pop();
        matrices.pop();


        // 3. 渲染能量场 (解除注释)
        // 传入 light 变量
        //renderEnergyField(entity, matrices, vertexConsumers, tickDelta, light);

        //matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }


    private void renderBlockFacingPlayer(BlockState blockState, BaseBlockEntity entity,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         float tickDelta, int light) {

        matrices.push();

//        // 1. 移动到实体位置（包含插值）
//        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
//        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
//        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());
//        matrices.translate(x, y, z);

        // 2. 平滑旋转（使用lerp插值）
        float targetYaw = calculateTargetYaw(entity, tickDelta);
        float targetPitch = calculateTargetPitch(entity, tickDelta);

        // 应用平滑旋转
        entity.setYaw(MathHelper.lerpAngleDegrees(0.1f, entity.getYaw(), targetYaw));
        //entity.setRenderPitch(MathHelper.lerp(0.1f, entity.getRenderPitch(), targetPitch));

        // 应用旋转到矩阵

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        //matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(entity.getRenderPitch()));

        // 3. 调整方块位置（使旋转中心在方块中心）
        matrices.translate(-0.5, 0, -0.5);

        BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
        // 4. 渲染方块
        blockRenderManager.renderBlockAsEntity(
                blockState,
                matrices,
                vertexConsumers,
                light,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
    }

    private float calculateTargetYaw(BaseBlockEntity entity, float tickDelta) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        PlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) return 0;

        // 计算实体指向玩家的方向
        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();

        // 转换为角度（-180到180）
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;

        // 确保角度在0-360范围内
        return MathHelper.wrapDegrees(yaw);
    }

    private float calculateTargetPitch(BaseBlockEntity entity, float tickDelta) {
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        PlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null) return 0;

        // 计算水平距离和垂直距离
        double dx = player.getX() - entity.getX();
        double dy = player.getY() - entity.getY();
        double dz = player.getZ() - entity.getZ();

        // 计算水平距离
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // 计算俯仰角（看向玩家的高度）
        float pitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDistance));

        // 调整范围
        return MathHelper.clamp(pitch, -90.0f, 90.0f);
    }



    private void renderEmojiOnFace(BaseBlockEntity entity, MatrixStack matrices,
                                   VertexConsumerProvider vertexConsumers, float tickDelta) {
        // --- 开始渲染颜文字 ---

        // 定义你要渲染的颜文字
        String kaomoji = "(❁´◡`❁))";
        Text text = Text.of(kaomoji);

        // 2. 压入矩阵栈，隔离变换，防止影响后续渲染
        matrices.push();


        // 3. 位移 (Translation)
        // 将原点移动到实体头顶上方。
        // entity.getHeight() 获取实体高度，+0.5f 是额外的悬浮距离
        matrices.translate(0.0D, entity.getHeight() + 0.5F, 0.0D);

        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(entity.getYaw()));
        // 4. 旋转 (Rotation / Billboarding)
        // 关键步骤：让文字始终面向相机（玩家）。
        // this.dispatcher.getRotation() 获取的是当前相机的旋转四元数。
        //matrices.multiply(this.dispatcher.getRotation());

        // 5. 缩放 (Scaling)
        // Minecraft 的字体默认非常大，通常需要缩小到 0.025 倍左右。
        // Y 轴取负值是因为 Minecraft 的文字渲染坐标系 Y 轴是向下的，而世界坐标系 Y 轴向上。
        float scale = 0.025F;
        matrices.scale(-scale, -scale, scale);

        // 6. 计算居中位置
        float textWidth = this.getTextRenderer().getWidth(text);
        float xOffset = -textWidth / 2f; // 向左移动一半宽度以居中

        // 7. 绘制文字
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        positionMatrix.translate(0.5f,0,0);

        // 使用 TextRenderer 绘制
        this.getTextRenderer().draw(
                text,
                xOffset,
                0,
                0xFFFFFF, // 颜色 (白色)
                true,    // 是否有阴影 (true 会更有立体感，但颜文字通常不需要)
                positionMatrix,
                vertexConsumers,
                // 渲染模式：NORMAL (普通), SEE_THROUGH (透视/穿墙可见), POLYGON_OFFSET (防止闪烁)
                // 如果你想让颜文字像名字一样即使隔着墙也能看见，可以使用 SEE_THROUGH
                TextRenderer.TextLayerType.NORMAL,
                0,        // 背景颜色 (0 为透明)
                0xF000F0     // 光照等级 (通常传参数里的 light，或者 0xF000F0 使其全亮)
        );

        // 8. 弹出矩阵栈，恢复状态
        matrices.pop();

//        matrices.push();
//
//        matrices.translate(0.5, 2.0, 0.5); // 保证完全在方块外看得到
//
//
//
//        float scale = 0.025f;
//        matrices.scale(-scale, -scale, scale);
//
//        Text text = Text.literal("(┬┬﹏┬┬))");
//        float width = MinecraftClient.getInstance().textRenderer.getWidth(text);
//
//
//        MinecraftClient.getInstance().textRenderer.draw(
//                text,
//                -width / 2f,
//                0,
//                0xFFFFFF,
//                false,
//                matrices.peek().getPositionMatrix(),
//                vertexConsumers,
//                TextRenderer.TextLayerType.NORMAL,
//                0,
//                0xF000F0
//        );
//
//        matrices.pop();
    }


    // 渲染球形能量场
    private void renderEnergyField(BaseBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
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
    private void renderTrail(BaseBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
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
//    private void renderBlockTrail(BaseBlockEntity entity, BlockState blockState, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
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

    private void renderBlockTrail(BaseBlockEntity entity, BlockState blockState, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta, int light) {
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

    private void renderTrail3(BaseBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        List<Vec3d> trail = entity.getTrailPositions();
        if (trail.size() < 2) return;

        // 使用我们自定义的 Layer
        VertexConsumer buffer = vertexConsumers.getBuffer(MyRenderLayers.TRAIL_GLOW);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        // 拖尾配置
        float width = 0.4f; // 稍微宽一点，因为边缘会淡出

        // 颜色 (RGBA) - 比如青色荧光
        float r = 0.2f;
        float g = 0.8f;
        float b = 1.0f;
        float maxAlpha = 1.0f; // Shader 会处理边缘透明，这里设为 1 即可

        // 插值位置
        double entityX = MathHelper.lerp(tickDelta, entity.prevX, entity.getX());
        double entityY = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double entityZ = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ());

        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d startWorld = trail.get(i);
            Vec3d endWorld = trail.get(i + 1);
            Vec3d dir = endWorld.subtract(startWorld);
            if (dir.lengthSquared() < 0.0001) continue;

            // Billboarding 计算
            Vec3d toCamera = cameraPos.subtract(startWorld);
            Vec3d right = dir.crossProduct(toCamera).normalize().multiply(width / 2.0);

            // 顶点计算
            Vec3d v1 = startWorld.subtract(right);
            Vec3d v2 = startWorld.add(right);
            Vec3d v3 = endWorld.add(right);
            Vec3d v4 = endWorld.subtract(right);

            // 相对坐标
            float x1 = (float) (v1.x - entityX); float y1 = (float) (v1.y - entityY); float z1 = (float) (v1.z - entityZ);
            float x2 = (float) (v2.x - entityX); float y2 = (float) (v2.y - entityY); float z2 = (float) (v2.z - entityZ);
            float x3 = (float) (v3.x - entityX); float y3 = (float) (v3.y - entityY); float z3 = (float) (v3.z - entityZ);
            float x4 = (float) (v4.x - entityX); float y4 = (float) (v4.y - entityY); float z4 = (float) (v4.z - entityZ);

            // 沿路径长度的纹理坐标 U (可选，如果 Shader 不需要沿长度变化，可以忽略)
            // 这里重点是 V 坐标：边缘是 0 和 1，Shader 里会处理成透明

            // 头部渐隐 Alpha
            float alpha = maxAlpha * ((float) i / trail.size());

            // 绘制顶点
            // 关键点：
            // 1. .texture(u, v) -> v 必须是 0 和 1，用来在 shader 里计算距离中心的距离
            // 2. .color() -> 传入基础颜色
            // 3. 不再需要 .light()，因为 RenderLayer 设置了不写入深度且 Shader 忽略光照

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).texture(0f, 0f); // V=0 边缘
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).texture(0f, 1f); // V=1 边缘
            buffer.vertex(matrix, x3, y3, z3).color(r, g, b, alpha).texture(1f, 1f); // V=1 边缘
            buffer.vertex(matrix, x4, y4, z4).color(r, g, b, alpha).texture(1f, 0f); // V=0 边缘
        }
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
    public Identifier getTexture(BaseBlockEntity entity) {
        return null; // 我们使用方块渲染，不需要实体纹理
    }
}
