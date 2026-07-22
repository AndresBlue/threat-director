package com.andresblue.threatdirector.creeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.HorizontalVelocity;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Mode;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Profile;
import org.junit.jupiter.api.Test;

class ApocalypticCreeperPolicyTest {
    @Test
    void profilesKeepEasyVanillaAndReserveFullKitForHard() {
        Profile easy = ApocalypticCreeperPolicy.profile(Mode.VANILLA);
        Profile normal = ApocalypticCreeperPolicy.profile(Mode.PARTIAL);
        Profile hard = ApocalypticCreeperPolicy.profile(Mode.APOCALYPTIC);

        assertEquals(1.0D, easy.rearApproachMultiplier());
        assertEquals(1.0D, easy.minimumFuseRatio());
        assertFalse(easy.silentSteps());
        assertFalse(easy.dashEnabled());
        assertFalse(easy.wallFuseEnabled());

        assertEquals(1.35D, normal.rearApproachMultiplier());
        assertEquals(0.70D, normal.minimumFuseRatio());
        assertFalse(normal.silentSteps());
        assertFalse(normal.dashEnabled());
        assertFalse(normal.wallFuseEnabled());

        assertEquals(2.0D, hard.rearApproachMultiplier());
        assertEquals(0.30D, hard.minimumFuseRatio());
        assertTrue(hard.silentSteps());
        assertTrue(hard.dashEnabled());
        assertTrue(hard.wallFuseEnabled());
    }

    @Test
    void facingAwayUsesOnlyHorizontalOrientation() {
        assertTrue(ApocalypticCreeperPolicy.targetFacesAway(1.0D, 0.0D, -4.0D, 0.0D));
        assertFalse(ApocalypticCreeperPolicy.targetFacesAway(1.0D, 0.0D, 4.0D, 0.0D));
        assertFalse(ApocalypticCreeperPolicy.targetFacesAway(1.0D, 0.0D, 0.0D, 4.0D));
        assertFalse(ApocalypticCreeperPolicy.targetFacesAway(0.0D, 0.0D, -4.0D, 0.0D));
    }

    @Test
    void fuseRangesAreInclusiveAndEasyRemainsDefault() {
        Profile easy = ApocalypticCreeperPolicy.profile(Mode.VANILLA);
        Profile normal = ApocalypticCreeperPolicy.profile(Mode.PARTIAL);
        Profile hard = ApocalypticCreeperPolicy.profile(Mode.APOCALYPTIC);

        assertEquals(30, ApocalypticCreeperPolicy.randomizedFuseTicks(easy, 30, 0.0D));
        assertEquals(21, ApocalypticCreeperPolicy.randomizedFuseTicks(normal, 30, 0.0D));
        assertEquals(30, ApocalypticCreeperPolicy.randomizedFuseTicks(normal, 30, 1.0D));
        assertEquals(9, ApocalypticCreeperPolicy.randomizedFuseTicks(hard, 30, 0.0D));
        assertEquals(30, ApocalypticCreeperPolicy.randomizedFuseTicks(hard, 30, 1.0D));
    }

    @Test
    void shortenedFuseProgressReachesVanillaThresholdOnSelectedTick() {
        assertEquals(3, ApocalypticCreeperPolicy.minimumFuseProgressBeforeVanillaTick(30, 9, 1));
        assertEquals(26, ApocalypticCreeperPolicy.minimumFuseProgressBeforeVanillaTick(30, 9, 8));
        assertEquals(29, ApocalypticCreeperPolicy.minimumFuseProgressBeforeVanillaTick(30, 9, 9));
        assertEquals(0, ApocalypticCreeperPolicy.minimumFuseProgressBeforeVanillaTick(30, 30, 1));
    }

    @Test
    void wallFuseRequiresHardGriefingObstructionAndShortRange() {
        Profile hard = ApocalypticCreeperPolicy.profile(Mode.APOCALYPTIC);

        assertTrue(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, true, false, true, false, 16.0D));
        assertFalse(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, false, false, true, false, 16.0D));
        assertFalse(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, true, true, true, false, 16.0D));
        assertFalse(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, true, false, false, false, 16.0D));
        assertFalse(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, true, false, true, true, 16.0D));
        assertFalse(ApocalypticCreeperPolicy.isWallFuseCandidate(
                hard, true, true, false, true, false, 25.0D));
    }

    @Test
    void dashRequiresHardRearExposureAndCapsHorizontalVelocity() {
        Profile normal = ApocalypticCreeperPolicy.profile(Mode.PARTIAL);
        Profile hard = ApocalypticCreeperPolicy.profile(Mode.APOCALYPTIC);

        assertFalse(ApocalypticCreeperPolicy.shouldDash(
                normal, true, true, true, true, false, 0, 36.0D));
        assertTrue(ApocalypticCreeperPolicy.shouldDash(
                hard, true, true, true, true, false, 0, 36.0D));
        assertFalse(ApocalypticCreeperPolicy.shouldDash(
                hard, true, true, false, true, false, 0, 36.0D));
        assertFalse(ApocalypticCreeperPolicy.shouldDash(
                hard, true, true, true, true, false, 1, 36.0D));

        HorizontalVelocity velocity = ApocalypticCreeperPolicy.boundedDashVelocity(
                0.60D, 0.0D, 5.0D, 0.0D);
        assertTrue(Math.hypot(velocity.x(), velocity.z())
                <= ApocalypticCreeperPolicy.DASH_MAX_HORIZONTAL_SPEED + 1.0E-9D);
        assertTrue(velocity.x() > 0.0D);
    }
}
