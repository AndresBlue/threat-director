package com.andresblue.threatdirector.breach;

import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.tactical.BlockPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreachPlannerTest {
    private static final BlockPoint WALL = new BlockPoint(1, 64, 0);

    @Test
    void softDirtWallProducesReadOnlyOneBlockPlan() {
        BreachPlan plan = new BreachPlanner().simulate(request(), world(false, ClaimStatus.NO_SYSTEM));
        assertEquals(BreachDecision.WOULD_BREACH, plan.decision());
        assertEquals(List.of(WALL), plan.blocks());
        assertEquals(1_125, plan.budgetCost());
        assertTrue(plan.safe());
    }

    @Test
    void blockEntityAndProtectedTargetFailClosed() {
        BreachPlan blocked = new BreachPlanner().simulate(request(), world(true, ClaimStatus.NO_SYSTEM));
        assertEquals(BreachDecision.NO_ROUTE, blocked.decision());
        assertEquals("block_entity_forbidden", blocked.reason());

        BreachPlan claimed = new BreachPlanner().simulate(request(), world(false, ClaimStatus.PROTECTED));
        assertEquals(BreachDecision.DENIED, claimed.decision());
        assertEquals(ProtectionStatus.PROTECTED, claimed.protection());
    }

    private static BreachRequest request() {
        return new BreachRequest(new BlockPoint(0, 64, 0), new BlockPoint(3, 64, 0),
                80, 65, EncounterState.ENGAGING, TacticalRole.BREACHER,
                BreachPolicy.SOFT_BLOCKS_ONLY, true, true, true,
                2, 3_000, 2, 0, 6, 1, 128);
    }

    private static BreachWorldView world(boolean blockEntity, ClaimStatus targetClaim) {
        return new BreachWorldView() {
            @Override public BlockSnapshot blockAt(BlockPoint position) {
                return new BlockSnapshot("minecraft:dirt", .5, blockEntity,
                        false, false, true, false, false);
            }
            @Override public ClaimStatus claimAt(BlockPoint position) {
                return position.equals(new BlockPoint(3, 64, 0)) ? targetClaim : ClaimStatus.NO_SYSTEM;
            }
            @Override public double distanceToNearestClaim(BlockPoint position) { return Double.POSITIVE_INFINITY; }
            @Override public boolean isSpawnProtected(BlockPoint position) { return false; }
            @Override public List<BlockPoint> obstructionsForStep(BlockPoint from, BlockPoint to,
                                                                   Set<BlockPoint> assumedRemoved) {
                return to.equals(WALL) && !assumedRemoved.contains(WALL) ? List.of(WALL) : List.of();
            }
            @Override public boolean isSafeStandable(BlockPoint feet, Set<BlockPoint> assumedRemoved) {
                return feet.y() == 64 && feet.z() == 0;
            }
        };
    }
}
