package com.puzzle_rain;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

@Config(name = "puzzle-rain")
public class ModConfig implements ConfigData {

    public V3 startPos=new V3();
    public V3 endPos=new V3();

    public ControlEnum controlType = ControlEnum.Velocity_roll;
    public double maxRadius=5;
    public double omega=5;

    public float factor = 0.2f;
    public List<V3> EmitterPoints=new ArrayList<>();
    public float factor_spread=5f;
    public boolean usespecificPos=true;
    public V3 specificPos=new V3();
    public List<String>ignoreBlocks=List.of(
            Registries.BLOCK.getId(Blocks.AIR).getPath()
    );
    @ConfigEntry.Gui.CollapsibleObject


    public boolean useCenterPoint = false;
    public boolean useReverse = false;
    public boolean useAutoCenterPoint=true;

    public V3 Dir=new V3();
    public float spherefactor=5;
    public boolean useEnergyField=false;
    public int interpolationSteps=3;
    public boolean useBlockTrail=false;


    public static class V3{
        public double x=0,y=0,z=0;
        V3(){

        }

        public BlockPos ToBP(){
            return new BlockPos((int) x, (int) y, (int) z);
        }

        public Vec3d ToVec3d(){
            return new Vec3d(x,y,z);
        }
        public void set(BlockPos pos){
            x=pos.getX();
            y=pos.getY();
            z=pos.getZ();
        }
    }

}
