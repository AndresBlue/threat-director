package com.andresblue.threatdirector.awareness;

import java.util.EnumSet;
import java.util.Set;

/** Immutable set of the additional senses assigned to one mob. */
public record AwarenessProfile(Set<AwarenessSense> senses) {
    public AwarenessProfile {
        senses = senses.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(senses));
    }

    public static AwarenessProfile none() {
        return new AwarenessProfile(Set.of());
    }

    public boolean has(AwarenessSense sense) {
        return senses.contains(sense);
    }

    public boolean isEmpty() {
        return senses.isEmpty();
    }
}
