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
        assertEquals(0.03875, easy.encounterChance(), 1.0e-9);
        assertEquals(0.095, normal.encounterChance(), 1.0e-9);
        assertEquals(0.18875, hard.encounterChance(), 1.0e-9);
        assertTrue(easy.mobCap() < normal.mobCap());
        assertTrue(normal.mobCap() < hard.mobCap());
        assertTrue(easy.minimumMobs() < normal.minimumMobs());
        assertTrue(normal.minimumMobs() < hard.minimumMobs());
        assertTrue(easy.meleeAttackers() < normal.meleeAttackers());
        assertTrue(normal.meleeAttackers() < hard.meleeAttackers());
        assertEquals(3, easy.meleeAttackers());
        assertEquals(4, normal.meleeAttackers());
        assertEquals(5, hard.meleeAttackers());
        assertTrue(easy.replanIntervalTicks() > normal.replanIntervalTicks());
        assertTrue(normal.replanIntervalTicks() > hard.replanIntervalTicks());
        assertTrue(easy.reinforcementSize() < normal.reinforcementSize());
        assertTrue(normal.reinforcementSize() < hard.reinforcementSize());
        assertTrue(normal.reinforcementIntervalTicks() > hard.reinforcementIntervalTicks());
        assertTrue(easy.cooldownMultiplier() > normal.cooldownMultiplier());
        assertTrue(normal.cooldownMultiplier() > hard.cooldownMultiplier());
        assertEquals(1.0, easy.cooldownMultiplier());
        assertEquals(0.75, normal.cooldownMultiplier());
        assertEquals(0.50, hard.cooldownMultiplier());
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
        assertEquals(config.encounter().hardMaxMobs(), forced.mobCap());
        assertEquals(3, forced.reinforcementSize());
        assertEquals(0.25, forced.cooldownMultiplier());
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
        assertEquals(0, pressure.reinforcementIntervalTicks());
        assertEquals("peaceful:night+dark+cave", pressure.zoneLabel());
    }
}
