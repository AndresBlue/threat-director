package com.andresblue.threatdirector.compatibility;

import java.util.List;
import java.util.Objects;

/** Immutable diagnostics from one Fabric integration discovery pass. */
public record IntegrationLoadReport(List<Entry> entries) {
    public IntegrationLoadReport {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    public long loadedCount() {
        return count(Outcome.LOADED);
    }

    public long skippedCount() {
        return count(Outcome.SKIPPED_UNAVAILABLE);
    }

    public long failedCount() {
        return count(Outcome.FAILED) + count(Outcome.DISCOVERY_FAILED);
    }

    public boolean successful() {
        return failedCount() == 0;
    }

    private long count(Outcome outcome) {
        return entries.stream().filter(entry -> entry.outcome() == outcome).count();
    }

    public enum Outcome {
        LOADED,
        SKIPPED_UNAVAILABLE,
        FAILED,
        DISCOVERY_FAILED
    }

    /** Error fields are empty strings for outcomes without an exception. */
    public record Entry(
            String providerId,
            String integrationId,
            Outcome outcome,
            String errorType,
            String message) {
        public Entry {
            providerId = Objects.requireNonNullElse(providerId, "<unknown>");
            integrationId = Objects.requireNonNullElse(integrationId, "");
            outcome = Objects.requireNonNull(outcome, "outcome");
            errorType = Objects.requireNonNullElse(errorType, "");
            message = Objects.requireNonNullElse(message, "");
        }
    }
}
