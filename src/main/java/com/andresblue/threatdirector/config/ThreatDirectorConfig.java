package com.andresblue.threatdirector.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable and server-authoritative configuration.
 *
 * <p>The file has exactly three user-facing groups: general, vanilla and modded. Compatibility
 * accessors keep the runtime compact while the on-disk layout stays understandable.</p>
 */
public record ThreatDirectorConfig(
        int schemaVersion,
        GeneralSection general,
        VanillaSection vanilla,
        ModdedSection modded) {

    public static final int CURRENT_SCHEMA_VERSION = 4;

    public static ThreatDirectorConfig vanillaPlus() {
        Map<String, Double> threatCosts = new LinkedHashMap<>();
        threatCosts.put("minecraft:zombie", 1.0);
        threatCosts.put("minecraft:skeleton", 1.3);
        threatCosts.put("minecraft:spider", 1.1);
        threatCosts.put("minecraft:creeper", 1.8);

        IntensitySection intensity = new IntensitySection(
                100, 0.15, 5.0, 100.0, 0.50, 200, 12_000,
                35.0, 30.0, 15.0, 10.0, 10.0, 30.0,
                35.0, 6.0, 30.0, 0.15, 0.65);
        PerformanceSection performance = new PerformanceSection(
                20_000, 192, 100, 600, 45.0, 55.0, 70.0, 90.0, 120.0, 160.0);
        GeneralSection general = new GeneralSection(
                true,
                Preset.VANILLA_PLUS,
                intensity,
                performance,
                new TelemetrySection(false, true, 256, 32, false),
                new IntegrationsSection(false));

        VanillaSection vanilla = new VanillaSection(
                true,
                true,
                true,
                new DirectorSection(40, 45.0, 0.06, 1_200, 2_400, true,
                        List.of("minecraft:overworld")),
                new EncounterSection(
                        4, 32, 96, 2, 64, 32, 64, 160,
                        true, true, true,
                        80, 180, 120, 2_400, 100, 400, 128.0,
                        List.of("minecraft:zombie", "minecraft:skeleton",
                                "minecraft:spider", "minecraft:creeper"),
                        threatCosts),
                new TacticalSection(12.0, 24.0, 64, 5, 20, 5.0, 4),
                new MultiplayerSection(24.0, 0.35, 1.75),
                new TargetingSection(64.0, 80.0, 48.0, 72.0, 40.0, 48.0),
                new EncounterPressureSection(
                        0.25, 0.50, 0.75, 0.08, 0.10, 0.12, 20.0,
                        new EasyPressureSection(6, 2, 1.75, 0.75, 1.50, 1.50),
                        new PressureTierSection(
                                1.25, 1.25, 0.0, 0.20, 0.25, 0.16,
                                3, 0.08, 3, 260, 80,
                                1.75, 0.75, 1.50, 1.15, 0.25, 0.90, 1.15),
                        new PressureTierSection(
                                2.75, 2.75, 0.20, 0.25, 0.25, 0.20,
                                6, 0.10, 5, 200, 60,
                                1.75, 0.75, 1.50, 0.75, 0.25, 0.50, 0.75),
                        new PressureTierSection(
                                5.0, 5.0, 0.50, 0.50, 0.35, 0.35,
                                12, 0.20, 10, 100, 20,
                                1.50, 0.50, 1.50, 0.35, 0.20, 0.15, 0.35)),
                new BreachSimulationSection(true, 6, 2, 1.5, 128));

        ModdedSection modded = new ModdedSection(
                true,
                new HudSection(true, true, true),
                new AwarenessSection(
                        true, true, true, true,
                        0.16, 0.14, 0.10, 0.23, 0.20, 0.14,
                        24.0, 32.0, 0.45, 0.0009,
                        8, 12.0, 2.5,
                        32.0, 64.0, 24.0, 28.0, 36.0,
                        12.0, 24.0, 10.0, 12.0, 16.0,
                        80, 120, 60, 80, 80),
                new TraitsSection(
                        true, true, true, true, true, true, true,
                        new TraitTierSection(
                                0.075, 0.14, 0.0, 2,
                                0.25, 0.42, 1.08,
                                0.28, 0.10, 0.06,
                                80, 0.75, 80, 10.0, 4,
                                0.07, 2.0, 0.10, 200),
                        new TraitTierSection(
                                0.14, 0.22, 0.05, 3,
                                0.32, 0.52, 1.16,
                                0.35, 0.18, 0.10,
                                60, 1.0, 60, 12.0, 6,
                                0.11, 3.0, 0.18, 160)),
                new BloodMoonSection(
                        true, true, true, 4, 6, 12_000,
                        List.of(
                                "The air tastes of iron. Night has not yet fallen.",
                                "The animals stare toward the horizon and refuse to make a sound.",
                                "Today, the shadows seem to be waiting for something.",
                                "Beneath the earth, something has begun to listen.",
                                "Dusk carries a silence that should not be here.",
                                "The waters are far too still for this hour.")),
                new SkeletonSection(
                        true, true, true, true, true,
                        64.0, 80.0, 128.0,
                        40, 30, 20,
                        0.10, 0.16, 0.32,
                        20, 600, 160,
                        0.14, 0.85, 0.18, 80, 120),
                new ZombieSection(
                        true, true, true, true, true, true, true, true,
                        new ZombieTierSection(0.40, 3, false, 0.0, 0.0, 0.0,
                                0.0, 0, 0, 0, 0),
                        new ZombieTierSection(0.65, 4, true, 0.06, 0.10, 0.42,
                                0.10, 1, 5, 300, 50),
                        new ZombieTierSection(0.85, 4, true, 0.10, 0.16, 0.55,
                                0.20, 2, 8, 200, 30),
                        48.0, 72.0, 96.0,
                        200,
                        0.04, 0.12, 0.06, 0.20, 0.08, 0.35,
                        24.0, 48.0, 72.0, 96.0,
                        3, 5, 4, 7, 5, 10),
                new SpiderSection(
                        true, true, true, true,
                        new SpiderTierSection(0, 0, -1, -1, false, 0, 0, 0.0),
                        new SpiderTierSection(0, 0, -1, -1, true, 600, 1, 0.72),
                        new SpiderTierSection(1, 1, 0, 0, true, 400, 2, 0.85),
                        0.005, 0.025, 0.01, 0.06),
                new CreeperSection(
                        true, true, true, true, true, true,
                        1.35, 0.70, 2.0, 0.30,
                        18.0, 20, 60, 0.38, 0.65,
                        45, 48.0, 28.0, 3.0, 6.0, 8),
                new EndermanSection(
                        true, true, true, true,
                        new EndermanTierSection(12.0, 2, 0.50, false, 0, 0),
                        new EndermanTierSection(18.0, 3, 0.65, true, 45, 120),
                        new EndermanTierSection(24.0, 6, 1.0, true, 30, 80)),
                new DrownedSection(
                        true, true, true, true, true,
                        new DrownedTierSection(1.55, 0.45, 0.78, 1, 0.62, 45, 56.0, true),
                        new DrownedTierSection(1.80, 0.60, 0.86, 2, 0.76, 34, 64.0, true),
                        new DrownedTierSection(2.05, 0.72, 0.92, 2, 0.90, 26, 72.0, true)),
                new OtherHostilesSection(
                        true, true, true, true, true, true, true,
                        40.0, 48.0, 48.0));

        return new ThreatDirectorConfig(CURRENT_SCHEMA_VERSION, general, vanilla, modded);
    }

    public Preset preset() { return general.preset(); }
    public boolean enabled() { return general.enabled(); }
    public DirectorSection director() { return vanilla.director(); }
    public IntensitySection intensity() { return general.intensity(); }
    public EncounterSection encounter() { return vanilla.encounter(); }
    public TacticalSection tactical() { return vanilla.tactical(); }
    public MultiplayerSection multiplayer() { return vanilla.multiplayer(); }
    public PerformanceSection performance() { return general.performance(); }
    public BreachSimulationSection breachSimulation() { return vanilla.breachSimulation(); }
    public TelemetrySection telemetry() { return general.telemetry(); }
    public IntegrationsSection integrations() { return general.integrations(); }

    public boolean vanillaEnabled() { return enabled() && vanilla.enabled(); }
    public boolean moddedEnabled() { return enabled() && modded.enabled(); }

    public record GeneralSection(
            boolean enabled,
            Preset preset,
            IntensitySection intensity,
            PerformanceSection performance,
            TelemetrySection telemetry,
            IntegrationsSection integrations) {}

    public record VanillaSection(
            boolean enabled,
            boolean adaptiveDirectorEnabled,
            boolean enhancedTargetingEnabled,
            DirectorSection director,
            EncounterSection encounter,
            TacticalSection tactical,
            MultiplayerSection multiplayer,
            TargetingSection targeting,
            EncounterPressureSection pressure,
            BreachSimulationSection breachSimulation) {}

    public record ModdedSection(
            boolean enabled,
            HudSection hud,
            AwarenessSection awareness,
            TraitsSection traits,
            BloodMoonSection bloodMoon,
            SkeletonSection skeleton,
            ZombieSection zombie,
            SpiderSection spider,
            CreeperSection creeper,
            EndermanSection enderman,
            DrownedSection drowned,
            OtherHostilesSection otherHostiles) {}

    public record DirectorSection(
            int scanIntervalTicks,
            double minimumIntensity,
            double encounterChancePerScan,
            int cooldownMinTicks,
            int cooldownMaxTicks,
            boolean requireNightOrDarkness,
            List<String> allowedDimensions) {
        public DirectorSection { allowedDimensions = immutableListOrNull(allowedDimensions); }
    }

    public record IntensitySection(
            int sampleIntervalTicks,
            double smoothingAlpha,
            double hysteresisPoints,
            double maxIntensity,
            double minimumHealthRatio,
            int recentDamageWindowTicks,
            int recentDeathCooldownTicks,
            double defenseCap,
            double offenseCap,
            double sustainCap,
            double groupCap,
            double contextCap,
            double stressPenaltyCap,
            double activationThreat,
            double playTimeHoursForMaximum,
            double experienceLevelForMaximum,
            double deathPlayTimePenalty,
            double maximumDeathPlayTimePenalty) {}

    public record EncounterSection(
            int minMobs,
            int maxMobs,
            int hardMaxMobs,
            int maxActiveEncounters,
            int recruitRadius,
            int spawnMinDistance,
            int spawnMaxDistance,
            int maxNearbyHostiles,
            boolean recruitNaturalMobs,
            boolean directedSpawning,
            boolean allowDirectorSpawnedCreepers,
            int stalkingMinTicks,
            int stalkingMaxTicks,
            int positioningMaxTicks,
            int engagingMaxTicks,
            int reliefTicks,
            int contactLossTicks,
            double maxPursuitDistance,
            List<String> allowedMobIds,
            Map<String, Double> threatCosts) {
        public EncounterSection {
            allowedMobIds = immutableListOrNull(allowedMobIds);
            threatCosts = immutableMapOrNull(threatCosts);
        }
    }

    public record TacticalSection(
            double positionMinRadius,
            double positionMaxRadius,
            int maxCandidatesPerSnapshot,
            int maxPathAttemptsPerMob,
            int replanIntervalTicks,
            double targetMoveReplanDistance,
            int maxSimultaneousMeleeAttackers) {}

    public record MultiplayerSection(
            double groupRadius,
            double additionalPlayerScale,
            double maxGroupMultiplier) {}

    public record TargetingSection(
            double normalSkeletonRange,
            double hardSkeletonRange,
            double normalZombieRange,
            double hardZombieRange,
            double normalGenericRange,
            double hardGenericRange) {}

    public record EncounterPressureSection(
            double easyDifficultyHazard,
            double normalDifficultyHazard,
            double hardDifficultyHazard,
            double nightHazard,
            double darknessHazard,
            double caveHazard,
            double intensityContextSpan,
            EasyPressureSection easy,
            PressureTierSection normal,
            PressureTierSection hard,
            PressureTierSection bloodMoon) {}

    public record EasyPressureSection(
            int mobCap,
            int meleeAttackers,
            double cadenceBaseMinusHazard,
            double cadenceMinimumMultiplier,
            double cadenceMaximumMultiplier,
            double cooldownMultiplier) {}

    public record PressureTierSection(
            double encounterChanceBaseMultiplier,
            double encounterChanceEnvironmentalBonus,
            double mobCapBaseFraction,
            double mobCapEnvironmentalFraction,
            double minimumMobFraction,
            double meleeFraction,
            int reinforcementMinimum,
            double reinforcementFraction,
            int maximumReinforcementWaves,
            int reinforcementIntervalTicks,
            int reinforcementEnvironmentReductionTicks,
            double cadenceBaseMinusHazard,
            double cadenceMinimumMultiplier,
            double cadenceMaximumMultiplier,
            double cooldownBaseMultiplier,
            double cooldownEnvironmentalReduction,
            double cooldownMinimumMultiplier,
            double cooldownMaximumMultiplier) {}

    public record PerformanceSection(
            int workBudgetMicrosPerTick,
            int maxPlannerOperationsPerTick,
            int degradeAfterTicks,
            int recoverAfterTicks,
            double level1TickTimeMs,
            double level2TickTimeMs,
            double level3TickTimeMs,
            double level4TickTimeMs,
            double level5TickTimeMs,
            double level6TickTimeMs) {}

    public record BreachSimulationSection(
            boolean enabled,
            int maxRayDistance,
            int maxBlocksInPlan,
            double maxBlockHardness,
            int spawnProtectionRadius) {}

    public record TelemetrySection(
            boolean enabled,
            boolean operatorChatLoggingEnabled,
            int queueCapacity,
            int flushBatchSize,
            boolean includePlayerUuid) {}

    public record IntegrationsSection(boolean enableExternalAdapters) {}
    public record HudSection(boolean enabled, boolean operatorsSeeByDefault, boolean allowCommandOptIn) {}

    public record AwarenessSection(
            boolean enabled,
            boolean movementSenseEnabled,
            boolean soundSenseEnabled,
            boolean lightSenseEnabled,
            double normalMovementChance,
            double normalSoundChance,
            double normalLightChance,
            double hardMovementChance,
            double hardSoundChance,
            double hardLightChance,
            double walkingDetectionRange,
            double sprintingDetectionRange,
            double sneakingRangeMultiplier,
            double minimumMovementSpeedSquared,
            int minimumLightLevel,
            double lightBaseRange,
            double lightRangePerLevel,
            double miningSoundRange,
            double explosionSoundRange,
            double doorSoundRange,
            double containerSoundRange,
            double combatSoundRange,
            double miningAttributionRadius,
            double explosionAttributionRadius,
            double doorAttributionRadius,
            double containerAttributionRadius,
            double combatAttributionRadius,
            int miningSoundLifetimeTicks,
            int explosionSoundLifetimeTicks,
            int doorSoundLifetimeTicks,
            int containerSoundLifetimeTicks,
            int combatSoundLifetimeTicks) {}

    public record TraitsSection(
            boolean enabled,
            boolean cowardEnabled,
            boolean berserkerEnabled,
            boolean leaderEnabled,
            boolean regenerativeEnabled,
            boolean swiftEnabled,
            boolean resilientEnabled,
            TraitTierSection normal,
            TraitTierSection hard) {}

    public record TraitTierSection(
            double anyTraitChance,
            double secondTraitChance,
            double thirdTraitChance,
            int maximumTraits,
            double cowardTriggerHealthFraction,
            double cowardExitHealthFraction,
            double cowardFleeSpeed,
            double berserkerTriggerHealthFraction,
            double berserkerDamageMultiplier,
            double berserkerSpeedMultiplier,
            int regenerationIntervalTicks,
            double regenerationAmount,
            int leaderIntervalTicks,
            double leaderRadius,
            int leaderMaximumAllies,
            double swiftSpeedMultiplier,
            double resilientArmorBonus,
            double resilientKnockbackResistance,
            int particleIntervalTicks) {}

    public record BloodMoonSection(
            boolean enabled,
            boolean announceOmens,
            boolean preventSleeping,
            int minimumIntervalNights,
            int maximumIntervalNights,
            int nightStartTick,
            List<String> omenMessages) {
        public BloodMoonSection { omenMessages = immutableListOrNull(omenMessages); }
    }

    public record SkeletonSection(
            boolean enabled,
            boolean predictiveAim,
            boolean homingArrows,
            boolean effectArrows,
            boolean retreatDash,
            double normalRange,
            double hardRange,
            double bloodMoonRange,
            int normalAttackIntervalTicks,
            int hardAttackIntervalTicks,
            int bloodMoonAttackIntervalTicks,
            double normalEffectChance,
            double hardEffectChance,
            double bloodMoonEffectChance,
            int minimumEffectDurationTicks,
            int maximumEffectDurationTicks,
            int meanEffectDurationTicks,
            double homingStrengthMultiplier,
            double dashHorizontalSpeed,
            double dashVerticalSpeed,
            int dashMinimumCooldownTicks,
            int dashMaximumCooldownTicks) {}

    public record ZombieSection(
            boolean enabled,
            boolean equipmentEnabled,
            boolean shieldBlockingEnabled,
            boolean stickyAggroEnabled,
            boolean nearbyAggroEnabled,
            boolean lightningHordesEnabled,
            boolean reinforcementsEnabled,
            boolean blockBreakingEnabled,
            ZombieTierSection normal,
            ZombieTierSection hard,
            ZombieTierSection bloodMoon,
            double normalTargetRange,
            double hardTargetRange,
            double bloodMoonTargetRange,
            int axeShieldDisableTicks,
            double normalHordeChanceAtActivation,
            double normalHordeChanceAtMaximum,
            double hardHordeChanceAtActivation,
            double hardHordeChanceAtMaximum,
            double bloodMoonHordeChanceAtActivation,
            double bloodMoonHordeChanceAtMaximum,
            double nearbyAlertBaseRadius,
            double normalAlertMaximumRadius,
            double hardAlertMaximumRadius,
            double bloodMoonAlertMaximumRadius,
            int normalHordeMinimum,
            int normalHordeMaximum,
            int hardHordeMinimum,
            int hardHordeMaximum,
            int bloodMoonHordeMinimum,
            int bloodMoonHordeMaximum) {}

    public record ZombieTierSection(
            double equipmentChance,
            int meleeToolChoices,
            boolean shieldAndPickaxe,
            double pickaxeChance,
            double shieldChance,
            double shieldBlockChance,
            double reinforcementChance,
            int maxReinforcements,
            int nearbyZombieCap,
            int reinforcementCooldownTicks,
            int blockBreakCooldownTicks) {}

    public record SpiderSection(
            boolean enabled,
            boolean statusBuffsEnabled,
            boolean cobwebOnHitEnabled,
            boolean hatchlingsEnabled,
            SpiderTierSection normal,
            SpiderTierSection hard,
            SpiderTierSection bloodMoon,
            double ordinaryCobwebChanceAtActivation,
            double ordinaryCobwebChanceAtMaximum,
            double bloodMoonCobwebChanceAtActivation,
            double bloodMoonCobwebChanceAtMaximum) {}

    public record SpiderTierSection(
            int speedAmplifier,
            int jumpAmplifier,
            int regenerationAmplifier,
            int strengthAmplifier,
            boolean cobwebOnHit,
            int cobwebCooldownTicks,
            int hatchlingCount,
            double hatchlingSpawnChance) {}

    public record CreeperSection(
            boolean enabled,
            boolean silentSteps,
            boolean rearSpeedBoost,
            boolean randomFuse,
            boolean dashEnabled,
            boolean coordinatedBreaching,
            double ordinaryRearSpeedMultiplier,
            double ordinaryMinimumFuseRatio,
            double bloodMoonRearSpeedMultiplier,
            double bloodMoonMinimumFuseRatio,
            double rearApproachMaximumDistance,
            int breachFuseDelayTicks,
            int dashCooldownTicks,
            double dashImpulse,
            double dashMaximumSpeed,
            int targetMemorySeconds,
            double targetMemoryMaximumDistance,
            double breachMaximumTargetDistance,
            double breachFuseDistance,
            double maximumBreakableHardness,
            int breachAssignmentSeconds) {}

    public record EndermanSection(
            boolean enabled,
            boolean allyAggroEnabled,
            boolean twoBlockPostureEnabled,
            boolean antiPillarTeleportEnabled,
            EndermanTierSection normal,
            EndermanTierSection hard,
            EndermanTierSection bloodMoon) {}

    public record EndermanTierSection(
            double allyAlertRadius,
            int maxAlliesAlerted,
            double allyAlertChance,
            boolean twoBlockCombatPosture,
            int pillarTriggerTicks,
            int pillarTeleportCooldownTicks) {}

    public record DrownedSection(
            boolean enabled,
            boolean swimSpeedEnabled,
            boolean tridentsEnabled,
            boolean riptideEnabled,
            boolean boatBreakingEnabled,
            DrownedTierSection normal,
            DrownedTierSection hard,
            DrownedTierSection bloodMoon) {}

    public record DrownedTierSection(
            double swimSpeedMultiplier,
            double tridentChance,
            double riptideChance,
            int riptideLevel,
            double dashSpeed,
            int dashCooldownTicks,
            double targetRange,
            boolean breaksBoats) {}

    public record OtherHostilesSection(
            boolean enabled,
            boolean witchesAndIllagers,
            boolean aerialFireMobs,
            boolean guardians,
            boolean drownedAndHusks,
            boolean swarms,
            boolean brutes,
            double normalTargetRange,
            double hardTargetRange,
            double bloodMoonTargetRange) {}

    private static <T> List<T> immutableListOrNull(List<T> source) {
        return source == null ? null : List.copyOf(source);
    }

    private static <K, V> Map<K, V> immutableMapOrNull(Map<K, V> source) {
        return source == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
