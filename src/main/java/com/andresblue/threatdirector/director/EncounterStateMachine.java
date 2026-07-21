package com.andresblue.threatdirector.director;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

public final class EncounterStateMachine {
    private static final Map<EncounterState, EnumSet<EncounterState>> ALLOWED = allowedTransitions();
    private final EncounterDurations durations;

    public EncounterStateMachine(EncounterDurations durations) {
        this.durations = durations;
    }

    public TransitionResult step(EncounterRuntime runtime, EncounterSnapshot snapshot) {
        long elapsed = runtime.elapsedAt(snapshot.nowTick());
        EncounterState current = runtime.state();

        if (current == EncounterState.DORMANT) {
            return snapshot.startRequested()
                    ? transition(runtime, EncounterState.STALKING, TransitionCause.ENCOUNTER_ACCEPTED, snapshot)
                    : TransitionResult.unchanged(runtime);
        }
        if (current == EncounterState.COOLDOWN) {
            return elapsed >= durations.cooldown()
                    ? transition(runtime, EncounterState.DORMANT, TransitionCause.COOLDOWN_EXPIRED, snapshot)
                    : TransitionResult.unchanged(runtime);
        }
        if (current == EncounterState.ABORTED) {
            return snapshot.cleanupComplete()
                    ? transition(runtime, EncounterState.COOLDOWN, TransitionCause.CLEANUP_COMPLETE, snapshot)
                    : TransitionResult.unchanged(runtime);
        }
        if (current == EncounterState.RELIEF) {
            if ((elapsed >= durations.reliefMin() && snapshot.pressureDisengaged())
                    || elapsed >= durations.reliefMax()) {
                return transition(runtime, EncounterState.COOLDOWN, TransitionCause.PRESSURE_DISENGAGED, snapshot);
            }
            return TransitionResult.unchanged(runtime);
        }
        if (current == EncounterState.RETREATING) {
            if (snapshot.retreatComplete() || elapsed >= durations.retreatingMax()) {
                return transition(runtime, EncounterState.COOLDOWN, TransitionCause.RETREAT_COMPLETE, snapshot);
            }
            return TransitionResult.unchanged(runtime);
        }

        // Safety and validity always outrank normal phase progress.
        if (!snapshot.targetValid()) {
            return safetyExit(runtime, TransitionCause.TARGET_INVALID, snapshot);
        }
        if (!snapshot.sameDimension()) {
            return safetyExit(runtime, TransitionCause.DIMENSION_CHANGED, snapshot);
        }
        if (snapshot.performanceLevel() >= 6) {
            return safetyExit(runtime, TransitionCause.PERFORMANCE_SHED, snapshot);
        }
        if (!snapshot.navigationViable()) {
            return safetyExit(runtime, TransitionCause.NAVIGATION_FAILED, snapshot);
        }
        if (snapshot.livingMobs() <= 0) {
            return safetyExit(runtime, TransitionCause.GROUP_DEFEATED, snapshot);
        }
        if (snapshot.targetInProtectedZone()) {
            return safetyExit(runtime, TransitionCause.ENTERED_PROTECTED_ZONE, snapshot);
        }

        return switch (current) {
            case STALKING -> stepStalking(runtime, snapshot, elapsed);
            case POSITIONING -> stepPositioning(runtime, snapshot, elapsed);
            case ENGAGING -> stepEngaging(runtime, snapshot, elapsed);
            case SEARCHING -> stepSearching(runtime, snapshot, elapsed);
            case CLIMAX -> stepClimax(runtime, snapshot, elapsed);
            default -> TransitionResult.unchanged(runtime);
        };
    }

    public TransitionResult requestTransition(EncounterRuntime runtime, EncounterState next,
                                              TransitionCause cause, EncounterSnapshot snapshot) {
        return transition(runtime, next, cause, snapshot);
    }

    private TransitionResult stepStalking(EncounterRuntime runtime, EncounterSnapshot snapshot, long elapsed) {
        if (snapshot.contactAgeTicks() >= 100 && elapsed >= durations.stalkingMax()) {
            return transition(runtime, EncounterState.ABORTED, TransitionCause.PHASE_TIMEOUT, snapshot);
        }
        if (elapsed >= durations.stalkingMin() && snapshot.contactAgeTicks() < 100) {
            return transition(runtime, EncounterState.POSITIONING, TransitionCause.STALKING_COMPLETE, snapshot);
        }
        return TransitionResult.unchanged(runtime);
    }

    private TransitionResult stepPositioning(EncounterRuntime runtime, EncounterSnapshot snapshot, long elapsed) {
        if (snapshot.contactAgeTicks() >= 100) {
            return transition(runtime, EncounterState.SEARCHING, TransitionCause.CONTACT_LOST, snapshot);
        }
        int readyRequired = Math.max(1, (int) Math.ceil(snapshot.livingMobs() * .60));
        if (elapsed >= durations.positioningMin() && snapshot.positionedMobs() >= readyRequired) {
            return transition(runtime, EncounterState.ENGAGING, TransitionCause.POSITIONS_READY, snapshot);
        }
        if (elapsed >= durations.positioningMax()) {
            return snapshot.positionedMobs() >= Math.min(2, snapshot.livingMobs())
                    ? transition(runtime, EncounterState.ENGAGING, TransitionCause.PARTIAL_POSITIONS_TIMEOUT, snapshot)
                    : transition(runtime, EncounterState.ABORTED, TransitionCause.PHASE_TIMEOUT, snapshot);
        }
        return TransitionResult.unchanged(runtime);
    }

