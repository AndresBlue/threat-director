package com.andresblue.threatdirector.api.v1;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/** Stable, high-level integration boundary. Tactical implementation details are deliberately absent. */
public interface ThreatDirectorApi {
    String ENTRYPOINT_KEY = "threatdirector-api-v1";
    int API_VERSION = 1;

    int apiVersion();

    boolean isAvailable();

    Optional<String> lastError();

    Optional<ThreatSnapshot> getThreat(ServerPlayerEntity player);

    Optional<EncounterHandle> requestEncounter(ServerWorld world, EncounterRequest request);

    boolean updateEncounter(EncounterHandle handle, EncounterUpdate update);

    boolean cancelEncounter(EncounterHandle handle, EncounterEndReason reason);

    Optional<EncounterSnapshot> inspectEncounter(EncounterHandle handle);

    void registerEncounterListener(ThreatDirectorEncounterListener listener);

    void unregisterEncounterListener(ThreatDirectorEncounterListener listener);
}
