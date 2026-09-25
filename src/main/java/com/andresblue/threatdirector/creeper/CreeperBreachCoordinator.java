package com.andresblue.threatdirector.creeper;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.server.MobGriefingGuard;
import com.andresblue.threatdirector.server.ThreatScaling;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Chooses a single creeper to open a real, claim-safe breach for each sheltered player.
 * The short-lived world cache prevents every creeper in the horde from priming at once.
 */
public final class CreeperBreachCoordinator {
    private static final int FAILED_SCAN_TICKS = 20;
    private static final double SHELTER_RAY_DISTANCE = 7.0D;
    private static final Map<ServerWorld, Map<UUID, Assignment>> ASSIGNMENTS = new WeakHashMap<>();
    private static final Directive NONE = new Directive(false, null);

    private CreeperBreachCoordinator() {
    }

    public static Directive directive(
            ServerWorld world,
            CreeperEntity requester,
            ServerPlayerEntity target
    ) {
        double threat = ThreatScaling.playerThreat(target);
        boolean visible = requester.getVisibilityCache().canSee(target);
        boolean permittedAtRequester = MobGriefingGuard.permits(world, requester.getBlockPos());
        // Cheap gates run before any shelter raycast. Passing true here only checks
        // the invariant gates; actual shelter is evaluated once per cached scan below.
        if (!ApocalypticCreeperPolicy.shouldCoordinateBreach(
                ThreatDirectorMod.isEnabled(),
                world.getDifficulty().getId(),
                threat,
                ThreatScaling.activationThreat(),
                permittedAtRequester,
                target.isAlive(),
                visible,
                true,
                requester.squaredDistanceTo(target))) {
            return NONE;
        }

        long now = world.getTime();
        Map<UUID, Assignment> worldAssignments = ASSIGNMENTS.computeIfAbsent(
                world, ignored -> new HashMap<>());
        Assignment assignment = worldAssignments.get(target.getUuid());
        if (assignment != null && assignment.expiresAtTick() > now) {
            if (assignment.creeperId() == null) {
                return NONE;
            }
            if (isStillUsable(world, target, assignment)) {
                return assignment.creeperId().equals(requester.getUuid())
                        ? new Directive(true, assignment.wallPosition())
                        : NONE;
            }
        }

        if (!isSheltered(world, target)) {
            worldAssignments.put(target.getUuid(),
                    new Assignment(null, null, now + FAILED_SCAN_TICKS));
            return NONE;
        }

        Candidate best = chooseCandidate(world, target);
        if (best == null) {
            worldAssignments.put(target.getUuid(), new Assignment(null, null, now + FAILED_SCAN_TICKS));
            return NONE;
        }
        assignment = new Assignment(
                best.creeper().getUuid(), best.wallPosition(), now
                + ThreatDirectorMod.config().modded().creeper().breachAssignmentSeconds() * 20L);
        worldAssignments.put(target.getUuid(), assignment);
        return assignment.creeperId().equals(requester.getUuid())
                ? new Directive(true, assignment.wallPosition())
                : NONE;
    }

    public static boolean isAtBreachPoint(CreeperEntity creeper, Directive directive) {
        return directive.designated()
                && directive.wallPosition() != null
                && creeper.squaredDistanceTo(Vec3d.ofCenter(directive.wallPosition()))
                <= square(ThreatDirectorMod.config().modded().creeper().breachFuseDistance());
    }

    private static Candidate chooseCandidate(ServerWorld world, ServerPlayerEntity target) {
        Candidate best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (CreeperEntity creeper : world.getEntitiesByClass(
                CreeperEntity.class,
                target.getBoundingBox().expand(ThreatDirectorMod.config().modded().creeper()
                        .breachMaximumTargetDistance()),
                candidate -> candidate.isAlive()
                        && !candidate.isIgnited()
                        && candidate.getFuseSpeed() <= 0
                        && candidate.getTarget() == target
                        && !candidate.getVisibilityCache().canSee(target))) {
            BlockPos wall = findBreakableWall(world, creeper, target);
            if (wall == null) {
                continue;
            }
            double score = creeper.squaredDistanceTo(Vec3d.ofCenter(wall));
            if (score < bestScore) {
                best = new Candidate(creeper, wall);
                bestScore = score;
            }
        }
        return best;
    }

    private static BlockPos findBreakableWall(
            ServerWorld world,
            CreeperEntity creeper,
            ServerPlayerEntity target
    ) {
        HitResult result = world.raycast(new RaycastContext(
                creeper.getEyePos(),
                target.getEyePos(),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                creeper));
        if (!(result instanceof BlockHitResult blockHit) || result.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos wall = blockHit.getBlockPos().toImmutable();
        return isBreakableWall(world, wall) ? wall : null;
    }

    private static boolean isBreakableWall(ServerWorld world, BlockPos wall) {
        if (wall == null
                || !MobGriefingGuard.permits(world, wall)
                || world.getBlockEntity(wall) != null) {
            return false;
        }
        BlockState state = world.getBlockState(wall);
        float hardness = state.getHardness(world, wall);
        return !state.isAir()
                && hardness >= 0.0F
                && hardness <= ThreatDirectorMod.config().modded().creeper().maximumBreakableHardness()
                && !state.getCollisionShape(world, wall).isEmpty();
    }

    private static boolean isSheltered(ServerWorld world, ServerPlayerEntity target) {
        BlockPos position = target.getBlockPos();
        if (!world.isSkyVisible(position.up())) {
            return true;
        }

        int blockedSides = 0;
        Vec3d eye = target.getEyePos();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            Vec3d end = eye.add(
                    direction.getOffsetX() * SHELTER_RAY_DISTANCE,
                    0.0D,
                    direction.getOffsetZ() * SHELTER_RAY_DISTANCE);
            HitResult hit = world.raycast(new RaycastContext(
                    eye,
                    end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    target));
            if (hit.getType() == HitResult.Type.BLOCK && ++blockedSides >= 2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStillUsable(
            ServerWorld world,
            ServerPlayerEntity target,
            Assignment assignment
    ) {
        Entity entity = world.getEntity(assignment.creeperId());
        return entity instanceof CreeperEntity creeper
                && creeper.isAlive()
                && creeper.getTarget() == target
                && isBreakableWall(world, assignment.wallPosition());
    }

    public record Directive(boolean designated, BlockPos wallPosition) {
    }

    private record Candidate(CreeperEntity creeper, BlockPos wallPosition) {
    }

    private record Assignment(UUID creeperId, BlockPos wallPosition, long expiresAtTick) {
    }

    private static double square(double value) {
        return value * value;
    }
}
