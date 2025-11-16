package com.puzzle_rain;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {
    private final Map<String, PlayerRegion> playerRegions = new ConcurrentHashMap<>();

    private static RegionManager instance;

    public static RegionManager getInstance() {
        if (instance == null) {
            instance = new RegionManager();
        }
        return instance;
    }

    private static class PlayerRegion {
        private BlockPos pos1;
        private BlockPos pos2;

        public boolean hasBothPositions() {
            return pos1 != null && pos2 != null;
        }

        public BlockBounds getBounds() {
            if (!hasBothPositions()) {
                return null;
            }
            return new BlockBounds(pos1, pos2);
        }

        public void clear() {
            pos1 = null;
            pos2 = null;
        }
    }

    public void setFirstPosition(PlayerEntity player, BlockPos pos) {
        if (player == null) return;


        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.computeIfAbsent(playerId, k -> new PlayerRegion());
        region.pos1 = pos;
    }

    public void setSecondPosition(PlayerEntity player, BlockPos pos) {
        if (player == null) return;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.computeIfAbsent(playerId, k -> new PlayerRegion());
        region.pos2 = pos;
    }

    public BlockPos getFirstPosition(ServerPlayerEntity player) {
        if (player == null) return null;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.get(playerId);
        return region != null ? region.pos1 : null;
    }

    public BlockPos getSecondPosition(ServerPlayerEntity player) {
        if (player == null) return null;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.get(playerId);
        return region != null ? region.pos2 : null;
    }

    public boolean hasBothPositions(ServerPlayerEntity player) {
        if (player == null) return false;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.get(playerId);
        return region != null && region.hasBothPositions();
    }

    public void clearSelection(ServerPlayerEntity player) {
        if (player == null) return;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.get(playerId);
        if (region != null) {
            region.clear();
        }
    }

    public BlockBounds getBounds(ServerPlayerEntity player) {
        if (player == null) return null;

        String playerId = player.getUuid().toString();
        PlayerRegion region = playerRegions.get(playerId);
        return region != null ? region.getBounds() : null;
    }
}