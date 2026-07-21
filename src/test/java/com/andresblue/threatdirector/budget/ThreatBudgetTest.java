package com.andresblue.threatdirector.budget;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatBudgetTest {
    @Test
    void piecewiseBudgetHasStableReferencePoints() {
        assertBudget(35, 3_500);
        assertBudget(47.5, 4_500);
        assertBudget(60, 5_500);
        assertBudget(72.5, 7_250);
        assertBudget(85, 9_000);
        assertBudget(100, 12_000);
    }

    @Test
    void integerLedgerNeverOverspends() {
        ThreatBudget budget = ThreatBudget.forIntensity(60, 10, 1, 0);
        assertTrue(budget.reservePattern("pincer", 500));
        assertTrue(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1_800));
        assertTrue(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000002"), 1_800));
        assertFalse(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000003"), 1_500));
        assertEquals(1_400, budget.remaining());
    }

    @Test
    void ambientMobsConsumePartialCost() {
        assertEquals(1_000, AmbientThreat.charge(1_000, 40, true));
        assertEquals(750, AmbientThreat.charge(1_000, 10, false));
        assertEquals(250, AmbientThreat.charge(1_000, 40, false));
    }

    private static void assertBudget(double intensity, int expected) {
        assertEquals(expected, ThreatBudget.forIntensity(intensity, 10, 1, 0).total());
    }
}
