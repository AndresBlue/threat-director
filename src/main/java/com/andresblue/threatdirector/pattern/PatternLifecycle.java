package com.andresblue.threatdirector.pattern;

import com.andresblue.threatdirector.director.EncounterState;

public final class PatternLifecycle {
    private PatternLifecycle() {}

    public static PatternExecutionResult evaluate(PatternId id, PatternExecutionSnapshot snapshot) {
        return switch (id) {
            case PINCER -> pincer(snapshot);
            case EXIT_CLOSURE -> exitClosure(snapshot);
        };
    }

    private static PatternExecutionResult pincer(PatternExecutionSnapshot value) {
        if (value.wingsInOppositePositions() && value.attackWindowDeltaTicks() <= 40) {
            return success("wings_synchronized");
        }
        if (!value.bothWingsAlive()) return cancelled("wing_lost");
        if (value.consecutiveReplanFailures() >= 3) return cancelled("replan_failed");
        if (value.targetDisplacement() > 16.0) return cancelled("anchor_abandoned");
        if (!activePhase(value.state())) return cancelled("phase_changed");
        if (value.elapsedTicks() >= 400) return cancelled("timeout");
        return running();
    }

    private static PatternExecutionResult exitClosure(PatternExecutionSnapshot value) {
        if (value.interceptorDistanceToWaypoint() <= 3.0 && value.interceptorArrivedBeforeTarget()
                && value.rearPressurePresent()) {
            return success("exit_occupied");
        }
        if (value.targetStationaryTicks() >= 80) return cancelled("target_stopped");
        if (value.escapeRouteConfidence() < .35) return cancelled("route_lost");
        if (value.routeDeviationDegrees() > 60.0) return cancelled("target_deviated");
        if (!value.waypointReachable()) return cancelled("waypoint_unreachable");
        if (!value.contact()) return cancelled("contact_lost");
        if (!activePhase(value.state())) return cancelled("phase_changed");
        if (value.elapsedTicks() >= 300) return cancelled("timeout");
        return running();
    }

    private static boolean activePhase(EncounterState state) {
        return state == EncounterState.POSITIONING || state == EncounterState.ENGAGING;
    }

    private static PatternExecutionResult running() {
        return new PatternExecutionResult(PatternExecutionResult.Status.RUNNING, "running");
    }

    private static PatternExecutionResult success(String reason) {
        return new PatternExecutionResult(PatternExecutionResult.Status.SUCCESS, reason);
    }

    private static PatternExecutionResult cancelled(String reason) {
        return new PatternExecutionResult(PatternExecutionResult.Status.CANCELLED, reason);
    }
}
