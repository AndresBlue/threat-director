package com.andresblue.threatdirector.apocalypse;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A difficulty-gated bow goal. The vanilla bow goal remains installed, so Easy
 * keeps its original behavior; this higher-priority goal takes over only on the
 * requested difficulty and preserves vanilla navigation and strafing/kiting.
 */
final class ApocalypseSkeletonRangedGoal extends BowAttackGoal<AbstractSkeletonEntity> {
    private final AbstractSkeletonEntity skeleton;
    private final int requiredDifficultyId;
    private final boolean bloodMoonProfile;
    private int dashCooldownTicks;

    ApocalypseSkeletonRangedGoal(AbstractSkeletonEntity skeleton, int requiredDifficultyId) {
        this(skeleton, requiredDifficultyId, false);
    }

    ApocalypseSkeletonRangedGoal(
            AbstractSkeletonEntity skeleton,
            int requiredDifficultyId,
            boolean bloodMoonProfile) {
        super(
                skeleton,
                1.0,
                SkeletonCombatPolicy.rangedAttackInterval(
                        requiredDifficultyId, bloodMoonProfile),
                (float) SkeletonCombatPolicy.rangedAttackRange(
                        requiredDifficultyId, bloodMoonProfile));
        this.skeleton = skeleton;
        this.requiredDifficultyId = requiredDifficultyId;
        this.bloodMoonProfile = bloodMoonProfile;
    }

    @Override
    public boolean canStart() {
        LivingEntity target = skeleton.getTarget();
        return hasRequiredDifficulty()
                && target != null
                && target.isAlive()
                && hasThreatRange(target)
                && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return hasRequiredDifficulty()
                && hasThreatRange(skeleton.getTarget())
                && super.shouldContinue();
    }

    @Override
    public void tick() {
        super.tick();
        if (dashCooldownTicks > 0) {
            dashCooldownTicks--;
        }

        LivingEntity target = skeleton.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        double squaredDistance = skeleton.squaredDistanceTo(target);
        boolean canSeeTarget = skeleton.getVisibilityCache().canSee(target);
        if (!SkeletonCombatPolicy.canDash(
                skeleton.getWorld().getDifficulty().getId(),
                skeleton.isOnGround(),
                canSeeTarget,
                dashCooldownTicks,
                squaredDistance,
                bloodMoonProfile)) {
            return;
        }

        SkeletonCombatPolicy.HorizontalDirection retreat = SkeletonCombatPolicy.retreatDirection(
                skeleton.getX(), skeleton.getZ(), target.getX(), target.getZ());
        if (retreat.equals(SkeletonCombatPolicy.HorizontalDirection.ZERO)) {
            return;
        }

        Vec3d currentVelocity = skeleton.getVelocity();
        skeleton.getNavigation().stop();
        var config = ThreatDirectorMod.config().modded().skeleton();
        skeleton.setVelocity(
                retreat.x() * config.dashHorizontalSpeed(),
                Math.max(currentVelocity.y, config.dashVerticalSpeed()),
                retreat.z() * config.dashHorizontalSpeed());
        skeleton.velocityModified = true;
        skeleton.getLookControl().lookAt(target, 30.0F, 30.0F);
        int minimumCooldown = config.dashMinimumCooldownTicks();
        int cooldownRange = Math.max(1, config.dashMaximumCooldownTicks() - minimumCooldown + 1);
        dashCooldownTicks = minimumCooldown + skeleton.getRandom().nextInt(cooldownRange);
    }

    private boolean hasRequiredDifficulty() {
        if (!ThreatScaling.targetingEnabled(ThreatScaling.TargetFamily.SKELETON)
                || skeleton.getWorld().getDifficulty().getId() != requiredDifficultyId) {
            return false;
        }
        return requiredDifficultyId < 3
                || BloodMoonModule.isActive(skeleton.getWorld()) == bloodMoonProfile;
    }

    private boolean hasThreatRange(LivingEntity target) {
        if (!(target instanceof ServerPlayerEntity player)) {
            return true;
        }
        double range = ThreatScaling.targetRange(
                ThreatScaling.TargetFamily.SKELETON,
                requiredDifficultyId,
                ThreatScaling.playerThreat(player),
                bloodMoonProfile);
        return range > 0.0 && skeleton.squaredDistanceTo(player) <= range * range;
    }
}
