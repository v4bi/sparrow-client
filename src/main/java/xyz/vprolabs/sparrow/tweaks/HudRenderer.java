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

    // Render path is single-threaded (render thread); every call site copies
    // pos[0]/pos[1] into locals IMMEDIATELY after addOffset returns, and none
    // retains the returned array across calls (verified 2026-08). So a shared
    // scratch array is safe: fill in place, zero allocations per frame.
    // Rejected: per-call "new int[]" — that is the 3x/frame allocation this
    // change removes. Rejected: returning offsets-relative coords — would
    // ripple into HudEditorScreen/HudMoveState callers.
    private static final int[] SCRATCH = new int[2];

    private HudRenderer() {}

    private static int[] addOffset(String key, int x, int y) {
        int[] off = HudPositions.getOffset(key);
        SCRATCH[0] = x + off[0];
        SCRATCH[1] = y + off[1];
        return SCRATCH;
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
            // Cached in HudState: rebuilt only when the int-truncated position
            // changes (~1x/sec walking), identical text otherwise. The cache
            // keeps %.0f HALF_UP formatting on rebuild, so output is unchanged.
            String text = HudState.coordsString(
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
            // Cached in HudState by ping value (rebuilds only when the ping
            // reading actually changes, not every frame).
            String text = HudState.pingString(HudState.currentPing);
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
