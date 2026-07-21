package com.andresblue.threatdirector.ai;

import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MobOrder(
        UUID encounterId,
        MobOrderType type,
        Vec3d destination,
        UUID targetPlayerId,
        double speed,
        long expiresAtTick
) {
    public MobOrder {
        Objects.requireNonNull(encounterId, "encounterId");
        Objects.requireNonNull(type, "type");
        if (speed < 0.0 || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("speed must be finite and non-negative");
        }
    }

    public static MobOrder moveTo(UUID encounterId, Vec3d destination, double speed, long expiresAtTick) {
        return new MobOrder(encounterId, MobOrderType.MOVE_TO, Objects.requireNonNull(destination), null, speed, expiresAtTick);
    }

    public static MobOrder hold(UUID encounterId, long expiresAtTick) {
        return new MobOrder(encounterId, MobOrderType.HOLD, null, null, 0.0, expiresAtTick);
    }

    public static MobOrder engage(UUID encounterId, UUID targetPlayerId, long expiresAtTick) {
        return new MobOrder(encounterId, MobOrderType.ENGAGE, null, Objects.requireNonNull(targetPlayerId), 1.0, expiresAtTick);
    }

    public static MobOrder retreat(UUID encounterId, Vec3d destination, double speed, long expiresAtTick) {
        return new MobOrder(encounterId, MobOrderType.RETREAT, Objects.requireNonNull(destination), null, speed, expiresAtTick);
    }

    public Optional<Vec3d> destinationOptional() {
        return Optional.ofNullable(destination);
    }

    public boolean suppressesAggression() {
        return type == MobOrderType.MOVE_TO || type == MobOrderType.HOLD || type == MobOrderType.RETREAT;
    }

    public boolean expired(long serverTick) {
        return serverTick > expiresAtTick;
    }
}
