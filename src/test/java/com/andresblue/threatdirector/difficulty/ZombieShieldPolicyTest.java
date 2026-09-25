package com.andresblue.threatdirector.difficulty;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieShieldPolicyTest {
    @Test
    void onlyFrontFacingAttacksCanBeBlocked() {
        assertTrue(ZombieShieldPolicy.isAttackInFront(0.0, 1.0, 0.0, 4.0));
        assertTrue(ZombieShieldPolicy.isAttackInFront(0.0, 1.0, 1.0, 0.0));
        assertFalse(ZombieShieldPolicy.isAttackInFront(0.0, 1.0, 0.0, -4.0));
        assertFalse(ZombieShieldPolicy.isAttackInFront(0.0, 0.0, 0.0, 1.0));
    }

    @Test
    void axeAndCooldownAlwaysDefeatTheShield() {
        assertFalse(ZombieShieldPolicy.shouldBlock(
                true, false, false, true, true, 1.0, 0.0));
        assertFalse(ZombieShieldPolicy.shouldBlock(
                true, true, false, true, false, 1.0, 0.0));
        assertFalse(ZombieShieldPolicy.shouldBlock(
                true, false, true, true, false, 1.0, 0.0));
        assertTrue(ZombieShieldPolicy.shouldBlock(
                true, false, false, true, false, 0.42, 0.419));
        assertFalse(ZombieShieldPolicy.shouldBlock(
                true, false, false, true, false, 0.42, 0.42));
    }
}
