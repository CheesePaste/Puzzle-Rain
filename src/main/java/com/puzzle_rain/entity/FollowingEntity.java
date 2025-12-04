package com.puzzle_rain.entity;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.NoSuchElementException;

public class FollowingEntity extends BaseBlockEntity implements Targetable{
    Entity target;
    float closeDistance=1;
    public FollowingEntity(EntityType<?> type, World world, Entity target, BlockPos pos, BlockState state) {

        super(type, world,pos,state);
        this.target=target;
    }

    public FollowingEntity(EntityType<FollowingEntity> followingEntityEntityType, World world) {
        super(followingEntityEntityType,world);
    }

    @Override
    public void tick() {

        if (target!=null){
            //PuzzleRain.LOGGER.info(target.getPos().toString());
            this.addVelocity(this.getDir().multiply(0.1));
            this.move(MovementType.SELF, this.getVelocity());
            //this.setPosition(this.target.getPos());
        }
        //this.move(MovementType.SELF, new Vec3d(0,1,0));
        //PuzzleRain.LOGGER.info("FUCK");
        super.tick();
    }

    @Override
    public Vec3d getDir() {
        if(target!=null){
            return target.getPos().subtract(this.getPos()).normalize();
        }
        return null;
    }

    @Override
    public boolean isClose() {
        if(target!=null&&this.getPos().isWithinRangeOf(target.getPos(),closeDistance,closeDistance)){
            return true;
        }
        if(target==null){
            throw new RuntimeException("Target is null");
        }
        return false;
    }
}
