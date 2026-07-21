package com.andresblue.threatdirector.pattern;

import com.andresblue.threatdirector.director.EncounterState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PatternSelector {
    public PatternSelection select(PatternContext context) {
        List<PatternEvaluation> evaluations = List.of(evaluatePincer(context), evaluateExitClosure(context));
        Optional<PatternEvaluation> selected = evaluations.stream()
                .filter(PatternEvaluation::eligible)
                .sorted(Comparator.comparingInt(PatternEvaluation::score).reversed()
                        .thenComparing(PatternEvaluation::id))
                .findFirst();
        return new PatternSelection(selected, evaluations);
    }

    public PatternEvaluation evaluatePincer(PatternContext context) {
        List<String> reasons = commonReasons(context, PatternId.PINCER, 50.0, 4, 500);
        if (context.flankerCandidates() < 2) reasons.add("requires_two_flankers");
        if (context.oppositePairDot() > -.866) reasons.add("opposite_sectors_unavailable");
        if (context.openness() < .45) reasons.add("terrain_too_narrow");
        double opposition = unit((-context.oppositePairDot() - .5) / .5);
        int score = (int) Math.round(350 + 200 * opposition + 150 * unit(context.openness())
                + 150 * unit(context.roleReadiness()) + 100 * unit(context.lineOfAdvanceQuality())
                - previousPenalty(context, PatternId.PINCER));
        return new PatternEvaluation(PatternId.PINCER, reasons.isEmpty(), clampScore(score),
                500, 1_200, 400, reasons);
    }

    public PatternEvaluation evaluateExitClosure(PatternContext context) {
        List<String> reasons = commonReasons(context, PatternId.EXIT_CLOSURE, 55.0, 3, 400);
        if (!context.interceptorAvailable()) reasons.add("requires_interceptor");
        if (context.escapeRouteConfidence() < .55) reasons.add("escape_route_uncertain");
        if (context.movementPersistence() < .45) reasons.add("movement_not_persistent");
        if (context.interceptorPathQuality() <= 0.0) reasons.add("intercept_unreachable");
        int score = (int) Math.round(300 + 250 * unit(context.escapeRouteConfidence())
                + 200 * unit(context.movementPersistence()) + 150 * unit(context.interceptorPathQuality())
                + 100 * unit(context.runsStraightMemory())
                - previousPenalty(context, PatternId.EXIT_CLOSURE));
        return new PatternEvaluation(PatternId.EXIT_CLOSURE, reasons.isEmpty(), clampScore(score),
                400, 900, 300, reasons);
    }

    private static List<String> commonReasons(PatternContext context, PatternId id,
                                              double minimumIntensity, int minimumMobs, int cost) {
        List<String> reasons = new ArrayList<>();
        if (context.state() != EncounterState.POSITIONING && context.state() != EncounterState.ENGAGING) {
            reasons.add("invalid_phase");
        }
        if (context.intensity() < minimumIntensity) reasons.add("intensity_too_low");
        if (context.livingMobs() < minimumMobs) reasons.add("insufficient_mobs");
        if (context.remainingThreat() < cost) reasons.add("insufficient_threat_budget");
        if (context.cooldownUntil().getOrDefault(id, Long.MIN_VALUE) > context.nowTick()) reasons.add("cooldown_active");
        return reasons;
    }

    private static int previousPenalty(PatternContext context, PatternId id) {
        return context.previousPattern().filter(id::equals).isPresent() ? 150 : 0;
    }

    private static int clampScore(int value) {
        return Math.max(0, Math.min(1_000, value));
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
