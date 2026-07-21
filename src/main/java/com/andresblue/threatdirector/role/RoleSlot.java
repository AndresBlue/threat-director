package com.andresblue.threatdirector.role;

import java.util.Objects;

public record RoleSlot(String id, TacticalRole role, boolean required) {
    public RoleSlot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }
}
