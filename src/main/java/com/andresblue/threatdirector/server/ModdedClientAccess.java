package com.andresblue.threatdirector.server;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Detects clients that advertise Threat Director's optional networking channel. */
public final class ModdedClientAccess {
    private ModdedClientAccess() {
    }

    public static boolean hasMod(ServerPlayerEntity player) {
        return player != null && ServerPlayNetworking.canSend(player, ThreatHudNetwork.CHANNEL);
    }

    /** Dedicated-server console remains authorized; players must have the client mod. */
    public static boolean canSeeCommands(ServerCommandSource source) {
        if (source.getEntity() == null) {
            return true;
        }
        return source.getEntity() instanceof ServerPlayerEntity player && hasMod(player);
    }
}
