package com.puzzle_rain;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class IronBlockWandItem extends Item {

    public IronBlockWandItem(Settings settings) {
        super(settings);
    }

    // ==================== 右键方块交互 ====================
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        Hand hand = context.getHand();

        if (!world.isClient) {
            // 服务端逻辑
            onRightClickBlockServer(world, player, hand, context.getBlockPos(), context.getSide());
        } else {
            // 客户端逻辑
            onRightClickBlockClient(world, player, hand, context.getBlockPos(), context.getSide());
        }

        return ActionResult.SUCCESS;
    }

    /**
     * 服务端右键方块处理 - 预留实现
     */
    protected void onRightClickBlockServer(World world, PlayerEntity player, Hand hand,
                                           net.minecraft.util.math.BlockPos pos,
                                           net.minecraft.util.math.Direction side) {
        // TODO: 实现服务端右键方块功能
        // 示例：触发方块动画效果
    }

    /**
     * 客户端右键方块处理 - 预留实现
     */
    protected void onRightClickBlockClient(World world, PlayerEntity player, Hand hand,
                                           net.minecraft.util.math.BlockPos pos,
                                           net.minecraft.util.math.Direction side) {
        // TODO: 实现客户端右键方块功能
        // 示例：播放粒子效果或音效
    }

    // ==================== 右键实体交互 ====================
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, Entity entity, Hand hand) {
        if (!user.getWorld().isClient) {
            // 服务端逻辑
            onRightClickEntityServer(user.getWorld(), user, hand, entity);
        } else {
            // 客户端逻辑
            onRightClickEntityClient(user.getWorld(), user, hand, entity);
        }

        return ActionResult.SUCCESS;
    }

    /**
     * 服务端右键实体处理 - 预留实现
     */
    protected void onRightClickEntityServer(World world, PlayerEntity player, Hand hand, Entity entity) {
        // TODO: 实现服务端右键实体功能
        // 示例：给实体添加特殊效果
    }

    /**
     * 客户端右键实体处理 - 预留实现
     */
    protected void onRightClickEntityClient(World world, PlayerEntity player, Hand hand, Entity entity) {
        // TODO: 实现客户端右键实体功能
        // 示例：播放实体交互动画
    }

    // ==================== Ctrl键按下检测 ====================
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // 检测Ctrl键是否按下
        boolean isCtrlPressed = false;

        // 在客户端检测按键状态
        if (world.isClient) {
            // 这里需要访问MinecraftClient来检测按键
            // 注意：这只能在客户端执行
            isCtrlPressed = isCtrlKeyPressedClient();
        }

        if (isCtrlPressed) {
            onCtrlKeyPressed(world, user, hand, stack);
        }

        return super.use(world, user, hand);
    }

    /**
     * 客户端Ctrl键检测 - 预留实现
     */
    protected boolean isCtrlKeyPressedClient() {
        // TODO: 实现客户端Ctrl键检测
        return MinecraftClient.getInstance().options.sneakKey.isPressed();
        // 或自定义键位绑定
    }

    /**
     * Ctrl键按下处理 - 预留实现
     */
    protected void onCtrlKeyPressed(World world, PlayerEntity player, Hand hand, ItemStack stack) {
        if (!world.isClient) {
            onCtrlKeyPressedServer(world, player, hand, stack);
        } else {
            onCtrlKeyPressedClient(world, player, hand, stack);
        }
    }

    /**
     * 服务端Ctrl键按下处理 - 预留实现
     */
    protected void onCtrlKeyPressedServer(World world, PlayerEntity player, Hand hand, ItemStack stack) {
        // TODO: 实现服务端Ctrl键功能
        // 示例：切换魔杖模式
    }

    /**
     * 客户端Ctrl键按下处理 - 预留实现
     */
    protected void onCtrlKeyPressedClient(World world, PlayerEntity player, Hand hand, ItemStack stack) {
        // TODO: 实现客户端Ctrl键功能
        // 示例：显示模式切换提示
    }

    // ==================== 物品属性 ====================
}
