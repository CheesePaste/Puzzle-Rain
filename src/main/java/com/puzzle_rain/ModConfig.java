package com.puzzle_rain;

import com.jcraft.jorbis.Block;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

@Config(name = "puzzle-rain")
public class ModConfig implements ConfigData {

    public int startPosX;
    public int startPosY;
    public int startPosZ;
    public int endPosX;
    public int endPosY;
    public int endPosZ;

    public ControlEnum controlType = ControlEnum.Velocity_roll;
    public double maxRadius=5;
    public double omega=5;

    public float factor = 0.2f;
    public List<V3> EmitterPoints=new ArrayList<>();
    public float factor_spread=5f;
    public boolean usespecificPos=true;
    public int specificPosX;
    public int specificPosY;
    public double specificPosZ;
    public List<String>ignoreBlocks=List.of(
            Registries.BLOCK.getId(Blocks.AIR).getPath()
    );

    static class V3{
        public int x,y,z;
        V3(){

        }
        V3(int x,int y,int z){
            this.x=x;
            this.y=y;
            this.z=z;
        }
        public BlockPos ToBP(){
            return new BlockPos(x,y,z);
        }
    }

}
