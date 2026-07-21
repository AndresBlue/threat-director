package com.andresblue.threatdirector.power;

public record ScoredContribution(
        String evaluatorId,
        PowerContribution contribution,
        int effectiveMilliPoints
) {}
