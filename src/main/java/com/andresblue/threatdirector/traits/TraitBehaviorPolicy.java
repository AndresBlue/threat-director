package com.andresblue.threatdirector.traits;

import com.andresblue.threatdirector.config.ConfigAccess;
import java.util.Objects;

/** Pure tuning and state gates for runtime trait behavior. */
public final class TraitBehaviorPolicy {
    private static final BehaviorProfile EASY = new BehaviorProfile(
            0.18D, 0.32D, 1.00D,
            0.18D, 0.04D, 0.03D,
            100, 0.5F,
            100, 8.0D, 2,
            0.04D, 1.0D, 0.05D,
            240
    );
    private static final BehaviorProfile NORMAL = new BehaviorProfile(
            0.25D, 0.42D, 1.08D,
            0.28D, 0.10D, 0.06D,
            80, 0.75F,
            80, 10.0D, 4,
            0.07D, 2.0D, 0.10D,
            200
    );
    private static final BehaviorProfile HARD = new BehaviorProfile(
            0.32D, 0.52D, 1.16D,
            0.35D, 0.18D, 0.10D,
            60, 1.0F,
            60, 12.0D, 6,
            0.11D, 3.0D, 0.18D,
            160
    );

    private TraitBehaviorPolicy() {
    }

    public static BehaviorProfile profile(TraitDifficulty difficulty) {
        if (ConfigAccess.current().moddedEnabled()
                && ConfigAccess.current().modded().traits().enabled()
                && difficulty != TraitDifficulty.EASY) {
            var configured = difficulty == TraitDifficulty.HARD
                    ? ConfigAccess.current().modded().traits().hard()
                    : ConfigAccess.current().modded().traits().normal();
            return new BehaviorProfile(
                    configured.cowardTriggerHealthFraction(),
                    configured.cowardExitHealthFraction(),
                    configured.cowardFleeSpeed(),
                    configured.berserkerTriggerHealthFraction(),
                    configured.berserkerDamageMultiplier(),
                    configured.berserkerSpeedMultiplier(),
                    configured.regenerationIntervalTicks(),
                    (float) configured.regenerationAmount(),
                    configured.leaderIntervalTicks(),
                    configured.leaderRadius(),
                    configured.leaderMaximumAllies(),
                    configured.swiftSpeedMultiplier(),
                    configured.resilientArmorBonus(),
                    configured.resilientKnockbackResistance(),
                    configured.particleIntervalTicks());
        }
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> EASY;
            case NORMAL -> NORMAL;
            case HARD -> HARD;
        };
    }

    public static boolean cowardShouldStart(double healthFraction, boolean hasLivingTarget,
                                            boolean directorControlled,
                                            BehaviorProfile profile) {
        return !directorControlled && hasLivingTarget
                && Double.isFinite(healthFraction)
                && healthFraction <= profile.cowardTriggerHealthFraction();
    }

    public static boolean cowardShouldContinue(double healthFraction, boolean hasLivingTarget,
                                               boolean directorControlled,
                                               BehaviorProfile profile) {
        return !directorControlled && hasLivingTarget
                && Double.isFinite(healthFraction)
                && healthFraction < profile.cowardExitHealthFraction();
    }

    public static boolean berserkerActive(double healthFraction, BehaviorProfile profile) {
        return Double.isFinite(healthFraction)
                && healthFraction <= profile.berserkerTriggerHealthFraction();
    }

    public record BehaviorProfile(
            double cowardTriggerHealthFraction,
            double cowardExitHealthFraction,
            double cowardFleeSpeed,
            double berserkerTriggerHealthFraction,
            double berserkerDamageMultiplier,
            double berserkerSpeedMultiplier,
            int regenerationIntervalTicks,
            float regenerationAmount,
            int leaderIntervalTicks,
            double leaderRadius,
            int leaderMaximumAllies,
            double swiftSpeedMultiplier,
            double resilientArmorBonus,
            double resilientKnockbackResistance,
            int particleIntervalTicks
    ) {
        public BehaviorProfile {
            if (cowardTriggerHealthFraction < 0.0D
                    || cowardExitHealthFraction < cowardTriggerHealthFraction
                    || cowardExitHealthFraction > 1.0D
                    || cowardFleeSpeed <= 0.0D
                    || berserkerTriggerHealthFraction < 0.0D
                    || berserkerTriggerHealthFraction > 1.0D
                    || berserkerDamageMultiplier < 0.0D
                    || berserkerSpeedMultiplier < 0.0D
                    || regenerationIntervalTicks < 1
                    || regenerationAmount < 0.0F
                    || leaderIntervalTicks < 1
                    || leaderRadius < 0.0D
                    || leaderMaximumAllies < 0
                    || swiftSpeedMultiplier < 0.0D
                    || resilientArmorBonus < 0.0D
                    || resilientKnockbackResistance < 0.0D
                    || resilientKnockbackResistance > 1.0D
                    || particleIntervalTicks < 1) {
                throw new IllegalArgumentException("invalid trait behavior profile");
            }
        }
    }
}