    private TransitionResult stepEngaging(EncounterRuntime runtime, EncounterSnapshot snapshot, long elapsed) {
        if (snapshot.contactAgeTicks() >= 100) {
            return transition(runtime, EncounterState.SEARCHING, TransitionCause.CONTACT_LOST, snapshot);
        }
        if (snapshot.distress() >= .70) {
            return transition(runtime, EncounterState.RELIEF, TransitionCause.PLAYER_DISTRESSED, snapshot);
        }
        if (snapshot.livingMobs() < 2 || snapshot.remainingThreatRatio() <= .25) {
            return transition(runtime, EncounterState.RELIEF, TransitionCause.THREAT_DEPLETED, snapshot);
        }
        if (elapsed >= durations.engagingMax()) {
            return transition(runtime, EncounterState.RELIEF, TransitionCause.ENGAGEMENT_TIMEOUT, snapshot);
        }
        if (snapshot.climaxEnabled() && elapsed >= durations.engagingMinBeforeClimax()
                && snapshot.intensity() >= 75.0 && snapshot.dominance() >= .65
                && snapshot.performanceLevel() <= 3) {
            return transition(runtime, EncounterState.CLIMAX, TransitionCause.CLIMAX_READY, snapshot);
        }
        return TransitionResult.unchanged(runtime);
    }

    private TransitionResult stepSearching(EncounterRuntime runtime, EncounterSnapshot snapshot, long elapsed) {
        if (snapshot.contactAgeTicks() < 20) {
            return transition(runtime, EncounterState.ENGAGING, TransitionCause.TARGET_REACQUIRED, snapshot);
        }
        if (elapsed >= durations.searchingMax()) {
            return transition(runtime, EncounterState.ABORTED, TransitionCause.PHASE_TIMEOUT, snapshot);
        }
        return TransitionResult.unchanged(runtime);
    }

    private TransitionResult stepClimax(EncounterRuntime runtime, EncounterSnapshot snapshot, long elapsed) {
        if (snapshot.distress() >= .70 || snapshot.remainingThreatRatio() <= .25
                || elapsed >= durations.climaxMax()) {
            return transition(runtime, EncounterState.RETREATING, TransitionCause.CLIMAX_COMPLETE, snapshot);
        }
        return TransitionResult.unchanged(runtime);
    }

    private TransitionResult safetyExit(EncounterRuntime runtime, TransitionCause cause, EncounterSnapshot snapshot) {
        EncounterState destination = switch (runtime.state()) {
            case ENGAGING, CLIMAX -> EncounterState.RELIEF;
            case STALKING, POSITIONING, SEARCHING -> EncounterState.ABORTED;
            default -> EncounterState.ABORTED;
        };
        return transition(runtime, destination, cause, snapshot);
    }

    private TransitionResult transition(EncounterRuntime runtime, EncounterState next,
                                        TransitionCause cause, EncounterSnapshot snapshot) {
        if (!ALLOWED.getOrDefault(runtime.state(), EnumSet.noneOf(EncounterState.class)).contains(next)) {
            return new TransitionResult(runtime, Optional.empty(),
                    Optional.of("Illegal encounter transition " + runtime.state() + " -> " + next));
        }
        EncounterRuntime nextRuntime = new EncounterRuntime(next, snapshot.nowTick());
        EncounterTransition event = new EncounterTransition(runtime.state(), next, cause,
                snapshot.targetPlayer(), snapshot.encounterId(), snapshot.nowTick(),
                snapshot.intensity(), snapshot.remainingThreat());
        return new TransitionResult(nextRuntime, Optional.of(event), Optional.empty());
    }

    private static Map<EncounterState, EnumSet<EncounterState>> allowedTransitions() {
        EnumMap<EncounterState, EnumSet<EncounterState>> map = new EnumMap<>(EncounterState.class);
        map.put(EncounterState.DORMANT, EnumSet.of(EncounterState.STALKING));
        map.put(EncounterState.STALKING, EnumSet.of(EncounterState.POSITIONING, EncounterState.ABORTED));
        map.put(EncounterState.POSITIONING, EnumSet.of(EncounterState.ENGAGING, EncounterState.SEARCHING, EncounterState.ABORTED));
        map.put(EncounterState.ENGAGING, EnumSet.of(EncounterState.SEARCHING, EncounterState.CLIMAX,
                EncounterState.RELIEF, EncounterState.ABORTED));
        map.put(EncounterState.SEARCHING, EnumSet.of(EncounterState.ENGAGING, EncounterState.ABORTED));
        map.put(EncounterState.CLIMAX, EnumSet.of(EncounterState.RETREATING, EncounterState.RELIEF, EncounterState.ABORTED));
        map.put(EncounterState.RELIEF, EnumSet.of(EncounterState.COOLDOWN));
        map.put(EncounterState.RETREATING, EnumSet.of(EncounterState.COOLDOWN));
        map.put(EncounterState.ABORTED, EnumSet.of(EncounterState.COOLDOWN));
        map.put(EncounterState.COOLDOWN, EnumSet.of(EncounterState.DORMANT));
        return Map.copyOf(map);
    }
}
