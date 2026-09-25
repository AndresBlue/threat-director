package com.andresblue.threatdirector.traits;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraitBehaviorPolicyTest {
    @Test
    void effectsScaleMonotonicallyWithDifficulty() {
        var easy = TraitBehaviorPolicy.profile(TraitDifficulty.EASY);
        var normal = TraitBehaviorPolicy.profile(TraitDifficulty.NORMAL);
        var hard = TraitBehaviorPolicy.profile(TraitDifficulty.HARD);

        assertTrue(easy.swiftSpeedMultiplier() < normal.swiftSpeedMultiplier());
        assertTrue(normal.swiftSpeedMultiplier() < hard.swiftSpeedMultiplier());
        assertTrue(easy.berserkerDamageMultiplier() < normal.berserkerDamageMultiplier());
        assertTrue(normal.berserkerDamageMultiplier() < hard.berserkerDamageMultiplier());
        assertTrue(easy.regenerationIntervalTicks() > normal.regenerationIntervalTicks());
        assertTrue(normal.regenerationIntervalTicks() > hard.regenerationIntervalTicks());
        assertTrue(easy.leaderMaximumAllies() < normal.leaderMaximumAllies());
        assertTrue(normal.leaderMaximumAllies() < hard.leaderMaximumAllies());
    }

    @Test
    void cowardBehaviorAlwaysYieldsToDirectorControl() {
        var hard = TraitBehaviorPolicy.profile(TraitDifficulty.HARD);

        assertTrue(TraitBehaviorPolicy.cowardShouldStart(0.20D, true, false, hard));
        assertFalse(TraitBehaviorPolicy.cowardShouldStart(0.20D, true, true, hard));
        assertFalse(TraitBehaviorPolicy.cowardShouldContinue(0.20D, true, true, hard));
        assertFalse(TraitBehaviorPolicy.cowardShouldStart(0.20D, false, false, hard));
    }

    @Test
    void cowardAndBerserkerUseHysteresisAndLowHealthGates() {
        var normal = TraitBehaviorPolicy.profile(TraitDifficulty.NORMAL);

        assertTrue(TraitBehaviorPolicy.cowardShouldStart(
                normal.cowardTriggerHealthFraction(), true, false, normal));
        assertTrue(TraitBehaviorPolicy.cowardShouldContinue(
                normal.cowardTriggerHealthFraction() + 0.05D, true, false, normal));
        assertFalse(TraitBehaviorPolicy.cowardShouldContinue(
                normal.cowardExitHealthFraction(), true, false, normal));
        assertTrue(TraitBehaviorPolicy.berserkerActive(
                normal.berserkerTriggerHealthFraction(), normal));
        assertFalse(TraitBehaviorPolicy.berserkerActive(
                normal.berserkerTriggerHealthFraction() + 0.01D, normal));
    }
}
