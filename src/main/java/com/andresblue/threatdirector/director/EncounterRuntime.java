package com.andresblue.threatdirector.director;

import java.util.Objects;

public record EncounterRuntime(EncounterState state, long enteredAtTick) {
    public EncounterRuntime {
        Objects.requireNonNull(state, "state");
    }

    public long elapsedAt(long tick) {
        return Math.max(0L, tick - enteredAtTick);
    }
}
