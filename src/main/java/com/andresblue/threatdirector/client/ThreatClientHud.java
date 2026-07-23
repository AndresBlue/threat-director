package com.andresblue.threatdirector.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Draws a compact, text-free threat meter in the vanilla HUD gap. */
public final class ThreatClientHud {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 2;
    private static final int BACKGROUND = 0xFF1A1A1A;
    private static final int NOTCH = 0xFF303030;
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
        // Vanilla XP ends at height - 24 and the hotbar begins at height - 22.
        // The two-pixel lane between them keeps the overlay out of both widgets.
        int y = screenHeight - 24;

        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BACKGROUND);
        int filledWidth = Math.round(progress * BAR_WIDTH);
        if (filledWidth > 0) {
            context.fill(x, y, x + filledWidth, y + BAR_HEIGHT, color(colorId));
        }
        for (int notch = 1; notch < 10; notch++) {
            int notchX = x + notch * BAR_WIDTH / 10;
            context.fill(notchX, y, notchX + 1, y + BAR_HEIGHT, NOTCH);
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
}
