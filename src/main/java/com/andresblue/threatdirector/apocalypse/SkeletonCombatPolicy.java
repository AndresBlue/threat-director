package com.andresblue.threatdirector.apocalypse;

/**
 * Pure balance and ballistics rules for the server-side skeleton module.
 * Minecraft difficulty ids are used deliberately so this class can be tested
 * without constructing game objects.
 */
public final class SkeletonCombatPolicy {
    public static final double VANILLA_RANGE = 15.0;
    /** Maximum enhanced range at full threat. The live goal scales this per player. */
    public static final double NORMAL_RANGE = 64.0;
    public static final double HARD_RANGE = 128.0;

    private static final int NORMAL_DIFFICULTY_ID = 2;
    private static final int HARD_DIFFICULTY_ID = 3;
    private static final double VANILLA_ARROW_SPEED = 1.6;
    private static final double PREDICTION_START_DISTANCE = 12.0;
    private static final double MAX_PREDICTION_TICKS = 20.0;
    private static final double MAX_HORIZONTAL_LEAD = 8.0;
    private static final double MAX_VERTICAL_LEAD = 3.0;
    private static final double HARD_DASH_DISTANCE_SQUARED = 49.0;
    private static final double MIN_DASH_DISTANCE_SQUARED = 1.0;

    private SkeletonCombatPolicy() {
    }

    public static boolean isEnhancedDifficulty(int difficultyId) {
        return difficultyId >= NORMAL_DIFFICULTY_ID;
    }

    public static double rangedAttackRange(int difficultyId) {
        if (difficultyId >= HARD_DIFFICULTY_ID) {
            return HARD_RANGE;
        }
        if (difficultyId >= NORMAL_DIFFICULTY_ID) {
            return NORMAL_RANGE;
        }
        return VANILLA_RANGE;
    }

    public static int rangedAttackInterval(int difficultyId) {
        return difficultyId >= HARD_DIFFICULTY_ID ? 20 : 40;
    }

    public static AimVector solveAim(
            int difficultyId,
            double originX,
            double originY,
            double originZ,
            double targetX,
            double targetY,
            double targetZ,
            double targetVelocityX,
            double targetVelocityY,
            double targetVelocityZ) {
        double deltaX = targetX - originX;
        double deltaY = targetY - originY;
        double deltaZ = targetZ - originZ;
        double horizontalDistance = Math.hypot(deltaX, deltaZ);
        float vanillaDivergence = 14.0F - clampDifficulty(difficultyId) * 4.0F;
        float projectileSpeed = difficultyId >= HARD_DIFFICULTY_ID
                ? 2.2F
                : difficultyId >= NORMAL_DIFFICULTY_ID ? 1.8F : (float) VANILLA_ARROW_SPEED;

        if (!isEnhancedDifficulty(difficultyId)
                || horizontalDistance < PREDICTION_START_DISTANCE) {
            return new AimVector(
                    deltaX,
                    deltaY + horizontalDistance * 0.2,
                    deltaZ,
                    projectileSpeed,
                    difficultyId >= HARD_DIFFICULTY_ID ? 0.5F : vanillaDivergence);
        }

        double predictionStrength = difficultyId >= HARD_DIFFICULTY_ID ? 0.85 : 0.45;
        double predictionTicks = Math.min(horizontalDistance / projectileSpeed, MAX_PREDICTION_TICKS);
        double leadX = clamp(
                finiteOrZero(targetVelocityX) * predictionTicks * predictionStrength,
                -MAX_HORIZONTAL_LEAD,
                MAX_HORIZONTAL_LEAD);
        double leadY = clamp(
                finiteOrZero(targetVelocityY) * predictionTicks * predictionStrength,
                -MAX_VERTICAL_LEAD,
                MAX_VERTICAL_LEAD);
        double leadZ = clamp(
                finiteOrZero(targetVelocityZ) * predictionTicks * predictionStrength,
                -MAX_HORIZONTAL_LEAD,
                MAX_HORIZONTAL_LEAD);

        double predictedX = deltaX + leadX;
        double predictedZ = deltaZ + leadZ;
        double predictedHorizontalDistance = Math.hypot(predictedX, predictedZ);
        float divergence = difficultyId >= HARD_DIFFICULTY_ID ? 0.25F : 3.0F;
        return new AimVector(
                predictedX,
                deltaY + leadY + predictedHorizontalDistance * 0.2,
                predictedZ,
                projectileSpeed,
                divergence);
    }

