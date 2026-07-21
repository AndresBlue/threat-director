package com.andresblue.threatdirector.tactical;

public record Vec2(double x, double z) {
    public static final Vec2 NORTH = new Vec2(0.0, -1.0);

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, z + other.z);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, z * factor);
    }

    public double length() {
        return StrictMath.hypot(x, z);
    }

    public Vec2 normalizedOr(Vec2 fallback) {
        double length = length();
        if (!Double.isFinite(length) || length < 1.0e-9) return fallback;
        return new Vec2(x / length, z / length);
    }

    public Vec2 clampMagnitude(double maximum) {
        double length = length();
        return length <= maximum ? this : scale(maximum / length);
    }

    public Vec2 rotate(double radians) {
        double cosine = StrictMath.cos(radians);
        double sine = StrictMath.sin(radians);
        return new Vec2(x * cosine - z * sine, x * sine + z * cosine);
    }

    public double dot(Vec2 other) {
        return x * other.x + z * other.z;
    }
}
