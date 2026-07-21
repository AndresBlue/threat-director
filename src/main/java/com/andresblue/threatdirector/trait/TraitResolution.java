package com.andresblue.threatdirector.trait;

import java.util.List;
import java.util.Set;

public record TraitResolution(Set<MobTrait> traits, List<String> warnings) {
    public TraitResolution {
        traits = Set.copyOf(traits);
        warnings = List.copyOf(warnings);
    }
}
