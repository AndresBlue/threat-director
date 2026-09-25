package com.andresblue.threatdirector.drowned;

/** Pure, bounded tuning for drowned anti-ocean behavior. */
public final class DrownedNightPolicy {
    private static final Profile VANILLA = new Profile(
            1.0, 0.0, 0.0, 0, 0.0, 0, 0.0, false);
    private static final Profile NORMAL_NIGHT = new Profile(
            1.55, 0.45, 0.78, 1, 0.62, 45, 56.0, true);
    private static final Profile HARD_NIGHT = new Profile(
            1.80, 0.60, 0.86, 2, 0.76, 34, 64.0, true);
    private static final Profile BLOOD_MOON = new Profile(
            2.05, 0.72, 0.92, 2, 0.90, 26, 72.0, true);

    private DrownedNightPolicy() {
    }

    /** Peaceful/Easy intentionally remain vanilla, including during a Blood Moon. */
    public static Profile profile(int difficultyId, boolean night, boolean bloodMoon) {
        if (difficultyId <= 1 || !night) {
            return VANILLA;
        }
        if (bloodMoon) {
            return BLOOD_MOON;
        }
        return difficultyId >= 3 ? HARD_NIGHT : NORMAL_NIGHT;
    }

    public static boolean acceptsRoll(double unitSample, double chance) {
        if (!Double.isFinite(unitSample) || unitSample < 0.0 || unitSample >= 1.0) {
            throw new IllegalArgumentException("unitSample must be in [0, 1)");
        }
        return unitSample < chance;
    }

    /**
     * Blends a dash into current velocity and clamps its magnitude so repeated
     * applications can never compound into unbounded motion.
     */
    public static Velocity dashVelocity(
            double currentX,
            double currentY,
            double currentZ,
            double offsetX,
            double offsetY,
            double offsetZ,
            double dashSpeed
    ) {
        double lengthSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (dashSpeed <= 0.0 || lengthSquared < 1.0E-8) {
            return new Velocity(currentX, currentY, currentZ);
        }
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        double x = currentX * 0.35 + offsetX * inverseLength * dashSpeed;
        double y = currentY * 0.35 + offsetY * inverseLength * dashSpeed;
        double z = currentZ * 0.35 + offsetZ * inverseLength * dashSpeed;
        double maximum = Math.max(0.1, dashSpeed * 1.15);
        double resultingLength = Math.sqrt(x * x + y * y + z * z);
        if (resultingLength > maximum) {
            double scale = maximum / resultingLength;
            x *= scale;
            y *= scale;
            z *= scale;
        }
        return new Velocity(x, y, z);
    }

    public record Profile(
            double swimSpeedMultiplier,
            double tridentChance,
            double riptideChance,
            int riptideLevel,
            double dashSpeed,
            int dashCooldownTicks,
            double targetRange,
            boolean breaksBoats
    ) {
        public boolean enhanced() {
            return targetRange > 0.0;
        }
    }

    public record Velocity(double x, double y, double z) {
    }
}
