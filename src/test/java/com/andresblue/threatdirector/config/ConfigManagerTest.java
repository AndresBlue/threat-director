package com.andresblue.threatdirector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
    private static final Gson GSON = new Gson();

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstLoadCreatesDocumentedThreeCategoryJson5() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig loaded = manager.load();

        assertTrue(Files.isRegularFile(manager.configFile()));
        assertSame(loaded, manager.current());
        String json5 = Files.readString(manager.configFile(), StandardCharsets.UTF_8);
        assertTrue(json5.contains("\"schemaVersion\": 4"));
        assertTrue(json5.contains("\"general\""));
        assertTrue(json5.contains("\"vanilla\""));
        assertTrue(json5.contains("\"modded\""));
        assertTrue(json5.contains("// Enables or disables this section"));
        assertTrue(json5.contains("20 ticks per second"));
    }

    @Test
    void generatedJson5RoundTripsIncludingComments() throws Exception {
        ConfigManager first = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig generated = first.load();

        ConfigManager second = new ConfigManager(temporaryDirectory);
        assertEquals(generated, second.load());
    }

    @Test
    void everyConfigPropertyReceivesAComment() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        String documented = DocumentedConfigWriter.write(config);
        long propertyCount = countProperties(GSON.toJsonTree(config).getAsJsonObject());
        long commentCount = documented.lines().filter(line -> line.stripLeading().startsWith("//")).count();
        // Four header comments plus one immediately preceding every property.
        assertEquals(propertyCount + 4, commentCount);
    }

    @Test
    void invalidReloadPreservesLastKnownGoodSnapshot() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        JsonObject invalid = GSON.toJsonTree(valid).getAsJsonObject();
        invalid.getAsJsonObject("vanilla").getAsJsonObject("encounter")
                .addProperty("maxMobs", 999);
        Files.writeString(manager.configFile(), GSON.toJson(invalid), StandardCharsets.UTF_8);

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
    void missingBooleanAndUnknownTypoAreRejectedInsteadOfSilentlyDefaulting() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        JsonObject invalid = GSON.toJsonTree(valid).getAsJsonObject();
        JsonObject modded = invalid.getAsJsonObject("modded");
        modded.remove("enabled");
        modded.addProperty("enabeld", true);
        Files.writeString(manager.configFile(), GSON.toJson(invalid), StandardCharsets.UTF_8);

        JsonParseException exception = assertThrows(JsonParseException.class, manager::reload);

        assertTrue(exception.getMessage().contains("missing modded.enabled"));
        assertTrue(exception.getMessage().contains("unknown modded.enabeld"));
        assertSame(valid, manager.current());
    }

    @Test
    void missingCurrentRootCategoryIsRejectedInsteadOfMigratedAsLegacy() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        JsonObject invalid = GSON.toJsonTree(valid).getAsJsonObject();
        invalid.remove("modded");
        Files.writeString(manager.configFile(), GSON.toJson(invalid), StandardCharsets.UTF_8);

        JsonParseException exception = assertThrows(JsonParseException.class, manager::reload);

        assertTrue(exception.getMessage().contains("missing modded"));
        assertSame(valid, manager.current());
    }

    @Test
    void emptyCurrentConfigIsRejectedInsteadOfSilentlyMigrated() throws Exception {
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        ThreatDirectorConfig valid = manager.load();
        Files.writeString(manager.configFile(), "{}", StandardCharsets.UTF_8);

        JsonParseException exception = assertThrows(JsonParseException.class, manager::reload);

        assertTrue(exception.getMessage().contains("missing schemaVersion"));
        assertSame(valid, manager.current());
    }

    @Test
    void legacyJsonMigratesAndKeepsOriginalAsBackup() throws Exception {
        String legacy = """
                {
                  "schemaVersion": 3,
                  "preset": "VANILLA_PLUS",
                  "enabled": true,
                  "director": {"scanIntervalTicks":40,"minimumIntensity":45,"encounterChancePerScan":0.123,"cooldownMinTicks":1200,"cooldownMaxTicks":2400,"requireNightOrDarkness":true,"allowedDimensions":["minecraft:overworld"]},
                  "intensity": {"sampleIntervalTicks":100,"smoothingAlpha":0.15,"hysteresisPoints":5,"maxIntensity":100,"minimumHealthRatio":0.5,"recentDamageWindowTicks":200,"recentDeathCooldownTicks":12000,"defenseCap":35,"offenseCap":30,"sustainCap":15,"groupCap":10,"contextCap":10,"stressPenaltyCap":30},
                  "encounter": {"minMobs":4,"maxMobs":32,"hardMaxMobs":96,"maxActiveEncounters":2,"recruitRadius":64,"spawnMinDistance":32,"spawnMaxDistance":64,"maxNearbyHostiles":160,"allowDirectorSpawnedCreepers":true,"stalkingMinTicks":80,"stalkingMaxTicks":180,"positioningMaxTicks":120,"engagingMaxTicks":2400,"reliefTicks":100,"contactLossTicks":400,"maxPursuitDistance":128,"allowedMobIds":["minecraft:zombie"],"threatCosts":{"minecraft:zombie":1}},
                  "tactical": {"positionMinRadius":12,"positionMaxRadius":24,"maxCandidatesPerSnapshot":64,"maxPathAttemptsPerMob":5,"replanIntervalTicks":20,"targetMoveReplanDistance":5,"maxSimultaneousMeleeAttackers":4},
                  "multiplayer": {"groupRadius":24,"additionalPlayerScale":0.35,"maxGroupMultiplier":1.75},
                  "performance": {"workBudgetMicrosPerTick":20000,"maxPlannerOperationsPerTick":192,"degradeAfterTicks":100,"recoverAfterTicks":600,"level1TickTimeMs":45,"level2TickTimeMs":55,"level3TickTimeMs":70,"level4TickTimeMs":90,"level5TickTimeMs":120,"level6TickTimeMs":160},
                  "breachSimulation": {"enabled":true,"simulationOnly":true,"maxRayDistance":6,"maxBlocksInPlan":2,"maxBlockHardness":1.5,"spawnProtectionRadius":128},
                  "telemetry": {"enabled":false,"queueCapacity":256,"flushBatchSize":32,"includePlayerUuid":false},
                  "integrations": {"enableExternalAdapters":false,"forceVanillaMobs":true},
                  "debug": {"detailedLogging":false,"particleDurationTicks":100}
                }
                """;
        ConfigManager manager = new ConfigManager(temporaryDirectory);
        Files.writeString(manager.legacyConfigFile(), legacy, StandardCharsets.UTF_8);

        ThreatDirectorConfig migrated = manager.load();

        assertEquals(4, migrated.schemaVersion());
        assertEquals(0.123, migrated.director().encounterChancePerScan());
        assertTrue(Files.isRegularFile(manager.legacyConfigFile()));
        assertTrue(Files.isRegularFile(manager.configFile()));
    }

    private static long countProperties(JsonObject object) {
        long count = object.entrySet().size();
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                count += countProperties(entry.getValue().getAsJsonObject());
            }
        }
        return count;
    }
}
