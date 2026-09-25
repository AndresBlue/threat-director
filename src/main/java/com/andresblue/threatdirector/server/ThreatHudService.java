package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.power.IntensityBand;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronizes the threat value to the optional client overlay.
 *
 * <p>Operators are visible by default. A non-operator can be explicitly enabled
 * by an OP through {@code /threatdirector hud on <player>}.</p>
 */
public final class ThreatHudService implements AutoCloseable {
    private final Set<UUID> explicitlyEnabled = new HashSet<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final Set<UUID> lastVisible = new HashSet<>();

    public void sync(
            MinecraftServer server,
            PlayerObservationService observations,
            EncounterManager encounters) {
        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            onlinePlayers.add(playerId);
            boolean operator = server.getPlayerManager().isOperator(player.getGameProfile());
            boolean display = shouldDisplay(
                    operator,
                    player.isSpectator(),
                    explicitlyEnabled.contains(playerId),
                    !hiddenPlayers.contains(playerId));
            if (!display) {
                if (lastVisible.remove(playerId)) {
                    ThreatHudNetwork.send(player, false, 0.0F, 0);
                }
                continue;
            }

            PlayerDirectorState displayState = observations.state(playerId);
            EncounterController encounter = encounters.forPlayer(playerId).orElse(null);
            if (encounter != null) {
                displayState = observations.state(encounter.targetPlayerId());
            }
            syncPlayer(player, displayState);
        }

        lastVisible.removeIf(playerId -> {
            if (onlinePlayers.contains(playerId)) {
                return false;
            }
            return true;
        });
    }

    private void syncPlayer(ServerPlayerEntity player, PlayerDirectorState state) {
        double intensity = Double.isFinite(state.intensity())
                ? Math.max(0.0, Math.min(100.0, state.intensity()))
                : 0.0;
        boolean sent = ThreatHudNetwork.send(
                player,
                true,
                (float) (intensity / 100.0),
                colorId(state.band()));
        if (sent) {
            lastVisible.add(player.getUuid());
        }
    }

    public void clear() {
        explicitlyEnabled.clear();
        hiddenPlayers.clear();
        lastVisible.clear();
    }

    public void clear(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (lastVisible.contains(player.getUuid())) {
                ThreatHudNetwork.send(player, false, 0.0F, 0);
            }
        }
        // A temporary config disable hides the overlay but keeps per-player preferences
        // for the next enable; a full runtime close calls clear() and drops them.
        lastVisible.clear();
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            explicitlyEnabled.add(playerId);
            hiddenPlayers.remove(playerId);
            return;
        }
        explicitlyEnabled.remove(playerId);
        hiddenPlayers.add(playerId);
    }

    public boolean isEnabled(UUID playerId) {
        return !hiddenPlayers.contains(playerId);
    }

    public boolean isEnabled(UUID playerId, boolean operator) {
        return !hiddenPlayers.contains(playerId)
                && (operator || explicitlyEnabled.contains(playerId));
    }

    static boolean shouldDisplay(
            boolean operator,
            boolean spectator,
            boolean explicitlyEnabled,
            boolean preferenceEnabled) {
        return !spectator && preferenceEnabled && (operator || explicitlyEnabled);
    }

    /** Compatibility overload retained for pure policy callers. */
    static boolean shouldDisplay(boolean operator, boolean spectator, boolean preferenceEnabled) {
        return shouldDisplay(operator, spectator, false, preferenceEnabled);
    }

    /** Pure presentation helper retained for diagnostics/tests; the live HUD draws no text. */
    static HudPresentation presentation(PlayerDirectorState state) {
        return presentation(state, false);
    }

    static HudPresentation presentation(PlayerDirectorState state, boolean groupThreat) {
        double intensity = Double.isFinite(state.intensity())
                ? Math.max(0.0, Math.min(100.0, state.intensity()))
                : 0.0;
        String suffix = state.hasIntensityOverride() ? " - FORCED" : "";
        if (groupThreat) {
            suffix += " - GROUP";
        }
        String title = "THREAT " + Math.round(intensity) + "% - " + label(state.band()) + suffix;
        return new HudPresentation((float) (intensity / 100.0), title, color(state.band()));
    }

    private static String label(IntensityBand band) {
        return switch (band) {
            case LOW -> "LOW";
            case TACTICAL -> "TACTICAL";
            case HIGH -> "HIGH";
            case EXTREME -> "EXTREME";
        };
    }

    private static BossBar.Color color(IntensityBand band) {
        return switch (band) {
            case LOW -> BossBar.Color.GREEN;
            case TACTICAL -> BossBar.Color.YELLOW;
            case HIGH -> BossBar.Color.RED;
            case EXTREME -> BossBar.Color.PURPLE;
        };
    }

    private static int colorId(IntensityBand band) {
        return switch (band) {
            case LOW -> 0;
            case TACTICAL -> 1;
            case HIGH -> 2;
            case EXTREME -> 3;
        };
    }

    @Override
    public void close() {
        clear();
    }

    record HudPresentation(float progress, String title, BossBar.Color color) {
    }
}
