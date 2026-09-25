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
        if (config.general() == null) {
            errors.add("general section is required");
        }
        if (config.vanilla() == null) {
            errors.add("vanilla section is required");
        }
        if (config.modded() == null) {
            errors.add("modded section is required");
        }
        if (config.general() == null || config.vanilla() == null || config.modded() == null) {
            return List.copyOf(errors);
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
        validateProgression(config.intensity(), errors);
        validateTargeting(config.vanilla().targeting(), errors);
        validateEncounterPressure(config.vanilla().pressure(), errors);
        validateModded(config.modded(), errors);

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

    private static void validateProgression(
            ThreatDirectorConfig.IntensitySection section, List<String> errors) {
        if (section == null) {
            return;
        }
        finiteRange("general.intensity.activationThreat", section.activationThreat(), 0.0, 100.0, errors);
        positiveFinite("general.intensity.playTimeHoursForMaximum", section.playTimeHoursForMaximum(), errors);
        positiveFinite("general.intensity.experienceLevelForMaximum", section.experienceLevelForMaximum(), errors);
        finiteRange("general.intensity.deathPlayTimePenalty", section.deathPlayTimePenalty(), 0.0, 1.0, errors);
        finiteRange("general.intensity.maximumDeathPlayTimePenalty",
                section.maximumDeathPlayTimePenalty(), 0.0, 1.0, errors);
        if (section.maximumDeathPlayTimePenalty() < section.deathPlayTimePenalty()) {
            errors.add("general.intensity.maximumDeathPlayTimePenalty must be >= deathPlayTimePenalty");
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
        if (section.hardMaxMobs() > 128) {
            errors.add("encounter.hardMaxMobs must be <= 128");
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

    private static void validateTargeting(
            ThreatDirectorConfig.TargetingSection section, List<String> errors) {
        if (section == null) {
            errors.add("vanilla.targeting section is required");
            return;
        }
        positiveFinite("vanilla.targeting.normalSkeletonRange", section.normalSkeletonRange(), errors);
        positiveFinite("vanilla.targeting.hardSkeletonRange", section.hardSkeletonRange(), errors);
        positiveFinite("vanilla.targeting.normalZombieRange", section.normalZombieRange(), errors);
        positiveFinite("vanilla.targeting.hardZombieRange", section.hardZombieRange(), errors);
        positiveFinite("vanilla.targeting.normalGenericRange", section.normalGenericRange(), errors);
        positiveFinite("vanilla.targeting.hardGenericRange", section.hardGenericRange(), errors);
        ordered("vanilla.targeting skeleton ranges", section.normalSkeletonRange(),
                section.hardSkeletonRange(), errors);
        ordered("vanilla.targeting zombie ranges", section.normalZombieRange(),
                section.hardZombieRange(), errors);
        ordered("vanilla.targeting generic ranges", section.normalGenericRange(),
                section.hardGenericRange(), errors);
    }

    private static void validateEncounterPressure(
            ThreatDirectorConfig.EncounterPressureSection section, List<String> errors) {
        if (section == null) {
            errors.add("vanilla.pressure section is required");
            return;
        }
        finiteRange("vanilla.pressure.easyDifficultyHazard", section.easyDifficultyHazard(), 0.0, 1.0, errors);
        finiteRange("vanilla.pressure.normalDifficultyHazard", section.normalDifficultyHazard(), 0.0, 1.0, errors);
        finiteRange("vanilla.pressure.hardDifficultyHazard", section.hardDifficultyHazard(), 0.0, 1.0, errors);
        ordered("vanilla.pressure Easy/Normal difficulty hazard", section.easyDifficultyHazard(),
                section.normalDifficultyHazard(), errors);
        ordered("vanilla.pressure Normal/Hard difficulty hazard", section.normalDifficultyHazard(),
                section.hardDifficultyHazard(), errors);
        finiteRange("vanilla.pressure.nightHazard", section.nightHazard(), 0.0, 1.0, errors);
        finiteRange("vanilla.pressure.darknessHazard", section.darknessHazard(), 0.0, 1.0, errors);
        finiteRange("vanilla.pressure.caveHazard", section.caveHazard(), 0.0, 1.0, errors);
        nonNegativeFinite("vanilla.pressure.intensityContextSpan", section.intensityContextSpan(), errors);
        if (section.easy() == null) {
            errors.add("vanilla.pressure.easy section is required");
        } else {
            nonNegative("vanilla.pressure.easy.mobCap", section.easy().mobCap(), errors);
            nonNegative("vanilla.pressure.easy.meleeAttackers", section.easy().meleeAttackers(), errors);
            positiveFinite("vanilla.pressure.easy.cadenceBaseMinusHazard",
                    section.easy().cadenceBaseMinusHazard(), errors);
            positiveFinite("vanilla.pressure.easy.cadenceMinimumMultiplier",
                    section.easy().cadenceMinimumMultiplier(), errors);
            positiveFinite("vanilla.pressure.easy.cadenceMaximumMultiplier",
                    section.easy().cadenceMaximumMultiplier(), errors);
            ordered("vanilla.pressure.easy cadence multipliers",
                    section.easy().cadenceMinimumMultiplier(),
                    section.easy().cadenceMaximumMultiplier(), errors);
            positiveFinite("vanilla.pressure.easy.cooldownMultiplier",
                    section.easy().cooldownMultiplier(), errors);
        }
        validatePressureTier("vanilla.pressure.normal", section.normal(), errors);
        validatePressureTier("vanilla.pressure.hard", section.hard(), errors);
        validatePressureTier("vanilla.pressure.bloodMoon", section.bloodMoon(), errors);
    }

    private static void validatePressureTier(
            String field, ThreatDirectorConfig.PressureTierSection tier, List<String> errors) {
        if (tier == null) {
            errors.add(field + " section is required");
            return;
        }
        nonNegativeFinite(field + ".encounterChanceBaseMultiplier",
                tier.encounterChanceBaseMultiplier(), errors);
        nonNegativeFinite(field + ".encounterChanceEnvironmentalBonus",
                tier.encounterChanceEnvironmentalBonus(), errors);
        finiteRange(field + ".mobCapBaseFraction", tier.mobCapBaseFraction(), 0.0, 1.0, errors);
        finiteRange(field + ".mobCapEnvironmentalFraction",
                tier.mobCapEnvironmentalFraction(), 0.0, 1.0, errors);
        if (tier.mobCapBaseFraction() + tier.mobCapEnvironmentalFraction() > 1.0) {
            errors.add(field + ".mobCapBaseFraction + mobCapEnvironmentalFraction must be <= 1");
        }
        probability(field + ".minimumMobFraction", tier.minimumMobFraction(), errors);
        probability(field + ".meleeFraction", tier.meleeFraction(), errors);
        nonNegative(field + ".reinforcementMinimum", tier.reinforcementMinimum(), errors);
        probability(field + ".reinforcementFraction", tier.reinforcementFraction(), errors);
        nonNegative(field + ".maximumReinforcementWaves", tier.maximumReinforcementWaves(), errors);
        nonNegative(field + ".reinforcementIntervalTicks", tier.reinforcementIntervalTicks(), errors);
        nonNegative(field + ".reinforcementEnvironmentReductionTicks",
                tier.reinforcementEnvironmentReductionTicks(), errors);
        if (tier.reinforcementEnvironmentReductionTicks() > tier.reinforcementIntervalTicks()) {
            errors.add(field + ".reinforcementEnvironmentReductionTicks must be <= reinforcementIntervalTicks");
        }
        positiveFinite(field + ".cadenceBaseMinusHazard", tier.cadenceBaseMinusHazard(), errors);
        positiveFinite(field + ".cadenceMinimumMultiplier", tier.cadenceMinimumMultiplier(), errors);
        positiveFinite(field + ".cadenceMaximumMultiplier", tier.cadenceMaximumMultiplier(), errors);
        ordered(field + " cadence multipliers", tier.cadenceMinimumMultiplier(),
                tier.cadenceMaximumMultiplier(), errors);
        positiveFinite(field + ".cooldownBaseMultiplier", tier.cooldownBaseMultiplier(), errors);
        nonNegativeFinite(field + ".cooldownEnvironmentalReduction",
                tier.cooldownEnvironmentalReduction(), errors);
        positiveFinite(field + ".cooldownMinimumMultiplier", tier.cooldownMinimumMultiplier(), errors);
        positiveFinite(field + ".cooldownMaximumMultiplier", tier.cooldownMaximumMultiplier(), errors);
        ordered(field + " cooldown multipliers", tier.cooldownMinimumMultiplier(),
                tier.cooldownMaximumMultiplier(), errors);
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
        // Both switches are intentionally configurable in 2.0. External adapters remain optional.
    }

    private static void validateModded(
            ThreatDirectorConfig.ModdedSection section, List<String> errors) {
        if (section.hud() == null) errors.add("modded.hud section is required");
        validateAwareness(section.awareness(), errors);
        validateTraits(section.traits(), errors);
        validateBloodMoon(section.bloodMoon(), errors);
        validateSkeleton(section.skeleton(), errors);
        validateZombie(section.zombie(), errors);
        validateSpider(section.spider(), errors);
        validateCreeper(section.creeper(), errors);
        validateEnderman(section.enderman(), errors);
        validateDrowned(section.drowned(), errors);
        if (section.otherHostiles() == null) {
            errors.add("modded.otherHostiles section is required");
        } else {
            positiveFinite("modded.otherHostiles.normalTargetRange", section.otherHostiles().normalTargetRange(), errors);
            positiveFinite("modded.otherHostiles.hardTargetRange", section.otherHostiles().hardTargetRange(), errors);
            positiveFinite("modded.otherHostiles.bloodMoonTargetRange", section.otherHostiles().bloodMoonTargetRange(), errors);
        }
    }

    private static void validateAwareness(
            ThreatDirectorConfig.AwarenessSection section, List<String> errors) {
        if (section == null) {
            errors.add("modded.awareness section is required");
            return;
        }
        probability("modded.awareness.normalMovementChance", section.normalMovementChance(), errors);
        probability("modded.awareness.normalSoundChance", section.normalSoundChance(), errors);
        probability("modded.awareness.normalLightChance", section.normalLightChance(), errors);
        probability("modded.awareness.hardMovementChance", section.hardMovementChance(), errors);
        probability("modded.awareness.hardSoundChance", section.hardSoundChance(), errors);
        probability("modded.awareness.hardLightChance", section.hardLightChance(), errors);
        positiveFinite("modded.awareness.walkingDetectionRange", section.walkingDetectionRange(), errors);
        positiveFinite("modded.awareness.sprintingDetectionRange", section.sprintingDetectionRange(), errors);
        finiteRange("modded.awareness.sneakingRangeMultiplier", section.sneakingRangeMultiplier(), 0.0, 1.0, errors);
        nonNegativeFinite("modded.awareness.minimumMovementSpeedSquared", section.minimumMovementSpeedSquared(), errors);
        range("modded.awareness.minimumLightLevel", section.minimumLightLevel(), 0, 15, errors);
        positiveFinite("modded.awareness.lightBaseRange", section.lightBaseRange(), errors);
        nonNegativeFinite("modded.awareness.lightRangePerLevel", section.lightRangePerLevel(), errors);
        positiveFinite("modded.awareness.miningSoundRange", section.miningSoundRange(), errors);
        positiveFinite("modded.awareness.explosionSoundRange", section.explosionSoundRange(), errors);
        positiveFinite("modded.awareness.doorSoundRange", section.doorSoundRange(), errors);
        positiveFinite("modded.awareness.containerSoundRange", section.containerSoundRange(), errors);
        positiveFinite("modded.awareness.combatSoundRange", section.combatSoundRange(), errors);
        nonNegativeFinite("modded.awareness.miningAttributionRadius", section.miningAttributionRadius(), errors);
        nonNegativeFinite("modded.awareness.explosionAttributionRadius", section.explosionAttributionRadius(), errors);
        nonNegativeFinite("modded.awareness.doorAttributionRadius", section.doorAttributionRadius(), errors);
        nonNegativeFinite("modded.awareness.containerAttributionRadius", section.containerAttributionRadius(), errors);
        nonNegativeFinite("modded.awareness.combatAttributionRadius", section.combatAttributionRadius(), errors);
        positive("modded.awareness.miningSoundLifetimeTicks", section.miningSoundLifetimeTicks(), errors);
        positive("modded.awareness.explosionSoundLifetimeTicks", section.explosionSoundLifetimeTicks(), errors);
        positive("modded.awareness.doorSoundLifetimeTicks", section.doorSoundLifetimeTicks(), errors);
        positive("modded.awareness.containerSoundLifetimeTicks", section.containerSoundLifetimeTicks(), errors);
        positive("modded.awareness.combatSoundLifetimeTicks", section.combatSoundLifetimeTicks(), errors);
    }

    private static void validateTraits(
            ThreatDirectorConfig.TraitsSection section, List<String> errors) {
        if (section == null) {
            errors.add("modded.traits section is required");
            return;
        }
        validateTraitTier("modded.traits.normal", section.normal(), errors);
        validateTraitTier("modded.traits.hard", section.hard(), errors);
    }

    private static void validateTraitTier(
            String field, ThreatDirectorConfig.TraitTierSection tier, List<String> errors) {
        if (tier == null) { errors.add(field + " section is required"); return; }
        probability(field + ".anyTraitChance", tier.anyTraitChance(), errors);
        probability(field + ".secondTraitChance", tier.secondTraitChance(), errors);
        probability(field + ".thirdTraitChance", tier.thirdTraitChance(), errors);
        range(field + ".maximumTraits", tier.maximumTraits(), 0, 3, errors);
        probability(field + ".cowardTriggerHealthFraction", tier.cowardTriggerHealthFraction(), errors);
        probability(field + ".cowardExitHealthFraction", tier.cowardExitHealthFraction(), errors);
        if (tier.cowardExitHealthFraction() < tier.cowardTriggerHealthFraction()) {
            errors.add(field + ".cowardExitHealthFraction must be >= cowardTriggerHealthFraction");
        }
        positiveFinite(field + ".cowardFleeSpeed", tier.cowardFleeSpeed(), errors);
        probability(field + ".berserkerTriggerHealthFraction", tier.berserkerTriggerHealthFraction(), errors);
        nonNegativeFinite(field + ".berserkerDamageMultiplier", tier.berserkerDamageMultiplier(), errors);
        nonNegativeFinite(field + ".berserkerSpeedMultiplier", tier.berserkerSpeedMultiplier(), errors);
        positive(field + ".regenerationIntervalTicks", tier.regenerationIntervalTicks(), errors);
        nonNegativeFinite(field + ".regenerationAmount", tier.regenerationAmount(), errors);
        positive(field + ".leaderIntervalTicks", tier.leaderIntervalTicks(), errors);
        nonNegativeFinite(field + ".leaderRadius", tier.leaderRadius(), errors);
        nonNegative(field + ".leaderMaximumAllies", tier.leaderMaximumAllies(), errors);
        nonNegativeFinite(field + ".swiftSpeedMultiplier", tier.swiftSpeedMultiplier(), errors);
        nonNegativeFinite(field + ".resilientArmorBonus", tier.resilientArmorBonus(), errors);
        probability(field + ".resilientKnockbackResistance", tier.resilientKnockbackResistance(), errors);
        positive(field + ".particleIntervalTicks", tier.particleIntervalTicks(), errors);
    }

    private static void validateBloodMoon(
            ThreatDirectorConfig.BloodMoonSection section, List<String> errors) {
        if (section == null) { errors.add("modded.bloodMoon section is required"); return; }
        range("modded.bloodMoon.minimumIntervalNights", section.minimumIntervalNights(), 1, 1000, errors);
        range("modded.bloodMoon.maximumIntervalNights", section.maximumIntervalNights(), 1, 1000, errors);
        if (section.minimumIntervalNights() > section.maximumIntervalNights()) {
            errors.add("modded.bloodMoon.minimumIntervalNights must be <= maximumIntervalNights");
        }
        range("modded.bloodMoon.nightStartTick", section.nightStartTick(), 0, 23_999, errors);
        if (section.omenMessages() == null || section.omenMessages().isEmpty()
                || section.omenMessages().stream().anyMatch(value -> value == null || value.isBlank())) {
            errors.add("modded.bloodMoon.omenMessages must contain non-blank messages");
        }
    }

    private static void validateSkeleton(
            ThreatDirectorConfig.SkeletonSection section, List<String> errors) {
        if (section == null) { errors.add("modded.skeleton section is required"); return; }
        positiveFinite("modded.skeleton.normalRange", section.normalRange(), errors);
        positiveFinite("modded.skeleton.hardRange", section.hardRange(), errors);
        positiveFinite("modded.skeleton.bloodMoonRange", section.bloodMoonRange(), errors);
        positive("modded.skeleton.normalAttackIntervalTicks", section.normalAttackIntervalTicks(), errors);
        positive("modded.skeleton.hardAttackIntervalTicks", section.hardAttackIntervalTicks(), errors);
        positive("modded.skeleton.bloodMoonAttackIntervalTicks", section.bloodMoonAttackIntervalTicks(), errors);
        probability("modded.skeleton.normalEffectChance", section.normalEffectChance(), errors);
        probability("modded.skeleton.hardEffectChance", section.hardEffectChance(), errors);
        probability("modded.skeleton.bloodMoonEffectChance", section.bloodMoonEffectChance(), errors);
        positive("modded.skeleton.minimumEffectDurationTicks", section.minimumEffectDurationTicks(), errors);
        positive("modded.skeleton.maximumEffectDurationTicks", section.maximumEffectDurationTicks(), errors);
        if (section.minimumEffectDurationTicks() > section.maximumEffectDurationTicks()) {
            errors.add("modded.skeleton.minimumEffectDurationTicks must be <= maximumEffectDurationTicks");
        }
        range("modded.skeleton.meanEffectDurationTicks", section.meanEffectDurationTicks(),
                section.minimumEffectDurationTicks(), section.maximumEffectDurationTicks(), errors);
        nonNegativeFinite("modded.skeleton.homingStrengthMultiplier", section.homingStrengthMultiplier(), errors);
        nonNegativeFinite("modded.skeleton.dashHorizontalSpeed", section.dashHorizontalSpeed(), errors);
        nonNegativeFinite("modded.skeleton.dashVerticalSpeed", section.dashVerticalSpeed(), errors);
        positive("modded.skeleton.dashMinimumCooldownTicks", section.dashMinimumCooldownTicks(), errors);
        positive("modded.skeleton.dashMaximumCooldownTicks", section.dashMaximumCooldownTicks(), errors);
        ordered("modded.skeleton ranges", section.normalRange(), section.hardRange(), errors);
        ordered("modded.skeleton hard/bloodMoon ranges", section.hardRange(), section.bloodMoonRange(), errors);
        if (section.dashMinimumCooldownTicks() > section.dashMaximumCooldownTicks()) {
            errors.add("modded.skeleton.dashMinimumCooldownTicks must be <= dashMaximumCooldownTicks");
        }
    }

    private static void validateZombie(
            ThreatDirectorConfig.ZombieSection section, List<String> errors) {
        if (section == null) { errors.add("modded.zombie section is required"); return; }
        validateZombieTier("modded.zombie.normal", section.normal(), errors);
        validateZombieTier("modded.zombie.hard", section.hard(), errors);
        validateZombieTier("modded.zombie.bloodMoon", section.bloodMoon(), errors);
        positiveFinite("modded.zombie.normalTargetRange", section.normalTargetRange(), errors);
        positiveFinite("modded.zombie.hardTargetRange", section.hardTargetRange(), errors);
        positiveFinite("modded.zombie.bloodMoonTargetRange", section.bloodMoonTargetRange(), errors);
        ordered("modded.zombie target ranges", section.normalTargetRange(), section.hardTargetRange(), errors);
        ordered("modded.zombie hard/Blood Moon target ranges", section.hardTargetRange(),
                section.bloodMoonTargetRange(), errors);
        positive("modded.zombie.axeShieldDisableTicks", section.axeShieldDisableTicks(), errors);
        probability("modded.zombie.normalHordeChanceAtActivation", section.normalHordeChanceAtActivation(), errors);
        probability("modded.zombie.normalHordeChanceAtMaximum", section.normalHordeChanceAtMaximum(), errors);
        probability("modded.zombie.hardHordeChanceAtActivation", section.hardHordeChanceAtActivation(), errors);
        probability("modded.zombie.hardHordeChanceAtMaximum", section.hardHordeChanceAtMaximum(), errors);
        probability("modded.zombie.bloodMoonHordeChanceAtActivation", section.bloodMoonHordeChanceAtActivation(), errors);
        probability("modded.zombie.bloodMoonHordeChanceAtMaximum", section.bloodMoonHordeChanceAtMaximum(), errors);
        positiveFinite("modded.zombie.nearbyAlertBaseRadius", section.nearbyAlertBaseRadius(), errors);
        positiveFinite("modded.zombie.normalAlertMaximumRadius", section.normalAlertMaximumRadius(), errors);
        positiveFinite("modded.zombie.hardAlertMaximumRadius", section.hardAlertMaximumRadius(), errors);
        positiveFinite("modded.zombie.bloodMoonAlertMaximumRadius", section.bloodMoonAlertMaximumRadius(), errors);
        positive("modded.zombie.normalHordeMinimum", section.normalHordeMinimum(), errors);
        positive("modded.zombie.normalHordeMaximum", section.normalHordeMaximum(), errors);
        positive("modded.zombie.hardHordeMinimum", section.hardHordeMinimum(), errors);
        positive("modded.zombie.hardHordeMaximum", section.hardHordeMaximum(), errors);
        positive("modded.zombie.bloodMoonHordeMinimum", section.bloodMoonHordeMinimum(), errors);
        positive("modded.zombie.bloodMoonHordeMaximum", section.bloodMoonHordeMaximum(), errors);
        ordered("modded.zombie normal horde chances", section.normalHordeChanceAtActivation(),
                section.normalHordeChanceAtMaximum(), errors);
        ordered("modded.zombie hard horde chances", section.hardHordeChanceAtActivation(),
                section.hardHordeChanceAtMaximum(), errors);
        ordered("modded.zombie Blood Moon horde chances", section.bloodMoonHordeChanceAtActivation(),
                section.bloodMoonHordeChanceAtMaximum(), errors);
        ordered("modded.zombie normal alert radius", section.nearbyAlertBaseRadius(),
                section.normalAlertMaximumRadius(), errors);
        ordered("modded.zombie hard alert radius", section.nearbyAlertBaseRadius(),
                section.hardAlertMaximumRadius(), errors);
        ordered("modded.zombie Blood Moon alert radius", section.nearbyAlertBaseRadius(),
                section.bloodMoonAlertMaximumRadius(), errors);
        boundedPair("modded.zombie.normalHorde", section.normalHordeMinimum(),
                section.normalHordeMaximum(), 64, errors);
        boundedPair("modded.zombie.hardHorde", section.hardHordeMinimum(),
                section.hardHordeMaximum(), 64, errors);
        boundedPair("modded.zombie.bloodMoonHorde", section.bloodMoonHordeMinimum(),
                section.bloodMoonHordeMaximum(), 64, errors);
    }

    private static void validateZombieTier(
            String field, ThreatDirectorConfig.ZombieTierSection tier, List<String> errors) {
        if (tier == null) { errors.add(field + " section is required"); return; }
        probability(field + ".equipmentChance", tier.equipmentChance(), errors);
        nonNegative(field + ".meleeToolChoices", tier.meleeToolChoices(), errors);
        probability(field + ".pickaxeChance", tier.pickaxeChance(), errors);
        probability(field + ".shieldChance", tier.shieldChance(), errors);
        probability(field + ".shieldBlockChance", tier.shieldBlockChance(), errors);
        probability(field + ".reinforcementChance", tier.reinforcementChance(), errors);
        nonNegative(field + ".maxReinforcements", tier.maxReinforcements(), errors);
        nonNegative(field + ".nearbyZombieCap", tier.nearbyZombieCap(), errors);
        nonNegative(field + ".reinforcementCooldownTicks", tier.reinforcementCooldownTicks(), errors);
        nonNegative(field + ".blockBreakCooldownTicks", tier.blockBreakCooldownTicks(), errors);
    }

    private static void validateSpider(
            ThreatDirectorConfig.SpiderSection section, List<String> errors) {
        if (section == null) { errors.add("modded.spider section is required"); return; }
        validateSpiderTier("modded.spider.normal", section.normal(), errors);
        validateSpiderTier("modded.spider.hard", section.hard(), errors);
        validateSpiderTier("modded.spider.bloodMoon", section.bloodMoon(), errors);
        probability("modded.spider.ordinaryCobwebChanceAtActivation", section.ordinaryCobwebChanceAtActivation(), errors);
        probability("modded.spider.ordinaryCobwebChanceAtMaximum", section.ordinaryCobwebChanceAtMaximum(), errors);
        probability("modded.spider.bloodMoonCobwebChanceAtActivation", section.bloodMoonCobwebChanceAtActivation(), errors);
        probability("modded.spider.bloodMoonCobwebChanceAtMaximum", section.bloodMoonCobwebChanceAtMaximum(), errors);
        ordered("modded.spider ordinary cobweb chances", section.ordinaryCobwebChanceAtActivation(),
                section.ordinaryCobwebChanceAtMaximum(), errors);
        ordered("modded.spider Blood Moon cobweb chances", section.bloodMoonCobwebChanceAtActivation(),
                section.bloodMoonCobwebChanceAtMaximum(), errors);
    }

    private static void validateSpiderTier(
            String field, ThreatDirectorConfig.SpiderTierSection tier, List<String> errors) {
        if (tier == null) { errors.add(field + " section is required"); return; }
        range(field + ".speedAmplifier", tier.speedAmplifier(), -1, 10, errors);
        range(field + ".jumpAmplifier", tier.jumpAmplifier(), -1, 10, errors);
        range(field + ".regenerationAmplifier", tier.regenerationAmplifier(), -1, 10, errors);
        range(field + ".strengthAmplifier", tier.strengthAmplifier(), -1, 10, errors);
        nonNegative(field + ".cobwebCooldownTicks", tier.cobwebCooldownTicks(), errors);
        range(field + ".hatchlingCount", tier.hatchlingCount(), 0, 16, errors);
        probability(field + ".hatchlingSpawnChance", tier.hatchlingSpawnChance(), errors);
    }

    private static void validateCreeper(
            ThreatDirectorConfig.CreeperSection section, List<String> errors) {
        if (section == null) { errors.add("modded.creeper section is required"); return; }
        positiveFinite("modded.creeper.ordinaryRearSpeedMultiplier", section.ordinaryRearSpeedMultiplier(), errors);
        probabilityPositive("modded.creeper.ordinaryMinimumFuseRatio", section.ordinaryMinimumFuseRatio(), errors);
        positiveFinite("modded.creeper.bloodMoonRearSpeedMultiplier", section.bloodMoonRearSpeedMultiplier(), errors);
        probabilityPositive("modded.creeper.bloodMoonMinimumFuseRatio", section.bloodMoonMinimumFuseRatio(), errors);
        positiveFinite("modded.creeper.rearApproachMaximumDistance", section.rearApproachMaximumDistance(), errors);
        positive("modded.creeper.breachFuseDelayTicks", section.breachFuseDelayTicks(), errors);
        positive("modded.creeper.dashCooldownTicks", section.dashCooldownTicks(), errors);
        nonNegativeFinite("modded.creeper.dashImpulse", section.dashImpulse(), errors);
        positiveFinite("modded.creeper.dashMaximumSpeed", section.dashMaximumSpeed(), errors);
        positive("modded.creeper.targetMemorySeconds", section.targetMemorySeconds(), errors);
        positiveFinite("modded.creeper.targetMemoryMaximumDistance", section.targetMemoryMaximumDistance(), errors);
        positiveFinite("modded.creeper.breachMaximumTargetDistance", section.breachMaximumTargetDistance(), errors);
        positiveFinite("modded.creeper.breachFuseDistance", section.breachFuseDistance(), errors);
        nonNegativeFinite("modded.creeper.maximumBreakableHardness", section.maximumBreakableHardness(), errors);
        positive("modded.creeper.breachAssignmentSeconds", section.breachAssignmentSeconds(), errors);
    }

    private static void validateEnderman(
            ThreatDirectorConfig.EndermanSection section, List<String> errors) {
        if (section == null) { errors.add("modded.enderman section is required"); return; }
        validateEndermanTier("modded.enderman.normal", section.normal(), errors);
        validateEndermanTier("modded.enderman.hard", section.hard(), errors);
        validateEndermanTier("modded.enderman.bloodMoon", section.bloodMoon(), errors);
    }

    private static void validateEndermanTier(
            String field, ThreatDirectorConfig.EndermanTierSection tier, List<String> errors) {
        if (tier == null) { errors.add(field + " section is required"); return; }
        nonNegativeFinite(field + ".allyAlertRadius", tier.allyAlertRadius(), errors);
        nonNegative(field + ".maxAlliesAlerted", tier.maxAlliesAlerted(), errors);
        probability(field + ".allyAlertChance", tier.allyAlertChance(), errors);
        nonNegative(field + ".pillarTriggerTicks", tier.pillarTriggerTicks(), errors);
        nonNegative(field + ".pillarTeleportCooldownTicks", tier.pillarTeleportCooldownTicks(), errors);
    }

    private static void validateDrowned(
            ThreatDirectorConfig.DrownedSection section, List<String> errors) {
        if (section == null) { errors.add("modded.drowned section is required"); return; }
        validateDrownedTier("modded.drowned.normal", section.normal(), errors);
        validateDrownedTier("modded.drowned.hard", section.hard(), errors);
        validateDrownedTier("modded.drowned.bloodMoon", section.bloodMoon(), errors);
    }

    private static void validateDrownedTier(
            String field, ThreatDirectorConfig.DrownedTierSection tier, List<String> errors) {
        if (tier == null) { errors.add(field + " section is required"); return; }
        positiveFinite(field + ".swimSpeedMultiplier", tier.swimSpeedMultiplier(), errors);
        probability(field + ".tridentChance", tier.tridentChance(), errors);
        probability(field + ".riptideChance", tier.riptideChance(), errors);
        range(field + ".riptideLevel", tier.riptideLevel(), 0, 5, errors);
        nonNegativeFinite(field + ".dashSpeed", tier.dashSpeed(), errors);
        nonNegative(field + ".dashCooldownTicks", tier.dashCooldownTicks(), errors);
        nonNegativeFinite(field + ".targetRange", tier.targetRange(), errors);
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

    private static void ordered(
            String field, double minimum, double maximum, List<String> errors) {
        if (Double.isFinite(minimum) && Double.isFinite(maximum) && minimum > maximum) {
            errors.add(field + " must have minimum/normal <= maximum/hard");
        }
    }

    private static void boundedPair(
            String field, int minimum, int maximum, int hardCap, List<String> errors) {
        if (minimum > maximum) {
            errors.add(field + "Minimum must be <= " + field + "Maximum");
        }
        if (maximum > hardCap) {
            errors.add(field + "Maximum must be <= " + hardCap);
        }
    }

    private static void probability(String field, double value, List<String> errors) {
        finiteRange(field, value, 0.0, 1.0, errors);
    }

    private static void probabilityPositive(String field, double value, List<String> errors) {
        if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
            errors.add(field + " must be finite and in (0, 1]");
        }
    }
}
