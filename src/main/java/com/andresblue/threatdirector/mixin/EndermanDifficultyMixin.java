package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.DifficultyAbilityPolicy;
import com.andresblue.threatdirector.difficulty.EndermanDifficultyAbilities;
import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndermanEntity.class)
public abstract class EndermanDifficultyMixin {
    @Unique
    private int threatdirector$pillarPressureTicks;
    @Unique
    private long threatdirector$nextPillarTeleportTick;
    @Unique
    private boolean threatdirector$ownsCombatPosture;

    @Invoker("teleportTo")
    protected abstract boolean threatdirector$teleportTo(double x, double y, double z);

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void threatdirector$updateCombatPostureAndAntiPillar(CallbackInfo callback) {
        EndermanEntity self = (EndermanEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DifficultyAbilityPolicy.EndermanRules rules = MinecraftDifficultyProfiles.forWorld(world).enderman();
        LivingEntity target = self.getTarget();
        boolean combatPosture = rules.twoBlockCombatPosture() && target != null && target.isAlive();
        if (combatPosture && self.getPose() != EntityPose.CROUCHING) {
            self.setPose(EntityPose.CROUCHING);
            threatdirector$ownsCombatPosture = true;
        } else if (!combatPosture && threatdirector$ownsCombatPosture) {
            if (self.getPose() == EntityPose.CROUCHING) {
                self.setPose(EntityPose.STANDING);
            }
            threatdirector$ownsCombatPosture = false;
        }

        if (!combatPosture || !EndermanDifficultyAbilities.hasPillarAdvantage(self, target)) {
            threatdirector$pillarPressureTicks = Math.max(0, threatdirector$pillarPressureTicks - 2);
            return;
        }
        threatdirector$pillarPressureTicks++;
        long now = world.getTime();
        if (threatdirector$pillarPressureTicks < rules.pillarTriggerTicks()
                || now < threatdirector$nextPillarTeleportTick) {
            return;
        }

        threatdirector$nextPillarTeleportTick = now + rules.pillarTeleportCooldownTicks();
        threatdirector$pillarPressureTicks = 0;
        if (!threatdirector$teleportTo(target.getX(), target.getY(), target.getZ())) {
            double angle = self.getRandom().nextDouble() * Math.PI * 2.0;
            threatdirector$teleportTo(
                    target.getX() + Math.cos(angle) * 0.35,
                    target.getY(),
                    target.getZ() + Math.sin(angle) * 0.35
            );
        }
    }

    @Inject(method = "getActiveEyeHeight", at = @At("HEAD"), cancellable = true)
    private void threatdirector$lowerEyesForCombatPosture(EntityPose pose, EntityDimensions dimensions,
                                                           CallbackInfoReturnable<Float> callback) {
        EndermanEntity self = (EndermanEntity) (Object) this;
        if (pose == EntityPose.CROUCHING
                && MinecraftDifficultyProfiles.forWorld(self.getWorld()).enderman().twoBlockCombatPosture()) {
            callback.setReturnValue(1.78F);
        }
    }
}
