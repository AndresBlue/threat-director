package com.andresblue.threatdirector.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andresblue.threatdirector.encounter.EncounterPopulation.Member;
import com.andresblue.threatdirector.encounter.EncounterPopulation.Origin;
import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTrait;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncounterPopulationTest {
    @Test
    void reportsOriginsAndMinimumWithoutExposingMutableCollections() {
        EncounterPopulation population = new EncounterPopulation(
                List.of(
                        member(UUID.randomUUID(), Origin.RECRUITED),
                        member(UUID.randomUUID(), Origin.DIRECTOR_SPAWNED),
                        member(UUID.randomUUID(), Origin.RECRUITED)),
                7,
                List.of("diagnostic"));

        assertEquals(2, population.recruitedCount());
        assertEquals(1, population.spawnedCount());
        assertTrue(population.meetsMinimum(3));
        assertFalse(population.meetsMinimum(4));
        assertThrows(UnsupportedOperationException.class, () -> population.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> population.diagnostics().clear());
    }

    @Test
    void rejectsDuplicateEntityIds() {
        UUID duplicate = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> new EncounterPopulation(
                        List.of(
                                member(duplicate, Origin.RECRUITED),
                                member(duplicate, Origin.DIRECTOR_SPAWNED)),
                        0,
                        List.of()));
    }

    @Test
    void frontConeGeometryTreatsSidesAndRearAsSafe() {
        assertFalse(EncounterPopulationService.isOutsideFrontCone(1.0, 0.0, 10.0, 0.0));
        assertTrue(EncounterPopulationService.isOutsideFrontCone(1.0, 0.0, 0.0, 10.0));
        assertTrue(EncounterPopulationService.isOutsideFrontCone(1.0, 0.0, -10.0, 0.0));
        assertTrue(EncounterPopulationService.isOutsideFrontCone(0.0, 0.0, 10.0, 0.0));
    }

    private static Member member(UUID id, Origin origin) {
        MobProfile profile = new MobProfile(
                id,
                "minecraft:zombie",
                Set.of(MobTrait.MELEE),
                1.0,
                20.0,
                2.0,
                3.0,
                0.23,
                0.0,
                true);
        return new Member(profile, origin, 1_000);
    }
}
