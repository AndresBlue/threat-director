package com.andresblue.threatdirector.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;

/** Loads and atomically publishes the server-authoritative configuration snapshot. */
public final class ConfigManager {
    public static final String CONFIG_FILE_NAME = "threatdirector.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configDirectory;
    private final Path configFile;
    private volatile ThreatDirectorConfig current = ThreatDirectorConfig.vanillaPlus();

    /** Creates a manager rooted at Fabric Loader's standard config directory. */
    public ConfigManager() {
        this(FabricLoader.getInstance().getConfigDir());
    }

    /** Constructor intended for tests and alternate server hosts. */
    public ConfigManager(Path configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
    }

    /**
     * Loads the file, creating a formatted VANILLA_PLUS file if none exists.
     *
     * <p>The active snapshot changes only after parsing and complete validation succeed. Therefore
     * callers may catch an exception from a reload while the server safely continues to use the
     * preceding snapshot.
     *
     * @throws IOException when the file cannot be read or created
     * @throws JsonParseException when the JSON syntax or shape is malformed
     * @throws ConfigValidationException when parsed values are unsafe or inconsistent
     */
    public synchronized ThreatDirectorConfig load() throws IOException {
        Files.createDirectories(configDirectory);
        if (Files.notExists(configFile)) {
            ThreatDirectorConfig defaults = ThreatDirectorConfig.vanillaPlus();
            ConfigValidator.requireValid(defaults);
            writeAtomically(defaults);
            current = defaults;
            return defaults;
        }

        ThreatDirectorConfig parsed;
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            parsed = GSON.fromJson(reader, ThreatDirectorConfig.class);
        }
        Migration migration = migrate(parsed);
        ThreatDirectorConfig candidate = migration.config();
        ConfigValidator.requireValid(candidate);
        if (migration.rewrite()) {
            writeAtomically(candidate);
        }
        current = candidate;
        return candidate;
    }

    /** Alias that emphasizes last-known-good behavior at an administrative reload call site. */
    public synchronized ThreatDirectorConfig reload() throws IOException {
        return load();
    }

    /** Returns the most recently activated immutable snapshot. */
    public ThreatDirectorConfig current() {
        return current;
    }

    public Path configFile() {
        return configFile;
    }

    private static Migration migrate(ThreatDirectorConfig candidate) {
        if (candidate == null || candidate.schemaVersion() != 1) {
            return new Migration(candidate, false);
        }
        if (candidate.equals(ThreatDirectorConfig.legacyVanillaPlusV1())) {
            return new Migration(ThreatDirectorConfig.vanillaPlus(), true);
        }
        ThreatDirectorConfig migrated = new ThreatDirectorConfig(
                ThreatDirectorConfig.CURRENT_SCHEMA_VERSION,
                candidate.preset(),
                candidate.enabled(),
                candidate.director(),
                candidate.intensity(),
                candidate.encounter(),
                candidate.tactical(),
                candidate.multiplayer(),
                candidate.performance(),
                candidate.breachSimulation(),
                candidate.telemetry(),
                candidate.integrations(),
                candidate.debug());
        return new Migration(migrated, true);
    }

    private void writeAtomically(ThreatDirectorConfig config) throws IOException {
        Path temporary = Files.createTempFile(configDirectory, "threatdirector-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    GSON.toJson(config) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        configFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record Migration(ThreatDirectorConfig config, boolean rewrite) {}
}
