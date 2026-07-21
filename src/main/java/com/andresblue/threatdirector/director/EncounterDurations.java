package com.andresblue.threatdirector.director;

public record EncounterDurations(
        long stalkingMin,
        long stalkingMax,
        long positioningMin,
        long positioningMax,
        long engagingMinBeforeClimax,
        long engagingMax,
        long contactLoss,
        long searchingMax,
        long climaxMax,
        long reliefMin,
        long reliefMax,
        long retreatingMax,
        long cooldown
) {
    public static EncounterDurations vanillaPlus() {
        return new EncounterDurations(200, 800, 100, 400, 400, 2_400,
                100, 300, 600, 200, 600, 300, 6_000);
    }
}
