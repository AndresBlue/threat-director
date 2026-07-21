package com.andresblue.threatdirector.api.integration;

/** Receives extensions from one optional integration during its isolated registration phase. */
public interface IntegrationRegistrar {
    /**
     * Stages an extension for publication.
     *
     * <p>Registration is atomic at integration granularity: if the integration subsequently fails,
     * everything it staged through this registrar is discarded.
     */
    <T> void register(IntegrationKey<T> key, T extension);
}
