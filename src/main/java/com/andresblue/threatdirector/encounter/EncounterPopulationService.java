package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.budget.ThreatBudget;
import com.andresblue.threatdirector.budget.ThreatCostRegistry;
import com.andresblue.threatdirector.config.ThreatDirectorConfig;
import com.andresblue.threatdirector.encounter.EncounterPopulation.Member;
import com.andresblue.threatdirector.encounter.EncounterPopulation.Origin;
import com.andresblue.threatdirector.trait.MobProfile;
import com.andresblue.threatdirector.trait.MobTraitRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

/**
 * Builds a bounded vanilla encounter population by recruiting first and spawning only the deficit.
 *
 * <p>This service must run on the server thread. It never requests a chunk and rolls back all work
 * performed by this call when it cannot reach the configured minimum population.
 */
public final class EncounterPopulationService {
    private static final double FRONT_CONE_COSINE = 0.5; // 60 degree half-angle.
    private static final double VECTOR_EPSILON_SQUARED = 1.0e-8;

    private final ThreatDirectorConfig config;
    private final ThreatDirectorConfig.EncounterSection encounter;
    private final ThreatDirectorConfig.TacticalSection tactical;
    private final ThreatCostRegistry costRegistry;
    private final VanillaMobAdapter mobAdapter;

    public EncounterPopulationService(
            ThreatDirectorConfig config,
            ThreatCostRegistry costRegistry,
            MobTraitRegistry traitRegistry) {
        this.config = Objects.requireNonNull(config, "config");
        this.encounter = Objects.requireNonNull(config.encounter(), "config.encounter");
        this.tactical = Objects.requireNonNull(config.tactical(), "config.tactical");
        this.costRegistry = Objects.requireNonNull(costRegistry, "costRegistry");
        this.mobAdapter = new VanillaMobAdapter(Objects.requireNonNull(traitRegistry, "traitRegistry"));
    }

    /**
     * Populates up to the lower of the configured cap and the budget cap.
     *
     * <p>The returned members are the allocations created by this invocation. Existing allocations
     * in {@code budget} count toward minimum and maximum size but are not repeated in the result.
     */
    public EncounterPopulation populate(
            ServerWorld world, ServerPlayerEntity target, ThreatBudget budget) {
        return populate(world, target, budget, Set.of(), encounter.maxMobs());
    }

    /**
     * Populates an encounter while excluding mobs already owned by another encounter manager.
     */
    public EncounterPopulation populate(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            Set<UUID> excludedMobIds) {
        return populate(world, target, budget, excludedMobIds, encounter.maxMobs());
    }

    /** Populates with an explicit effective cap, used for bounded multiplayer scaling. */
    public EncounterPopulation populate(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            Set<UUID> excludedMobIds,
            int effectiveMobCap) {
        return populate(
                world,
                target,
                budget,
                excludedMobIds,
                effectiveMobCap,
                encounter.minMobs(),
                config.performance().maxPlannerOperationsPerTick(),
                Long.MAX_VALUE);
    }

    /** Populates while consuming no more than the manager's remaining global path allowance. */
    public EncounterPopulation populate(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            Set<UUID> excludedMobIds,
            int effectiveMobCap,
            int planningOperationBudget) {
        return populate(
                world,
                target,
                budget,
                excludedMobIds,
                effectiveMobCap,
                encounter.minMobs(),
                planningOperationBudget,
                Long.MAX_VALUE);
    }

    /** Populates with contextual minimum and cap while reporting the operations actually spent. */
    public EncounterPopulation populate(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            Set<UUID> excludedMobIds,
            int effectiveMobCap,
            int effectiveMinimumPopulation,
            int planningOperationBudget) {
        return populate(
                world,
                target,
                budget,
                excludedMobIds,
                effectiveMobCap,
                effectiveMinimumPopulation,
                planningOperationBudget,
                Long.MAX_VALUE);
    }

    /**
     * Populates with both an operation allowance and an absolute monotonic deadline.
     *
     * <p>The deadline is checked between candidate and path operations. It cannot interrupt a
     * pathfinder already executing, but it prevents a delayed invocation from starting the rest
     * of a large candidate batch.
     */
    public EncounterPopulation populate(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            Set<UUID> excludedMobIds,
            int effectiveMobCap,
            int effectiveMinimumPopulation,
            int planningOperationBudget,
            long deadlineNanos) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(budget, "budget");
        Set<UUID> exclusions = Set.copyOf(
                Objects.requireNonNull(excludedMobIds, "excludedMobIds"));

