package com.andresblue.threatdirector.telemetry;

import java.nio.file.Path;
import java.util.Objects;

/** Factory that keeps disabled telemetry allocation- and filesystem-free at call sites. */
public final class TelemetryRecorders {
    private static final TelemetryRecorder DISABLED = new DisabledTelemetryRecorder();

    private TelemetryRecorders() {}

    public static TelemetryRecorder localNdjson(
            boolean enabled, Path outputFile, int queueCapacity, int flushBatchSize) {
        if (!enabled) {
            return DISABLED;
        }
        return new NdjsonTelemetryRecorder(
                Objects.requireNonNull(outputFile, "outputFile"),
                queueCapacity,
                flushBatchSize);
    }

    public static TelemetryRecorder disabled() {
        return DISABLED;
    }

    private static final class DisabledTelemetryRecorder implements TelemetryRecorder {
        private static final TelemetrySnapshot SNAPSHOT = new TelemetrySnapshot(
                false, false, 0, 0, 0, 0, 0, 0, 0, "");

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public TelemetryRecordResult record(TelemetryEvent event) {
            Objects.requireNonNull(event, "event");
            return TelemetryRecordResult.DISABLED;
        }

        @Override
        public int flush() {
            return 0;
        }

        @Override
        public TelemetrySnapshot snapshot() {
            return SNAPSHOT;
        }

        @Override
        public void close() {}
    }
}
