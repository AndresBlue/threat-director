package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.difficulty.DifficultyAbilityPolicy;
import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import com.andresblue.threatdirector.difficulty.ZombieDifficultyAbilities;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
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
        if (rules.maxReinforcements() == 0 || now < threatdirector$nextReinforcementTick) {
            return;
        }
        threatdirector$nextReinforcementTick = now + rules.reinforcementCooldownTicks();
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity living ? living : null;
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
