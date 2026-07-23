package com.andresblue.threatdirector.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Draws a compact, text-free threat meter in the vanilla HUD gap. */
public final class ThreatClientHud {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final Identifier ICONS_TEXTURE =
            new Identifier("minecraft", "textures/gui/icons.png");
    private static volatile boolean visible;
    private static volatile float progress;
    private static volatile int colorId;

    private ThreatClientHud() {
    }

    public static void update(boolean nextVisible, float nextProgress, int nextColorId) {
        visible = nextVisible;
        progress = Math.max(0.0F, Math.min(1.0F, nextProgress));
        colorId = Math.max(0, Math.min(3, nextColorId));
    }

    public static void hide() {
        visible = false;
        progress = 0.0F;
        colorId = 0;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!visible
                || client.player == null
                || client.options.hudHidden
                || client.currentScreen != null) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int x = (screenWidth - BAR_WIDTH) / 2;
        // InGameHudLayoutMixin raises XP/status bars by eight pixels while the
        // hotbar remains at the bottom. This lane mirrors the vanilla XP bar.
        int y = screenHeight - 29;

        // Reuse the exact vanilla XP-bar background texture (icons.png, v=64).
        context.drawTexture(ICONS_TEXTURE, x, y, 0, 64, BAR_WIDTH, BAR_HEIGHT);
        int filledWidth = Math.round(progress * BAR_WIDTH);
        if (filledWidth > 2) {
            int fillX = x + 1;
            int fillEnd = x + filledWidth - 1;
            int fillColor = color(colorId);
            context.fill(fillX, y + 1, fillEnd, y + 4, fillColor);
            context.fill(fillX, y + 1, fillEnd, y + 2, lighten(fillColor));
            context.fill(fillX, y + 3, fillEnd, y + 4, darken(fillColor));
        }
    }

    private static int color(int id) {
        return switch (id) {
            case 1 -> 0xFFFFD83D;
            case 2 -> 0xFFE53935;
            case 3 -> 0xFFB84CFF;
            default -> 0xFF55C94A;
        };
    }

    private static int lighten(int argb) {
        return blend(argb, 0xFFFFFFFF, 0.35F);
    }

    private static int darken(int argb) {
        return blend(argb, 0xFF000000, 0.30F);
    }

    private static int blend(int first, int second, float amount) {
        int r = blendChannel(first >> 16, second >> 16, amount);
        int g = blendChannel(first >> 8, second >> 8, amount);
        int b = blendChannel(first, second, amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blendChannel(int first, int second, float amount) {
        return Math.round((first & 0xFF) * (1.0F - amount) + (second & 0xFF) * amount);
    }
}
