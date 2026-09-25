package com.andresblue.threatdirector.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerObservationServiceTest {
    @Test
    void deathsReducePlayTimePressureWithoutErasingRecovery() {
        long sixHours = 6L * 60L * 60L * 20L;

        assertEquals(1.0, PlayerObservationService.playTimeProgress(sixHours, 0), 1.0e-9);
        assertEquals(0.85, PlayerObservationService.playTimeProgress(sixHours, 1), 1.0e-9);
        assertEquals(0.70, PlayerObservationService.playTimeProgress(sixHours, 2), 1.0e-9);
        assertEquals(0.35, PlayerObservationService.playTimeProgress(sixHours, 5), 1.0e-9);
        assertEquals(0.35, PlayerObservationService.playTimeProgress(sixHours, 99), 1.0e-9);
    }

    @Test
    void deathPenaltyScalesPartialPlayTimeAndInvalidValuesAreSafe() {
        long threeHours = 3L * 60L * 60L * 20L;

        assertEquals(0.50, PlayerObservationService.playTimeProgress(threeHours, 0), 1.0e-9);
        assertEquals(0.425, PlayerObservationService.playTimeProgress(threeHours, 1), 1.0e-9);
        assertEquals(0.0, PlayerObservationService.playTimeProgress(-1L, -3), 1.0e-9);
    }
}
