package com.andresblue.threatdirector.budget;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Mutable encounter ledger. All values are integer milli-threat units. */
public final class ThreatBudget {
    private final int total;
    private final int baseTotal;
    private final int reinforcementReserve;
    private final int mobCountCap;
    private final int ambientCharge;
    private final Map<UUID, Integer> mobs = new LinkedHashMap<>();
    private final Map<String, Integer> patternReservations = new LinkedHashMap<>();
    private int retiredMobThreat;
    private boolean reinforcementReserveUnlocked;

    public ThreatBudget(int total, int mobCountCap, int ambientCharge) {
        this(total, mobCountCap, ambientCharge, 0);
    }

    private ThreatBudget(
            int baseTotal, int mobCountCap, int ambientCharge, int reinforcementReserve) {
        if (baseTotal < 0 || mobCountCap < 0 || ambientCharge < 0
                || reinforcementReserve < 0) {
            throw new IllegalArgumentException("Threat budget values must be non-negative");
        }
        this.baseTotal = baseTotal;
        this.reinforcementReserve = reinforcementReserve;
        this.total = saturatedSum(baseTotal, reinforcementReserve);
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
        return forIntensity(
                intensity,
                configuredMobCap,
                performanceFactor,
                ambientCharge,
                groupMultiplier,
                0.0);
    }

    /** Creates a lifetime budget with a bounded reserve that only later waves can consume. */
    public static ThreatBudget forIntensity(
            double intensity,
            int configuredMobCap,
            double performanceFactor,
            int ambientCharge,
            double groupMultiplier,
            double reinforcementReserveFactor) {
        double bounded = clamp(intensity, 0.0, 100.0);
        int base;
        int intensityCap;
        if (bounded < 35.0) {
            base = 0;
            intensityCap = 0;
        } else if (bounded < 60.0) {
            base = 3_500 + (int) Math.round((bounded - 35.0) / 25.0 * 2_000.0);
            intensityCap = 6;
        } else if (bounded < 85.0) {
            base = 5_500 + (int) Math.round((bounded - 60.0) / 25.0 * 3_500.0);
            intensityCap = 9;
        } else {
            base = 9_000 + (int) Math.round((bounded - 85.0) / 15.0 * 3_000.0);
            intensityCap = 12;
        }
        double boundedGroupMultiplier = Math.max(1.0, finiteOr(groupMultiplier, 1.0));
        int baseTotal = roundedProduct(
                base,
                clamp(performanceFactor, 0.0, 1.0) * boundedGroupMultiplier);
        int reserve = roundedProduct(
                baseTotal, clamp(finiteOr(reinforcementReserveFactor, 0.0), 0.0, 1.0));
        int scaledIntensityCap = roundedUpProduct(intensityCap, boundedGroupMultiplier);
        return new ThreatBudget(
                baseTotal,
                Math.min(Math.max(0, configuredMobCap), scaledIntensityCap),
                ambientCharge,
                reserve);
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
        // Pattern reservations may use only the base ledger. The reinforcement reserve exists to
        // sustain later bodies, not to let a tactical pattern starve every future wave.
        if (cost < 0 || patternReservations.containsKey(patternId) || cost > remainingBase()) {
            return false;
        }
        patternReservations.put(patternId, cost);
        return true;
    }

    public void releaseMob(UUID mobId) {
        mobs.remove(mobId);
    }

    /** Retires a defeated/expended mob without making its lifetime threat available again. */
    public void retireMob(UUID mobId) {
        Integer retired = mobs.remove(mobId);
        if (retired != null) {
            retiredMobThreat = saturatedSum(retiredMobThreat, retired);
        }
    }

    public void releasePattern(String patternId) {
        patternReservations.remove(patternId);
    }

    public int remaining() {
        int availableTotal = reinforcementReserveUnlocked ? total : baseTotal;
        return (int) Math.max(0L, (long) availableTotal - spent());
    }

    private int remainingBase() {
        return (int) Math.max(0L, (long) baseTotal - spent());
    }

    private long spent() {
        return (long) ambientCharge
                + spentOnMobs()
                + retiredMobThreat
                + reservedForPatterns();
    }

    /** Makes the lifetime reserve available after the initial population has been committed. */
    public void unlockReinforcementReserve() {
        reinforcementReserveUnlocked = true;
    }

    public boolean reinforcementReserveUnlocked() {
        return reinforcementReserveUnlocked;
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

    public int baseTotal() {
        return baseTotal;
    }

    public int reinforcementReserve() {
        return reinforcementReserve;
    }

    public int retiredMobThreat() {
        return retiredMobThreat;
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

    private static int saturatedSum(int first, int second) {
        long sum = (long) first + second;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
