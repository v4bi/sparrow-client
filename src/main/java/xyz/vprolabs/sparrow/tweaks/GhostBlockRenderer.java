package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.GhostBlockState;
import xyz.vprolabs.sparrow.state.HudMoveState;
import xyz.vprolabs.sparrow.state.HudPositions;

public final class GhostBlockRenderer {
    private static final int TEXT_COLOR = 0xFFFF4444;
    private static final int BG_COLOR = 0x88000000;
    private static int labelW = -1;
    private static final String PREFIX = "\u00a7cGhost: ";

    // PREFIX + count + " blocks" concatenated a fresh String every frame.
    // Cache by count, same pattern as labelW (which only caches the WIDTH).
    // -1 sentinel is safe: render() early-returns when count == 0, so the
    // first reachable count is >= 1 and always rebuilds. Rejected: caching
    // only the width while rebuilding the text — the String alloc is the
    // actual garbage, the width lookup is free.
    private static int cachedCount = -1;
    private static String cachedText = null;

    private GhostBlockRenderer() {}

    public static void render(DrawContext ctx, TextRenderer font) {
        // Opt-in (2026-08-02): render only when the module is enabled.
        if (!Modules.ghostBlock.isEnabled()) return;
        int count = GhostBlockState.ghostBlocks.size();
        if (count == 0) return;

        if (count != cachedCount) {
            cachedCount = count;
            cachedText = PREFIX + count + " blocks";
        }
        String text = cachedText;
        int tw = font.getWidth(text);
        if (labelW < 0) labelW = tw;

        int[] off = HudPositions.getOffset("ghost-block");
        int x = 5 + off[0];
        int y = 5 + off[1];
        if (HudMoveState.active) {
            HudMoveState.elementBounds.put("ghost-block", new int[]{x, y, tw + 2, font.fontHeight + 2});
            HudHelper.drawBorder(ctx, x - 1, y - 1, tw + 2, font.fontHeight + 2, 0xFFFFFFFF);
        }
        HudHelper.drawBoxedText(ctx, font, text, x, y, BG_COLOR, TEXT_COLOR);
    }
}
