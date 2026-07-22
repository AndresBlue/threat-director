package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.power.IntensityBand;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Maintains one vanilla boss bar per connected player, without requiring client-side code. */
public final class ThreatHudService implements AutoCloseable {
    private final Map<UUID, ServerBossBar> bars = new HashMap<>();
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public void sync(
            MinecraftServer server,
            PlayerObservationService observations,
            EncounterManager encounters) {
        Set<UUID> onlinePlayers = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean operator = server.getPlayerManager().isOperator(player.getGameProfile());
            if (!shouldDisplay(
                    operator,
                    player.isSpectator(),
                    !hiddenPlayers.contains(player.getUuid()))) {
                continue;
            }
            onlinePlayers.add(player.getUuid());
            PlayerDirectorState displayState = observations.state(player.getUuid());
            EncounterController encounter = encounters.forPlayer(player.getUuid()).orElse(null);
            boolean groupThreat = encounter != null
                    && !encounter.targetPlayerId().equals(player.getUuid());
            if (encounter != null) {
                displayState = observations.state(encounter.targetPlayerId());
            }
            syncPlayer(player, displayState, groupThreat);
        }

        bars.entrySet().removeIf(entry -> {
            if (onlinePlayers.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().clearPlayers();
            return true;
        });
    }

    private void syncPlayer(
            ServerPlayerEntity player, PlayerDirectorState state, boolean groupThreat) {
        HudPresentation presentation = presentation(state, groupThreat);
        ServerBossBar bar = bars.computeIfAbsent(player.getUuid(), ignored -> createBar());

        for (ServerPlayerEntity attached : List.copyOf(bar.getPlayers())) {
            if (attached != player) {
                bar.removePlayer(attached);
            }
        }
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }

        bar.setName(Text.literal(presentation.title()));
        bar.setPercent(presentation.progress());
        bar.setColor(presentation.color());
        bar.setStyle(BossBar.Style.NOTCHED_10);
        bar.setVisible(true);
    }

    private static ServerBossBar createBar() {
        ServerBossBar bar = new ServerBossBar(
                Text.literal("THREAT 0% · BAJO"),
                BossBar.Color.GREEN,
                BossBar.Style.NOTCHED_10);
        bar.setDarkenSky(false);
        bar.setDragonMusic(false);
        bar.setThickenFog(false);
        return bar;
    }

    static HudPresentation presentation(PlayerDirectorState state) {
        return presentation(state, false);
    }

    static HudPresentation presentation(PlayerDirectorState state, boolean groupThreat) {
        double intensity = Double.isFinite(state.intensity())
                ? Math.max(0.0, Math.min(100.0, state.intensity()))
                : 0.0;
        IntensityBand band = state.band();
        String suffix = state.hasIntensityOverride() ? " · FORZADO" : "";
        if (groupThreat) {
            suffix += " · GRUPO";
        }
        String title = "THREAT " + Math.round(intensity) + "% · " + label(band) + suffix;
        return new HudPresentation((float) (intensity / 100.0), title, color(band));
    }

    private static String label(IntensityBand band) {
        return switch (band) {
            case LOW -> "BAJO";
            case TACTICAL -> "TÁCTICO";
            case HIGH -> "ALTO";
            case EXTREME -> "EXTREMO";
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

    public void clear() {
        bars.values().forEach(ServerBossBar::clearPlayers);
        bars.clear();
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            hiddenPlayers.remove(playerId);
            return;
        }
        hiddenPlayers.add(playerId);
        ServerBossBar removed = bars.remove(playerId);
        if (removed != null) {
            removed.clearPlayers();
        }
    }

    public boolean isEnabled(UUID playerId) {
        return !hiddenPlayers.contains(playerId);
    }

    static boolean shouldDisplay(boolean operator, boolean spectator, boolean preferenceEnabled) {
        return operator && !spectator && preferenceEnabled;
    }

    @Override
    public void close() {
        clear();
        hiddenPlayers.clear();
    }

    record HudPresentation(float progress, String title, BossBar.Color color) {
    }
}
