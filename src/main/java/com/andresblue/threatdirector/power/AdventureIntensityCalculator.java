package com.andresblue.threatdirector.power;

import java.util.List;

public final class AdventureIntensityCalculator {
    private AdventureIntensityCalculator() {}

    public static AdventureIntensityBreakdown calculate(AdventureSignals signals) {
        return calculate(signals, 10.0, 10.0, 30.0);
    }

    public static AdventureIntensityBreakdown calculate(
            AdventureSignals signals,
            double groupCap,
            double contextCap,
            double stressPenaltyCap) {
        double groupPower = groupPower(signals.individualPowerScores());
        double strongestPower = strongestPower(signals.individualPowerScores());
        double environment = .25 * unit(signals.night())
                + .20 * unit(signals.darkness())
                + .18 * unit(signals.dimensionThreat())
                + .12 * unit(signals.biomeThreat())
                + .10 * unit(signals.depth())
                + .05 * unit(signals.lowVisibility())
                + .10 * unit(signals.outsideSafeArea());
        double readiness = .55 * unit(signals.ticksSinceDangerousEncounter() / 12_000.0)
                + .25 * unit(signals.ticksSinceCombat() / 2_400.0)
                + .20 * (1.0 - unit(signals.recentIntensityEma() / 100.0));

        double maxHealth = Math.max(1.0, finiteNonNegative(signals.maxHealth()));
        double damageRatio = unit(signals.damageTakenLast1200Ticks() / maxHealth);
        double dominance = unit(signals.combatObservationConfidence())
                * (.65 * unit(signals.hostileKillsLast1200Ticks() / 6.0) + .35 * (1.0 - damageRatio));

        double effectiveHealth = finiteNonNegative(signals.currentHealth())
                + Math.min(finiteNonNegative(signals.absorption()), maxHealth * .5);
        double missingHealth = 1.0 - unit(effectiveHealth / maxHealth);
        double deathMemory = signals.hasRecentDeath()
                ? StrictMath.exp(-Math.max(0L, signals.ticksSinceDeath()) / 12_000.0) : 0.0;
        double distress = .50 * missingHealth
                + .30 * unit(signals.damageTakenLast200Ticks() / maxHealth)
                + .20 * deathMemory;
        double load = Math.max(unit(signals.entityLoad()), unit(signals.planningLoad()));

        double groupScale = finiteNonNegative(groupCap) / 10.0;
        double contextScale = finiteNonNegative(contextCap) / 10.0;
        double stressScale = finiteNonNegative(stressPenaltyCap) / 30.0;
        double groupUplift = Math.max(0.0, groupPower - strongestPower);
        double target = 100.0 * unit(.05 + .50 * strongestPower
                + .50 * groupUplift * groupScale
                + (.22 * environment + .13 * readiness) * contextScale
                + .10 * dominance - .30 * distress * stressScale - .15 * load);
        return new AdventureIntensityBreakdown(groupPower, environment, readiness, dominance, distress, load, target);
    }

    public static double groupPower(List<Double> individualScores) {
        if (individualScores == null || individualScores.isEmpty()) {
            return 0.0;
        }
        double maximum = 0.0;
        double sum = 0.0;
        int count = 0;
        for (Double score : individualScores) {
            if (score == null || !Double.isFinite(score)) {
                continue;
            }
            double normalized = unit(score / 100.0);
            maximum = Math.max(maximum, normalized);
            sum += normalized;
            count++;
        }
        if (count == 0) {
            return 0.0;
        }
        double others = Math.max(0.0, sum - maximum);
        return unit(maximum + (1.0 - maximum) * (1.0 - StrictMath.exp(-.60 * others)));
    }

    public static double smooth(double current, double target, long deltaTicks) {
        long boundedDelta = Math.max(0L, Math.min(200L, deltaTicks));
        double tau = target >= current ? 1_200.0 : 300.0;
        double alpha = 1.0 - StrictMath.exp(-boundedDelta / tau);
        return clamp(current + alpha * (target - current), 0.0, 100.0);
    }

    public static double smooth(
            double current,
            double target,
            long deltaTicks,
            int sampleIntervalTicks,
            double configuredAlpha) {
        double baseAlpha = clamp(configuredAlpha, 1.0e-6, 1.0);
        if (target < current) {
            baseAlpha = Math.min(1.0, baseAlpha * 4.0);
        }
        double intervals = Math.max(0L, deltaTicks) / (double) Math.max(1, sampleIntervalTicks);
        double effectiveAlpha = 1.0 - StrictMath.pow(1.0 - baseAlpha, intervals);
        return clamp(current + effectiveAlpha * (target - current), 0.0, 100.0);
    }

    private static double strongestPower(List<Double> individualScores) {
        if (individualScores == null) return 0.0;
        return individualScores.stream()
                .filter(java.util.Objects::nonNull)
                .filter(Double::isFinite)
                .mapToDouble(score -> unit(score / 100.0))
                .max().orElse(0.0);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double unit(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Hysteresis {
        private IntensityBand band;
        private int pendingUpTicks;
        private int pendingDownTicks;

        public Hysteresis(IntensityBand initialBand) {
            this.band = initialBand;
        }

        public IntensityBand update(double intensity, double distress, int elapsedTicks) {
            return update(intensity, distress, elapsedTicks, 10.0);
        }

        public IntensityBand update(
                double intensity,
                double distress,
                int elapsedTicks,
                double hysteresisPoints) {
            IntensityBand desiredUp = upwardBand(intensity);
            IntensityBand desiredDown = downwardBand(
                    intensity, band, Math.max(0.0, hysteresisPoints));
            if (desiredUp.ordinal() > band.ordinal()) {
                pendingDownTicks = 0;
                pendingUpTicks += Math.max(0, elapsedTicks);
                if (pendingUpTicks >= 200) {
                    band = IntensityBand.values()[band.ordinal() + 1];
                    pendingUpTicks = 0;
                }
            } else if (desiredDown.ordinal() < band.ordinal()) {
                pendingUpTicks = 0;
                pendingDownTicks += Math.max(0, elapsedTicks);
                if (distress >= .65 || pendingDownTicks >= 40) {
                    band = IntensityBand.values()[band.ordinal() - 1];
                    pendingDownTicks = 0;
                }
            } else {
                pendingUpTicks = 0;
                pendingDownTicks = 0;
            }
            return band;
        }

        public IntensityBand band() {
            return band;
        }

        private static IntensityBand upwardBand(double intensity) {
            return IntensityBand.fromIntensity(intensity);
        }

        private static IntensityBand downwardBand(
                double intensity, IntensityBand current, double hysteresisPoints) {
            return switch (current) {
                case EXTREME -> intensity < 85.0 - hysteresisPoints
                        ? IntensityBand.HIGH : IntensityBand.EXTREME;
                case HIGH -> intensity < 65.0 - hysteresisPoints
                        ? IntensityBand.TACTICAL : IntensityBand.HIGH;
                case TACTICAL -> intensity < 40.0 - hysteresisPoints
                        ? IntensityBand.LOW : IntensityBand.TACTICAL;
                case LOW -> IntensityBand.LOW;
            };
        }
    }
}
