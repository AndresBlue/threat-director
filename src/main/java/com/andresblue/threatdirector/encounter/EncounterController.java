package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.ai.DirectorAiInstaller;
import com.andresblue.threatdirector.ai.MobOrder;
import com.andresblue.threatdirector.ai.MobOrderStore;
import com.andresblue.threatdirector.budget.ThreatBudget;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.director.EncounterDurations;
import com.andresblue.threatdirector.director.EncounterRuntime;
import com.andresblue.threatdirector.director.EncounterSnapshot;
import com.andresblue.threatdirector.director.EncounterState;
import com.andresblue.threatdirector.director.EncounterStateMachine;
import com.andresblue.threatdirector.director.EncounterTransition;
import com.andresblue.threatdirector.director.TransitionCause;
import com.andresblue.threatdirector.director.TransitionResult;
import com.andresblue.threatdirector.pattern.PatternContext;
import com.andresblue.threatdirector.pattern.PatternEvaluation;
import com.andresblue.threatdirector.pattern.PatternExecutionResult;
import com.andresblue.threatdirector.pattern.PatternExecutionSnapshot;
import com.andresblue.threatdirector.pattern.PatternId;
import com.andresblue.threatdirector.pattern.PatternLifecycle;
import com.andresblue.threatdirector.pattern.PatternSelector;
import com.andresblue.threatdirector.role.TacticalRole;
import com.andresblue.threatdirector.server.PlayerDirectorState;
import com.andresblue.threatdirector.tactical.BlockPoint;
import com.andresblue.threatdirector.tactical.CandidatePosition;
import com.andresblue.threatdirector.tactical.EscapeDirectionProbe;
import com.andresblue.threatdirector.tactical.EscapeRoutePrediction;
import com.andresblue.threatdirector.tactical.EscapeRoutePredictor;
import com.andresblue.threatdirector.tactical.SectorPlan;
import com.andresblue.threatdirector.tactical.SectorPlanner;
import com.andresblue.threatdirector.tactical.SectorPlanningRequest;
import com.andresblue.threatdirector.tactical.TacticalSector;
import com.andresblue.threatdirector.tactical.Vec2;
import com.andresblue.threatdirector.trait.MobProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class EncounterController {
    public static final String MANAGED_TAG = "threatdirector_managed";
    static final long FULLY_UNLOADED_ROSTER_GRACE_TICKS = 200L;

    private final UUID encounterId;
    private final ServerWorld world;
    private final UUID targetPlayerId;
    private final ThreatDirectorConfig config;
    private final ThreatBudget budget;
    private final Map<UUID, Integer> memberCosts = new LinkedHashMap<>();
    private final Map<UUID, MobProfile> memberProfiles = new LinkedHashMap<>();
    private final Set<UUID> directorSpawnedMobIds = new java.util.HashSet<>();
    private final GroupBlackboard blackboard = new GroupBlackboard();
    private final MobOrderStore orderStore;
    private final DirectorAiInstaller aiInstaller;
    private final Predicate<ServerPlayerEntity> combatTargetEligibility;
    private final EncounterStateMachine stateMachine;
    private final PatternSelector patternSelector = new PatternSelector();
    private final EscapeRoutePredictor escapeRoutePredictor = new EscapeRoutePredictor();
    private final SectorPlanner sectorPlanner = new SectorPlanner();
    private final Consumer<EncounterTransition> transitionSink;
    private final Map<PatternId, Long> patternCooldowns = new EnumMap<>(PatternId.class);
    private final Vec3d encounterOrigin;

    private EncounterRuntime runtime;
    private Optional<PatternId> previousPattern = Optional.empty();
    private final int initialThreat;
    private final Map<UUID, Double> lastPathQualityByMob = new HashMap<>();
    private final Map<UUID, Long> patternArrivalTicks = new HashMap<>();
    private Vec3d lastPlanAnchor;
    private Vec3d activePatternAnchor;
    private EscapeRoutePrediction escapePrediction;
    private Vec2 previousMovementDirection;
    private double movementPersistence;
    private double lastPlanAveragePathQuality;
    private int consecutiveReplanFailures;
    private long activePatternStartTick = Long.MIN_VALUE;
    private long targetStationarySinceTick = Long.MIN_VALUE;
    private long nextStalkingCueTick;
    private boolean cleanupComplete;
    private boolean finished;
    private long lastReinforcementTick;
    private int reinforcementWaves;
    private int performanceLevel;
    private int orderLifetimeTicks = 100;
    private int plannerOperationBudget;
    private int lastPlannerOperationsUsed;
    private Set<UUID> lastRoleMemberIds = Set.of();
    private boolean targetPerceivedThisTick;
    private EncounterPressure currentPressure;
    private long fullyUnloadedSinceTick = Long.MIN_VALUE;

    public EncounterController(
            UUID encounterId,
            ServerWorld world,
            UUID targetPlayerId,
            EncounterPopulation population,
            ThreatBudget budget,
            ThreatDirectorConfig config,
            MobOrderStore orderStore,
            DirectorAiInstaller aiInstaller,
            Predicate<ServerPlayerEntity> combatTargetEligibility,
            Consumer<EncounterTransition> transitionSink,
            double startingIntensity,
            long startTick) {
        this.encounterId = encounterId;
        this.world = world;
        this.targetPlayerId = targetPlayerId;
        this.budget = budget;
        this.config = config;
        this.orderStore = orderStore;
        this.aiInstaller = aiInstaller;
        this.combatTargetEligibility = combatTargetEligibility;
        this.transitionSink = transitionSink;
        this.stateMachine = new EncounterStateMachine(durations(config));
        this.runtime = new EncounterRuntime(EncounterState.DORMANT, startTick);
        ServerPlayerEntity initialTarget = target();
        this.encounterOrigin = initialTarget == null ? Vec3d.ZERO : initialTarget.getPos();

        List<MobProfile> profiles = population.members().stream()
                .map(EncounterPopulation.Member::profile)
                .toList();
        Map<UUID, TacticalRole> roles = new VanillaRoleAssignment().assign(profiles);
        int threat = 0;
        for (EncounterPopulation.Member member : population.members()) {
            UUID mobId = member.profile().id();
            memberCosts.put(mobId, member.threatCost());
            memberProfiles.put(mobId, member.profile());
            if (member.origin() == EncounterPopulation.Origin.DIRECTOR_SPAWNED) {
                directorSpawnedMobIds.add(mobId);
            }
            threat += member.threatCost();
            blackboard.assignRole(mobId, roles.getOrDefault(mobId, TacticalRole.VANGUARD));
            resolveMob(mobId).ifPresent(mob -> {
                mob.addCommandTag(MANAGED_TAG);
                aiInstaller.install(mob);
            });
        }
        // Initial recruitment can spend only baseTotal. Reinforcements may consume the dedicated
        // lifetime reserve once the committed initial roster is fully registered.
        budget.unlockReinforcementReserve();
        this.lastRoleMemberIds = Set.copyOf(memberCosts.keySet());
        this.initialThreat = threat;
        this.lastReinforcementTick = startTick;
        this.nextStalkingCueTick = startTick + 60L;
        start(startTick, startingIntensity);
    }

    public void tick(
            long tick,
            PlayerDirectorState playerState,
            int governorLevel,
            int decisionIntervalTicks,
            int plannerOperationBudget) {
        if (finished) {
            return;
        }
        performanceLevel = governorLevel;
        orderLifetimeTicks = Math.max(40, decisionIntervalTicks + 20);
        this.plannerOperationBudget = Math.max(0, plannerOperationBudget);
        lastPlannerOperationsUsed = 0;
        ServerPlayerEntity target = target();
        if (target != null) {
            currentPressure = EncounterPressure.calculate(config, playerState, target);
        }
        List<MobEntity> living = livingMobs();
        if (governorLevel >= 6) {
            TransitionResult shed = stateMachine.step(runtime, snapshot(target, living, playerState, tick, false));
            applyTransition(shed);
            cleanup(true);
            cleanupComplete = true;
            finished = true;
            return;
        }
        living = releaseMembersAttackedByOtherPlayers(target, living);
        retireResolvedDeadMembers();
        RosterAvailability rosterAvailability = rosterAvailability(
                memberCosts.size(), living.size(), fullyUnloadedSinceTick, tick);
        fullyUnloadedSinceTick = rosterAvailability.fullyUnloadedSinceTick();
        if (rosterAvailability.withinGrace()
                && canAwaitFullyUnloadedRoster(target)) {
            // EntityLookup does not expose mobs whose chunks are unloaded. Keep their UUIDs,
            // threat allocations and global ownership while the chunks have a chance to return.
            targetPerceivedThisTick = false;
            return;
        }
        reassignRolesIfMembershipChanged(living);
        updatePerception(target, living, tick);

        if (governorLevel >= 5 && !"NONE".equals(blackboard.activePattern())) {
            finishActivePattern();
        }

        if (target != null && targetPerceivedThisTick && governorLevel < 5
                && planningPhase() && shouldReplan(target, tick)) {
            planPositions(target, living, playerState, tick);
        }
        evaluateActivePattern(target, living, tick);

        EncounterSnapshot snapshot = snapshot(target, living, playerState, tick, false);
        TransitionResult result = stateMachine.step(runtime, snapshot);
        applyTransition(result);

        if (runtime.state() == EncounterState.ABORTED && !cleanupComplete) {
            cleanup(true);
            cleanupComplete = true;
            result = stateMachine.step(runtime, snapshot(target, living, playerState, tick, false));
            applyTransition(result);
        }

        executePhase(target, living, tick);
        if (runtime.state() == EncounterState.COOLDOWN) {
            cleanup(true);
            finished = true;
        }
    }

    private void start(long tick, double intensity) {
        ServerPlayerEntity target = target();
        List<MobEntity> living = livingMobs();
        if (target != null) {
            blackboard.updateTarget(target.getPos(), predictedEscape(target), tick);
        }
        boolean valid = target != null && combatTargetEligibility.test(target);
        TransitionResult result = stateMachine.step(
                runtime,
                new EncounterSnapshot(
                        encounterId,
                        targetPlayerId,
                        tick,
                        intensity,
                        initialThreat,
                        initialThreat,
                        living.size(),
                        0,
                        0,
                        true,
                        valid,
                        target != null && target.getServerWorld() == world,
                        performanceLevel,
                        true,
                        target != null && inSpawnProtection(target),
                        0.0,
                        false,
                        false,
                        false,
                        false));
        applyTransition(result);
    }

    private void executePhase(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        switch (runtime.state()) {
            case STALKING, POSITIONING -> executePositioning(target, living, tick);
            case ENGAGING, CLIMAX -> executeEngagement(target, living, tick);
            case SEARCHING -> executeSearch(living, tick);
            case RELIEF, RETREATING -> executeRelief(target, living, tick);
            case ABORTED, COOLDOWN, DORMANT -> {
                // No tactical work.
            }
        }
    }

    private void executePositioning(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        if (target == null) {
            return;
        }
        for (int index = 0; index < living.size(); index++) {
            MobEntity mob = living.get(index);
            long orderNow = mob.getWorld().getTime();
            // Members without an expensive path-validated sector slot still advance toward a
            // shared rally ring instead of freezing when the planner slice is exhausted.
            int formationIndex = index;
            Vec3d destination = blackboard.position(mob.getUuid()).orElseGet(
                    () -> fallbackRallyPoint(target, mob, formationIndex, living.size()));
            if (mob.squaredDistanceTo(destination) <= 4.0) {
                orderStore.issue(mob, MobOrder.hold(encounterId, orderNow + orderDuration(40)));
            } else {
                double speed = currentPressure == null
                        ? 1.05
                        : 1.05 + currentPressure.hazard() * 0.15;
                orderStore.issue(mob, MobOrder.moveTo(
                        encounterId, destination, speed, orderNow + orderDuration(80)));
            }
        }
        if (runtime.state() == EncounterState.STALKING && tick >= nextStalkingCueTick) {
            BlockPos soundPosition = target.getBlockPos().add(
                    world.random.nextBetween(-10, 10),
                    world.random.nextBetween(-2, 3),
                    world.random.nextBetween(-10, 10));
            world.playSound(
                    null,
                    soundPosition,
                    SoundEvents.ENTITY_SKELETON_AMBIENT,
                    SoundCategory.HOSTILE,
                    0.45F,
                    0.75F + world.random.nextFloat() * 0.25F);
            nextStalkingCueTick = tick + 100L + world.random.nextInt(61);
        }
    }

    private void executeEngagement(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        if (target == null) {
            return;
        }
        if (!targetPerceivedThisTick) {
            Vec3d lastKnown = blackboard.lastKnownTargetPosition().orElse(null);
            for (MobEntity mob : living) {
                mob.setTarget(null);
                if (lastKnown != null) {
                    orderStore.issue(mob, MobOrder.moveTo(
                            encounterId,
                            lastKnown,
                            1.0,
                            mob.getWorld().getTime() + orderDuration(60)));
                }
            }
            return;
        }
        List<MobEntity> melee = living.stream()
                .filter(mob -> blackboard.role(mob.getUuid()).orElse(TacticalRole.VANGUARD)
                        != TacticalRole.HARASSER)
                .sorted(Comparator.comparing(mob -> mob.getUuid().toString()))
                .toList();
        int contextualMelee = currentPressure == null
                ? config.tactical().maxSimultaneousMeleeAttackers()
                : currentPressure.meleeAttackers();
        int meleeLimit = Math.min(contextualMelee, melee.size());
        int rotation = melee.isEmpty() ? 0 : (int) ((tick / 60L) % melee.size());
        List<ServerPlayerEntity> groupTargets = combatTargets(target, living);
        if (groupTargets.isEmpty()) {
            return;
        }

        Map<UUID, Integer> assignments = new HashMap<>();
        for (MobEntity mob : living) {
            long orderNow = mob.getWorld().getTime();
            TacticalRole role = blackboard.role(mob.getUuid()).orElse(TacticalRole.VANGUARD);
            boolean mayAttack = role == TacticalRole.HARASSER || selectedMelee(mob, melee, rotation, meleeLimit);
            if (mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()) < 0.3F && role != TacticalRole.HARASSER) {
                Vec3d retreat = retreatPoint(mob, target);
                mob.setTarget(null);
                orderStore.issue(mob, MobOrder.retreat(
                        encounterId, retreat, 1.1, orderNow + orderDuration(80)));
            } else if (mayAttack) {
                ServerPlayerEntity assignedTarget = selectGroupTarget(
                        groupTargets, assignments, mob, tick);
                assignments.merge(assignedTarget.getUuid(), 1, Integer::sum);
                orderStore.issue(mob, MobOrder.engage(
                        encounterId, assignedTarget.getUuid(), orderNow + orderDuration(60)));
                mob.setTarget(assignedTarget);
            } else {
                mob.setTarget(null);
                Vec3d hold = blackboard.position(mob.getUuid()).orElseGet(
                        () -> fallbackRallyPoint(
                                target, mob, living.indexOf(mob), living.size()));
                orderStore.issue(mob, MobOrder.moveTo(
                        encounterId,
                        hold,
                        currentPressure == null ? 1.0 : 1.0 + currentPressure.hazard() * 0.12,
                        orderNow + orderDuration(60)));
            }
        }
    }

    /**
     * Distributes attackers across visible group members without making the roster's UUID order a
     * predictable target rotation. The least-pressured player is preferred, while a per-mob/tick
     * tie breaker changes on replans. This keeps a three- or four-player raid interactive without
     * concentrating the whole horde on one player or multiplying damage linearly.
     */
    private ServerPlayerEntity selectGroupTarget(
            List<ServerPlayerEntity> candidates,
            Map<UUID, Integer> assignments,
            MobEntity mob,
            long tick) {
        int replanRound = (int) Math.max(0L, tick / 60L);
        return candidates.stream()
                .min(Comparator
                        .comparingInt((ServerPlayerEntity player) ->
                                assignments.getOrDefault(player.getUuid(), 0))
                        .thenComparingInt(player ->
                                Math.floorMod(player.getUuid().hashCode()
                                        ^ mob.getUuid().hashCode() ^ replanRound, 31))
                        .thenComparing(player -> player.getUuid().toString()))
                .orElse(candidates.get(0));
    }

    private void executeSearch(List<MobEntity> living, long tick) {
        Vec3d lastKnown = blackboard.lastKnownTargetPosition().orElse(null);
        if (lastKnown == null) {
            return;
        }
        for (MobEntity mob : living) {
            orderStore.issue(mob, MobOrder.moveTo(
                    encounterId, lastKnown, 1.0,
                    mob.getWorld().getTime() + orderDuration(60)));
        }
    }

    private void executeRelief(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        for (MobEntity mob : living) {
            mob.setTarget(null);
            Vec3d destination = target == null
                    ? mob.getPos()
                    : retreatPoint(mob, target);
            orderStore.issue(mob, MobOrder.retreat(
                    encounterId, destination, 1.05,
                    mob.getWorld().getTime() + orderDuration(80)));
        }
    }

    private void planPositions(
            ServerPlayerEntity target,
            List<MobEntity> living,
            PlayerDirectorState playerState,
            long tick) {
        updateEscapePrediction(target, living);
        choosePattern(target, living, playerState, tick);
        BlockPoint playerPoint = point(target.getBlockPos());
        Vec3d look = target.getRotationVec(1.0F);
        Vec3d velocity = target.getVelocity();
        Vec2 facing = new Vec2(look.x, look.z);
        Vec2 movement = new Vec2(velocity.x, velocity.z);
        ArrayList<BlockPoint> occupied = new ArrayList<>();
        List<MobEntity> orderedBase = living.stream()
                .sorted(Comparator.comparing(mob -> mob.getUuid().toString()))
                .toList();
        ArrayList<MobEntity> ordered = new ArrayList<>(orderedBase);
        if (!ordered.isEmpty()) {
            int replanInterval = currentPressure == null
                    ? config.tactical().replanIntervalTicks()
                    : currentPressure.replanIntervalTicks();
            int offset = (int) Math.floorMod(
                    tick / Math.max(1, replanInterval), ordered.size());
            java.util.Collections.rotate(ordered, -offset);
        }
        int remainingPathAttempts = Math.min(
                config.performance().maxPlannerOperationsPerTick(), plannerOperationBudget);
        int initialPathAttempts = remainingPathAttempts;
        int successfulPlans = 0;
        double pathQualityTotal = 0.0;
        lastPathQualityByMob.clear();

        for (int index = 0; index < ordered.size(); index++) {
            if (remainingPathAttempts <= 0) {
                break;
            }
            MobEntity mob = ordered.get(index);
            TacticalRole role = blackboard.role(mob.getUuid()).orElse(TacticalRole.VANGUARD);
            TacticalSector sector = sectorFor(role, index, blackboard.activePattern());
            Vec2 planningFacing = facing;
            if (escapePrediction != null && sector == TacticalSector.ESCAPE_PRIMARY) {
                planningFacing = escapePrediction.primary().scale(-1.0);
            } else if (escapePrediction != null && sector == TacticalSector.ESCAPE_SECONDARY) {
                planningFacing = escapePrediction.secondary().scale(-1.0);
            }
            MinecraftTerrainProbe terrain = new MinecraftTerrainProbe(
                    world,
                    mob,
                    target,
                    Math.min(
                            config.tactical().maxPathAttemptsPerMob()
                                    + (currentPressure != null
                                            && currentPressure.hazard() >= 0.70 ? 1 : 0),
                            remainingPathAttempts));
            int candidateLimit = Math.max(1, Math.min(
                    config.tactical().maxCandidatesPerSnapshot(),
                    performanceCandidateLimit(performanceLevel)));
            SectorPlan plan = sectorPlanner.plan(new SectorPlanningRequest(
                    playerPoint,
                    planningFacing,
                    movement,
                    point(mob.getBlockPos()),
                    sector,
                    role,
                    occupied,
                    candidateLimit,
                    candidateLimit * 4,
                    config.tactical().positionMinRadius(),
                    config.tactical().positionMaxRadius()), terrain);
            remainingPathAttempts -= terrain.pathAttempts();
            Optional<CandidatePosition> selected = plan.candidates().stream().findFirst();
            if (selected.isPresent()) {
                CandidatePosition candidate = selected.get();
                BlockPoint position = candidate.position();
                occupied.add(position);
                blackboard.reservePosition(mob.getUuid(), center(position));
                lastPathQualityByMob.put(mob.getUuid(), candidate.pathQuality());
                pathQualityTotal += candidate.pathQuality();
                successfulPlans++;
            }
        }

        if (!living.isEmpty() && successfulPlans == 0) {
            consecutiveReplanFailures++;
        } else {
            consecutiveReplanFailures = 0;
        }
        lastPlanAveragePathQuality = successfulPlans == 0 ? 0.0 : pathQualityTotal / successfulPlans;
        lastPlannerOperationsUsed = initialPathAttempts - remainingPathAttempts;

        blackboard.markPlan(blackboard.activePattern(), tick);
        lastPlanAnchor = target.getPos();
    }

    private void choosePattern(
            ServerPlayerEntity target,
            List<MobEntity> living,
            PlayerDirectorState state,
            long tick) {
        if (!"NONE".equals(blackboard.activePattern()) || performanceLevel >= 5) {
            return;
        }
        long mobileRoles = blackboard.roles().values().stream()
                .filter(role -> role == TacticalRole.FLANKER || role == TacticalRole.INTERCEPTOR)
                .count();
        int wingCandidates = (int) Math.max(mobileRoles, living.size() >= 4 ? 2 : mobileRoles);
        double speed = horizontalSpeed(target.getVelocity());
        double oppositePairDot = flankerOpposition(target);
        double openness = escapePrediction == null ? 0.0
                : (escapePrediction.primaryConfidence() + escapePrediction.secondaryConfidence()) / 2.0;
        long ready = living.stream().filter(mob -> mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()) >= 0.35F)
                .count();
        double roleReadiness = living.isEmpty() ? 0.0 : ready / (double) living.size();
        double interceptorPathQuality = living.stream()
                .filter(mob -> blackboard.role(mob.getUuid()).orElse(null) == TacticalRole.INTERCEPTOR)
                .mapToDouble(mob -> lastPathQualityByMob.getOrDefault(mob.getUuid(), 0.0))
                .max().orElse(0.0);
        PatternContext context = new PatternContext(
                runtime.state(),
                currentPressure == null
                        ? state.intensity()
                        : currentPressure.effectiveIntensity(),
                living.size(),
                wingCandidates,
                oppositePairDot,
                openness,
                roleReadiness,
                lastPlanAveragePathQuality,
                blackboard.roles().containsValue(TacticalRole.INTERCEPTOR),
                escapePrediction == null ? 0.0 : escapePrediction.primaryConfidence(),
                movementPersistence,
                interceptorPathQuality,
                speed > 0.03 ? movementPersistence : 0.0,
                budget.remaining(),
                tick,
                patternCooldowns,
                previousPattern);
        patternSelector.select(context).selected().ifPresent(evaluation -> activatePattern(evaluation, tick));
    }

    private void activatePattern(PatternEvaluation evaluation, long tick) {
        if (!budget.reservePattern(evaluation.id().name(), evaluation.threatCost())) {
            return;
        }
        previousPattern = Optional.of(evaluation.id());
        patternCooldowns.put(evaluation.id(), tick + evaluation.cooldownTicks());
        blackboard.markPlan(evaluation.id().name(), tick);
        activePatternStartTick = tick;
        activePatternAnchor = blackboard.lastKnownTargetPosition().orElse(lastPlanAnchor);
        patternArrivalTicks.clear();
    }

    private void evaluateActivePattern(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        if ("NONE".equals(blackboard.activePattern())) {
            return;
        }
        PatternId id;
        try {
            id = PatternId.valueOf(blackboard.activePattern());
        } catch (IllegalArgumentException exception) {
            blackboard.finishPattern();
            return;
        }

        List<MobEntity> flankers = living.stream()
                .filter(mob -> {
                    TacticalRole role = blackboard.role(mob.getUuid()).orElse(null);
                    return role == TacticalRole.FLANKER || role == TacticalRole.INTERCEPTOR;
                })
                .sorted(Comparator.comparing(mob -> mob.getUuid().toString()))
                .limit(2)
                .toList();
        for (MobEntity flanker : flankers) {
            if (atReservedPosition(flanker)) {
                patternArrivalTicks.putIfAbsent(flanker.getUuid(), tick);
            }
        }
        long arrivalDelta = flankers.size() < 2
                || !patternArrivalTicks.containsKey(flankers.get(0).getUuid())
                || !patternArrivalTicks.containsKey(flankers.get(1).getUuid())
                ? Long.MAX_VALUE
                : Math.abs(patternArrivalTicks.get(flankers.get(0).getUuid())
                        - patternArrivalTicks.get(flankers.get(1).getUuid()));

        MobEntity interceptor = living.stream()
                .filter(mob -> blackboard.role(mob.getUuid()).orElse(null) == TacticalRole.INTERCEPTOR)
                .findFirst().orElse(null);
        Vec3d waypoint = interceptor == null ? null : blackboard.position(interceptor.getUuid()).orElse(null);
        if (target != null && horizontalSpeed(target.getVelocity()) < 0.02) {
            if (targetStationarySinceTick == Long.MIN_VALUE) targetStationarySinceTick = tick;
        } else {
            targetStationarySinceTick = Long.MIN_VALUE;
        }
        long stationaryTicks = targetStationarySinceTick == Long.MIN_VALUE ? 0L : tick - targetStationarySinceTick;
        boolean contact = targetPerceivedThisTick;
        double interceptorDistance = interceptor == null || waypoint == null
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(interceptor.squaredDistanceTo(waypoint));
        boolean interceptorFirst = target != null && waypoint != null
                && interceptorDistance <= 3.0 && target.squaredDistanceTo(waypoint) > 9.0;
        boolean rearPressure = target != null && living.stream()
                .filter(mob -> mob != interceptor)
                .anyMatch(mob -> mob.squaredDistanceTo(target) <= 14.0 * 14.0);
        PatternExecutionSnapshot execution = new PatternExecutionSnapshot(
                activePatternStartTick == Long.MIN_VALUE ? 0L : tick - activePatternStartTick,
                runtime.state(),
                flankers.size() >= 2,
                consecutiveReplanFailures,
                target == null || activePatternAnchor == null
                        ? 0.0 : Math.sqrt(target.getPos().squaredDistanceTo(activePatternAnchor)),
                target != null && flankers.size() >= 2 && flankerOpposition(target) <= -0.75
                        && flankers.stream().allMatch(this::atReservedPosition),
                arrivalDelta,
                stationaryTicks,
                escapePrediction == null ? 0.0 : escapePrediction.primaryConfidence(),
                routeDeviationDegrees(target),
                waypoint != null && interceptor != null
                        && lastPathQualityByMob.getOrDefault(interceptor.getUuid(), 0.0) > 0.0,
                contact,
                interceptorDistance,
                interceptorFirst,
                rearPressure);
        PatternExecutionResult result = PatternLifecycle.evaluate(id, execution);
        if (result.status() != PatternExecutionResult.Status.RUNNING) {
            finishActivePattern();
        }
    }

    private void finishActivePattern() {
        if (!"NONE".equals(blackboard.activePattern())) {
            budget.releasePattern(blackboard.activePattern());
        }
        blackboard.finishPattern();
        activePatternStartTick = Long.MIN_VALUE;
        activePatternAnchor = null;
        patternArrivalTicks.clear();
    }

    private void updateEscapePrediction(ServerPlayerEntity target, List<MobEntity> living) {
        Vec3d velocity3d = target.getVelocity();
        Vec3d look3d = target.getRotationVec(1.0F);
        Vec2 velocity = new Vec2(velocity3d.x, velocity3d.z);
        Vec2 facing = new Vec2(look3d.x, look3d.z);
        Vec2 currentDirection = velocity.normalizedOr(facing.normalizedOr(Vec2.NORTH));
        double speedFactor = Math.min(1.0, horizontalSpeed(velocity3d) / 0.10);
        movementPersistence = previousMovementDirection == null
                ? speedFactor
                : clamp01((previousMovementDirection.dot(currentDirection) + 1.0) * 0.5) * speedFactor;
        previousMovementDirection = currentDirection;

        Vec2 away = awayFromThreats(target, living);
        int directionCount = performanceLevel >= 3 ? 4 : 8;
        escapePrediction = escapeRoutePredictor.predict(
                velocity, facing, away, directionCount,
                (direction, distance) -> sampleEscapeDirection(target, living, velocity, direction, distance));
        Vec2 primary = escapePrediction.primary();
        blackboard.updatePredictedEscape(target.getPos().add(primary.x() * 12.0, 0.0, primary.z() * 12.0));
    }

    private EscapeDirectionProbe.EscapeSample sampleEscapeDirection(
            ServerPlayerEntity target,
            List<MobEntity> living,
            Vec2 velocity,
            Vec2 direction,
            int distance) {
        BlockPos base = target.getBlockPos().add(
                (int) Math.round(direction.x() * distance), 0,
                (int) Math.round(direction.z() * distance));
        BlockPos standable = findStandableNear(base);
        if (standable == null) {
            return new EscapeDirectionProbe.EscapeSample(0.0, 0.0, 0.0, 0.0);
        }
        int clearSides = 0;
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos adjacent = standable.offset(side);
            if (world.getBlockState(adjacent).getCollisionShape(world, adjacent).isEmpty()
                    && world.getBlockState(adjacent.up()).getCollisionShape(world, adjacent.up()).isEmpty()) {
                clearSides++;
            }
        }
        double nearest = living.stream()
                .mapToDouble(mob -> mob.getPos().squaredDistanceTo(Vec3d.ofCenter(standable)))
                .min().orElse(16.0 * 16.0);
        double distanceFromThreats = clamp01(Math.sqrt(nearest) / 16.0);
        Vec2 recentDirection = velocity.normalizedOr(direction);
        double recentlyUsed = clamp01(recentDirection.dot(direction));
        return new EscapeDirectionProbe.EscapeSample(
                1.0, clearSides / 4.0, distanceFromThreats, recentlyUsed);
    }

    private BlockPos findStandableNear(BlockPos base) {
        for (int offset = 0; offset <= 2; offset++) {
            BlockPos[] candidates = offset == 0
                    ? new BlockPos[]{base}
                    : new BlockPos[]{base.up(offset), base.down(offset)};
            for (BlockPos candidate : candidates) {
                if (!world.isChunkLoaded(candidate) || !world.getWorldBorder().contains(candidate)) continue;
                boolean volumeClear = world.getBlockState(candidate).getCollisionShape(world, candidate).isEmpty()
                        && world.getBlockState(candidate.up()).getCollisionShape(world, candidate.up()).isEmpty()
                        && world.getFluidState(candidate).isEmpty()
                        && world.getFluidState(candidate.up()).isEmpty();
                if (volumeClear && world.getBlockState(candidate.down())
                        .isSideSolidFullSquare(world, candidate.down(), Direction.UP)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Vec2 awayFromThreats(ServerPlayerEntity target, List<MobEntity> living) {
        if (living.isEmpty()) return new Vec2(0.0, 0.0);
        double x = 0.0;
        double z = 0.0;
        for (MobEntity mob : living) {
            x += mob.getX();
            z += mob.getZ();
        }
        return new Vec2(target.getX() - x / living.size(), target.getZ() - z / living.size());
    }

    private double flankerOpposition(ServerPlayerEntity target) {
        List<Vec2> directions = blackboard.roles().entrySet().stream()
                .filter(entry -> entry.getValue() == TacticalRole.FLANKER
                        || entry.getValue() == TacticalRole.INTERCEPTOR)
                .map(entry -> blackboard.position(entry.getKey()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(position -> new Vec2(position.x - target.getX(), position.z - target.getZ())
                        .normalizedOr(Vec2.NORTH))
                .toList();
        double minimum = 1.0;
        for (int left = 0; left < directions.size(); left++) {
            for (int right = left + 1; right < directions.size(); right++) {
                minimum = Math.min(minimum, directions.get(left).dot(directions.get(right)));
            }
        }
        return minimum;
    }

    private boolean atReservedPosition(MobEntity mob) {
        return blackboard.position(mob.getUuid())
                .map(position -> mob.squaredDistanceTo(position) <= 9.0)
                .orElse(false);
    }

    private double routeDeviationDegrees(ServerPlayerEntity target) {
        if (target == null || escapePrediction == null || horizontalSpeed(target.getVelocity()) < 0.02) {
            return 0.0;
        }
        Vec2 movement = new Vec2(target.getVelocity().x, target.getVelocity().z).normalizedOr(Vec2.NORTH);
        double dot = Math.max(-1.0, Math.min(1.0, movement.dot(escapePrediction.primary())));
        return StrictMath.toDegrees(StrictMath.acos(dot));
    }

    private boolean planningPhase() {
        return runtime.state() == EncounterState.STALKING
                || runtime.state() == EncounterState.POSITIONING
                || runtime.state() == EncounterState.ENGAGING;
    }

    private EncounterSnapshot snapshot(
            ServerPlayerEntity target,
            List<MobEntity> living,
            PlayerDirectorState playerState,
            long tick,
            boolean startRequested) {
        int remainingThreat = memberCosts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int positioned = (int) living.stream().filter(mob -> blackboard.position(mob.getUuid())
                .map(position -> mob.squaredDistanceTo(position) <= 9.0)
                .orElse(false)).count();
        long contactAge = blackboard.lastContactTick() == 0L
                ? Long.MAX_VALUE
                : Math.max(0L, tick - blackboard.lastContactTick());
        double distress = playerState.breakdown() == null ? 0.0 : playerState.breakdown().distress();
        if (target != null) {
            double liveHealthRatio = target.getHealth() / Math.max(1.0F, target.getMaxHealth());
            if (liveHealthRatio < config.intensity().minimumHealthRatio()) {
                distress = Math.max(distress, 1.0);
            }
        }
        boolean valid = target != null && combatTargetEligibility.test(target);
        boolean sameDimension = target != null && target.getServerWorld() == world;
        boolean fullyUnloadedGraceExpired = !memberCosts.isEmpty()
                && living.isEmpty()
                && fullyUnloadedSinceTick != Long.MIN_VALUE
                && tick - fullyUnloadedSinceTick >= FULLY_UNLOADED_ROSTER_GRACE_TICKS;
        boolean pursuitViable = !fullyUnloadedGraceExpired
                && (target == null || target.getPos().squaredDistanceTo(encounterOrigin)
                        <= square(config.encounter().maxPursuitDistance()));
        boolean disengaged = target == null || living.stream().allMatch(mob ->
                mob.squaredDistanceTo(target) > 18.0 * 18.0);
        return new EncounterSnapshot(
                encounterId,
                targetPlayerId,
                tick,
                currentPressure == null
                        ? playerState.intensity()
                        : currentPressure.effectiveIntensity(),
                remainingThreat,
                initialThreat,
                memberCosts.size(),
                positioned,
                contactAge,
                startRequested,
                valid,
                sameDimension,
                performanceLevel,
                pursuitViable,
                target != null && inSpawnProtection(target),
                distress,
                disengaged,
                disengaged,
                cleanupComplete,
                false);
    }

    private void updatePerception(ServerPlayerEntity target, List<MobEntity> living, long tick) {
        if (target == null) {
            targetPerceivedThisTick = false;
            return;
        }
        boolean perceived = living.stream().anyMatch(mob ->
                mob.squaredDistanceTo(target) <= 48.0 * 48.0
                        && mob.getVisibilityCache().canSee(target));
        targetPerceivedThisTick = perceived;
        if (perceived || runtime.state() == EncounterState.STALKING && blackboard.lastContactTick() == 0L) {
            blackboard.updateTarget(target.getPos(), predictedEscape(target), tick);
        }
    }

    private boolean shouldReplan(ServerPlayerEntity target, long tick) {
        if (blackboard.lastPlanTick() == Long.MIN_VALUE) {
            return true;
        }
        int replanInterval = currentPressure == null
                ? config.tactical().replanIntervalTicks()
                : currentPressure.replanIntervalTicks();
        if (tick - blackboard.lastPlanTick() >= replanInterval) {
            return true;
        }
        return lastPlanAnchor == null || lastPlanAnchor.squaredDistanceTo(target.getPos())
                >= square(config.tactical().targetMoveReplanDistance());
    }

    private void applyTransition(TransitionResult result) {
        runtime = result.runtime();
        result.transition().ifPresent(transitionSink);
    }

    public boolean forceState(EncounterState state, long tick, double intensity) {
        return applyForcedState(state, tick, intensity, false);
    }

    /** Administrative scene control; unlike forceState, re-entering a phase restarts its timer. */
    public boolean forceSceneState(EncounterState state, long tick, double intensity) {
        boolean changed = applyForcedState(state, tick, intensity, true);
        if (changed && (state == EncounterState.RELIEF || state == EncounterState.RETREATING)) {
            List<MobEntity> living = livingMobs();
            for (MobEntity mob : living) {
                mob.setTarget(null);
            }
            executeRelief(target(), living, tick);
        }
        return changed;
    }

    private boolean applyForcedState(
            EncounterState state, long tick, double intensity, boolean allowRestart) {
        if (finished || state == null || (!allowRestart && state == runtime.state())
                || state == EncounterState.DORMANT
                || state == EncounterState.COOLDOWN
                || state == EncounterState.ABORTED) {
            return false;
        }
        EncounterTransition transition = new EncounterTransition(
                runtime.state(),
                state,
                TransitionCause.ADMIN_FORCED,
                targetPlayerId,
                encounterId,
                tick,
                intensity,
                budget.remaining());
        runtime = new EncounterRuntime(state, tick);
        transitionSink.accept(transition);
        return true;
    }

    public void cancel(long tick, double intensity) {
        forceState(EncounterState.RELIEF, tick, intensity);
        List<MobEntity> living = livingMobs();
        for (MobEntity mob : living) {
            mob.setTarget(null);
        }
        executeRelief(target(), living, tick);
    }

    public void close() {
        cleanup(true);
        finished = true;
    }

    private void cleanup(boolean discardDirectorSpawned) {
        orderStore.clearEncounter(encounterId);
        for (UUID mobId : memberCosts.keySet()) {
            resolveMob(mobId).ifPresent(mob -> {
                mob.setTarget(null);
                mob.removeScoreboardTag(MANAGED_TAG);
                if (discardDirectorSpawned && directorSpawnedMobIds.contains(mobId)) {
                    mob.discard();
                }
            });
            budget.releaseMob(mobId);
            blackboard.remove(mobId);
        }
        for (PatternId pattern : PatternId.values()) {
            budget.releasePattern(pattern.name());
        }
    }

    private void retireResolvedDeadMembers() {
        List<UUID> confirmedDead = memberCosts.keySet().stream()
                .filter(mobId -> {
                    Entity entity = world.getEntity(mobId);
                    return entity instanceof MobEntity mob && !mob.isAlive();
                })
                .toList();
        confirmedDead.forEach(this::confirmMobDeath);
    }

    /** Retires one member only after the server has explicitly confirmed its death. */
    public boolean confirmMobDeath(UUID mobId) {
        return retireMember(mobId);
    }

    private boolean retireMember(UUID mobId) {
        if (mobId == null || !memberCosts.containsKey(mobId)) {
            return false;
        }
        resolveMob(mobId).ifPresent(mob -> {
            mob.setTarget(null);
            mob.removeScoreboardTag(MANAGED_TAG);
        });
        memberCosts.remove(mobId);
        memberProfiles.remove(mobId);
        directorSpawnedMobIds.remove(mobId);
        budget.retireMob(mobId);
        blackboard.remove(mobId);
        orderStore.clearMobs(List.of(mobId));
        return true;
    }

    private boolean canAwaitFullyUnloadedRoster(ServerPlayerEntity target) {
        if (target == null
                || !combatTargetEligibility.test(target)
                || target.getServerWorld() != world
                || target.getPos().squaredDistanceTo(encounterOrigin)
                        > square(config.encounter().maxPursuitDistance())
                || inSpawnProtection(target)) {
            return false;
        }
        return switch (runtime.state()) {
            case STALKING, POSITIONING, ENGAGING, SEARCHING, CLIMAX -> true;
            default -> false;
        };
    }

    private void reassignRolesIfMembershipChanged(List<MobEntity> living) {
        Set<UUID> currentMembers = living.stream().map(Entity::getUuid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (currentMembers.equals(lastRoleMemberIds)) {
            return;
        }
        List<MobProfile> profiles = living.stream()
                .map(mob -> memberProfiles.get(mob.getUuid()))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, TacticalRole> reassigned = new VanillaRoleAssignment().assign(profiles);
        for (MobEntity mob : living) {
            blackboard.assignRole(mob.getUuid(),
                    reassigned.getOrDefault(mob.getUuid(), TacticalRole.VANGUARD));
        }
        lastRoleMemberIds = currentMembers;
    }

    private List<MobEntity> releaseMembersAttackedByOtherPlayers(
            ServerPlayerEntity target,
            List<MobEntity> living) {
        ArrayList<MobEntity> retained = new ArrayList<>(living.size());
        for (MobEntity mob : living) {
            if (mob.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity attacker
                    && !isEncounterGroupMember(attacker, target)) {
                orderStore.clear(mob);
                mob.removeScoreboardTag(MANAGED_TAG);
                retireMember(mob.getUuid());
            } else {
                retained.add(mob);
            }
        }
        return retained;
    }

    private boolean isEncounterGroupMember(
            net.minecraft.entity.player.PlayerEntity attacker,
            ServerPlayerEntity primary) {
        return primary != null
                && attacker.getWorld() == world
                && attacker.squaredDistanceTo(primary) <= square(config.multiplayer().groupRadius())
                && attacker.isAlive()
                && !attacker.isSpectator()
                && !attacker.isCreative();
    }

    private List<ServerPlayerEntity> combatTargets(
            ServerPlayerEntity primary, List<MobEntity> living) {
        List<ServerPlayerEntity> targets = world.getServer().getPlayerManager().getPlayerList().stream()
                .filter(player -> player.getServerWorld() == world)
                .filter(combatTargetEligibility)
                .filter(player -> player.squaredDistanceTo(primary)
                        <= square(config.multiplayer().groupRadius()))
                .filter(player -> living.stream().anyMatch(mob ->
                        mob.squaredDistanceTo(player) <= 48.0 * 48.0
                                && mob.getVisibilityCache().canSee(player)))
                .filter(player -> player.getHealth() / Math.max(1.0F, player.getMaxHealth())
                        >= config.intensity().minimumHealthRatio())
                .sorted(Comparator.comparing(player -> player.getUuid().toString()))
                .toList();
        return targets;
    }

    private ServerPlayerEntity target() {
        Entity entity = world.getEntity(targetPlayerId);
        return entity instanceof ServerPlayerEntity player ? player : null;
    }

    private List<MobEntity> livingMobs() {
        ArrayList<MobEntity> result = new ArrayList<>();
        for (UUID mobId : memberCosts.keySet()) {
            resolveMob(mobId).filter(Entity::isAlive).ifPresent(result::add);
        }
        return result;
    }

    private Optional<MobEntity> resolveMob(UUID mobId) {
        Entity entity = world.getEntity(mobId);
        return entity instanceof MobEntity mob ? Optional.of(mob) : Optional.empty();
    }

    private boolean inSpawnProtection(ServerPlayerEntity target) {
        if (world != world.getServer().getOverworld()) {
            return false;
        }
        BlockPos spawn = world.getSpawnPos();
        int radius = config.breachSimulation().spawnProtectionRadius();
        return Math.abs(target.getBlockX() - spawn.getX()) <= radius
                && Math.abs(target.getBlockZ() - spawn.getZ()) <= radius;
    }

    private static boolean selectedMelee(
            MobEntity mob,
            List<MobEntity> ordered,
            int rotation,
            int limit) {
        int index = ordered.indexOf(mob);
        if (index < 0 || ordered.isEmpty()) {
            return false;
        }
        int relative = Math.floorMod(index - rotation, ordered.size());
        return relative < limit;
    }

    private static TacticalSector sectorFor(TacticalRole role, int index, String pattern) {
        if (PatternId.PINCER.name().equals(pattern)) {
            return index % 2 == 0 ? TacticalSector.LEFT : TacticalSector.RIGHT;
        }
        if (PatternId.EXIT_CLOSURE.name().equals(pattern) && role == TacticalRole.INTERCEPTOR) {
            return TacticalSector.ESCAPE_PRIMARY;
        }
        return switch (role) {
            case VANGUARD -> TacticalSector.FRONT;
            case FLANKER -> index % 2 == 0 ? TacticalSector.LEFT : TacticalSector.RIGHT;
            case HARASSER -> index % 2 == 0 ? TacticalSector.LEFT : TacticalSector.RIGHT;
            case INTERCEPTOR -> TacticalSector.ESCAPE_PRIMARY;
            case RESERVE, SCOUT -> TacticalSector.RESERVE;
            default -> TacticalSector.REAR;
        };
    }

    private static Vec3d predictedEscape(ServerPlayerEntity target) {
        Vec3d velocity = target.getVelocity().multiply(20.0);
        if (velocity.horizontalLengthSquared() > 36.0) {
            velocity = velocity.normalize().multiply(6.0);
        }
        return target.getPos().add(velocity.x, 0.0, velocity.z);
    }

    private static Vec3d retreatPoint(MobEntity mob, ServerPlayerEntity target) {
        Vec3d away = mob.getPos().subtract(target.getPos());
        if (away.horizontalLengthSquared() < 0.01) {
            away = new Vec3d(1.0, 0.0, 0.0);
        }
        Vec3d horizontal = new Vec3d(away.x, 0.0, away.z).normalize();
        return mob.getPos().add(horizontal.multiply(12.0));
    }

    private Vec3d fallbackRallyPoint(
            ServerPlayerEntity target, MobEntity mob, int index, int groupSize) {
        Vec3d radial = mob.getPos().subtract(target.getPos());
        Vec3d horizontal;
        if (radial.horizontalLengthSquared() < 0.01) {
            double angle = Math.PI * 2.0 * index / Math.max(1, groupSize);
            horizontal = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
        } else {
            horizontal = new Vec3d(radial.x, 0.0, radial.z).normalize();
        }
        double radius = Math.min(
                config.tactical().positionMaxRadius(),
                config.tactical().positionMinRadius() + 2.0);
        return target.getPos().add(horizontal.multiply(radius));
    }

    private static int performanceCandidateLimit(int level) {
        return switch (Math.max(0, Math.min(6, level))) {
            case 0 -> 32;
            case 1 -> 28;
            case 2 -> 20;
            case 3 -> 14;
            case 4 -> 8;
            case 5 -> 4;
            default -> 1;
        };
    }

    private static EncounterDurations durations(ThreatDirectorConfig config) {
        return new EncounterDurations(
                config.encounter().stalkingMinTicks(),
                config.encounter().stalkingMaxTicks(),
                100,
                config.encounter().positioningMaxTicks(),
                400,
                config.encounter().engagingMaxTicks(),
                config.encounter().contactLossTicks(),
                config.encounter().contactLossTicks(),
                600,
                config.encounter().reliefTicks(),
                config.encounter().reliefTicks() + 200L,
                300,
                config.director().cooldownMinTicks());
    }

    private static BlockPoint point(BlockPos position) {
        return new BlockPoint(position.getX(), position.getY(), position.getZ());
    }

    private static Vec3d center(BlockPoint position) {
        return new Vec3d(position.x() + 0.5, position.y(), position.z() + 0.5);
    }

    private static double horizontalSpeed(Vec3d velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    private static double square(double value) {
        return value * value;
    }

    private int orderDuration(int requestedTicks) {
        return Math.max(requestedTicks, orderLifetimeTicks);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public UUID encounterId() {
        return encounterId;
    }

    public UUID targetPlayerId() {
        return targetPlayerId;
    }

    public boolean includesPlayer(ServerPlayerEntity player) {
        if (player == null || player.getServerWorld() != world) {
            return false;
        }
        if (player.getUuid().equals(targetPlayerId)) {
            return true;
        }
        ServerPlayerEntity primary = target();
        return primary != null
                && player.squaredDistanceTo(primary)
                        <= square(config.multiplayer().groupRadius())
                && combatTargetEligibility.test(player);
    }

    public EncounterState state() {
        return runtime.state();
    }

    public GroupBlackboard blackboard() {
        return blackboard;
    }

    public ThreatBudget budget() {
        return budget;
    }

    /** Snapshot used by the manager to release ownership when this controller lets a mob go. */
    public Set<UUID> memberIds() {
        return Set.copyOf(memberCosts.keySet());
    }

    public boolean canReinforce(
            long tick, int intervalTicks, int maximumWaves, int minimumLivingMobs) {
        if (finished
                || reinforcementWaves >= Math.max(0, maximumWaves)
                || tick - lastReinforcementTick < Math.max(1, intervalTicks)
                || !targetPerceivedThisTick
                || (runtime.state() != EncounterState.ENGAGING
                        && runtime.state() != EncounterState.CLIMAX)) {
            return false;
        }
        int living = livingCount();
        return living >= Math.max(2, minimumLivingMobs)
                && living == memberCosts.size()
                && budget.mobCount() < budget.mobCountCap();
    }

    /** Adds one bounded reinforcement wave whose budget allocations were already committed. */
    public int addPopulation(EncounterPopulation population, long tick) {
        if (finished || population == null || population.members().isEmpty()) {
            return 0;
        }
        for (EncounterPopulation.Member member : population.members()) {
            UUID mobId = member.profile().id();
            if (memberCosts.containsKey(mobId) || resolveMob(mobId).isEmpty()) {
                return 0;
            }
        }
        for (EncounterPopulation.Member member : population.members()) {
            UUID mobId = member.profile().id();
            memberCosts.put(mobId, member.threatCost());
            memberProfiles.put(mobId, member.profile());
            if (member.origin() == EncounterPopulation.Origin.DIRECTOR_SPAWNED) {
                directorSpawnedMobIds.add(mobId);
            }
            resolveMob(mobId).ifPresent(mob -> {
                mob.setTarget(null);
                mob.addCommandTag(MANAGED_TAG);
                aiInstaller.install(mob);
            });
        }
        lastRoleMemberIds = Set.of();
        reassignRolesIfMembershipChanged(livingMobs());
        reinforcementWaves++;
        lastReinforcementTick = tick;
        executePhase(target(), livingMobs(), tick);
        return population.members().size();
    }

    public void deferReinforcement(long tick) {
        lastReinforcementTick = tick;
    }

    public int reinforcementWaves() {
        return reinforcementWaves;
    }

    public int livingCount() {
        return livingMobs().size();
    }

    public boolean finished() {
        return finished;
    }

    public int lastPlannerOperationsUsed() {
        return lastPlannerOperationsUsed;
    }

    static RosterAvailability rosterAvailability(
            int rosterCount, int livingCount, long previousFullyUnloadedTick, long tick) {
        int roster = Math.max(0, rosterCount);
        int living = Math.max(0, Math.min(roster, livingCount));
        boolean hasUnresolvedMembers = living < roster;
        boolean fullyUnloaded = roster > 0 && living == 0;
        long since = fullyUnloaded
                ? previousFullyUnloadedTick == Long.MIN_VALUE
                        ? tick
                        : previousFullyUnloadedTick
                : Long.MIN_VALUE;
        long elapsed = since == Long.MIN_VALUE ? Long.MAX_VALUE : Math.max(0L, tick - since);
        return new RosterAvailability(
                hasUnresolvedMembers,
                fullyUnloaded,
                fullyUnloaded && elapsed < FULLY_UNLOADED_ROSTER_GRACE_TICKS,
                since);
    }

    record RosterAvailability(
            boolean hasUnresolvedMembers,
            boolean fullyUnloaded,
            boolean withinGrace,
            long fullyUnloadedSinceTick) {}
}
