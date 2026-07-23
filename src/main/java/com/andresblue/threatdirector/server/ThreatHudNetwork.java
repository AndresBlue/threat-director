package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.ThreatDirectorMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Packet channel for the optional client-side threat overlay. */
public final class ThreatHudNetwork {
    public static final Identifier CHANNEL = new Identifier(ThreatDirectorMod.MOD_ID, "threat_hud");

    private ThreatHudNetwork() {
    }

    public static boolean send(ServerPlayerEntity player, boolean visible, float progress, int color) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            return false;
        }
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeBoolean(visible);
        buffer.writeFloat(progress);
        buffer.writeVarInt(color);
        ServerPlayNetworking.send(player, CHANNEL, buffer);
        return true;
    }
}
