package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.compatibility.ClaimPosition;
import java.util.Optional;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/** Shared fail-closed authority for every block change introduced by Threat Director mobs. */
public final class MobGriefingGuard {
    private MobGriefingGuard() {
    }

    public static boolean permits(ServerWorld world, BlockPos position) {
        if (world == null
                || position == null
                || !world.isChunkLoaded(position)
                || !world.getWorldBorder().contains(position)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return false;
        }

        Optional<ThreatDirectorRuntime> optionalRuntime = ThreatDirectorMod.runtime();
        if (optionalRuntime.isEmpty()) {
            return false;
        }
        ThreatDirectorRuntime runtime = optionalRuntime.get();
        if (runtime.server() != world.getServer() || !runtime.config().enabled()) {
            return false;
        }
        if (!runtime.claims().permitsDestructiveMobAction(ClaimPosition.from(world, position))) {
            return false;
        }

        if (world.getRegistryKey() != World.OVERWORLD) {
            return true;
        }
        int radius = Math.max(0, world.getServer().getSpawnProtectionRadius());
        if (radius == 0) {
            return true;
        }
        BlockPos spawn = world.getServer().getOverworld().getSpawnPos();
        return Math.max(
                        Math.abs(position.getX() - spawn.getX()),
                        Math.abs(position.getZ() - spawn.getZ()))
                > radius;
    }
}