    /**
     * Backwards-compatible deterministic payload helper used by older callers/tests.
     * The same sample drives effect selection and duration, while live arrows use
     * independent samples for less predictable timing.
     */
    public static ArrowPayload arrowPayload(int difficultyId, double roll) {
        return arrowPayload(difficultyId, roll, roll);
    }

    public static ArrowPayload arrowPayload(int difficultyId, double roll, double durationRoll) {
        if (!Double.isFinite(roll) || roll < 0.0 || roll >= 1.0) {
            return ArrowPayload.NONE;
        }
        if (difficultyId < NORMAL_DIFFICULTY_ID) {
            return ArrowPayload.NONE;
        }
        if (difficultyId < HARD_DIFFICULTY_ID) {
            return roll < 0.10
                    ? new ArrowPayload(ArrowEffect.SLOWNESS, effectDurationTicks(durationRoll), 0)
                    : ArrowPayload.NONE;
        }

        if (roll < 0.68) {
            return ArrowPayload.NONE;
        }
        if (roll < 0.76) {
            return new ArrowPayload(ArrowEffect.SLOWNESS, effectDurationTicks(durationRoll), 1);
        }
        if (roll < 0.84) {
            return new ArrowPayload(ArrowEffect.POISON, effectDurationTicks(durationRoll), 0);
        }
        if (roll < 0.92) {
            return new ArrowPayload(ArrowEffect.NAUSEA, effectDurationTicks(durationRoll), 0);
        }
        return new ArrowPayload(ArrowEffect.BLINDNESS, effectDurationTicks(durationRoll), 0);
    }

    /**
     * Returns a bounded, shifted-exponential duration: 1–30 seconds, with an
     * approximately eight-second mean. The cap prevents permanent crowd-control
     * while the distribution keeps repeated shots from feeling scripted.
     */
    public static int effectDurationTicks(double roll) {
        if (!Double.isFinite(roll)) {
            roll = 0.0;
        }
        roll = clamp(roll, 0.0, Math.nextDown(1.0));
        final double minimum = 20.0;
        final double maximum = 600.0;
        final double scale = 140.0;
        double normalizer = 1.0 - Math.exp(-(maximum - minimum) / scale);
        double duration = minimum - scale * Math.log(1.0 - roll * normalizer);
        return (int) Math.max(minimum, Math.min(maximum, Math.round(duration)));
    }

    public static boolean canDash(
            int difficultyId,
            boolean onGround,
            boolean canSeeTarget,
            int cooldownTicks,
            double squaredDistance) {
        return difficultyId >= HARD_DIFFICULTY_ID
                && onGround
                && canSeeTarget
                && cooldownTicks <= 0
                && squaredDistance >= MIN_DASH_DISTANCE_SQUARED
                && squaredDistance <= HARD_DASH_DISTANCE_SQUARED;
    }

    public static HorizontalDirection retreatDirection(
            double skeletonX,
            double skeletonZ,
            double targetX,
            double targetZ) {
        double x = skeletonX - targetX;
        double z = skeletonZ - targetZ;
        double length = Math.hypot(x, z);
        if (length < 1.0E-6) {
            return HorizontalDirection.ZERO;
        }
        return new HorizontalDirection(x / length, z / length);
    }

    private static int clampDifficulty(int difficultyId) {
        return Math.max(0, Math.min(HARD_DIFFICULTY_ID, difficultyId));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum ArrowEffect {
        NONE,
        SLOWNESS,
        POISON,
        NAUSEA,
        BLINDNESS
    }

    public record AimVector(
            double x,
            double y,
            double z,
            float speed,
            float divergence) {
    }

    public record ArrowPayload(ArrowEffect effect, int durationTicks, int amplifier) {
        public static final ArrowPayload NONE = new ArrowPayload(ArrowEffect.NONE, 0, 0);

        public ArrowPayload {
            if (effect == null) {
                throw new IllegalArgumentException("effect cannot be null");
            }
            if (durationTicks < 0 || amplifier < 0) {
                throw new IllegalArgumentException("duration and amplifier cannot be negative");
            }
        }

        public boolean appliesEffect() {
            return effect != ArrowEffect.NONE;
        }
    }

    public record HorizontalDirection(double x, double z) {
        public static final HorizontalDirection ZERO = new HorizontalDirection(0.0, 0.0);
    }
}
