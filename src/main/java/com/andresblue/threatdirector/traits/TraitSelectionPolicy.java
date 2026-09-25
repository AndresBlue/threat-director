package com.andresblue.threatdirector.traits;

import com.andresblue.threatdirector.config.ConfigAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Pure, deterministic and bounded trait selection. */
public final class TraitSelectionPolicy {
    private static final SelectionProfile EASY = new SelectionProfile(0.0D, 0.0D, 0.0D, 1);
    private static final SelectionProfile NORMAL = new SelectionProfile(0.075D, 0.14D, 0.0D, 2);
    private static final SelectionProfile HARD = new SelectionProfile(0.14D, 0.22D, 0.05D, 3);

    private TraitSelectionPolicy() {
    }

    public static SelectionProfile profile(TraitDifficulty difficulty) {
        if (ConfigAccess.current().moddedEnabled()
                && ConfigAccess.current().modded().traits().enabled()
                && difficulty != TraitDifficulty.EASY) {
            var configured = difficulty == TraitDifficulty.HARD
                    ? ConfigAccess.current().modded().traits().hard()
                    : ConfigAccess.current().modded().traits().normal();
            return new SelectionProfile(
                    configured.anyTraitChance(), configured.secondTraitChance(),
                    configured.thirdTraitChance(), configured.maximumTraits());
        }
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case EASY -> EASY;
            case NORMAL -> NORMAL;
            case HARD -> HARD;
        };
    }

    /** Stable for a seed, allowing a mob UUID/world seed pair to reproduce its first roll. */
    public static Set<MobTrait> select(TraitDifficulty difficulty, long seed) {
        return select(difficulty, new Random(mix64(seed)));
    }

    public static Set<MobTrait> select(TraitDifficulty difficulty, RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        SelectionProfile profile = profile(difficulty);
        if (random.nextDouble() >= profile.anyTraitChance()) {
            return Set.of();
        }

        int requested = 1;
        if (requested < profile.maximumTraits()
                && random.nextDouble() < profile.secondTraitChance()) {
            requested++;
        }
        if (requested < profile.maximumTraits()
                && random.nextDouble() < profile.thirdTraitChance()) {
            requested++;
        }

        EnumSet<MobTrait> selected = EnumSet.noneOf(MobTrait.class);
        var toggles = ConfigAccess.current().modded().traits();
        List<MobTrait> candidates = new ArrayList<>(List.of(MobTrait.values()));
        candidates.removeIf(trait -> switch (trait) {
            case COWARD -> !toggles.cowardEnabled();
            case BERSERKER -> !toggles.berserkerEnabled();
            case LEADER -> !toggles.leaderEnabled();
            case REGENERATIVE -> !toggles.regenerativeEnabled();
            case SWIFT -> !toggles.swiftEnabled();
            case RESILIENT -> !toggles.resilientEnabled();
        });
        while (selected.size() < requested && !candidates.isEmpty()) {
            MobTrait chosen = removeWeighted(candidates, random);
            if (selected.stream().allMatch(existing -> existing.isCompatibleWith(chosen))) {
                selected.add(chosen);
            }
        }
        return Collections.unmodifiableSet(selected);
    }

    private static MobTrait removeWeighted(List<MobTrait> candidates, RandomGenerator random) {
        int totalWeight = candidates.stream().mapToInt(MobTrait::selectionWeight).sum();
        int roll = random.nextInt(totalWeight);
        for (int index = 0; index < candidates.size(); index++) {
            MobTrait candidate = candidates.get(index);
            roll -= candidate.selectionWeight();
            if (roll < 0) {
                candidates.remove(index);
                return candidate;
            }
        }
        throw new IllegalStateException("weighted trait roll exhausted its candidates");
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public record SelectionProfile(double anyTraitChance, double secondTraitChance,
                                   double thirdTraitChance, int maximumTraits) {
        public SelectionProfile {
            requireProbability(anyTraitChance, "anyTraitChance");
            requireProbability(secondTraitChance, "secondTraitChance");
            requireProbability(thirdTraitChance, "thirdTraitChance");
            if (maximumTraits < 0 || maximumTraits > 3) {
                throw new IllegalArgumentException("maximumTraits must be in [0, 3]");
            }
        }

        private static void requireProbability(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
                throw new IllegalArgumentException(name + " must be in [0, 1]");
            }
        }
    }
}
