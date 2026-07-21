package com.andresblue.threatdirector.performance;

public record GovernorUpdate(
        int previousLevel,
        int currentLevel,
        int desiredLevel,
        boolean changed,
        GovernorLimits limits
) {}
