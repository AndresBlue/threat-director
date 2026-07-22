package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.EndermanDifficultyAbilities;
import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import com.andresblue.threatdirector.difficulty.SpiderDifficultyAbilities;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpiderEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDifficultyMixin {
    @Inject(method = "damage", at = @At("RETURN"))
    private void threatdirector$alertEndermenWhenHostileIsHurt(DamageSource source, float amount,
                                                               CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue() || !((Object) this instanceof HostileEntity hostile)) {
            return;
        }
        if (source.getAttacker() instanceof LivingEntity attacker) {
            EndermanDifficultyAbilities.alertNearbyAllies(hostile, attacker);
        }
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void threatdirector$hatchSpidersOnDeath(DamageSource source, CallbackInfo callback) {
        if ((Object) this instanceof SpiderEntity spider) {
            SpiderDifficultyAbilities.spawnHatchlings(spider, source);
        }
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void threatdirector$useTwoBlockEndermanDimensions(EntityPose pose,
                                                               CallbackInfoReturnable<EntityDimensions> callback) {
        if (pose != EntityPose.CROUCHING || !((Object) this instanceof EndermanEntity enderman)
                || !MinecraftDifficultyProfiles.forWorld(enderman.getWorld()).enderman()
                .twoBlockCombatPosture()) {
            return;
        }
        callback.setReturnValue(EntityDimensions.changing(callback.getReturnValue().width, 1.95F));
    }
}
