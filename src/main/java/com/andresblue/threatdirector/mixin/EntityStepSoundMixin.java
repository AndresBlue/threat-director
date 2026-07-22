package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityStepSoundMixin {
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void threatdirector$silenceHardCreeperSteps(
            BlockPos position,
            BlockState state,
            CallbackInfo callbackInfo
    ) {
        Entity entity = (Entity) (Object) this;
        World world = entity.getWorld();
        if (!world.isClient()
                && ThreatDirectorMod.isEnabled()
                && world.getDifficulty() == Difficulty.HARD
                && entity instanceof CreeperEntity) {
            callbackInfo.cancel();
        }
    }
}
