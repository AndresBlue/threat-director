package com.andresblue.threatdirector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigValidatorTest {
    private static final Gson GSON = new Gson();

    @Test
    void vanillaPlusDefaultsAreValidAndSafe() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();

        assertEquals(config, ConfigValidator.requireValid(config));
        assertEquals(2, config.schemaVersion());
        assertEquals(Preset.VANILLA_PLUS, config.preset());
        assertTrue(config.enabled());
        assertTrue(config.breachSimulation().simulationOnly());
        assertTrue(config.integrations().forceVanillaMobs());
        assertEquals(60, config.director().scanIntervalTicks());
        assertEquals(0.04, config.director().encounterChancePerScan());
        assertEquals(3_600, config.director().cooldownMinTicks());
        assertEquals(7_200, config.director().cooldownMaxTicks());
        assertEquals(8, config.encounter().maxMobs());
        assertEquals(12, config.encounter().hardMaxMobs());
        assertEquals(24, config.encounter().maxNearbyHostiles());
        assertEquals(32, config.tactical().maxCandidatesPerSnapshot());
        assertEquals(4, config.tactical().maxPathAttemptsPerMob());
        assertEquals(30, config.tactical().replanIntervalTicks());
        assertEquals(3, config.tactical().maxSimultaneousMeleeAttackers());
        assertEquals(3_500, config.performance().workBudgetMicrosPerTick());
        assertEquals(48, config.performance().maxPlannerOperationsPerTick());
        assertEquals(40.0, config.performance().level1TickTimeMs());
        assertEquals(100.0, config.performance().level6TickTimeMs());
        assertThrows(
                UnsupportedOperationException.class,
                () -> config.encounter().allowedMobIds().add("minecraft:witch"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> config.encounter().threatCosts().put("minecraft:witch", 2.5));
    }

    @Test
    void validationAccumulatesIndependentViolations() {
        JsonObject json = defaultJson();
        JsonObject encounter = json.getAsJsonObject("encounter");
        encounter.addProperty("minMobs", 9);
        encounter.addProperty("maxMobs", 7);
        encounter.addProperty("hardMaxMobs", 17);
        encounter.addProperty("spawnMinDistance", 50);
        encounter.addProperty("spawnMaxDistance", 48);
        json.getAsJsonObject("intensity").addProperty("smoothingAlpha", 2.0);
        json.getAsJsonObject("telemetry").addProperty("flushBatchSize", 300);

        ConfigValidationException exception = assertThrows(
                ConfigValidationException.class,
                () -> ConfigValidator.requireValid(fromJson(json)));

        List<String> violations = exception.violations();
        assertTrue(violations.stream().anyMatch(value -> value.contains("minMobs")));
        assertTrue(violations.stream().anyMatch(value -> value.contains("hardMaxMobs")));
        assertTrue(violations.stream().anyMatch(value -> value.contains("spawnMinDistance")));
        assertTrue(violations.stream().anyMatch(value -> value.contains("smoothingAlpha")));
        assertTrue(violations.stream().anyMatch(value -> value.contains("flushBatchSize")));
    }

    @Test
    void rejectsUnsafeMvpFeatureSwitches() {
        JsonObject json = defaultJson();
        json.getAsJsonObject("breachSimulation").addProperty("simulationOnly", false);
        json.getAsJsonObject("integrations").addProperty("enableExternalAdapters", true);
        json.getAsJsonObject("integrations").addProperty("forceVanillaMobs", false);

        ConfigValidationException exception = assertThrows(
                ConfigValidationException.class,
                () -> ConfigValidator.requireValid(fromJson(json)));

        assertTrue(exception.violations().stream()
                .anyMatch(value -> value.contains("simulationOnly")));
        assertTrue(exception.violations().stream()
                .anyMatch(value -> value.contains("enableExternalAdapters")));
        assertTrue(exception.violations().stream()
                .anyMatch(value -> value.contains("forceVanillaMobs")));
    }

    @Test
    void acceptsSixteenAsTheAbsoluteMobSafetyCap() {
        JsonObject json = defaultJson();
        json.getAsJsonObject("encounter").addProperty("hardMaxMobs", 16);

        ThreatDirectorConfig config = fromJson(json);

        assertEquals(config, ConfigValidator.requireValid(config));
    }

    @Test
    void rejectsInvalidAndDuplicateIdentifiers() {
        JsonObject json = defaultJson();
        json.getAsJsonObject("director")
                .add("allowedDimensions", GSON.toJsonTree(List.of("Minecraft:Overworld", "x:y", "x:y")));

        ConfigValidationException exception = assertThrows(
                ConfigValidationException.class,
                () -> ConfigValidator.requireValid(fromJson(json)));

        assertTrue(exception.violations().stream().anyMatch(value -> value.contains("invalid id")));
        assertTrue(exception.violations().stream().anyMatch(value -> value.contains("duplicate id")));
    }

    private static JsonObject defaultJson() {
        return GSON.toJsonTree(ThreatDirectorConfig.vanillaPlus()).getAsJsonObject();
    }

    private static ThreatDirectorConfig fromJson(JsonObject json) {
        return GSON.fromJson(json, ThreatDirectorConfig.class);
    }
}
