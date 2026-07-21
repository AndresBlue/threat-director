package com.andresblue.threatdirector.trait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MobTraitRegistry {
    private final Map<String, Set<MobTrait>> baseTraits;
    private final List<MobTraitProvider> providers;

    public MobTraitRegistry(Map<String, ? extends Set<MobTrait>> baseTraits,
                            List<? extends MobTraitProvider> providers) {
        Map<String, Set<MobTrait>> copied = new HashMap<>();
        baseTraits.forEach((key, value) -> copied.put(key, Set.copyOf(value)));
        this.baseTraits = Map.copyOf(copied);
        List<MobTraitProvider> ordered = new ArrayList<>(providers);
        ordered.sort(Comparator.comparingInt(MobTraitProvider::priority).thenComparing(MobTraitProvider::id));
        this.providers = List.copyOf(ordered);
    }

    public static MobTraitRegistry vanillaMvp() {
        return new MobTraitRegistry(Map.of(
                "minecraft:zombie", EnumSet.of(MobTrait.MELEE, MobTrait.TANKY, MobTrait.BREACH_CAPABLE),
                "minecraft:skeleton", EnumSet.of(MobTrait.RANGED),
                "minecraft:spider", EnumSet.of(MobTrait.MELEE, MobTrait.FAST, MobTrait.CLIMBING),
                "minecraft:creeper", EnumSet.of(MobTrait.EXPLOSIVE, MobTrait.STEALTHY, MobTrait.HIGH_VALUE)
        ), List.of());
    }

    public MobTraitRegistry withProvider(MobTraitProvider provider) {
        Objects.requireNonNull(provider, "provider");
        List<MobTraitProvider> next = new ArrayList<>(providers);
        next.add(provider);
        return new MobTraitRegistry(baseTraits, next);
    }

    public TraitResolution resolve(String entityTypeId) {
        EnumSet<MobTrait> traits = EnumSet.noneOf(MobTrait.class);
        traits.addAll(baseTraits.getOrDefault(entityTypeId, Set.of()));
        List<String> warnings = new ArrayList<>();
        for (MobTraitProvider provider : providers) {
            try {
                MobTraitProvider.TraitPatch patch = provider.traitsFor(entityTypeId);
                if (patch == null) {
                    warnings.add("Trait provider " + provider.id() + " returned null");
                    continue;
                }
                traits.removeAll(patch.remove());
                traits.addAll(patch.add());
            } catch (RuntimeException exception) {
                warnings.add("Trait provider " + provider.id() + " failed: " + exception.getClass().getSimpleName());
            }
        }
        return new TraitResolution(traits, warnings);
    }
}
