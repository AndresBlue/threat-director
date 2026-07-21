package com.andresblue.threatdirector.telemetry;

import java.util.Objects;

/** Operational counters suitable for debug commands without exposing the live queue. */
public record TelemetrySnapshot(
        boolean enabled,
        boolean closed,
        int queued,
        int capacity,
        long accepted,
        long written,
        long droppedQueueFull,
        long serializationFailures,
        long writeFailures,
        String lastError) {
    public TelemetrySnapshot {
        lastError = Objects.requireNonNullElse(lastError, "");
    }
}
