package com.andresblue.threatdirector.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.JumpingMount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reserves a real lane above the hotbar for the threat overlay. */
@Mixin(InGameHud.class)
public abstract class InGameHudLayoutMixin {
    @Unique
    private static final int THREATDIRECTOR_HUD_SHIFT = 8;

    @Inject(method = "renderStatusBars", at = @At("HEAD"))
    private void threatdirector$raiseStatusBars(DrawContext context, CallbackInfo callbackInfo) {
        raise(context);
    }

    @Inject(method = "renderStatusBars", at = @At("RETURN"))
    private void threatdirector$restoreStatusBars(DrawContext context, CallbackInfo callbackInfo) {
        restore(context);
    }

    @Inject(method = "renderMountHealth", at = @At("HEAD"))
    private void threatdirector$raiseMountHealth(DrawContext context, CallbackInfo callbackInfo) {
        raise(context);
    }

    @Inject(method = "renderMountHealth", at = @At("RETURN"))
    private void threatdirector$restoreMountHealth(DrawContext context, CallbackInfo callbackInfo) {
        restore(context);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void threatdirector$raiseExperienceBar(DrawContext context, int x, CallbackInfo callbackInfo) {
        raise(context);
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void threatdirector$restoreExperienceBar(DrawContext context, int x, CallbackInfo callbackInfo) {
        restore(context);
    }

    @Inject(method = "renderMountJumpBar", at = @At("HEAD"))
    private void threatdirector$raiseJumpBar(
            JumpingMount mount, DrawContext context, int x, CallbackInfo callbackInfo) {
        raise(context);
    }

    @Inject(method = "renderMountJumpBar", at = @At("RETURN"))
    private void threatdirector$restoreJumpBar(
            JumpingMount mount, DrawContext context, int x, CallbackInfo callbackInfo) {
        restore(context);
    }

    @Unique
    private static void raise(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, -THREATDIRECTOR_HUD_SHIFT, 0.0F);
    }

    @Unique
    private static void restore(DrawContext context) {
        context.getMatrices().pop();
    }
}
