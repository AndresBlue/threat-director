package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.ThreatDirectorMod;
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
        return clamp((clamp(threat, 0.0, 100.0) - ACTIVATION_THREAT)
                / (100.0 - ACTIVATION_THREAT), 0.0, 1.0);
    }

    public static double targetRange(TargetFamily family, int difficultyId, double threat) {
        if (difficultyId < 2 || threat < ACTIVATION_THREAT) {
            return 0.0;
        }
        double progress = progression(threat);
        return switch (family) {
            case SKELETON -> difficultyId >= 3
                    ? 32.0 + 96.0 * progress
                    : 24.0 + 40.0 * progress;
            case ZOMBIE -> difficultyId >= 3
                    ? 24.0 + 72.0 * progress
                    : 20.0 + 28.0 * progress;
            case GENERIC -> difficultyId >= 3
                    ? 24.0 + 24.0 * progress
                    : 20.0 + 28.0 * progress;
        };
    }

    public static double maximumTargetRange(TargetFamily family, int difficultyId) {
        return switch (family) {
            case SKELETON -> difficultyId >= 3 ? 128.0 : 64.0;
            case ZOMBIE -> difficultyId >= 3 ? 96.0 : 48.0;
            case GENERIC -> 48.0;
        };
    }

    public static double homingStrength(int difficultyId, double threat) {
        if (difficultyId < 2 || threat < ACTIVATION_THREAT) {
            return 0.0;
        }
        double progress = progression(threat);
        return difficultyId >= 3
                ? 0.08 + progress * 0.28
                : 0.04 + progress * 0.12;
    }

    public static double zombieHordeChance(int difficultyId, double threat) {
        if (difficultyId < 2 || threat < ACTIVATION_THREAT) {
            return 0.0;
        }
        double progress = progression(threat);
        return difficultyId >= 3
                ? 0.08 + progress * 0.27
                : 0.04 + progress * 0.08;
    }

    public static double zombieAlertRadius(int difficultyId, double threat) {
        if (difficultyId < 2 || threat < ACTIVATION_THREAT) {
            return 0.0;
        }
        double progress = progression(threat);
        return difficultyId >= 3
                ? 32.0 + progress * 64.0
                : 24.0 + progress * 24.0;
    }

    public static int zombieHordeCount(int difficultyId, double threat, int randomInclusiveRoll) {
        int minimum = difficultyId >= 3 ? 5 : 3;
        int maximum = difficultyId >= 3 ? 10 : 5;
        int boundedRoll = Math.max(0, Math.min(maximum - minimum, randomInclusiveRoll));
        return progression(threat) <= 0.0 ? 0 : minimum + boundedRoll;
    }

    /** Hard-mode spiders place a web rarely even at maximum threat. */
    public static double spiderCobwebChance(int difficultyId, double threat) {
        if (difficultyId < 3 || threat < ACTIVATION_THREAT) {
            return 0.0;
        }
        return 0.01 + progression(threat) * 0.05;
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
