package com.andresblue.threatdirector.api.v1;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record EncounterRequest(
        UUID sourceId,
        String factionId,
        String operationType,
        Set<UUID> targetPlayers,
        String regionId,
        Optional<UUID> captainId,
        Optional<UUID> captainEntityId,
        double strategicPriority,
        PressureProfile desiredPressure,
        Set<String> preferredMobFamilies,
        Set<UUID> requiredParticipants,
        int maximumParticipants,
        boolean allowReinforcements,
        boolean allowBreach,
        boolean highValueCaptain,
        boolean prioritizeCaptainSurvival,
        StrategicThreatContext strategicContext) {
    public EncounterRequest {
        if (sourceId == null || factionId == null || factionId.isBlank()
                || operationType == null || operationType.isBlank()
                || regionId == null || regionId.isBlank() || desiredPressure == null
                || strategicContext == null) {
            throw new IllegalArgumentException("Encounter request identity and context are required");
        }
        targetPlayers = Set.copyOf(targetPlayers == null ? Set.of() : targetPlayers);
        captainId = captainId == null ? Optional.empty() : captainId;
        captainEntityId = captainEntityId == null ? Optional.empty() : captainEntityId;
        preferredMobFamilies = Set.copyOf(
                preferredMobFamilies == null ? Set.of() : preferredMobFamilies);
        requiredParticipants = Set.copyOf(
                requiredParticipants == null ? Set.of() : requiredParticipants);
        strategicPriority = Math.max(0.0, Math.min(1.0, strategicPriority));
        maximumParticipants = Math.max(1, maximumParticipants);
    }
}
