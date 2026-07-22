package com.andresblue.threatdirector.api.v1;

import java.util.UUID;

public record ThreatSnapshot(UUID playerId, double threat, String intensityBand, long sampledAtTick) {
    public ThreatSnapshot {
        if (playerId == null || intensityBand == null || !Double.isFinite(threat)) {
            throw new IllegalArgumentException("Invalid threat snapshot");
        }
    }
}
