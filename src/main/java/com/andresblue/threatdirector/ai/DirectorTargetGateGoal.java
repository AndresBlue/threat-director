package com.andresblue.threatdirector.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.EnumSet;
import java.util.Optional;

public final class DirectorTargetGateGoal extends Goal {
    private final MobEntity mob;
    private final MobOrderStore orderStore;

    public DirectorTargetGateGoal(MobEntity mob, MobOrderStore orderStore) {
        this.mob = mob;
        this.orderStore = orderStore;
        setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        return controlledOrder().isPresent();
    }

    @Override
    public boolean shouldContinue() {
        return controlledOrder().isPresent();
    }

    @Override
    public void start() {
        applyOrder();
    }

    @Override
    public void tick() {
        applyOrder();
    }

    private Optional<MobOrder> controlledOrder() {
        if (recentlyAttackedByPlayer()) {
            return Optional.empty();
        }
        return orderStore.current(mob, mob.getWorld().getTime())
                .filter(order -> order.suppressesAggression()
                        || order.type() == MobOrderType.ENGAGE);
    }

    private void applyOrder() {
        controlledOrder().ifPresent(order -> {
            if (order.type() != MobOrderType.ENGAGE || order.targetPlayerId() == null) {
                mob.setTarget(null);
                return;
            }
            if (mob.getWorld() instanceof ServerWorld world) {
                Entity entity = world.getEntity(order.targetPlayerId());
                mob.setTarget(entity instanceof PlayerEntity target
                        && target.isAlive()
                        && !target.isSpectator()
                        && !target.isCreative()
                        ? target : null);
            }
        });
    }

    private boolean recentlyAttackedByPlayer() {
        return mob.getAttacker() instanceof PlayerEntity
                && mob.age - mob.getLastAttackedTime() <= 100;
    }
}
