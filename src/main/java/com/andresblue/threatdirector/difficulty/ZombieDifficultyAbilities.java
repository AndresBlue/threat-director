package com.andresblue.threatdirector.difficulty;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.server.MobGriefingGuard;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class ZombieDifficultyAbilities {
    /** Persisted marker used to make an acquired player target sticky until death. */
    public static final String AGGRO_TAG = "threatdirector_zombie_aggro";
    private static final double REINFORCEMENT_SCAN_RADIUS = 24.0;
    private static final int SPAWN_ATTEMPTS_PER_REINFORCEMENT = 12;

    private ZombieDifficultyAbilities() {
    }

    /** Reasserts a locked player target so walls and distance cannot make it drop. */
    public static boolean maintainAggro(ZombieEntity zombie) {
        if (!ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().zombie().enabled()
                || !ThreatDirectorMod.config().modded().zombie().stickyAggroEnabled()
                || !(zombie.getTarget() instanceof ServerPlayerEntity player)
                || !player.isAlive()
                || !zombie.getCommandTags().contains(AGGRO_TAG)) {
            return false;
        }
        zombie.setTarget(player);
        return true;
    }

    public static void markAggro(ZombieEntity zombie, ServerPlayerEntity target) {
        if (ThreatDirectorMod.isModdedEnabled()
                && ThreatDirectorMod.config().modded().zombie().enabled()
                && ThreatDirectorMod.config().modded().zombie().stickyAggroEnabled()
                && target != null && target.isAlive()) {
            zombie.addCommandTag(AGGRO_TAG);
            zombie.setTarget(target);
        }
    }

    /** Alerts nearby zombies belonging to the same server-side threat field. */
    public static int alertNearby(ZombieEntity caller, ServerPlayerEntity target) {
        if (!(caller.getWorld() instanceof ServerWorld world)
                || !ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().zombie().nearbyAggroEnabled()
                || target == null
                || !target.isAlive()) {
            return 0;
        }
        int difficultyId = world.getDifficulty().getId();
        double radius = ThreatScaling.zombieAlertRadius(
                difficultyId,
                ThreatScaling.playerThreat(target),
                BloodMoonModule.isActive(world));
        if (radius <= 0.0) {
            return 0;
        }
        int alerted = 0;
        for (ZombieEntity zombie : world.getEntitiesByClass(
                ZombieEntity.class,
                caller.getBoundingBox().expand(radius),
                entity -> entity.isAlive() && entity != caller)) {
            markAggro(zombie, target);
            alerted++;
        }
        return alerted;
    }

    /**
     * Converts first contact into a visible lightning-backed horde event. The caller
     * is marked on success; the mixin marks it after a failed roll as well, preventing
     * repeated rolls every tick while preserving the "never forget" target rule.
     */
    public static int tryTriggerHorde(ZombieEntity caller, ServerPlayerEntity target) {
        if (!(caller.getWorld() instanceof ServerWorld world)
                || !ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().zombie().lightningHordesEnabled()
                || target == null
                || !target.isAlive()
                || caller.getCommandTags().contains(AGGRO_TAG)
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) {
            return 0;
        }
        int difficultyId = world.getDifficulty().getId();
        double threat = ThreatScaling.playerThreat(target);
        boolean bloodMoon = BloodMoonModule.isActive(world);
        double chance = ThreatScaling.zombieHordeChance(difficultyId, threat, bloodMoon);
        if (chance <= 0.0 || caller.getRandom().nextDouble() >= chance) {
            return 0;
        }

        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(caller.getX(), caller.getY(), caller.getZ());
            lightning.setCosmetic(!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING));
            world.spawnEntity(lightning);
        }

        markAggro(caller, target);
        var feature = ThreatDirectorMod.config().modded().zombie();
        int minimum = difficultyId >= 3
                ? bloodMoon ? feature.bloodMoonHordeMinimum() : feature.hardHordeMinimum()
                : feature.normalHordeMinimum();
        int maximum = difficultyId >= 3
                ? bloodMoon ? feature.bloodMoonHordeMaximum() : feature.hardHordeMaximum()
                : feature.normalHordeMaximum();
        int roll = caller.getRandom().nextInt(maximum - minimum + 1);
        int count = ThreatScaling.zombieHordeCount(difficultyId, threat, roll, bloodMoon);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawnOne(world, caller, target)) {
                spawned++;
            }
        }
        return spawned;
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

        Item selected;
        if (rules.shieldAndPickaxe()
                && DifficultyAbilityPolicy.acceptsRoll(
                zombie.getRandom().nextDouble(), rules.pickaxeChance())) {
            selected = Items.IRON_PICKAXE;
        } else {
            Item[] tools = rules.shieldAndPickaxe()
                    ? new Item[]{Items.IRON_SHOVEL, Items.IRON_AXE, Items.IRON_SWORD}
                    : new Item[]{Items.STONE_SHOVEL, Items.STONE_AXE, Items.IRON_SWORD};
            selected = tools[zombie.getRandom().nextInt(
                    Math.min(rules.meleeToolChoices(), tools.length))];
        }
        zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(selected));
        zombie.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05F);

        if (rules.shieldAndPickaxe()
                && DifficultyAbilityPolicy.acceptsRoll(
                zombie.getRandom().nextDouble(), rules.shieldChance())) {
            zombie.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            zombie.setEquipmentDropChance(EquipmentSlot.OFFHAND, 0.02F);
        }
    }

    /** Returns true only when at least one bounded reinforcement was actually spawned. */
    public static boolean tryCallReinforcements(ZombieEntity caller, LivingEntity attacker) {
        if (!(caller.getWorld() instanceof ServerWorld world)
                || !ThreatDirectorMod.config().modded().zombie().reinforcementsEnabled()
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
                || !ThreatDirectorMod.config().modded().zombie().blockBreakingEnabled()
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
                if (attacker instanceof ServerPlayerEntity player
                        && ThreatScaling.playerThreat(player) >= ThreatScaling.activationThreat()) {
                    markAggro(reinforcement, player);
                }
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
