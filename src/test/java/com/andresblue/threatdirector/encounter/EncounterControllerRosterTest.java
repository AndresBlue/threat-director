package com.andresblue.threatdirector.encounter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterControllerRosterTest {
    @Test
    void partiallyUnresolvedRosterRetainsOwnershipWithoutStartingFullUnloadGrace() {
        EncounterController.RosterAvailability availability =
                EncounterController.rosterAvailability(5, 3, Long.MIN_VALUE, 1_000L);

        assertTrue(availability.hasUnresolvedMembers());
        assertFalse(availability.fullyUnloaded());
        assertFalse(availability.withinGrace());
        assertEquals(Long.MIN_VALUE, availability.fullyUnloadedSinceTick());
    }

    @Test
    void fullyUnloadedRosterGetsBoundedGraceAndPreservesOriginalStartTick() {
        EncounterController.RosterAvailability first =
                EncounterController.rosterAvailability(4, 0, Long.MIN_VALUE, 1_000L);
        EncounterController.RosterAvailability beforeExpiry =
                EncounterController.rosterAvailability(
                        4, 0, first.fullyUnloadedSinceTick(),
                        1_000L + EncounterController.FULLY_UNLOADED_ROSTER_GRACE_TICKS - 1L);
        EncounterController.RosterAvailability expired =
                EncounterController.rosterAvailability(
                        4, 0, first.fullyUnloadedSinceTick(),
                        1_000L + EncounterController.FULLY_UNLOADED_ROSTER_GRACE_TICKS);

        assertTrue(first.hasUnresolvedMembers());
        assertTrue(first.fullyUnloaded());
        assertTrue(first.withinGrace());
        assertEquals(1_000L, first.fullyUnloadedSinceTick());
        assertTrue(beforeExpiry.withinGrace());
        assertEquals(1_000L, beforeExpiry.fullyUnloadedSinceTick());
        assertFalse(expired.withinGrace());
    }

    @Test
    void loadingAnyMemberResetsFullUnloadGrace() {
        EncounterController.RosterAvailability availability =
                EncounterController.rosterAvailability(4, 1, 1_000L, 1_050L);

        assertTrue(availability.hasUnresolvedMembers());
        assertFalse(availability.fullyUnloaded());
        assertEquals(Long.MIN_VALUE, availability.fullyUnloadedSinceTick());
    }
}
