package com.andresblue.threatdirector.config;

import java.util.Objects;
import java.util.function.Supplier;

/** Test-safe access to the last server-authoritative snapshot. */
public final class ConfigAccess {
    private static volatile Supplier<ThreatDirectorConfig> supplier = ThreatDirectorConfig::vanillaPlus;

    private ConfigAccess() {
    }

    public static ThreatDirectorConfig current() {
        return supplier.get();
    }

    public static void bind(Supplier<ThreatDirectorConfig> configSupplier) {
        supplier = Objects.requireNonNull(configSupplier, "configSupplier");
    }
}
