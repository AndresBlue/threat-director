package com.andresblue.threatdirector.power;

import java.util.List;

public final class VanillaConsumableEvaluator implements PowerEvaluator {
    @Override
    public String id() {
        return "vanilla:consumables";
    }

    @Override
    public int priority() {
        return 130;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        int shield = snapshot.shieldUsable() ? 4_000 : 0;
        int healing = (int) Math.round(5_000.0 * norm(snapshot.portableHealing(), 40.0));
        int regeneration = (int) Math.round(3_000.0 * norm(snapshot.regenerationSeconds(), 20.0));
        int absorption = (int) Math.round(2_000.0 * norm(snapshot.absorptionReserve(), 20.0));
        int totem = snapshot.totemUsable() ? 4_000 : 0;
        int food = (int) Math.round(1_000.0 * clamp01(snapshot.foodAvailability()));
        return List.of(
                new PowerContribution(PowerAxis.DEFENSE, "defense.shield", shield, 1.0, "Usable shield"),
                new PowerContribution(PowerAxis.SUSTAIN, "consumable.healing", healing, 1.0, "Portable healing"),
                new PowerContribution(PowerAxis.SUSTAIN, "effect.regeneration", regeneration, 1.0, "Regeneration reserve"),
                new PowerContribution(PowerAxis.SUSTAIN, "effect.absorption", absorption, 1.0, "Absorption reserve"),
                new PowerContribution(PowerAxis.SUSTAIN, "consumable.totem", totem, 1.0, "Usable totem"),
                new PowerContribution(PowerAxis.SUSTAIN, "consumable.food", food, 1.0, "Food availability")
        );
    }

    private static double norm(double value, double denominator) {
        return clamp01(Double.isFinite(value) ? value / denominator : 0.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
