package com.andresblue.threatdirector.pattern;

import com.andresblue.threatdirector.director.EncounterState;

public record PatternExecutionSnapshot(
        long elapsedTicks,
        EncounterState state,
        boolean bothWingsAlive,
        int consecutiveReplanFailures,
        double targetDisplacement,
        boolean wingsInOppositePositions,
        long attackWindowDeltaTicks,
        long targetStationaryTicks,
        double escapeRouteConfidence,
        double routeDeviationDegrees,
        boolean waypointReachable,
        boolean contact,
        double interceptorDistanceToWaypoint,
        boolean interceptorArrivedBeforeTarget,
        boolean rearPressurePresent
) {}
