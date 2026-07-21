package com.andresblue.threatdirector.power;

import java.util.Objects;

public record PowerContribution(
        PowerAxis axis,
        String evidenceKey,
        int milliPoints,
        double confidence,
        String explanation
) {
    public PowerContribution {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(evidenceKey, "evidenceKey");
        explanation = explanation == null ? "" : explanation;
    }
}
