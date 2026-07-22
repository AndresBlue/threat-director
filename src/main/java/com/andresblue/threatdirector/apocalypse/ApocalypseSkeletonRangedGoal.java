package com.andresblue.threatdirector.apocalypse;

import com.andresblue.threatdirector.ThreatDirectorMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.util.math.Vec3d;

/**
 * A difficulty-gated bow goal. The vanilla bow goal remains installed, so Easy
 * keeps its original behavior; this higher-priority goal takes over only on the
 * requested difficulty and preserves vanilla navigation and strafing/kiting.
 */
final class ApocalypseSkeletonRangedGoal extends BowAttackGoal<AbstractSkeletonEntity> {
    private static final double HARD_DASH_SPEED = 0.85;
    private static final double HARD_DASH_VERTICAL_SPEED = 0.18;
    private static final int MIN_DASH_COOLDOWN_TICKS = 80;
    private static final int DASH_COOLDOWN_VARIANCE_TICKS = 41;

    private final AbstractSkeletonEntity skeleton;
    private final int requiredDifficultyId;
    private int dashCooldownTicks;

    ApocalypseSkeletonRangedGoal(AbstractSkeletonEntity skeleton, int requiredDifficultyId) {
        super(
                skeleton,
                1.0,
                SkeletonCombatPolicy.rangedAttackInterval(requiredDifficultyId),
                (float) SkeletonCombatPolicy.rangedAttackRange(requiredDifficultyId));
        this.skeleton = skeleton;
        this.requiredDifficultyId = requiredDifficultyId;
    }

    @Override
    public boolean canStart() {
        LivingEntity target = skeleton.getTarget();
        return hasRequiredDifficulty()
                && target != null
                && target.isAlive()
                && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return hasRequiredDifficulty() && super.shouldContinue();
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
                squaredDistance)) {
            return;
        }

        SkeletonCombatPolicy.HorizontalDirection retreat = SkeletonCombatPolicy.retreatDirection(
                skeleton.getX(), skeleton.getZ(), target.getX(), target.getZ());
        if (retreat.equals(SkeletonCombatPolicy.HorizontalDirection.ZERO)) {
            return;
        }

        Vec3d currentVelocity = skeleton.getVelocity();
        skeleton.getNavigation().stop();
        skeleton.setVelocity(
                retreat.x() * HARD_DASH_SPEED,
                Math.max(currentVelocity.y, HARD_DASH_VERTICAL_SPEED),
                retreat.z() * HARD_DASH_SPEED);
        skeleton.velocityModified = true;
        skeleton.getLookControl().lookAt(target, 30.0F, 30.0F);
        dashCooldownTicks = MIN_DASH_COOLDOWN_TICKS
                + skeleton.getRandom().nextInt(DASH_COOLDOWN_VARIANCE_TICKS);
    }

    private boolean hasRequiredDifficulty() {
        return ThreatDirectorMod.isEnabled()
                && skeleton.getWorld().getDifficulty().getId() == requiredDifficultyId;
    }
}
