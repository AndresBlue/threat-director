package com.andresblue.threatdirector.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

/** Loads, migrates and atomically publishes the documented server configuration. */
public final class ConfigManager {
    public static final String CONFIG_FILE_NAME = "threatdirector.json5";
    public static final String LEGACY_CONFIG_FILE_NAME = "threatdirector.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configDirectory;
    private final Path configFile;
    private final Path legacyConfigFile;
    private volatile ThreatDirectorConfig current = ThreatDirectorConfig.vanillaPlus();

    public ConfigManager() {
        this(FabricLoader.getInstance().getConfigDir());
    }

    public ConfigManager(Path configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        this.legacyConfigFile = configDirectory.resolve(LEGACY_CONFIG_FILE_NAME);
    }

    /**
     * Loads JSON5 (strict JSON plus comments), creating it when absent. Old threatdirector.json
     * files are migrated without deletion so administrators retain a recoverable original.
     */
    public synchronized ThreatDirectorConfig load() throws IOException {
        Files.createDirectories(configDirectory);
        if (Files.notExists(configFile)) {
            ThreatDirectorConfig initial = Files.isRegularFile(legacyConfigFile)
                    ? readLegacy(legacyConfigFile)
                    : ThreatDirectorConfig.vanillaPlus();
            ConfigValidator.requireValid(initial);
            writeAtomically(initial);
            current = initial;
            return initial;
        }

        ThreatDirectorConfig candidate = readCurrent(configFile);
        ConfigValidator.requireValid(candidate);
        current = candidate;
        return candidate;
    }

    public synchronized ThreatDirectorConfig reload() throws IOException {
        return load();
    }

    public ThreatDirectorConfig current() {
        return current;
    }

    public Path configFile() {
        return configFile;
    }

    public Path legacyConfigFile() {
        return legacyConfigFile;
    }

