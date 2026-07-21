package com.andresblue.threatdirector.tactical;

import java.util.Optional;

/** Read-only bounded world adapter used by the pure tactical planner. */
public interface TerrainProbe {
    Optional<BlockPoint> findStandable(BlockPoint approximate, int verticalRange);

    /** Negative means unreachable; otherwise 0..1. */
    double pathQuality(BlockPoint mobStart, BlockPoint destination);

    double lineOfSightQuality(BlockPoint destination);

    double coverQuality(BlockPoint destination);
}
