package com.andresblue.threatdirector.bloodmoon;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Fabric registration boundary kept separate from the core runtime. */
public final class BloodMoonModule {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private BloodMoonModule() {
    }

    /** Safe to call more than once; only one world-tick listener is installed. */
    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            ServerTickEvents.END_WORLD_TICK.register(BloodMoonService::tick);
            ServerLifecycleEvents.SERVER_STOPPING.register(BloodMoonService::clearSession);
        }
    }

    /** Stable cross-policy query; client worlds deliberately return false. */
    public static boolean isActive(World world) {
        return world instanceof ServerWorld serverWorld && BloodMoonService.isActive(serverWorld);
    }

    public static BloodMoonService.BloodMoonStatus status(ServerWorld world) {
        return BloodMoonService.status(world);
    }
}
