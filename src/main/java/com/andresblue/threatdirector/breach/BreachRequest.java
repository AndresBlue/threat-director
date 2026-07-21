package com.andresblue.threatdirector.breach;

import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.tactical.BlockPoint;

public record BreachRequest(
        BlockPoint mobStart,
        BlockPoint target,
        double intensity,
        double minimumIntensity,
        EncounterState encounterState,
        TacticalRole role,
        BreachPolicy policy,
        boolean tacticalDestructionRule,
        boolean globalCooldownReady,
        boolean mobCooldownReady,
        int maximumBlocks,
        int blockBudget,
        double maximumHardness,
        double minimumClaimDistance,
        int horizontalRadius,
        int verticalRadius,
        int maximumVisitedNodes
) {}
