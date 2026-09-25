package com.andresblue.threatdirector.traits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TraitSelectionPolicyTest {
    @Test
    void minecraftDifficultyIdsMapToStableTraitTiers() {
        assertEquals(TraitDifficulty.EASY, TraitDifficulty.fromDifficultyId(0));
        assertEquals(TraitDifficulty.EASY, TraitDifficulty.fromDifficultyId(1));
        assertEquals(TraitDifficulty.NORMAL, TraitDifficulty.fromDifficultyId(2));
        assertEquals(TraitDifficulty.HARD, TraitDifficulty.fromDifficultyId(3));
    }

    @Test
    void rollsAreDeterministicBoundedAndNeverContradictory() {
        for (TraitDifficulty difficulty : TraitDifficulty.values()) {
            int maximum = TraitSelectionPolicy.profile(difficulty).maximumTraits();
            for (long seed = 0; seed < 10_000; seed++) {
                Set<MobTrait> first = TraitSelectionPolicy.select(difficulty, seed);
                assertEquals(first, TraitSelectionPolicy.select(difficulty, seed));
                assertTrue(first.size() <= maximum);
                assertFalse(first.contains(MobTrait.COWARD)
                        && first.contains(MobTrait.BERSERKER));
            }
        }
    }

    @Test
    void difficultyRaisesPrevalenceWhileCombinationsRemainRare() {
        int easy = countTraited(TraitDifficulty.EASY, 30_000);
        int normal = countTraited(TraitDifficulty.NORMAL, 30_000);
        int hard = countTraited(TraitDifficulty.HARD, 30_000);
        int hardCombinations = countCombinations(TraitDifficulty.HARD, 30_000);

        assertEquals(0, easy);
        assertTrue(easy < normal);
        assertTrue(normal < hard);
        assertTrue(hardCombinations > 0);
        assertTrue(hardCombinations < hard / 3);
    }

    @Test
    void codecIgnoresUnrelatedAndUnknownTags() {
        Set<String> tags = Set.of(
                "some_other_mod.flag",
                TraitTagCodec.encode(MobTrait.LEADER),
                TraitTagCodec.TRAIT_PREFIX + "future_trait");

        assertEquals(Set.of(MobTrait.LEADER), TraitTagCodec.decode(tags));
    }

    private static int countTraited(TraitDifficulty difficulty, int samples) {
        int count = 0;
        for (long seed = 0; seed < samples; seed++) {
            if (!TraitSelectionPolicy.select(difficulty, seed).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static int countCombinations(TraitDifficulty difficulty, int samples) {
        int count = 0;
        for (long seed = 0; seed < samples; seed++) {
            if (TraitSelectionPolicy.select(difficulty, seed).size() > 1) {
                count++;
            }
        }
        return count;
    }
}
