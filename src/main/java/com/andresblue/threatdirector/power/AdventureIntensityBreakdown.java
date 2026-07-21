package com.andresblue.threatdirector.power;

public record AdventureIntensityBreakdown(
        double groupPower,
        double environment,
        double readiness,
        double dominance,
        double distress,
        double load,
        double targetIntensity
) {}