        List<Member> members = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        List<MobEntity> spawnedEntities = new ArrayList<>();
        int initialBudgetCount = budget.mobCount();
        int desiredTotal = Math.min(
                Math.min(Math.max(0, effectiveMobCap), encounter.hardMaxMobs()),
                budget.mobCountCap());
        int minimumTotal = Math.max(encounter.minMobs(), effectiveMinimumPopulation);

        if (!config.enabled()) {
            diagnostics.add("Threat Director is disabled");
            return new EncounterPopulation(List.of(), 0, diagnostics, 0);
        }
        if (target.getWorld() != world || !target.isAlive() || target.isRemoved()) {
            diagnostics.add("Target is not a live member of the supplied world");
            return new EncounterPopulation(List.of(), 0, diagnostics, 0);
        }
        if (desiredTotal < minimumTotal) {
            diagnostics.add("Threat budget mob cap is below the configured encounter minimum");
            return new EncounterPopulation(List.of(), 0, diagnostics, 0);
        }

        int boundedPlanningBudget = Math.max(0, planningOperationBudget);
        // Recruitment and directed spawning share the same global allowance. Reserving most of it
        // for the deficit prevents a handful of unreachable natural mobs from starving formation.
        int ordinaryRecruitmentBudget = boundedPlanningBudget <= 1
                ? boundedPlanningBudget : boundedPlanningBudget / 3;
        int recruitmentBudget = Math.min(desiredTotal, ordinaryRecruitmentBudget);
        int initialPathChecks = encounter.recruitNaturalMobs()
                ? recruitNaturalMobs(
                world,
                target,
                budget,
                desiredTotal,
                exclusions,
                members,
                diagnostics,
                recruitmentBudget,
                deadlineNanos)
                : 0;

        int nearbyHostiles = deadlineReached(deadlineNanos)
                ? encounter.maxNearbyHostiles()
                : countNearbyHostiles(world, target);
        int spawnAttempts = 0;
        if (budget.mobCount() < desiredTotal) {
            if (!encounter.directedSpawning()) {
                diagnostics.add("Directed spawning is disabled by config");
            } else if (!canUseDirectedSpawning(world)) {
                diagnostics.add("Directed spawning is disabled by world difficulty or gamerule");
            } else if (nearbyHostiles >= encounter.maxNearbyHostiles()) {
                diagnostics.add("Nearby hostile cap prevents directed spawning");
            } else {
                SpawnResult result = spawnDeficit(
                        world,
                        target,
                        budget,
                        desiredTotal,
                        nearbyHostiles,
                        exclusions,
                        members,
                        diagnostics,
                        Math.max(0, boundedPlanningBudget - initialPathChecks),
                        deadlineNanos);
                spawnAttempts = result.attempts();
                spawnedEntities.addAll(result.entities());
            }
        }

        int planningOperations = initialPathChecks + spawnAttempts;
        if (deadlineReached(deadlineNanos)) {
            diagnostics.add("Planner work deadline was reached; remaining candidates were skipped");
        }
        if (initialBudgetCount + members.size() < minimumTotal) {
            diagnostics.add("Minimum encounter population was not reached: actual="
                    + (initialBudgetCount + members.size()) + ", minimum=" + minimumTotal
                    + "; allocations rolled back");
            rollback(budget, members, spawnedEntities);
            return new EncounterPopulation(List.of(), spawnAttempts, diagnostics, planningOperations);
        }

