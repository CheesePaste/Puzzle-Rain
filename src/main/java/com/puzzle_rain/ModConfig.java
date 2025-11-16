package com.puzzle_rain;

import com.jcraft.jorbis.Block;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

@Config(name = "puzzle-rain")
public class ModConfig implements ConfigData {

    public BlockPos StartPos;
    public BlockPos EndPos;

    public List<V3> EmitterPoints=new ArrayList<>();

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
