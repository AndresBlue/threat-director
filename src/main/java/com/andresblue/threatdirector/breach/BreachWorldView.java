package com.andresblue.threatdirector.breach;

import com.andresblue.threatdirector.tactical.BlockPoint;

import java.util.List;
import java.util.Set;

/** Read-only collision/protection facade. It deliberately exposes no world mutation method. */
public interface BreachWorldView {
    BlockSnapshot blockAt(BlockPoint position);

    ClaimStatus claimAt(BlockPoint position);

    double distanceToNearestClaim(BlockPoint position);

    boolean isSpawnProtected(BlockPoint position);

    /** Blocks preventing the mob body from taking the requested step. */
    List<BlockPoint> obstructionsForStep(BlockPoint from, BlockPoint to, Set<BlockPoint> assumedRemoved);

    /** Checks floor, clearance, dangerous fluids and falls after hypothetical removals. */
    boolean isSafeStandable(BlockPoint feet, Set<BlockPoint> assumedRemoved);
}
