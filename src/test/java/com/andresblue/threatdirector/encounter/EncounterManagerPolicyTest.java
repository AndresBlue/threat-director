package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.server.PlayerDirectorState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterManagerPolicyTest {
    @Test
    void ownershipClaimsAreAtomicAndReleasedOnReconcileOrFinish() {
        EncounterManager.MobOwnershipIndex ownership =
                new EncounterManager.MobOwnershipIndex();
        UUID firstEncounter = UUID.randomUUID();
        UUID secondEncounter = UUID.randomUUID();
        UUID retainedMob = UUID.randomUUID();
        UUID releasedMob = UUID.randomUUID();
        UUID unclaimedMob = UUID.randomUUID();

        assertTrue(ownership.claimAll(firstEncounter, List.of(retainedMob, releasedMob)));
        assertFalse(ownership.claimAll(secondEncounter, List.of(releasedMob, unclaimedMob)));
        assertTrue(ownership.ownerOf(unclaimedMob).isEmpty(), "a failed batch must claim nothing");

        ownership.retainForEncounter(firstEncounter, Set.of(retainedMob));
        assertTrue(ownership.ownerOf(releasedMob).isEmpty());
        assertEquals(firstEncounter, ownership.ownerOf(retainedMob).orElseThrow());

        assertTrue(ownership.claimAll(secondEncounter, List.of(releasedMob, unclaimedMob)));
        ownership.releaseEncounter(firstEncounter);
        assertEquals(2, ownership.size());
        ownership.releaseEncounter(secondEncounter);
        assertEquals(0, ownership.size());
    }

    @Test
    void healthBoundaryAndRecentDeathWindowAreInclusiveAndDeterministic() {
        assertTrue(EncounterManager.meetsMinimumHealth(10.0, 20.0, 0.50));
        assertFalse(EncounterManager.meetsMinimumHealth(9.99, 20.0, 0.50));
        assertFalse(EncounterManager.meetsMinimumHealth(10.0, 0.0, 0.50));

        PlayerDirectorState state = new PlayerDirectorState(UUID.randomUUID());
        assertFalse(state.recentlyDied(100L, 200L));
        state.recordDeath(100L);
        assertTrue(state.recentlyDied(100L, 200L));
        assertTrue(state.recentlyDied(299L, 200L));
        assertFalse(state.recentlyDied(300L, 200L));
        assertFalse(state.recentlyDied(100L, 0L));
    }
}
