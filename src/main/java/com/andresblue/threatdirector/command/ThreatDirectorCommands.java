package com.andresblue.threatdirector.command;

import com.andresblue.threatdirector.breach.BreachPlan;
import com.andresblue.threatdirector.breach.BreachPlanner;
import com.andresblue.threatdirector.breach.BreachPolicy;
import com.andresblue.threatdirector.breach.BreachRequest;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.encounter.EncounterController;
import com.andresblue.threatdirector.encounter.EncounterManager;
import com.andresblue.threatdirector.performance.GovernorLimits;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.server.MinecraftBreachWorldView;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.server.ThreatDirectorRuntime;
import com.andresblue.threatdirector.tactical.BlockPoint;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
                                context.getSource())))
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
        Optional<EncounterController> encounter = runtime.encounters().forPlayer(player.getUuid());
        long cooldown = Math.max(0L, state.cooldownUntilTick() - runtime.server().getTicks());
        String active = encounter.map(value -> value.state() + " / " + value.livingCount() + " mobs")
                .orElse("ninguno");
        return feedback(source, String.format(Locale.ROOT,
                "%s: intensidad %.1f (%s), poder %.1f, encuentro %s, cooldown %d ticks",
                player.getGameProfile().getName(),
                state.intensity(),
                state.band(),
                state.powerReport() == null ? 0.0 : state.powerReport().score(),
                active,
                cooldown));
    }

    private static int intensity(
            ThreatDirectorRuntime runtime,
            ServerCommandSource source,
            ServerPlayerEntity player) {
        PlayerDirectorState state = runtime.observations().state(player.getUuid());
        if (state.breakdown() == null || state.powerReport() == null) {
            return feedback(source, "Todavía no existe una muestra de intensidad para ese jugador.");
        }
        return feedback(source, String.format(Locale.ROOT,
                "Intensidad %.2f (objetivo %.2f): poder %.2f, entorno %.2f, preparación %.2f, dominio %.2f, estrés %.2f, carga %.2f",
                state.intensity(),
                state.breakdown().targetIntensity(),
                state.powerReport().score(),
                state.breakdown().environment(),
                state.breakdown().readiness(),
                state.breakdown().dominance(),
                state.breakdown().distress(),
                state.breakdown().load()));
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
                runtime.observations().state(player.getUuid()).intensity())
                ? feedback(source, "Fase forzada a " + state)
                : error(source, "No se pudo cambiar la fase.");
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
                + ", mobs=" + controller.budget().spentOnMobs()
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
                "PerformanceGovernor nivel %d, MSPT %.2f, intervalo %d, candidatos %d, encuentros activos %d",
                limits.level(),
                runtime.server().getTickTime(),
                limits.decisionIntervalTicks(),
                limits.candidateLimit(),
                runtime.encounters().active().size()));
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
}
