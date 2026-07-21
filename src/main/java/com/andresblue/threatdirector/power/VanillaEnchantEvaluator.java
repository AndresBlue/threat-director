package com.andresblue.threatdirector.power;

import java.util.List;

public final class VanillaEnchantEvaluator implements PowerEvaluator {
    @Override
    public String id() {
        return "vanilla:enchantments";
    }

    @Override
    public int priority() {
        return 120;
    }

    @Override
    public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
        int epf = Math.max(0, Math.min(20, snapshot.genericProtectionEpf()));
        double damageBonus = Double.isFinite(snapshot.expectedEnchantmentDamage())
                ? Math.max(0.0, snapshot.expectedEnchantmentDamage()) : 0.0;
        return List.of(
                new PowerContribution(PowerAxis.DEFENSE, "enchant.protection",
                        (int) Math.round(6_000.0 * epf / 20.0), 1.0, "Generic protection EPF"),
                new PowerContribution(PowerAxis.OFFENSE, "enchant.damage",
                        (int) Math.round(5_000.0 * Math.min(1.0, damageBonus / 5.0)), 1.0,
                        "Expected enchantment damage bonus")
        );
    }
}
