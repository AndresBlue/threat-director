package com.andresblue.threatdirector.trait;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MobProfile(
        UUID id,
        String typeId,
        Set<MobTrait> traits,
        double healthFraction,
        double maxHealth,
        double armor,
        double attackDamage,
        double movementSpeed,
        double rangeNorm,
        boolean alive
) {
    public MobProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        traits = Set.copyOf(traits);
    }

    public boolean has(MobTrait trait) {
        return traits.contains(trait);
    }
}
