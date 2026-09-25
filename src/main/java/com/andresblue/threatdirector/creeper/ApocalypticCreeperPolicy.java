package com.andresblue.threatdirector.creeper;

import com.andresblue.threatdirector.config.ConfigAccess;
import java.util.Objects;

/**
 * Pure policy for the server-side creeper difficulty extensions.
 *
 * <p>The Minecraft-facing mixins deliberately keep world and entity access out
 * of this class so the balance rules can be tested without bootstrapping a
 * game server.</p>
 */
public final class ApocalypticCreeperPolicy {
    public static final double REAR_APPROACH_MAX_DISTANCE_SQUARED = 18.0D * 18.0D;
    public static final double WALL_FUSE_MAX_DISTANCE_SQUARED = 4.5D * 4.5D;
    public static final int WALL_FUSE_DELAY_TICKS = 20;
    public static final double DASH_MIN_DISTANCE_SQUARED = 4.0D * 4.0D;
    public static final double DASH_MAX_DISTANCE_SQUARED = 10.0D * 10.0D;
    public static final int DASH_COOLDOWN_TICKS = 60;
    public static final double DASH_IMPULSE = 0.38D;
    public static final double DASH_MAX_HORIZONTAL_SPEED = 0.65D;
    public static final int TARGET_MEMORY_TICKS = 20 * 45;
    public static final double TARGET_MEMORY_MAX_DISTANCE_SQUARED = 48.0D * 48.0D;
    public static final double BREACH_MAX_TARGET_DISTANCE_SQUARED = 28.0D * 28.0D;
    public static final double BREACH_FUSE_DISTANCE_SQUARED = 3.0D * 3.0D;

    private static final double FACING_AWAY_DOT_THRESHOLD = -0.25D;
    private static final Profile VANILLA = new Profile(1.0D, 1.0D, false, false, false);
    private static final Profile PARTIAL = new Profile(1.35D, 0.70D, false, false, false);
    private static final Profile APOCALYPTIC = new Profile(2.0D, 0.30D, true, true, true);

    private ApocalypticCreeperPolicy() {
    }

    public static Profile profile(Mode mode) {
        if (mode != Mode.VANILLA && ConfigAccess.current().moddedEnabled()) {
            var config = ConfigAccess.current().modded().creeper();
            boolean apocalypse = mode == Mode.APOCALYPTIC;
            return new Profile(
                    config.rearSpeedBoost()
                            ? apocalypse ? config.bloodMoonRearSpeedMultiplier()
                            : config.ordinaryRearSpeedMultiplier() : 1.0,
                    config.randomFuse()
                            ? apocalypse ? config.bloodMoonMinimumFuseRatio()
                            : config.ordinaryMinimumFuseRatio() : 1.0,
                    config.silentSteps() && apocalypse,
                    config.dashEnabled() && apocalypse,
                    config.coordinatedBreaching() && apocalypse);
        }
        return switch (Objects.requireNonNull(mode, "mode")) {
            case VANILLA -> VANILLA;
            case PARTIAL -> PARTIAL;
            case APOCALYPTIC -> APOCALYPTIC;
        };
    }

    /**
     * Tests whether the target's horizontal look vector points away from the
     * creeper. Zero-length input is treated conservatively as not facing away.
     */
    public static boolean targetFacesAway(
            double targetLookX,
            double targetLookZ,
            double targetToCreeperX,
            double targetToCreeperZ
    ) {
        double lookLength = Math.hypot(targetLookX, targetLookZ);
        double offsetLength = Math.hypot(targetToCreeperX, targetToCreeperZ);
        if (lookLength < 1.0E-6D || offsetLength < 1.0E-6D) {
            return false;
        }

        double normalizedDot = (
                targetLookX * targetToCreeperX + targetLookZ * targetToCreeperZ
        ) / (lookLength * offsetLength);
        return normalizedDot <= FACING_AWAY_DOT_THRESHOLD;
    }

    public static boolean shouldBoostRearApproach(
            Profile profile,
            boolean targetPresent,
            boolean targetFacesAway,
            boolean priming,
            double distanceSquared
    ) {
        return profile.rearApproachMultiplier() > 1.0D
                && targetPresent
                && targetFacesAway
                && !priming
                && distanceSquared <= square(ConfigAccess.current().modded().creeper()
                .rearApproachMaximumDistance());
    }

    /**
     * Selects an inclusive fuse duration in the profile's configured range.
     * The sample is clamped to [0, 1], which also makes boundary tests exact.
     */
    public static int randomizedFuseTicks(Profile profile, int defaultFuseTicks, double sample) {
        int maximum = Math.max(1, defaultFuseTicks);
        int minimum = Math.max(1, (int) Math.ceil(maximum * profile.minimumFuseRatio()));
        if (minimum >= maximum) {
            return maximum;
        }

        double normalizedSample = Math.max(0.0D, Math.min(1.0D, sample));
        if (normalizedSample >= 1.0D) {
            return maximum;
        }
        int inclusiveRange = maximum - minimum + 1;
        return minimum + (int) Math.floor(normalizedSample * inclusiveRange);
    }

