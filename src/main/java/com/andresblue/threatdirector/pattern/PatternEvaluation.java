package com.andresblue.threatdirector.pattern;

import java.util.List;

public record PatternEvaluation(
        PatternId id,
        boolean eligible,
        int score,
        int threatCost,
        long cooldownTicks,
        long maximumDurationTicks,
        List<String> reasons
) {
    public PatternEvaluation {
        reasons = List.copyOf(reasons);
    }
}
