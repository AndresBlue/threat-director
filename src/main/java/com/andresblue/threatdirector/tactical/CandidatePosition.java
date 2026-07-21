package com.andresblue.threatdirector.tactical;

public record CandidatePosition(
        BlockPoint position,
        TacticalSector sector,
        int score,
        double pathQuality,
        double separation,
        double lineOrRouteQuality,
        double coverQuality
) {}
