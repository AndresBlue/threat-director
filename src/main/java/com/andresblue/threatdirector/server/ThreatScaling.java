package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.config.ConfigAccess;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Per-player threat gates for the server-side mob extensions.
 *
 * <p>Threat starts affecting the extra behavior at 35 points. This keeps a newly
 * spawned, lightly equipped player close to vanilla while allowing a geared player
 * to reach the advertised Apocalypse ranges.</p>
 */
public final class ThreatScaling {
    public static final double ACTIVATION_THREAT = 35.0;

    private ThreatScaling() {
    }

    public static double playerThreat(ServerPlayerEntity player) {
        if (player == null) {
            return 0.0;
        }
        return ThreatDirectorMod.runtime()
                .map(runtime -> runtime.observations().state(player.getUuid()).intensity())
                .filter(Double::isFinite)
                .map(value -> clamp(value, 0.0, 100.0))
                .orElse(0.0);
    }

    public static double progression(double threat) {
        double activation = activationThreat();
        return clamp((clamp(threat, 0.0, 100.0) - activation)
                / Math.max(1.0, 100.0 - activation), 0.0, 1.0);
    }

    public static double activationThreat() {
        double configured = ConfigAccess.current().intensity().activationThreat();
        return Double.isFinite(configured) ? clamp(configured, 0.0, 99.0) : ACTIVATION_THREAT;
    }

    public static double targetRange(TargetFamily family, int difficultyId, double threat) {
        return targetRange(family, difficultyId, threat, false);
    }

    public static double targetRange(
            TargetFamily family, int difficultyId, double threat, boolean bloodMoon) {
        if (!targetingEnabled(family) || difficultyId < 2 || threat < activationThreat()) {
            return 0.0;
        }
        double progress = progression(threat);
        double maximum = maximumTargetRange(family, difficultyId, bloodMoon);
        return switch (family) {
            case SKELETON -> difficultyId >= 3
                    ? 32.0 + (maximum - 32.0) * progress
                    : 24.0 + (maximum - 24.0) * progress;
            case ZOMBIE -> difficultyId >= 3
                    ? 24.0 + (maximum - 24.0) * progress
                    : 20.0 + (maximum - 20.0) * progress;
            case GENERIC -> difficultyId >= 3
                    ? 24.0 + (maximum - 24.0) * progress
                    : 20.0 + (maximum - 20.0) * progress;
        };
    }

    public static double maximumTargetRange(TargetFamily family, int difficultyId) {
        return maximumTargetRange(family, difficultyId, false);
    }

    public static double maximumTargetRange(
            TargetFamily family, int difficultyId, boolean bloodMoon) {
        boolean useModded = moddedTargetingEnabled(family);
        var targeting = ConfigAccess.current().vanilla().targeting();
        return switch (family) {
            case SKELETON -> useModded
                    ? difficultyId >= 3
                    ? (bloodMoon ? ConfigAccess.current().modded().skeleton().bloodMoonRange()
                    : ConfigAccess.current().modded().skeleton().hardRange())
                    : ConfigAccess.current().modded().skeleton().normalRange()
                    : difficultyId >= 3 ? targeting.hardSkeletonRange() : targeting.normalSkeletonRange();
            case ZOMBIE -> useModded
                    ? difficultyId >= 3
                    ? (bloodMoon ? ConfigAccess.current().modded().zombie().bloodMoonTargetRange()
                    : ConfigAccess.current().modded().zombie().hardTargetRange())
                    : ConfigAccess.current().modded().zombie().normalTargetRange()
                    : difficultyId >= 3 ? targeting.hardZombieRange() : targeting.normalZombieRange();
            case GENERIC -> difficultyId >= 3
                    ? useModded && bloodMoon
                    ? ConfigAccess.current().modded().otherHostiles().bloodMoonTargetRange()
                    : useModded ? ConfigAccess.current().modded().otherHostiles().hardTargetRange()
                    : targeting.hardGenericRange()
                    : useModded ? ConfigAccess.current().modded().otherHostiles().normalTargetRange()
                    : targeting.normalGenericRange();
        };
    }

    /** True when either the vanilla-like AI layer or this family's modded module owns targeting. */
    public static boolean targetingEnabled(TargetFamily family) {
        return (ConfigAccess.current().vanillaEnabled()
                && ConfigAccess.current().vanilla().enhancedTargetingEnabled())
                || moddedTargetingEnabled(family);
    }

    private static boolean moddedTargetingEnabled(TargetFamily family) {
        if (!ConfigAccess.current().moddedEnabled()) {
            return false;
        }
        return switch (family) {
            case SKELETON -> ConfigAccess.current().modded().skeleton().enabled();
            case ZOMBIE -> ConfigAccess.current().modded().zombie().enabled();
            case GENERIC -> ConfigAccess.current().modded().otherHostiles().enabled();
        };
    }

