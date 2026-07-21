package com.andresblue.threatdirector.breach;

import com.andresblue.threatdirector.tactical.BlockPoint;

import java.util.List;

public record BreachPlan(
        BreachDecision decision,
        List<BlockPoint> blocks,
        String reason,
        List<BlockPoint> routeOpened,
        int blocksRequired,
        ProtectionStatus protection,
        boolean safe,
        int budgetCost,
        int visitedNodes
) {
    public BreachPlan {
        blocks = List.copyOf(blocks);
        routeOpened = List.copyOf(routeOpened);
    }
}
