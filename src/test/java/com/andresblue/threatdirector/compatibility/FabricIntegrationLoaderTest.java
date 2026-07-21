package com.andresblue.threatdirector.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andresblue.threatdirector.api.integration.IntegrationKey;
import com.andresblue.threatdirector.api.integration.IntegrationRegistrar;
import com.andresblue.threatdirector.api.integration.ThreatDirectorIntegration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FabricIntegrationLoaderTest {
    private static final IntegrationKey<String> TEST_EXTENSION =
            IntegrationKey.of("test:value", String.class);

    @Test
    void exactFabricEntrypointKeyIsStable() {
        assertEquals("threatdirector", ThreatDirectorIntegration.ENTRYPOINT_KEY);
    }

    @Test
    void failedProviderRollsBackAndDoesNotPreventLaterProvider() {
        ThreatDirectorIntegration broken = integration("broken", registrar -> {
            registrar.register(TEST_EXTENSION, "must-not-leak");
            throw new NoClassDefFoundError("optional.mod.MissingApi");
        });
        ThreatDirectorIntegration healthy =
                integration("healthy", registrar -> registrar.register(TEST_EXTENSION, "ready"));
        FabricIntegrationLoader loader = loader(candidate("broken-mod", broken), candidate("good-mod", healthy));
        CompatibilityRegistry registry = new CompatibilityRegistry();

        IntegrationLoadReport report = loader.loadInto(registry);

        assertEquals(1, report.loadedCount());
        assertEquals(1, report.failedCount());
        assertEquals(List.of("ready"), registry.extensions(TEST_EXTENSION));
        assertEquals(List.of("healthy"), List.copyOf(registry.integrationIds()));
    }

    @Test
    void unavailableProviderIsSkippedWithoutCallingRegister() {
        AtomicBoolean registerCalled = new AtomicBoolean();
        ThreatDirectorIntegration unavailable = new ThreatDirectorIntegration() {
            @Override
            public String integrationId() {
                return "optional_claims";
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public void register(IntegrationRegistrar registrar) {
                registerCalled.set(true);
            }
        };
        CompatibilityRegistry registry = new CompatibilityRegistry();

        IntegrationLoadReport report =
                loader(candidate("claims-mod", unavailable)).loadInto(registry);

        assertEquals(1, report.skippedCount());
        assertEquals(0, report.failedCount());
        assertFalse(registerCalled.get());
        assertTrue(registry.integrationIds().isEmpty());
    }

    @Test
    void entrypointConstructionFailureIsContained() {
        FabricIntegrationLoader.IntegrationCandidate broken =
                new FabricIntegrationLoader.IntegrationCandidate("broken-mod", () -> {
                    throw new ExceptionInInitializerError("bad optional dependency");
                });
        ThreatDirectorIntegration healthy =
                integration("healthy", registrar -> registrar.register(TEST_EXTENSION, "ready"));

        IntegrationLoadReport report = loader(broken, candidate("good-mod", healthy))
                .loadInto(new CompatibilityRegistry());

        assertEquals(1, report.loadedCount());
        assertEquals(1, report.failedCount());
        assertEquals(ExceptionInInitializerError.class.getName(), report.entries().get(0).errorType());
    }

    @Test
    void duplicateIntegrationIdDoesNotPublishSecondProvider() {
        ThreatDirectorIntegration first =
                integration("duplicate", registrar -> registrar.register(TEST_EXTENSION, "first"));
        ThreatDirectorIntegration second =
                integration("duplicate", registrar -> registrar.register(TEST_EXTENSION, "second"));
        CompatibilityRegistry registry = new CompatibilityRegistry();

        IntegrationLoadReport report =
                loader(candidate("first-mod", first), candidate("second-mod", second))
                        .loadInto(registry);

        assertEquals(1, report.loadedCount());
        assertEquals(1, report.failedCount());
        assertEquals(List.of("first"), registry.extensions(TEST_EXTENSION));
    }

    @Test
    void registrarCannotBeRetainedAndMutateRegistryAfterCommit() {
        AtomicReference<IntegrationRegistrar> retained = new AtomicReference<>();
        ThreatDirectorIntegration integration = integration("retainer", registrar -> {
            retained.set(registrar);
            registrar.register(TEST_EXTENSION, "initial");
        });
        CompatibilityRegistry registry = new CompatibilityRegistry();

        loader(candidate("retainer-mod", integration)).loadInto(registry);

        assertThrows(
                IllegalStateException.class,
                () -> retained.get().register(TEST_EXTENSION, "late"));
        assertEquals(List.of("initial"), registry.extensions(TEST_EXTENSION));
    }

    @Test
    void discoveryFailureReturnsDiagnosticsInsteadOfThrowing() {
        FabricIntegrationLoader loader = new FabricIntegrationLoader(() -> {
            throw new IllegalStateException("loader unavailable");
        });

        IntegrationLoadReport report = loader.loadInto(new CompatibilityRegistry());

        assertEquals(1, report.failedCount());
        assertEquals(
                IntegrationLoadReport.Outcome.DISCOVERY_FAILED,
                report.entries().get(0).outcome());
    }

    private static FabricIntegrationLoader loader(
            FabricIntegrationLoader.IntegrationCandidate... candidates) {
        return new FabricIntegrationLoader(() -> List.of(candidates));
    }

    private static FabricIntegrationLoader.IntegrationCandidate candidate(
            String providerId, ThreatDirectorIntegration integration) {
        return new FabricIntegrationLoader.IntegrationCandidate(providerId, () -> integration);
    }

    private static ThreatDirectorIntegration integration(
            String id, RegistrationAction registration) {
        return new ThreatDirectorIntegration() {
            @Override
            public String integrationId() {
                return id;
            }

            @Override
            public void register(IntegrationRegistrar registrar) {
                registration.register(registrar);
            }
        };
    }

    @FunctionalInterface
    private interface RegistrationAction {
        void register(IntegrationRegistrar registrar);
    }
}
