package com.andresblue.threatdirector.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimProtectionServicesTest {
    private static final ClaimPosition POSITION =
            new ClaimPosition("minecraft:overworld", 10, 64, -20);

    @Test
    void emptyProviderSetIsUnknownAndFailsClosed() {
        ClaimProtectionService service = ClaimProtectionServices.combine(List.of());

        assertEquals(ClaimProtectionStatus.UNKNOWN, service.protectionAt(POSITION));
        assertFalse(service.permitsDestructiveMobAction(POSITION));
    }

    @Test
    void protectedAnswerDominatesAllOtherProviders() {
        ClaimProtectionService service = ClaimProtectionServices.combine(List.of(
                ignored -> ClaimProtectionStatus.UNKNOWN,
                ignored -> ClaimProtectionStatus.UNPROTECTED,
                ignored -> ClaimProtectionStatus.PROTECTED));

        assertEquals(ClaimProtectionStatus.PROTECTED, service.protectionAt(POSITION));
        assertFalse(service.permitsDestructiveMobAction(POSITION));
    }

    @Test
    void unknownOrBrokenProviderPreventsUnsafeUnprotectedAnswer() {
        ClaimProtectionService unknown = ClaimProtectionServices.combine(List.of(
                ignored -> ClaimProtectionStatus.UNPROTECTED,
                ignored -> ClaimProtectionStatus.UNKNOWN));
        ClaimProtectionService broken = ClaimProtectionServices.combine(List.of(
                ignored -> ClaimProtectionStatus.UNPROTECTED,
                ignored -> {
                    throw new NoClassDefFoundError("removed claims API");
                }));

        assertEquals(ClaimProtectionStatus.UNKNOWN, unknown.protectionAt(POSITION));
        assertEquals(ClaimProtectionStatus.UNKNOWN, broken.protectionAt(POSITION));
    }

    @Test
    void allProvidersMustExplicitlyAgreeOnUnprotected() {
        ClaimProtectionService service = ClaimProtectionServices.combine(List.of(
                ignored -> ClaimProtectionStatus.UNPROTECTED,
                ignored -> ClaimProtectionStatus.UNPROTECTED));

        assertEquals(ClaimProtectionStatus.UNPROTECTED, service.protectionAt(POSITION));
        assertTrue(service.permitsDestructiveMobAction(POSITION));
    }

    @Test
    void knownAbsenceAndUnknownAvailabilityAreDifferentFallbacks() {
        assertEquals(
                ClaimProtectionStatus.UNKNOWN,
                ClaimProtectionServices.conservativeFallback().protectionAt(POSITION));
        assertEquals(
                ClaimProtectionStatus.UNPROTECTED,
                ClaimProtectionServices.noClaimSystemPresent().protectionAt(POSITION));
    }

    @Test
    void registryLookupComposesRegisteredClaimServices() {
        CompatibilityRegistry registry = new CompatibilityRegistry();
        CompatibilityRegistry.StagedRegistrar staged = registry.beginRegistration("claims");
        staged.register(
                ClaimProtectionService.EXTENSION_KEY,
                ignored -> ClaimProtectionStatus.PROTECTED);
        registry.commit(staged);

        ClaimProtectionService service = ClaimProtectionServices.from(registry);

        assertEquals(ClaimProtectionStatus.PROTECTED, service.protectionAt(POSITION));
    }
}
