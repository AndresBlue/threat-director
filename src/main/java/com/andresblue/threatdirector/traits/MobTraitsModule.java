package com.andresblue.threatdirector.traits;

import com.andresblue.threatdirector.mixin.MobEntityAccessor;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/** Installs persistent traits on vanilla and modded hostile mobs as they enter a server world. */
public final class MobTraitsModule {
    private static final Set<MobEntity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile boolean registered;
    private static volatile BooleanSupplier enabled = () -> true;

    private MobTraitsModule() {
    }

    public static synchronized void register(BooleanSupplier enabledSupplier) {
        if (registered) {
            return;
        }
        enabled = java.util.Objects.requireNonNull(enabledSupplier, "enabledSupplier");
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register(MobTraitsModule::install);
    }

    public static boolean isRegistered() {
        return registered;
    }

    static boolean isEligible(MobEntity mob) {
        // Spawn group compatibility also covers conventional modded hostile mobs.
        return mob.getType().getSpawnGroup() == SpawnGroup.MONSTER;
    }

    private static void install(Entity entity, ServerWorld world) {
        if (!(entity instanceof MobEntity mob) || !isEligible(mob) || !enabled.getAsBoolean()
                || !INSTALLED.add(mob)) {
            return;
        }
        Set<MobTrait> traits = MobTraitPersistence.assignIfAbsent(mob, world);
        if (traits.isEmpty()) {
            return;
        }

        GameplayDebugEvents.record("traits.assigned", java.util.Map.of(
                "entity", mob.getType().toString(),
                "entityUuid", mob.getUuid().toString(),
                "traits", traits.stream().map(Enum::name).sorted().toList().toString()));

        MobEntityAccessor accessor = (MobEntityAccessor) mob;
        accessor.threatdirector$getGoalSelector().add(7, new TraitMaintenanceGoal(mob, traits));
        if (traits.contains(MobTrait.COWARD)) {
            // DirectorMovementGoal has priority 1; this additionally checks MANAGED_TAG.
            accessor.threatdirector$getGoalSelector().add(2, new CowardFleeGoal(mob));
        }
    }
}
