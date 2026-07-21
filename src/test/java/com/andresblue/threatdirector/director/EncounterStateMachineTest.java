package com.andresblue.threatdirector.director;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterStateMachineTest {
    private final EncounterStateMachine machine = new EncounterStateMachine(EncounterDurations.vanillaPlus());

    @Test
    void positioningQuorumStartsEngagement() {
        TransitionResult result = machine.step(new EncounterRuntime(EncounterState.POSITIONING, 0),
                snapshot(100, 4, 3, 0, .2, .8, false));
        assertEquals(EncounterState.ENGAGING, result.runtime().state());
        assertEquals(TransitionCause.POSITIONS_READY, result.transition().orElseThrow().cause());
    }

    @Test
    void safetyGuardOutranksClimax() {
        TransitionResult result = machine.step(new EncounterRuntime(EncounterState.ENGAGING, 0),
                snapshot(500, 5, 5, 0, .8, .9, true));
        assertEquals(EncounterState.RELIEF, result.runtime().state());
        assertEquals(TransitionCause.PLAYER_DISTRESSED, result.transition().orElseThrow().cause());
    }

    @Test
    void illegalTransitionIsRejectedWithoutChangingState() {
        EncounterRuntime dormant = new EncounterRuntime(EncounterState.DORMANT, 0);
        TransitionResult result = machine.requestTransition(dormant, EncounterState.ENGAGING,
                TransitionCause.ADMIN_FORCED, snapshot(0, 0, 0, 0, 0, 0, false));
        assertEquals(dormant, result.runtime());
        assertTrue(result.rejection().isPresent());
    }

    private static EncounterSnapshot snapshot(long tick, int living, int positioned, long contactAge,
                                                double distress, double dominance, boolean climax) {
        return new EncounterSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000011"),
                tick, 80, 4_000, 5_000, living, positioned, contactAge,
                false, true, true, 0, true, false, distress, dominance,
                false, false, false, climax);
    }
}
