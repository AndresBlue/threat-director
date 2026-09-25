package com.andresblue.threatdirector;

import com.andresblue.threatdirector.command.ThreatDirectorCommands;
import com.andresblue.threatdirector.config.ConfigManager;
import com.andresblue.threatdirector.config.ConfigAccess;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.apocalypse.ApocalypseSkeletonModule;
import com.andresblue.threatdirector.apocalypse.other.OtherHostileApocalypseModule;
import com.andresblue.threatdirector.awareness.AwarenessModule;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.server.ThreatDirectorRuntime;
import com.andresblue.threatdirector.traits.MobTraitsModule;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public final class ThreatDirectorMod implements ModInitializer {
    public static final String MOD_ID = "threatdirector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ConfigManager CONFIG_MANAGER = new ConfigManager();
    private static volatile ThreatDirectorRuntime runtime;

    @Override
    public void onInitialize() {
        ConfigAccess.bind(CONFIG_MANAGER::current);
        try {
            CONFIG_MANAGER.load();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error(
                    "Could not load threatdirector.json5; using in-memory Vanilla Plus defaults",
                    exception);
        }

        ApocalypseSkeletonModule.register();
        OtherHostileApocalypseModule.register();
        AwarenessModule.register(ThreatDirectorMod::isAwarenessEnabled);
        MobTraitsModule.register(ThreatDirectorMod::isTraitsEnabled);
        BloodMoonModule.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ThreatDirectorCommands.register(dispatcher, ThreatDirectorMod::runtime));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ThreatDirectorRuntime previous = runtime;
            if (previous != null) {
                previous.close();
            }
            runtime = new ThreatDirectorRuntime(server, CONFIG_MANAGER);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ThreatDirectorRuntime current = runtime;
            if (current != null && current.server() == server) {
                current.tick();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            ThreatDirectorRuntime current = runtime;
            if (current != null) {
                current.afterDeath(entity, damageSource);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ThreatDirectorRuntime current = runtime;
            if (current != null && current.server() == server) {
                current.close();
                runtime = null;
            }
        });
        LOGGER.info("Threat Director 2.0 initialized for Minecraft 1.20.1 / Fabric Loader 0.18.4");
    }

    public static Optional<ThreatDirectorRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    /** Global gameplay switch used by server-side entity hooks as well as the encounter runtime. */
    public static boolean isEnabled() {
        return CONFIG_MANAGER.current().enabled();
    }

    public static ThreatDirectorConfig config() {
        return ConfigAccess.current();
    }

    public static boolean isVanillaEnabled() {
        return config().vanillaEnabled();
    }

    public static boolean isModdedEnabled() {
        return config().moddedEnabled();
    }

    public static boolean isAwarenessEnabled() {
        return isModdedEnabled() && config().modded().awareness().enabled();
    }

    public static boolean isTraitsEnabled() {
        return isModdedEnabled() && config().modded().traits().enabled();
    }
}
