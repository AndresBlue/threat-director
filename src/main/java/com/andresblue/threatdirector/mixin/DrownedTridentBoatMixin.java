package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.drowned.DrownedNightAbilities;
import com.andresblue.threatdirector.telemetry.GameplayDebugEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A night-drowned trident destroys the struck boat, even when it hits its passenger. */
@Mixin(TridentEntity.class)
public abstract class DrownedTridentBoatMixin {
    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void threatdirector$breakBoatOnDrownedTrident(
            EntityHitResult hit,
            CallbackInfo callback
    ) {
        TridentEntity trident = (TridentEntity) (Object) this;
        if (!(trident.getWorld() instanceof ServerWorld world)
                || !(trident.getOwner() instanceof DrownedEntity drowned)
                || !DrownedNightAbilities.profile(world).breaksBoats()) {
            return;
        }
        Entity hitEntity = hit.getEntity();
        BoatEntity boat = hitEntity instanceof BoatEntity directBoat
                ? directBoat
                : hitEntity.getVehicle() instanceof BoatEntity riddenBoat ? riddenBoat : null;
        if (boat != null && boat.isAlive()) {
            boat.damage(world.getDamageSources().trident(trident, drowned), 100.0F);
            GameplayDebugEvents.record("drowned.boat_broken", java.util.Map.of(
                    "drowned", drowned.getUuid().toString(),
                    "boat", boat.getUuid().toString(),
                    "directHit", hitEntity instanceof BoatEntity));
        }
    }
}
