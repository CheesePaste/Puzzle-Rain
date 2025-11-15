package com.puzzle_rain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.puzzle_rain.PuzzleRain;
import com.puzzle_rain.BlockBounds;
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
        );
    }

    private static int setPos1(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.getBlockPos();
        PuzzleRain.getInstance().getAnimationManager().getSelectionManager().setFirstPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("First position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos1WithArgs(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "position");
        PuzzleRain.getInstance().getAnimationManager().getSelectionManager().setFirstPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("First position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos2(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = player.getBlockPos();
        PuzzleRain.getInstance().getAnimationManager().getSelectionManager().setSecondPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("Second position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int setPos2WithArgs(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "position");
        PuzzleRain.getInstance().getAnimationManager().getSelectionManager().setSecondPosition(player, pos);

        context.getSource().sendFeedback(() -> Text.literal("Second position set to " + pos.toShortString()), false);
        return 1;
    }

    private static int startAnimation(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        var selectionManager = PuzzleRain.getInstance().getAnimationManager().getSelectionManager();

        if (!selectionManager.hasBothPositions(player)) {
            context.getSource().sendError(Text.literal("You need to set both positions first!"));
            return 0;
        }

        BlockBounds bounds = selectionManager.getBounds(player);
        if (bounds.getVolume() > 1000) {
            context.getSource().sendError(Text.literal("Selection too large! Maximum 1000 blocks."));
            return 0;
        }

        PuzzleRain.getInstance().getAnimationManager().startAnimation(player.getServerWorld(), bounds);
        context.getSource().sendFeedback(() -> Text.literal("Started puzzle rain animation!"), false);

        return 1;
    }

    private static int clearSelection(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        PuzzleRain.getInstance().getAnimationManager().getSelectionManager().clearSelection(player);
        context.getSource().sendFeedback(() -> Text.literal("Selection cleared!"), false);

        return 1;
    }
}
