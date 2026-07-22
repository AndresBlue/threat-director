package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.HorizontalVelocity;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Mode;
import com.andresblue.threatdirector.creeper.ApocalypticCreeperPolicy.Profile;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
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

    @Inject(method = "tick", at = @At("HEAD"))
    private void threatdirector$applyApocalypticBehavior(CallbackInfo callbackInfo) {
        CreeperEntity creeper = (CreeperEntity) (Object) this;
        World world = creeper.getWorld();
        if (world.isClient()) {
            return;
        }

        LivingEntity target = creeper.getTarget();
        double targetThreat = target instanceof ServerPlayerEntity player
                ? ThreatScaling.playerThreat(player)
                : 0.0;
        Mode mode = ThreatDirectorMod.isEnabled()
                ? threatdirector$mode(world.getDifficulty(), targetThreat)
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
            threatdirector$dashCooldownTicks = ApocalypticCreeperPolicy.DASH_COOLDOWN_TICKS;
        }

        boolean movementObstructed = creeper.horizontalCollision || creeper.getNavigation().isIdle();
        boolean wallCandidate = ApocalypticCreeperPolicy.isWallFuseCandidate(
                profile,
                targetPresent,
                world instanceof ServerWorld serverWorld
                        && MobGriefingGuard.permits(serverWorld, creeper.getBlockPos()),
                targetVisible,
                movementObstructed,
                priming,
                distanceSquared
        );
        threatdirector$wallBlockedTicks = wallCandidate
                ? Math.min(ApocalypticCreeperPolicy.WALL_FUSE_DELAY_TICKS, threatdirector$wallBlockedTicks + 1)
                : 0;
        if (threatdirector$wallBlockedTicks >= ApocalypticCreeperPolicy.WALL_FUSE_DELAY_TICKS) {
            creeper.ignite();
            creeper.setFuseSpeed(1);
            threatdirector$wallBlockedTicks = 0;
            priming = true;
        }

        threatdirector$advanceShortFuse(creeper, profile, priming);
    }

    @Unique
    private static Mode threatdirector$mode(Difficulty difficulty, double threat) {
        if (threat < ThreatScaling.ACTIVATION_THREAT) {
            return Mode.VANILLA;
        }
        return switch (difficulty) {
            case NORMAL -> Mode.PARTIAL;
            case HARD -> threat >= 70.0 ? Mode.APOCALYPTIC : Mode.PARTIAL;
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
