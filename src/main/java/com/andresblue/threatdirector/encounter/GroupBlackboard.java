package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.role.TacticalRole;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class GroupBlackboard {
    private final Map<UUID, TacticalRole> roles = new LinkedHashMap<>();
    private final Map<UUID, Vec3d> tacticalPositions = new LinkedHashMap<>();
    private Vec3d lastKnownTargetPosition;
    private Vec3d predictedEscapePosition;
    private String activePattern = "NONE";
    private long lastContactTick;
    private long lastPlanTick = Long.MIN_VALUE;

    public void assignRole(UUID mobId, TacticalRole role) {
        roles.put(mobId, role);
    }

    public void reservePosition(UUID mobId, Vec3d position) {
        tacticalPositions.put(mobId, position);
    }

    public void remove(UUID mobId) {
        roles.remove(mobId);
        tacticalPositions.remove(mobId);
    }

    public void updateTarget(Vec3d position, Vec3d predictedEscape, long contactTick) {
        lastKnownTargetPosition = position;
        predictedEscapePosition = predictedEscape;
        lastContactTick = contactTick;
    }

    public void updatePredictedEscape(Vec3d predictedEscape) {
        predictedEscapePosition = predictedEscape;
    }

    public void markPlan(String pattern, long tick) {
        activePattern = pattern;
        lastPlanTick = tick;
    }

    public void finishPattern() {
        activePattern = "NONE";
    }

    public Map<UUID, TacticalRole> roles() {
        return Collections.unmodifiableMap(roles);
    }

    public Map<UUID, Vec3d> tacticalPositions() {
        return Collections.unmodifiableMap(tacticalPositions);
    }

    public Optional<TacticalRole> role(UUID mobId) {
        return Optional.ofNullable(roles.get(mobId));
    }

    public Optional<Vec3d> position(UUID mobId) {
        return Optional.ofNullable(tacticalPositions.get(mobId));
    }

    public Optional<Vec3d> lastKnownTargetPosition() {
        return Optional.ofNullable(lastKnownTargetPosition);
    }

    public Optional<Vec3d> predictedEscapePosition() {
        return Optional.ofNullable(predictedEscapePosition);
    }

    public String activePattern() {
        return activePattern;
    }

    public long lastContactTick() {
        return lastContactTick;
    }

    public long lastPlanTick() {
        return lastPlanTick;
    }
}
