package com.andresblue.threatdirector.api.integration;

import java.util.List;
import java.util.Optional;

/** Read-only view of successfully registered optional extensions. */
public interface IntegrationLookup {
    <T> List<T> extensions(IntegrationKey<T> key);

    default <T> Optional<T> first(IntegrationKey<T> key) {
        List<T> extensions = extensions(key);
        return extensions.isEmpty() ? Optional.empty() : Optional.of(extensions.get(0));
    }
}
