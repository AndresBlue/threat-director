package com.andresblue.threatdirector.tactical;

import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.pattern.PatternContext;
import com.andresblue.threatdirector.pattern.PatternId;
import com.andresblue.threatdirector.pattern.PatternSelector;
import com.andresblue.threatdirector.role.TacticalRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalAndPatternTest {
    @Test
    void northFacingFrameAndSectorCandidateAreStable() {
        TerrainProbe open = new TerrainProbe() {
            @Override public Optional<BlockPoint> findStandable(BlockPoint approximate, int verticalRange) {
                return Optional.of(approximate);
            }
            @Override public double pathQuality(BlockPoint mobStart, BlockPoint destination) { return 1; }
            @Override public double lineOfSightQuality(BlockPoint destination) { return 1; }
            @Override public double coverQuality(BlockPoint destination) { return 1; }
        };
        SectorPlanningRequest request = new SectorPlanningRequest(
                new BlockPoint(0, 64, 0), Vec2.NORTH, new Vec2(0, 0),
                new BlockPoint(0, 64, 10), TacticalSector.FRONT, TacticalRole.VANGUARD,
                List.of(), 24, 100);
        SectorPlan plan = new SectorPlanner().plan(request, open);

        assertEquals(Vec2.NORTH, plan.frame().forward());
        assertEquals(new BlockPoint(0, 64, -6), plan.best().orElseThrow().position());
        assertFalse(plan.vanillaNavigationFallback());
        assertTrue(plan.operationsUsed() <= 100);
    }

    @Test
    void selectorChoosesPincerInOpenTerrainAndRejectsItInCorridor() {
        PatternSelector selector = new PatternSelector();
        PatternContext open = context(-1, .9, .2, .2);
        assertEquals(PatternId.PINCER, selector.select(open).selected().orElseThrow().id());

        PatternContext corridor = context(-.2, .2, .9, .9);
        assertEquals(PatternId.EXIT_CLOSURE, selector.select(corridor).selected().orElseThrow().id());
        assertFalse(selector.evaluatePincer(corridor).eligible());
    }

    private static PatternContext context(double oppositeDot, double openness,
                                          double routeConfidence, double persistence) {
        return new PatternContext(EncounterState.ENGAGING, 75, 5, 2,
                oppositeDot, openness, 1, 1, true, routeConfidence,
                persistence, 1, persistence, 2_000, 100, Map.of(), Optional.empty());
    }
}
