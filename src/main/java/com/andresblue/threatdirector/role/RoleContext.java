package com.andresblue.threatdirector.role;

public record RoleContext(
        double vanguardPathQuality,
        double flankerPathQuality,
        double harasserPathQuality,
        double interceptorPathQuality,
        double coverQuality
) {
    public static RoleContext fullyReachable() {
        return new RoleContext(1.0, 1.0, 1.0, 1.0, 1.0);
    }
}
