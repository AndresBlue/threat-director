package com.andresblue.threatdirector.apocalypse.other;

import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.difficulty.ZombieDifficultyAbilities;
import com.andresblue.threatdirector.server.ThreatScaling;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.PressureProfile;
import java.util.function.Predicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

final class DifficultyPlayerTargetGoal extends ActiveTargetGoal<PlayerEntity> {
    private static final Predicate<LivingEntity> VALID_PLAYER = living -> {
        if (!(living instanceof ServerPlayerEntity player)
                || player.isCreative()
                || player.isSpectator()) {
            return false;
        }
        // The integrated server is authoritative, but threat remains per player:
        // a lightly equipped LAN guest must not inherit the host's pressure.
        return true;
    };

    private final int requiredDifficultyId;
    private final PressureProfile profile;
    private final ThreatScaling.TargetFamily targetFamily;
    private final boolean bloodMoonProfile;
    private int nextScanAge;

    DifficultyPlayerTargetGoal(MobEntity mob, int requiredDifficultyId, PressureProfile profile) {
        this(mob, requiredDifficultyId, profile, false);
    }

    DifficultyPlayerTargetGoal(
            MobEntity mob,
            int requiredDifficultyId,
            PressureProfile profile,
            boolean bloodMoonProfile) {
        this(mob, requiredDifficultyId, profile, targetFamily(mob), bloodMoonProfile);
    }

    private DifficultyPlayerTargetGoal(
            MobEntity mob,
            int requiredDifficultyId,
            PressureProfile profile,
            ThreatScaling.TargetFamily targetFamily,
            boolean bloodMoonProfile) {
        super(mob, PlayerEntity.class, 1, profile.requiresVisibility(), false, VALID_PLAYER);
        this.requiredDifficultyId = requiredDifficultyId;
        this.profile = profile;
        this.targetFamily = targetFamily;
        this.bloodMoonProfile = bloodMoonProfile;
        this.nextScanAge = mob.age + Math.floorMod(mob.getUuid().hashCode(), profile.targetScanIntervalTicks());
        setMaxTimeWithoutVisibility(profile.unseenMemoryTicks());
        targetPredicate
                .setBaseMaxDistance(ThreatScaling.maximumTargetRange(
                        targetFamily, requiredDifficultyId, bloodMoonProfile))
                .setPredicate(this::isEligiblePlayer);
        if (!profile.requiresVisibility()) {
            targetPredicate.ignoreVisibility();
        }
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
        if (!hasRequiredDifficulty()) {
            return false;
        }
        if (targetFamily == ThreatScaling.TargetFamily.ZOMBIE
                && mob.getCommandTags().contains(ZombieDifficultyAbilities.AGGRO_TAG)) {
            LivingEntity target = mob.getTarget();
            return target != null && target.isAlive();
        }
        return super.shouldContinue();
    }

    @Override
    protected double getFollowRange() {
        // ActiveTargetGoal's constructor invokes this virtual method before this subclass's
        // fields have been assigned. Fall back to vanilla during that construction window.
        if (profile == null) {
            return super.getFollowRange();
        }
        LivingEntity currentTarget = mob.getTarget();
        if (currentTarget instanceof ServerPlayerEntity player) {
            double scaled = ThreatScaling.targetRange(playerFamily(), requiredDifficultyId,
                    ThreatScaling.playerThreat(player), bloodMoonProfile);
            if (scaled > 0.0) {
                return scaled;
            }
        }
        return ThreatScaling.maximumTargetRange(
                targetFamily, requiredDifficultyId, bloodMoonProfile);
    }

    private boolean isEligiblePlayer(LivingEntity living) {
        if (!isValidPlayer(living)) {
            return false;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) living;
        double range = ThreatScaling.targetRange(
                targetFamily,
                requiredDifficultyId,
                ThreatScaling.playerThreat(player),
                bloodMoonProfile);
        return range > 0.0 && mob.squaredDistanceTo(player) <= range * range;
    }

    private ThreatScaling.TargetFamily playerFamily() {
        return targetFamily;
    }

    private static boolean isValidPlayer(LivingEntity living) {
        if (!(living instanceof ServerPlayerEntity player)
                || player.isCreative()
                || player.isSpectator()) {
            return false;
        }
        return true;
    }

    private static ThreatScaling.TargetFamily targetFamily(MobEntity mob) {
        if (mob instanceof AbstractSkeletonEntity) {
            return ThreatScaling.TargetFamily.SKELETON;
        }
        if (mob instanceof ZombieEntity) {
            return ThreatScaling.TargetFamily.ZOMBIE;
        }
        return ThreatScaling.TargetFamily.GENERIC;
    }

    private boolean hasRequiredDifficulty() {
        if (!ThreatScaling.targetingEnabled(targetFamily)
                || mob.getCommandTags().contains(EncounterController.MANAGED_TAG)
                || mob.getWorld().getDifficulty().getId() != requiredDifficultyId) {
            return false;
        }
        return requiredDifficultyId < 3
                || BloodMoonModule.isActive(mob.getWorld()) == bloodMoonProfile;
    }
}
