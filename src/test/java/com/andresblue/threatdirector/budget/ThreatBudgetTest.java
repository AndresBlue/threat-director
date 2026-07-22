package com.andresblue.threatdirector.budget;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatBudgetTest {
    @Test
    void piecewiseBudgetHasStableReferencePoints() {
        assertBudget(35, 16_000);
        assertBudget(47.5, 32_000);
        assertBudget(60, 48_000);
        assertBudget(72.5, 72_000);
        assertBudget(85, 96_000);
        assertBudget(100, 160_000);
        assertEquals(96, ThreatBudget.forIntensity(100, 96, 1, 0).mobCountCap());
    }

    @Test
    void integerLedgerNeverOverspends() {
        ThreatBudget budget = ThreatBudget.forIntensity(35, 10, .28125, 0);
        assertTrue(budget.reservePattern("pincer", 500));
        assertTrue(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1_800));
        assertTrue(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000002"), 1_800));
        assertFalse(budget.allocateMob(UUID.fromString("00000000-0000-0000-0000-000000000003"), 1_500));
        assertEquals(400, budget.remaining());
    }

    @Test
    void ambientMobsConsumePartialCost() {
        assertEquals(1_000, AmbientThreat.charge(1_000, 40, true));
        assertEquals(750, AmbientThreat.charge(1_000, 10, false));
        assertEquals(250, AmbientThreat.charge(1_000, 40, false));
    }

    @Test
    void nearbyPlayersScaleBudgetWithDiminishingReturnsAndHardCaps() {
        double solo = ThreatBudget.diminishingGroupMultiplier(1, 0.35, 1.75);
        double duo = ThreatBudget.diminishingGroupMultiplier(2, 0.35, 1.75);
        double trio = ThreatBudget.diminishingGroupMultiplier(3, 0.35, 1.75);
        double quartet = ThreatBudget.diminishingGroupMultiplier(4, 0.35, 1.75);

        assertEquals(1.0, solo);
        assertEquals(1.35, duo, 0.000_001);
        assertTrue(trio - duo < duo - solo);
        assertTrue(quartet - trio < trio - duo);
        assertEquals(1.75, ThreatBudget.diminishingGroupMultiplier(8, 0.35, 1.75));

        ThreatBudget groupBudget = ThreatBudget.forIntensity(60, 32, 1, 0, duo);
        assertEquals(64_800, groupBudget.total());
        assertEquals(32, groupBudget.mobCountCap());
        assertEquals(44, ThreatBudget.scaledMobCap(32, 96, duo));
    }

    @Test
    void reinforcementReserveIsLifetimeBudgetAndDefeatedMobsAreNotRefunded() {
        ThreatBudget budget = ThreatBudget.forIntensity(100, 96, 1, 0, 1, 1);
        UUID first = UUID.randomUUID();

        assertEquals(160_000, budget.baseTotal());
        assertEquals(160_000, budget.reinforcementReserve());
        assertEquals(320_000, budget.total());
        assertEquals(160_000, budget.remaining(), "reserve stays locked for initial population");
        assertTrue(budget.allocateMob(first, 1_000));
        int afterAllocation = budget.remaining();
        budget.retireMob(first);

        assertEquals(afterAllocation, budget.remaining());
        assertEquals(1_000, budget.retiredMobThreat());
        assertEquals(0, budget.spentOnMobs());
        budget.unlockReinforcementReserve();
        assertTrue(budget.reinforcementReserveUnlocked());
        assertEquals(319_000, budget.remaining());
        assertFalse(budget.reservePattern("reserve-only", 160_000));
        assertTrue(budget.allocateMob(UUID.randomUUID(), 160_000));
    }

    private static void assertBudget(double intensity, int expected) {
        assertEquals(expected, ThreatBudget.forIntensity(intensity, 10, 1, 0).total());
    }
}
