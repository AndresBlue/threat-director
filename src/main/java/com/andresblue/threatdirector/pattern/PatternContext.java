package com.andresblue.threatdirector.pattern;

import com.andresblue.threatdirector.director.EncounterState;

import java.util.Map;
import java.util.Optional;

public record PatternContext(
        EncounterState state,
        double intensity,
        int livingMobs,
        int flankerCandidates,
        double oppositePairDot,
        double openness,
        double roleReadiness,
        double lineOfAdvanceQuality,
        boolean interceptorAvailable,
        double escapeRouteConfidence,
        double movementPersistence,
        double interceptorPathQuality,
        double runsStraightMemory,
        int remainingThreat,
        long nowTick,
        Map<PatternId, Long> cooldownUntil,
        Optional<PatternId> previousPattern
) {
    public PatternContext {
        cooldownUntil = Map.copyOf(cooldownUntil);
        previousPattern = previousPattern == null ? Optional.empty() : previousPattern;
    }
}
