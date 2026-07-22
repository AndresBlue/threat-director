package com.andresblue.threatdirector.difficulty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyAbilityPolicyTest {
    @Test
    void easyAndPeacefulRemainVanilla() {
        assertEquals(DifficultyAbilityPolicy.Tier.EASY,
                DifficultyAbilityPolicy.hostileTierFromDifficultyId(0));
        assertEquals(DifficultyAbilityPolicy.Tier.EASY,
                DifficultyAbilityPolicy.hostileTierFromDifficultyId(1));

        DifficultyAbilityPolicy.AbilityProfile easy = DifficultyAbilityPolicy.profile(
                DifficultyAbilityPolicy.Tier.EASY);
        assertEquals(0, easy.zombie().meleeToolChoices());
        assertEquals(0, easy.zombie().maxReinforcements());
        assertEquals(0, easy.zombie().blockBreakCooldownTicks());
        assertEquals(-1, easy.spider().speedAmplifier());
        assertFalse(easy.spider().cobwebOnHit());
        assertEquals(0, easy.enderman().maxAlliesAlerted());
        assertFalse(easy.enderman().twoBlockCombatPosture());
    }

    @Test
    void normalIsPartialAndHardEnablesTheFullSet() {
        DifficultyAbilityPolicy.AbilityProfile normal = DifficultyAbilityPolicy.profile(
                DifficultyAbilityPolicy.Tier.NORMAL);
        DifficultyAbilityPolicy.AbilityProfile hard = DifficultyAbilityPolicy.profile(
                DifficultyAbilityPolicy.Tier.HARD);

        assertTrue(normal.zombie().equipmentChance() > 0.0);
        assertEquals(0, normal.zombie().maxReinforcements());
        assertEquals(0, normal.zombie().blockBreakCooldownTicks());
        assertTrue(normal.spider().speedAmplifier() >= 0);
        assertFalse(normal.spider().cobwebOnHit());
        assertTrue(normal.enderman().maxAlliesAlerted() > 0);
        assertFalse(normal.enderman().twoBlockCombatPosture());

        assertTrue(hard.zombie().shieldAndPickaxe());
        assertTrue(hard.zombie().maxReinforcements() <= 2);
        assertTrue(hard.zombie().blockBreakCooldownTicks() >= 20);
        assertTrue(hard.spider().cobwebOnHit());
        assertTrue(hard.spider().hatchlingCount() <= 2);
        assertTrue(hard.enderman().twoBlockCombatPosture());
        assertTrue(hard.enderman().maxAlliesAlerted() <= 6);
    }

    @Test
    void rollsAndSpawnCountsHaveExplicitBounds() {
        assertTrue(DifficultyAbilityPolicy.acceptsRoll(0.19, 0.20));
        assertFalse(DifficultyAbilityPolicy.acceptsRoll(0.20, 0.20));
        assertThrows(IllegalArgumentException.class,
                () -> DifficultyAbilityPolicy.acceptsRoll(1.0, 0.5));

        assertEquals(2, DifficultyAbilityPolicy.boundedSpawnCount(5, 2));
        assertEquals(0, DifficultyAbilityPolicy.boundedSpawnCount(-1, 2));
        assertEquals(0, DifficultyAbilityPolicy.boundedSpawnCount(1, -2));
    }

    @Test
    void antiPillarGeometryRequiresCloseGroundedAndElevatedTarget() {
        assertTrue(DifficultyAbilityPolicy.hasPillarAdvantage(15.99, 2.25, true));
        assertFalse(DifficultyAbilityPolicy.hasPillarAdvantage(16.01, 2.25, true));
        assertFalse(DifficultyAbilityPolicy.hasPillarAdvantage(4.0, 2.24, true));
        assertFalse(DifficultyAbilityPolicy.hasPillarAdvantage(4.0, 3.0, false));
    }

    @Test
    void toolRolesExposeTheirExactBlockFamilies() {
        assertTrue(ZombieToolRole.SHOVEL.prefers(ZombieToolRole.BlockFamily.SHOVEL_MINEABLE));
        assertTrue(ZombieToolRole.AXE.prefers(ZombieToolRole.BlockFamily.AXE_MINEABLE));
        assertTrue(ZombieToolRole.SWORD.prefers(ZombieToolRole.BlockFamily.SWORD_EFFICIENT));
        assertTrue(ZombieToolRole.PICKAXE.prefers(ZombieToolRole.BlockFamily.PICKAXE_MINEABLE));
        assertFalse(ZombieToolRole.NONE.prefers(ZombieToolRole.BlockFamily.PICKAXE_MINEABLE));
        assertFalse(ZombieToolRole.AXE.prefers(ZombieToolRole.BlockFamily.OTHER));
    }
}
