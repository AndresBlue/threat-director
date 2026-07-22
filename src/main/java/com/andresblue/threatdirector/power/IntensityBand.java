package com.andresblue.threatdirector.power;

public enum IntensityBand {
    LOW,
    TACTICAL,
    HIGH,
    EXTREME;

    public static IntensityBand fromIntensity(double intensity) {
        double bounded = Double.isFinite(intensity)
                ? Math.max(0.0, Math.min(100.0, intensity))
                : 0.0;
        if (bounded >= 85.0) {
            return EXTREME;
        }
        if (bounded >= 65.0) {
            return HIGH;
        }
        if (bounded >= 40.0) {
            return TACTICAL;
        }
        return LOW;
    }
}
