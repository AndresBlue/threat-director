package com.andresblue.threatdirector.director;

import java.util.Objects;
import java.util.UUID;

public record EncounterSnapshot(
        UUID encounterId,
        UUID targetPlayer,
        long nowTick,
        double intensity,
        int remainingThreat,
        int initialThreat,
        int livingMobs,
        int positionedMobs,
        long contactAgeTicks,
        boolean startRequested,
        boolean targetValid,
        boolean sameDimension,
        int performanceLevel,
        boolean navigationViable,
        boolean targetInProtectedZone,
        double distress,
        boolean pressureDisengaged,
        boolean retreatComplete,
        boolean cleanupComplete,
        boolean climaxEnabled
) {
    public EncounterSnapshot {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(targetPlayer, "targetPlayer");
    }

    public double remainingThreatRatio() {
        return initialThreat <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, remainingThreat / (double) initialThreat));
    }
}
