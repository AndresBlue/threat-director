package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.DifficultyAbilityPolicy;
import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import com.andresblue.threatdirector.difficulty.ZombieDifficultyAbilities;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class ZombieDifficultyMixin {
    @Unique
    private long threatdirector$nextReinforcementTick;
    @Unique
    private long threatdirector$nextBlockBreakTick;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void threatdirector$maintainStickyAggro(CallbackInfo callback) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld)
                || ZombieDifficultyAbilities.maintainAggro(self)) {
            return;
        }
        if (self.getCommandTags().contains(ZombieDifficultyAbilities.AGGRO_TAG)
                || !(self.getTarget() instanceof ServerPlayerEntity player)
                || !player.isAlive()
                || ThreatScaling.playerThreat(player) < ThreatScaling.ACTIVATION_THREAT) {
            return;
        }
        // Roll once at first contact, then make the target permanent even if the roll fails.
        ZombieDifficultyAbilities.tryTriggerHorde(self, player);
        ZombieDifficultyAbilities.markAggro(self, player);
    }

    @Inject(method = "initialize", at = @At("RETURN"))
    private void threatdirector$equipForDifficulty(ServerWorldAccess world, LocalDifficulty difficulty,
                                                   SpawnReason spawnReason, EntityData entityData,
                                                   NbtCompound entityNbt,
                                                   CallbackInfoReturnable<EntityData> callback) {
        ZombieDifficultyAbilities.equipForDifficulty((ZombieEntity) (Object) this);
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void threatdirector$callBoundedReinforcements(DamageSource source, float amount,
                                                          CallbackInfoReturnable<Boolean> callback) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!callback.getReturnValue() || !(self.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DifficultyAbilityPolicy.ZombieRules rules = MinecraftDifficultyProfiles.forWorld(world).zombie();
        long now = world.getTime();
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;
        if (attacker instanceof ServerPlayerEntity player
                && ThreatScaling.playerThreat(player) >= ThreatScaling.ACTIVATION_THREAT) {
            ZombieDifficultyAbilities.alertNearby(self, player);
            ZombieDifficultyAbilities.tryTriggerHorde(self, player);
            ZombieDifficultyAbilities.markAggro(self, player);
        }
        if (rules.maxReinforcements() == 0 || now < threatdirector$nextReinforcementTick) {
            return;
        }
        threatdirector$nextReinforcementTick = now + rules.reinforcementCooldownTicks();
        ZombieDifficultyAbilities.tryCallReinforcements(self, attacker);
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void threatdirector$breakPreferredObstruction(CallbackInfo callback) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int cooldown = MinecraftDifficultyProfiles.forWorld(world).zombie().blockBreakCooldownTicks();
        long now = world.getTime();
        if (cooldown == 0 || now < threatdirector$nextBlockBreakTick) {
            return;
        }
        // Charge the interval before attempting, so blocked/claimed positions cannot create hot loops.
        threatdirector$nextBlockBreakTick = now + cooldown;
        ZombieDifficultyAbilities.tryBreakPreferredObstruction(self);
    }
}
