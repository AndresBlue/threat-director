package com.andresblue.threatdirector.apocalypse.other;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.PressureProfile;
import java.util.function.Predicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

final class DifficultyPlayerTargetGoal extends ActiveTargetGoal<PlayerEntity> {
    private static final Predicate<LivingEntity> VALID_PLAYER = living -> {
        if (!(living instanceof ServerPlayerEntity player)
                || player.isCreative()
                || player.isSpectator()) {
            return false;
        }
        MinecraftServer server = player.getServerWorld().getServer();
        return !server.isSingleplayer() || server.isHost(player.getGameProfile());
    };

    private final int requiredDifficultyId;
    private final PressureProfile profile;
    private int nextScanAge;

    DifficultyPlayerTargetGoal(MobEntity mob, int requiredDifficultyId, PressureProfile profile) {
        super(mob, PlayerEntity.class, 1, profile.requiresVisibility(), false, VALID_PLAYER);
        this.requiredDifficultyId = requiredDifficultyId;
        this.profile = profile;
        this.nextScanAge = mob.age + Math.floorMod(mob.getUuid().hashCode(), profile.targetScanIntervalTicks());
        setMaxTimeWithoutVisibility(profile.unseenMemoryTicks());
    }

    @Override
    public boolean canStart() {
        if (!hasRequiredDifficulty() || mob.age < nextScanAge) {
            return false;
        }
        nextScanAge = mob.age + profile.targetScanIntervalTicks();
        return super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return hasRequiredDifficulty() && super.shouldContinue();
    }

    @Override
    protected double getFollowRange() {
        // ActiveTargetGoal's constructor invokes this virtual method before this subclass's
        // fields have been assigned. Fall back to vanilla during that construction window.
        if (profile == null) {
            return super.getFollowRange();
        }
        return Math.max(super.getFollowRange(), profile.minimumTargetRange());
    }

    private boolean hasRequiredDifficulty() {
        return ThreatDirectorMod.isEnabled()
                && !mob.getCommandTags().contains(EncounterController.MANAGED_TAG)
                && mob.getWorld().getDifficulty().getId() == requiredDifficultyId;
    }
}
