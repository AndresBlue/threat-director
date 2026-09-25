package com.andresblue.threatdirector.awareness;

import java.util.EnumSet;
import java.util.Set;

/** Stable command tags persist awareness without registering synced custom attributes. */
public final class AwarenessTags {
    public static final String ASSIGNED = "threatdirector_awareness_assigned";
    public static final String MOVEMENT = "threatdirector_awareness_movement";
    public static final String SOUND = "threatdirector_awareness_sound";
    public static final String LIGHT = "threatdirector_awareness_light";

    private AwarenessTags() {
    }

    public static AwarenessProfile decode(Set<String> tags) {
        EnumSet<AwarenessSense> senses = EnumSet.noneOf(AwarenessSense.class);
        if (tags.contains(MOVEMENT)) {
            senses.add(AwarenessSense.MOVEMENT);
        }
        if (tags.contains(SOUND)) {
            senses.add(AwarenessSense.SOUND);
        }
        if (tags.contains(LIGHT)) {
            senses.add(AwarenessSense.LIGHT);
        }
        return new AwarenessProfile(senses);
    }

    public static Set<String> encode(AwarenessProfile profile) {
        java.util.HashSet<String> tags = new java.util.HashSet<>();
        tags.add(ASSIGNED);
        if (profile.has(AwarenessSense.MOVEMENT)) {
            tags.add(MOVEMENT);
        }
        if (profile.has(AwarenessSense.SOUND)) {
            tags.add(SOUND);
        }
        if (profile.has(AwarenessSense.LIGHT)) {
            tags.add(LIGHT);
        }
        return Set.copyOf(tags);
    }
}