    public static double homingStrength(int difficultyId, double threat) {
        return homingStrength(difficultyId, threat, false);
    }

    public static double homingStrength(int difficultyId, double threat, boolean bloodMoon) {
        if (!ConfigAccess.current().moddedEnabled()
                || !ConfigAccess.current().modded().skeleton().homingArrows()
                || difficultyId < 2 || threat < activationThreat()) {
            return 0.0;
        }
        double progress = progression(threat);
        double configuredMultiplier = ConfigAccess.current().modded().skeleton()
                .homingStrengthMultiplier();
        return (difficultyId >= 3
                ? bloodMoon ? 0.08 + progress * 0.28 : 0.06 + progress * 0.14
                : 0.04 + progress * 0.12) * configuredMultiplier;
    }

    public static double zombieHordeChance(int difficultyId, double threat) {
        return zombieHordeChance(difficultyId, threat, false);
    }

    public static double zombieHordeChance(int difficultyId, double threat, boolean bloodMoon) {
        if (!ConfigAccess.current().moddedEnabled()
                || !ConfigAccess.current().modded().zombie().lightningHordesEnabled()
                || difficultyId < 2 || threat < activationThreat()) {
            return 0.0;
        }
        double progress = progression(threat);
        var config = ConfigAccess.current().modded().zombie();
        double minimum = difficultyId >= 3
                ? bloodMoon ? config.bloodMoonHordeChanceAtActivation()
                : config.hardHordeChanceAtActivation()
                : config.normalHordeChanceAtActivation();
        double maximum = difficultyId >= 3
                ? bloodMoon ? config.bloodMoonHordeChanceAtMaximum()
                : config.hardHordeChanceAtMaximum()
                : config.normalHordeChanceAtMaximum();
        return minimum + progress * (maximum - minimum);
    }

    public static double zombieAlertRadius(int difficultyId, double threat) {
        return zombieAlertRadius(difficultyId, threat, false);
    }

    public static double zombieAlertRadius(int difficultyId, double threat, boolean bloodMoon) {
        if (!ConfigAccess.current().moddedEnabled()
                || !ConfigAccess.current().modded().zombie().nearbyAggroEnabled()
                || difficultyId < 2 || threat < activationThreat()) {
            return 0.0;
        }
        double progress = progression(threat);
        var config = ConfigAccess.current().modded().zombie();
        double base = config.nearbyAlertBaseRadius();
        double maximum = difficultyId >= 3
                ? bloodMoon ? config.bloodMoonAlertMaximumRadius()
                : config.hardAlertMaximumRadius()
                : config.normalAlertMaximumRadius();
        return base + progress * (maximum - base);
    }

    public static int zombieHordeCount(int difficultyId, double threat, int randomInclusiveRoll) {
        return zombieHordeCount(difficultyId, threat, randomInclusiveRoll, false);
    }

    public static int zombieHordeCount(
            int difficultyId, double threat, int randomInclusiveRoll, boolean bloodMoon) {
        var config = ConfigAccess.current().modded().zombie();
        int minimum = difficultyId >= 3
                ? bloodMoon ? config.bloodMoonHordeMinimum() : config.hardHordeMinimum()
                : config.normalHordeMinimum();
        int maximum = difficultyId >= 3
                ? bloodMoon ? config.bloodMoonHordeMaximum() : config.hardHordeMaximum()
                : config.normalHordeMaximum();
        int boundedRoll = Math.max(0, Math.min(maximum - minimum, randomInclusiveRoll));
        return progression(threat) <= 0.0 ? 0 : minimum + boundedRoll;
    }

    /** Hard-mode spiders place a web rarely even at maximum threat. */
    public static double spiderCobwebChance(int difficultyId, double threat) {
        return spiderCobwebChance(difficultyId, threat, false);
    }

    public static double spiderCobwebChance(int difficultyId, double threat, boolean bloodMoon) {
        if (!ConfigAccess.current().moddedEnabled()
                || !ConfigAccess.current().modded().spider().cobwebOnHitEnabled()
                || difficultyId < 3 || threat < activationThreat()) {
            return 0.0;
        }
        var config = ConfigAccess.current().modded().spider();
        double minimum = bloodMoon
                ? config.bloodMoonCobwebChanceAtActivation()
                : config.ordinaryCobwebChanceAtActivation();
        double maximum = bloodMoon
                ? config.bloodMoonCobwebChanceAtMaximum()
                : config.ordinaryCobwebChanceAtMaximum();
        return minimum + progression(threat) * (maximum - minimum);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum TargetFamily {
        SKELETON,
        ZOMBIE,
        GENERIC
    }
}
