package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.apocalypse.ApocalypseSkeletonModule;
import com.andresblue.threatdirector.apocalypse.SkeletonCombatPolicy;
import com.andresblue.threatdirector.bloodmoon.BloodMoonModule;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityMixin {
    @Shadow
    protected abstract PersistentProjectileEntity createArrowProjectile(
            ItemStack arrow,
            float damageModifier);

    @Inject(
            method = "attack(Lnet/minecraft/entity/LivingEntity;F)V",
            at = @At("HEAD"),
            cancellable = true)
    private void threatdirector$attackWithPredictiveArrow(
            LivingEntity target,
            float pullProgress,
            CallbackInfo callbackInfo) {
        AbstractSkeletonEntity skeleton = (AbstractSkeletonEntity) (Object) this;
        World world = skeleton.getWorld();
        int difficultyId = world.getDifficulty().getId();
        boolean bloodMoon = BloodMoonModule.isActive(world);
        if (world.isClient
                || !ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().skeleton().enabled()
                || !ThreatDirectorMod.config().modded().skeleton().predictiveAim()
                || !ApocalypseSkeletonModule.isRegistered()
                || !SkeletonCombatPolicy.isEnhancedDifficulty(difficultyId)
                || (target instanceof ServerPlayerEntity player
                && ThreatScaling.playerThreat(player) < ThreatScaling.activationThreat())) {
            return;
        }

        ItemStack ammunition = skeleton.getProjectileType(skeleton.getStackInHand(
                ProjectileUtil.getHandPossiblyHolding(skeleton, Items.BOW)));
        PersistentProjectileEntity projectile = createArrowProjectile(ammunition, pullProgress);
        Vec3d targetVelocity = target.getVelocity();
        SkeletonCombatPolicy.AimVector aim = SkeletonCombatPolicy.solveAim(
                difficultyId,
                skeleton.getX(),
                projectile.getY(),
                skeleton.getZ(),
                target.getX(),
                target.getBodyY(1.0 / 3.0),
                target.getZ(),
                targetVelocity.x,
                targetVelocity.y,
                targetVelocity.z,
                bloodMoon);
        projectile.setVelocity(aim.x(), aim.y(), aim.z(), aim.speed(), aim.divergence());
        projectile.addCommandTag(ApocalypseSkeletonModule.ENHANCED_ARROW_TAG);
        addVanillaArrowEffect(skeleton, projectile, difficultyId, target, bloodMoon);

        skeleton.playSound(
                SoundEvents.ENTITY_SKELETON_SHOOT,
                1.0F,
                1.0F / (skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
        world.spawnEntity(projectile);
        callbackInfo.cancel();
    }

    private static void addVanillaArrowEffect(
            AbstractSkeletonEntity skeleton,
            PersistentProjectileEntity projectile,
            int difficultyId,
            LivingEntity target,
            boolean bloodMoon) {
        if (!(projectile instanceof ArrowEntity arrow)) {
            return;
        }

        if (target instanceof ServerPlayerEntity player
                && ThreatScaling.playerThreat(player) < ThreatScaling.activationThreat()) {
            return;
        }
        SkeletonCombatPolicy.ArrowPayload payload = SkeletonCombatPolicy.arrowPayload(
                difficultyId,
                skeleton.getRandom().nextFloat(),
                skeleton.getRandom().nextFloat(),
                bloodMoon);
        if (!payload.appliesEffect()) {
            return;
        }

        StatusEffect effect = switch (payload.effect()) {
            case SLOWNESS -> StatusEffects.SLOWNESS;
            case POISON -> StatusEffects.POISON;
            case NAUSEA -> StatusEffects.NAUSEA;
            case BLINDNESS -> StatusEffects.BLINDNESS;
            case NONE -> throw new IllegalStateException("NONE payload cannot apply an effect");
        };
        arrow.addEffect(new StatusEffectInstance(
                effect,
                payload.durationTicks(),
                payload.amplifier()));
    }
}
