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
}
