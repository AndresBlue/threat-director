package com.andresblue.threatdirector.compatibility;

import java.util.Objects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Immutable claim query that does not retain a world, chunk, entity, or external-mod object. */
public record ClaimPosition(String dimensionId, int x, int y, int z) {
    public ClaimPosition {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
    }

    public static ClaimPosition from(ServerWorld world, BlockPos position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        return new ClaimPosition(
                world.getRegistryKey().getValue().toString(),
                position.getX(),
                position.getY(),
                position.getZ());
    }
}
