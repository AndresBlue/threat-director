package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.ai.DirectorAiInstaller;
import com.andresblue.threatdirector.ai.MobOrderStore;
import com.andresblue.threatdirector.compatibility.ClaimProtectionService;
import com.andresblue.threatdirector.compatibility.ClaimProtectionServices;
import com.andresblue.threatdirector.compatibility.CompatibilityRegistry;
import com.andresblue.threatdirector.compatibility.FabricIntegrationLoader;
import com.andresblue.threatdirector.compatibility.IntegrationLoadReport;
import com.andresblue.threatdirector.config.ConfigManager;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.performance.GovernorLimits;
import com.andresblue.threatdirector.performance.GovernorUpdate;
import com.andresblue.threatdirector.performance.LoadSample;
import com.andresblue.threatdirector.performance.PerformanceGovernor;
import com.andresblue.threatdirector.power.PowerEvaluationPipeline;
import com.andresblue.threatdirector.telemetry.TelemetryRecorder;
import com.andresblue.threatdirector.telemetry.TelemetryRecorders;
import com.andresblue.threatdirector.telemetry.ChatDebugLogger;
import com.andresblue.threatdirector.telemetry.DebugChatTelemetryRecorder;
import com.andresblue.threatdirector.telemetry.TelemetryEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class ThreatDirectorRuntime implements AutoCloseable {
    private static final Set<String> KNOWN_CLAIM_MODS = Set.of(
            "ftbchunks",
            "openpartiesandclaims",
            "flan",
            "goml",
            "claimchunk",
            "cadmus");

    private final MinecraftServer server;
    private final ConfigManager configManager;
    private final PlayerObservationService observations;
    private final PerformanceGovernor governor;
    private final ThreatHudService hud = new ThreatHudService();
    private final MobOrderStore orderStore = new MobOrderStore();
    private final CompatibilityRegistry compatibility = new CompatibilityRegistry();
    private final IntegrationLoadReport integrationReport;
    private final ClaimProtectionService claims;
    private final boolean claimSystemPresent;
    private final ChatDebugLogger chatDebugLogger;
    private final TelemetryRecorder telemetry;
    private final EncounterManager encounters;
    private boolean closed;

    public ThreatDirectorRuntime(MinecraftServer server, ConfigManager configManager) {
        this.server = server;
        this.configManager = configManager;
        ThreatDirectorConfig config = configManager.current();
        this.governor = new PerformanceGovernor(config.performance());
        this.observations = new PlayerObservationService(PowerEvaluationPipeline.vanilla());

        if (config.integrations().enableExternalAdapters()) {
            this.integrationReport = new FabricIntegrationLoader().loadInto(compatibility);
        } else {
            this.integrationReport = new IntegrationLoadReport(List.of());
        }

        this.claimSystemPresent = KNOWN_CLAIM_MODS.stream()
                .anyMatch(modId -> FabricLoader.getInstance().isModLoaded(modId));
        if (!compatibility.extensions(ClaimProtectionService.EXTENSION_KEY).isEmpty()) {
            this.claims = ClaimProtectionServices.from(compatibility);
        } else if (claimSystemPresent) {
            this.claims = ClaimProtectionServices.conservativeFallback();
        } else {
            this.claims = ClaimProtectionServices.noClaimSystemPresent();
        }

        Path telemetryPath = server.getSavePath(WorldSavePath.ROOT)
                .resolve("threatdirector")
                .resolve("telemetry")
                .resolve("events.jsonl");
        this.chatDebugLogger = new ChatDebugLogger(server);
        TelemetryRecorder localTelemetry = TelemetryRecorders.localNdjson(
                config.telemetry().enabled(),
                telemetryPath,
                config.telemetry().queueCapacity(),
                config.telemetry().flushBatchSize());
        this.telemetry = new DebugChatTelemetryRecorder(localTelemetry, chatDebugLogger);
        DirectorAiInstaller aiInstaller = new DirectorAiInstaller(orderStore);
        this.encounters = new EncounterManager(server, observations, orderStore, aiInstaller, telemetry);

        ThreatDirectorMod.LOGGER.info(
                "Threat Director 2.0 runtime started: vanilla={}, modded={}, external integrations={}, claims={}",
                config.vanilla().enabled(),
                config.modded().enabled(),
                integrationReport.loadedCount(),
                claimSystemPresent ? "detected" : "none");
    }

    public void tick() {
        if (closed) {
            return;
        }
        ThreatDirectorConfig config = configManager.current();
        long tick = server.getTicks();
        if (!config.enabled()) {
            int cleared = encounters.disable(config, tick);
            hud.clear(server);
            if (cleared > 0) {
                ThreatDirectorMod.LOGGER.info(
                        "Threat Director disabled by server config; released {} active encounters",
                        cleared);
            }
            return;
        }
        if (tick % 20L == 0L) {
            governor.configure(config.performance());
            double activeRatio = encounters.active().size()
                    / (double) Math.max(1, config.encounter().maxActiveEncounters());
            GovernorUpdate update = governor.update(new LoadSample(
                    tick,
                    server.getTickTime(),
                    server.getTickTime(),
                    0.0,
                    activeRatio,
                    activeRatio));
            if (update.changed()) {
                telemetry.record(TelemetryEvent.now("performance.level", java.util.Map.of(
                        "previous", update.previousLevel(),
                        "current", update.currentLevel(),
                        "desired", update.desiredLevel(),
                        "tick", tick)));
            }
        }

        GovernorLimits limits = governor.limits();
        double planningLoad = Math.min(1.0, encounters.active().size()
                / (double) Math.max(1, config.encounter().maxActiveEncounters()));
        observations.sampleAll(server, config, planningLoad);
        if (config.vanillaEnabled()) {
            encounters.tick(config, limits);
        } else {
            encounters.disable(config, tick);
        }
        if (tick % 10L == 0L) {
            refreshHud();
        }

        if (config.vanillaEnabled()
                && config.vanilla().adaptiveDirectorEnabled()
                && tick % config.director().scanIntervalTicks() == 0L) {
            encounters.scanForEncounters(config, limits);
        }
        if (telemetry.isEnabled() && tick % (governor.level() >= 4 ? 100L : 20L) == 0L) {
            telemetry.flush();
        }
    }

    public void afterDeath(net.minecraft.entity.LivingEntity entity, DamageSource source) {
        long tick = server.getTicks();
        java.util.Map<String, Object> deathAttributes = new java.util.LinkedHashMap<>();
        deathAttributes.put("entity", entity.getType().toString());
        deathAttributes.put("tick", tick);
        if (entity.getUuid() != null) {
            deathAttributes.put("entityUuid", entity.getUuid().toString());
        }
        if (source.getAttacker() != null) {
            deathAttributes.put("attacker", source.getAttacker().getType().toString());
        }
        telemetry.record(TelemetryEvent.now("entity.death", deathAttributes));
        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            encounters.confirmMobDeath(mob.getUuid());
        }
        if (entity instanceof ServerPlayerEntity player) {
            observations.recordDeath(player, tick);
            telemetry.record(TelemetryEvent.now("player.death", java.util.Map.of(
                    "player", player.getUuid().toString(),
                    "tick", tick)));
            encounters.cancel(player.getUuid(), tick);
        }
        if (entity instanceof net.minecraft.entity.mob.HostileEntity
                && source.getAttacker() instanceof ServerPlayerEntity killer) {
            observations.recordKill(killer, tick);
            telemetry.record(TelemetryEvent.now("player.kill", java.util.Map.of(
                    "player", killer.getUuid().toString(),
                    "entity", entity.getType().toString(),
                    "tick", tick)));
        }
    }

    public ThreatDirectorConfig reloadConfig() throws IOException {
        return configManager.reload();
    }

    public MinecraftServer server() {
        return server;
    }

    public ThreatDirectorConfig config() {
        return configManager.current();
    }

    public PlayerObservationService observations() {
        return observations;
    }

    public EncounterManager encounters() {
        return encounters;
    }

    public PerformanceGovernor governor() {
        return governor;
    }

    public void refreshHud() {
        if (configManager.current().moddedEnabled()
                && configManager.current().modded().hud().enabled()) {
            hud.sync(server, observations, encounters);
        } else {
            hud.clear(server);
        }
    }

    public boolean setHudEnabled(ServerPlayerEntity player, boolean enabled) {
        if (!configManager.current().moddedEnabled()
                || !configManager.current().modded().hud().enabled()
                || !configManager.current().modded().hud().allowCommandOptIn()) {
            return false;
        }
        hud.setEnabled(player.getUuid(), enabled);
        refreshHud();
        return true;
    }

    public boolean isHudEnabled(ServerPlayerEntity player) {
        boolean operator = configManager.current().modded().hud().operatorsSeeByDefault()
                && server.getPlayerManager().isOperator(player.getGameProfile());
        return hud.isEnabled(player.getUuid(), operator);
    }

    public CompatibilityRegistry compatibility() {
        return compatibility;
    }

    public IntegrationLoadReport integrationReport() {
        return integrationReport;
    }

    public ClaimProtectionService claims() {
        return claims;
    }

    public boolean claimSystemPresent() {
        return claimSystemPresent;
    }

    public TelemetryRecorder telemetry() {
        return telemetry;
    }

    public ChatDebugLogger chatDebugLogger() {
        return chatDebugLogger;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        hud.close();
        encounters.close();
        telemetry.close();
        ThreatDirectorMod.LOGGER.info("Threat Director runtime stopped");
    }
}
