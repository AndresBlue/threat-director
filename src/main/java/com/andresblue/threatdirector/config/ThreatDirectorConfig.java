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

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Returns the safe, standalone MVP preset. */
    public static ThreatDirectorConfig vanillaPlus() {
        Map<String, Double> threatCosts = new LinkedHashMap<>();
        threatCosts.put("minecraft:zombie", 1.0);
        threatCosts.put("minecraft:skeleton", 1.3);
        threatCosts.put("minecraft:spider", 1.1);
        threatCosts.put("minecraft:creeper", 1.8);

        return new ThreatDirectorConfig(
                CURRENT_SCHEMA_VERSION,
                Preset.VANILLA_PLUS,
                true,
                new DirectorSection(
                        100,
                        45.0,
                        0.015,
                        9_600,
                        14_400,
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
                        3,
                        6,
                        8,
                        2,
                        48,
                        28,
                        48,
                        16,
                        false,
                        160,
                        300,
                        240,
                        1_200,
                        200,
                        300,
                        64.0,
                        List.of(
                                "minecraft:zombie",
                                "minecraft:skeleton",
                                "minecraft:spider",
                                "minecraft:creeper"),
                        threatCosts),
                new TacticalSection(10.0, 18.0, 16, 2, 40, 5.0, 2),
                new MultiplayerSection(24.0, 0.35, 1.75),
                new PerformanceSection(
                        2_000,
                        4,
                        100,
                        600,
                        35.0,
                        42.0,
                        48.0,
                        55.0,
                        65.0,
                        80.0),
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
