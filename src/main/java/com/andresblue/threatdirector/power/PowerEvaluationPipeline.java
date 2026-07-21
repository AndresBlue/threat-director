package com.andresblue.threatdirector.power;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PowerEvaluationPipeline {
    private final List<PowerEvaluator> evaluators;

    public PowerEvaluationPipeline(List<? extends PowerEvaluator> evaluators) {
        Objects.requireNonNull(evaluators, "evaluators");
        List<PowerEvaluator> ordered = new ArrayList<>(evaluators);
        ordered.sort(Comparator.comparingInt(PowerEvaluator::priority).thenComparing(PowerEvaluator::id));
        Set<String> ids = new HashSet<>();
        for (PowerEvaluator evaluator : ordered) {
            Objects.requireNonNull(evaluator, "evaluator");
            if (evaluator.id() == null || evaluator.id().isBlank()) {
                throw new IllegalArgumentException("Power evaluator id must not be blank");
            }
            if (!ids.add(evaluator.id())) {
                throw new IllegalArgumentException("Duplicate power evaluator id: " + evaluator.id());
            }
        }
        this.evaluators = List.copyOf(ordered);
    }

    public static PowerEvaluationPipeline vanilla() {
        return new PowerEvaluationPipeline(List.of(
                new VanillaArmorEvaluator(),
                new VanillaWeaponEvaluator(),
                new VanillaEnchantEvaluator(),
                new VanillaConsumableEvaluator(),
                new AttributeEvaluator(),
                new TagBasedEvaluator()
        ));
    }

    public PowerReport evaluate(PlayerPowerSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<Evidence, ScoredContribution> bestEvidence = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        for (PowerEvaluator evaluator : evaluators) {
            List<PowerContribution> contributions;
            try {
                contributions = evaluator.evaluate(snapshot);
            } catch (RuntimeException exception) {
                warnings.add("Evaluator " + evaluator.id() + " failed: " + exception.getClass().getSimpleName());
                continue;
            }
            if (contributions == null) {
                warnings.add("Evaluator " + evaluator.id() + " returned null");
                continue;
            }
            for (PowerContribution contribution : contributions) {
                if (contribution == null || contribution.evidenceKey().isBlank()) {
                    warnings.add("Evaluator " + evaluator.id() + " returned invalid evidence");
                    continue;
                }
                if (contribution.milliPoints() < 0 || !Double.isFinite(contribution.confidence())) {
                    warnings.add("Evaluator " + evaluator.id() + " returned non-finite or negative evidence");
                    continue;
                }
                double confidence = clamp01(contribution.confidence());
                int raw = Math.min(contribution.milliPoints(), contribution.axis().cap());
                int effective = (int) Math.round(raw * confidence);
                ScoredContribution scored = new ScoredContribution(evaluator.id(), contribution, effective);
                Evidence key = new Evidence(contribution.axis(), contribution.evidenceKey());
                bestEvidence.merge(key, scored, PowerEvaluationPipeline::better);
            }
        }

        List<ScoredContribution> accepted = new ArrayList<>(bestEvidence.values());
        accepted.sort(Comparator
                .comparing((ScoredContribution value) -> value.contribution().axis())
                .thenComparing(value -> value.contribution().evidenceKey())
                .thenComparing(ScoredContribution::evaluatorId));

        EnumMap<PowerAxis, Integer> totals = new EnumMap<>(PowerAxis.class);
        for (PowerAxis axis : PowerAxis.values()) {
            totals.put(axis, 0);
        }
        for (ScoredContribution scored : accepted) {
            PowerAxis axis = scored.contribution().axis();
            totals.compute(axis, (ignored, current) -> Math.min(axis.cap(), current + scored.effectiveMilliPoints()));
        }
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        return new PowerReport(Math.min(100_000, total), totals, accepted, warnings);
    }

    private static ScoredContribution better(ScoredContribution left, ScoredContribution right) {
        int byPoints = Integer.compare(left.effectiveMilliPoints(), right.effectiveMilliPoints());
        if (byPoints != 0) {
            return byPoints >= 0 ? left : right;
        }
        return left.evaluatorId().compareTo(right.evaluatorId()) <= 0 ? left : right;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Evidence(PowerAxis axis, String key) {}
}
