package com.andresblue.threatdirector.api.v1;

import java.util.Optional;

public record EncounterUpdate(
        Optional<PressureProfile> desiredPressure,
        Optional<Boolean> allowReinforcements,
        Optional<Boolean> authorizeRetreat,
        String note) {
    public EncounterUpdate {
        desiredPressure = desiredPressure == null ? Optional.empty() : desiredPressure;
        allowReinforcements = allowReinforcements == null ? Optional.empty() : allowReinforcements;
        authorizeRetreat = authorizeRetreat == null ? Optional.empty() : authorizeRetreat;
        note = note == null ? "" : note;
    }
}
