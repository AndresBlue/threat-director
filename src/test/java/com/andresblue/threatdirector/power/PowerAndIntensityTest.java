package com.andresblue.threatdirector.power;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PowerAndIntensityTest {
    @Test
    void vanillaReferenceLoadoutsHaveStableScores() {
        PowerEvaluationPipeline pipeline = PowerEvaluationPipeline.vanilla();

        assertEquals(5.75, pipeline.evaluate(snapshot(0, 0, 1, 4)).score(), 1.0e-9);
        assertEquals(42.55, pipeline.evaluate(snapshot(20, 8, 8, 1.6)).score(), 1.0e-9);
        assertEquals(45.65, pipeline.evaluate(snapshot(20, 12, 9, 1.6)).score(), 1.0e-9);
    }

    @Test
    void duplicateEvidenceUsesMaximumAndEvaluatorFailureIsIsolated() {
        PowerEvaluator low = evaluator("a", 2_000, false);
        PowerEvaluator high = evaluator("b", 7_000, false);
        PowerEvaluator broken = evaluator("broken", 0, true);
        PowerReport report = new PowerEvaluationPipeline(List.of(low, broken, high))
                .evaluate(snapshot(0, 0, 1, 4));

        assertEquals(7_000, report.totalMilliPoints());
        assertEquals(1, report.acceptedContributions().size());
        assertFalse(report.warnings().isEmpty());
    }

    @Test
    void groupScalingAndTemporalSmoothingAreDeterministic() {
        assertEquals(.7209294695715875, AdventureIntensityCalculator.groupPower(List.of(60.0, 60.0)), 1.0e-12);
        assertEquals(40.6611418471353, AdventureIntensityCalculator.smooth(40, 80, 20), 1.0e-12);
        assertEquals(77.4202794012647, AdventureIntensityCalculator.smooth(80, 40, 20), 1.0e-12);
        assertEquals(20.0,
                AdventureIntensityCalculator.smooth(0, 100, 100, 100, .20),
                1.0e-12);
    }

    @Test
    void configuredPowerCapsLimitTheMatchingAxis() {
        PowerReport report = PowerEvaluationPipeline.vanilla().evaluate(
                snapshot(20, 8, 8, 1.6), Map.of(PowerAxis.OFFENSE, 1_000));
        assertEquals(1_000, report.axisTotals().get(PowerAxis.OFFENSE));
    }

    @Test
    void hysteresisNeedsDwellTimeButDistressDropsImmediately() {
        AdventureIntensityCalculator.Hysteresis hysteresis =
                new AdventureIntensityCalculator.Hysteresis(IntensityBand.LOW);
        assertEquals(IntensityBand.LOW, hysteresis.update(70, 0, 199));
        assertEquals(IntensityBand.TACTICAL, hysteresis.update(70, 0, 1));
        assertEquals(IntensityBand.LOW, hysteresis.update(20, .70, 1));

        AdventureIntensityCalculator.Hysteresis configurable =
                new AdventureIntensityCalculator.Hysteresis(IntensityBand.TACTICAL);
        assertEquals(IntensityBand.LOW, configurable.update(34, .70, 1, 5));
    }

    private static PowerEvaluator evaluator(String id, int points, boolean broken) {
        return new PowerEvaluator() {
            @Override public String id() { return id; }
            @Override public List<PowerContribution> evaluate(PlayerPowerSnapshot snapshot) {
                if (broken) throw new IllegalStateException("expected");
                return List.of(new PowerContribution(PowerAxis.OFFENSE, "same", points, 1.0, "test"));
            }
        };
    }

    private static PlayerPowerSnapshot snapshot(double armor, double toughness, double damage, double speed) {
        return new PlayerPowerSnapshot(armor, toughness, damage, speed,
                0, 1, 3, 0, false, false,
                0, 0, false, false, 20,
                0, 0, 0, 0, .10, 3, 0);
    }
}
