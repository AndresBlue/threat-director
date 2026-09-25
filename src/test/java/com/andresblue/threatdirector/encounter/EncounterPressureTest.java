package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterPressureTest {
    private static final EncounterPressure.Context SURFACE_EASY =
            new EncounterPressure.Context(Difficulty.EASY, false, false, false);
    private static final EncounterPressure.Context SURFACE_NORMAL =
            new EncounterPressure.Context(Difficulty.NORMAL, false, false, false);
    private static final EncounterPressure.Context SURFACE_HARD =
            new EncounterPressure.Context(Difficulty.HARD, false, false, false);
    private static final EncounterPressure.Context HARD_CAVE_NIGHT =
            new EncounterPressure.Context(Difficulty.HARD, true, true, true);
    private static final EncounterPressure.Context HARD_BLOOD_MOON =
            new EncounterPressure.Context(Difficulty.HARD, true, true, false, true);

    @Test
    void difficultyProducesStrictlyIncreasingSurfacePressure() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        EncounterPressure easy = EncounterPressure.calculate(config, 60.0, false, SURFACE_EASY);
        EncounterPressure normal = EncounterPressure.calculate(config, 60.0, false, SURFACE_NORMAL);
        EncounterPressure hard = EncounterPressure.calculate(config, 60.0, false, SURFACE_HARD);

        assertTrue(easy.hazard() < normal.hazard());
        assertTrue(normal.hazard() < hard.hazard());
        assertTrue(easy.effectiveIntensity() < normal.effectiveIntensity());
        assertTrue(normal.effectiveIntensity() < hard.effectiveIntensity());
        assertTrue(easy.encounterChance() < normal.encounterChance());
        assertTrue(normal.encounterChance() < hard.encounterChance());
        assertEquals(0.0, easy.encounterChance(), 1.0e-9);
        assertEquals(0.075, normal.encounterChance(), 1.0e-9);
        assertEquals(0.165, hard.encounterChance(), 1.0e-9);
        assertTrue(easy.mobCap() < normal.mobCap());
        assertTrue(normal.mobCap() < hard.mobCap());
        assertEquals(6, easy.mobCap());
        assertEquals(32, normal.mobCap());
        assertEquals(45, hard.mobCap());
        assertTrue(easy.minimumMobs() < normal.minimumMobs());
        assertTrue(normal.minimumMobs() < hard.minimumMobs());
        assertEquals(4, easy.minimumMobs());
        assertEquals(8, normal.minimumMobs());
        assertEquals(12, hard.minimumMobs());
        assertTrue(easy.meleeAttackers() < normal.meleeAttackers());
        assertTrue(normal.meleeAttackers() < hard.meleeAttackers());
        assertEquals(2, easy.meleeAttackers());
        assertEquals(6, normal.meleeAttackers());
        assertEquals(9, hard.meleeAttackers());
        assertTrue(easy.replanIntervalTicks() > normal.replanIntervalTicks());
        assertTrue(normal.replanIntervalTicks() > hard.replanIntervalTicks());
        assertTrue(easy.reinforcementSize() < normal.reinforcementSize());
        assertTrue(normal.reinforcementSize() < hard.reinforcementSize());
        assertEquals(0, easy.reinforcementSize());
        assertEquals(3, normal.reinforcementSize());
        assertEquals(6, hard.reinforcementSize());
        assertTrue(normal.reinforcementIntervalTicks() > hard.reinforcementIntervalTicks());
        assertEquals(260, normal.reinforcementIntervalTicks());
        assertEquals(200, hard.reinforcementIntervalTicks());
        assertTrue(easy.cooldownMultiplier() > normal.cooldownMultiplier());
        assertTrue(normal.cooldownMultiplier() > hard.cooldownMultiplier());
        assertEquals(1.5, easy.cooldownMultiplier());
        assertEquals(1.15, normal.cooldownMultiplier());
        assertEquals(0.75, hard.cooldownMultiplier());
        assertEquals(0, easy.maximumReinforcementWaves());
        assertEquals(3, normal.maximumReinforcementWaves());
        assertEquals(5, hard.maximumReinforcementWaves());
        assertEquals("easy:surface", easy.zoneLabel());
        assertEquals("normal:surface", normal.zoneLabel());
        assertEquals("hard:surface", hard.zoneLabel());
    }

    @Test
    void environmentalSignalsAccumulateIntoBoundedHazard() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        EncounterPressure surface = EncounterPressure.calculate(
                config, 50.0, false, SURFACE_NORMAL);
        EncounterPressure night = EncounterPressure.calculate(
                config,
                50.0,
                false,
                new EncounterPressure.Context(Difficulty.NORMAL, true, false, false));
        EncounterPressure dark = EncounterPressure.calculate(
                config,
                50.0,
                false,
                new EncounterPressure.Context(Difficulty.NORMAL, true, true, false));
        EncounterPressure cave = EncounterPressure.calculate(
                config,
                50.0,
                false,
                new EncounterPressure.Context(Difficulty.NORMAL, true, true, true));

        assertTrue(surface.hazard() < night.hazard());
        assertTrue(night.hazard() < dark.hazard());
        assertTrue(dark.hazard() < cave.hazard());
        assertTrue(surface.effectiveIntensity() < cave.effectiveIntensity());
        assertEquals("normal:night+dark+cave", cave.zoneLabel());
        assertTrue(cave.hazard() <= 1.0);
        assertTrue(cave.mobCap() <= config.encounter().hardMaxMobs());
    }

    @Test
    void explicitIntensityOverrideIsNeverRaisedByDifficultyOrEnvironment() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        PlayerDirectorState state = new PlayerDirectorState(UUID.randomUUID());
        state.setIntensityOverride(20.0);

        EncounterPressure forced = EncounterPressure.calculate(config, state, HARD_CAVE_NIGHT);
        EncounterPressure adaptive = EncounterPressure.calculate(
                config, 20.0, false, HARD_CAVE_NIGHT);

        assertEquals(1.0, forced.hazard());
        assertEquals(20.0, forced.effectiveIntensity());
        assertTrue(adaptive.effectiveIntensity() > forced.effectiveIntensity());
        assertEquals(61, forced.mobCap());
        assertEquals(16, forced.minimumMobs());
        assertEquals(13, forced.meleeAttackers());
        assertEquals(7, forced.reinforcementSize());
        assertEquals(5, forced.maximumReinforcementWaves());
        assertEquals(140, forced.reinforcementIntervalTicks());
        assertEquals(0.50, forced.cooldownMultiplier());
    }

    @Test
    void bloodMoonRestoresTheFormerApocalypsePeak() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        EncounterPressure ordinary = EncounterPressure.calculate(
                config, 100.0, false,
                new EncounterPressure.Context(Difficulty.HARD, true, true, false));
        EncounterPressure bloodMoon = EncounterPressure.calculate(
                config, 100.0, false, HARD_BLOOD_MOON);

        assertTrue(bloodMoon.encounterChance() > ordinary.encounterChance());
        assertTrue(bloodMoon.mobCap() > ordinary.mobCap());
        assertEquals(10, bloodMoon.maximumReinforcementWaves());
        assertEquals(0.23, bloodMoon.cooldownMultiplier(), 1.0e-9);
        assertTrue(bloodMoon.zoneLabel().contains("blood_moon"));
    }

    @Test
    void peacefulContextDisablesEncounterPopulationAndReinforcements() {
        ThreatDirectorConfig config = ThreatDirectorConfig.vanillaPlus();
        EncounterPressure pressure = EncounterPressure.calculate(
                config,
                80.0,
                false,
                new EncounterPressure.Context(Difficulty.PEACEFUL, true, true, true));

        assertEquals(0.0, pressure.hazard());
        assertEquals(0.0, pressure.encounterChance());
        assertEquals(0, pressure.mobCap());
        assertEquals(0, pressure.minimumMobs());
        assertEquals(0, pressure.meleeAttackers());
        assertEquals(0, pressure.reinforcementSize());
        assertEquals(0, pressure.maximumReinforcementWaves());
        assertEquals(0, pressure.reinforcementIntervalTicks());
        assertEquals("peaceful:night+dark+cave", pressure.zoneLabel());
    }
}
