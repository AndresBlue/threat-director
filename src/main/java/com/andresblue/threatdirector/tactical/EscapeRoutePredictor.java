package com.andresblue.threatdirector.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EscapeRoutePredictor {
    private static final int[] DISTANCES = {4, 8, 12};

    public EscapeRoutePrediction predict(Vec2 velocity, Vec2 facing, Vec2 awayFromThreats,
                                         int directionCount, EscapeDirectionProbe probe) {
        int count = directionCount >= 16 ? 16 : directionCount >= 8 ? 8 : 4;
        Vec2 base = velocity.normalizedOr(Vec2.NORTH).scale(.60)
                .add(facing.normalizedOr(Vec2.NORTH).scale(.25))
                .add(awayFromThreats.normalizedOr(Vec2.NORTH).scale(.15))
                .normalizedOr(Vec2.NORTH);
        List<ScoredDirection> directions = new ArrayList<>();
        int operations = 0;
        for (int index = 0; index < count; index++) {
            Vec2 direction = base.rotate(index * 2.0 * StrictMath.PI / count).normalizedOr(Vec2.NORTH);
            double passable = 0;
            double clearance = 0;
            double distance = 0;
            double recent = 0;
            for (int probeDistance : DISTANCES) {
                EscapeDirectionProbe.EscapeSample sample = probe.sample(direction, probeDistance);
                operations++;
                passable += unit(sample.passable());
                clearance += unit(sample.clearance());
                distance += unit(sample.distanceFromThreats());
                recent += unit(sample.recentlyUsed());
            }
            double score = .45 * passable / 3.0 + .25 * clearance / 3.0
                    + .20 * distance / 3.0 + .10 * recent / 3.0;
            directions.add(new ScoredDirection(index, direction, score));
        }
        directions.sort(Comparator.comparingDouble(ScoredDirection::score).reversed()
                .thenComparingInt(ScoredDirection::index));
        ScoredDirection primary = directions.get(0);
        ScoredDirection secondary = directions.stream()
                .filter(candidate -> angleDegrees(primary.direction(), candidate.direction()) >= 67.5)
                .findFirst().orElse(primary);
        return new EscapeRoutePrediction(primary.direction(), primary.score(),
                secondary.direction(), secondary.score(), operations);
    }

    private static double angleDegrees(Vec2 left, Vec2 right) {
        double dot = Math.max(-1.0, Math.min(1.0, left.dot(right)));
        return StrictMath.toDegrees(StrictMath.acos(dot));
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ScoredDirection(int index, Vec2 direction, double score) {}
}
