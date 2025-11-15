package com.puzzle_rain;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class SelectionManager {
    private final Map<ServerPlayerEntity, BlockPos> firstPositions = new HashMap<>();
    private final Map<ServerPlayerEntity, BlockPos> secondPositions = new HashMap<>();

    public void setFirstPosition(ServerPlayerEntity player, BlockPos pos) {
        firstPositions.put(player, pos);
    }

    public void setSecondPosition(ServerPlayerEntity player, BlockPos pos) {
        secondPositions.put(player, pos);
    }

    public BlockPos getFirstPosition(ServerPlayerEntity player) {
        return firstPositions.get(player);
    }

    public BlockPos getSecondPosition(ServerPlayerEntity player) {
        return secondPositions.get(player);
    }

    public boolean hasBothPositions(ServerPlayerEntity player) {
        return firstPositions.containsKey(player) && secondPositions.containsKey(player);
    }

    public void clearSelection(ServerPlayerEntity player) {
        firstPositions.remove(player);
        secondPositions.remove(player);
    }

    public BlockBounds getBounds(ServerPlayerEntity player) {
        if (!hasBothPositions(player)) return null;

        BlockPos pos1 = firstPositions.get(player);
        BlockPos pos2 = secondPositions.get(player);

        return new BlockBounds(pos1, pos2);
    }
}