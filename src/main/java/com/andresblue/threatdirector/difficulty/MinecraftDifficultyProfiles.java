package com.andresblue.threatdirector.difficulty;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import net.minecraft.world.World;

public final class MinecraftDifficultyProfiles {
    private MinecraftDifficultyProfiles() {
    }

    public static DifficultyAbilityPolicy.AbilityProfile forWorld(World world) {
        if (!ThreatDirectorMod.isModdedEnabled()) {
            return DifficultyAbilityPolicy.profile(DifficultyAbilityPolicy.Tier.EASY);
        }
        int id = world.getDifficulty().getId();
        if (id <= 1) {
            return DifficultyAbilityPolicy.profile(DifficultyAbilityPolicy.Tier.EASY);
        }
        ThreatDirectorConfig.ModdedSection modded = ThreatDirectorMod.config().modded();
        boolean bloodMoon = BloodMoonModule.isActive(world);
        ThreatDirectorConfig.ZombieTierSection zombieTier = id >= 3
                ? bloodMoon ? modded.zombie().bloodMoon() : modded.zombie().hard()
                : modded.zombie().normal();
        ThreatDirectorConfig.SpiderTierSection spiderTier = id >= 3
                ? bloodMoon ? modded.spider().bloodMoon() : modded.spider().hard()
                : modded.spider().normal();
        ThreatDirectorConfig.EndermanTierSection endermanTier = id >= 3
                ? bloodMoon ? modded.enderman().bloodMoon() : modded.enderman().hard()
                : modded.enderman().normal();

        return new DifficultyAbilityPolicy.AbilityProfile(
                id >= 3 ? DifficultyAbilityPolicy.Tier.HARD : DifficultyAbilityPolicy.Tier.NORMAL,
                zombieRules(modded.zombie(), zombieTier),
                spiderRules(modded.spider(), spiderTier),
                endermanRules(modded.enderman(), endermanTier));
    }

    private static DifficultyAbilityPolicy.ZombieRules zombieRules(
            ThreatDirectorConfig.ZombieSection feature,
            ThreatDirectorConfig.ZombieTierSection tier) {
        if (!feature.enabled()) {
            return new DifficultyAbilityPolicy.ZombieRules(
                    0.0, 0, false, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        }
        return new DifficultyAbilityPolicy.ZombieRules(
                feature.equipmentEnabled() ? tier.equipmentChance() : 0.0,
                feature.equipmentEnabled() ? tier.meleeToolChoices() : 0,
                feature.equipmentEnabled() && tier.shieldAndPickaxe(),
                feature.equipmentEnabled() ? tier.pickaxeChance() : 0.0,
                feature.equipmentEnabled() && feature.shieldBlockingEnabled()
                        ? tier.shieldChance() : 0.0,
                feature.shieldBlockingEnabled() ? tier.shieldBlockChance() : 0.0,
                feature.reinforcementsEnabled() ? tier.reinforcementChance() : 0.0,
                feature.reinforcementsEnabled() ? tier.maxReinforcements() : 0,
                tier.nearbyZombieCap(),
                tier.reinforcementCooldownTicks(),
                feature.blockBreakingEnabled() ? tier.blockBreakCooldownTicks() : 0);
    }

    private static DifficultyAbilityPolicy.SpiderRules spiderRules(
            ThreatDirectorConfig.SpiderSection feature,
            ThreatDirectorConfig.SpiderTierSection tier) {
        if (!feature.enabled()) {
            return new DifficultyAbilityPolicy.SpiderRules(-1, -1, -1, -1, false, 0, 0, 0.0);
        }
        return new DifficultyAbilityPolicy.SpiderRules(
                feature.statusBuffsEnabled() ? tier.speedAmplifier() : -1,
                feature.statusBuffsEnabled() ? tier.jumpAmplifier() : -1,
                feature.statusBuffsEnabled() ? tier.regenerationAmplifier() : -1,
                feature.statusBuffsEnabled() ? tier.strengthAmplifier() : -1,
                feature.cobwebOnHitEnabled() && tier.cobwebOnHit(),
                feature.cobwebOnHitEnabled() ? tier.cobwebCooldownTicks() : 0,
                feature.hatchlingsEnabled() ? tier.hatchlingCount() : 0,
                feature.hatchlingsEnabled() ? tier.hatchlingSpawnChance() : 0.0);
    }

    private static DifficultyAbilityPolicy.EndermanRules endermanRules(
            ThreatDirectorConfig.EndermanSection feature,
            ThreatDirectorConfig.EndermanTierSection tier) {
        if (!feature.enabled()) {
            return new DifficultyAbilityPolicy.EndermanRules(0.0, 0, 0.0, false, 0, 0);
        }
        return new DifficultyAbilityPolicy.EndermanRules(
                feature.allyAggroEnabled() ? tier.allyAlertRadius() : 0.0,
                feature.allyAggroEnabled() ? tier.maxAlliesAlerted() : 0,
                feature.allyAggroEnabled() ? tier.allyAlertChance() : 0.0,
                feature.twoBlockPostureEnabled() && tier.twoBlockCombatPosture(),
                feature.antiPillarTeleportEnabled() ? tier.pillarTriggerTicks() : 0,
                feature.antiPillarTeleportEnabled() ? tier.pillarTeleportCooldownTicks() : 0);
    }
}
