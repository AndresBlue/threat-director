package com.andresblue.threatdirector.director;

import java.util.UUID;

public record EncounterTransition(
        EncounterState from,
        EncounterState to,
        TransitionCause cause,
        UUID targetPlayer,
        UUID encounterId,
        long serverTick,
        double adventureIntensity,
        int remainingThreat
) {}
