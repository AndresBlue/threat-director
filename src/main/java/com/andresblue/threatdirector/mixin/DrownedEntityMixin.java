package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.drowned.DrownedNightAbilities;
import com.andresblue.threatdirector.drowned.DrownedNightPolicy;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DrownedEntity.class)
public abstract class DrownedEntityMixin {
    @Unique
    private int threatdirector$dashCooldown;
    @Unique
    private int threatdirector$targetScanCooldown;

    @Inject(method = "initialize", at = @At("RETURN"))
    private void threatdirector$equipNightTrident(
            ServerWorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            EntityData entityData,
            NbtCompound entityNbt,
            CallbackInfoReturnable<EntityData> callback
    ) {
        DrownedNightAbilities.equipNightTrident((DrownedEntity) (Object) this);
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void threatdirector$applyNightSwimmerBehavior(Vec3d movementInput, CallbackInfo callback) {
        DrownedEntity drowned = (DrownedEntity) (Object) this;
        if (!(drowned.getWorld() instanceof ServerWorld world)) {
            return;
        }
        DrownedNightPolicy.Profile profile = DrownedNightAbilities.profile(world);
        DrownedNightAbilities.updateSwimSpeed(drowned, profile);

        if (threatdirector$targetScanCooldown-- <= 0) {
            threatdirector$targetScanCooldown = 20;
            DrownedNightAbilities.acquireOceanTarget(drowned, profile);
        }
        if (threatdirector$dashCooldown > 0) {
            threatdirector$dashCooldown--;
        } else if (DrownedNightAbilities.dashTowardTarget(drowned, profile)) {
            threatdirector$dashCooldown = profile.dashCooldownTicks();
        }
    }
}
