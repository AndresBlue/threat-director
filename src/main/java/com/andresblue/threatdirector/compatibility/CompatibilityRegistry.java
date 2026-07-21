package com.andresblue.threatdirector.compatibility;

import com.andresblue.threatdirector.api.integration.IntegrationKey;
import com.andresblue.threatdirector.api.integration.IntegrationLookup;
import com.andresblue.threatdirector.api.integration.IntegrationRegistrar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Thread-safe registry of extensions published by successfully loaded integrations.
 *
 * <p>Providers never write directly into the live maps. The Fabric loader gives each provider a
 * staging registrar and atomically commits it only after registration returns normally.
 */
public final class CompatibilityRegistry implements IntegrationLookup {
    private final Map<IntegrationKey<?>, List<RegisteredExtension>> extensions =
            new LinkedHashMap<>();
    private final Set<String> integrationIds = new LinkedHashSet<>();

    @Override
    public synchronized <T> List<T> extensions(IntegrationKey<T> key) {
        Objects.requireNonNull(key, "key");
        List<RegisteredExtension> registered = extensions.getOrDefault(key, List.of());
        List<T> result = new ArrayList<>(registered.size());
        for (RegisteredExtension value : registered) {
            result.add(key.extensionType().cast(value.extension()));
        }
        return List.copyOf(result);
    }

    /** Returns the identifiers in deterministic load order. */
    public synchronized Set<String> integrationIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(integrationIds));
    }

    synchronized StagedRegistrar beginRegistration(String integrationId) {
        if (integrationIds.contains(integrationId)) {
            throw new IllegalStateException("Duplicate integration id: " + integrationId);
        }
        return new StagedRegistrar(integrationId);
    }

    synchronized void commit(StagedRegistrar staged) {
        Objects.requireNonNull(staged, "staged");
        List<PendingExtension> pending = staged.closeAndSnapshot();
        if (!integrationIds.add(staged.integrationId())) {
            throw new IllegalStateException(
                    "Duplicate integration id: " + staged.integrationId());
        }
        for (PendingExtension value : pending) {
            extensions.computeIfAbsent(value.key(), ignored -> new ArrayList<>())
                    .add(new RegisteredExtension(staged.integrationId(), value.extension()));
        }
    }

    private record RegisteredExtension(String integrationId, Object extension) {}

    private record PendingExtension(IntegrationKey<?> key, Object extension) {}

    static final class StagedRegistrar implements IntegrationRegistrar {
        private final String integrationId;
        private final List<PendingExtension> pending = new ArrayList<>();
        private boolean closed;

        private StagedRegistrar(String integrationId) {
            this.integrationId = integrationId;
        }

        @Override
        public synchronized <T> void register(IntegrationKey<T> key, T extension) {
            if (closed) {
                throw new IllegalStateException("Integration registration is already closed");
            }
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(extension, "extension");
            if (!key.extensionType().isInstance(extension)) {
                throw new IllegalArgumentException(
                        "Extension for " + key.id() + " must implement "
                                + key.extensionType().getName());
            }
            pending.add(new PendingExtension(key, extension));
        }

        synchronized String integrationId() {
            return integrationId;
        }

        synchronized List<PendingExtension> closeAndSnapshot() {
            if (closed) {
                throw new IllegalStateException("Integration registration is already closed");
            }
            closed = true;
            return List.copyOf(pending);
        }

        synchronized void discard() {
            closed = true;
            pending.clear();
        }
    }
}
