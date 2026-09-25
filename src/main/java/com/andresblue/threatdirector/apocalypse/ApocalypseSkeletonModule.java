package com.andresblue.threatdirector.apocalypse;

import com.andresblue.threatdirector.mixin.MobEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.world.Difficulty;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Installs enhanced skeleton AI without registering custom entities or packets. */
public final class ApocalypseSkeletonModule {
    public static final String SKELETON_AI_TAG = "threatdirector_apocalypse_skeleton";
    public static final String ENHANCED_ARROW_TAG = "threatdirector_apocalypse_arrow";

    private static final Set<AbstractSkeletonEntity> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean registered;

    private ApocalypseSkeletonModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> install(entity));
    }

    public static boolean isRegistered() {
        return registered;
    }

    private static void install(Entity entity) {
        if (!(entity instanceof AbstractSkeletonEntity skeleton) || !INSTALLED.add(skeleton)) {
            return;
        }

        MobEntityAccessor accessor = (MobEntityAccessor) skeleton;
        accessor.threatdirector$getGoalSelector().add(
                3,
                new ApocalypseSkeletonRangedGoal(skeleton, Difficulty.NORMAL.getId()));
        accessor.threatdirector$getGoalSelector().add(
                3,
                new ApocalypseSkeletonRangedGoal(skeleton, Difficulty.HARD.getId()));
        accessor.threatdirector$getGoalSelector().add(
                3,
                new ApocalypseSkeletonRangedGoal(skeleton, Difficulty.HARD.getId(), true));
        skeleton.addCommandTag(SKELETON_AI_TAG);
    }
}
