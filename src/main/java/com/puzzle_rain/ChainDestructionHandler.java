// ChainDestructionHandler.java
package com.puzzle_rain;

import com.puzzle_rain.PuzzleRain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class ChainDestructionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChainDestructionHandler");

    // 配置参数
    private static final int MAX_BLOCKS_TO_BREAK = 10000;
    private static final int SEARCH_RADIUS = 500;

    public static ActionResult onBlockInteract(PlayerEntity player, World world, BlockPos pos) {
        // 检查是否手持钻石
        ItemStack mainHandStack = player.getMainHandStack();
        if (!mainHandStack.isOf(Items.DIAMOND)) {
            return ActionResult.PASS;
        }

        // 检查是否是潜行右键（防止误触）
        if (!player.isSneaking()) {
            return ActionResult.PASS;
        }

        // 只在服务器端执行
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        // 检查权限
        if (!player.isCreative() && !player.canModifyBlocks()) {
            player.sendMessage(Text.literal("你没有权限破坏方块"), true);
            return ActionResult.FAIL;
        }

        // 执行连锁破坏
        performChainDestruction(world, pos, player);

        return ActionResult.SUCCESS;
    }

    private static void performChainDestruction(World world, BlockPos startPos, PlayerEntity player) {
        BlockState startState = world.getBlockState(startPos);

        // 检查起始方块
        if (startState.isAir() || isUnbreakable(startState, world, startPos)) {
            player.sendMessage(Text.literal("无法破坏这个方块"), true);
            return;
        }

        Set<BlockPos> destroyedBlocks = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(startPos);
        destroyedBlocks.add(startPos);

        int blocksBroken = 0;
        long startTime = System.currentTimeMillis();
        final long MAX_OPERATION_TIME = 5000;

        // BFS算法查找相连方块
        while (!queue.isEmpty() && blocksBroken < MAX_BLOCKS_TO_BREAK) {
            BlockPos currentPos = queue.poll();

            // 检查超时
            if (System.currentTimeMillis() - startTime > MAX_OPERATION_TIME) {
                LOGGER.warn("Chain destruction operation timed out");
                player.sendMessage(Text.literal("操作超时，已破坏 " + blocksBroken + " 个方块"), true);
                break;
            }

            // 破坏当前方块
            if (breakBlock(world, currentPos, player)) {
                blocksBroken++;

                // 检查六个方向的相邻方块
                for (BlockPos neighbor : getNeighbors(currentPos)) {
                    if (!destroyedBlocks.contains(neighbor) &&
                            isWithinRange(startPos, neighbor) &&
                            isValidBlock(world.getBlockState(neighbor)) &&
                            !isUnbreakable(world.getBlockState(neighbor), world, neighbor)) {

                        queue.offer(neighbor);
                        destroyedBlocks.add(neighbor);

                        if (destroyedBlocks.size() >= MAX_BLOCKS_TO_BREAK) {
                            break;
                        }
                    }
                }
            }

            // 进度反馈
            if (blocksBroken % 100 == 0 && blocksBroken > 0) {
                player.sendMessage(Text.literal("已破坏 " + blocksBroken + " 个方块..."), true);
            }
        }

        // 完成反馈
        if (blocksBroken > 0) {
            player.sendMessage(Text.literal("连锁破坏完成！共破坏 " + blocksBroken + " 个方块"), false);
            LOGGER.info("Chain destruction completed: {} blocks broken", blocksBroken);
        } else {
            player.sendMessage(Text.literal("没有方块被破坏"), true);
        }
    }

    private static boolean breakBlock(World world, BlockPos pos, PlayerEntity player) {
        BlockState state = world.getBlockState(pos);

        if (state.isAir() || isUnbreakable(state, world, pos)) {
            return false;
        }

        try {
            // 使用玩家的工具来破坏方块，触发正确的掉落和经验
            if (player.canHarvest(state)) {
                world.breakBlock(pos, true, player);
                return true;
            } else {
                // 如果玩家不能正常采集，直接破坏但不掉落物品
                world.breakBlock(pos, false);
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Error breaking block at {}", pos, e);
            return false;
        }
    }

    private static boolean isValidBlock(BlockState state) {
        return !state.isAir();
    }

    private static boolean isUnbreakable(BlockState state, World world, BlockPos pos) {
        return state.isIn(BlockTags.WITHER_IMMUNE) ||
                state.getHardness(world, pos) < 0; // 硬度为负的方块不可破坏
    }

    private static boolean isWithinRange(BlockPos start, BlockPos current) {
        return Math.abs(start.getX() - current.getX()) <= SEARCH_RADIUS &&
                Math.abs(start.getY() - current.getY()) <= SEARCH_RADIUS &&
                Math.abs(start.getZ() - current.getZ()) <= SEARCH_RADIUS;
    }

    private static BlockPos[] getNeighbors(BlockPos pos) {
        return new BlockPos[]{
                pos.up(),      // 上
                pos.down(),    // 下
                pos.north(),   // 北
                pos.south(),   // 南
                pos.east(),    // 东
                pos.west()     // 西
        };
    }
}