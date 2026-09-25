package com.andresblue.threatdirector.awareness;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/** Makes a sound-aware mob inspect an unattributed noise instead of gaining omniscient aggro. */
final class AwarenessInvestigateGoal extends Goal {
    private final MobEntity mob;
    private final AwarenessMemory memory;
    private BlockPos destination;
    private long nextRepathTick;

    AwarenessInvestigateGoal(MobEntity mob, AwarenessMemory memory) {
        this.mob = mob;
        this.memory = memory;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!AwarenessModule.canUseAwareness(mob) || mob.getTarget() != null) {
            return false;
        }
        destination = memory.position(mob.getWorld().getTime());
        return destination != null && mob.getBlockPos().getSquaredDistance(destination) > 4.0;
    }

    @Override
    public void start() {
        nextRepathTick = 0L;
        moveTowardDestination();
    }

    @Override
    public boolean shouldContinue() {
        if (!AwarenessModule.canUseAwareness(mob) || mob.getTarget() != null) {
            return false;
        }
        destination = memory.position(mob.getWorld().getTime());
        return destination != null && mob.getBlockPos().getSquaredDistance(destination) > 4.0;
    }

    @Override
    public void tick() {
        if (destination == null) {
            return;
        }
        mob.getLookControl().lookAt(
                destination.getX() + 0.5,
                destination.getY() + 0.5,
                destination.getZ() + 0.5
        );
        long now = mob.getWorld().getTime();
        if (now >= nextRepathTick) {
            moveTowardDestination();
            nextRepathTick = now + 20L;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        destination = null;
    }

    private void moveTowardDestination() {
        if (destination != null) {
            mob.getNavigation().startMovingTo(
                    destination.getX() + 0.5,
                    destination.getY(),
                    destination.getZ() + 0.5,
                    1.05
            );
        }
    }
}
