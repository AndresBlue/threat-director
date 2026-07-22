package com.andresblue.threatdirector.api.v1;

public record EncounterEvent(
        EncounterHandle handle,
        EncounterEventType type,
        long serverTick,
        String detail) {
    public EncounterEvent {
        if (handle == null || type == null) throw new IllegalArgumentException("Event identity required");
        detail = detail == null ? "" : detail;
    }
}
