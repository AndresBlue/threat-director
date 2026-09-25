package com.andresblue.threatdirector.apocalypse.other;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.AbilityRules;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Family;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.HorizontalVelocity;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Tier;
import org.junit.jupiter.api.Test;

class OtherHostilePolicyTest {
    @Test
    void easyIsVanillaAndBloodMoonRestoresHardTargetingPeak() {
        assertFalse(OtherHostilePolicy.pressure(Tier.EASY).enabled());

        var normal = OtherHostilePolicy.pressure(Tier.NORMAL);
        assertTrue(normal.enabled());
        assertEquals(28.0D, normal.minimumTargetRange());
        assertTrue(normal.requiresVisibility());

        var hard = OtherHostilePolicy.pressure(Tier.HARD);
        assertTrue(hard.enabled());
        assertEquals(40.0D, hard.minimumTargetRange());
        assertTrue(hard.requiresVisibility());
        assertTrue(hard.unseenMemoryTicks() > normal.unseenMemoryTicks());

        var bloodMoon = OtherHostilePolicy.pressure(Tier.HARD, true);
        assertEquals(48.0D, bloodMoon.minimumTargetRange());
        assertFalse(bloodMoon.requiresVisibility());
        assertTrue(bloodMoon.unseenMemoryTicks() > hard.unseenMemoryTicks());
    }

    @Test
    void hardEnablesEveryNamedFamilyWhileOtherRemainsGeneric() {
        for (Family family : Family.values()) {
            AbilityRules rules = OtherHostilePolicy.ability(Tier.HARD, family);
            assertEquals(family != Family.OTHER, rules.enabled(), family.name());
        }
    }

    @Test
    void normalKeepsBruteChargeDisabledAndUsesSmallerSwarmCaps() {
        assertFalse(OtherHostilePolicy.ability(Tier.NORMAL, Family.BRUTE).enabled());
        assertTrue(OtherHostilePolicy.ability(Tier.HARD, Family.BRUTE).enabled());
        assertTrue(OtherHostilePolicy.ability(Tier.NORMAL, Family.SWARM).maximumAllies()
                < OtherHostilePolicy.ability(Tier.HARD, Family.SWARM).maximumAllies());
    }

    @Test
    void visibleAbilitiesRespectTheirDistanceWindow() {
        AbilityRules aerial = OtherHostilePolicy.ability(Tier.HARD, Family.AERIAL_FIRE);

        assertTrue(OtherHostilePolicy.canUseVisibleRangedAbility(
                aerial, true, true, 100.0D));
        assertFalse(OtherHostilePolicy.canUseVisibleRangedAbility(
                aerial, true, false, 100.0D));
        assertFalse(OtherHostilePolicy.canUseVisibleRangedAbility(
                aerial, true, true, 16.0D));
        assertFalse(OtherHostilePolicy.canUseVisibleRangedAbility(
                aerial, true, true, 625.0D));
    }

    @Test
    void approachImpulseNeverExceedsFamilySpeedCap() {
        AbilityRules brute = OtherHostilePolicy.ability(Tier.HARD, Family.BRUTE);
        HorizontalVelocity velocity = OtherHostilePolicy.boundedApproachVelocity(
                brute, 0.70D, 0.0D, 10.0D, 0.0D);

        assertTrue(Math.hypot(velocity.x(), velocity.z())
                <= brute.maximumHorizontalSpeed() + 1.0E-9D);
        assertTrue(velocity.x() > 0.0D);
    }

    @Test
    void cooldownsAndAllyCapsStayBounded() {
        for (Tier tier : Tier.values()) {
            for (Family family : Family.values()) {
                AbilityRules rules = OtherHostilePolicy.ability(tier, family);
                assertTrue(rules.cooldownTicks() >= 0);
                assertTrue(rules.retryTicks() >= 1);
                assertTrue(rules.maximumAllies() <= 4);
                if (rules.enabled()) {
                    assertTrue(rules.cooldownTicks() >= 70);
                }
            }
        }
    }
}
