package com.andresblue.threatdirector.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatScalingTest {
    @Test
    void extraPressureIsDormantUntilActivationThreshold() {
        assertEquals(0.0, ThreatScaling.progression(34.9));
        assertEquals(0.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.SKELETON, 3, 34.9));
        assertEquals(0.0, ThreatScaling.zombieHordeChance(3, 0.0));
    }

    @Test
    void ordinaryHardIsReducedAndBloodMoonRestoresAdvertisedSightRanges() {
        assertEquals(80.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.SKELETON, 3, 100.0));
        assertEquals(72.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.ZOMBIE, 3, 100.0));
        assertEquals(128.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.SKELETON, 3, 100.0, true));
        assertEquals(96.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.ZOMBIE, 3, 100.0, true));
        assertEquals(64.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.SKELETON, 2, 100.0));
        assertEquals(40.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.GENERIC, 2, 100.0));
        assertEquals(48.0, ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.GENERIC, 3, 100.0));
    }

    @Test
    void hordeRollStaysWithinDifficultyBounds() {
        assertEquals(4, ThreatScaling.zombieHordeCount(3, 100.0, 0));
        assertEquals(7, ThreatScaling.zombieHordeCount(3, 100.0, 999));
        assertEquals(10, ThreatScaling.zombieHordeCount(3, 100.0, 999, true));
        assertTrue(ThreatScaling.zombieHordeChance(3, 100.0)
                > ThreatScaling.zombieHordeChance(2, 100.0));
    }
}
