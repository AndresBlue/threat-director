package com.andresblue.threatdirector.apocalypse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonCombatPolicyTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void easyAimRemainsVanilla() {
        SkeletonCombatPolicy.AimVector aim = SkeletonCombatPolicy.solveAim(
                1,
                0, 1, 0,
                15, 2, 0,
                0, 0, 0.4);

        assertEquals(15.0, aim.x(), EPSILON);
        assertEquals(4.0, aim.y(), EPSILON);
        assertEquals(0.0, aim.z(), EPSILON);
        assertEquals(1.6F, aim.speed());
        assertEquals(10.0F, aim.divergence());
    }

    @Test
    void hardLeadsMovingTargetsMoreThanNormalAtRange() {
        SkeletonCombatPolicy.AimVector normal = SkeletonCombatPolicy.solveAim(
                2,
                0, 1, 0,
                20, 2, 0,
                0, 0, 0.25);
        SkeletonCombatPolicy.AimVector hard = SkeletonCombatPolicy.solveAim(
                3,
                0, 1, 0,
                20, 2, 0,
                0, 0, 0.25);

        assertTrue(normal.z() > 0.0);
        assertTrue(hard.z() > normal.z());
        assertEquals(3.0F, normal.divergence());
        assertEquals(0.25F, hard.divergence());
        assertEquals(1.8F, normal.speed());
        assertEquals(2.2F, hard.speed());
    }

    @Test
    void predictionDoesNotTurnShortRangeShotsIntoPerfectShots() {
        SkeletonCombatPolicy.AimVector hard = SkeletonCombatPolicy.solveAim(
                3,
                0, 1, 0,
                8, 2, 0,
                0, 0, 0.5);

        assertEquals(0.0, hard.z(), EPSILON);
        assertEquals(0.5F, hard.divergence());
    }

    @Test
    void normalOnlyGetsOccasionalSlowness() {
        assertEquals(
                SkeletonCombatPolicy.ArrowEffect.SLOWNESS,
                SkeletonCombatPolicy.arrowPayload(2, 0.05).effect());
        assertFalse(SkeletonCombatPolicy.arrowPayload(2, 0.20).appliesEffect());
        assertFalse(SkeletonCombatPolicy.arrowPayload(1, 0.01).appliesEffect());
    }

    @Test
    void hardRollsAcrossAllFourVanillaEffects() {
        assertFalse(SkeletonCombatPolicy.arrowPayload(3, 0.50).appliesEffect());
        assertEquals(
                SkeletonCombatPolicy.ArrowEffect.SLOWNESS,
                SkeletonCombatPolicy.arrowPayload(3, 0.70).effect());
        assertEquals(
                SkeletonCombatPolicy.ArrowEffect.POISON,
                SkeletonCombatPolicy.arrowPayload(3, 0.80).effect());
        assertEquals(
                SkeletonCombatPolicy.ArrowEffect.NAUSEA,
                SkeletonCombatPolicy.arrowPayload(3, 0.88).effect());
        assertEquals(
                SkeletonCombatPolicy.ArrowEffect.BLINDNESS,
                SkeletonCombatPolicy.arrowPayload(3, 0.96).effect());
    }

    @Test
    void dashRequiresHardGroundVisibilityCooldownAndCloseTarget() {
        assertTrue(SkeletonCombatPolicy.canDash(3, true, true, 0, 36.0));
        assertFalse(SkeletonCombatPolicy.canDash(2, true, true, 0, 36.0));
        assertFalse(SkeletonCombatPolicy.canDash(3, false, true, 0, 36.0));
        assertFalse(SkeletonCombatPolicy.canDash(3, true, false, 0, 36.0));
        assertFalse(SkeletonCombatPolicy.canDash(3, true, true, 1, 36.0));
        assertFalse(SkeletonCombatPolicy.canDash(3, true, true, 0, 64.0));
    }

    @Test
    void retreatDirectionIsNormalizedAndPointsAway() {
        SkeletonCombatPolicy.HorizontalDirection retreat =
                SkeletonCombatPolicy.retreatDirection(3, 4, 0, 0);

        assertEquals(0.6, retreat.x(), EPSILON);
        assertEquals(0.8, retreat.z(), EPSILON);
        assertEquals(1.0, Math.hypot(retreat.x(), retreat.z()), EPSILON);
    }

    @Test
    void difficultyControlsRangeWithoutChangingVanillaCadence() {
        assertEquals(15.0, SkeletonCombatPolicy.rangedAttackRange(1), EPSILON);
        assertEquals(24.0, SkeletonCombatPolicy.rangedAttackRange(2), EPSILON);
        assertEquals(42.0, SkeletonCombatPolicy.rangedAttackRange(3), EPSILON);
        assertEquals(40, SkeletonCombatPolicy.rangedAttackInterval(2));
        assertEquals(20, SkeletonCombatPolicy.rangedAttackInterval(3));
    }
}
