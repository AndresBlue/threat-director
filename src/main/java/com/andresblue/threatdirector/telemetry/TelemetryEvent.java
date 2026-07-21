package com.andresblue.threatdirector.telemetry;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable payload for one local NDJSON telemetry line. */
public record TelemetryEvent(
        Instant recordedAt, String eventType, Map<String, ?> attributes) {
    private static final Pattern VALID_EVENT_TYPE =
            Pattern.compile("[a-z][a-z0-9_.:-]{0,127}");

    public TelemetryEvent {
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(attributes, "attributes");
        if (!VALID_EVENT_TYPE.matcher(eventType).matches()) {
            throw new IllegalArgumentException(
                    "eventType must be a lowercase telemetry identifier: " + eventType);
        }
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "attribute key");
            if (key.isBlank()) {
                throw new IllegalArgumentException("Telemetry attribute keys must not be blank");
            }
            copy.put(key, entry.getValue());
        }
        attributes = Collections.unmodifiableMap(copy);
    }

    public static TelemetryEvent now(String eventType, Map<String, ?> attributes) {
        return new TelemetryEvent(Instant.now(), eventType, attributes);
    }
}
