package com.andresblue.threatdirector.drowned;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonService;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import java.util.UUID;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Minecraft adapter for the pure drowned night policy. */
public final class DrownedNightAbilities {
    public static final UUID SWIM_SPEED_MODIFIER_ID =
            UUID.fromString("1d3552fe-46bf-49e0-a124-194556f0b701");

    private DrownedNightAbilities() {
    }

    public static DrownedNightPolicy.Profile profile(ServerWorld world) {
        boolean night = world.isNight();
        var feature = ThreatDirectorMod.config().modded().drowned();
        if (!ThreatDirectorMod.isModdedEnabled() || !feature.enabled()
                || !night || world.getDifficulty().getId() <= 1) {
            return DrownedNightPolicy.profile(1, false, false);
        }
        var tier = world.getDifficulty().getId() >= 3
                ? BloodMoonService.isActive(world) ? feature.bloodMoon() : feature.hard()
                : feature.normal();
        return new DrownedNightPolicy.Profile(
                feature.swimSpeedEnabled() ? tier.swimSpeedMultiplier() : 1.0,
                feature.tridentsEnabled() ? tier.tridentChance() : 0.0,
                feature.riptideEnabled() ? tier.riptideChance() : 0.0,
                feature.riptideEnabled() ? tier.riptideLevel() : 0,
                feature.riptideEnabled() ? tier.dashSpeed() : 0.0,
                tier.dashCooldownTicks(),
                tier.targetRange(),
                feature.boatBreakingEnabled() && tier.breaksBoats());
    }

    public static void equipNightTrident(DrownedEntity drowned) {
        if (!(drowned.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DrownedNightPolicy.Profile profile = profile(world);
        if (!profile.enhanced()
                || !DrownedNightPolicy.acceptsRoll(
                        drowned.getRandom().nextDouble(), profile.tridentChance())) {
            return;
        }
        ItemStack trident = new ItemStack(Items.TRIDENT);
        if (DrownedNightPolicy.acceptsRoll(
                drowned.getRandom().nextDouble(), profile.riptideChance())) {
            trident.addEnchantment(Enchantments.RIPTIDE, profile.riptideLevel());
        }
        drowned.equipStack(EquipmentSlot.MAINHAND, trident);
        drowned.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.025F);
        GameplayDebugEvents.record("drowned.trident_equipped", java.util.Map.of(
                "entityUuid", drowned.getUuid().toString(),
                "riptide", EnchantmentHelper.getLevel(Enchantments.RIPTIDE, trident),
                "difficulty", world.getDifficulty().getName()));
    }

    public static void updateSwimSpeed(DrownedEntity drowned, DrownedNightPolicy.Profile profile) {
        EntityAttributeInstance speed = drowned.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean shouldBoost = profile.swimSpeedMultiplier() > 1.0 && drowned.isTouchingWater();
        EntityAttributeModifier existing = speed.getModifier(SWIM_SPEED_MODIFIER_ID);
        if (!shouldBoost) {
            if (existing != null) {
                speed.removeModifier(SWIM_SPEED_MODIFIER_ID);
            }
            return;
        }
        double value = profile.swimSpeedMultiplier() - 1.0;
        if (existing != null && Math.abs(existing.getValue() - value) < 1.0E-6) {
            return;
        }
        if (existing != null) {
            speed.removeModifier(SWIM_SPEED_MODIFIER_ID);
        }
        speed.addTemporaryModifier(new EntityAttributeModifier(
                SWIM_SPEED_MODIFIER_ID,
                "Threat Director night swimmer",
                value,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    /** A single nearest-player lookup, intended to run at most once per second. */
    public static void acquireOceanTarget(DrownedEntity drowned, DrownedNightPolicy.Profile profile) {
        if (!profile.enhanced()
                || drowned.getTarget() != null
                || !(drowned.getWorld() instanceof ServerWorld world)) {
            return;
        }
        PlayerEntity nearest = world.getClosestPlayer(drowned, profile.targetRange());
        if (nearest instanceof ServerPlayerEntity player
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()) {
            drowned.setTarget(player);
        }
    }

    public static boolean dashTowardTarget(
            DrownedEntity drowned,
            DrownedNightPolicy.Profile profile
    ) {
        LivingEntity target = drowned.getTarget();
        if (!profile.enhanced()
                || !drowned.isSubmergedInWater()
                || target == null
                || !target.isAlive()
                || drowned.squaredDistanceTo(target) > profile.targetRange() * profile.targetRange()) {
            return false;
        }
        Vec3d current = drowned.getVelocity();
        Vec3d offset = target.getPos()
                .add(0.0, target.getStandingEyeHeight() * 0.45, 0.0)
                .subtract(drowned.getPos());
        DrownedNightPolicy.Velocity dash = DrownedNightPolicy.dashVelocity(
                current.x, current.y, current.z,
                offset.x, offset.y, offset.z,
                profile.dashSpeed()
        );
        drowned.setVelocity(dash.x(), dash.y(), dash.z());
        drowned.velocityModified = true;
        return true;
    }
}
