package com.andresblue.threatdirector.api.v1;

public record StrategicThreatContext(
        double territorialControl,
        double militaryStrength,
        double fortification,
        double politicalHostility,
        double objectiveImportance) {
    public StrategicThreatContext {
        territorialControl = clamp(territorialControl);
        militaryStrength = clamp(militaryStrength);
        fortification = clamp(fortification);
        politicalHostility = clamp(politicalHostility);
        objectiveImportance = clamp(objectiveImportance);
    }

    public double combined() {
        return territorialControl * .20 + militaryStrength * .25 + fortification * .15
                + politicalHostility * .15 + objectiveImportance * .25;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
