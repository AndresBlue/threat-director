package com.andresblue.threatdirector.ai;

import com.andresblue.threatdirector.mixin.MobEntityAccessor;
import net.minecraft.entity.mob.MobEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class DirectorAiInstaller {
    private final MobOrderStore orderStore;
    private final Set<MobEntity> installed = Collections.newSetFromMap(new WeakHashMap<>());

    public DirectorAiInstaller(MobOrderStore orderStore) {
        this.orderStore = orderStore;
    }

    public void install(MobEntity mob) {
        if (!installed.add(mob)) {
            return;
        }

        MobEntityAccessor accessor = (MobEntityAccessor) mob;
        accessor.threatdirector$getGoalSelector().add(1, new DirectorMovementGoal(mob, orderStore));
        accessor.threatdirector$getTargetSelector().add(0, new DirectorTargetGateGoal(mob, orderStore));
    }
}
