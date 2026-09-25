package com.andresblue.threatdirector.awareness;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwarenessPolicyTest {
    @Test
    void assignmentIsDeterministicAndDifficultyIncreasesItsRate() {
        assertEquals(
                AwarenessPolicy.roll(314159L, 3),
                AwarenessPolicy.roll(314159L, 3)
        );

        int easySenses = 0;
        int hardSenses = 0;
        for (long seed = 0; seed < 10_000; seed++) {
            easySenses += AwarenessPolicy.roll(seed, 1).senses().size();
            hardSenses += AwarenessPolicy.roll(seed, 3).senses().size();
        }
        assertEquals(0, easySenses);
        assertTrue(hardSenses > easySenses);
        assertTrue(AwarenessPolicy.roll(1L, 0).isEmpty());
    }

    @Test
    void independentRollsProduceNoSenseSingleSenseAndCombinations() {
        boolean sawNone = false;
        boolean sawSingle = false;
        boolean sawCombination = false;
        boolean sawAll = false;
        for (long seed = 0; seed < 100_000 && !sawAll; seed++) {
            int count = AwarenessPolicy.roll(seed, 3).senses().size();
            sawNone |= count == 0;
            sawSingle |= count == 1;
            sawCombination |= count >= 2;
            sawAll |= count == 3;
        }
        assertTrue(sawNone);
        assertTrue(sawSingle);
        assertTrue(sawCombination);
        assertTrue(sawAll);
    }

    @Test
    void movementRequiresTheSenseMotionVisibilityAndRange() {
        AwarenessProfile movement = new AwarenessProfile(Set.of(AwarenessSense.MOVEMENT));
        assertTrue(AwarenessPolicy.detectsMovement(movement,
                stimulus(20.0, 0.01, false, false, 0, true)));
        assertTrue(AwarenessPolicy.detectsMovement(movement,
                stimulus(31.0, 0.02, true, false, 0, true)));
        assertFalse(AwarenessPolicy.detectsMovement(movement,
                stimulus(25.0, 0.01, false, false, 0, true)));
        assertFalse(AwarenessPolicy.detectsMovement(movement,
                stimulus(5.0, 0.0, false, false, 0, true)));
        assertFalse(AwarenessPolicy.detectsMovement(movement,
                stimulus(5.0, 0.01, false, false, 0, false)));
        assertFalse(AwarenessPolicy.detectsMovement(AwarenessProfile.none(),
                stimulus(5.0, 0.01, false, false, 0, true)));
    }

    @Test
    void sneakingReducesMovementRangeAndBrightLightExtendsLightDetection() {
        AwarenessProfile movement = new AwarenessProfile(Set.of(AwarenessSense.MOVEMENT));
        assertFalse(AwarenessPolicy.detectsMovement(movement,
                stimulus(11.0, 0.01, false, true, 0, true)));
        assertTrue(AwarenessPolicy.detectsMovement(movement,
                stimulus(10.0, 0.01, false, true, 0, true)));

        AwarenessProfile light = new AwarenessProfile(Set.of(AwarenessSense.LIGHT));
        assertFalse(AwarenessPolicy.detectsLight(light,
                stimulus(5.0, 0.0, false, false, 7, true)));
        assertTrue(AwarenessPolicy.detectsLight(light,
                stimulus(12.0, 0.0, false, false, 8, true)));
        assertTrue(AwarenessPolicy.detectsLight(light,
                stimulus(29.0, 0.0, false, false, 15, true)));
        assertFalse(AwarenessPolicy.detectsLight(light,
                stimulus(30.0, 0.0, false, false, 15, true)));
    }

    @Test
    void realServerGameEventsMapToBoundedSoundFamilies() {
        assertEquals(AwarenessSoundType.MINING,
                AwarenessPolicy.classifyGameEvent("minecraft:block_destroy").orElseThrow().type());
        assertEquals(AwarenessSoundType.EXPLOSION,
                AwarenessPolicy.classifyGameEvent("explode").orElseThrow().type());
        assertEquals(AwarenessSoundType.DOOR,
                AwarenessPolicy.classifyGameEvent("block_open").orElseThrow().type());
        assertEquals(AwarenessSoundType.CONTAINER,
                AwarenessPolicy.classifyGameEvent("container_close").orElseThrow().type());
        assertEquals(AwarenessSoundType.COMBAT,
                AwarenessPolicy.classifyGameEvent("entity_damage").orElseThrow().type());
        assertTrue(AwarenessPolicy.classifyGameEvent("step").isEmpty());

        AwarenessProfile sound = new AwarenessProfile(Set.of(AwarenessSense.SOUND));
        AwarenessPolicy.SoundRules explosion = AwarenessPolicy
                .classifyGameEvent("explode").orElseThrow();
        assertTrue(AwarenessPolicy.canHear(sound, explosion, 64.0 * 64.0, 120));
        assertFalse(AwarenessPolicy.canHear(sound, explosion, 64.1 * 64.1, 20));
        assertFalse(AwarenessPolicy.canHear(sound, explosion, 10.0, 121));
        assertFalse(AwarenessPolicy.canHear(AwarenessProfile.none(), explosion, 10.0, 1));
    }

    @Test
    void stableTagsRoundTripEveryCombination() {
        AwarenessProfile profile = new AwarenessProfile(EnumSet.of(
                AwarenessSense.MOVEMENT,
                AwarenessSense.SOUND,
                AwarenessSense.LIGHT
        ));
        Set<String> tags = AwarenessTags.encode(profile);
        assertTrue(tags.contains(AwarenessTags.ASSIGNED));
        assertEquals(profile, AwarenessTags.decode(tags));

        Set<String> emptyTags = AwarenessTags.encode(AwarenessProfile.none());
        assertEquals(Set.of(AwarenessTags.ASSIGNED), emptyTags);
        assertEquals(AwarenessProfile.none(), AwarenessTags.decode(emptyTags));
    }

    @Test
    void soundBufferHonorsConfiguredLifetimesLongerThanDefault() {
        AwarenessSoundBuffer buffer = new AwarenessSoundBuffer();
        AwarenessProfile sound = new AwarenessProfile(Set.of(AwarenessSense.SOUND));
        AwarenessPolicy.SoundRules longMemory = new AwarenessPolicy.SoundRules(
                AwarenessSoundType.COMBAT, 16.0, 200, 8.0);
        buffer.record(longMemory, BlockPos.ORIGIN, null, 0L);

        assertTrue(buffer.latestHeard(sound, BlockPos.ORIGIN, -1L, 150L).isPresent());
        assertTrue(buffer.latestHeard(sound, BlockPos.ORIGIN, -1L, 200L).isPresent());
        assertTrue(buffer.latestHeard(sound, BlockPos.ORIGIN, -1L, 201L).isEmpty());
    }

    private static AwarenessPolicy.PlayerStimulus stimulus(
            double distance, double speedSquared, boolean sprinting,
            boolean sneaking, int light, boolean lineOfSight
    ) {
        return new AwarenessPolicy.PlayerStimulus(
                distance * distance,
                speedSquared,
                sprinting,
                sneaking,
                light,
                lineOfSight
        );
    }
}
