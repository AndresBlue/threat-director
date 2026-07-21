package com.andresblue.threatdirector.breach;

import java.util.Objects;

public record BlockSnapshot(
        String blockId,
        double hardness,
        boolean blockEntity,
        boolean container,
        boolean sensitive,
        boolean soft,
        boolean playerPlaced,
        boolean hazardousAfterRemoval
) {
    public BlockSnapshot {
        Objects.requireNonNull(blockId, "blockId");
    }
}
