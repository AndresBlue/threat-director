package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.power.AdventureIntensityBreakdown;
import com.andresblue.threatdirector.power.IntensityBand;
import net.minecraft.entity.boss.BossBar;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDirectorStateTest {
    @Test
    void forcedThreatDoesNotOverwriteTheAdaptiveTrack() {
        PlayerDirectorState state = new PlayerDirectorState(UUID.randomUUID());
        state.updateEvaluation(null, breakdown(), 42.0, 100L, 200, 10.0);

        state.setIntensityOverride(95.0);
        state.updateEvaluation(null, breakdown(), 55.0, 200L, 100, 10.0);

        assertEquals(95.0, state.intensity());
        assertEquals(55.0, state.adaptiveIntensity());
        assertEquals(IntensityBand.EXTREME, state.band());
        assertEquals(95.0, state.intensityOverride().orElseThrow());

        assertTrue(state.clearIntensityOverride());
        assertEquals(55.0, state.intensity());
        assertEquals(IntensityBand.TACTICAL, state.band());
        assertFalse(state.clearIntensityOverride());
    }

    @Test
    void forcedThreatRejectsInvalidValuesAndBuildsTheHudPresentation() {
        PlayerDirectorState state = new PlayerDirectorState(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> state.setIntensityOverride(-0.1));
        assertThrows(IllegalArgumentException.class, () -> state.setIntensityOverride(100.1));
        assertThrows(IllegalArgumentException.class, () -> state.setIntensityOverride(Double.NaN));

        state.setIntensityOverride(72.4);
        ThreatHudService.HudPresentation presentation = ThreatHudService.presentation(state);
        assertEquals(0.724F, presentation.progress(), 1.0e-6F);
        assertEquals("THREAT 72% · ALTO · FORZADO", presentation.title());
        assertEquals(BossBar.Color.RED, presentation.color());

        ThreatHudService.HudPresentation groupPresentation =
                ThreatHudService.presentation(state, true);
        assertEquals("THREAT 72% · ALTO · FORZADO · GRUPO", groupPresentation.title());
    }

    @Test
    void intensityBandsUseStableThreatThresholds() {
        assertEquals(IntensityBand.LOW, IntensityBand.fromIntensity(39.99));
        assertEquals(IntensityBand.TACTICAL, IntensityBand.fromIntensity(40.0));
        assertEquals(IntensityBand.HIGH, IntensityBand.fromIntensity(65.0));
        assertEquals(IntensityBand.EXTREME, IntensityBand.fromIntensity(85.0));
    }

    @Test
    void hudPreferenceIsPerPlayerAndResetWhenTheRuntimeCloses() {
        ThreatHudService hud = new ThreatHudService();
        UUID playerId = UUID.randomUUID();
        assertTrue(hud.isEnabled(playerId));
        hud.setEnabled(playerId, false);
        assertFalse(hud.isEnabled(playerId));
        hud.setEnabled(playerId, true);
        assertTrue(hud.isEnabled(playerId));
        hud.setEnabled(playerId, false);
        hud.close();
        assertTrue(hud.isEnabled(playerId));
    }

    @Test
    void threatBarIsVisibleOnlyToOperatorsWhoHaveNotHiddenIt() {
        assertTrue(ThreatHudService.shouldDisplay(true, false, true));
        assertFalse(ThreatHudService.shouldDisplay(false, false, true));
        assertFalse(ThreatHudService.shouldDisplay(true, true, true));
        assertFalse(ThreatHudService.shouldDisplay(true, false, false));
    }

    private static AdventureIntensityBreakdown breakdown() {
        return new AdventureIntensityBreakdown(0.4, 0.5, 0.6, 0.0, 0.0, 0.0, 50.0);
    }
}
