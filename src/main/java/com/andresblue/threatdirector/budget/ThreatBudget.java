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
        int total = (int) Math.round(base * clamp(performanceFactor, 0.0, 1.0));
        return new ThreatBudget(total, Math.min(Math.max(0, configuredMobCap), intensityCap), ambientCharge);
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
}
