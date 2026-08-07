package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudState;

public final class HitConfirmRenderer {
    // 8-digit AARRGGBB (the 6-digit alpha trap: 0x00FF00 would be fully
    // transparent if used raw). render() ORs a fading alpha into the top
    // byte, overwriting these constants' alpha — but 8-digit forms keep
    // them safe if ever used without composition (B6).
    private static final int HIT_COLOR = 0xFF00FF00;    // green checkmark
    private static final int MARKER_COLOR = 0xFFFF4444; // red cross

    private HitConfirmRenderer() {}

    public static void render(DrawContext context, TextRenderer font) {
        if (!HudState.isShowing()) return;

        long elapsed = System.currentTimeMillis() - HudState.lastHitTime;
        int alpha = (int) (255 * (1 - elapsed / 300.0));
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;

        int scaledW = context.getScaledWindowWidth();
        int scaledH = context.getScaledWindowHeight();
        int cx = scaledW / 2;
        int cy = scaledH / 2;

        if (Modules.hitmarker.isEnabled()) {
            // Draw red "✕" at crosshair center
            int markerColor = (alpha << 24) | MARKER_COLOR;
            String text = "\u2715";
            int tw = font.getWidth(text);
            HudHelper.drawText(context, font, text, cx - tw / 2, cy - font.fontHeight / 2, markerColor);
        } else {
            // Draw green checkmark
            int color = (alpha << 24) | HIT_COLOR;
            String text = "\u2714";
            HudHelper.drawText(context, font, text, cx - font.getWidth(text) / 2, cy - font.fontHeight / 2, color);
        }
    }
}
