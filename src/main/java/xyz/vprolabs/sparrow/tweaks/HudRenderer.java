package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.GhostBlockState;
import xyz.vprolabs.sparrow.state.HudMoveState;
import xyz.vprolabs.sparrow.state.HudPositions;
import xyz.vprolabs.sparrow.state.HudState;

public final class HudRenderer {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int WARN_COLOR = 0xFFFF4444;
    private static final int BG_COLOR = 0x88000000;
    private static final int PADDING = 4;

    private static int desyncWarnW = -1;
    private static int pingW = -1;

    private HudRenderer() {}

    private static int[] addOffset(String key, int x, int y) {
        int[] off = HudPositions.getOffset(key);
        return new int[]{x + off[0], y + off[1]};
    }

    public static void render(DrawContext ctx, TextRenderer font) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (client.options.hudHidden) return;

        GhostBlockState.tick();

        int scaledW = ctx.getScaledWindowWidth();
        int scaledH = ctx.getScaledWindowHeight();

        // Coords
        if (Modules.coords.isEnabled()) {
            String text = String.format("XYZ: %.0f / %.0f / %.0f",
                client.player.getX(), client.player.getY(), client.player.getZ());
            int[] pos = addOffset("coords", PADDING, scaledH - 55);
            int x = pos[0], y = pos[1];
            int tw = font.getWidth(text);
            if (HudMoveState.active) {
                HudMoveState.elementBounds.put("coords", new int[]{x, y, tw + 2, font.fontHeight + 2});
                HudHelper.drawBorder(ctx, x - 1, y - 1, tw + 2, font.fontHeight + 2, 0xFFFFFFFF);
            }
            HudHelper.drawBoxedText(ctx, font, text, x, y, BG_COLOR, TEXT_COLOR);
        }

        // Ping (cached width)
        if (Modules.ping.isEnabled() && HudState.currentPing > 0) {
            String text = "Ping: " + HudState.currentPing + "ms";
            int tw = font.getWidth(text);
            if (pingW < 0) pingW = tw;
            int[] pos = addOffset("ping", scaledW - tw - PADDING - 1, PADDING);
            int x = pos[0], y = pos[1];
            if (HudMoveState.active) {
                HudMoveState.elementBounds.put("ping", new int[]{x, y, tw + 2, font.fontHeight + 2});
                HudHelper.drawBorder(ctx, x - 1, y - 1, tw + 2, font.fontHeight + 2, 0xFFFFFFFF);
            }
            HudHelper.drawBoxedText(ctx, font, text, x, y, BG_COLOR, TEXT_COLOR);
        }

        // Desync warning (moved 50px higher, cached width)
        if (Modules.desync.isEnabled()) {
            long elapsed = System.currentTimeMillis() - HudState.lastDesyncTime;
            if (elapsed < HudState.DESYNC_HIDE_DURATION) {
                String text = "\u00a7c\u26a0 Desync detected!";
                if (desyncWarnW < 0) desyncWarnW = font.getWidth(text);
                int[] pos = addOffset("desync", (scaledW - desyncWarnW) / 2, scaledH / 2 - 70);
                int x = pos[0], y = pos[1];
                if (HudMoveState.active) {
                    HudMoveState.elementBounds.put("desync", new int[]{x, y, desyncWarnW + 2, font.fontHeight + 2});
                    HudHelper.drawBorder(ctx, x - 1, y - 1, desyncWarnW + 2, font.fontHeight + 2, 0xFFFFFFFF);
                }
                HudHelper.drawBoxedText(ctx, font, text, x, y, BG_COLOR, WARN_COLOR);
            }
        }

        // These have their own matrix push/pop internally
        GhostBlockRenderer.render(ctx, font);
        KnockbackRenderer.render(ctx, font);
    }
}
