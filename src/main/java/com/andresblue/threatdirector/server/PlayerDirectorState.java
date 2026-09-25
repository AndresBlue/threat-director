package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.power.AdventureIntensityBreakdown;
import com.andresblue.threatdirector.power.AdventureIntensityCalculator;
import com.andresblue.threatdirector.power.IntensityBand;
import com.andresblue.threatdirector.power.PowerReport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalDouble;
import java.util.UUID;

public final class PlayerDirectorState {
    private final UUID playerId;
    private final AdventureIntensityCalculator.Hysteresis hysteresis =
            new AdventureIntensityCalculator.Hysteresis(IntensityBand.LOW);
    private final Deque<DamageSample> damageSamples = new ArrayDeque<>();

    private PowerReport powerReport;
    private AdventureIntensityBreakdown breakdown;
    private double adaptiveIntensity;
    private double intensityOverride = Double.NaN;
    private double previousEffectiveHealth = -1.0;
    private long lastSampleTick = -1L;
    private long lastCombatTick = Long.MIN_VALUE;
    private long lastDangerousEncounterTick = Long.MIN_VALUE;
    private long lastDeathTick = Long.MIN_VALUE;
    private long cooldownUntilTick;

    public PlayerDirectorState(UUID playerId) {
        this.playerId = playerId;
    }

    public void observeHealth(long tick, double effectiveHealth) {
        if (previousEffectiveHealth >= 0.0 && effectiveHealth < previousEffectiveHealth) {
            double damage = previousEffectiveHealth - effectiveHealth;
            damageSamples.addLast(new DamageSample(tick, damage));
            lastCombatTick = tick;
        }
        previousEffectiveHealth = effectiveHealth;
        prune(tick);
    }

    public void recordKill(long tick) {
        lastCombatTick = tick;
    }

    public void recordDeath(long tick) {
        lastDeathTick = tick;
        lastCombatTick = tick;
        previousEffectiveHealth = -1.0;
    }

    public void markEncounterEnded(long tick, long cooldownTicks) {
        lastDangerousEncounterTick = tick;
        cooldownUntilTick = Math.max(cooldownUntilTick, tick + cooldownTicks);
    }

    public void updateEvaluation(
            PowerReport powerReport,
            AdventureIntensityBreakdown breakdown,
            double newIntensity,
            long sampleTick,
            int elapsedTicks,
            double hysteresisPoints) {
        this.powerReport = powerReport;
        this.breakdown = breakdown;
        this.adaptiveIntensity = newIntensity;
        this.lastSampleTick = sampleTick;
        hysteresis.update(newIntensity, breakdown.distress(), elapsedTicks, hysteresisPoints);
    }

    public double damageWithin(long now, long windowTicks) {
        prune(now);
        return damageSamples.stream()
                .filter(sample -> now - sample.tick <= windowTicks)
                .mapToDouble(DamageSample::amount)
                .sum();
    }

    private void prune(long now) {
        while (!damageSamples.isEmpty() && now - damageSamples.peekFirst().tick > 1_200L) {
            damageSamples.removeFirst();
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public PowerReport powerReport() {
        return powerReport;
    }

    public AdventureIntensityBreakdown breakdown() {
        return breakdown;
    }

    public double intensity() {
        return hasIntensityOverride() ? intensityOverride : adaptiveIntensity;
    }

    public double adaptiveIntensity() {
        return adaptiveIntensity;
    }

    public OptionalDouble intensityOverride() {
        return hasIntensityOverride()
                ? OptionalDouble.of(intensityOverride)
                : OptionalDouble.empty();
    }

    public boolean hasIntensityOverride() {
        return Double.isFinite(intensityOverride);
    }

    public void setIntensityOverride(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException("Threat override must be between 0 and 100");
        }
        intensityOverride = value;
    }

    public boolean clearIntensityOverride() {
        boolean cleared = hasIntensityOverride();
        intensityOverride = Double.NaN;
        return cleared;
    }

    public IntensityBand band() {
        return hasIntensityOverride()
                ? IntensityBand.fromIntensity(intensityOverride)
                : hysteresis.band();
    }

    public long lastSampleTick() {
        return lastSampleTick;
    }

    public long lastCombatTick() {
        return lastCombatTick;
    }

    public long lastDangerousEncounterTick() {
        return lastDangerousEncounterTick;
    }

    public long lastDeathTick() {
        return lastDeathTick;
    }

    public long cooldownUntilTick() {
        return cooldownUntilTick;
    }

    public boolean onCooldown(long tick) {
        return tick < cooldownUntilTick;
    }

    /** Returns whether the player is still inside the configured post-death safety window. */
    public boolean recentlyDied(long tick, long cooldownTicks) {
        if (lastDeathTick == Long.MIN_VALUE || cooldownTicks <= 0L) {
            return false;
        }
        return Math.max(0L, tick - lastDeathTick) < cooldownTicks;
    }

    private record DamageSample(long tick, double amount) {
    }
}
