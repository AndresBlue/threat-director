package com.andresblue.threatdirector.api.integration;

/**
 * Fabric entrypoint SPI for optional Threat Director integrations.
 *
 * <p>An integration publishes an implementation from its own {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": {
 *   "threatdirector": ["example.compat.ExampleThreatDirectorIntegration"]
 * }
 * }</pre>
 *
 * <p>The loader instantiates and registers every entrypoint independently. A missing optional class
 * or an exception in one provider therefore does not disable the director or other integrations.
 */
public interface ThreatDirectorIntegration {
    String ENTRYPOINT_KEY = "threatdirector";

    /** Stable lowercase identifier used in diagnostics and to reject duplicate registrations. */
    String integrationId();

    /**
     * Allows a provider to decline loading after performing only safe availability checks.
     * Fabric dependency metadata should remain the primary mechanism for hard dependencies.
     */
    default boolean isAvailable() {
        return true;
    }

    /** Registers this provider's extensions. All staged entries are discarded if this call fails. */
    void register(IntegrationRegistrar registrar);
}
