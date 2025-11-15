package com.puzzle_rain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.BlockBounds;
import com.puzzle_rain.RegionManager;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.*;

public class PuzzleRainCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("puzzlerain")
                .then(literal("pos1")
                        .executes(PuzzleRainCommand::setPos1)
                        .then(argument("position", BlockPosArgumentType.blockPos())
                                .executes(PuzzleRainCommand::setPos1WithArgs)))
                .then(literal("pos2")
                        .executes(PuzzleRainCommand::setPos2)
                        .then(argument("position", BlockPosArgumentType.blockPos())
                                .executes(PuzzleRainCommand::setPos2WithArgs)))
                .then(literal("start")
                        .executes(PuzzleRainCommand::startAnimation))
                .then(literal("clear")
                        .executes(PuzzleRainCommand::clearSelection))
                .then(literal("status")
                        .executes(PuzzleRainCommand::getStatus))
        );
    }

    private static int setPos1(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.getBlockPos();
        RegionManager.getInstance().setFirstPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("First position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos1WithArgs(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "position");
        RegionManager.getInstance().setFirstPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("First position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos2(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.getBlockPos();
        RegionManager.getInstance().setSecondPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("Second position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos2WithArgs(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "position");
        RegionManager.getInstance().setSecondPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("Second position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int startAnimation(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (!RegionManager.getInstance().hasBothPositions(player)) {
            context.getSource().sendError(Text.literal("You need to set both positions first!"));
            return 0;
        }

        BlockBounds bounds = RegionManager.getInstance().getBounds(player);
        if (bounds == null) {
            context.getSource().sendError(Text.literal("Failed to get selection bounds!"));
            return 0;
        }

        if (bounds.getVolume() > 1000) {
            context.getSource().sendError(Text.literal("Selection too large! Maximum 1000 blocks."));
            return 0;
        }

        // 检查选择区域是否过大以避免服务器崩溃
        int xSize = Math.abs(bounds.getMax().getX() - bounds.getMin().getX());
        int ySize = Math.abs(bounds.getMax().getY() - bounds.getMin().getY());
        int zSize = Math.abs(bounds.getMax().getZ() - bounds.getMin().getZ());

        if (xSize > 64 || ySize > 64 || zSize > 64) {
            context.getSource().sendError(Text.literal("Selection too large in one dimension! Maximum 64 blocks in any direction."));
            return 0;
        }

        // Generate unique task ID for this animation
        String taskId = player.getUuid().toString() + "_" + System.currentTimeMillis();

        // Start the animation task
        PuzzleRain.getInstance()
            .getAnimationTaskManager()
            .startAnimation(player.getServerWorld(), bounds, taskId);

        context.getSource().sendFeedback(() -> Text.literal("Started puzzle rain animation for " + bounds.getVolume() + " blocks!"), false);

        return 1;
    }

    private static int clearSelection(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        RegionManager.getInstance().clearSelection(player);
        context.getSource().sendFeedback(() -> Text.literal("Selection cleared!"), false);

        return 1;
    }

    private static int getStatus(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        int activeTasks = PuzzleRain.getInstance().getAnimationTaskManager().getActiveTaskCount();
        context.getSource().sendFeedback(() -> Text.literal("Active animation tasks: " + activeTasks), false);

        return 1;
    }
}
