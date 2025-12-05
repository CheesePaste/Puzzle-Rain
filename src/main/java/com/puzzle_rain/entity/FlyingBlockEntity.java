package com.puzzle_rain.entity;

import com.jcraft.jogg.Packet;
import com.puzzle_rain.ControlEnum;
import com.puzzle_rain.GravitationalDistortionShader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FlyingBlockEntity extends BaseBlockEntity {
    // 特有字段
    private double gravityRadius = 3.0;
    private boolean isCreatingGravity = true;

    // 构造函数
    public FlyingBlockEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public FlyingBlockEntity(World world, BlockPos pos, BlockState blockState) {
        super(ModEntities.FLYING_BLOCK_ENTITY, world, pos, blockState);
    }

    // 实现抽象方法
    @Override
    public void tick() {
        // 更新位置（基于速度）
        if (this.getVelocity().lengthSquared() > 0.001) {
            this.move(MovementType.SELF, this.getVelocity());
        }
        super.tick();
    }

    @Override
    public boolean isNoClip() {
        return true;
    }


    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        // 实体移除时清理引力场
        if (this.getWorld().isClient()) {
            GravitationalDistortionShader.removeGravityCenter(this.getPos());
        }
    }

    // 特有方法
    public double getGravityRadius() {
        return gravityRadius;
    }

    public void setGravityRadius(double radius) {
        this.gravityRadius = radius;
    }

    public boolean isCreatingGravity() {
        return isCreatingGravity;
    }

    public void setCreatingGravity(boolean creatingGravity) {
        isCreatingGravity = creatingGravity;
    }

}