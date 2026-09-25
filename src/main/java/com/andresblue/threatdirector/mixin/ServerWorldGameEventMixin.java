package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.awareness.AwarenessModule;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the authoritative server GameEvent stream used by vanilla sculk sensors. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldGameEventMixin {
    @Inject(method = "emitGameEvent", at = @At("HEAD"))
    private void threatdirector$captureAwarenessSound(
            GameEvent event,
            Vec3d position,
            GameEvent.Emitter emitter,
            CallbackInfo callbackInfo
    ) {
        AwarenessModule.onGameEvent((ServerWorld) (Object) this, event, position, emitter);
    }
}
