package com.andresblue.threatdirector.difficulty;

import net.minecraft.block.BlockState;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.tag.BlockTags;

/**
 * Maps a zombie's carried tool to the vanilla block family its bounded Hard-mode
 * obstruction breaker is allowed to mine.
 */
public final class ZombieToolBlockHelper {
    private ZombieToolBlockHelper() {
    }

    public static ZombieToolRole roleOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ZombieToolRole.NONE;
        }
        if (stack.getItem() instanceof ShovelItem) {
            return ZombieToolRole.SHOVEL;
        }
        if (stack.getItem() instanceof AxeItem) {
            return ZombieToolRole.AXE;
        }
        if (stack.getItem() instanceof SwordItem) {
            return ZombieToolRole.SWORD;
        }
        if (stack.getItem() instanceof PickaxeItem) {
            return ZombieToolRole.PICKAXE;
        }
        return ZombieToolRole.NONE;
    }

    public static boolean isPreferredBlock(ItemStack stack, BlockState state) {
        if (state == null) {
            return false;
        }
        return switch (roleOf(stack)) {
            case SHOVEL -> state.isIn(BlockTags.SHOVEL_MINEABLE);
            case AXE -> state.isIn(BlockTags.AXE_MINEABLE);
            case SWORD -> state.isIn(BlockTags.SWORD_EFFICIENT);
            case PICKAXE -> state.isIn(BlockTags.PICKAXE_MINEABLE);
            case NONE -> false;
        };
    }
}
