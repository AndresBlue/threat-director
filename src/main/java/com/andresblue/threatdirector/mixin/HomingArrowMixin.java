package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.apocalypse.ApocalypseSkeletonModule;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-side steering for arrows fired by enhanced skeletons. */
@Mixin(PersistentProjectileEntity.class)
public abstract class HomingArrowMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void threatdirector$steerTowardTarget(CallbackInfo callbackInfo) {
        PersistentProjectileEntity arrow = (PersistentProjectileEntity) (Object) this;
        if (arrow.getWorld().isClient
                || !ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().skeleton().enabled()
                || !ThreatDirectorMod.config().modded().skeleton().homingArrows()
                || !arrow.getCommandTags().contains(ApocalypseSkeletonModule.ENHANCED_ARROW_TAG)) {
            return;
        }
        Entity owner = arrow.getOwner();
        if (!(owner instanceof AbstractSkeletonEntity skeleton)
                || !(skeleton.getTarget() instanceof ServerPlayerEntity player)
                || !player.isAlive()) {
            return;
        }
        int difficultyId = arrow.getWorld().getDifficulty().getId();
        double strength = ThreatScaling.homingStrength(
                difficultyId,
                ThreatScaling.playerThreat(player),
                BloodMoonModule.isActive(arrow.getWorld()));
        if (strength <= 0.0) {
            return;
        }
        Vec3d current = arrow.getVelocity();
        double speed = current.length();
        if (speed < 1.0E-5) {
            return;
        }
        Vec3d desired = player.getPos()
                .add(0.0, player.getStandingEyeHeight() * 0.75, 0.0)
                .subtract(arrow.getPos());
        if (desired.lengthSquared() < 1.0E-5) {
            return;
        }
        Vec3d steered = current.normalize()
                .lerp(desired.normalize(), Math.min(0.45, strength))
                .normalize()
                .multiply(speed);
        arrow.setVelocity(steered);
        arrow.velocityModified = true;
    }
}
