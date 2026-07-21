package com.andresblue.threatdirector.power;

import java.util.List;

public final class VanillaArmorEvaluator implements PowerEvaluator {
    private static final double REPRESENTATIVE_HIT = 8.0;

    @Override
    public String id() {
        return "vanilla:armor";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        double armor = finiteNonNegative(snapshot.armor());
        double toughness = finiteNonNegative(snapshot.armorToughness());
        double denominator = 2.0 + toughness / 4.0;
        double effectiveArmor = Math.min(20.0, Math.max(armor / 5.0, armor - REPRESENTATIVE_HIT / denominator));
        double reduction = effectiveArmor / 25.0;
        int points = (int) Math.round(25_000.0 * clamp01(reduction / 0.80));
        return List.of(new PowerContribution(
                PowerAxis.DEFENSE, "armor.physical", points, 1.0,
                "Vanilla armor/toughness reduction against an 8 point hit"
        ));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
