package com.puzzle_rain;

import com.puzzle_rain.entity.FollowingEntity;
import com.puzzle_rain.entity.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * 铁块魔杖物品类
 * 提供四种模式：跟随模式、静止模式、放置模式、操纵模式
 */
public class IronBlockWandItem extends Item {

    // 定义四种模式
    public enum WandMode {
        FOLLOW(0, "跟随模式"),
        STATIC(1, "静止模式"),
        PLACE(2, "放置模式"),
        CONTROL(3, "操纵模式");

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

    public IronBlockWandItem(Settings settings) {
        super(settings
                .maxCount(1) // 魔杖通常只能拿一个
                // 添加默认组件：初始模式为FOLLOW
                .component(ModComponents.WAND_MODE, new WandModeComponent(0))
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
     */
    public static void switchToNextMode(PlayerEntity player, Hand hand) {
        if (player == null) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty() || !(stack.getItem() instanceof IronBlockWandItem)) return;

        WandMode currentMode = getMode(stack);
        WandMode nextMode = currentMode.next();

        // 设置新模式
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
                return ActionResult.PASS;

            case CONTROL:
                // 模式4：操纵模式 - 不处理方块右键
                return ActionResult.PASS;
        }

        // 破坏原方块（对于FOLLOW和STATIC模式）
        world.breakBlock(pos, false, player);

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
    }

    // ==================== 右键空气交互 ====================
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        // 获取当前模式
        WandMode mode = getMode(stack);

        if (mode == WandMode.CONTROL) {
            // 在操纵模式下，右键空气发射当前控制的实体
            handleControlModeLaunch(world, user, hand, stack);
        }

        return TypedActionResult.success(stack);
    }

    /**
     * 处理操纵模式的发射
     */
    private void handleControlModeLaunch(World world, PlayerEntity player, Hand hand, ItemStack stack) {
        // 查找玩家当前是否正在操纵实体
        FollowingEntity controlledEntity = findControlledEntity(world, player);

        if (controlledEntity != null && controlledEntity.isControlled()) {
            // 获取玩家视角方向
            Vec3d lookDirection = player.getRotationVec(1.0F);

            // 发射实体
            controlledEntity.launchEntity(lookDirection);
            player.swingHand(hand, true);
            player.sendMessage(Text.literal("实体已发射").formatted(Formatting.GREEN), true);
        } else {
            player.sendMessage(Text.literal("没有选中的实体").formatted(Formatting.RED), true);
        }
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
        WandMode mode = getMode(stack);

        // 检查是否为FollowingEntity
        if (!(entity instanceof FollowingEntity followingEntity)) {
            // 如果不是FollowingEntity，且是操纵模式，可以允许选择其他实体
            if (mode == WandMode.CONTROL) {
                player.sendMessage(Text.literal("只能操纵方块实体").formatted(Formatting.RED), true);
            }
            return ActionResult.PASS;
        }

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

            case CONTROL:
                // 模式4：操纵模式 - 选中/发射/取消选中实体
                handleControlModeSelect(world, player, followingEntity, hand);
                break;
        }

        // 播放使用动画
        player.swingHand(hand, true);

        return ActionResult.SUCCESS;
    }

    /**
     * 处理操纵模式的选中/发射/取消选中
     */
    private void handleControlModeSelect(World world, PlayerEntity player, FollowingEntity entity, Hand hand) {
        // 检查实体是否已经被控制
        if (entity.isControlled()) {
            // 如果已经被这个玩家控制，发射它
            if (entity.isControlledBy(player)) {
                // 获取玩家视角方向
                Vec3d lookDirection = player.getRotationVec(1.0F);

                // 发射实体
                entity.launchEntity(lookDirection);
                player.sendMessage(Text.literal("实体已发射").formatted(Formatting.GREEN), true);
            } else {
                // 被其他玩家控制，不能操作
                player.sendMessage(Text.literal("这个实体已被其他玩家控制").formatted(Formatting.RED), true);
            }
        } else {
            // 实体没有被控制，尝试控制它

            // 首先取消对当前控制的实体的控制
            FollowingEntity currentlyControlled = findControlledEntity(world, player);
            if (currentlyControlled != null && currentlyControlled.isControlledBy(player)) {
                currentlyControlled.setControllingPlayer(null);
                player.sendMessage(Text.literal("已取消选中之前的实体").formatted(Formatting.YELLOW), true);
            }

            // 然后控制新的实体
            entity.setControllingPlayer(player);
            player.sendMessage(Text.literal("已选中实体，移动准心进行操纵，再次右键发射").formatted(Formatting.GREEN), true);
        }
    }

    /**
     * 查找玩家当前正在操纵的实体
     */
    private FollowingEntity findControlledEntity(World world, PlayerEntity player) {
        // 遍历世界中的所有FollowingEntity
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(100), // 搜索100格范围内
                e -> e instanceof FollowingEntity)) {
            FollowingEntity followingEntity = (FollowingEntity) entity;
            if (followingEntity.isControlledBy(player)) {
                return followingEntity;
            }
        }
        return null;
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
        tooltip.add(Text.literal("§6模式4 - 操纵").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("§f右键实体: §7选中实体/发射已选中的实体").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§f右键空气: §7发射当前选中的实体").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§f移动准心: §7操纵实体移动").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§f选中实体后: §7发光效果表示被控制").formatted(Formatting.GRAY));
    }

    // ==================== 其他实用方法 ====================

    /**
     * 获取模式对应的颜色
     */
    public static Formatting getModeColor(WandMode mode) {
        return switch (mode) {
            case FOLLOW -> Formatting.GREEN;
            case STATIC -> Formatting.YELLOW;
            case PLACE -> Formatting.BLUE;
            case CONTROL -> Formatting.LIGHT_PURPLE;
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