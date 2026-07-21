package com.andresblue.threatdirector.role;

import java.util.Map;
import java.util.UUID;

public record RoleAssignment(
        Map<String, UUID> mobBySlot,
        int requiredSlotsCovered,
        int totalScore
) {
    public RoleAssignment {
        mobBySlot = Map.copyOf(mobBySlot);
    }
}
