package com.andresblue.threatdirector.awareness;

import net.minecraft.util.math.BlockPos;

final class AwarenessMemory {
    private BlockPos investigationPosition;
    private long expiresAtTick;

    void remember(BlockPos position, long expiresAtTick) {
        this.investigationPosition = position.toImmutable();
        this.expiresAtTick = expiresAtTick;
    }

    BlockPos position(long now) {
        if (investigationPosition == null || now > expiresAtTick) {
            clear();
            return null;
        }
        return investigationPosition;
    }

    void clear() {
        investigationPosition = null;
        expiresAtTick = 0L;
    }
}
