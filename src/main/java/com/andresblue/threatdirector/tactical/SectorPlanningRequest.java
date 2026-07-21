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
        int operationBudget
) {
    public SectorPlanningRequest {
        occupied = List.copyOf(occupied);
    }
}
