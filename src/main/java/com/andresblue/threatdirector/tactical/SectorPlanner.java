package com.andresblue.threatdirector.tactical;

import com.andresblue.threatdirector.role.TacticalRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SectorPlanner {
    private static final double[] ANGLES = {-StrictMath.PI / 8.0, 0.0, StrictMath.PI / 8.0};
    private static final int[] RADII = {6, 9, 12};
    private static final int CALLS_PER_CANDIDATE = 4;

    public SectorPlan plan(SectorPlanningRequest request, TerrainProbe terrain) {
        TacticalFrame frame = TacticalFrame.from(request.player(), request.horizontalFacing(), request.meanVelocity());
        Vec2 sectorDirection = frame.direction(request.sector()).normalizedOr(Vec2.NORTH);
        int operationLimit = Math.max(0, request.operationBudget());
        int candidateLimit = Math.max(0, request.candidateLimit());
        int operations = 0;
        List<CandidatePosition> candidates = new ArrayList<>();
        Set<BlockPoint> seen = new HashSet<>();

        outer:
        for (int radius : RADII) {
            for (double angle : ANGLES) {
                if (operations + CALLS_PER_CANDIDATE > operationLimit || candidates.size() >= candidateLimit) break outer;
                Vec2 direction = sectorDirection.rotate(angle);
                BlockPoint approximate = new BlockPoint(
                        (int) Math.round(frame.anchorX() + direction.x() * radius),
                        frame.anchorY(),
                        (int) Math.round(frame.anchorZ() + direction.z() * radius)
                );
                operations++;
                var standable = terrain.findStandable(approximate, 3);
                if (standable.isEmpty() || !seen.add(standable.get())) continue;
                BlockPoint position = standable.get();
                double pathQuality = terrain.pathQuality(request.mobStart(), position);
                operations++;
                if (!Double.isFinite(pathQuality) || pathQuality < 0.0) continue;
                double line = terrain.lineOfSightQuality(position);
                operations++;
                double cover = terrain.coverQuality(position);
                operations++;

                Vec2 fromAnchor = new Vec2(position.x() - frame.anchorX(), position.z() - frame.anchorZ());
                double distance = fromAnchor.length();
                double sectorFit = unit(fromAnchor.normalizedOr(sectorDirection).dot(sectorDirection));
                double distanceFit = distanceFit(request.role(), distance);
                double separation = separation(position, request.occupied());
                double roleQuality = request.role() == TacticalRole.INTERCEPTOR ? sectorFit : unit(line);
                int score = (int) Math.round(300 * unit(pathQuality) + 200 * sectorFit
                        + 150 * distanceFit + 150 * separation + 120 * roleQuality + 80 * unit(cover));
                candidates.add(new CandidatePosition(position, request.sector(), score, unit(pathQuality),
                        separation, roleQuality, unit(cover)));
            }
        }
        candidates.sort(Comparator.comparingInt(CandidatePosition::score).reversed()
                .thenComparing(CandidatePosition::position));
        boolean fallback = candidates.isEmpty() || candidates.get(0).score() < 550;
        return new SectorPlan(frame, candidates, operations, fallback);
    }

    private static double distanceFit(TacticalRole role, double distance) {
        double minimum;
        double maximum;
        switch (role) {
            case VANGUARD -> { minimum = 4; maximum = 7; }
            case FLANKER -> { minimum = 7; maximum = 11; }
            case HARASSER -> { minimum = 10; maximum = 16; }
            case INTERCEPTOR -> { minimum = 8; maximum = 14; }
            default -> { minimum = 6; maximum = 12; }
        }
        double midpoint = (minimum + maximum) / 2.0;
        double halfWidth = (maximum - minimum) / 2.0;
        return unit(1.0 - Math.abs(distance - midpoint) / Math.max(1.0, halfWidth));
    }

    private static double separation(BlockPoint position, List<BlockPoint> occupied) {
        if (occupied.isEmpty()) return 1.0;
        double closest = occupied.stream().mapToDouble(position::horizontalDistance).min().orElse(4.0);
        return unit(closest / 4.0);
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
