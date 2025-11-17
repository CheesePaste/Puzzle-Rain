package com.puzzle_rain;

import com.jcraft.jorbis.Block;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
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

    public float factor = 0.2f;
    public List<V3> EmitterPoints=new ArrayList<>();
    public float factor_spread=5f;

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
