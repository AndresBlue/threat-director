package com.andresblue.threatdirector.performance;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;

import java.util.Arrays;

public final class PerformanceGovernor {
    private static final long STARTUP_WARMUP_TICKS = 100L;
    private static final int EMERGENCY_SAMPLES_REQUIRED = 2;
    private static final double[] CAPACITY_ENTRY = {.75, .90, 1.00, 1.10, 1.25, 1.50};
    private static final double[] CAPACITY_RECOVERY = {.65, .80, .90, 1.00, 1.15, 1.35};

    private double[] msptEntry = {35, 40, 45, 50, 60, 100};
    private double[] msptRecovery = {32, 37, 42, 47, 55, 70};
    private int degradeSamples = 3;
    private int recoverSamples = 30;

    private int level;
    private int badSamples;
    private int goodSamples;
    private int emergencySamples;
    private long ignoreUntilTick = STARTUP_WARMUP_TICKS;

    public PerformanceGovernor() {
        this(0);
    }

    public PerformanceGovernor(int initialLevel) {
        this.level = Math.max(0, Math.min(6, initialLevel));
    }

    public PerformanceGovernor(ThreatDirectorConfig.PerformanceSection section) {
        this();
        configure(section);
    }

    public void configure(ThreatDirectorConfig.PerformanceSection section) {
        double[] configuredEntry = {
                section.level1TickTimeMs(),
                section.level2TickTimeMs(),
                section.level3TickTimeMs(),
                section.level4TickTimeMs(),
                section.level5TickTimeMs(),
                section.level6TickTimeMs()
        };
        int configuredDegrade = samplesFor(section.degradeAfterTicks());
        int configuredRecover = samplesFor(section.recoverAfterTicks());
        if (Arrays.equals(msptEntry, configuredEntry)
                && degradeSamples == configuredDegrade
                && recoverSamples == configuredRecover) {
            return;
        }
        msptEntry = configuredEntry;
        msptRecovery = Arrays.stream(configuredEntry).map(value -> value * 0.90).toArray();
        degradeSamples = configuredDegrade;
        recoverSamples = configuredRecover;
        badSamples = 0;
        goodSamples = 0;
    }

    public GovernorUpdate update(LoadSample sample) {
        int previous = level;
        double mspt = sample.effectiveMspt();
        double capacity = sample.capacityRatio();
        int desired = Math.max(levelFor(mspt, msptEntry), levelFor(capacity, CAPACITY_ENTRY));

        // Critical load is never masked by warm-up or an administrative reset. Requiring two
        // consecutive samples still filters a single world-load spike.
        if (mspt >= msptEntry[5] || capacity >= 1.75) {
            emergencySamples++;
            badSamples = 0;
            goodSamples = 0;
            if (emergencySamples >= EMERGENCY_SAMPLES_REQUIRED) {
                level = 6;
                emergencySamples = 0;
            }
            return new GovernorUpdate(previous, level, desired, previous != level, limitsFor(level));
        }

        // Integrated servers often report a few expensive but non-critical ticks while the world
        // and the LAN listener are starting. Those transients should not cause ordinary decay.
        if (sample.serverTick() < ignoreUntilTick) {
            badSamples = 0;
            goodSamples = 0;
            emergencySamples = 0;
            return new GovernorUpdate(previous, level, desired, false, limitsFor(level));
        }

        emergencySamples = 0;
        if (desired > level) {
            badSamples++;
            goodSamples = 0;
            if (badSamples >= degradeSamples) {
                level++;
                badSamples = 0;
            }
        } else if (level > 0 && safeToRecover(level, mspt, capacity)) {
            emergencySamples = 0;
            goodSamples++;
            badSamples = 0;
            if (goodSamples >= recoverSamples) {
                level--;
                goodSamples = 0;
            }
        } else {
            emergencySamples = 0;
            badSamples = 0;
            goodSamples = 0;
        }
        return new GovernorUpdate(previous, level, desired, previous != level, limitsFor(level));
    }

    /**
     * Administrative recovery hook. It clears accumulated load state and grants a short warm-up
     * window; subsequent sustained load can still degrade the director normally.
     */
    public GovernorUpdate reset(long currentServerTick) {
        int previous = level;
        level = 0;
        badSamples = 0;
        goodSamples = 0;
        emergencySamples = 0;
        ignoreUntilTick = Math.max(0L, currentServerTick) + STARTUP_WARMUP_TICKS;
        return new GovernorUpdate(previous, level, 0, previous != level, limitsFor(level));
    }

    public int level() {
        return level;
    }

    public GovernorLimits limits() {
        return limitsFor(level);
    }

    public static GovernorLimits limitsFor(int requestedLevel) {
        int value = Math.max(0, Math.min(6, requestedLevel));
        return switch (value) {
            case 0 -> new GovernorLimits(0, 20, 100, 32, 16, 1.00, 1.00, true, true, false);
            case 1 -> new GovernorLimits(1, 30, 140, 28, 16, 1.00, 1.00, true, true, false);
            case 2 -> new GovernorLimits(2, 40, 200, 20, 12, .90, .90, true, true, false);
            case 3 -> new GovernorLimits(3, 60, 300, 14, 8, .75, .80, true, true, false);
            case 4 -> new GovernorLimits(4, 80, 400, 8, 4, .50, .65, true, true, false);
            case 5 -> new GovernorLimits(5, 100, 600, 4, 4, .25, .40, false, false, false);
            case 6 -> new GovernorLimits(6, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0,
                    0.0, 0.0, false, false, true);
            default -> throw new IllegalStateException("Unexpected governor level");
        };
    }

    private boolean safeToRecover(int currentLevel, double mspt, double capacity) {
        int index = currentLevel - 1;
        return mspt < msptRecovery[index] && capacity < CAPACITY_RECOVERY[index];
    }

    private static int levelFor(double value, double[] thresholds) {
        int result = 0;
        for (double threshold : thresholds) {
            if (value >= threshold) result++;
            else break;
        }
        return result;
    }

    private static int samplesFor(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 20.0));
    }
}
