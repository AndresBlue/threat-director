package com.andresblue.threatdirector.tactical;

public interface EscapeDirectionProbe {
    EscapeSample sample(Vec2 direction, int distance);

    record EscapeSample(double passable, double clearance, double distanceFromThreats, double recentlyUsed) {}
}
