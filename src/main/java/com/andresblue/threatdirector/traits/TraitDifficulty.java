package com.andresblue.threatdirector.traits;

/** Difficulty input kept free of Minecraft classes so selection is unit-testable. */
public enum TraitDifficulty {
    EASY,
    NORMAL,
    HARD;

    /** Maps Minecraft's stable difficulty ids: peaceful=0, easy=1, normal=2, hard=3. */
    public static TraitDifficulty fromDifficultyId(int difficultyId) {
        if (difficultyId >= 3) {
            return HARD;
        }
        if (difficultyId == 2) {
            return NORMAL;
        }
        return EASY;
    }
}
