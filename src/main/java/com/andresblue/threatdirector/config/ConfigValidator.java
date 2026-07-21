package com.andresblue.threatdirector.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates all server configuration before it becomes the active immutable snapshot. */
public final class ConfigValidator {
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9/._-]+$");

    private ConfigValidator() {}

    /**
     * Returns {@code config} when it is safe to activate, otherwise reports every detected issue in
     * one exception so an administrator can repair the file in a single pass.
     */
    public static ThreatDirectorConfig requireValid(ThreatDirectorConfig config) {
        List<String> violations = validate(config);
        if (!violations.isEmpty()) {
            throw new ConfigValidationException(violations);
        }
        return config;
    }

    /** Returns an immutable list of human-readable field violations. */
    public static List<String> validate(ThreatDirectorConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            return List.of("root must be a JSON object");
        }

        if (config.schemaVersion() != ThreatDirectorConfig.CURRENT_SCHEMA_VERSION) {
            errors.add("schemaVersion must be " + ThreatDirectorConfig.CURRENT_SCHEMA_VERSION);
        }
        if (config.preset() == null) {
            errors.add("preset must be VANILLA_PLUS");
        } else if (config.preset() != Preset.VANILLA_PLUS) {
            errors.add("preset is not supported by this MVP");
        }

        validateDirector(config.director(), config.intensity(), errors);
        validateIntensity(config.intensity(), errors);
        validateEncounter(config.encounter(), errors);
        validateTactical(config.tactical(), config.encounter(), errors);
        validateMultiplayer(config.multiplayer(), errors);
        validatePerformance(config.performance(), errors);
        validateBreach(config.breachSimulation(), errors);
        validateTelemetry(config.telemetry(), errors);
        validateIntegrations(config.integrations(), config.preset(), errors);
        validateDebug(config.debug(), errors);

        return List.copyOf(errors);
    }

    private static void validateDirector(
            ThreatDirectorConfig.DirectorSection section,
            ThreatDirectorConfig.IntensitySection intensity,
            List<String> errors) {
        if (section == null) {
            errors.add("director section is required");
            return;
        }
        positive("director.scanIntervalTicks", section.scanIntervalTicks(), errors);
        finiteRange(
                "director.encounterChancePerScan",
                section.encounterChancePerScan(),
                0.0,
                1.0,
                errors);
        positive("director.cooldownMinTicks", section.cooldownMinTicks(), errors);
        positive("director.cooldownMaxTicks", section.cooldownMaxTicks(), errors);
        if (section.cooldownMinTicks() > section.cooldownMaxTicks()) {
            errors.add("director.cooldownMinTicks must be <= cooldownMaxTicks");
        }

        double maximum = intensity == null ? 100.0 : intensity.maxIntensity();
        finiteRange("director.minimumIntensity", section.minimumIntensity(), 0.0, maximum, errors);
        validateIdentifiers("director.allowedDimensions", section.allowedDimensions(), errors);
    }

    private static void validateIntensity(
            ThreatDirectorConfig.IntensitySection section, List<String> errors) {
        if (section == null) {
            errors.add("intensity section is required");
            return;
        }
        positive("intensity.sampleIntervalTicks", section.sampleIntervalTicks(), errors);
        if (!Double.isFinite(section.smoothingAlpha())
                || section.smoothingAlpha() <= 0.0
                || section.smoothingAlpha() > 1.0) {
            errors.add("intensity.smoothingAlpha must be finite and in (0, 1]");
        }
        finiteRange(
                "intensity.maxIntensity", section.maxIntensity(), 1.0, 100.0, errors);
        finiteRange(
                "intensity.hysteresisPoints",
                section.hysteresisPoints(),
                0.0,
                section.maxIntensity(),
                errors);
        finiteRange(
                "intensity.minimumHealthRatio",
                section.minimumHealthRatio(),
                0.0,
                1.0,
                errors);
        nonNegative("intensity.recentDamageWindowTicks", section.recentDamageWindowTicks(), errors);
        nonNegative("intensity.recentDeathCooldownTicks", section.recentDeathCooldownTicks(), errors);

        nonNegativeFinite("intensity.defenseCap", section.defenseCap(), errors);
        nonNegativeFinite("intensity.offenseCap", section.offenseCap(), errors);
        nonNegativeFinite("intensity.sustainCap", section.sustainCap(), errors);
        nonNegativeFinite("intensity.groupCap", section.groupCap(), errors);
        nonNegativeFinite("intensity.contextCap", section.contextCap(), errors);
        nonNegativeFinite("intensity.stressPenaltyCap", section.stressPenaltyCap(), errors);
        double positiveCaps = section.defenseCap()
                + section.offenseCap()
                + section.sustainCap()
                + section.groupCap()
                + section.contextCap();
        if (Double.isFinite(positiveCaps) && positiveCaps > section.maxIntensity()) {
            errors.add("intensity positive contribution caps must sum to <= maxIntensity");
        }
    }

    private static void validateEncounter(
            ThreatDirectorConfig.EncounterSection section, List<String> errors) {
        if (section == null) {
            errors.add("encounter section is required");
            return;
        }
        positive("encounter.minMobs", section.minMobs(), errors);
        positive("encounter.maxMobs", section.maxMobs(), errors);
        positive("encounter.hardMaxMobs", section.hardMaxMobs(), errors);
        if (section.minMobs() > section.maxMobs()) {
            errors.add("encounter.minMobs must be <= maxMobs");
        }
        if (section.maxMobs() > section.hardMaxMobs()) {
            errors.add("encounter.maxMobs must be <= hardMaxMobs");
        }
        if (section.hardMaxMobs() > 10) {
            errors.add("encounter.hardMaxMobs must be <= 10");
        }
        positive("encounter.maxActiveEncounters", section.maxActiveEncounters(), errors);
        positive("encounter.recruitRadius", section.recruitRadius(), errors);
        positive("encounter.spawnMinDistance", section.spawnMinDistance(), errors);
        positive("encounter.spawnMaxDistance", section.spawnMaxDistance(), errors);
        if (section.spawnMinDistance() >= section.spawnMaxDistance()) {
            errors.add("encounter.spawnMinDistance must be < spawnMaxDistance");
        }
        if (section.spawnMaxDistance() > section.recruitRadius()) {
            errors.add("encounter.spawnMaxDistance must be <= recruitRadius");
        }
        nonNegative("encounter.maxNearbyHostiles", section.maxNearbyHostiles(), errors);
        if (section.maxNearbyHostiles() < section.maxMobs()) {
            errors.add("encounter.maxNearbyHostiles must be >= maxMobs");
        }

        positive("encounter.stalkingMinTicks", section.stalkingMinTicks(), errors);
        positive("encounter.stalkingMaxTicks", section.stalkingMaxTicks(), errors);
        if (section.stalkingMinTicks() > section.stalkingMaxTicks()) {
            errors.add("encounter.stalkingMinTicks must be <= stalkingMaxTicks");
        }
        positive("encounter.positioningMaxTicks", section.positioningMaxTicks(), errors);
        positive("encounter.engagingMaxTicks", section.engagingMaxTicks(), errors);
        positive("encounter.reliefTicks", section.reliefTicks(), errors);
        positive("encounter.contactLossTicks", section.contactLossTicks(), errors);
        positiveFinite("encounter.maxPursuitDistance", section.maxPursuitDistance(), errors);
        if (section.maxPursuitDistance() < section.spawnMaxDistance()) {
            errors.add("encounter.maxPursuitDistance must be >= spawnMaxDistance");
        }

        validateIdentifiers("encounter.allowedMobIds", section.allowedMobIds(), errors);
        validateThreatCosts(section.threatCosts(), section.allowedMobIds(), errors);
    }

    private static void validateTactical(
            ThreatDirectorConfig.TacticalSection section,
            ThreatDirectorConfig.EncounterSection encounter,
            List<String> errors) {
        if (section == null) {
            errors.add("tactical section is required");
            return;
        }
        positiveFinite("tactical.positionMinRadius", section.positionMinRadius(), errors);
        positiveFinite("tactical.positionMaxRadius", section.positionMaxRadius(), errors);
        if (section.positionMinRadius() >= section.positionMaxRadius()) {
            errors.add("tactical.positionMinRadius must be < positionMaxRadius");
        }
        if (encounter != null
                && section.positionMaxRadius() >= encounter.spawnMinDistance()) {
            errors.add("tactical.positionMaxRadius must be < encounter.spawnMinDistance");
        }
        positive("tactical.maxCandidatesPerSnapshot", section.maxCandidatesPerSnapshot(), errors);
        positive("tactical.maxPathAttemptsPerMob", section.maxPathAttemptsPerMob(), errors);
        positive("tactical.replanIntervalTicks", section.replanIntervalTicks(), errors);
        positiveFinite(
                "tactical.targetMoveReplanDistance", section.targetMoveReplanDistance(), errors);
        positive(
                "tactical.maxSimultaneousMeleeAttackers",
                section.maxSimultaneousMeleeAttackers(),
                errors);
    }

    private static void validateMultiplayer(
            ThreatDirectorConfig.MultiplayerSection section, List<String> errors) {
        if (section == null) {
            errors.add("multiplayer section is required");
            return;
        }
        positiveFinite("multiplayer.groupRadius", section.groupRadius(), errors);
        nonNegativeFinite(
                "multiplayer.additionalPlayerScale", section.additionalPlayerScale(), errors);
        finiteRange(
                "multiplayer.maxGroupMultiplier",
                section.maxGroupMultiplier(),
                1.0,
                4.0,
                errors);
    }

    private static void validatePerformance(
            ThreatDirectorConfig.PerformanceSection section, List<String> errors) {
        if (section == null) {
            errors.add("performance section is required");
            return;
        }
        range(
                "performance.workBudgetMicrosPerTick",
                section.workBudgetMicrosPerTick(),
                100,
                50_000,
                errors);
        positive(
                "performance.maxPlannerOperationsPerTick",
                section.maxPlannerOperationsPerTick(),
                errors);
        positive("performance.degradeAfterTicks", section.degradeAfterTicks(), errors);
        positive("performance.recoverAfterTicks", section.recoverAfterTicks(), errors);
        if (section.recoverAfterTicks() < section.degradeAfterTicks()) {
            errors.add("performance.recoverAfterTicks must be >= degradeAfterTicks");
        }

        double[] thresholds = {
            section.level1TickTimeMs(),
            section.level2TickTimeMs(),
            section.level3TickTimeMs(),
            section.level4TickTimeMs(),
            section.level5TickTimeMs(),
            section.level6TickTimeMs()
        };
        for (int index = 0; index < thresholds.length; index++) {
            positiveFinite("performance.level" + (index + 1) + "TickTimeMs", thresholds[index], errors);
            if (index > 0 && thresholds[index] <= thresholds[index - 1]) {
                errors.add("performance tick-time thresholds must be strictly increasing");
                break;
            }
        }
    }

    private static void validateBreach(
            ThreatDirectorConfig.BreachSimulationSection section, List<String> errors) {
        if (section == null) {
            errors.add("breachSimulation section is required");
            return;
        }
        if (!section.simulationOnly()) {
            errors.add("breachSimulation.simulationOnly must remain true in the MVP");
        }
        positive("breachSimulation.maxRayDistance", section.maxRayDistance(), errors);
        range("breachSimulation.maxBlocksInPlan", section.maxBlocksInPlan(), 1, 4, errors);
        nonNegativeFinite(
                "breachSimulation.maxBlockHardness", section.maxBlockHardness(), errors);
        nonNegative(
                "breachSimulation.spawnProtectionRadius", section.spawnProtectionRadius(), errors);
    }

    private static void validateTelemetry(
            ThreatDirectorConfig.TelemetrySection section, List<String> errors) {
        if (section == null) {
            errors.add("telemetry section is required");
            return;
        }
        positive("telemetry.queueCapacity", section.queueCapacity(), errors);
        positive("telemetry.flushBatchSize", section.flushBatchSize(), errors);
        if (section.flushBatchSize() > section.queueCapacity()) {
            errors.add("telemetry.flushBatchSize must be <= queueCapacity");
        }
    }

    private static void validateIntegrations(
            ThreatDirectorConfig.IntegrationsSection section,
            Preset preset,
            List<String> errors) {
        if (section == null) {
            errors.add("integrations section is required");
            return;
        }
        if (preset == Preset.VANILLA_PLUS) {
            if (section.enableExternalAdapters()) {
                errors.add("integrations.enableExternalAdapters must be false for VANILLA_PLUS");
            }
            if (!section.forceVanillaMobs()) {
                errors.add("integrations.forceVanillaMobs must be true for VANILLA_PLUS");
            }
        }
    }

    private static void validateDebug(
            ThreatDirectorConfig.DebugSection section, List<String> errors) {
        if (section == null) {
            errors.add("debug section is required");
            return;
        }
        positive("debug.particleDurationTicks", section.particleDurationTicks(), errors);
    }

    private static void validateThreatCosts(
            Map<String, Double> costs, List<String> allowedMobIds, List<String> errors) {
        if (costs == null || costs.isEmpty()) {
            errors.add("encounter.threatCosts must contain at least one entry");
            return;
        }
        for (Map.Entry<String, Double> entry : costs.entrySet()) {
            if (!isIdentifier(entry.getKey())) {
                errors.add("encounter.threatCosts contains invalid id: " + entry.getKey());
            }
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value) || value <= 0.0 || value > 100.0) {
                errors.add("encounter.threatCosts[" + entry.getKey() + "] must be in (0, 100]");
            }
        }
        if (allowedMobIds != null) {
            for (String mobId : allowedMobIds) {
                if (!costs.containsKey(mobId)) {
                    errors.add("encounter.threatCosts is missing allowed mob: " + mobId);
                }
            }
        }
    }

    private static void validateIdentifiers(
            String field, List<String> identifiers, List<String> errors) {
        if (identifiers == null || identifiers.isEmpty()) {
            errors.add(field + " must contain at least one identifier");
            return;
        }
        Set<String> unique = new HashSet<>();
        for (String identifier : identifiers) {
            if (!isIdentifier(identifier)) {
                errors.add(field + " contains invalid id: " + identifier);
            } else if (!unique.add(identifier)) {
                errors.add(field + " contains duplicate id: " + identifier);
            }
        }
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    private static void positive(String field, int value, List<String> errors) {
        if (value <= 0) {
            errors.add(field + " must be > 0");
        }
    }

    private static void nonNegative(String field, int value, List<String> errors) {
        if (value < 0) {
            errors.add(field + " must be >= 0");
        }
    }

    private static void range(
            String field, int value, int minimum, int maximum, List<String> errors) {
        if (value < minimum || value > maximum) {
            errors.add(field + " must be in [" + minimum + ", " + maximum + "]");
        }
    }

    private static void positiveFinite(String field, double value, List<String> errors) {
        if (!Double.isFinite(value) || value <= 0.0) {
            errors.add(field + " must be finite and > 0");
        }
    }

    private static void nonNegativeFinite(String field, double value, List<String> errors) {
        if (!Double.isFinite(value) || value < 0.0) {
            errors.add(field + " must be finite and >= 0");
        }
    }

    private static void finiteRange(
            String field, double value, double minimum, double maximum, List<String> errors) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            errors.add(field + " must be finite and in [" + minimum + ", " + maximum + "]");
        }
    }
}
