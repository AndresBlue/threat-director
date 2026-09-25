package com.andresblue.threatdirector.traits;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3d;

/** Low-health self-preservation that always yields to an encounter order. */
final class CowardFleeGoal extends Goal {
    private static final double START_DISTANCE_SQUARED = 14.0D * 14.0D;
    private static final double STOP_DISTANCE_SQUARED = 20.0D * 20.0D;

    private final MobEntity mob;
    private LivingEntity threat;
    private int nextPathAge;

    CowardFleeGoal(MobEntity mob) {
        this.mob = mob;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!ThreatDirectorMod.isTraitsEnabled()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        TraitBehaviorPolicy.BehaviorProfile profile = profile();
        boolean active = TraitBehaviorPolicy.cowardShouldStart(
                healthFraction(), target != null && target.isAlive(), directorControlled(), profile);
        if (!active || mob.squaredDistanceTo(target) > START_DISTANCE_SQUARED) {
            return false;
        }
        threat = target;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        return ThreatDirectorMod.isTraitsEnabled()
                && threat != null
                && TraitBehaviorPolicy.cowardShouldContinue(
                        healthFraction(), threat.isAlive(), directorControlled(), profile())
                && mob.squaredDistanceTo(threat) < STOP_DISTANCE_SQUARED;
    }

    @Override
    public void start() {
        nextPathAge = mob.age;
        GameplayDebugEvents.record("traits.coward_flee", java.util.Map.of(
                "entity", mob.getType().toString(),
                "entityUuid", mob.getUuid().toString(),
                "health", mob.getHealth()));
        moveAway();
    }

    @Override
    public void tick() {
        if (mob.age >= nextPathAge) {
            moveAway();
            nextPathAge = mob.age + 10;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        threat = null;
    }

    private void moveAway() {
        if (threat == null) {
            return;
        }
        Vec3d away = mob.getPos().subtract(threat.getPos());
        if (away.horizontalLengthSquared() < 1.0E-6D) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
            away = new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
        } else {
            away = new Vec3d(away.x, 0.0D, away.z).normalize();
        }
        Vec3d destination = mob.getPos().add(away.multiply(10.0D));
        mob.getLookControl().lookAt(destination.x, destination.y, destination.z);
        mob.getNavigation().startMovingTo(
                destination.x, destination.y, destination.z, profile().cowardFleeSpeed());
    }

    private boolean directorControlled() {
        return mob.getCommandTags().contains(EncounterController.MANAGED_TAG);
    }

    private double healthFraction() {
        return mob.getMaxHealth() <= 0.0F ? 1.0D : mob.getHealth() / mob.getMaxHealth();
    }

    private TraitBehaviorPolicy.BehaviorProfile profile() {
        return TraitBehaviorPolicy.profile(
                TraitDifficulty.fromDifficultyId(mob.getWorld().getDifficulty().getId()));
    }
}
