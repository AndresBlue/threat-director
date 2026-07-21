package com.andresblue.threatdirector.budget;

import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;

import java.util.HashMap;
import java.util.Map;

public final class ThreatCostRegistry {
    private final Map<String, Integer> configuredCosts;

    public ThreatCostRegistry(Map<String, Integer> configuredCosts) {
        Map<String, Integer> copy = new HashMap<>();
        configuredCosts.forEach((key, cost) -> copy.put(key, Math.max(0, cost)));
        this.configuredCosts = Map.copyOf(copy);
    }

    public static ThreatCostRegistry vanillaMvp() {
        return new ThreatCostRegistry(Map.of(
                "minecraft:zombie", 1_000,
                "minecraft:skeleton", 1_300,
                "minecraft:spider", 1_100,
                "minecraft:creeper", 1_800
        ));
    }

    public int cost(MobProfile profile) {
        Integer configured = configuredCosts.get(profile.typeId());
        return configured != null ? configured : conservativeEstimate(profile, false);
    }

    public int conservativeEstimate(MobProfile profile, boolean incompleteData) {
        double speedNorm = clamp01((finite(profile.movementSpeed()) - .20) / .20);
        double raw = .35
                + .025 * finite(profile.maxHealth())
                + .02 * finite(profile.armor())
                + .05 * finite(profile.attackDamage())
                + .20 * speedNorm
                + .35 * bool(profile.has(MobTrait.RANGED))
                + .45 * bool(profile.has(MobTrait.FLYING))
                + .80 * bool(profile.has(MobTrait.EXPLOSIVE))
                + .25 * bool(profile.has(MobTrait.SPELLCASTER))
                + .20 * bool(profile.has(MobTrait.SUPPORT));
        raw = clamp(raw, 1.0, 8.0) * (incompleteData ? 1.25 : 1.0);
        return ceilTo100((int) Math.ceil(raw * 1_000.0));
    }

    private static int ceilTo100(int value) {
        return ((value + 99) / 100) * 100;
    }

    private static double bool(boolean value) {
        return value ? 1.0 : 0.0;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
