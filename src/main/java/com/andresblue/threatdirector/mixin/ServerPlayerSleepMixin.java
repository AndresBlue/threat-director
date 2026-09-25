package com.andresblue.threatdirector.mixin;

import com.andresblue.threatdirector.ThreatDirectorMod;
import com.andresblue.threatdirector.bloodmoon.BloodMoonService;
import com.andresblue.threatdirector.localization.LocalizedText;
import com.andresblue.threatdirector.server.ModdedClientAccess;
import com.mojang.datafixers.util.Either;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the vanilla all-players-sleeping path from skipping a Blood Moon. */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerSleepMixin {
    @Inject(method = "trySleep", at = @At("HEAD"), cancellable = true)
    private void threatdirector$preventBloodMoonSleep(
            BlockPos position,
            CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> callback
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!(player.getWorld() instanceof ServerWorld world)
                || !ThreatDirectorMod.config().modded().bloodMoon().preventSleeping()
                || !BloodMoonService.isActive(world)) {
            return;
        }
        if (ModdedClientAccess.hasMod(player)) {
            player.sendMessage(
                    LocalizedText.of("message.threatdirector.blood_moon.sleep_blocked")
                            .formatted(Formatting.DARK_RED, Formatting.ITALIC),
                    true
            );
        }
        callback.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.OTHER_PROBLEM));
    }
}
