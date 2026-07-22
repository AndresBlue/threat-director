package com.andresblue.threatdirector.difficulty;

import com.andresblue.threatdirector.ThreatDirectorMod;
import net.minecraft.world.World;

public final class MinecraftDifficultyProfiles {
    private MinecraftDifficultyProfiles() {
    }

    public static DifficultyAbilityPolicy.AbilityProfile forWorld(World world) {
        if (!ThreatDirectorMod.isEnabled()) {
            return DifficultyAbilityPolicy.profile(DifficultyAbilityPolicy.Tier.EASY);
        }
        int id = world.getDifficulty().getId();
        return DifficultyAbilityPolicy.profile(DifficultyAbilityPolicy.hostileTierFromDifficultyId(id));
    }
}