        return new EncounterPopulation(members, spawnAttempts, diagnostics, planningOperations);
    }

    private int recruitNaturalMobs(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            int desiredTotal,
            Set<UUID> excludedMobIds,
            List<Member> members,
            List<String> diagnostics,
            int maximumPathChecks,
            long deadlineNanos) {
        if (deadlineReached(deadlineNanos)) {
            return 0;
        }
        double radius = encounter.recruitRadius();
        double minimumDistance = Math.min(encounter.spawnMinDistance(), tactical.positionMinRadius());
        Set<String> allowedTypes = new HashSet<>(encounter.allowedMobIds());
        Box searchArea = target.getBoundingBox().expand(radius);
        List<MobEntity> candidates = world.getEntitiesByClass(
                MobEntity.class,
                searchArea,
                mob -> isRecruitable(
                        mob,
                        target,
                        allowedTypes,
                        excludedMobIds,
                        radius,
                        minimumDistance));

        candidates.sort(Comparator
                .comparingInt((MobEntity mob) -> isOutsideFrontCone(target, mob.getPos()) ? 0 : 1)
                .thenComparingDouble(mob -> horizontalDistanceSquared(target.getPos(), mob.getPos()))
                .thenComparing(mob -> mob.getUuid().toString()));

        int pathChecks = 0;
        for (MobEntity candidate : candidates) {
            if (budget.mobCount() >= desiredTotal || deadlineReached(deadlineNanos)) {
                break;
            }
            if (pathChecks >= maximumPathChecks) {
                break;
            }
            pathChecks++;
            if (!hasInitialRoute(candidate, target)) {
                continue;
            }
            Optional<VanillaMobAdapter.AdaptedMob> adapted = mobAdapter.adapt(candidate);
            if (adapted.isEmpty()) {
                continue;
            }
            appendWarnings(candidate.getUuid().toString(), adapted.get().warnings(), diagnostics);
            MobProfile profile = adapted.get().profile();
            int cost = costRegistry.cost(profile);
            if (cost <= 0) {
                diagnostics.add("Skipped natural mob with non-positive threat cost: " + profile.typeId());
                continue;
            }
            if (budget.allocateMob(profile.id(), cost)) {
                members.add(new Member(profile, Origin.RECRUITED, cost));
            }
        }
        return pathChecks;
    }

    private SpawnResult spawnDeficit(
            ServerWorld world,
            ServerPlayerEntity target,
            ThreatBudget budget,
            int desiredTotal,
            int nearbyHostiles,
            Set<UUID> excludedMobIds,
            List<Member> members,
            List<String> diagnostics,
            int pathAttemptBudget,
            long deadlineNanos) {
        List<EntityType<? extends MobEntity>> spawnTypes = mobAdapter.spawnTypes(
                encounter.allowedMobIds(), encounter.allowDirectorSpawnedCreepers());
        if (spawnTypes.isEmpty()) {
            diagnostics.add("No configured vanilla entity type is eligible for directed spawning");
            return new SpawnResult(0, List.of());
        }

        Random random = world.getRandom();
        int attempts = 0;
        int maximumAttempts = Math.min(
                tactical.maxCandidatesPerSnapshot(),
                Math.max(0, pathAttemptBudget));
        int typeOffset = random.nextInt(spawnTypes.size());
        List<MobEntity> spawned = new ArrayList<>();

        while (budget.mobCount() < desiredTotal
                && nearbyHostiles + spawned.size() < encounter.maxNearbyHostiles()
                && attempts < maximumAttempts
                && !deadlineReached(deadlineNanos)) {
            EntityType<? extends MobEntity> type =
                    spawnTypes.get((typeOffset + attempts) % spawnTypes.size());
            attempts++;
            Optional<MobEntity> candidate = createSafeSpawnCandidate(
                    world, target, type, random, deadlineNanos);
            if (candidate.isEmpty()) {
                continue;
            }

            MobEntity mob = candidate.get();
            Optional<VanillaMobAdapter.AdaptedMob> adapted = mobAdapter.adapt(mob);
            if (adapted.isEmpty()) {
                mob.discard();
                continue;
            }
            appendWarnings("spawn candidate", adapted.get().warnings(), diagnostics);
            MobProfile profile = adapted.get().profile();
            int cost = costRegistry.cost(profile);
            if (excludedMobIds.contains(profile.id())
                    || cost <= 0
                    || !budget.allocateMob(profile.id(), cost)) {
                mob.discard();
                continue;
            }

            if (!world.spawnEntity(mob)) {
                budget.releaseMob(profile.id());
                mob.discard();
                diagnostics.add("Server rejected a validated spawn candidate: " + profile.typeId());
                continue;
            }

            spawned.add(mob);
            members.add(new Member(profile, Origin.DIRECTOR_SPAWNED, cost));
        }

        return new SpawnResult(attempts, spawned);
    }

    private Optional<MobEntity> createSafeSpawnCandidate(
            ServerWorld world,
            ServerPlayerEntity target,
            EntityType<? extends MobEntity> type,
            Random random,
            long deadlineNanos) {
        if (deadlineReached(deadlineNanos)) {
            return Optional.empty();
        }
        Vec3d look = target.getRotationVec(1.0F);
        double horizontalLookLengthSquared = look.x * look.x + look.z * look.z;
        double angle;
        if (horizontalLookLengthSquared <= VECTOR_EPSILON_SQUARED) {
            angle = random.nextDouble() * Math.PI * 2.0;
        } else {
            double lookAngle = Math.atan2(look.z, look.x);
            double excludedHalfAngle = Math.acos(FRONT_CONE_COSINE);
            double allowedArc = Math.PI * 2.0 - excludedHalfAngle * 2.0;
            angle = lookAngle + excludedHalfAngle + random.nextDouble() * allowedArc;
        }

        double minimum = encounter.spawnMinDistance();
        double maximum = encounter.spawnMaxDistance();
        double distance = Math.sqrt(minimum * minimum
                + random.nextDouble() * (maximum * maximum - minimum * minimum));
        int x = (int) Math.floor(target.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(target.getZ() + Math.sin(angle) * distance);
        double centeredX = x + 0.5;
        double centeredZ = z + 0.5;
        Vec3d horizontalCandidate = new Vec3d(centeredX, target.getY(), centeredZ);

        double actualDistanceSquared = horizontalDistanceSquared(target.getPos(), horizontalCandidate);
        if (actualDistanceSquared < minimum * minimum
                || actualDistanceSquared > maximum * maximum
                || !isOutsideFrontCone(target, horizontalCandidate)) {
            return Optional.empty();
        }
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }

        int verticalRadius = Math.max(4, (int) Math.ceil(tactical.positionMinRadius()));
        int highestY = Math.min(world.getTopY() - 2, target.getBlockY() + verticalRadius);
        int lowestY = Math.max(world.getBottomY() + 1, target.getBlockY() - verticalRadius);
        for (int y = highestY; y >= lowestY; y--) {
            if (deadlineReached(deadlineNanos)) {
                return Optional.empty();
            }
            BlockPos position = new BlockPos(x, y, z);
            if (!world.getWorldBorder().contains(position)
                    || !SpawnRestriction.canSpawn(type, world, SpawnReason.EVENT, position, random)
                    || !MobEntity.canMobSpawn(type, world, SpawnReason.EVENT, position, random)) {
                continue;
            }

            MobEntity mob = type.create(world);
            if (mob == null) {
                return Optional.empty();
            }
            mob.refreshPositionAndAngles(centeredX, y, centeredZ, random.nextFloat() * 360.0F, 0.0F);
            if (!world.getWorldBorder().contains(mob.getBoundingBox())
                    || !world.isSpaceEmpty(mob)
                    || !mob.canSpawn(world, SpawnReason.EVENT)) {
                mob.discard();
                continue;
            }

            mob.initialize(
                    world, world.getLocalDifficulty(position), SpawnReason.EVENT, null, null);
            if (mob.hasVehicle()) {
                mob.stopRiding();
            }
            if (mob.hasPassengers()) {
                mob.removeAllPassengers();
            }
            mob.refreshPositionAndAngles(centeredX, y, centeredZ, mob.getYaw(), mob.getPitch());
            if (!world.getWorldBorder().contains(mob.getBoundingBox()) || !world.isSpaceEmpty(mob)) {
                mob.discard();
                continue;
            }
            if (deadlineReached(deadlineNanos)) {
                mob.discard();
                return Optional.empty();
            }
            if (!hasInitialRoute(mob, target)) {
                mob.discard();
                continue;
            }
            return Optional.of(mob);
        }
        return Optional.empty();
    }

    private boolean isRecruitable(
            MobEntity mob,
            ServerPlayerEntity target,
            Set<String> allowedTypes,
            Set<UUID> excludedMobIds,
            double maximumDistance,
            double minimumDistance) {
        if (!mobAdapter.supports(mob)
                || !allowedTypes.contains(VanillaMobAdapter.exactTypeId(mob.getType()))
                || excludedMobIds.contains(mob.getUuid())
                || !mob.isAlive()
                || mob.isRemoved()
                || mob.isPersistent()
                || mob.hasCustomName()
                || mob.isAiDisabled()
                || mob.isLeashed()
                || mob.hasVehicle()
                || mob.hasPassengers()
                || (mob instanceof CreeperEntity
                        && mob.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                        && !encounter.allowDirectorSpawnedCreepers())
                || (mob.getTarget() != null && mob.getTarget() != target)) {
            return false;
        }
        if (Math.abs(mob.getY() - target.getY()) > tactical.positionMaxRadius()) {
            return false;
        }
        double distanceSquared = horizontalDistanceSquared(target.getPos(), mob.getPos());
        return distanceSquared >= minimumDistance * minimumDistance
                && distanceSquared <= maximumDistance * maximumDistance;
    }

    private int countNearbyHostiles(ServerWorld world, ServerPlayerEntity target) {
        double radius = encounter.recruitRadius();
        return world.getEntitiesByClass(
                        HostileEntity.class,
                        target.getBoundingBox().expand(radius),
                        mob -> mob.isAlive() && !mob.isRemoved())
                .size();
    }

    private static boolean hasInitialRoute(MobEntity mob, ServerPlayerEntity target) {
        Path path = mob.getNavigation().findPathTo(target.getBlockPos(), 0);
        if (path == null || path.getEnd() == null) {
            return false;
        }
        return path.reachesTarget()
                || path.getEnd().getBlockPos().getSquaredDistance(target.getBlockPos()) <= 16.0;
    }

    private static boolean canUseDirectedSpawning(ServerWorld world) {
        return world.getDifficulty() != Difficulty.PEACEFUL
                && world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
    }

    private static boolean isOutsideFrontCone(ServerPlayerEntity target, Vec3d position) {
        Vec3d look = target.getRotationVec(1.0F);
        return isOutsideFrontCone(
                look.x,
                look.z,
                position.x - target.getX(),
                position.z - target.getZ());
    }

    static boolean isOutsideFrontCone(double lookX, double lookZ, double offsetX, double offsetZ) {
        double lookLengthSquared = lookX * lookX + lookZ * lookZ;
        double offsetLengthSquared = offsetX * offsetX + offsetZ * offsetZ;
        if (lookLengthSquared <= VECTOR_EPSILON_SQUARED) {
            return true;
        }
        if (offsetLengthSquared <= VECTOR_EPSILON_SQUARED) {
            return false;
        }
        double dot = (lookX * offsetX + lookZ * offsetZ)
                / Math.sqrt(lookLengthSquared * offsetLengthSquared);
        return dot <= FRONT_CONE_COSINE;
    }

    private static double horizontalDistanceSquared(Vec3d first, Vec3d second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static boolean deadlineReached(long deadlineNanos) {
        return deadlineNanos != Long.MAX_VALUE && System.nanoTime() - deadlineNanos >= 0L;
    }

    private static void appendWarnings(
            String source, List<String> warnings, List<String> diagnostics) {
        for (String warning : warnings) {
            diagnostics.add(source + ": " + warning);
        }
    }

    private static void rollback(
            ThreatBudget budget, List<Member> members, List<MobEntity> spawnedEntities) {
        for (MobEntity spawned : spawnedEntities) {
            if (!spawned.isRemoved()) {
                spawned.discard();
            }
        }
        for (Member member : members) {
            budget.releaseMob(member.profile().id());
        }
    }

    /** Releases allocations and removes only mobs spawned by the failed population attempt. */
    void rollbackPopulation(
            ServerWorld world, ThreatBudget budget, EncounterPopulation population) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(population, "population");
        for (Member member : population.members()) {
            if (member.origin() == Origin.DIRECTOR_SPAWNED) {
                Entity entity = world.getEntity(member.profile().id());
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
            }
            budget.releaseMob(member.profile().id());
        }
    }

    private record SpawnResult(int attempts, List<MobEntity> entities) {
        private SpawnResult {
            entities = List.copyOf(entities);
        }
    }
}
