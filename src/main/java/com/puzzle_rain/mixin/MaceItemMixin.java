package com.puzzle_rain.mixin;

import com.puzzle_rain.entity.HammerStrikeEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaceItem.class)
public class MaceItemMixin {

    @Inject(method = "postHit", at = @At("HEAD"))
    private void onPostHit(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        // 只在服务端执行，且攻击者是玩家
        if (attacker.getWorld() instanceof ServerWorld serverWorld && attacker instanceof PlayerEntity player) {
            // 检查是否应该触发自定义效果（比如有特定附魔）
            if (shouldTriggerCustomEffect(stack, player)) {
                triggerHammerStrikeEffect(serverWorld, target.getBlockPos(), player, stack);
            }
        }
    }

    @Inject(method = "postHit", at = @At("TAIL"))
    private void onPostHitTail(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        // 你也可以在攻击完成后触发效果
    }

    /**
     * 检查是否应该触发自定义重锤效果
     */
    private boolean shouldTriggerCustomEffect(ItemStack stack, PlayerEntity player) {
        // 这里可以根据你的需求添加条件，比如：
        // 1. 检查特定附魔
        // 2. 检查玩家是否在潜行
        // 3. 检查冷却时间
        // 4. 随机几率等

        // 示例：检查是否有"风暴"附魔
        // return EnchantmentHelper.getLevel(YourModEnchantments.STORM_ENCHANTMENT, stack) > 0;

        // 暂时返回true进行测试
        return true;
    }

    /**
     * 触发重锤打击效果
     */
    private void triggerHammerStrikeEffect(ServerWorld world, BlockPos centerPos, PlayerEntity player, ItemStack stack) {
        // 计算打击半径（可以根据附魔等级调整）
        float strikeRadius = calculateStrikeRadius(stack);

        // 创建锤击效果实体
        HammerStrikeEntity strikeEntity = new HammerStrikeEntity(world, centerPos, strikeRadius);
        world.spawnEntity(strikeEntity);

        // 可选：添加粒子效果、音效等
        spawnAdditionalEffects(world, centerPos, player, stack);

        // 可选：消耗耐久度或设置冷却
        applyCosts(player, stack);
    }

    /**
     * 计算打击半径
     */
    private float calculateStrikeRadius(ItemStack stack) {
        float baseRadius = 8.0f; // 基础半径 (reduced from 20 to make it more reasonable for testing)

        // 根据附魔等级增加半径
        // int enchantmentLevel = EnchantmentHelper.getLevel(YourModEnchantments.STORM_ENCHANTMENT, stack);
        // return baseRadius + enchantmentLevel * 1.5f;

        return baseRadius;
    }

    /**
     * 生成额外的视觉效果和音效
     */
    private void spawnAdditionalEffects(ServerWorld world, BlockPos centerPos, PlayerEntity player, ItemStack stack) {
        // 这里可以添加额外的粒子效果、音效等
        // 例如：

        // 冲击波粒子
        world.spawnParticles(
            ParticleTypes.EXPLOSION,
            centerPos.getX() + 0.5, centerPos.getY() + 1, centerPos.getZ() + 0.5,
            5, 2, 2, 2, 0.1
        );

        // 自定义音效
        // world.playSound(null, centerPos, YourModSounds.HAMMER_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * 应用消耗（耐久度、冷却等）
     */
    private void applyCosts(PlayerEntity player, ItemStack stack) {
        // 消耗耐久度

        // 设置攻击冷却
        player.getItemCooldownManager().set(stack.getItem(), 20); // 1秒冷却
    }
}