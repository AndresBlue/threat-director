package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.power.PlayerPowerSnapshot;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;

public final class VanillaPlayerSnapshotFactory {
    private VanillaPlayerSnapshotFactory() {
    }

    public static PlayerPowerSnapshot capture(ServerPlayerEntity player) {
        ItemStack primary = bestCombatStack(player);
        boolean ranged = primary.getItem() instanceof BowItem || primary.getItem() instanceof CrossbowItem;
        int projectiles = countProjectiles(player);
        boolean loaded = primary.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(primary);
        boolean infinity = EnchantmentHelper.getLevel(Enchantments.INFINITY, primary) > 0;

        double projectileDamage = ranged ? (primary.getItem() instanceof CrossbowItem ? 9.0 : 6.0) : 0.0;
        double chargeSeconds = primary.getItem() instanceof CrossbowItem ? 1.25 : ranged ? 1.0 : 0.0;
        double range = primary.getItem() instanceof CrossbowItem ? 32.0 : ranged ? 24.0 : 3.0;

        int protectionEpf = 0;
        for (ItemStack armor : player.getArmorItems()) {
            protectionEpf += EnchantmentHelper.getLevel(Enchantments.PROTECTION, armor) * 4;
        }

        int sharpness = EnchantmentHelper.getLevel(Enchantments.SHARPNESS, primary);
        int smite = EnchantmentHelper.getLevel(Enchantments.SMITE, primary);
        int bane = EnchantmentHelper.getLevel(Enchantments.BANE_OF_ARTHROPODS, primary);
        double enchantDamage = Math.max(
                sharpness == 0 ? 0.0 : 0.5 * sharpness + 0.5,
                Math.max(2.5 * smite, 2.5 * bane));

        InventoryResources resources = inventoryResources(player);
        StatusEffectInstance regeneration = player.getStatusEffect(StatusEffects.REGENERATION);
        double regenerationSeconds = regeneration == null ? 0.0 : regeneration.getDuration() / 20.0;

        return new PlayerPowerSnapshot(
                player.getArmor(),
                player.getAttributeValue(EntityAttributes.GENERIC_ARMOR_TOUGHNESS),
                player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE),
                player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED),
                projectileDamage,
                chargeSeconds,
                range,
                projectiles,
                infinity,
                loaded,
                Math.min(20, protectionEpf),
                enchantDamage,
                resources.shield,
                resources.totem,
                player.getMaxHealth(),
                resources.portableHealing,
                regenerationSeconds,
                player.getAbsorptionAmount() + resources.absorptionReserve,
                resources.foodAvailability,
                player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),
                3.0,
                resources.utilityPotential);
    }

    private static ItemStack bestCombatStack(ServerPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        if (main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem) {
            return main;
        }
        if (off.getItem() instanceof BowItem || off.getItem() instanceof CrossbowItem) {
            return off;
        }
        return main;
    }

    private static int countProjectiles(ServerPlayerEntity player) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof ArrowItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static InventoryResources inventoryResources(ServerPlayerEntity player) {
        boolean shield = false;
        boolean totem = false;
        double healing = 0.0;
        double absorption = 0.0;
        double food = 0.0;
        double utility = 0.0;

        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int usefulCount = Math.min(stack.getCount(), 8);
            shield |= stack.isOf(Items.SHIELD) && stack.getDamage() < stack.getMaxDamage();
            totem |= stack.isOf(Items.TOTEM_OF_UNDYING);

            if (stack.isOf(Items.GOLDEN_APPLE)) {
                healing += 4.0 * usefulCount;
                absorption += 4.0 * usefulCount;
            } else if (stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
                healing += 8.0 * usefulCount;
                absorption += 16.0 * usefulCount;
            } else if (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION)) {
                if (PotionUtil.getPotion(stack) == Potions.HEALING) {
                    healing += 4.0 * usefulCount;
                } else if (PotionUtil.getPotion(stack) == Potions.STRONG_HEALING) {
                    healing += 8.0 * usefulCount;
                } else if (PotionUtil.getPotion(stack) == Potions.REGENERATION
                        || PotionUtil.getPotion(stack) == Potions.LONG_REGENERATION
                        || PotionUtil.getPotion(stack) == Potions.STRONG_REGENERATION) {
                    healing += 6.0 * usefulCount;
                } else {
                    utility += 0.5;
                }
            }

            if (stack.getItem().isFood() && stack.getItem().getFoodComponent() != null) {
                food += stack.getItem().getFoodComponent().getHunger() * usefulCount;
            }
        }

        long beneficialEffects = player.getStatusEffects().stream()
                .filter(effect -> effect.getEffectType().isBeneficial())
                .count();
        utility += beneficialEffects;
        return new InventoryResources(
                shield,
                totem,
                Math.min(40.0, healing),
                Math.min(20.0, absorption),
                Math.min(1.0, food / 80.0),
                Math.min(1.0, utility / 4.0));
    }

    private record InventoryResources(
            boolean shield,
            boolean totem,
            double portableHealing,
            double absorptionReserve,
            double foodAvailability,
            double utilityPotential) {
    }
}
