package com.puzzle_rain.entity;

import com.puzzle_rain.entity.HammerStrikeEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class HammerStrikeEntityRenderer extends EntityRenderer<HammerStrikeEntity> {

    public HammerStrikeEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(HammerStrikeEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // 这个实体主要是逻辑控制，不需要渲染可见的几何体
        // 但我们可以在这里添加粒子效果或其他视觉反馈

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(HammerStrikeEntity entity) {
        // 返回一个透明的纹理，因为这个实体不需要可见纹理
        return Identifier.of("minecraft", "textures/block/barrier.png");
    }

    @Override
    protected int getBlockLight(HammerStrikeEntity entity, BlockPos pos) {
        // 返回最大亮度，确保在任何光照条件下都可见（如果需要渲染的话）
        return 15;
    }
}
