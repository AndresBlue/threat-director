package com.andresblue.threatdirector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstLoadCreatesAndActivatesVanillaPlusFile() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);

        ThreatDirectorConfig loaded = manager.load();

        assertTrue(Files.isRegularFile(manager.configFile()));
        assertSame(loaded, manager.current());
        assertEquals(Preset.VANILLA_PLUS, loaded.preset());
        String json = Files.readString(manager.configFile(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\": 2"));
        assertTrue(json.contains("\"preset\": \"VANILLA_PLUS\""));
        assertTrue(json.contains("\"simulationOnly\": true"));
    }

    @Test
    void generatedFileRoundTripsThroughValidation() throws Exception {
        ConfigManager firstManager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig generated = firstManager.load();

        ConfigManager secondManager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig loaded = secondManager.load();

        assertEquals(generated, loaded);
    }

    @Test
    void invalidReloadPreservesLastKnownGoodSnapshot() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        JsonObject invalid = GSON.toJsonTree(valid).getAsJsonObject();
        invalid.getAsJsonObject("encounter").addProperty("maxMobs", 99);
        Files.writeString(
                manager.configFile(), GSON.toJson(invalid), StandardCharsets.UTF_8);

        assertThrows(ConfigValidationException.class, manager::reload);
        assertSame(valid, manager.current());
    }

    @Test
    void malformedReloadPreservesLastKnownGoodSnapshot() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        Files.writeString(manager.configFile(), "{ not-json", StandardCharsets.UTF_8);

        assertThrows(JsonParseException.class, manager::reload);
        assertSame(valid, manager.current());
    }

    @Test
    void jsonNullIsRejectedAsAValidationProblem() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        manager.load();
        Files.writeString(manager.configFile(), "null", StandardCharsets.UTF_8);

        ConfigValidationException exception =
                assertThrows(ConfigValidationException.class, manager::reload);

        assertTrue(exception.violations().contains("root must be a JSON object"));
    }

    @Test
    void exactLegacyDefaultsMigrateToAggressiveV2DefaultsAndRewriteFile() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig legacy = ThreatDirectorConfig.legacyVanillaPlusV1();
        assertLegacyFingerprint(legacy);
        Files.writeString(
                manager.configFile(), GSON.toJson(legacy), StandardCharsets.UTF_8);

        ThreatDirectorConfig loaded = manager.load();

        assertEquals(ThreatDirectorConfig.vanillaPlus(), loaded);
        ThreatDirectorConfig persisted = GSON.fromJson(
                Files.readString(manager.configFile(), StandardCharsets.UTF_8),
                ThreatDirectorConfig.class);
        assertEquals(loaded, persisted);
    }

    @Test
    void customizedLegacyFilePreservesEveryValueExceptSchemaVersion() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        JsonObject customizedJson = GSON.toJsonTree(
                ThreatDirectorConfig.legacyVanillaPlusV1()).getAsJsonObject();
        customizedJson.getAsJsonObject("director")
                .addProperty("encounterChancePerScan", 0.123);
        customizedJson.getAsJsonObject("encounter").addProperty("maxMobs", 7);
        customizedJson.getAsJsonObject("tactical")
                .addProperty("maxCandidatesPerSnapshot", 21);
        customizedJson.getAsJsonObject("performance")
                .addProperty("maxPlannerOperationsPerTick", 9);
        ThreatDirectorConfig customizedV1 = GSON.fromJson(
                customizedJson, ThreatDirectorConfig.class);
        Files.writeString(
                manager.configFile(), GSON.toJson(customizedV1), StandardCharsets.UTF_8);

        ThreatDirectorConfig loaded = manager.load();

        ThreatDirectorConfig expected = withCurrentSchema(customizedV1);
        assertEquals(expected, loaded);
        ThreatDirectorConfig persisted = GSON.fromJson(
                Files.readString(manager.configFile(), StandardCharsets.UTF_8),
                ThreatDirectorConfig.class);
        assertEquals(expected, persisted);
    }

    private static void assertLegacyFingerprint(ThreatDirectorConfig legacy) {
        assertEquals(1, legacy.schemaVersion());
        assertEquals(100, legacy.director().scanIntervalTicks());
        assertEquals(0.015, legacy.director().encounterChancePerScan());
        assertEquals(9_600, legacy.director().cooldownMinTicks());
        assertEquals(14_400, legacy.director().cooldownMaxTicks());
        assertEquals(6, legacy.encounter().maxMobs());
        assertEquals(8, legacy.encounter().hardMaxMobs());
        assertEquals(16, legacy.encounter().maxNearbyHostiles());
        assertEquals(16, legacy.tactical().maxCandidatesPerSnapshot());
        assertEquals(2, legacy.tactical().maxPathAttemptsPerMob());
        assertEquals(40, legacy.tactical().replanIntervalTicks());
        assertEquals(2, legacy.tactical().maxSimultaneousMeleeAttackers());
        assertEquals(2_000, legacy.performance().workBudgetMicrosPerTick());
        assertEquals(4, legacy.performance().maxPlannerOperationsPerTick());
        assertEquals(35.0, legacy.performance().level1TickTimeMs());
        assertEquals(80.0, legacy.performance().level6TickTimeMs());
    }

    private static ThreatDirectorConfig withCurrentSchema(ThreatDirectorConfig config) {
        return new ThreatDirectorConfig(
                ThreatDirectorConfig.CURRENT_SCHEMA_VERSION,
                config.preset(),
                config.enabled(),
                config.director(),
                config.intensity(),
                config.encounter(),
                config.tactical(),
                config.multiplayer(),
                config.performance(),
                config.breachSimulation(),
                config.telemetry(),
                config.integrations(),
                config.debug());
    }
}
