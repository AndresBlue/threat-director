package com.andresblue.threatdirector.compatibility;

import com.andresblue.threatdirector.api.integration.IntegrationKey;
import java.util.Objects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Optional claim-system boundary. Implementations live in compatibility integrations, never in the
 * director core.
 */
@FunctionalInterface
public interface ClaimProtectionService {
    IntegrationKey<ClaimProtectionService> EXTENSION_KEY = IntegrationKey.of(
            "threatdirector:claim_protection", ClaimProtectionService.class);

    ClaimProtectionStatus protectionAt(ClaimPosition position);

    default ClaimProtectionStatus protectionAt(ServerWorld world, BlockPos position) {
        return protectionAt(ClaimPosition.from(world, position));
    }

    /** Convenience guard for breach/block-change call sites; UNKNOWN deliberately returns false. */
    default boolean permitsDestructiveMobAction(ClaimPosition position) {
        return Objects.requireNonNull(protectionAt(position), "protection result")
                .permitsDestructiveMobAction();
    }
}
