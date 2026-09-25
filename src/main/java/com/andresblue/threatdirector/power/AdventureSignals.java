package com.andresblue.threatdirector.power;

import java.util.List;

public record AdventureSignals(
        List<Double> individualPowerScores,
        double night,
        double darkness,
        double dimensionThreat,
        double biomeThreat,
        double depth,
        double lowVisibility,
        double outsideSafeArea,
        long ticksSinceDangerousEncounter,
        long ticksSinceCombat,
        double recentIntensityEma,
        double maxHealth,
        double currentHealth,
        double absorption,
        double damageTakenLast200Ticks,
        boolean hasRecentDeath,
        long ticksSinceDeath,
        double entityLoad,
        double planningLoad,
        double playTimeProgress,
        double experienceProgress
) {
    public AdventureSignals {
        individualPowerScores = List.copyOf(individualPowerScores);
    }
}
