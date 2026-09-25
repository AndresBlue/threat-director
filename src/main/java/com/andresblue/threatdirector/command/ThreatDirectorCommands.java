package com.andresblue.threatdirector.command;

import com.andresblue.threatdirector.breach.BreachPlan;
import com.andresblue.threatdirector.breach.BreachPlanner;
import com.andresblue.threatdirector.breach.BreachPolicy;
import com.andresblue.threatdirector.breach.BreachRequest;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.encounter.EncounterPressure;
import com.andresblue.threatdirector.localization.LocalizedText;
import com.andresblue.threatdirector.performance.GovernorLimits;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.server.MinecraftBreachWorldView;
import com.andresblue.threatdirector.server.ModdedClientAccess;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.server.ThreatDirectorRuntime;
import com.andresblue.threatdirector.tactical.BlockPoint;
import com.andresblue.threatdirector.telemetry.TelemetryEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class ThreatDirectorCommands {
    private static final String[] TEST_SCENES = {
            "stalking", "positioning", "engaging", "searching",
            "climax", "relief", "retreating"
    };

    private ThreatDirectorCommands() {
    }

    /** Registers the compact 2.0 command tree. */
    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("threatdirector")
                .requires(ModdedClientAccess::canSeeCommands)
                .then(threatCommand(runtimeSupplier))
                .then(hudCommand(runtimeSupplier))
                .then(loggingCommand(runtimeSupplier))
                .then(debugCommand(runtimeSupplier));
        dispatcher.register(root);
    }

    /**
     * Legacy 1.1 tree retained for source compatibility with integrations that called this helper
     * directly. It is no longer registered by the mod; use {@link #register} instead.
     */
    @Deprecated
    public static void registerLegacy(
            CommandDispatcher<ServerCommandSource> dispatcher,
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("threatdirector")
                .requires(ModdedClientAccess::canSeeCommands)
                .then(CommandManager.literal("status")
                        .executes(context -> status(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> status(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("intensity")
                        .executes(context -> intensity(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> intensity(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(threatCommand(runtimeSupplier))
                .then(hudCommand(runtimeSupplier))
                .then(CommandManager.literal("start")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> start(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> start(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("cancel")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> cancel(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.literal("all")
                                .executes(context -> cancelAll(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource())))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> cancel(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("phase")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("state", StringArgumentType.word())
                                .executes(context -> forcePhase(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        context.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(context, "state")))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> forcePhase(
                                                runtime(context.getSource(), runtimeSupplier),
                                                context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "state"))))))
                .then(sceneCommand(runtimeSupplier))
                .then(CommandManager.literal("roles")
                        .executes(context -> roles(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("sectors")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> sectors(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("budget")
                        .executes(context -> budget(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("breach")
                        .then(CommandManager.literal("simulate")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> simulateBreach(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        context.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("config")
                        .then(CommandManager.literal("reload")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> reload(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource()))))
                .then(CommandManager.literal("integrations")
                        .executes(context -> integrations(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource())))
                .then(CommandManager.literal("performance")
                        .executes(context -> performance(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource()))
                        .then(CommandManager.literal("reset")
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> resetPerformance(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource()))))
                .then(CommandManager.literal("preset")
                        .executes(context -> feedback(
                                context.getSource(),
                                "command.threatdirector.preset",
                                runtime(context.getSource(), runtimeSupplier).config().preset())))
                .then(CommandManager.literal("telemetry")
                        .executes(context -> feedback(
                                context.getSource(),
                                "command.threatdirector.telemetry",
                                runtime(context.getSource(), runtimeSupplier).telemetry().snapshot())))
                .then(loggingCommand(runtimeSupplier))
                .then(debugCommand(runtimeSupplier));
        dispatcher.register(root);
    }

    /**
     * Keeps the common command surface short while retaining the old top-level branches as
     * backwards-compatible aliases for existing worlds and test scripts.
     */
    private static LiteralArgumentBuilder<ServerCommandSource> debugCommand(
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        return CommandManager.literal("debug")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("status")
                        .executes(context -> status(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> status(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("intensity")
                        .executes(context -> intensity(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> intensity(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("start")
                        .executes(context -> start(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> start(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("cancel")
                        .executes(context -> cancel(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.literal("all")
                                .executes(context -> cancelAll(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource())))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> cancel(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("phase")
                        .then(CommandManager.argument("state", StringArgumentType.word())
                                .executes(context -> forcePhase(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        context.getSource().getPlayerOrThrow(),
                                        StringArgumentType.getString(context, "state")))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> forcePhase(
                                                runtime(context.getSource(), runtimeSupplier),
                                                context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "state"))))))
                .then(sceneCommand(runtimeSupplier))
                .then(CommandManager.literal("roles")
                        .executes(context -> roles(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("sectors")
                        .executes(context -> sectors(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("budget")
                        .executes(context -> budget(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("breach")
                        .then(CommandManager.literal("simulate")
                                .executes(context -> simulateBreach(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        context.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("config")
                        .then(CommandManager.literal("reload")
                                .executes(context -> reload(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource()))))
                .then(CommandManager.literal("integrations")
                        .executes(context -> integrations(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource())))
                .then(CommandManager.literal("performance")
                        .executes(context -> performance(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource()))
                        .then(CommandManager.literal("reset")
                                .executes(context -> resetPerformance(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource()))))
                .then(CommandManager.literal("preset")
                        .executes(context -> feedback(
                                context.getSource(),
                                "command.threatdirector.preset",
                                runtime(context.getSource(), runtimeSupplier).config().preset())))
                .then(CommandManager.literal("telemetry")
                        .executes(context -> feedback(
                                context.getSource(),
                                "command.threatdirector.telemetry",
                                runtime(context.getSource(), runtimeSupplier).telemetry().snapshot())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> loggingCommand(
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        return CommandManager.literal("logging")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> loggingStatus(
                        runtime(context.getSource(), runtimeSupplier),
                        context.getSource(),
                        context.getSource().getPlayerOrThrow()))
                .then(CommandManager.literal("status")
                        .executes(context -> loggingStatus(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> loggingStatus(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("on")
                        .executes(context -> setLogging(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow(),
                                true))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> setLogging(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player"),
                                        true))))
                .then(CommandManager.literal("off")
                        .executes(context -> setLogging(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow(),
                                false))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> setLogging(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player"),
                                        false))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> threatCommand(
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        return CommandManager.literal("threat")
                .executes(context -> threat(
                        runtime(context.getSource(), runtimeSupplier),
                        context.getSource(),
                        context.getSource().getPlayerOrThrow()))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> threat(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"))))
                .then(CommandManager.literal("set")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument(
                                        "value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(context -> setThreat(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        context.getSource().getPlayerOrThrow(),
                                        DoubleArgumentType.getDouble(context, "value")))
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(context -> setThreat(
                                                runtime(context.getSource(), runtimeSupplier),
                                                context.getSource(),
                                                EntityArgumentType.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(CommandManager.literal("clear")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> clearThreat(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow()))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> clearThreat(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> sceneCommand(
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        return CommandManager.literal("scene")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("state", StringArgumentType.word())
                        .suggests((context, builder) ->
                                CommandSource.suggestMatching(TEST_SCENES, builder))
                        .executes(context -> forceScene(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow(),
                                StringArgumentType.getString(context, "state")))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> forceScene(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player"),
                                        StringArgumentType.getString(context, "state")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> hudCommand(
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        return CommandManager.literal("hud")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> hudStatus(
                        runtime(context.getSource(), runtimeSupplier),
                        context.getSource(),
                        context.getSource().getPlayerOrThrow()))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> hudStatus(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"))))
                .then(CommandManager.literal("on")
                        .executes(context -> setHud(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow(),
                                true))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> setHud(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player"),
                                        true))))
                .then(CommandManager.literal("off")
                        .executes(context -> setHud(
                                runtime(context.getSource(), runtimeSupplier),
                                context.getSource(),
                                context.getSource().getPlayerOrThrow(),
                                false))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> setHud(
                                        runtime(context.getSource(), runtimeSupplier),
                                        context.getSource(),
                                        EntityArgumentType.getPlayer(context, "player"),
                                        false))));
    }

    private static ThreatDirectorRuntime runtime(
            ServerCommandSource source,
            Supplier<Optional<ThreatDirectorRuntime>> supplier) {
        return supplier.get().orElseThrow(() -> new IllegalStateException(
                "Threat Director runtime is not available on this server"));
    }

    private static int status(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        PlayerDirectorState state = runtime.observations().state(player.getUuid());
        ThreatContext threat = threatContext(runtime, player);
        Optional<EncounterController> encounter = runtime.encounters().forPlayer(player.getUuid());
        ServerPlayerEntity pressureTarget = encounter
                .map(value -> runtime.server().getPlayerManager().getPlayer(value.targetPlayerId()))
                .orElse(player);
        if (pressureTarget == null) {
            pressureTarget = player;
        }
        PlayerDirectorState pressureState = runtime.observations().state(pressureTarget.getUuid());
        EncounterPressure pressure = EncounterPressure.calculate(
                runtime.config(), pressureState, pressureTarget);
        long cooldown = Math.max(0L, state.cooldownUntilTick() - runtime.server().getTicks());
        Text active = encounter.<Text>map(value -> LocalizedText.of(
                        "command.threatdirector.status.encounter",
                        encounterState(value.state()), value.livingCount()))
                .orElseGet(() -> LocalizedText.of("command.threatdirector.value.none"));
        Text mode = threat.state().hasIntensityOverride()
                ? LocalizedText.of("command.threatdirector.mode.forced",
                        decimal(threat.state().adaptiveIntensity()))
                : LocalizedText.of("command.threatdirector.mode.adaptive");
        if (threat.groupThreat()) {
            mode = LocalizedText.of("command.threatdirector.mode.group", mode);
        }
        return feedback(source, "command.threatdirector.status",
                player.getGameProfile().getName(),
                decimal(threat.state().intensity()),
                intensityBand(threat.state().band()),
                mode,
                decimal(pressure.effectiveIntensity()),
                pressure.zoneLabel(),
                decimal(state.powerReport() == null ? 0.0 : state.powerReport().score()),
                active,
                cooldown);
    }

    private static int intensity(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        PlayerDirectorState state = runtime.observations().state(player.getUuid());
        ThreatContext displayedThreat = threatContext(runtime, player);
        Text groupNote = displayedThreat.groupThreat()
                ? LocalizedText.of("command.threatdirector.intensity.group_note",
                        decimal2(displayedThreat.state().intensity()), displayedThreat.ownerName())
                : Text.empty();
        if (state.breakdown() == null || state.powerReport() == null) {
            return feedback(source, "command.threatdirector.intensity.no_sample",
                    decimal2(state.intensity()), overrideMarker(state), groupNote);
        }
        return feedback(source, "command.threatdirector.intensity",
                decimal2(state.intensity()),
                overrideMarker(state),
                decimal2(state.adaptiveIntensity()),
                decimal2(state.breakdown().targetIntensity()),
                decimal2(state.powerReport().score()),
                decimal2(state.breakdown().environment()),
                decimal2(state.breakdown().readiness()),
                decimal2(state.breakdown().distress()),
                decimal2(state.breakdown().load()),
                decimal2(state.breakdown().playTimeProgress()),
                decimal2(state.breakdown().experienceProgress()),
                decimal2(state.breakdown().progression()),
                groupNote);
    }

    private static int threat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        Text mode = state.hasIntensityOverride()
                ? LocalizedText.of("command.threatdirector.threat.mode_forced",
                        decimal(state.adaptiveIntensity()))
                : LocalizedText.of("command.threatdirector.mode.adaptive");
        Text shared = threat.groupThreat()
                ? LocalizedText.of("command.threatdirector.threat.shared", threat.ownerName())
                : Text.empty();
        return feedback(source, "command.threatdirector.threat",
                player.getGameProfile().getName(),
                decimal(state.intensity()),
                intensityBand(state.band()),
                mode,
                shared);
    }

    private static int setThreat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            return error(source, "command.threatdirector.error.threat_range");
        }
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        state.setIntensityOverride(value);
        runtime.refreshHud();
        recordCommand(runtime, "threat.set", player, value);
        return feedback(source, "command.threatdirector.threat.set",
                threat.ownerName(),
                decimal(value),
                intensityBand(state.band()));
    }

    private static int clearThreat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        boolean cleared = state.clearIntensityOverride();
        runtime.refreshHud();
        if (cleared) {
            recordCommand(runtime, "threat.clear", player);
        }
        return cleared
                ? feedback(source, "command.threatdirector.threat.cleared",
                        threat.ownerName(), decimal(state.intensity()))
                : feedback(source, "command.threatdirector.threat.already_adaptive",
                        threat.ownerName());
    }

    private static int hudStatus(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        return feedback(source, "command.threatdirector.hud.status",
                player.getGameProfile().getName(),
                enabledHidden(runtime.isHudEnabled(player)));
    }

    private static int setHud(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            boolean enabled) {
        if (!runtime.setHudEnabled(player, enabled)) {
            return error(source, "command.threatdirector.error.hud_disabled");
        }
        recordCommand(runtime, enabled ? "hud.on" : "hud.off", player);
        return feedback(source, "command.threatdirector.hud.set",
                enabledHidden(enabled), player.getGameProfile().getName());
    }

    private static int loggingStatus(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        boolean enabled = runtime.chatDebugLogger().isEnabled(player.getUuid());
        return feedback(source, "command.threatdirector.logging.status",
                activeInactive(enabled), player.getGameProfile().getName(),
                runtime.chatDebugLogger().status());
    }

    private static int setLogging(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            boolean enabled) {
        if (!runtime.config().telemetry().operatorChatLoggingEnabled()) {
            return error(source, "command.threatdirector.error.logging_disabled");
        }
        if (!runtime.chatDebugLogger().setEnabled(player, enabled)) {
            return error(source, "command.threatdirector.error.logging_op_only");
        }
        recordCommand(runtime, enabled ? "logging.on" : "logging.off", player);
        return feedback(source, "command.threatdirector.logging.set",
                enabledDisabled(enabled), player.getGameProfile().getName());
    }

    private static int start(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterManager.StartResult result = runtime.encounters().forceStart(
                player, runtime.config(), runtime.governor().limits());
        if (result.started()) {
            recordCommand(runtime, "debug.start", player);
        }
        return result.started()
                ? feedback(source, "command.threatdirector.encounter.started",
                        result.encounterId(), result.members(), result.threatBudget())
                : error(source, "command.threatdirector.error.encounter_start", result.reason());
    }

    private static int cancel(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        boolean cancelled = runtime.encounters().cancel(player.getUuid(), runtime.server().getTicks());
        if (cancelled) {
            recordCommand(runtime, "debug.cancel", player);
        }
        return cancelled
                ? feedback(source, "command.threatdirector.encounter.relief")
                : error(source, "command.threatdirector.error.no_player_encounter");
    }

    private static int cancelAll(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        int count = runtime.encounters().cancelAll(runtime.server().getTicks());
        return feedback(source, "command.threatdirector.encounter.relief_all", count);
    }

    private static int forcePhase(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            String rawState) {
        EncounterState state;
        try {
            state = EncounterState.valueOf(rawState.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return error(source, "command.threatdirector.error.unknown_phase", rawState);
        }
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "command.threatdirector.error.no_player_encounter");
        }
        return controller.forceState(
                state,
                runtime.server().getTicks(),
                runtime.observations().state(controller.targetPlayerId()).intensity())
                ? feedback(source, "command.threatdirector.phase.forced", encounterState(state))
                : error(source, "command.threatdirector.error.phase_change");
    }

    private static int forceScene(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            String rawState) {
        EncounterState requested = testScene(rawState);
        if (requested == null) {
            return error(source, "command.threatdirector.error.unknown_scene",
                    rawState, String.join(", ", TEST_SCENES));
        }

        boolean startedNow = false;
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            EncounterManager.StartResult result = runtime.encounters().forceStart(
                    player, runtime.config(), runtime.governor().limits());
            if (!result.started()) {
                return error(source, "command.threatdirector.error.scene_prepare", result.reason());
            }
            startedNow = true;
            controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
            if (controller == null) {
                return error(source, "command.threatdirector.error.controller_unavailable");
            }
        }

        PlayerDirectorState encounterState = runtime.observations().state(controller.targetPlayerId());
        boolean changed = controller.forceSceneState(
                requested,
                runtime.server().getTicks(),
                encounterState.intensity());
        if (!changed) {
            return error(source, "command.threatdirector.error.scene_force", encounterState(requested));
        }
        runtime.refreshHud();
        recordCommand(runtime, "debug.scene." + requested.name().toLowerCase(Locale.ROOT), player);

        return feedback(source, "command.threatdirector.scene.forced",
                encounterState(requested),
                player.getGameProfile().getName(),
                startedNow ? LocalizedText.of("command.threatdirector.scene.created") : Text.empty(),
                decimal(encounterState.intensity()),
                controller.livingCount(),
                controller.budget().total());
    }

    private static EncounterState testScene(String rawState) {
        if (rawState == null) {
            return null;
        }
        return switch (rawState.toLowerCase(Locale.ROOT)) {
            case "stalking" -> EncounterState.STALKING;
            case "positioning" -> EncounterState.POSITIONING;
            case "engaging" -> EncounterState.ENGAGING;
            case "searching" -> EncounterState.SEARCHING;
            case "climax" -> EncounterState.CLIMAX;
            case "relief" -> EncounterState.RELIEF;
            case "retreating" -> EncounterState.RETREATING;
            default -> null;
        };
    }

    private static ThreatContext threatContext(
            ThreatDirectorRuntime runtime, ServerPlayerEntity requestedPlayer) {
        EncounterController encounter = runtime.encounters()
                .forPlayer(requestedPlayer.getUuid())
                .orElse(null);
        if (encounter == null || encounter.targetPlayerId().equals(requestedPlayer.getUuid())) {
            return new ThreatContext(
                    runtime.observations().state(requestedPlayer.getUuid()),
                    requestedPlayer.getGameProfile().getName(),
                    false);
        }
        ServerPlayerEntity primary = runtime.server().getPlayerManager()
                .getPlayer(encounter.targetPlayerId());
        String ownerName = primary == null
                ? encounter.targetPlayerId().toString()
                : primary.getGameProfile().getName();
        return new ThreatContext(
                runtime.observations().state(encounter.targetPlayerId()),
                ownerName,
                true);
    }

    private static int roles(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "command.threatdirector.error.no_active_encounter");
        }
        String roles = controller.blackboard().roles().entrySet().stream()
                .map(entry -> entry.getKey().toString().substring(0, 8) + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return feedback(source, "command.threatdirector.roles", roles);
    }

    private static int sectors(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "command.threatdirector.error.no_active_encounter");
        }
        controller.blackboard().tacticalPositions().values().forEach(position ->
                player.getServerWorld().spawnParticles(
                        ParticleTypes.END_ROD,
                        position.x,
                        position.y + 0.5,
                        position.z,
                        6,
                        0.2,
                        0.4,
                        0.2,
                        0.01));
        return feedback(source, "command.threatdirector.sectors",
                controller.blackboard().tacticalPositions().size());
    }

    private static int budget(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "command.threatdirector.error.no_active_encounter");
        }
        return feedback(source, "command.threatdirector.budget",
                controller.budget().total(),
                controller.budget().baseTotal(),
                controller.budget().reinforcementReserve(),
                controller.budget().spentOnMobs(),
                controller.budget().retiredMobThreat(),
                controller.budget().reservedForPatterns(),
                controller.budget().remaining());
    }

    private static int simulateBreach(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatDirectorConfig config = runtime.config();
        if (!config.breachSimulation().enabled()) {
            return error(source, "command.threatdirector.error.breach_disabled");
        }
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        EncounterState phase = controller == null ? EncounterState.ENGAGING : controller.state();
        BlockPoint target = point(player.getBlockPos());
        BlockPoint start = controller == null
                ? new BlockPoint(target.x() + 4, target.y(), target.z())
                : controller.blackboard().roles().keySet().stream()
                        .map(player.getServerWorld()::getEntity)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .map(entity -> point(entity.getBlockPos()))
                        .orElse(new BlockPoint(target.x() + 4, target.y(), target.z()));
        BreachRequest request = new BreachRequest(
                start,
                target,
                runtime.observations().state(player.getUuid()).intensity(),
                config.director().minimumIntensity(),
                phase,
                TacticalRole.BREACHER,
                BreachPolicy.SOFT_BLOCKS_ONLY,
                true,
                true,
                true,
                config.breachSimulation().maxBlocksInPlan(),
                5_000,
                config.breachSimulation().maxBlockHardness(),
                8.0,
                config.breachSimulation().maxRayDistance(),
                4,
                config.breachSimulation().spawnProtectionRadius());
        BreachPlan plan = new BreachPlanner().simulate(
                request,
                new MinecraftBreachWorldView(
                        player.getServerWorld(),
                        runtime.claims(),
                        config.breachSimulation().spawnProtectionRadius(),
                        runtime.claimSystemPresent()));
        return feedback(source, "command.threatdirector.breach",
                plan.decision(), plan.reason(), plan.blocksRequired(), plan.budgetCost(),
                plan.protection(), yesNo(plan.safe()), plan.visitedNodes());
    }

    private static int reload(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        try {
            ThreatDirectorConfig config = runtime.reloadConfig();
            return feedback(source, "command.threatdirector.config.reloaded", config.preset());
        } catch (IOException | RuntimeException exception) {
            return error(source, "command.threatdirector.error.config_rejected", exception.getMessage());
        }
    }

    private static int integrations(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        Object ids = runtime.compatibility().integrationIds().isEmpty()
                ? LocalizedText.of("command.threatdirector.value.none")
                : String.join(", ", runtime.compatibility().integrationIds());
        return feedback(source, "command.threatdirector.integrations", ids,
                runtime.integrationReport().failedCount(), yesNo(runtime.claimSystemPresent()));
    }

    private static int performance(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        GovernorLimits limits = runtime.governor().limits();
        return feedback(source, "command.threatdirector.performance",
                limits.level(),
                decimal2(runtime.server().getTickTime()),
                limits.decisionIntervalTicks(),
                limits.candidateLimit(),
                runtime.encounters().active().size(),
                yesNo(limits.allowNewEncounters()),
                runtime.config().performance().maxPlannerOperationsPerTick(),
                runtime.config().performance().workBudgetMicrosPerTick());
    }

    private static int resetPerformance(
            ThreatDirectorRuntime runtime, ServerCommandSource source) {
        int previous = runtime.governor().level();
        runtime.governor().reset(runtime.server().getTicks());
        return feedback(source, "command.threatdirector.performance.reset", previous);
    }

    private static BlockPoint point(net.minecraft.util.math.BlockPos position) {
        return new BlockPoint(position.getX(), position.getY(), position.getZ());
    }

    private static int feedback(ServerCommandSource source, String key, Object... arguments) {
        source.sendFeedback(() -> LocalizedText.of(key, arguments), false);
        return 1;
    }

    private static void recordCommand(
            ThreatDirectorRuntime runtime,
            String action,
            ServerPlayerEntity player) {
        runtime.telemetry().record(TelemetryEvent.now("command." + action, Map.of(
                "player", player.getUuid().toString(),
                "serverTick", runtime.server().getTicks())));
    }

    private static void recordCommand(
            ThreatDirectorRuntime runtime,
            String action,
            ServerPlayerEntity player,
            double value) {
        runtime.telemetry().record(TelemetryEvent.now("command." + action, Map.of(
                "player", player.getUuid().toString(),
                "value", value,
                "serverTick", runtime.server().getTicks())));
    }

    private static int error(ServerCommandSource source, String key, Object... arguments) {
        source.sendError(LocalizedText.of(key, arguments));
        return 0;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String decimal2(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Text overrideMarker(PlayerDirectorState state) {
        return state.hasIntensityOverride()
                ? LocalizedText.of("command.threatdirector.value.forced_marker")
                : Text.empty();
    }

    private static Text intensityBand(com.andresblue.threatdirector.power.IntensityBand band) {
        return LocalizedText.of("value.threatdirector.intensity_band."
                + band.name().toLowerCase(Locale.ROOT));
    }

    private static Text encounterState(EncounterState state) {
        return LocalizedText.of("value.threatdirector.encounter_state."
                + state.name().toLowerCase(Locale.ROOT));
    }

    private static Text enabledHidden(boolean enabled) {
        return LocalizedText.of(enabled
                ? "command.threatdirector.value.enabled"
                : "command.threatdirector.value.hidden");
    }

    private static Text enabledDisabled(boolean enabled) {
        return LocalizedText.of(enabled
                ? "command.threatdirector.value.enabled"
                : "command.threatdirector.value.disabled");
    }

    private static Text activeInactive(boolean active) {
        return LocalizedText.of(active
                ? "command.threatdirector.value.active"
                : "command.threatdirector.value.inactive");
    }

    private static Text yesNo(boolean value) {
        return LocalizedText.of(value
                ? "command.threatdirector.value.yes"
                : "command.threatdirector.value.no");
    }

    private record ThreatContext(
            PlayerDirectorState state, String ownerName, boolean groupThreat) {
    }
}
