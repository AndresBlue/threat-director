package com.andresblue.threatdirector.power;

import java.util.List;
import java.util.Map;

public record PowerReport(
        int totalMilliPoints,
        Map<PowerAxis, Integer> axisTotals,
        List<ScoredContribution> acceptedContributions,
        List<String> warnings
) {
    public PowerReport {
        axisTotals = Map.copyOf(axisTotals);
        acceptedContributions = List.copyOf(acceptedContributions);
        warnings = List.copyOf(warnings);
    }

    public double score() {
        return totalMilliPoints / 1_000.0;
    }
}
