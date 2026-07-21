package com.andresblue.threatdirector.tactical;

import com.andresblue.threatdirector.role.TacticalRole;

import java.util.List;

public record SectorPlanningRequest(
        BlockPoint player,
        Vec2 horizontalFacing,
        Vec2 meanVelocity,
        BlockPoint mobStart,
        TacticalSector sector,
        TacticalRole role,
        List<BlockPoint> occupied,
        int candidateLimit,
        int operationBudget,
        double minimumRadius,
        double maximumRadius
) {
    public SectorPlanningRequest {
        occupied = List.copyOf(occupied);
        if (!Double.isFinite(minimumRadius) || !Double.isFinite(maximumRadius)
                || minimumRadius <= 0.0 || maximumRadius < minimumRadius) {
            throw new IllegalArgumentException("tactical radii must be finite, positive and ordered");
        }
    }

    public SectorPlanningRequest(
            BlockPoint player,
            Vec2 horizontalFacing,
            Vec2 meanVelocity,
            BlockPoint mobStart,
            TacticalSector sector,
            TacticalRole role,
            List<BlockPoint> occupied,
            int candidateLimit,
            int operationBudget) {
        this(player, horizontalFacing, meanVelocity, mobStart, sector, role, occupied,
                candidateLimit, operationBudget, 6.0, 12.0);
    }
}
