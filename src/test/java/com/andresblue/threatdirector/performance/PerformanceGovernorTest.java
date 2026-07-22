package com.andresblue.threatdirector.performance;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PerformanceGovernorTest {
    @Test
    void threeBadSamplesRaiseOneLevelAndThirtyGoodSamplesRecoverOne() {
        PerformanceGovernor governor = new PerformanceGovernor();
        for (int index = 0; index < 2; index++) governor.update(sample(200, 40, 0));
        assertEquals(0, governor.level());
        governor.update(sample(220, 40, 0));
        assertEquals(1, governor.level());
        for (int index = 0; index < 30; index++) governor.update(sample(240 + index * 20L, 20, 0));
        assertEquals(0, governor.level());
    }

    @Test
    void emergencyRequiresTwoSamplesAndEntersVanillaFallback() {
        PerformanceGovernor governor = new PerformanceGovernor();
        governor.update(sample(200, 101, 0));
        assertEquals(0, governor.level());
        governor.update(sample(220, 101, 0));
        assertEquals(6, governor.level());
        assertEquals(0, governor.limits().candidateLimit());
        assertFalse(governor.limits().allowNewEncounters());
    }

    @Test
    void serverPolicyControlsThresholdsAndHysteresisDurations() {
        ThreatDirectorConfig.PerformanceSection policy = new ThreatDirectorConfig.PerformanceSection(
                2_000, 4, 40, 60, 10, 20, 30, 40, 50, 60);
        PerformanceGovernor governor = new PerformanceGovernor(policy);

        governor.update(sample(200, 15, 0));
        assertEquals(0, governor.level());
        governor.update(sample(220, 15, 0));
        assertEquals(1, governor.level());

        governor.update(sample(240, 60, 0));
        assertEquals(1, governor.level());
        governor.update(sample(260, 60, 0));
        assertEquals(6, governor.level());
    }

    @Test
    void resetClearsDegradationAndIgnoresOrdinaryPostResetLoad() {
        PerformanceGovernor governor = new PerformanceGovernor(5);

        governor.reset(1_000L);
        assertEquals(0, governor.level());
        governor.update(sample(1_020L, 80, 0));
        assertEquals(0, governor.level());
        governor.update(sample(1_080L, 80, 0));
        assertEquals(0, governor.level());
    }

    @Test
    void criticalLoadIsNotMaskedByResetWarmup() {
        PerformanceGovernor governor = new PerformanceGovernor(5);

        governor.reset(1_000L);
        governor.update(sample(1_020L, 200, 0));
        assertEquals(0, governor.level());
        governor.update(sample(1_040L, 200, 0));
        assertEquals(6, governor.level());
    }

    private static LoadSample sample(double mspt, double capacity) {
        return sample(200L, mspt, capacity);
    }

    private static LoadSample sample(long tick, double mspt, double capacity) {
        return new LoadSample(tick, mspt, mspt, capacity, capacity, capacity);
    }
}
