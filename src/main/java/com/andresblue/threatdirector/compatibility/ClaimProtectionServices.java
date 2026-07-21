package com.andresblue.threatdirector.compatibility;

import com.andresblue.threatdirector.api.integration.IntegrationLookup;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Safe factories and composition rules for claim protection services. */
public final class ClaimProtectionServices {
    private static final ClaimProtectionService CONSERVATIVE =
            ignored -> ClaimProtectionStatus.UNKNOWN;
    private static final ClaimProtectionService NO_CLAIM_SYSTEM =
            ignored -> ClaimProtectionStatus.UNPROTECTED;

    private ClaimProtectionServices() {}

    /** Default when no compatible provider can establish whether a claim exists. */
    public static ClaimProtectionService conservativeFallback() {
        return CONSERVATIVE;
    }

    /**
     * Explicit fallback for a host that has positively established that no claim system is loaded.
     * Do not use this merely because discovery of an unknown provider failed.
     */
    public static ClaimProtectionService noClaimSystemPresent() {
        return NO_CLAIM_SYSTEM;
    }

    /** Builds a conservative aggregate from registered compatibility providers. */
    public static ClaimProtectionService from(IntegrationLookup integrations) {
        Objects.requireNonNull(integrations, "integrations");
        return combine(integrations.extensions(ClaimProtectionService.EXTENSION_KEY));
    }

    /**
     * Combines providers using protection-first, fail-closed semantics.
     *
     * <ul>
     *   <li>Any PROTECTED answer wins.</li>
     *   <li>Any UNKNOWN answer or provider failure yields UNKNOWN unless another says PROTECTED.</li>
     *   <li>UNPROTECTED is returned only when every provider says UNPROTECTED.</li>
     *   <li>An empty provider set returns UNKNOWN.</li>
     * </ul>
     */
    public static ClaimProtectionService combine(
            Collection<? extends ClaimProtectionService> providers) {
        List<ClaimProtectionService> snapshot = List.copyOf(
                Objects.requireNonNull(providers, "providers"));
        if (snapshot.isEmpty()) {
            return CONSERVATIVE;
        }
        return position -> resolve(snapshot, position);
    }

    private static ClaimProtectionStatus resolve(
            List<ClaimProtectionService> providers, ClaimPosition position) {
        Objects.requireNonNull(position, "position");
        boolean unknown = false;
        for (ClaimProtectionService provider : providers) {
            try {
                ClaimProtectionStatus status = provider.protectionAt(position);
                if (status == ClaimProtectionStatus.PROTECTED) {
                    return ClaimProtectionStatus.PROTECTED;
                }
                if (status != ClaimProtectionStatus.UNPROTECTED) {
                    unknown = true;
                }
            } catch (Throwable error) {
                rethrowIfFatal(error);
                unknown = true;
            }
        }
        return unknown ? ClaimProtectionStatus.UNKNOWN : ClaimProtectionStatus.UNPROTECTED;
    }

    private static void rethrowIfFatal(Throwable error) {
        if (error instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (error instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }
}
