package com.andresblue.threatdirector.traits;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Namespaced command tags are persisted by vanilla entity NBT. */
public final class TraitTagCodec {
    public static final String ROLLED_TAG = "threatdirector.traits.rolled.v1";
    public static final String TRAIT_PREFIX = "threatdirector.trait.";

    private TraitTagCodec() {
    }

    public static String encode(MobTrait trait) {
        return TRAIT_PREFIX + trait.name().toLowerCase(Locale.ROOT);
    }

    public static Set<MobTrait> decode(Set<String> tags) {
        EnumSet<MobTrait> traits = EnumSet.noneOf(MobTrait.class);
        for (MobTrait trait : MobTrait.values()) {
            if (tags.contains(encode(trait))) {
                traits.add(trait);
            }
        }
        return Set.copyOf(traits);
    }
}
