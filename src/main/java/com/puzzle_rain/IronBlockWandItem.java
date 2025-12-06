package com.puzzle_rain;

import com.puzzle_rain.entity.FollowingEntity;
import com.puzzle_rain.entity.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 铁块魔杖物品类
 * 提供三种模式：跟随模式、静止模式、放置模式
 */
public class IronBlockWandItem extends Item {


    // 定义三种模式
    public enum WandMode {
        FOLLOW(0, "跟随模式"),
        STATIC(1, "静止模式"),
        PLACE(2, "放置模式");

        private final int index;
        private final String displayName;

        WandMode(int index, String displayName) {
            this.index = index;
            this.displayName = displayName;
        }

        public int getIndex() { return index; }
        public String getDisplayName() { return displayName; }

        // 获取下一个模式（循环）
        public WandMode next() {
            int nextIndex = (this.index + 1) % values().length;
            return fromIndex(nextIndex);
        }

        // 从索引获取模式
        public static WandMode fromIndex(int index) {
            for (WandMode mode : values()) {
                if (mode.index == index) {
                    return mode;
                }
            }
            return FOLLOW; // 默认值
        }
    }

    // 组件管理
    private static final String MODE_KEY = "wand_mode";

    public IronBlockWandItem(Settings settings) {
        super(settings
                .maxCount(1) // 魔杖通常只能拿一个
                // 添加默认组件：初始模式为FOLLOW
                .component(ModComponents.WAND_MODE, new com.puzzle_rain.WandModeComponent(0))
        );
    }

    // ==================== 组件数据管理 ====================

    /**
     * 获取魔杖当前模式
     */
    public static WandMode getMode(ItemStack stack) {
        if (stack.isEmpty()) {
            return WandMode.FOLLOW;
        }

        WandModeComponent component = stack.get(ModComponents.WAND_MODE);
        if (component != null) {
            return WandMode.fromIndex(component.getModeIndex());
        }
        return WandMode.FOLLOW; // 默认模式
    }

