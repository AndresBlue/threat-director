package com.andresblue.threatdirector.power;

import java.util.List;

public final class VanillaWeaponEvaluator implements PowerEvaluator {
    @Override
    public String id() {
        return "vanilla:weapon";
    }

    @Override
    public int priority() {
        return 110;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        double meleeDps = finiteNonNegative(snapshot.baseAttackDamage())
                * clamp(finiteNonNegative(snapshot.attackSpeed()), 0.1, 4.0);

        double ammunitionReliability;
        if (snapshot.projectileLoaded() || snapshot.infiniteProjectiles() || snapshot.projectileCount() >= 8) {
            ammunitionReliability = 1.0;
        } else if (snapshot.projectileCount() > 0) {
            ammunitionReliability = 0.6;
        } else {
            ammunitionReliability = 0.0;
        }
        double rangedDps = finiteNonNegative(snapshot.projectileDamage())
                / Math.max(0.25, finiteNonNegative(snapshot.projectileChargeSeconds()))
                * ammunitionReliability;
        double dpsNorm = clamp01((Math.max(meleeDps, rangedDps) - 2.0) / 16.0);
        double rangeNorm = clamp01((finiteNonNegative(snapshot.effectiveRange()) - 3.0) / 21.0);
        int points = (int) Math.round(26_000.0 * dpsNorm + 4_000.0 * rangeNorm);
        return List.of(new PowerContribution(
                PowerAxis.OFFENSE, "weapon.primary", points, 1.0,
                "Best sustainable melee/ranged option"
        ));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }
}
