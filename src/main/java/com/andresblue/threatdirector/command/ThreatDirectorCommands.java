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
import com.andresblue.threatdirector.performance.GovernorLimits;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.server.MinecraftBreachWorldView;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.server.ThreatDirectorRuntime;
import com.andresblue.threatdirector.tactical.BlockPoint;
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

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            Supplier<Optional<ThreatDirectorRuntime>> runtimeSupplier) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("threatdirector")
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
                                "Preset activo: " + runtime(context.getSource(), runtimeSupplier).config().preset())))
                .then(CommandManager.literal("telemetry")
                        .executes(context -> feedback(
                                context.getSource(),
                                "Telemetría local: " + runtime(context.getSource(), runtimeSupplier)
                                        .telemetry().snapshot())));
        dispatcher.register(root);
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
        String active = encounter.map(value -> value.state() + " / " + value.livingCount() + " mobs")
                .orElse("ninguno");
        String mode = threat.state().hasIntensityOverride()
                ? String.format(Locale.ROOT, "forzada; adaptativa %.1f",
                        threat.state().adaptiveIntensity())
                : "adaptativa";
        if (threat.groupThreat()) {
            mode += "; grupo";
        }
        return feedback(source, String.format(Locale.ROOT,
                "%s: threat %.1f (%s, %s), presión de encuentro %.1f (%s), "
                        + "poder %.1f, encuentro %s, cooldown %d ticks",
                player.getGameProfile().getName(),
                threat.state().intensity(),
                threat.state().band(),
                mode,
                pressure.effectiveIntensity(),
                pressure.zoneLabel(),
                state.powerReport() == null ? 0.0 : state.powerReport().score(),
                active,
                cooldown));
    }

    private static int intensity(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        PlayerDirectorState state = runtime.observations().state(player.getUuid());
        ThreatContext displayedThreat = threatContext(runtime, player);
        String groupNote = displayedThreat.groupThreat()
                ? String.format(Locale.ROOT,
                        "; HUD grupal %.2f de %s",
                        displayedThreat.state().intensity(), displayedThreat.ownerName())
                : "";
        if (state.breakdown() == null || state.powerReport() == null) {
            return feedback(source, String.format(Locale.ROOT,
                    "Intensidad individual %.2f%s%s; todavía no existe una muestra adaptativa para ese jugador.",
                    state.intensity(),
                    state.hasIntensityOverride() ? " (FORZADO)" : "",
                    groupNote));
        }
        return feedback(source, String.format(Locale.ROOT,
                "Intensidad individual %.2f%s, adaptativa %.2f (objetivo %.2f): poder %.2f, entorno %.2f, preparación %.2f, dominio %.2f, estrés %.2f, carga %.2f%s",
                state.intensity(),
                state.hasIntensityOverride() ? " (FORZADO)" : "",
                state.adaptiveIntensity(),
                state.breakdown().targetIntensity(),
                state.powerReport().score(),
                state.breakdown().environment(),
                state.breakdown().readiness(),
                state.breakdown().dominance(),
                state.breakdown().distress(),
                state.breakdown().load(),
                groupNote));
    }

    private static int threat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        return feedback(source, String.format(Locale.ROOT,
                "%s: threat %.1f/100, nivel %s, modo %s%s",
                player.getGameProfile().getName(),
                state.intensity(),
                state.band(),
                state.hasIntensityOverride()
                        ? String.format(Locale.ROOT, "FORZADO (adaptativo %.1f)", state.adaptiveIntensity())
                        : "adaptativo",
                threat.groupThreat() ? "; compartido con " + threat.ownerName() : ""));
    }

    private static int setThreat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            return error(source, "El threat debe ser un número finito entre 0 y 100.");
        }
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        state.setIntensityOverride(value);
        runtime.refreshHud();
        return feedback(source, String.format(Locale.ROOT,
                "Threat de %s forzado a %.1f/100 (%s). Usa /threatdirector threat clear para restaurar el cálculo adaptativo.",
                threat.ownerName(),
                value,
                state.band()));
    }

    private static int clearThreat(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatContext threat = threatContext(runtime, player);
        PlayerDirectorState state = threat.state();
        boolean cleared = state.clearIntensityOverride();
        runtime.refreshHud();
        return feedback(source, cleared
                ? String.format(Locale.ROOT,
                        "Override de %s eliminado; threat adaptativo actual %.1f/100.",
                        threat.ownerName(), state.intensity())
                : threat.ownerName() + " ya utilizaba threat adaptativo.");
    }

    private static int hudStatus(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        return feedback(source, "HUD de threat para " + player.getGameProfile().getName() + ": "
                + (runtime.isHudEnabled(player) ? "activado" : "oculto"));
    }

    private static int setHud(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            boolean enabled) {
        runtime.setHudEnabled(player, enabled);
        return feedback(source, "HUD de threat " + (enabled ? "activado" : "oculto")
                + " para " + player.getGameProfile().getName() + ".");
    }

    private static int start(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterManager.StartResult result = runtime.encounters().forceStart(
                player, runtime.config(), runtime.governor().limits());
        return result.started()
                ? feedback(source, "Encuentro " + result.encounterId() + " iniciado con "
                        + result.members() + " mobs y budget " + result.threatBudget())
                : error(source, "No se pudo iniciar: " + result.reason());
    }

    private static int cancel(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        return runtime.encounters().cancel(player.getUuid(), runtime.server().getTicks())
                ? feedback(source, "Encuentro enviado a Relief.")
                : error(source, "Ese jugador no tiene un encuentro activo.");
    }

    private static int cancelAll(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        int count = runtime.encounters().cancelAll(runtime.server().getTicks());
        return feedback(source, "Encuentros enviados a Relief: " + count);
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
            return error(source, "Fase desconocida: " + rawState);
        }
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "Ese jugador no tiene un encuentro activo.");
        }
        return controller.forceState(
                state,
                runtime.server().getTicks(),
                runtime.observations().state(controller.targetPlayerId()).intensity())
                ? feedback(source, "Fase forzada a " + state)
                : error(source, "No se pudo cambiar la fase.");
    }

    private static int forceScene(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player,
            String rawState) {
        EncounterState requested = testScene(rawState);
        if (requested == null) {
            return error(source, "Escena desconocida: " + rawState
                    + ". Opciones: " + String.join(", ", TEST_SCENES));
        }

        boolean startedNow = false;
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            EncounterManager.StartResult result = runtime.encounters().forceStart(
                    player, runtime.config(), runtime.governor().limits());
            if (!result.started()) {
                return error(source, "No se pudo preparar la escena: " + result.reason());
            }
            startedNow = true;
            controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
            if (controller == null) {
                return error(source, "El encuentro se inició pero su controlador no está disponible.");
            }
        }

        PlayerDirectorState encounterState = runtime.observations().state(controller.targetPlayerId());
        boolean changed = controller.forceSceneState(
                requested,
                runtime.server().getTicks(),
                encounterState.intensity());
        if (!changed) {
            return error(source, "No se pudo forzar la escena " + requested + ".");
        }
        runtime.refreshHud();

        return feedback(source, String.format(Locale.ROOT,
                "Escena %s forzada para %s%s: threat %.1f, %d mobs, budget %d.",
                requested,
                player.getGameProfile().getName(),
                startedNow ? " (encuentro creado)" : "",
                encounterState.intensity(),
                controller.livingCount(),
                controller.budget().total()));
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
            return error(source, "No hay encuentro activo.");
        }
        String roles = controller.blackboard().roles().entrySet().stream()
                .map(entry -> entry.getKey().toString().substring(0, 8) + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
        return feedback(source, "Roles: " + roles);
    }

    private static int sectors(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "No hay encuentro activo.");
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
        return feedback(source, "Posiciones tácticas visibles: "
                + controller.blackboard().tacticalPositions().size());
    }

    private static int budget(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        EncounterController controller = runtime.encounters().forPlayer(player.getUuid()).orElse(null);
        if (controller == null) {
            return error(source, "No hay encuentro activo.");
        }
        return feedback(source, "Threat Budget: total=" + controller.budget().total()
                + " (base=" + controller.budget().baseTotal()
                + ", reserva=" + controller.budget().reinforcementReserve() + ")"
                + ", mobs activos=" + controller.budget().spentOnMobs()
                + ", mobs retirados=" + controller.budget().retiredMobThreat()
                + ", patrones=" + controller.budget().reservedForPatterns()
                + ", restante=" + controller.budget().remaining());
    }

    private static int simulateBreach(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        ThreatDirectorConfig config = runtime.config();
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
        return feedback(source, "Breach SIMULADO: decisión=" + plan.decision()
                + ", motivo=" + plan.reason()
                + ", bloques=" + plan.blocksRequired()
                + ", coste=" + plan.budgetCost()
                + ", protección=" + plan.protection()
                + ", seguro=" + plan.safe()
                + ", nodos=" + plan.visitedNodes());
    }

    private static int reload(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        try {
            ThreatDirectorConfig config = runtime.reloadConfig();
            return feedback(source, "Configuración recargada: " + config.preset());
        } catch (IOException | RuntimeException exception) {
            return error(source, "Configuración rechazada; se conserva la anterior: " + exception.getMessage());
        }
    }

    private static int integrations(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        String ids = runtime.compatibility().integrationIds().isEmpty()
                ? "ninguna"
                : String.join(", ", runtime.compatibility().integrationIds());
        return feedback(source, "Integraciones cargadas=" + ids
                + ", fallidas=" + runtime.integrationReport().failedCount()
                + ", sistema de claims=" + runtime.claimSystemPresent());
    }

    private static int performance(ThreatDirectorRuntime runtime, ServerCommandSource source) {
        GovernorLimits limits = runtime.governor().limits();
        return feedback(source, String.format(Locale.ROOT,
                "PerformanceGovernor nivel %d, MSPT %.2f, intervalo %d, candidatos %d, "
                        + "encuentros activos %d, nuevos=%s, planner=%d ops/%d us",
                limits.level(),
                runtime.server().getTickTime(),
                limits.decisionIntervalTicks(),
                limits.candidateLimit(),
                runtime.encounters().active().size(),
                limits.allowNewEncounters() ? "sí" : "no",
                runtime.config().performance().maxPlannerOperationsPerTick(),
                runtime.config().performance().workBudgetMicrosPerTick()));
    }

    private static int resetPerformance(
            ThreatDirectorRuntime runtime, ServerCommandSource source) {
        int previous = runtime.governor().level();
        runtime.governor().reset(runtime.server().getTicks());
        return feedback(source, "PerformanceGovernor reiniciado de nivel " + previous
                + " a 0. La degradación ordinaria se observará de nuevo durante 100 ticks; "
                + "dos muestras de carga crítica aún activarán el nivel 6 de seguridad.");
    }

    private static BlockPoint point(net.minecraft.util.math.BlockPos position) {
        return new BlockPoint(position.getX(), position.getY(), position.getZ());
    }

    private static int feedback(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }

    private static int error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(message));
        return 0;
    }

    private record ThreatContext(
            PlayerDirectorState state, String ownerName, boolean groupThreat) {
    }
}
