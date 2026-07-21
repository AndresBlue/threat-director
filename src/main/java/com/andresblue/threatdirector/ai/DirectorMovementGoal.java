package com.andresblue.threatdirector.ai;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public final class DirectorMovementGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.25;

    private final MobEntity mob;
    private final MobOrderStore orderStore;
    private UUID lastEncounterId;
    private MobOrderType lastOrderType;
    private Vec3d lastDestination;
    private int nextPathAttemptAge;

    public DirectorMovementGoal(MobEntity mob, MobOrderStore orderStore) {
        this.mob = mob;
        this.orderStore = orderStore;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return tacticalOrder().isPresent();
    }

    @Override
    public boolean shouldContinue() {
        return tacticalOrder().isPresent();
    }

    @Override
    public boolean canStop() {
        return true;
    }

    @Override
    public void start() {
        applyOrder();
    }

    @Override
    public void tick() {
        applyOrder();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        lastEncounterId = null;
        lastOrderType = null;
        lastDestination = null;
        nextPathAttemptAge = mob.age;
    }

    private Optional<MobOrder> tacticalOrder() {
        if (mob.getAttacker() instanceof PlayerEntity
                && mob.age - mob.getLastAttackedTime() <= 100) {
            return Optional.empty();
        }
        long tick = mob.getWorld().getTime();
        return orderStore.current(mob, tick).filter(order ->
                order.type() == MobOrderType.MOVE_TO
                        || order.type() == MobOrderType.HOLD
                        || order.type() == MobOrderType.RETREAT);
    }

    private void applyOrder() {
        tacticalOrder().ifPresent(order -> {
            if (orderChanged(order)) {
                lastEncounterId = order.encounterId();
                lastOrderType = order.type();
                lastDestination = order.destination();
                nextPathAttemptAge = mob.age;
            }
            mob.setTarget(null);
            if (order.type() == MobOrderType.HOLD) {
                mob.getNavigation().stop();
                return;
            }

            Vec3d destination = order.destination();
            if (destination == null || mob.squaredDistanceTo(destination) <= ARRIVAL_DISTANCE_SQUARED) {
                mob.getNavigation().stop();
                return;
            }

            mob.getLookControl().lookAt(destination.x, destination.y, destination.z);
            if (mob.age >= nextPathAttemptAge) {
                mob.getNavigation().startMovingTo(destination.x, destination.y, destination.z, order.speed());
                nextPathAttemptAge = mob.age + 15;
            }
        });
    }

    private boolean orderChanged(MobOrder order) {
        if (!order.encounterId().equals(lastEncounterId) || order.type() != lastOrderType) {
            return true;
        }
        Vec3d destination = order.destination();
        if (destination == null || lastDestination == null) {
            return destination != lastDestination;
        }
        return destination.squaredDistanceTo(lastDestination) > 0.25;
    }
}
