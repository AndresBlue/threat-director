package com.andresblue.threatdirector.compatibility;

import com.andresblue.threatdirector.api.integration.ThreatDirectorIntegration;
import com.andresblue.threatdirector.compatibility.IntegrationLoadReport.Entry;
import com.andresblue.threatdirector.compatibility.IntegrationLoadReport.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Discovers the {@code threatdirector} Fabric entrypoint and loads every provider in isolation. */
public final class FabricIntegrationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("threatdirector/integrations");
    private static final Pattern VALID_INTEGRATION_ID =
            Pattern.compile("[a-z][a-z0-9_.-]*(?::[a-z0-9][a-z0-9/._-]*)?");

    private final IntegrationDiscovery discovery;

    public FabricIntegrationLoader() {
        this(FabricIntegrationLoader::discoverFabricEntrypoints);
    }

    FabricIntegrationLoader(IntegrationDiscovery discovery) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
    }

    /**
     * Loads all currently declared providers without allowing a broken optional integration to
     * abort the remaining providers or the Threat Director core.
     */
    public IntegrationLoadReport loadInto(CompatibilityRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        List<Entry> report = new ArrayList<>();
        List<IntegrationCandidate> candidates;
        try {
            candidates = List.copyOf(discovery.discover());
        } catch (Throwable error) {
            rethrowIfFatal(error);
            LOGGER.error("Could not discover Threat Director integration entrypoints", error);
            report.add(failure("fabric-loader", "", Outcome.DISCOVERY_FAILED, error));
            return new IntegrationLoadReport(report);
        }

        for (IntegrationCandidate candidate : candidates) {
            loadCandidate(candidate, registry, report);
        }
        return new IntegrationLoadReport(report);
    }

    private static void loadCandidate(
            IntegrationCandidate candidate, CompatibilityRegistry registry, List<Entry> report) {
        String integrationId = "";
        CompatibilityRegistry.StagedRegistrar staged = null;
        try {
            ThreatDirectorIntegration integration = Objects.requireNonNull(
                    candidate.entrypointFactory().get(), "Entrypoint returned null");
            integrationId = requireValidId(integration.integrationId());
            if (!integration.isAvailable()) {
                report.add(new Entry(
                        candidate.providerId(),
                        integrationId,
                        Outcome.SKIPPED_UNAVAILABLE,
                        "",
                        "Provider reported unavailable"));
                LOGGER.debug(
                        "Skipping unavailable Threat Director integration '{}' from {}",
                        integrationId,
                        candidate.providerId());
                return;
            }

            staged = registry.beginRegistration(integrationId);
            integration.register(staged);
            registry.commit(staged);
            staged = null;
            report.add(new Entry(candidate.providerId(), integrationId, Outcome.LOADED, "", ""));
            LOGGER.info(
                    "Loaded Threat Director integration '{}' from {}",
                    integrationId,
                    candidate.providerId());
        } catch (Throwable error) {
            rethrowIfFatal(error);
            report.add(failure(candidate.providerId(), integrationId, Outcome.FAILED, error));
            LOGGER.warn(
                    "Ignoring failed Threat Director integration from {}",
                    candidate.providerId(),
                    error);
        } finally {
            if (staged != null) {
                staged.discard();
            }
        }
    }

    private static String requireValidId(String integrationId) {
        if (integrationId == null || !VALID_INTEGRATION_ID.matcher(integrationId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Threat Director integration id: " + integrationId);
        }
        return integrationId;
    }

    private static Entry failure(
            String providerId, String integrationId, Outcome outcome, Throwable error) {
        return new Entry(
                providerId,
                integrationId,
                outcome,
                error.getClass().getName(),
                error.getMessage());
    }

    private static List<IntegrationCandidate> discoverFabricEntrypoints() {
        List<IntegrationCandidate> result = new ArrayList<>();
        for (EntrypointContainer<ThreatDirectorIntegration> container : FabricLoader.getInstance()
                .getEntrypointContainers(
                        ThreatDirectorIntegration.ENTRYPOINT_KEY, ThreatDirectorIntegration.class)) {
            String providerId;
            try {
                providerId = container.getProvider().getMetadata().getId();
            } catch (Throwable error) {
                rethrowIfFatal(error);
                providerId = "<unknown>";
            }
            result.add(new IntegrationCandidate(providerId, container::getEntrypoint));
        }
        return result;
    }

    private static void rethrowIfFatal(Throwable error) {
        if (error instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (error instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    @FunctionalInterface
    interface IntegrationDiscovery {
        List<IntegrationCandidate> discover();
    }

    record IntegrationCandidate(
            String providerId, Supplier<? extends ThreatDirectorIntegration> entrypointFactory) {
        IntegrationCandidate {
            providerId = Objects.requireNonNullElse(providerId, "<unknown>");
            Objects.requireNonNull(entrypointFactory, "entrypointFactory");
        }
    }
}
