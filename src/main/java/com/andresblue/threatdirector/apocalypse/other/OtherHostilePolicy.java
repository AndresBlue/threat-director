package com.andresblue.threatdirector.apocalypse.other;

import java.util.Objects;

/** Pure balance policy for vanilla hostile families not handled by dedicated modules. */
public final class OtherHostilePolicy {
    private static final PressureProfile EASY_PRESSURE = new PressureProfile(0.0D, 0, 0, true);
    private static final PressureProfile NORMAL_PRESSURE = new PressureProfile(28.0D, 20, 60, true);
    private static final PressureProfile HARD_PRESSURE = new PressureProfile(48.0D, 10, 240, false);
    private static final AbilityRules DISABLED = new AbilityRules(
            0, 20, 0.0D, 0, 0, 0, 0.0D, 0.0D, 0.0D, 0.0D);

    private OtherHostilePolicy() {
    }

    public static PressureProfile pressure(Tier tier) {
        return switch (Objects.requireNonNull(tier, "tier")) {
            case EASY -> EASY_PRESSURE;
            case NORMAL -> NORMAL_PRESSURE;
            case HARD -> HARD_PRESSURE;
        };
    }

    public static AbilityRules ability(Tier tier, Family family) {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(family, "family");
        if (tier == Tier.EASY || family == Family.OTHER) {
            return DISABLED;
        }

        if (tier == Tier.NORMAL) {
            return switch (family) {
                case WITCH_ILLAGER -> new AbilityRules(180, 20, 8.0D, 1, 40, 0,
                        0.0D, 0.0D, 0.0D, 12.0D * 12.0D);
                case AERIAL_FIRE -> new AbilityRules(120, 20, 0.0D, 0, 0, 0,
                        0.16D, 0.40D, 6.0D * 6.0D, 18.0D * 18.0D);
                case GUARDIAN -> new AbilityRules(140, 20, 0.0D, 0, 40, 0,
                        0.0D, 0.0D, 0.0D, 12.0D * 12.0D);
                case DROWNED_HUSK -> new AbilityRules(120, 20, 0.0D, 0, 40, 0,
                        0.0D, 0.0D, 0.0D, 3.5D * 3.5D);
                case SWARM -> new AbilityRules(160, 20, 6.0D, 2, 0, 0,
                        0.0D, 0.0D, 0.0D, 0.0D);
                case BRUTE, OTHER -> DISABLED;
            };
        }

        return switch (family) {
            case WITCH_ILLAGER -> new AbilityRules(100, 20, 10.0D, 3, 80, 0,
                    0.0D, 0.0D, 0.0D, 14.0D * 14.0D);
            case AERIAL_FIRE -> new AbilityRules(70, 20, 0.0D, 0, 0, 0,
                    0.28D, 0.60D, 6.0D * 6.0D, 24.0D * 24.0D);
            case GUARDIAN -> new AbilityRules(100, 20, 0.0D, 0, 80, 1,
                    0.0D, 0.0D, 0.0D, 14.0D * 14.0D);
            case DROWNED_HUSK -> new AbilityRules(80, 20, 0.0D, 0, 80, 0,
                    0.0D, 0.0D, 0.0D, 3.5D * 3.5D);
            case SWARM -> new AbilityRules(100, 20, 10.0D, 4, 0, 0,
                    0.0D, 0.0D, 0.0D, 0.0D);
            case BRUTE -> new AbilityRules(100, 20, 0.0D, 0, 0, 0,
                    0.45D, 0.75D, 5.0D * 5.0D, 12.0D * 12.0D);
            case OTHER -> DISABLED;
        };
    }

    public static boolean canUseVisibleRangedAbility(
            AbilityRules rules,
            boolean targetPresent,
            boolean targetVisible,
            double distanceSquared
    ) {
        return rules.enabled()
                && targetPresent
                && targetVisible
                && distanceSquared >= rules.minimumDistanceSquared()
                && distanceSquared <= rules.maximumDistanceSquared();
    }

    public static boolean canUseClosePressure(
            AbilityRules rules,
            boolean targetPresent,
            boolean targetVisible,
            double distanceSquared
    ) {
        return rules.enabled()
                && targetPresent
                && targetVisible
                && distanceSquared <= rules.maximumDistanceSquared();
    }

    /** Adds a horizontal approach impulse while preserving a strict speed cap. */
    public static HorizontalVelocity boundedApproachVelocity(
            AbilityRules rules,
            double currentX,
            double currentZ,
            double targetOffsetX,
            double targetOffsetZ
    ) {
        double offsetLength = Math.hypot(targetOffsetX, targetOffsetZ);
        if (!rules.enabled() || rules.impulse() <= 0.0D || offsetLength < 1.0E-6D) {
            return new HorizontalVelocity(currentX, currentZ);
        }

        double nextX = currentX + targetOffsetX / offsetLength * rules.impulse();
        double nextZ = currentZ + targetOffsetZ / offsetLength * rules.impulse();
        double speed = Math.hypot(nextX, nextZ);
        if (speed > rules.maximumHorizontalSpeed()) {
            double scale = rules.maximumHorizontalSpeed() / speed;
            nextX *= scale;
            nextZ *= scale;
        }
        return new HorizontalVelocity(nextX, nextZ);
    }

    public enum Tier {
        EASY,
        NORMAL,
        HARD
    }

    public enum Family {
        WITCH_ILLAGER,
        AERIAL_FIRE,
        GUARDIAN,
        DROWNED_HUSK,
        SWARM,
        BRUTE,
        OTHER
    }

    public record PressureProfile(
            double minimumTargetRange,
            int targetScanIntervalTicks,
            int unseenMemoryTicks,
            boolean requiresVisibility
    ) {
        public PressureProfile {
            if (minimumTargetRange < 0.0D || targetScanIntervalTicks < 0 || unseenMemoryTicks < 0) {
                throw new IllegalArgumentException("pressure values cannot be negative");
            }
        }

        public boolean enabled() {
            return minimumTargetRange > 0.0D && targetScanIntervalTicks > 0;
        }
    }

    public record AbilityRules(
            int cooldownTicks,
            int retryTicks,
            double allyRadius,
            int maximumAllies,
            int statusDurationTicks,
            int statusAmplifier,
            double impulse,
            double maximumHorizontalSpeed,
            double minimumDistanceSquared,
            double maximumDistanceSquared
    ) {
        public AbilityRules {
            if (cooldownTicks < 0 || retryTicks < 1 || allyRadius < 0.0D || maximumAllies < 0
                    || statusDurationTicks < 0 || statusAmplifier < 0 || impulse < 0.0D
                    || maximumHorizontalSpeed < 0.0D || minimumDistanceSquared < 0.0D
                    || maximumDistanceSquared < 0.0D) {
                throw new IllegalArgumentException("ability values cannot be negative");
            }
        }

        public boolean enabled() {
            return cooldownTicks > 0;
        }
    }

    public record HorizontalVelocity(double x, double z) {
    }
}
