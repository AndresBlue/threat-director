package com.andresblue.threatdirector.performance;

public final class PerformanceGovernor {
    private static final double[] MSPT_ENTRY = {35, 40, 45, 50, 60, 80};
    private static final double[] CAPACITY_ENTRY = {.75, .90, 1.00, 1.10, 1.25, 1.50};
    private static final double[] MSPT_RECOVERY = {32, 37, 42, 47, 55, 70};
    private static final double[] CAPACITY_RECOVERY = {.65, .80, .90, 1.00, 1.15, 1.35};

    private int level;
    private int badSamples;
    private int goodSamples;

    public PerformanceGovernor() {
        this(0);
    }

    public PerformanceGovernor(int initialLevel) {
        this.level = Math.max(0, Math.min(6, initialLevel));
    }

    public GovernorUpdate update(LoadSample sample) {
        int previous = level;
        double mspt = sample.effectiveMspt();
        double capacity = sample.capacityRatio();
        int desired = Math.max(levelFor(mspt, MSPT_ENTRY), levelFor(capacity, CAPACITY_ENTRY));

        if (mspt >= 100.0 || capacity >= 1.75) {
            level = 6;
            badSamples = 0;
            goodSamples = 0;
        } else if (desired > level) {
            badSamples++;
            goodSamples = 0;
            if (badSamples >= 3) {
                level++;
                badSamples = 0;
            }
        } else if (level > 0 && safeToRecover(level, mspt, capacity)) {
            goodSamples++;
            badSamples = 0;
            if (goodSamples >= 30) {
                level--;
                goodSamples = 0;
            }
        } else {
            badSamples = 0;
            goodSamples = 0;
        }
        return new GovernorUpdate(previous, level, desired, previous != level, limitsFor(level));
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
            case 0 -> new GovernorLimits(0, 20, 100, 24, 16, 1.00, 1.00, true, true, false);
            case 1 -> new GovernorLimits(1, 30, 140, 18, 16, 1.00, 1.00, true, true, false);
            case 2 -> new GovernorLimits(2, 40, 200, 12, 8, .90, .90, true, true, false);
            case 3 -> new GovernorLimits(3, 60, 300, 8, 8, .75, .80, true, true, false);
            case 4 -> new GovernorLimits(4, 80, 400, 6, 4, .50, .65, true, true, false);
            case 5 -> new GovernorLimits(5, 100, 600, 4, 4, .25, .40, false, false, false);
            case 6 -> new GovernorLimits(6, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0,
                    0.0, 0.0, false, false, true);
            default -> throw new IllegalStateException("Unexpected governor level");
        };
    }

    private static boolean safeToRecover(int currentLevel, double mspt, double capacity) {
        int index = currentLevel - 1;
        return mspt < MSPT_RECOVERY[index] && capacity < CAPACITY_RECOVERY[index];
    }

    private static int levelFor(double value, double[] thresholds) {
        int result = 0;
        for (double threshold : thresholds) {
            if (value >= threshold) result++;
            else break;
        }
        return result;
    }
}
