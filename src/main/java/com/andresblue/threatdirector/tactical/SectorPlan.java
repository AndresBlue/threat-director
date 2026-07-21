package com.andresblue.threatdirector.tactical;

import java.util.List;
import java.util.Optional;

public record SectorPlan(
        TacticalFrame frame,
        List<CandidatePosition> candidates,
        int operationsUsed,
        boolean vanillaNavigationFallback
) {
    public SectorPlan {
        candidates = List.copyOf(candidates);
    }

    public Optional<CandidatePosition> best() {
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }
}
