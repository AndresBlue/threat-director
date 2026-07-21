package com.andresblue.threatdirector.performance;

public record GovernorLimits(
        int level,
        int decisionIntervalTicks,
        int terrainRefreshTicks,
        int candidateLimit,
        int escapeDirectionCount,
        double activeControllerFactor,
        double threatBudgetFactor,
        boolean allowNewEncounters,
        boolean allowPatterns,
        boolean vanillaOnly
) {}
