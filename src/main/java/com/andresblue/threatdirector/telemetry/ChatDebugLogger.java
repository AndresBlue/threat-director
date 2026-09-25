package com.andresblue.threatdirector.telemetry;

import com.andresblue.threatdirector.localization.LocalizedText;
import com.andresblue.threatdirector.server.ModdedClientAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opt-in, server-only debug stream for operators.
 *
 * <p>The logger is intentionally subscription based. It does not broadcast to normal players and
 * it re-checks operator status for every event, so a permission change immediately removes access.
 * It is fed by the same immutable events used by local telemetry and therefore adds no polling work
 * when disabled.
 */
public final class ChatDebugLogger implements AutoCloseable {
    private static final int MAX_MESSAGE_LENGTH = 240;

    private final MinecraftServer server;
    private final Set<UUID> subscribers = new HashSet<>();
    private boolean closed;

    public ChatDebugLogger(MinecraftServer server) {
        this.server = server;
    }

    public boolean setEnabled(ServerPlayerEntity player, boolean enabled) {
        if (closed || player == null || !isOperator(player)) {
            return false;
        }
        if (enabled) {
            subscribers.add(player.getUuid());
        } else {
            subscribers.remove(player.getUuid());
        }
        return true;
    }

    public boolean isEnabled(UUID playerId) {
        return !closed && subscribers.contains(playerId);
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    public void record(TelemetryEvent event) {
        if (closed || subscribers.isEmpty() || event == null) {
            return;
        }
        String message = format(event);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (subscribers.contains(player.getUuid()) && isOperator(player)
                    && ModdedClientAccess.hasMod(player)) {
                player.sendMessage(LocalizedText.of("message.threatdirector.debug.event", message)
                        .formatted(Formatting.DARK_AQUA), false);
            }
        }
    }

    public String status() {
        return "subscribers=" + subscribers.size() + ", closed=" + closed;
    }

    @Override
    public void close() {
        closed = true;
        subscribers.clear();
    }

    private boolean isOperator(ServerPlayerEntity player) {
        return server.getPlayerManager().isOperator(player.getGameProfile());
    }

    private static String format(TelemetryEvent event) {
        String attributes = event.attributes().entrySet().stream()
                .map(ChatDebugLogger::formatAttribute)
                .collect(Collectors.joining(", "));
        String message = event.eventType()
                + (attributes.isBlank() ? "" : " " + attributes);
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private static String formatAttribute(Map.Entry<String, ?> entry) {
        return entry.getKey() + "=" + String.valueOf(entry.getValue());
    }
}