    private static ThreatDirectorConfig readCurrent(Path path) throws IOException {
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = GSON.fromJson(reader, JsonObject.class);
        }
        if (root == null) {
            return null;
        }
        boolean hasCurrentSection = root.has("general")
                || root.has("vanilla")
                || root.has("modded");
        if (!hasCurrentSection && hasLegacySchemaVersion(root)) {
            return migrate(GSON.fromJson(root, LegacyConfig.class));
        }
        requireCompleteKnownShape(root);
        return GSON.fromJson(root, ThreatDirectorConfig.class);
    }

    private static boolean hasLegacySchemaVersion(JsonObject root) {
        JsonElement schemaVersion = root.get("schemaVersion");
        if (schemaVersion == null || !schemaVersion.isJsonPrimitive()
                || !schemaVersion.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        int value = schemaVersion.getAsInt();
        return value > 0 && value < ThreatDirectorConfig.CURRENT_SCHEMA_VERSION;
    }

    /** Rejects missing booleans and misspelled keys instead of letting Gson silently default them. */
    private static void requireCompleteKnownShape(JsonObject root) {
        JsonObject template = GSON.toJsonTree(ThreatDirectorConfig.vanillaPlus()).getAsJsonObject();
        List<String> problems = new java.util.ArrayList<>();
        compareShape(root, template, "", problems);
        if (!problems.isEmpty()) {
            throw new JsonParseException("Invalid config structure: " + String.join("; ", problems));
        }
    }

    private static void compareShape(
            JsonObject actual, JsonObject template, String parentPath, List<String> problems) {
        for (Map.Entry<String, JsonElement> entry : template.entrySet()) {
            String path = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
            if (!actual.has(entry.getKey())) {
                problems.add("missing " + path);
                continue;
            }
            JsonElement actualValue = actual.get(entry.getKey());
            if (entry.getValue().isJsonObject()
                    && !"vanilla.encounter.threatCosts".equals(path)) {
                if (actualValue == null || !actualValue.isJsonObject()) {
                    problems.add(path + " must be an object");
                } else {
                    compareShape(actualValue.getAsJsonObject(), entry.getValue().getAsJsonObject(),
                            path, problems);
                }
            }
        }
        for (Map.Entry<String, JsonElement> entry : actual.entrySet()) {
            if (!template.has(entry.getKey())) {
                String path = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
                problems.add("unknown " + path);
            }
        }
    }

    private static ThreatDirectorConfig readLegacy(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            LegacyConfig legacy = GSON.fromJson(reader, LegacyConfig.class);
            if (legacy == null) {
                throw new JsonParseException("legacy config root must be an object");
            }
            return migrate(legacy);
        }
    }

    private static ThreatDirectorConfig migrate(LegacyConfig legacy) {
        if (legacy == null) {
            return null;
        }
        ThreatDirectorConfig defaults = ThreatDirectorConfig.vanillaPlus();
        ThreatDirectorConfig.GeneralSection general = new ThreatDirectorConfig.GeneralSection(
                legacy.enabled,
                legacy.preset == null ? defaults.preset() : legacy.preset,
                migrateIntensity(legacy.intensity, defaults.intensity()),
                legacy.performance == null ? defaults.performance() : new ThreatDirectorConfig.PerformanceSection(
                        legacy.performance.workBudgetMicrosPerTick,
                        legacy.performance.maxPlannerOperationsPerTick,
                        legacy.performance.degradeAfterTicks,
                        legacy.performance.recoverAfterTicks,
                        legacy.performance.level1TickTimeMs,
                        legacy.performance.level2TickTimeMs,
                        legacy.performance.level3TickTimeMs,
                        legacy.performance.level4TickTimeMs,
                        legacy.performance.level5TickTimeMs,
                        legacy.performance.level6TickTimeMs),
                legacy.telemetry == null ? defaults.telemetry() : new ThreatDirectorConfig.TelemetrySection(
                        legacy.telemetry.enabled, defaults.telemetry().operatorChatLoggingEnabled(),
                        legacy.telemetry.queueCapacity,
                        legacy.telemetry.flushBatchSize, legacy.telemetry.includePlayerUuid),
                legacy.integrations == null ? defaults.integrations() : new ThreatDirectorConfig.IntegrationsSection(
                        legacy.integrations.enableExternalAdapters));

        ThreatDirectorConfig.VanillaSection baseVanilla = defaults.vanilla();
        ThreatDirectorConfig.VanillaSection vanilla = new ThreatDirectorConfig.VanillaSection(
                true,
                true,
                true,
                legacy.director == null ? baseVanilla.director() : new ThreatDirectorConfig.DirectorSection(
                        legacy.director.scanIntervalTicks, legacy.director.minimumIntensity,
                        legacy.director.encounterChancePerScan, legacy.director.cooldownMinTicks,
                        legacy.director.cooldownMaxTicks, legacy.director.requireNightOrDarkness,
                        legacy.director.allowedDimensions),
                migrateEncounter(legacy.encounter, baseVanilla.encounter()),
                legacy.tactical == null ? baseVanilla.tactical() : new ThreatDirectorConfig.TacticalSection(
                        legacy.tactical.positionMinRadius, legacy.tactical.positionMaxRadius,
                        legacy.tactical.maxCandidatesPerSnapshot,
                        legacy.tactical.maxPathAttemptsPerMob,
                        legacy.tactical.replanIntervalTicks,
                        legacy.tactical.targetMoveReplanDistance,
                        legacy.tactical.maxSimultaneousMeleeAttackers),
                legacy.multiplayer == null ? baseVanilla.multiplayer() : new ThreatDirectorConfig.MultiplayerSection(
                        legacy.multiplayer.groupRadius, legacy.multiplayer.additionalPlayerScale,
                        legacy.multiplayer.maxGroupMultiplier),
                baseVanilla.targeting(),
                baseVanilla.pressure(),
                legacy.breachSimulation == null ? baseVanilla.breachSimulation()
                        : new ThreatDirectorConfig.BreachSimulationSection(
                                legacy.breachSimulation.enabled,
                                legacy.breachSimulation.maxRayDistance,
                                legacy.breachSimulation.maxBlocksInPlan,
                                legacy.breachSimulation.maxBlockHardness,
                                legacy.breachSimulation.spawnProtectionRadius));
        return new ThreatDirectorConfig(
                ThreatDirectorConfig.CURRENT_SCHEMA_VERSION, general, vanilla, defaults.modded());
    }

    private static ThreatDirectorConfig.IntensitySection migrateIntensity(
            LegacyIntensity old,
            ThreatDirectorConfig.IntensitySection defaults) {
        return old == null ? defaults : new ThreatDirectorConfig.IntensitySection(
                old.sampleIntervalTicks, old.smoothingAlpha, old.hysteresisPoints,
                old.maxIntensity, old.minimumHealthRatio, old.recentDamageWindowTicks,
                old.recentDeathCooldownTicks, old.defenseCap, old.offenseCap, old.sustainCap,
                old.groupCap, old.contextCap, old.stressPenaltyCap,
                defaults.activationThreat(), defaults.playTimeHoursForMaximum(),
                defaults.experienceLevelForMaximum(), defaults.deathPlayTimePenalty(),
                defaults.maximumDeathPlayTimePenalty());
    }

    private static ThreatDirectorConfig.EncounterSection migrateEncounter(
            LegacyEncounter old,
            ThreatDirectorConfig.EncounterSection defaults) {
        return old == null ? defaults : new ThreatDirectorConfig.EncounterSection(
                old.minMobs, old.maxMobs, old.hardMaxMobs, old.maxActiveEncounters,
                old.recruitRadius, old.spawnMinDistance, old.spawnMaxDistance,
                old.maxNearbyHostiles, true, true, old.allowDirectorSpawnedCreepers,
                old.stalkingMinTicks, old.stalkingMaxTicks, old.positioningMaxTicks,
                old.engagingMaxTicks, old.reliefTicks, old.contactLossTicks,
                old.maxPursuitDistance, old.allowedMobIds, old.threatCosts);
    }

    private void writeAtomically(ThreatDirectorConfig config) throws IOException {
        Path temporary = Files.createTempFile(configDirectory, "threatdirector-", ".tmp");
        try {
            Files.writeString(temporary, DocumentedConfigWriter.write(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record LegacyConfig(
            int schemaVersion, Preset preset, boolean enabled,
            LegacyDirector director, LegacyIntensity intensity, LegacyEncounter encounter,
            LegacyTactical tactical, LegacyMultiplayer multiplayer, LegacyPerformance performance,
            LegacyBreach breachSimulation, LegacyTelemetry telemetry,
            LegacyIntegrations integrations) {}

    private record LegacyDirector(int scanIntervalTicks, double minimumIntensity,
                                  double encounterChancePerScan, int cooldownMinTicks,
                                  int cooldownMaxTicks, boolean requireNightOrDarkness,
                                  List<String> allowedDimensions) {}
    private record LegacyIntensity(int sampleIntervalTicks, double smoothingAlpha,
                                   double hysteresisPoints, double maxIntensity,
                                   double minimumHealthRatio, int recentDamageWindowTicks,
                                   int recentDeathCooldownTicks, double defenseCap,
                                   double offenseCap, double sustainCap, double groupCap,
                                   double contextCap, double stressPenaltyCap) {}
    private record LegacyEncounter(int minMobs, int maxMobs, int hardMaxMobs,
                                   int maxActiveEncounters, int recruitRadius,
                                   int spawnMinDistance, int spawnMaxDistance,
                                   int maxNearbyHostiles, boolean allowDirectorSpawnedCreepers,
                                   int stalkingMinTicks, int stalkingMaxTicks,
                                   int positioningMaxTicks, int engagingMaxTicks,
                                   int reliefTicks, int contactLossTicks,
                                   double maxPursuitDistance, List<String> allowedMobIds,
                                   Map<String, Double> threatCosts) {}
    private record LegacyTactical(double positionMinRadius, double positionMaxRadius,
                                  int maxCandidatesPerSnapshot, int maxPathAttemptsPerMob,
                                  int replanIntervalTicks, double targetMoveReplanDistance,
                                  int maxSimultaneousMeleeAttackers) {}
    private record LegacyMultiplayer(double groupRadius, double additionalPlayerScale,
                                     double maxGroupMultiplier) {}
    private record LegacyPerformance(int workBudgetMicrosPerTick, int maxPlannerOperationsPerTick,
                                     int degradeAfterTicks, int recoverAfterTicks,
                                     double level1TickTimeMs, double level2TickTimeMs,
                                     double level3TickTimeMs, double level4TickTimeMs,
                                     double level5TickTimeMs, double level6TickTimeMs) {}
    private record LegacyBreach(boolean enabled, boolean simulationOnly, int maxRayDistance,
                                int maxBlocksInPlan, double maxBlockHardness,
                                int spawnProtectionRadius) {}
    private record LegacyTelemetry(boolean enabled, int queueCapacity, int flushBatchSize,
                                   boolean includePlayerUuid) {}
    private record LegacyIntegrations(boolean enableExternalAdapters, boolean forceVanillaMobs) {}
}
