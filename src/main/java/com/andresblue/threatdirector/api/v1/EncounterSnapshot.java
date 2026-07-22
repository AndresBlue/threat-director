package com.andresblue.threatdirector.api.v1;

public record EncounterSnapshot(
        EncounterHandle handle,
        String phase,
        int livingParticipants,
        int remainingThreatBudget,
        int initialThreatBudget,
        boolean active,
        String factionId,
        String operationType,
        String regionId) {}
