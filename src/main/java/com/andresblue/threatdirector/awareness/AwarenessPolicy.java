package com.andresblue.threatdirector.awareness;

import com.andresblue.threatdirector.config.ConfigAccess;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Pure assignment and detection policy. Minecraft-facing adapters only provide
 * observations and persist the resulting profile.
 */
public final class AwarenessPolicy {
    private static final AssignmentRates EASY = new AssignmentRates(0.0, 0.0, 0.0);

    private AwarenessPolicy() {
    }

    /** Maps Minecraft's stable difficulty ids: peaceful=0, easy=1, normal=2, hard=3. */
    public static AssignmentRates ratesForDifficulty(int difficultyId) {
        var config = ConfigAccess.current().modded().awareness();
        if (!ConfigAccess.current().moddedEnabled() || !config.enabled()) {
            return EASY;
        }
        if (difficultyId >= 3) {
            return new AssignmentRates(
                    config.movementSenseEnabled() ? config.hardMovementChance() : 0.0,
                    config.soundSenseEnabled() ? config.hardSoundChance() : 0.0,
                    config.lightSenseEnabled() ? config.hardLightChance() : 0.0);
        }
        if (difficultyId == 2) {
            return new AssignmentRates(
                    config.movementSenseEnabled() ? config.normalMovementChance() : 0.0,
                    config.soundSenseEnabled() ? config.normalSoundChance() : 0.0,
                    config.lightSenseEnabled() ? config.normalLightChance() : 0.0);
        }
        return EASY;
    }

    /** Independent rolls deliberately allow every combination, including no extra sense. */
    public static AwarenessProfile roll(long seed, int difficultyId) {
        AssignmentRates rates = ratesForDifficulty(difficultyId);
        SplittableRandom random = new SplittableRandom(seed);
        EnumSet<AwarenessSense> senses = EnumSet.noneOf(AwarenessSense.class);
        if (random.nextDouble() < rates.movementChance()) {
            senses.add(AwarenessSense.MOVEMENT);
        }
        if (random.nextDouble() < rates.soundChance()) {
            senses.add(AwarenessSense.SOUND);
        }
        if (random.nextDouble() < rates.lightChance()) {
            senses.add(AwarenessSense.LIGHT);
        }
        return new AwarenessProfile(senses);
    }

    public static boolean detectsMovement(AwarenessProfile profile, PlayerStimulus stimulus) {
        if (!profile.has(AwarenessSense.MOVEMENT)
                || !stimulus.lineOfSight()
                || stimulus.speedSquared() < ConfigAccess.current().modded().awareness()
                .minimumMovementSpeedSquared()) {
            return false;
        }
        var config = ConfigAccess.current().modded().awareness();
        double range = stimulus.sprinting()
                ? config.sprintingDetectionRange() : config.walkingDetectionRange();
        if (stimulus.sneaking()) {
            range *= config.sneakingRangeMultiplier();
        }
        return stimulus.distanceSquared() <= range * range;
    }

    public static boolean detectsLight(AwarenessProfile profile, PlayerStimulus stimulus) {
        if (!profile.has(AwarenessSense.LIGHT)
                || !stimulus.lineOfSight()
                || stimulus.blockLight() < ConfigAccess.current().modded().awareness()
                .minimumLightLevel()) {
            return false;
        }
        var config = ConfigAccess.current().modded().awareness();
        double range = config.lightBaseRange()
                + (stimulus.blockLight() - config.minimumLightLevel()) * config.lightRangePerLevel();
        return stimulus.distanceSquared() <= range * range;
    }

    /** Uses GameEvent ids so the classification remains testable without a Minecraft world. */
    public static Optional<SoundRules> classifyGameEvent(String gameEventId) {
        if (gameEventId == null) {
            return Optional.empty();
        }
        String normalized = gameEventId.toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        var config = ConfigAccess.current().modded().awareness();
        return switch (normalized) {
            case "block_destroy" -> Optional.of(new SoundRules(
                    AwarenessSoundType.MINING, config.miningSoundRange(),
                    config.miningSoundLifetimeTicks(), config.miningAttributionRadius()));
            case "explode" -> Optional.of(new SoundRules(
                    AwarenessSoundType.EXPLOSION, config.explosionSoundRange(),
                    config.explosionSoundLifetimeTicks(), config.explosionAttributionRadius()));
            case "block_open", "block_close" -> Optional.of(new SoundRules(
                    AwarenessSoundType.DOOR, config.doorSoundRange(),
                    config.doorSoundLifetimeTicks(), config.doorAttributionRadius()));
            case "container_open", "container_close" -> Optional.of(new SoundRules(
                    AwarenessSoundType.CONTAINER, config.containerSoundRange(),
                    config.containerSoundLifetimeTicks(), config.containerAttributionRadius()));
            case "entity_damage", "entity_die", "projectile_shoot" -> Optional.of(new SoundRules(
                    AwarenessSoundType.COMBAT, config.combatSoundRange(),
                    config.combatSoundLifetimeTicks(), config.combatAttributionRadius()));
            default -> Optional.empty();
        };
    }

    public static boolean canHear(AwarenessProfile profile, SoundRules rules,
                                  double distanceSquared, long ageTicks) {
        return profile.has(AwarenessSense.SOUND)
                && ageTicks >= 0L
                && ageTicks <= rules.lifetimeTicks()
                && distanceSquared <= rules.radius() * rules.radius();
    }

    public record AssignmentRates(double movementChance, double soundChance, double lightChance) {
        public AssignmentRates {
            requireProbability(movementChance, "movementChance");
            requireProbability(soundChance, "soundChance");
            requireProbability(lightChance, "lightChance");
        }

        private static void requireProbability(double value, String field) {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(field + " must be in [0, 1]");
            }
        }
    }

    public record PlayerStimulus(double distanceSquared, double speedSquared,
                                 boolean sprinting, boolean sneaking,
                                 int blockLight, boolean lineOfSight) {
        public PlayerStimulus {
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
                throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
            }
            if (!Double.isFinite(speedSquared) || speedSquared < 0.0) {
                throw new IllegalArgumentException("speedSquared must be finite and non-negative");
            }
            blockLight = Math.max(0, Math.min(15, blockLight));
        }
    }

    public record SoundRules(AwarenessSoundType type, double radius,
                             int lifetimeTicks, double attributionRadius) {
        public SoundRules {
            if (type == null || !Double.isFinite(radius) || radius <= 0.0
                    || lifetimeTicks <= 0 || !Double.isFinite(attributionRadius)
                    || attributionRadius < 0.0) {
                throw new IllegalArgumentException("Invalid sound rules");
            }
        }
    }
}
