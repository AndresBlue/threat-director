package com.andresblue.threatdirector.telemetry;

import com.andresblue.threatdirector.ThreatDirectorMod;
import java.util.Map;

/** Best-effort bridge from lightweight entity modules to local telemetry and OP chat debug. */
public final class GameplayDebugEvents {
    private GameplayDebugEvents() {
    }

    public static void record(String eventType, Map<String, ?> attributes) {
        try {
            ThreatDirectorMod.runtime().ifPresent(runtime ->
                    runtime.telemetry().record(TelemetryEvent.now(eventType, attributes)));
        } catch (RuntimeException error) {
            // Diagnostics must never become a gameplay failure path.
            ThreatDirectorMod.LOGGER.debug(
                    "Could not record gameplay debug event {}", eventType, error);
        }
    }
}
