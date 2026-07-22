package com.andresblue.threatdirector.difficulty;

import com.andresblue.threatdirector.server.MobGriefingGuard;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

import java.util.List;

public final class ZombieDifficultyAbilities {
    private static final double REINFORCEMENT_SCAN_RADIUS = 24.0;
    private static final int SPAWN_ATTEMPTS_PER_REINFORCEMENT = 12;

    private ZombieDifficultyAbilities() {
    }

    public static void equipForDifficulty(ZombieEntity zombie) {
        if (!(zombie.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DifficultyAbilityPolicy.ZombieRules rules = MinecraftDifficultyProfiles.forWorld(world).zombie();
        if (rules.meleeToolChoices() == 0
                || !DifficultyAbilityPolicy.acceptsRoll(zombie.getRandom().nextDouble(), rules.equipmentChance())) {
            return;
        }

        Item[] tools = rules.shieldAndPickaxe()
                ? new Item[]{Items.IRON_SHOVEL, Items.IRON_AXE, Items.IRON_SWORD, Items.IRON_PICKAXE}
                : new Item[]{Items.STONE_SHOVEL, Items.STONE_AXE, Items.IRON_SWORD};
        Item selected = tools[zombie.getRandom().nextInt(Math.min(rules.meleeToolChoices(), tools.length))];
        zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(selected));
        zombie.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05F);

        if (rules.shieldAndPickaxe() && zombie.getRandom().nextFloat() < 0.30F) {
            zombie.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            zombie.setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.02F);
        }
    }

    /** Returns true only when at least one bounded reinforcement was actually spawned. */
    public static boolean tryCallReinforcements(ZombieEntity caller, LivingEntity attacker) {
        if (!(caller.getWorld() instanceof ServerWorld world)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
            return false;
        }
        DifficultyAbilityPolicy.ZombieRules rules = MinecraftDifficultyProfiles.forWorld(world).zombie();
        if (rules.maxReinforcements() == 0
                || !DifficultyAbilityPolicy.acceptsRoll(caller.getRandom().nextDouble(), rules.reinforcementChance())) {
            return false;
        }

        List<ZombieEntity> nearby = world.getEntitiesByClass(
                ZombieEntity.class,
                caller.getBoundingBox().expand(REINFORCEMENT_SCAN_RADIUS),
                ZombieEntity::isAlive
        );
        int available = Math.max(0, rules.nearbyZombieCap() - nearby.size());
        int requested = 1 + caller.getRandom().nextInt(rules.maxReinforcements());
        int count = DifficultyAbilityPolicy.boundedSpawnCount(requested, Math.min(available, rules.maxReinforcements()));
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawnOne(world, caller, attacker)) {
                spawned++;
            }
        }
        return spawned > 0;
    }

    /**
     * Breaks at most one loaded, non-sensitive obstruction in front of a Hard zombie.
     * The runtime claim service is authoritative and UNKNOWN fails closed.
     */
    public static boolean tryBreakPreferredObstruction(ZombieEntity zombie) {
        if (!(zombie.getWorld() instanceof ServerWorld world)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
                || MinecraftDifficultyProfiles.forWorld(world).zombie().blockBreakCooldownTicks() == 0) {
            return false;
        }
        LivingEntity target = zombie.getTarget();
        ItemStack tool = zombie.getMainHandStack();
        if (target == null || !target.isAlive() || zombie.squaredDistanceTo(target) < 4.0
                || ZombieToolBlockHelper.roleOf(tool) == ZombieToolRole.NONE) {
            return false;
        }

        Direction direction = Direction.getFacing(
                target.getX() - zombie.getX(), 0.0, target.getZ() - zombie.getZ());
        BlockPos feet = zombie.getBlockPos().offset(direction);
        if (breakIfPermitted(world, zombie, tool, feet)) {
            return true;
        }
        return breakIfPermitted(world, zombie, tool, feet.up());
    }

    private static boolean spawnOne(ServerWorld world, ZombieEntity caller, LivingEntity attacker) {
        BlockPos origin = caller.getBlockPos();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS_PER_REINFORCEMENT; attempt++) {
            int xOffset = signedOffset(caller, 6, 14);
            int zOffset = signedOffset(caller, 6, 14);
            BlockPos candidate = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    origin.add(xOffset, 0, zOffset)
            );
            if (Math.abs(candidate.getY() - origin.getY()) > 8
                    || !world.isChunkLoaded(candidate)
                    || !world.getWorldBorder().contains(candidate)
                    || !SpawnRestriction.canSpawn(EntityType.ZOMBIE, world, SpawnReason.REINFORCEMENT,
                    candidate, caller.getRandom())) {
                continue;
            }

            ZombieEntity reinforcement = EntityType.ZOMBIE.create(world);
            if (reinforcement == null) {
                return false;
            }
            reinforcement.refreshPositionAndAngles(
                    candidate.getX() + 0.5,
                    candidate.getY(),
                    candidate.getZ() + 0.5,
                    caller.getRandom().nextFloat() * 360.0F,
                    0.0F
            );
            if (!world.isSpaceEmpty(reinforcement)) {
                reinforcement.discard();
                continue;
            }
            reinforcement.initialize(world, world.getLocalDifficulty(candidate),
                    SpawnReason.REINFORCEMENT, null, null);
            if (attacker != null && attacker.isAlive()) {
                reinforcement.setTarget(attacker);
            }
            return world.spawnEntity(reinforcement);
        }
        return false;
    }

    private static boolean breakIfPermitted(ServerWorld world, ZombieEntity zombie,
                                            ItemStack tool, BlockPos position) {
        if (world.getBlockEntity(position) != null || !MobGriefingGuard.permits(world, position)) {
            return false;
        }
        BlockState state = world.getBlockState(position);
        float hardness = state.getHardness(world, position);
        if (state.isAir() || hardness < 0.0F || hardness > 5.0F
                || state.getCollisionShape(world, position).isEmpty()
                || !ZombieToolBlockHelper.isPreferredBlock(tool, state)
                || !tool.isSuitableFor(state)) {
            return false;
        }
        // No drops prevents the ability from becoming an automated resource farm.
        return world.breakBlock(position, false, zombie, 32);
    }

    private static int signedOffset(ZombieEntity caller, int minimum, int exclusiveMaximum) {
        int magnitude = minimum + caller.getRandom().nextInt(exclusiveMaximum - minimum);
        return caller.getRandom().nextBoolean() ? magnitude : -magnitude;
    }
}
