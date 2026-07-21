package com.andresblue.threatdirector.director;

import java.util.Optional;

public record TransitionResult(
        EncounterRuntime runtime,
        Optional<EncounterTransition> transition,
        Optional<String> rejection
) {
    public TransitionResult {
        transition = transition == null ? Optional.empty() : transition;
        rejection = rejection == null ? Optional.empty() : rejection;
    }

    public static TransitionResult unchanged(EncounterRuntime runtime) {
        return new TransitionResult(runtime, Optional.empty(), Optional.empty());
    }
}
