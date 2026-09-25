package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.HorizontalVelocity;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Mode;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Profile;
import com.andresblue.threatdirector.creeper.CreeperBreachCoordinator;
import com.andresblue.threatdirector.creeper.CreeperBreachCoordinator.Directive;
import com.andresblue.threatdirector.server.MobGriefingGuard;
import com.andresblue.threatdirector.server.ThreatScaling;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {
    @Unique
    private static final UUID THREATDIRECTOR_REAR_APPROACH_ID =
            UUID.fromString("9c859ff8-7b1b-4d8b-9ec2-f1c66cc08420");

    @Shadow
    private int currentFuseTime;

    @Shadow
    private int fuseTime;

    @Unique
    private int threatdirector$selectedFuseTicks = -1;

    @Unique
    private int threatdirector$elapsedPrimingTicks;

    @Unique
    private int threatdirector$wallBlockedTicks;

    @Unique
    private int threatdirector$dashCooldownTicks;

    @Unique
    private UUID threatdirector$rememberedPlayerId;

    @Unique
    private long threatdirector$targetMemoryExpiresAtTick;

    @Inject(method = "tick", at = @At("HEAD"))
    private void threatdirector$applyApocalypticBehavior(CallbackInfo callbackInfo) {
        CreeperEntity creeper = (CreeperEntity) (Object) this;
        World world = creeper.getWorld();
        if (world.isClient()) {
            return;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        LivingEntity target = threatdirector$rememberOrRestoreTarget(creeper, serverWorld);
        double targetThreat = target instanceof ServerPlayerEntity player
                ? ThreatScaling.playerThreat(player)
                : 0.0;
        Mode mode = ThreatDirectorMod.isModdedEnabled()
                && ThreatDirectorMod.config().modded().creeper().enabled()
                ? threatdirector$mode(world, targetThreat)
                : Mode.VANILLA;
        Profile profile = ApocalypticCreeperPolicy.profile(mode);
        if (threatdirector$dashCooldownTicks > 0) {
            threatdirector$dashCooldownTicks--;
        }

        boolean targetPresent = target != null && target.isAlive();
        double distanceSquared = targetPresent ? creeper.squaredDistanceTo(target) : Double.POSITIVE_INFINITY;
        boolean targetVisible = targetPresent && creeper.getVisibilityCache().canSee(target);
        boolean targetFacesAway = targetPresent && threatdirector$targetFacesAway(creeper, target);
        boolean priming = creeper.isIgnited() || creeper.getFuseSpeed() > 0;

        threatdirector$updateRearApproach(
                creeper,
                profile,
                targetPresent,
                targetFacesAway,
                priming,
                distanceSquared
        );

        if (ApocalypticCreeperPolicy.shouldDash(
                profile,
                targetPresent,
                targetFacesAway,
                targetVisible,
                creeper.isOnGround(),
                priming,
                threatdirector$dashCooldownTicks,
                distanceSquared
        )) {
            threatdirector$dashToward(creeper, target);
            threatdirector$dashCooldownTicks = ThreatDirectorMod.config().modded().creeper()
                    .dashCooldownTicks();
        }

        Directive breach = target instanceof ServerPlayerEntity player
                && ThreatDirectorMod.config().modded().creeper().coordinatedBreaching()
                ? CreeperBreachCoordinator.directive(serverWorld, creeper, player)
                : new Directive(false, null);
        if (breach.designated() && !priming
                && Math.floorMod(creeper.age + creeper.getId(), 10) == 0) {
            BlockPos wall = breach.wallPosition();
            creeper.getNavigation().startMovingTo(
                    wall.getX() + 0.5D, wall.getY(), wall.getZ() + 0.5D, 1.15D);
        }

        boolean movementObstructed = creeper.horizontalCollision || creeper.getNavigation().isIdle();
        boolean coordinatedBreach = !priming
                && CreeperBreachCoordinator.isAtBreachPoint(creeper, breach);
        boolean legacyWallCandidate = !(target instanceof ServerPlayerEntity)
                && ApocalypticCreeperPolicy.isWallFuseCandidate(
                profile,
                targetPresent,
                MobGriefingGuard.permits(serverWorld, creeper.getBlockPos()),
                targetVisible,
                movementObstructed,
                priming,
                distanceSquared
        );
        boolean wallCandidate = coordinatedBreach || legacyWallCandidate;
        threatdirector$wallBlockedTicks = wallCandidate
                ? Math.min(ThreatDirectorMod.config().modded().creeper().breachFuseDelayTicks(),
                threatdirector$wallBlockedTicks + 1)
                : 0;
        if (threatdirector$wallBlockedTicks >= ThreatDirectorMod.config().modded().creeper()
                .breachFuseDelayTicks()) {
            creeper.ignite();
            creeper.setFuseSpeed(1);
            threatdirector$wallBlockedTicks = 0;
            priming = true;
        }

        threatdirector$advanceShortFuse(creeper, profile, priming);
    }

    @Unique
    private static Mode threatdirector$mode(World world, double threat) {
        if (threat < ThreatScaling.activationThreat()) {
            return Mode.VANILLA;
        }
        return switch (world.getDifficulty()) {
            case NORMAL -> Mode.PARTIAL;
            case HARD -> threat >= 70.0 && BloodMoonModule.isActive(world)
                    ? Mode.APOCALYPTIC
                    : Mode.PARTIAL;
            default -> Mode.VANILLA;
        };
    }

    @Unique
    private static boolean threatdirector$targetFacesAway(CreeperEntity creeper, LivingEntity target) {
        Vec3d look = target.getRotationVector();
        Vec3d targetToCreeper = creeper.getPos().subtract(target.getPos());
        return ApocalypticCreeperPolicy.targetFacesAway(
                look.x,
                look.z,
                targetToCreeper.x,
                targetToCreeper.z
        );
    }

    @Unique
    private LivingEntity threatdirector$rememberOrRestoreTarget(
            CreeperEntity creeper,
            ServerWorld world
    ) {
        LivingEntity current = creeper.getTarget();
        if (current instanceof ServerPlayerEntity player && player.isAlive()) {
            double threat = ThreatScaling.playerThreat(player);
            if (ThreatDirectorMod.isModdedEnabled()
                    && ThreatDirectorMod.config().modded().creeper().enabled()
                    && world.getDifficulty().getId() >= 2
                    && threat >= ThreatScaling.activationThreat()) {
                threatdirector$rememberedPlayerId = player.getUuid();
                threatdirector$targetMemoryExpiresAtTick = world.getTime()
                        + ThreatDirectorMod.config().modded().creeper().targetMemorySeconds() * 20L;
            }
            return current;
        }
        if (threatdirector$rememberedPlayerId == null) {
            return current;
        }

        ServerPlayerEntity remembered = world.getServer().getPlayerManager()
                .getPlayer(threatdirector$rememberedPlayerId);
        double threat = ThreatScaling.playerThreat(remembered);
        boolean shouldRestore = remembered != null
                && ApocalypticCreeperPolicy.shouldRetainPlayerTarget(
                ThreatDirectorMod.isModdedEnabled(),
                world.getDifficulty().getId(),
                threat,
                ThreatScaling.activationThreat(),
                remembered.isAlive(),
                remembered.getWorld() == world,
                threatdirector$targetMemoryExpiresAtTick - world.getTime(),
                creeper.squaredDistanceTo(remembered));
        if (!shouldRestore) {
            threatdirector$rememberedPlayerId = null;
            return current;
        }
        creeper.setTarget(remembered);
        // A wall may make vanilla clear the goal again on this same tick. Refreshing
        // the grace window here means LOS alone never expires the target; death,
        // dimension changes, low threat or leaving the 48-block leash still do.
        threatdirector$targetMemoryExpiresAtTick = world.getTime()
                + ThreatDirectorMod.config().modded().creeper().targetMemorySeconds() * 20L;
        return remembered;
    }

    @Unique
    private void threatdirector$updateRearApproach(
            CreeperEntity creeper,
            Profile profile,
            boolean targetPresent,
            boolean targetFacesAway,
            boolean priming,
            double distanceSquared
    ) {
        EntityAttributeInstance speed = creeper.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }

        boolean boosted = ApocalypticCreeperPolicy.shouldBoostRearApproach(
                profile,
                targetPresent,
                targetFacesAway,
                priming,
                distanceSquared
        );
        EntityAttributeModifier existing = speed.getModifier(THREATDIRECTOR_REAR_APPROACH_ID);
        if (!boosted) {
            if (existing != null) {
                speed.removeModifier(THREATDIRECTOR_REAR_APPROACH_ID);
            }
            return;
        }

        double modifierValue = profile.rearApproachMultiplier() - 1.0D;
        if (existing != null && Math.abs(existing.getValue() - modifierValue) < 1.0E-6D) {
            return;
        }
        if (existing != null) {
            speed.removeModifier(THREATDIRECTOR_REAR_APPROACH_ID);
        }
        speed.addTemporaryModifier(new EntityAttributeModifier(
                THREATDIRECTOR_REAR_APPROACH_ID,
                "Threat Director rear approach",
                modifierValue,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    @Unique
    private void threatdirector$dashToward(CreeperEntity creeper, LivingEntity target) {
        Vec3d velocity = creeper.getVelocity();
        Vec3d offset = target.getPos().subtract(creeper.getPos());
        HorizontalVelocity dash = ApocalypticCreeperPolicy.boundedDashVelocity(
                velocity.x,
                velocity.z,
                offset.x,
                offset.z
        );
        creeper.setVelocity(dash.x(), velocity.y, dash.z());
        creeper.velocityModified = true;
    }

    @Unique
    private void threatdirector$advanceShortFuse(CreeperEntity creeper, Profile profile, boolean priming) {
        if (profile.minimumFuseRatio() >= 1.0D) {
            threatdirector$resetFuseSchedule();
            return;
        }
        if (!priming) {
            if (currentFuseTime <= 0) {
                threatdirector$resetFuseSchedule();
            }
            return;
        }

        if (threatdirector$selectedFuseTicks < 0) {
            threatdirector$selectedFuseTicks = ApocalypticCreeperPolicy.randomizedFuseTicks(
                    profile,
                    fuseTime,
                    creeper.getRandom().nextDouble()
            );
            threatdirector$elapsedPrimingTicks = 0;
        }
        threatdirector$elapsedPrimingTicks++;
        int minimumProgress = ApocalypticCreeperPolicy.minimumFuseProgressBeforeVanillaTick(
                fuseTime,
                threatdirector$selectedFuseTicks,
                threatdirector$elapsedPrimingTicks
        );
        currentFuseTime = Math.max(currentFuseTime, minimumProgress);
    }

    @Unique
    private void threatdirector$resetFuseSchedule() {
        threatdirector$selectedFuseTicks = -1;
        threatdirector$elapsedPrimingTicks = 0;
    }
}
