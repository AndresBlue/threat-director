package com.andresblue.threatdirector.trait;

import java.util.Set;

public interface MobTraitProvider {
    String id();

    default int priority() {
        return 1_000;
    }

    TraitPatch traitsFor(String entityTypeId);

    record TraitPatch(Set<MobTrait> add, Set<MobTrait> remove) {
        public TraitPatch {
            add = Set.copyOf(add);
            remove = Set.copyOf(remove);
        }

        public static TraitPatch empty() {
            return new TraitPatch(Set.of(), Set.of());
        }
    }
}
