package com.andresblue.threatdirector.encounter;

import com.andresblue.threatdirector.tactical.BlockPoint;
import com.andresblue.threatdirector.tactical.TerrainProbe;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public final class MinecraftTerrainProbe implements TerrainProbe {
    private final ServerWorld world;
    private final MobEntity mob;
    private final PlayerEntity target;
    private final int maxPathAttempts;
    private int pathAttempts;

    public MinecraftTerrainProbe(ServerWorld world, MobEntity mob, PlayerEntity target, int maxPathAttempts) {
        this.world = world;
        this.mob = mob;
        this.target = target;
        this.maxPathAttempts = Math.max(0, maxPathAttempts);
    }

    @Override
    public Optional<BlockPoint> findStandable(BlockPoint approximate, int verticalRange) {
        for (int offset = 0; offset <= Math.max(0, verticalRange); offset++) {
            if (offset == 0) {
                Optional<BlockPoint> exact = standable(approximate.x(), approximate.y(), approximate.z());
                if (exact.isPresent()) {
                    return exact;
                }
                continue;
            }
            Optional<BlockPoint> above = standable(approximate.x(), approximate.y() + offset, approximate.z());
            if (above.isPresent()) {
                return above;
            }
            Optional<BlockPoint> below = standable(approximate.x(), approximate.y() - offset, approximate.z());
            if (below.isPresent()) {
                return below;
            }
        }
        return Optional.empty();
    }

    @Override
    public double pathQuality(BlockPoint mobStart, BlockPoint destination) {
        if (pathAttempts >= maxPathAttempts) {
            return -1.0;
        }
        pathAttempts++;
        Path path = mob.getNavigation().findPathTo(toBlockPos(destination), 0);
        if (path == null) {
            return -1.0;
        }
        if (path.reachesTarget()) {
            return 1.0;
        }
        return path.getEnd() != null
                && path.getEnd().getBlockPos().getSquaredDistance(toBlockPos(destination)) <= 9.0
                ? 0.6
                : -1.0;
    }

    @Override
    public double lineOfSightQuality(BlockPoint destination) {
        Vec3d start = target.getEyePos();
        Vec3d end = Vec3d.ofCenter(toBlockPos(destination)).add(0.0, 0.5, 0.0);
        HitResult hit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                target));
        return hit.getType() == HitResult.Type.MISS ? 1.0 : 0.0;
    }

    @Override
    public double coverQuality(BlockPoint destination) {
        BlockPos position = toBlockPos(destination);
        int cover = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos adjacent = position.offset(direction);
            if (!world.getBlockState(adjacent).getCollisionShape(world, adjacent).isEmpty()) {
                cover++;
            }
        }
        return cover / 4.0;
    }

    public int pathAttempts() {
        return pathAttempts;
    }

    private Optional<BlockPoint> standable(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!world.isChunkLoaded(feet) || !world.getWorldBorder().contains(feet)) {
            return Optional.empty();
        }
        BlockPos head = feet.up();
        BlockPos floor = feet.down();
        BlockState feetState = world.getBlockState(feet);
        BlockState headState = world.getBlockState(head);
        BlockState floorState = world.getBlockState(floor);
        boolean volumeClear = feetState.getCollisionShape(world, feet).isEmpty()
                && headState.getCollisionShape(world, head).isEmpty()
                && feetState.getFluidState().isEmpty()
                && headState.getFluidState().isEmpty();
        boolean solidFloor = floorState.isSideSolidFullSquare(world, floor, Direction.UP);
        return volumeClear && solidFloor
                ? Optional.of(new BlockPoint(x, y, z))
                : Optional.empty();
    }

    private static BlockPos toBlockPos(BlockPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
