package com.andresblue.threatdirector.telemetry;

/** Optional local telemetry sink. Implementations must not transmit data over the network. */
public interface TelemetryRecorder extends AutoCloseable {
    boolean isEnabled();

    /** Attempts a non-blocking enqueue; a full queue drops the new event. */
    TelemetryRecordResult record(TelemetryEvent event);

    /**
     * Writes at most the configured batch size and returns the number of completed NDJSON lines.
     * I/O errors are captured in {@link #snapshot()} rather than propagated into the server loop.
     */
    int flush();

    TelemetrySnapshot snapshot();

    /** Stops accepting events and makes a best-effort attempt to drain the bounded queue. */
    @Override
    void close();
}
