package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.difficulty.DifficultyAbilityPolicy;
import com.andresblue.threatdirector.difficulty.MinecraftDifficultyProfiles;
import com.andresblue.threatdirector.difficulty.ZombieDifficultyAbilities;
import com.andresblue.threatdirector.difficulty.ZombieShieldPolicy;
import com.andresblue.threatdirector.server.ThreatScaling;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
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
    @Unique
    private long threatdirector$shieldDisabledUntilTick;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void threatdirector$maintainStickyAggro(CallbackInfo callback) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) {
            return;
        }
        threatdirector$updateShieldPose(self, world);
        if (ZombieDifficultyAbilities.maintainAggro(self)) {
            return;
        }
        if (self.getCommandTags().contains(ZombieDifficultyAbilities.AGGRO_TAG)
                || !(self.getTarget() instanceof ServerPlayerEntity player)
                || !player.isAlive()
                || ThreatScaling.playerThreat(player) < ThreatScaling.activationThreat()) {
            return;
        }
        // Roll once at first contact, then make the target permanent even if the roll fails.
        ZombieDifficultyAbilities.tryTriggerHorde(self, player);
        ZombieDifficultyAbilities.markAggro(self, player);
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void threatdirector$blockDamageWithShield(DamageSource source, float amount,
                                                       CallbackInfoReturnable<Boolean> callback) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!ThreatDirectorMod.isModdedEnabled()
                || !ThreatDirectorMod.config().modded().zombie().enabled()
                || !ThreatDirectorMod.config().modded().zombie().shieldBlockingEnabled()
                || !(self.getWorld() instanceof ServerWorld world)
                || !self.getOffHandStack().isOf(Items.SHIELD)
                || !(source.getAttacker() instanceof ServerPlayerEntity player)) {
            return;
        }

        boolean axeMelee = source.getSource() == player
                && player.getMainHandStack().getItem() instanceof AxeItem;
        if (axeMelee) {
            threatdirector$shieldDisabledUntilTick = world.getTime()
                    + ThreatDirectorMod.config().modded().zombie().axeShieldDisableTicks();
            self.stopUsingItem();
            world.playSound(null, self.getX(), self.getY(), self.getZ(),
                    SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE,
                    1.0F, 0.8F + self.getRandom().nextFloat() * 0.2F);
            return;
        }

        Vec3d sourcePosition = source.getPosition();
        if (sourcePosition == null) {
            return;
        }
        Vec3d facing = self.getRotationVector();
        Vec3d sourceOffset = sourcePosition.subtract(self.getPos());
        DifficultyAbilityPolicy.ZombieRules rules = MinecraftDifficultyProfiles.forWorld(world).zombie();
        boolean inFront = ZombieShieldPolicy.isAttackInFront(
                facing.x, facing.z, sourceOffset.x, sourceOffset.z);
        boolean blocked = ZombieShieldPolicy.shouldBlock(
                true,
                world.getTime() < threatdirector$shieldDisabledUntilTick,
                source.isIn(DamageTypeTags.BYPASSES_SHIELD),
                inFront,
                false,
                rules.shieldBlockChance(),
                self.getRandom().nextDouble());
        if (!blocked) {
            return;
        }

        self.setCurrentHand(Hand.OFF_HAND);
        self.setTarget(player);
        if (ThreatScaling.playerThreat(player) >= ThreatScaling.activationThreat()) {
            ZombieDifficultyAbilities.alertNearby(self, player);
            ZombieDifficultyAbilities.tryTriggerHorde(self, player);
            ZombieDifficultyAbilities.markAggro(self, player);
        }
        world.playSound(null, self.getX(), self.getY(), self.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE,
                1.0F, 0.9F + self.getRandom().nextFloat() * 0.2F);
        callback.setReturnValue(false);
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
                && ThreatScaling.playerThreat(player) >= ThreatScaling.activationThreat()) {
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

    @Unique
    private void threatdirector$updateShieldPose(ZombieEntity zombie, ServerWorld world) {
        if (!zombie.getOffHandStack().isOf(Items.SHIELD)) {
            return;
        }
        if (world.getTime() < threatdirector$shieldDisabledUntilTick) {
            if (zombie.isUsingItem() && zombie.getActiveHand() == Hand.OFF_HAND) {
                zombie.stopUsingItem();
            }
            return;
        }
        if (zombie.getTarget() != null && zombie.getTarget().isAlive() && !zombie.isUsingItem()) {
            zombie.setCurrentHand(Hand.OFF_HAND);
        }
    }
}
