package com.andresblue.threatdirector.power;

import java.util.List;

public final class AttributeEvaluator implements PowerEvaluator {
    @Override
    public String id() {
        return "vanilla:attributes";
    }

    @Override
    public int priority() {
        return 140;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        int health = (int) Math.round(5_000.0 * norm(snapshot.maxHealth(), 40.0));
        int speed = (int) Math.round(7_000.0 * clamp01((finite(snapshot.movementSpeed()) - 0.10) / 0.10));
        int reach = (int) Math.round(3_000.0 * clamp01((finite(snapshot.reach()) - 3.0) / 3.0));
        return List.of(
                new PowerContribution(PowerAxis.SUSTAIN, "attribute.max_health", health, 1.0, "Maximum health"),
                new PowerContribution(PowerAxis.MOBILITY, "attribute.speed", speed, 1.0, "Movement speed above baseline"),
                new PowerContribution(PowerAxis.MOBILITY, "attribute.reach", reach, 1.0, "Interaction/attack reach")
        );
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double norm(double value, double denominator) {
        return clamp01(finite(value) / denominator);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
