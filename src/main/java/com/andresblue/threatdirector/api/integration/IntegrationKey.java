package com.andresblue.threatdirector.api.integration;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed, namespaced key for an extension point exposed to optional integrations.
 *
 * <p>The key carries its runtime type so a broken integration cannot place an incompatible object
 * in the registry and make the failure surface later in the director core.
 */
public record IntegrationKey<T>(String id, Class<T> extensionType) {
    private static final Pattern VALID_ID =
            Pattern.compile("[a-z][a-z0-9_.-]*:[a-z0-9][a-z0-9/._-]*");

    public IntegrationKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(extensionType, "extensionType");
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Integration key must be a lowercase namespaced id: " + id);
        }
    }

    public static <T> IntegrationKey<T> of(String id, Class<T> extensionType) {
        return new IntegrationKey<>(id, extensionType);
    }
}
