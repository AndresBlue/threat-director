package com.andresblue.threatdirector.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, server-authoritative Threat Director configuration.
 *
 * <p>All durations are expressed in game ticks, distances in blocks, and tick-time thresholds in
 * milliseconds. Keeping units in the component names makes the generated JSON self-documenting.
 */
public record ThreatDirectorConfig(
        int schemaVersion,
        Preset preset,
        boolean enabled,
        DirectorSection director,
        IntensitySection intensity,
        EncounterSection encounter,
        TacticalSection tactical,
        MultiplayerSection multiplayer,
        PerformanceSection performance,
        BreachSimulationSection breachSimulation,
        TelemetrySection telemetry,
        IntegrationsSection integrations,
        DebugSection debug) {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    /** Returns the safe, standalone MVP preset. */
    public static ThreatDirectorConfig vanillaPlus() {
        return vanillaPlusDefaults(CURRENT_SCHEMA_VERSION);
    }

    /** Exact schema-v1 preset used only to distinguish untouched legacy files during migration. */
    static ThreatDirectorConfig legacyVanillaPlusV1() {
        return vanillaPlusDefaults(1);
    }

    /** Exact schema-v2 preset used to migrate untouched installations to apocalypse defaults. */
    static ThreatDirectorConfig legacyVanillaPlusV2() {
        return vanillaPlusDefaults(2);
    }

    private static ThreatDirectorConfig vanillaPlusDefaults(int schemaVersion) {
        boolean legacyV1 = schemaVersion == 1;
        boolean legacyV2 = schemaVersion == 2;
        boolean apocalypse = schemaVersion >= 3;
        Map<String, Double> threatCosts = new LinkedHashMap<>();
        threatCosts.put("minecraft:zombie", 1.0);
        threatCosts.put("minecraft:skeleton", 1.3);
        threatCosts.put("minecraft:spider", 1.1);
        threatCosts.put("minecraft:creeper", 1.8);

        return new ThreatDirectorConfig(
                schemaVersion,
                Preset.VANILLA_PLUS,
                true,
                new DirectorSection(
                        legacyV1 ? 100 : legacyV2 ? 60 : 40,
                        45.0,
                        legacyV1 ? 0.015 : legacyV2 ? 0.04 : 0.06,
                        legacyV1 ? 9_600 : legacyV2 ? 3_600 : 1_200,
                        legacyV1 ? 14_400 : legacyV2 ? 7_200 : 2_400,
                        true,
                        List.of("minecraft:overworld")),
                new IntensitySection(
                        100,
                        0.15,
                        5.0,
                        100.0,
                        0.50,
                        200,
                        12_000,
                        35.0,
                        30.0,
                        15.0,
                        10.0,
                        10.0,
                        30.0),
                new EncounterSection(
                        apocalypse ? 4 : 3,
                        legacyV1 ? 6 : legacyV2 ? 8 : 32,
                        legacyV1 ? 8 : legacyV2 ? 12 : 96,
                        2,
                        apocalypse ? 64 : 48,
                        apocalypse ? 32 : 28,
                        apocalypse ? 64 : 48,
                        legacyV1 ? 16 : legacyV2 ? 24 : 160,
                        apocalypse,
                        apocalypse ? 80 : 160,
                        apocalypse ? 180 : 300,
                        apocalypse ? 120 : 240,
                        apocalypse ? 2_400 : 1_200,
                        apocalypse ? 100 : 200,
                        apocalypse ? 400 : 300,
                        apocalypse ? 128.0 : 64.0,
                        List.of(
                                "minecraft:zombie",
                                "minecraft:skeleton",
                                "minecraft:spider",
                                "minecraft:creeper"),
                        threatCosts),
                new TacticalSection(
                        apocalypse ? 12.0 : 10.0,
                        apocalypse ? 24.0 : 18.0,
                        legacyV1 ? 16 : legacyV2 ? 32 : 64,
                        legacyV1 ? 2 : legacyV2 ? 4 : 5,
                        legacyV1 ? 40 : legacyV2 ? 30 : 20,
                        5.0,
                        legacyV1 ? 2 : legacyV2 ? 3 : 4),
                new MultiplayerSection(24.0, 0.35, 1.75),
                new PerformanceSection(
                        legacyV1 ? 2_000 : legacyV2 ? 3_500 : 20_000,
                        legacyV1 ? 4 : legacyV2 ? 48 : 192,
                        100,
                        600,
                        legacyV1 ? 35.0 : legacyV2 ? 40.0 : 45.0,
                        legacyV1 ? 42.0 : legacyV2 ? 48.0 : 55.0,
                        legacyV1 ? 48.0 : legacyV2 ? 56.0 : 70.0,
                        legacyV1 ? 55.0 : legacyV2 ? 65.0 : 90.0,
                        legacyV1 ? 65.0 : legacyV2 ? 80.0 : 120.0,
                        legacyV1 ? 80.0 : legacyV2 ? 100.0 : 160.0),
                new BreachSimulationSection(true, true, 6, 2, 1.5, 128),
                new TelemetrySection(false, 256, 32, false),
                new IntegrationsSection(false, true),
                new DebugSection(false, 100));
    }

    public record DirectorSection(
            int scanIntervalTicks,
            double minimumIntensity,
            double encounterChancePerScan,
            int cooldownMinTicks,
            int cooldownMaxTicks,
            boolean requireNightOrDarkness,
            List<String> allowedDimensions) {

        public DirectorSection {
            allowedDimensions = immutableListOrNull(allowedDimensions);
        }
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
            double stressPenaltyCap) {}

    public record EncounterSection(
            int minMobs,
            int maxMobs,
            int hardMaxMobs,
            int maxActiveEncounters,
            int recruitRadius,
            int spawnMinDistance,
            int spawnMaxDistance,
            int maxNearbyHostiles,
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
            boolean simulationOnly,
            int maxRayDistance,
            int maxBlocksInPlan,
            double maxBlockHardness,
            int spawnProtectionRadius) {}

    public record TelemetrySection(
            boolean enabled,
            int queueCapacity,
            int flushBatchSize,
            boolean includePlayerUuid) {}

    public record IntegrationsSection(
            boolean enableExternalAdapters,
            boolean forceVanillaMobs) {}

    public record DebugSection(boolean detailedLogging, int particleDurationTicks) {}

    private static <T> List<T> immutableListOrNull(List<T> source) {
        return source == null ? null : List.copyOf(source);
    }

    private static <K, V> Map<K, V> immutableMapOrNull(Map<K, V> source) {
        if (source == null) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