    /**
     * 设置魔杖模式并返回新的物品堆栈
     */
    public static ItemStack setMode(ItemStack stack, WandMode mode) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack newStack = stack.copy();
        newStack.set(ModComponents.WAND_MODE, new WandModeComponent(mode.getIndex()));
        return newStack;
    }

    /**
     * 切换到下一个模式 (服务端安全方法)
     * 这个方法应该在服务器端调用，以确保数据同步
     */
    public static void switchToNextMode(PlayerEntity player, Hand hand) {
        if (player == null) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof IronBlockWandItem)) return;

        WandMode currentMode = getMode(stack);
        WandMode nextMode = currentMode.next();

        // 设置新模式，ItemStack.set() 会创建副本，所以需要重新设置回玩家手中
        ItemStack newStack = setMode(stack, nextMode);
        player.setStackInHand(hand, newStack);

        // 发送反馈消息
        if (!player.getWorld().isClient()) {
            player.sendMessage(
                    Text.literal("魔杖模式: §e" + nextMode.getDisplayName()).formatted(Formatting.YELLOW),
                    true
            );
        }
    }

    /**
     * 客户端快速切换模式（仅用于预览，实际切换应在服务端完成）
     */
    public static void switchToNextModeClient(PlayerEntity player, Hand hand) {
        if (player == null) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof IronBlockWandItem)) return;

        WandMode currentMode = getMode(stack);
        WandMode nextMode = currentMode.next();

        // 客户端预览消息
        player.sendMessage(
                Text.literal("魔杖模式 -> " + nextMode.getDisplayName()).formatted(Formatting.GOLD),
                true
        );
    }

    // ==================== 右键方块交互 ====================
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        Hand hand = context.getHand();
        BlockPos pos = context.getBlockPos();
        BlockState blockState = world.getBlockState(pos);
        ItemStack stack = player != null ? player.getStackInHand(hand) : ItemStack.EMPTY;

        if (world.isClient()) {
            // 客户端只需要显示效果
            onRightClickBlockClient(world, player, hand, pos, context.getSide());
            return ActionResult.SUCCESS;
        }

        // 服务端逻辑 - 根据当前模式处理
        WandMode mode = getMode(stack);

        // 检查玩家是否能够修改世界
        if (player != null && !player.getAbilities().allowModifyWorld) {
            return ActionResult.FAIL;
        }

        switch (mode) {
            case FOLLOW:
                // 模式1：创建跟随实体
                createFollowingEntity(world, player, pos, blockState);
                break;

            case STATIC:
                // 模式2：创建静止实体
                createStaticEntity(world, player, pos, blockState);
                break;

            case PLACE:
                // 模式3：直接放置方块
                // 注意：对于放置模式，右键方块是放置而不是转化，所以这里不破坏原方块
                // 如果需要其他功能，可以在这里扩展
                return ActionResult.PASS;
        }

        // 破坏原方块（对于FOLLOW和STATIC模式）
        if (mode != WandMode.PLACE) {
            world.breakBlock(pos, false, player);
        }

        // 播放使用音效
        if (player != null) {
            player.swingHand(hand, true);
        }

        return ActionResult.SUCCESS;
    }

    /**
     * 创建跟随实体
     */
    private void createFollowingEntity(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (world.isClient()) return;

        FollowingEntity entity = new FollowingEntity(ModEntities.FollowingEntity, world, player, pos, state);
        entity.setTarget(player); // 设置跟随目标

        // 设置实体位置为中心点
        entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // 生成实体
        if (world.spawnEntity(entity)) {
            PuzzleRain.LOGGER.info("创建跟随实体，ID: {}", entity.getId());

            // 发送反馈消息
            if (player != null) {
                player.sendMessage(Text.literal("方块已激活跟随模式").formatted(Formatting.GREEN), true);
            }
        } else {
            PuzzleRain.LOGGER.error("创建跟随实体失败");
        }
    }

    /**
     * 创建静止实体
     */
    private void createStaticEntity(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (world.isClient()) return;

        FollowingEntity entity = new FollowingEntity(ModEntities.FollowingEntity, world, null, pos, state);
        // 不设置目标，实体将保持静止
        entity.setVelocity(0, 0, 0);

        // 设置实体位置为中心点
        entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // 生成实体
        if (world.spawnEntity(entity)) {
            PuzzleRain.LOGGER.info("创建静止实体，ID: {}", entity.getId());

            // 发送反馈消息
            if (player != null) {
                player.sendMessage(Text.literal("方块已激活静止模式").formatted(Formatting.GREEN), true);
            }
        } else {
            PuzzleRain.LOGGER.error("创建静止实体失败");
        }
    }

    /**
     * 客户端右键方块处理
     */
    protected void onRightClickBlockClient(World world, PlayerEntity player, Hand hand,
                                           BlockPos pos, Direction side) {
        // 播放使用动画
        if (player != null) {
            player.swingHand(hand, true);
        }

        // 可以在这里添加粒子效果或音效
        // 例如：播放使用音效
        // player.playSound(SoundEvents.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.0F);
    }

    // ==================== 右键实体交互 ====================
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) {
            onRightClickEntityClient(user, hand);
            return ActionResult.SUCCESS;
        }

        // 服务端逻辑
        return onRightClickEntityServer(user.getWorld(), user, hand, entity, stack);
    }

    /**
     * 服务端右键实体处理
     */
    protected ActionResult onRightClickEntityServer(World world, PlayerEntity player, Hand hand,
                                                    LivingEntity entity, ItemStack stack) {
        // 检查是否为FollowingEntity
        if (!(entity instanceof FollowingEntity)) {
            return ActionResult.PASS;
        }

        FollowingEntity followingEntity = (FollowingEntity) entity;
        WandMode mode = getMode(stack);

        switch (mode) {
            case FOLLOW:
                // 模式1：设置跟随玩家
                followingEntity.setTarget(player);
                player.sendMessage(Text.literal("方块开始跟随你").formatted(Formatting.GREEN), true);
                break;

            case STATIC:
                // 模式2：取消跟随，保持静止
                followingEntity.setTarget(null);
                followingEntity.setVelocity(0, 0, 0);
                player.sendMessage(Text.literal("方块已静止").formatted(Formatting.YELLOW), true);
                break;

            case PLACE:
                // 模式3：变回方块
                BlockState storedState = followingEntity.getBlockState();
                BlockPos entityPos = entity.getBlockPos();

                // 移除实体并放置方块
                entity.discard();
                world.setBlockState(entityPos, storedState);
                player.sendMessage(Text.literal("方块已还原").formatted(Formatting.GREEN), true);
                break;
        }

        // 播放使用动画
        player.swingHand(hand, true);

        return ActionResult.SUCCESS;
    }

    /**
     * 客户端右键实体处理
     */
    protected void onRightClickEntityClient(PlayerEntity player, Hand hand) {
        // 播放交互动画
        player.swingHand(hand, true);
    }

    // ==================== 工具提示 ====================

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);

        // 显示当前模式
        WandMode mode = getMode(stack);
        tooltip.add(Text.literal("§7模式: §e" + mode.getDisplayName()));
        tooltip.add(Text.literal("§8按 M 键切换模式").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.empty());
        tooltip.add(Text.literal("§6模式1 - 跟随").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§f右键方块: §7创建跟随玩家的实体").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§f右键实体: §7让实体开始跟随").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§6模式2 - 静止").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§f右键方块: §7创建静止实体").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§f右键实体: §7让实体停止移动").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§6模式3 - 放置").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§f右键实体: §7将实体变回方块").formatted(Formatting.GRAY));
    }

    // ==================== 属性修改器 ====================


    // ==================== 其他实用方法 ====================

    /**
     * 获取模式对应的颜色
     */
    public static Formatting getModeColor(WandMode mode) {
        return switch (mode) {
            case FOLLOW -> Formatting.GREEN;
            case STATIC -> Formatting.YELLOW;
            case PLACE -> Formatting.BLUE;
        };
    }

    /**
     * 检查物品是否是铁块魔杖
     */
    public static boolean isIronBlockWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IronBlockWandItem;
    }

    /**
     * 获取魔杖的显示名称（包含模式信息）
     */
    public static Text getWandDisplayName(ItemStack stack) {
        if (!isIronBlockWand(stack)) {
            return Text.literal("未知魔杖");
        }

        WandMode mode = getMode(stack);
        return Text.literal("铁块魔杖 [")
                .append(Text.literal(mode.getDisplayName()).formatted(getModeColor(mode)))
                .append(Text.literal("]"))
                .formatted(Formatting.WHITE);
    }
}