package com.andresblue.threatdirector.tactical;

public record EscapeRoutePrediction(
        Vec2 primary,
        double primaryConfidence,
        Vec2 secondary,
        double secondaryConfidence,
        int operationsUsed
) {}
