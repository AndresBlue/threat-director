package com.andresblue.threatdirector.budget;

public final class AmbientThreat {
    private AmbientThreat() {}

    public static int charge(int baseCost, double distance, boolean attackingTarget) {
        if (attackingTarget) return Math.max(0, baseCost);
        double multiplier;
        if (distance <= 16.0) multiplier = .75;
        else if (distance <= 32.0) multiplier = .50;
        else if (distance <= 48.0) multiplier = .25;
        else multiplier = 0.0;
        return (int) Math.round(Math.max(0, baseCost) * multiplier);
    }
}
