package com.andresblue.threatdirector.budget;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Mutable encounter ledger. All values are integer milli-threat units. */
public final class ThreatBudget {
    private final int total;
    private final int mobCountCap;
    private final int ambientCharge;
    private final Map<UUID, Integer> mobs = new LinkedHashMap<>();
    private final Map<String, Integer> patternReservations = new LinkedHashMap<>();

    public ThreatBudget(int total, int mobCountCap, int ambientCharge) {
        if (total < 0 || mobCountCap < 0 || ambientCharge < 0) {
            throw new IllegalArgumentException("Threat budget values must be non-negative");
        }
        this.total = total;
        this.mobCountCap = mobCountCap;
        this.ambientCharge = ambientCharge;
    }

    public static ThreatBudget forIntensity(double intensity, int configuredMobCap, double performanceFactor,
                                            int ambientCharge) {
        return forIntensity(intensity, configuredMobCap, performanceFactor, ambientCharge, 1.0);
    }

    /**
     * Creates a budget scaled for a nearby Survival group.
     *
     * <p>The performance governor is applied before the group multiplier so degraded servers still
     * shed work. The configured cap remains an absolute ceiling even when the group multiplier is
     * greater than one.
     */
    public static ThreatBudget forIntensity(
            double intensity,
            int configuredMobCap,
            double performanceFactor,
            int ambientCharge,
            double groupMultiplier) {
        double bounded = clamp(intensity, 0.0, 100.0);
        int base;
        int intensityCap;
        if (bounded < 35.0) {
            base = 0;
            intensityCap = 0;
        } else if (bounded < 60.0) {
            base = 3_500 + (int) Math.round((bounded - 35.0) / 25.0 * 2_000.0);
            intensityCap = 5;
        } else if (bounded < 85.0) {
            base = 5_500 + (int) Math.round((bounded - 60.0) / 25.0 * 3_500.0);
            intensityCap = 8;
        } else {
            base = 9_000 + (int) Math.round((bounded - 85.0) / 15.0 * 3_000.0);
            intensityCap = 10;
        }
        double boundedGroupMultiplier = Math.max(1.0, finiteOr(groupMultiplier, 1.0));
        int total = roundedProduct(
                base,
                clamp(performanceFactor, 0.0, 1.0) * boundedGroupMultiplier);
        int scaledIntensityCap = roundedUpProduct(intensityCap, boundedGroupMultiplier);
        return new ThreatBudget(
                total,
                Math.min(Math.max(0, configuredMobCap), scaledIntensityCap),
                ambientCharge);
    }

    /** Returns a logarithmic, capped multiplier with diminishing gains per additional player. */
    public static double diminishingGroupMultiplier(
            int survivalPlayerCount,
            double additionalPlayerScale,
            double maxGroupMultiplier) {
        if (survivalPlayerCount <= 1) {
            return 1.0;
        }
        double scale = Math.max(0.0, finiteOr(additionalPlayerScale, 0.0));
        double maximum = Math.max(1.0, finiteOr(maxGroupMultiplier, 1.0));
        double logarithmicPlayers = Math.log(survivalPlayerCount) / Math.log(2.0);
        return Math.min(maximum, 1.0 + scale * logarithmicPlayers);
    }

    /** Scales the normal mob ceiling without ever exceeding the hard safety ceiling. */
    public static int scaledMobCap(int normalCap, int hardCap, double groupMultiplier) {
        int boundedNormalCap = Math.max(0, normalCap);
        int boundedHardCap = Math.max(0, hardCap);
        int scaled = roundedUpProduct(
                boundedNormalCap,
                Math.max(1.0, finiteOr(groupMultiplier, 1.0)));
        return Math.min(boundedHardCap, scaled);
    }

    public boolean allocateMob(UUID mobId, int cost) {
        Objects.requireNonNull(mobId, "mobId");
        if (cost < 0 || mobs.containsKey(mobId) || mobs.size() >= mobCountCap || cost > remaining()) {
            return false;
        }
        mobs.put(mobId, cost);
        return true;
    }

    public boolean reservePattern(String patternId, int cost) {
        Objects.requireNonNull(patternId, "patternId");
        if (cost < 0 || patternReservations.containsKey(patternId) || cost > remaining()) {
            return false;
        }
        patternReservations.put(patternId, cost);
        return true;
    }

    public void releaseMob(UUID mobId) {
        mobs.remove(mobId);
    }

    public void releasePattern(String patternId) {
        patternReservations.remove(patternId);
    }

    public int remaining() {
        return Math.max(0, total - ambientCharge - spentOnMobs() - reservedForPatterns());
    }

    public int spentOnMobs() {
        return mobs.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int reservedForPatterns() {
        return patternReservations.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int total() {
        return total;
    }

    public int ambientCharge() {
        return ambientCharge;
    }

    public int mobCount() {
        return mobs.size();
    }

    public int mobCountCap() {
        return mobCountCap;
    }

    public Map<UUID, Integer> mobs() {
        return Map.copyOf(mobs);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static int roundedProduct(int value, double multiplier) {
        double product = value * multiplier;
        return product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(product);
    }

    private static int roundedUpProduct(int value, double multiplier) {
        double product = value * multiplier;
        return product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(product);
    }
}
