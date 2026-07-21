package com.andresblue.threatdirector.server;

import com.andresblue.threatdirector.breach.BlockSnapshot;
import com.andresblue.threatdirector.breach.BreachWorldView;
import com.andresblue.threatdirector.breach.ClaimStatus;
import com.andresblue.threatdirector.compatibility.ClaimProtectionService;
import com.andresblue.threatdirector.compatibility.ClaimProtectionStatus;
import com.andresblue.threatdirector.tactical.BlockPoint;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MinecraftBreachWorldView implements BreachWorldView {
    private final ServerWorld world;
    private final ClaimProtectionService claims;
    private final int spawnProtectionRadius;
    private final boolean claimSystemPresent;

    public MinecraftBreachWorldView(
            ServerWorld world,
            ClaimProtectionService claims,
            int spawnProtectionRadius,
            boolean claimSystemPresent) {
        this.world = world;
        this.claims = claims;
        this.spawnProtectionRadius = Math.max(0, spawnProtectionRadius);
        this.claimSystemPresent = claimSystemPresent;
    }

    @Override
    public BlockSnapshot blockAt(BlockPoint position) {
        BlockPos pos = toBlockPos(position);
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        boolean sensitive = isVanillaOre(block)
                || block == Blocks.SPAWNER
                || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.END_PORTAL
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.NETHER_PORTAL
                || block == Blocks.ENDER_CHEST;
        boolean soft = state.isIn(BlockTags.DIRT)
                || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.LEAVES)
                || state.isIn(BlockTags.WOOL)
                || state.isIn(BlockTags.PLANKS)
                || block == Blocks.GRAVEL
                || block == Blocks.GLASS
                || block == Blocks.GLASS_PANE;
        boolean hazard = adjacentLava(pos) || world.getFluidState(pos.up()).isIn(net.minecraft.registry.tag.FluidTags.LAVA);
        return new BlockSnapshot(
                Registries.BLOCK.getId(block).toString(),
                state.getHardness(world, pos),
                blockEntity != null,
                blockEntity instanceof Inventory,
                sensitive,
                soft,
                false,
                hazard);
    }

    @Override
    public ClaimStatus claimAt(BlockPoint position) {
        ClaimProtectionStatus status = claims.protectionAt(world, toBlockPos(position));
        return switch (status) {
            case PROTECTED -> ClaimStatus.PROTECTED;
            case UNPROTECTED -> ClaimStatus.UNPROTECTED;
            case UNKNOWN -> ClaimStatus.UNKNOWN;
        };
    }

    @Override
    public double distanceToNearestClaim(BlockPoint position) {
        return claimSystemPresent ? Double.NaN : Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isSpawnProtected(BlockPoint position) {
        if (world.getRegistryKey() != World.OVERWORLD) {
            return false;
        }
        BlockPos spawn = world.getServer().getOverworld().getSpawnPos();
        return Math.abs(position.x() - spawn.getX()) <= spawnProtectionRadius
                && Math.abs(position.z() - spawn.getZ()) <= spawnProtectionRadius;
    }

    @Override
    public List<BlockPoint> obstructionsForStep(
            BlockPoint from,
            BlockPoint to,
            Set<BlockPoint> assumedRemoved) {
        ArrayList<BlockPoint> obstructions = new ArrayList<>(2);
        BlockPoint feet = to;
        BlockPoint head = new BlockPoint(to.x(), to.y() + 1, to.z());
        if (!assumedRemoved.contains(feet) && blocksMovement(feet)) {
            obstructions.add(feet);
        }
        if (!assumedRemoved.contains(head) && blocksMovement(head)) {
            obstructions.add(head);
        }
        return List.copyOf(obstructions);
    }

    @Override
    public boolean isSafeStandable(BlockPoint feet, Set<BlockPoint> assumedRemoved) {
        BlockPoint head = new BlockPoint(feet.x(), feet.y() + 1, feet.z());
        BlockPoint floor = new BlockPoint(feet.x(), feet.y() - 1, feet.z());
        if ((!assumedRemoved.contains(feet) && blocksMovement(feet))
                || (!assumedRemoved.contains(head) && blocksMovement(head))
                || assumedRemoved.contains(floor)) {
            return false;
        }
        BlockPos floorPos = toBlockPos(floor);
        BlockState floorState = world.getBlockState(floorPos);
        return floorState.isSideSolidFullSquare(world, floorPos, Direction.UP)
                && world.getFluidState(toBlockPos(feet)).isEmpty()
                && world.getFluidState(toBlockPos(head)).isEmpty()
                && !adjacentLava(toBlockPos(feet));
    }

    private boolean blocksMovement(BlockPoint point) {
        BlockPos position = toBlockPos(point);
        return !world.getBlockState(position).getCollisionShape(world, position).isEmpty();
    }

    private boolean adjacentLava(BlockPos position) {
        for (Direction direction : Direction.values()) {
            if (world.getFluidState(position.offset(direction)).isIn(net.minecraft.registry.tag.FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVanillaOre(Block block) {
        return block == Blocks.COAL_ORE
                || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.COPPER_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.EMERALD_ORE
                || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DIAMOND_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS;
    }

    private static BlockPos toBlockPos(BlockPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }
}
