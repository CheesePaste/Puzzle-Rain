package com.puzzle_rain;

import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class BlockBounds {
    private final BlockPos min;
    private final BlockPos max;
    private final BlockPos center;

    public BlockBounds(BlockPos pos1, BlockPos pos2) {
        this.min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        this.max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
        this.center = new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
    }

    public List<BlockPos> getAllBlockPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    public BlockPos getMin() { return min; }
    public BlockPos getMax() { return max; }
    public BlockPos getCenter() { return center; }
    public int getVolume() {
        return (max.getX() - min.getX() + 1) *
                (max.getY() - min.getY() + 1) *
                (max.getZ() - min.getZ() + 1);
    }


}
