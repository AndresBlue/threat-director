package com.andresblue.threatdirector.apocalypse.other;

import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Family;
import com.andresblue.threatdirector.apocalypse.other.OtherHostilePolicy.Tier;
import com.andresblue.threatdirector.mixin.MobEntityAccessor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Difficulty;

/** Installs bounded, vanilla-only hostile abilities as entities load on the server. */
public final class OtherHostileApocalypseModule {
    private static final Set<MobEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean registered;

    private OtherHostileApocalypseModule() {
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
        if (!(entity instanceof MobEntity mob)
                || !OtherHostileFamilies.isSupported(mob)
                || !INSTALLED.add(mob)) {
            return;
        }

        MobEntityAccessor accessor = (MobEntityAccessor) mob;
        accessor.threatdirector$getTargetSelector().add(
                0,
                new DifficultyPlayerTargetGoal(
                        mob,
                        Difficulty.HARD.getId(),
                        OtherHostilePolicy.pressure(Tier.HARD)
                )
        );
        accessor.threatdirector$getTargetSelector().add(
                1,
                new DifficultyPlayerTargetGoal(
                        mob,
                        Difficulty.NORMAL.getId(),
                        OtherHostilePolicy.pressure(Tier.NORMAL)
                )
        );

        Family family = OtherHostileFamilies.familyOf(mob);
        if (family != Family.OTHER) {
            accessor.threatdirector$getGoalSelector().add(0, new ThematicPressureGoal(mob, family));
        }
    }
}
