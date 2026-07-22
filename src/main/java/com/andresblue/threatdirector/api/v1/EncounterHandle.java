package com.andresblue.threatdirector.api.v1;

import java.util.UUID;

public record EncounterHandle(UUID encounterId, UUID sourceId) {
    public EncounterHandle {
        if (encounterId == null || sourceId == null) {
            throw new IllegalArgumentException("Encounter and source IDs are required");
        }
    }
}
