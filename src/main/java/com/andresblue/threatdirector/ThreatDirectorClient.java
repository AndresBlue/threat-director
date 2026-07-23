package com.andresblue.threatdirector;

import com.andresblue.threatdirector.client.ThreatClientHud;
import com.andresblue.threatdirector.server.ThreatHudNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Client-only renderer for the optional threat bar. */
public final class ThreatDirectorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                ThreatHudNetwork.CHANNEL,
                (client, handler, buffer, responseSender) -> {
                    boolean visible = buffer.readBoolean();
                    float progress = buffer.readFloat();
                    int color = buffer.readVarInt();
                    client.execute(() -> ThreatClientHud.update(visible, progress, color));
                });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ThreatClientHud.hide());
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> ThreatClientHud.render(drawContext));
    }
}
