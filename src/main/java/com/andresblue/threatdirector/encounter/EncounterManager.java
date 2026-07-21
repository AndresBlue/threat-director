package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.ai.DirectorAiInstaller;
import com.andresblue.threatdirector.ai.MobOrderStore;
import com.andresblue.threatdirector.budget.ThreatBudget;
import com.andresblue.threatdirector.budget.ThreatCostRegistry;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.director.EncounterTransition;
import com.andresblue.threatdirector.performance.GovernorLimits;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.server.PlayerObservationService;
import com.andresblue.threatdirector.telemetry.TelemetryEvent;
import com.andresblue.threatdirector.telemetry.TelemetryRecorder;
import com.andresblue.threatdirector.trait.MobTraitRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EncounterManager {
    private final MinecraftServer server;
    private final PlayerObservationService observations;
    private final MobOrderStore orderStore;
    private final DirectorAiInstaller aiInstaller;
    private final TelemetryRecorder telemetry;
    private final Map<UUID, EncounterController> encounters = new HashMap<>();
    private final Map<UUID, UUID> encounterByPlayer = new HashMap<>();
    private final MobOwnershipIndex mobOwnership = new MobOwnershipIndex();
    private int plannerScheduleCursor;
    private long planningBudgetTick = Long.MIN_VALUE;
    private int remainingPlanningOperations;
    private long planningDeadlineNanos;

    public EncounterManager(
            MinecraftServer server,
            PlayerObservationService observations,
            MobOrderStore orderStore,
            DirectorAiInstaller aiInstaller,
            TelemetryRecorder telemetry) {
        this.server = server;
        this.observations = observations;
        this.orderStore = orderStore;
        this.aiInstaller = aiInstaller;
        this.telemetry = telemetry;
    }

    public void scanForEncounters(ThreatDirectorConfig config, GovernorLimits limits) {
        if (!config.enabled() || !limits.allowNewEncounters()) {
            return;
        }
        int controllerCap = Math.max(1, (int) Math.floor(
                config.encounter().maxActiveEncounters() * limits.activeControllerFactor()));
        if (encounters.size() >= controllerCap) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (encounters.size() >= controllerCap) {
                break;
            }
            PlayerDirectorState state = observations.state(player.getUuid());
            Optional<String> rejection = eligibilityRejection(player, state, config, false);
            if (rejection.isPresent()) {
                continue;
            }
            if (!isPreferredGroupTarget(player, config)) {
                continue;
            }
            if (player.getRandom().nextDouble() > config.director().encounterChancePerScan()) {
                continue;
            }
            int populationBudget = reserveRemainingPlanningBudget(config);
            if (populationBudget <= 0) {
                break;
            }
            start(player, state, config, limits, false, populationBudget);
            break;
        }
    }

    public StartResult forceStart(
            ServerPlayerEntity player,
            ThreatDirectorConfig config,
            GovernorLimits limits) {
        if (!limits.allowNewEncounters() || limits.vanillaOnly()) {
            return StartResult.rejected("performance governor is not accepting new encounters");
        }
        int controllerCap = Math.max(1, (int) Math.floor(
                config.encounter().maxActiveEncounters() * limits.activeControllerFactor()));
        if (encounters.size() >= controllerCap) {
            return StartResult.rejected("active encounter safety cap has been reached");
        }
        int populationBudget = reserveRemainingPlanningBudget(config);
        if (populationBudget <= 0) {
            return StartResult.rejected("planner work budget is exhausted for this tick");
        }
        return start(
                player,
                observations.state(player.getUuid()),
                config,
                limits,
                true,
                populationBudget);
    }

    private StartResult start(
            ServerPlayerEntity player,
            PlayerDirectorState state,
            ThreatDirectorConfig config,
            GovernorLimits limits,
            boolean forced,
            int populationPlannerBudget) {
        Optional<String> rejection = eligibilityRejection(player, state, config, forced);
        if (rejection.isPresent()) {
            return StartResult.rejected(rejection.get());
        }

        double intensity = forced
                ? Math.max(config.director().minimumIntensity(), Math.max(55.0, state.intensity()))
                : state.intensity();
        int nearbySurvivalPlayers = nearbySurvivalPlayerCount(player, config);
        double groupMultiplier = ThreatBudget.diminishingGroupMultiplier(
                nearbySurvivalPlayers,
                config.multiplayer().additionalPlayerScale(),
                config.multiplayer().maxGroupMultiplier());
        int groupMobCap = ThreatBudget.scaledMobCap(
                config.encounter().maxMobs(),
                config.encounter().hardMaxMobs(),
                groupMultiplier);
        ThreatBudget budget = ThreatBudget.forIntensity(
                intensity,
                groupMobCap,
                limits.threatBudgetFactor(),
                0,
                groupMultiplier);
        if (budget.mobCountCap() < config.encounter().minMobs()) {
            return StartResult.rejected("threat budget is below the minimum encounter size");
        }

        UUID id = UUID.randomUUID();
        ThreatCostRegistry costs = costRegistry(config);
        EncounterPopulationService populationService = new EncounterPopulationService(
                config, costs, MobTraitRegistry.vanillaMvp());
        EncounterPopulation population = populationService.populate(
                player.getServerWorld(),
                player,
                budget,
                mobOwnership.ownedMobIds(),
                groupMobCap,
                populationPlannerBudget);
        if (!population.meetsMinimum(config.encounter().minMobs())) {
            String reason = population.diagnostics().isEmpty()
                    ? "safe population could not be formed"
                    : String.join("; ", population.diagnostics());
            return StartResult.rejected(reason);
        }
        List<UUID> memberIds = population.members().stream()
                .map(member -> member.profile().id())
                .toList();
        if (!mobOwnership.claimAll(id, memberIds)) {
            populationService.rollbackPopulation(player.getServerWorld(), budget, population);
            return StartResult.rejected("encounter population conflicted with global mob ownership");
        }

        long tick = server.getTicks();
        EncounterController controller;
        try {
            controller = new EncounterController(
                    id,
                    player.getServerWorld(),
                    player.getUuid(),
                    population,
                    budget,
                    config,
                    orderStore,
                    aiInstaller,
                    candidate -> candidate.getUuid().equals(player.getUuid())
                            ? isCombatSafe(candidate, config)
                            : isActiveCombatTargetEligible(candidate, config),
                    transition -> recordTransition(transition, config),
                    intensity,
                    tick);
        } catch (RuntimeException | Error initializationFailure) {
            mobOwnership.releaseEncounter(id);
            populationService.rollbackPopulation(player.getServerWorld(), budget, population);
            throw initializationFailure;
        }
        encounters.put(id, controller);
        encounterByPlayer.put(player.getUuid(), id);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter", id.toString());
        attributes.put("intensity", intensity);
        attributes.put("members", population.members().size());
        attributes.put("recruited", population.recruitedCount());
        attributes.put("spawned", population.spawnedCount());
        attributes.put("threatBudget", budget.total());
        attributes.put("nearbySurvivalPlayers", nearbySurvivalPlayers);
        attributes.put("groupMultiplier", groupMultiplier);
        attributes.put("forced", forced);
        if (config.telemetry().includePlayerUuid()) {
            attributes.put("player", player.getUuidAsString());
        }
        telemetry.record(TelemetryEvent.now("encounter.started", attributes));
        ThreatDirectorMod.LOGGER.info(
                "Started encounter {} for {} with {} mobs at intensity {}",
                id,
                player.getGameProfile().getName(),
                population.members().size(),
                String.format(java.util.Locale.ROOT, "%.1f", intensity));
        return StartResult.started(id, population.members().size(), budget.total());
    }

    public void tick(ThreatDirectorConfig config, GovernorLimits limits) {
        long tick = server.getTicks();
        // Level 6 must still deliver the emergency shed signal to active controllers.
        int interval = limits.vanillaOnly() ? 20 : Math.max(1, limits.decisionIntervalTicks());
        ensurePlanningBudget(config);
        int remainingPlannerOperations = remainingPlanningOperations;
        long workDeadline = planningDeadlineNanos;
        List<EncounterController> completed = new ArrayList<>();
        List<EncounterController> scheduled = new ArrayList<>(encounters.values());
        scheduled.sort((first, second) -> first.encounterId().compareTo(second.encounterId()));
        int scheduleStart = scheduled.isEmpty()
                ? 0
                : Math.floorMod(plannerScheduleCursor, scheduled.size());
        boolean processedController = false;
        for (int index = 0; index < scheduled.size(); index++) {
            EncounterController controller = scheduled.get(
                    (scheduleStart + index) % scheduled.size());
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(
                    controller.targetPlayerId());
            boolean urgentLowHealth = target != null && !meetsMinimumHealth(
                    target.getHealth(),
                    target.getMaxHealth(),
                    config.intensity().minimumHealthRatio());
            boolean urgentPrimarySafety = target == null
                    || !isCombatSafe(target, config);
            boolean urgentAssignedTargetSafety = orderStore.targetPlayerIds(
                            controller.encounterId()).stream()
                    .map(playerId -> server.getPlayerManager().getPlayer(playerId))
                    .anyMatch(player -> player == null
                            || !(player.getUuid().equals(controller.targetPlayerId())
                                    ? isCombatSafe(player, config)
                                    : isActiveCombatTargetEligible(player, config)));
            PlayerDirectorState playerState = observations.state(controller.targetPlayerId());
            if (urgentLowHealth) {
                controller.cancel(tick, playerState.intensity());
            }
            if (!urgentPrimarySafety
                    && !urgentAssignedTargetSafety
                    && Math.floorMod(tick + controller.encounterId().hashCode(), interval) != 0L) {
                continue;
            }
            processedController = true;
            int controllerPlannerBudget = System.nanoTime() - workDeadline >= 0L
                    ? 0
                    : remainingPlannerOperations;
            controller.tick(
                    tick,
                    playerState,
                    limits.level(),
                    interval,
                    controllerPlannerBudget);
            remainingPlannerOperations = Math.max(
                    0, remainingPlannerOperations - controller.lastPlannerOperationsUsed());
            mobOwnership.retainForEncounter(controller.encounterId(), controller.memberIds());
            if (controller.finished()) {
                completed.add(controller);
            }
        }
        if (processedController && !scheduled.isEmpty()) {
            plannerScheduleCursor = scheduleStart + 1 == scheduled.size()
                    ? 0
                    : scheduleStart + 1;
        }
        remainingPlanningOperations = remainingPlannerOperations;
        for (EncounterController controller : completed) {
            finish(controller, config, tick);
        }
    }

    private void finish(EncounterController controller, ThreatDirectorConfig config, long tick) {
        encounters.remove(controller.encounterId());
        encounterByPlayer.remove(controller.targetPlayerId(), controller.encounterId());
        mobOwnership.releaseEncounter(controller.encounterId());
        long minimum = config.director().cooldownMinTicks();
        long maximum = config.director().cooldownMaxTicks();
        long cooldown = minimum + (maximum > minimum
                ? server.getOverworld().random.nextInt((int) (maximum - minimum + 1L))
                : 0L);
        observations.state(controller.targetPlayerId()).markEncounterEnded(tick, cooldown);
        telemetry.record(TelemetryEvent.now("encounter.finished", Map.of(
                "encounter", controller.encounterId().toString(),
                "cooldownTicks", cooldown)));
    }

    private Optional<String> eligibilityRejection(
            ServerPlayerEntity player,
            PlayerDirectorState state,
            ThreatDirectorConfig config,
            boolean forced) {
        if (!config.enabled()) {
            return Optional.of("Threat Director is disabled");
        }
        if (!isSurvivalPlayer(player)) {
            return Optional.of("target is not an eligible survival player");
        }
        if (!meetsMinimumHealth(
                player.getHealth(),
                player.getMaxHealth(),
                config.intensity().minimumHealthRatio())) {
            return Optional.of("target health is below the configured safety ratio");
        }
        if (state.recentlyDied(
                server.getTicks(), config.intensity().recentDeathCooldownTicks())) {
            return Optional.of("target is inside the recent-death safety window");
        }
        if (player.getServerWorld().getDifficulty() == Difficulty.PEACEFUL) {
            return Optional.of("world difficulty is Peaceful");
        }
        if (!config.director().allowedDimensions().contains(
                player.getServerWorld().getRegistryKey().getValue().toString())) {
            return Optional.of("dimension is not enabled");
        }
        if (encounterByPlayer.containsKey(player.getUuid())) {
            return Optional.of("player already has an active encounter");
        }
        if (nearActiveTarget(player, config.multiplayer().groupRadius())) {
            return Optional.of("nearby group already has an active encounter");
        }
        if (inSpawnProtection(player, config.breachSimulation().spawnProtectionRadius())) {
            return Optional.of("player is inside spawn protection");
        }
        if (!forced && state.onCooldown(server.getTicks())) {
            return Optional.of("player is on encounter cooldown");
        }
        if (!forced && state.intensity() < config.director().minimumIntensity()) {
            return Optional.of("Adventure Intensity is below the configured threshold");
        }
        if (!forced && config.director().requireNightOrDarkness()
                && !player.getServerWorld().isNight()
                && player.getServerWorld().getLightLevel(player.getBlockPos()) > 7) {
            return Optional.of("neither night nor darkness conditions are met");
        }
        return Optional.empty();
    }

    private boolean nearActiveTarget(ServerPlayerEntity player, double radius) {
        double radiusSquared = radius * radius;
        for (UUID targetId : encounterByPlayer.keySet()) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
            if (target != null
                    && target.getServerWorld() == player.getServerWorld()
                    && target.squaredDistanceTo(player) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreferredGroupTarget(
            ServerPlayerEntity candidate, ThreatDirectorConfig config) {
        double radiusSquared = config.multiplayer().groupRadius()
                * config.multiplayer().groupRadius();
        double candidatePower = powerScore(observations.state(candidate.getUuid()));
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (other == candidate
                    || other.getServerWorld() != candidate.getServerWorld()
                    || other.squaredDistanceTo(candidate) > radiusSquared
                    || eligibilityRejection(
                                    other,
                                    observations.state(other.getUuid()),
                                    config,
                                    false)
                            .isPresent()) {
                continue;
            }
            double otherPower = powerScore(observations.state(other.getUuid()));
            if (otherPower > candidatePower
                    || (Double.compare(otherPower, candidatePower) == 0
                            && other.getUuid().compareTo(candidate.getUuid()) < 0)) {
                return false;
            }
        }
        return true;
    }

    private int nearbySurvivalPlayerCount(
            ServerPlayerEntity target, ThreatDirectorConfig config) {
        double radiusSquared = square(config.multiplayer().groupRadius());
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() == target.getServerWorld()
                    && isActiveCombatTargetEligible(player, config)
                    && player.squaredDistanceTo(target) <= radiusSquared) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static boolean isSurvivalPlayer(ServerPlayerEntity player) {
        return player.isAlive()
                && player.interactionManager.getGameMode() == GameMode.SURVIVAL;
    }

    private boolean isActiveCombatTargetEligible(
            ServerPlayerEntity player, ThreatDirectorConfig config) {
        return isCombatSafe(player, config)
                && !observations.state(player.getUuid()).onCooldown(server.getTicks());
    }

    private boolean isCombatSafe(
            ServerPlayerEntity player, ThreatDirectorConfig config) {
        return isSurvivalPlayer(player)
                && meetsMinimumHealth(
                        player.getHealth(),
                        player.getMaxHealth(),
                        config.intensity().minimumHealthRatio())
                && !observations.state(player.getUuid()).recentlyDied(
                        server.getTicks(), config.intensity().recentDeathCooldownTicks())
                && !inSpawnProtection(player, config.breachSimulation().spawnProtectionRadius());
    }

    private static double square(double value) {
        return value * value;
    }

    private static double powerScore(PlayerDirectorState state) {
        return state.powerReport() == null ? Double.NEGATIVE_INFINITY : state.powerReport().score();
    }

    static boolean meetsMinimumHealth(
            double currentHealth, double maximumHealth, double minimumRatio) {
        if (!Double.isFinite(currentHealth)
                || !Double.isFinite(maximumHealth)
                || maximumHealth <= 0.0) {
            return false;
        }
        return currentHealth / maximumHealth >= minimumRatio;
    }

    private static boolean inSpawnProtection(ServerPlayerEntity player, int radius) {
        if (player.getServerWorld().getRegistryKey() != World.OVERWORLD) {
            return false;
        }
        BlockPos spawn = player.getServerWorld().getSpawnPos();
        return Math.abs(player.getBlockX() - spawn.getX()) <= radius
                && Math.abs(player.getBlockZ() - spawn.getZ()) <= radius;
    }

    private static ThreatCostRegistry costRegistry(ThreatDirectorConfig config) {
        Map<String, Integer> costs = new HashMap<>();
        config.encounter().threatCosts().forEach((id, cost) ->
                costs.put(id, (int) Math.round(cost * 1_000.0)));
        return new ThreatCostRegistry(costs);
    }

    private void ensurePlanningBudget(ThreatDirectorConfig config) {
        long tick = server.getTicks();
        if (planningBudgetTick == tick) {
            return;
        }
        planningBudgetTick = tick;
        remainingPlanningOperations = Math.max(
                0, config.performance().maxPlannerOperationsPerTick());
        long workBudgetNanos = Math.max(
                0L, (long) config.performance().workBudgetMicrosPerTick()) * 1_000L;
        planningDeadlineNanos = System.nanoTime() + workBudgetNanos;
    }

    private int reserveRemainingPlanningBudget(ThreatDirectorConfig config) {
        ensurePlanningBudget(config);
        if (System.nanoTime() - planningDeadlineNanos >= 0L) {
            return 0;
        }
        int reserved = remainingPlanningOperations;
        remainingPlanningOperations = 0;
        return reserved;
    }

    private void recordTransition(EncounterTransition transition, ThreatDirectorConfig config) {
        ThreatDirectorMod.LOGGER.info(
                "Encounter {}: {} -> {} ({})",
                transition.encounterId(),
                transition.from(),
                transition.to(),
                transition.cause());
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("encounter", transition.encounterId().toString());
        attributes.put("from", transition.from().name());
        attributes.put("to", transition.to().name());
        attributes.put("cause", transition.cause().name());
        attributes.put("serverTick", transition.serverTick());
        attributes.put("intensity", transition.adventureIntensity());
        attributes.put("remainingThreat", transition.remainingThreat());
        if (config.telemetry().includePlayerUuid()) {
            attributes.put("player", transition.targetPlayer().toString());
        }
        telemetry.record(TelemetryEvent.now("encounter.transition", attributes));
    }

    public Optional<EncounterController> forPlayer(UUID playerId) {
        UUID encounterId = encounterByPlayer.get(playerId);
        return Optional.ofNullable(encounterId == null ? null : encounters.get(encounterId));
    }

    public Optional<EncounterController> byId(UUID encounterId) {
        return Optional.ofNullable(encounters.get(encounterId));
    }

    public Collection<EncounterController> active() {
        return List.copyOf(encounters.values());
    }

    public boolean cancel(UUID playerId, long tick) {
        EncounterController controller = forPlayer(playerId).orElse(null);
        if (controller == null) {
            return false;
        }
        controller.cancel(tick, observations.state(playerId).intensity());
        return true;
    }

    public int cancelAll(long tick) {
        int count = encounters.size();
        encounters.values().forEach(controller -> controller.cancel(
                tick, observations.state(controller.targetPlayerId()).intensity()));
        return count;
    }

    public int disable(ThreatDirectorConfig config, long tick) {
        int count = encounters.size();
        for (EncounterController controller : List.copyOf(encounters.values())) {
            controller.close();
            observations.state(controller.targetPlayerId()).markEncounterEnded(
                    tick, config.director().cooldownMinTicks());
        }
        encounters.clear();
        encounterByPlayer.clear();
        mobOwnership.clear();
        orderStore.clearAll();
        return count;
    }

    public void close() {
        encounters.values().forEach(EncounterController::close);
        encounters.clear();
        encounterByPlayer.clear();
        mobOwnership.clear();
        orderStore.clearAll();
    }

    /** Server-thread ownership index; claimAll is all-or-nothing for a population. */
    static final class MobOwnershipIndex {
        private final Map<UUID, UUID> ownerByMob = new HashMap<>();

        boolean claimAll(UUID encounterId, Collection<UUID> mobIds) {
            Objects.requireNonNull(encounterId, "encounterId");
            Set<UUID> uniqueMobIds = Set.copyOf(Objects.requireNonNull(mobIds, "mobIds"));
            for (UUID mobId : uniqueMobIds) {
                UUID owner = ownerByMob.get(mobId);
                if (owner != null && !owner.equals(encounterId)) {
                    return false;
                }
            }
            uniqueMobIds.forEach(mobId -> ownerByMob.put(mobId, encounterId));
            return true;
        }

        Set<UUID> ownedMobIds() {
            return Set.copyOf(ownerByMob.keySet());
        }

        Optional<UUID> ownerOf(UUID mobId) {
            return Optional.ofNullable(ownerByMob.get(mobId));
        }

        void retainForEncounter(UUID encounterId, Collection<UUID> retainedMobIds) {
            Set<UUID> retained = Set.copyOf(retainedMobIds);
            ownerByMob.entrySet().removeIf(entry -> entry.getValue().equals(encounterId)
                    && !retained.contains(entry.getKey()));
        }

        void releaseEncounter(UUID encounterId) {
            ownerByMob.entrySet().removeIf(entry -> entry.getValue().equals(encounterId));
        }

        int size() {
            return ownerByMob.size();
        }

        void clear() {
            ownerByMob.clear();
        }
    }

    public record StartResult(
            boolean started,
            String reason,
            UUID encounterId,
            int members,
            int threatBudget) {
        static StartResult rejected(String reason) {
            return new StartResult(false, reason, null, 0, 0);
        }

        static StartResult started(UUID encounterId, int members, int threatBudget) {
            return new StartResult(true, "started", encounterId, members, threatBudget);
        }
    }
}
