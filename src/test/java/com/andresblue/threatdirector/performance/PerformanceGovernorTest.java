package com.andresblue.threatdirector.performance;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PerformanceGovernorTest {
    @Test
    void threeBadSamplesRaiseOneLevelAndThirtyGoodSamplesRecoverOne() {
        PerformanceGovernor governor = new PerformanceGovernor();
        for (int index = 0; index < 2; index++) governor.update(sample(40, 0));
        assertEquals(0, governor.level());
        governor.update(sample(40, 0));
        assertEquals(1, governor.level());
        for (int index = 0; index < 30; index++) governor.update(sample(20, 0));
        assertEquals(0, governor.level());
    }

    @Test
    void emergencyEntersVanillaFallbackImmediately() {
        PerformanceGovernor governor = new PerformanceGovernor();
        governor.update(sample(101, 0));
        assertEquals(6, governor.level());
        assertEquals(0, governor.limits().candidateLimit());
        assertFalse(governor.limits().allowNewEncounters());
    }

    @Test
    void serverPolicyControlsThresholdsAndHysteresisDurations() {
        ThreatDirectorConfig.PerformanceSection policy = new ThreatDirectorConfig.PerformanceSection(
                2_000, 4, 40, 60, 10, 20, 30, 40, 50, 60);
        PerformanceGovernor governor = new PerformanceGovernor(policy);

        governor.update(sample(15, 0));
        assertEquals(0, governor.level());
        governor.update(sample(15, 0));
        assertEquals(1, governor.level());

        governor.update(sample(60, 0));
        assertEquals(6, governor.level());
    }

    private static LoadSample sample(double mspt, double capacity) {
        return new LoadSample(0, mspt, mspt, capacity, capacity, capacity);
    }
}
