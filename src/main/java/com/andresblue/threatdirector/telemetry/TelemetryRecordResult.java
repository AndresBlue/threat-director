package com.andresblue.threatdirector.telemetry;

/** Non-throwing result of trying to enqueue one telemetry event. */
public enum TelemetryRecordResult {
    ENQUEUED,
    DISABLED,
    QUEUE_FULL,
    SERIALIZATION_FAILED,
    CLOSED
}
