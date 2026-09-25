package com.andresblue.threatdirector.drowned;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrownedNightPolicyTest {
    @Test
    void easyAndDaytimeStayVanilla() {
        DrownedNightPolicy.Profile easyBloodMoon = DrownedNightPolicy.profile(1, true, true);
        DrownedNightPolicy.Profile hardDay = DrownedNightPolicy.profile(3, false, false);

        assertFalse(easyBloodMoon.enhanced());
        assertFalse(easyBloodMoon.breaksBoats());
        assertFalse(hardDay.enhanced());
        assertEquals(0.0, hardDay.tridentChance());
    }

    @Test
    void nightProfilesEscalateAndBloodMoonRestoresPeakPressure() {
        DrownedNightPolicy.Profile normal = DrownedNightPolicy.profile(2, true, false);
        DrownedNightPolicy.Profile hard = DrownedNightPolicy.profile(3, true, false);
        DrownedNightPolicy.Profile bloodMoon = DrownedNightPolicy.profile(3, true, true);

        assertTrue(normal.enhanced());
        assertTrue(normal.breaksBoats());
        assertTrue(hard.swimSpeedMultiplier() > normal.swimSpeedMultiplier());
        assertTrue(hard.tridentChance() > normal.tridentChance());
        assertTrue(bloodMoon.swimSpeedMultiplier() > hard.swimSpeedMultiplier());
        assertTrue(bloodMoon.riptideChance() >= 0.90);
        assertTrue(bloodMoon.dashCooldownTicks() < hard.dashCooldownTicks());
    }

    @Test
    void activeProfileDoesNotDependOnSwimSpeedToggle() {
        DrownedNightPolicy.Profile tridentOnly = new DrownedNightPolicy.Profile(
                1.0, 0.5, 0.0, 0, 0.0, 40, 56.0, false);

        assertTrue(tridentOnly.enhanced());
    }

    @Test
    void probabilityBoundaryIsExactAndRejectsInvalidSamples() {
        assertTrue(DrownedNightPolicy.acceptsRoll(0.599, 0.60));
        assertFalse(DrownedNightPolicy.acceptsRoll(0.60, 0.60));
        assertThrows(IllegalArgumentException.class,
                () -> DrownedNightPolicy.acceptsRoll(Double.NaN, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> DrownedNightPolicy.acceptsRoll(1.0, 0.5));
    }

    @Test
    void dashPointsAtTargetAndCannotCompoundPastItsCap() {
        DrownedNightPolicy.Velocity dash = DrownedNightPolicy.dashVelocity(
                50.0, 50.0, 50.0,
                4.0, 1.0, 0.0,
                0.80
        );
        double speed = Math.sqrt(
                dash.x() * dash.x() + dash.y() * dash.y() + dash.z() * dash.z());

        assertTrue(dash.x() > 0.0);
        assertTrue(speed <= 0.80 * 1.15 + 1.0E-9);
    }
}
