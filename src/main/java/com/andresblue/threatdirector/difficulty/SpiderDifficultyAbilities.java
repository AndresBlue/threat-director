package com.andresblue.threatdirector.difficulty;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.server.world.ServerWorld;
import com.andresblue.threatdirector.server.MobGriefingGuard;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

public final class SpiderDifficultyAbilities {
    private static final String HATCHLING_TAG = "threatdirector.spider_hatchling";
    /** Refreshed by the server mixin; finite duration lets config/difficulty downgrades self-clean. */
    private static final int MANAGED_EFFECT_DURATION = 400;

    private SpiderDifficultyAbilities() {
    }

    public static void applySpawnBuffs(SpiderEntity spider) {
        if (!(spider.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DifficultyAbilityPolicy.SpiderRules rules = MinecraftDifficultyProfiles.forWorld(world).spider();
        add(spider, StatusEffects.SPEED, rules.speedAmplifier());
        add(spider, StatusEffects.JUMP_BOOST, rules.jumpAmplifier());
        add(spider, StatusEffects.REGENERATION, rules.regenerationAmplifier());
        add(spider, StatusEffects.STRENGTH, rules.strengthAmplifier());
    }

    /** Places at most one web, and only under vanilla mobGriefing authority. */
    public static boolean tryPlaceCobweb(SpiderEntity spider, LivingEntity victim) {
        if (!(spider.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        DifficultyAbilityPolicy.SpiderRules rules = MinecraftDifficultyProfiles.forWorld(world).spider();
        if (!rules.cobwebOnHit()) {
            return false;
        }

        BlockPos feet = victim.getBlockPos();
        if (canReplaceWithWeb(world, feet)) {
            return world.setBlockState(feet, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
        }
        BlockPos above = feet.up();
        return canReplaceWithWeb(world, above)
                && world.setBlockState(above, Blocks.COBWEB.getDefaultState(), Block.NOTIFY_ALL);
    }

    /** Cave spiders are used as vanilla, registry-free small spiderlings. */
    public static void spawnHatchlings(SpiderEntity parent, DamageSource source) {
        if (!(parent.getWorld() instanceof ServerWorld world)
                || parent.getCommandTags().contains(HATCHLING_TAG)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
            return;
        }
        int count = MinecraftDifficultyProfiles.forWorld(world).spider().hatchlingCount();
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;
        for (int i = 0; i < count; i++) {
            spawnHatchling(world, parent, attacker, i);
        }
    }

    private static void add(SpiderEntity spider, StatusEffect effect, int amplifier) {
        if (amplifier >= 0) {
            spider.addStatusEffect(new StatusEffectInstance(
                    effect, MANAGED_EFFECT_DURATION, amplifier, false, false
            ));
        }
    }

    private static boolean canReplaceWithWeb(ServerWorld world, BlockPos position) {
        return MobGriefingGuard.permits(world, position)
                && world.getFluidState(position).isEmpty()
                && world.getBlockState(position).isReplaceable();
    }

    private static void spawnHatchling(ServerWorld world, SpiderEntity parent,
                                       LivingEntity attacker, int index) {
        double angle = (Math.PI * 2.0 * index / 2.0) + parent.getRandom().nextDouble() * 0.5;
        double x = parent.getX() + Math.cos(angle) * 0.8;
        double z = parent.getZ() + Math.sin(angle) * 0.8;
        BlockPos position = BlockPos.ofFloored(x, parent.getY(), z);
        if (!world.isChunkLoaded(position) || !world.getWorldBorder().contains(position)) {
            return;
        }

        CaveSpiderEntity hatchling = EntityType.CAVE_SPIDER.create(world);
        if (hatchling == null) {
            return;
        }
        hatchling.refreshPositionAndAngles(x, parent.getY(), z,
                parent.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!world.isSpaceEmpty(hatchling)) {
            hatchling.discard();
            return;
        }
        hatchling.initialize(world, world.getLocalDifficulty(position),
                SpawnReason.REINFORCEMENT, null, null);
        hatchling.addCommandTag(HATCHLING_TAG);
        if (attacker != null && attacker.isAlive()) {
            hatchling.setTarget(attacker);
        }
        world.spawnEntity(hatchling);
    }
}