    /**
     * Returns the minimum server fuse progress immediately before vanilla adds
     * its one tick. This reaches the vanilla threshold on the selected tick
     * without changing the persisted vanilla fuse length.
     */
    public static int minimumFuseProgressBeforeVanillaTick(
            int defaultFuseTicks,
            int selectedFuseTicks,
            int elapsedPrimingTicks
    ) {
        int maximum = Math.max(1, defaultFuseTicks);
        int duration = Math.max(1, selectedFuseTicks);
        int elapsed = Math.max(1, Math.min(duration, elapsedPrimingTicks));
        long scaled = (long) elapsed * maximum;
        int progress = (int) ((scaled + duration - 1L) / duration) - 1;
        return Math.max(0, Math.min(maximum - 1, progress));
    }

    public static boolean isWallFuseCandidate(
            Profile profile,
            boolean targetPresent,
            boolean mobGriefing,
            boolean targetVisible,
            boolean movementObstructed,
            boolean alreadyPriming,
            double distanceSquared
    ) {
        return profile.wallFuseEnabled()
                && targetPresent
                && mobGriefing
                && !targetVisible
                && movementObstructed
                && !alreadyPriming
                && distanceSquared <= WALL_FUSE_MAX_DISTANCE_SQUARED;
    }

    public static boolean shouldDash(
            Profile profile,
            boolean targetPresent,
            boolean targetFacesAway,
            boolean targetVisible,
            boolean onGround,
            boolean priming,
            int cooldownTicks,
            double distanceSquared
    ) {
        return profile.dashEnabled()
                && targetPresent
                && targetFacesAway
                && targetVisible
                && onGround
                && !priming
                && cooldownTicks <= 0
                && distanceSquared >= DASH_MIN_DISTANCE_SQUARED
                && distanceSquared <= DASH_MAX_DISTANCE_SQUARED;
    }

    public static boolean shouldRetainPlayerTarget(
            boolean modEnabled,
            int difficultyId,
            double threat,
            double activationThreat,
            boolean playerAlive,
            boolean sameWorld,
            long memoryTicksRemaining,
            double distanceSquared
    ) {
        return modEnabled
                && difficultyId >= 2
                && threat >= activationThreat
                && playerAlive
                && sameWorld
                && memoryTicksRemaining > 0
                && distanceSquared <= square(ConfigAccess.current().modded().creeper()
                .targetMemoryMaximumDistance());
    }

    public static boolean shouldCoordinateBreach(
            boolean modEnabled,
            int difficultyId,
            double threat,
            double activationThreat,
            boolean mobGriefing,
            boolean targetAlive,
            boolean targetVisible,
            boolean targetSheltered,
            double distanceSquared
    ) {
        return modEnabled
                && difficultyId >= 3
                && threat >= activationThreat
                && mobGriefing
                && targetAlive
                && !targetVisible
                && targetSheltered
                && distanceSquared <= square(ConfigAccess.current().modded().creeper()
                .breachMaximumTargetDistance());
    }

    /**
     * Adds a horizontal impulse toward the target and clamps the resulting
     * speed. Collision handling remains entirely vanilla.
     */
    public static HorizontalVelocity boundedDashVelocity(
            double currentX,
            double currentZ,
            double targetOffsetX,
            double targetOffsetZ
    ) {
        double offsetLength = Math.hypot(targetOffsetX, targetOffsetZ);
        if (offsetLength < 1.0E-6D) {
            return new HorizontalVelocity(currentX, currentZ);
        }

        double impulse = ConfigAccess.current().moddedEnabled()
                ? ConfigAccess.current().modded().creeper().dashImpulse() : DASH_IMPULSE;
        double maximum = ConfigAccess.current().moddedEnabled()
                ? ConfigAccess.current().modded().creeper().dashMaximumSpeed()
                : DASH_MAX_HORIZONTAL_SPEED;
        double nextX = currentX + targetOffsetX / offsetLength * impulse;
        double nextZ = currentZ + targetOffsetZ / offsetLength * impulse;
        double nextSpeed = Math.hypot(nextX, nextZ);
        if (nextSpeed > maximum) {
            double scale = maximum / nextSpeed;
            nextX *= scale;
            nextZ *= scale;
        }
        return new HorizontalVelocity(nextX, nextZ);
    }

    public enum Mode {
        VANILLA,
        PARTIAL,
        APOCALYPTIC
    }

    public record Profile(
            double rearApproachMultiplier,
            double minimumFuseRatio,
            boolean silentSteps,
            boolean dashEnabled,
            boolean wallFuseEnabled
    ) {
        public Profile {
            if (rearApproachMultiplier < 1.0D) {
                throw new IllegalArgumentException("rearApproachMultiplier must be at least 1");
            }
            if (minimumFuseRatio <= 0.0D || minimumFuseRatio > 1.0D) {
                throw new IllegalArgumentException("minimumFuseRatio must be in (0, 1]");
            }
        }
    }

    public record HorizontalVelocity(double x, double z) {
    }

    private static double square(double value) {
        return value * value;
    }
}
