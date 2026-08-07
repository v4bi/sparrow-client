/*
 * Sparrow Mod - Fire timer HUD renderer. All positioning + draw calls
 * live here so the mixin is just a thin orchestrator. Pattern mirrors
 * StorageTooltipRenderer for consistency.
 * Made By: vProLabs (https://www.vprolabs.xyz)
 * Discord: discord.gg/SNzUYWbc5Q
 * Donations: ko-fi.com/v4bi
 */

package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudMoveState;
import xyz.vprolabs.sparrow.state.HudPositions;
import xyz.vprolabs.sparrow.state.HudState;

public final class FireTimerRenderer {
    private static final int TEXT_COLOR = 0xFFFFDDDD;
    private static final int BG_COLOR = 0x88000000;

    private FireTimerRenderer() {
    }

    public static void render(DrawContext context, TextRenderer font, int ticks) {
        int displayBucket = ticks / 2;
        if (displayBucket != HudState.lastFireTicks / 2 || HudState.lastFireText == null) {
            HudState.lastFireTicks = ticks;
            HudState.lastFireText = String.format("\u00a7c\u00a7l!FIRE!\u00a7r %.1fs", ticks / 20.0f);
            HudState.lastFireWidth = font.getWidth(HudState.lastFireText);
        }

        String text = HudState.lastFireText;
        int textWidth = HudState.lastFireWidth;
        int textHeight = font.fontHeight;

        int x;
        int y;
        String pos = Modules.fireTimerPos.stringValue();

        if ("TOP_LEFT".equals(pos)) {
            x = 5;
            y = 5;
        } else if ("TOP_RIGHT".equals(pos)) {
            x = context.getScaledWindowWidth() - textWidth - 5;
            y = 5;
        } else {
            x = (context.getScaledWindowWidth() - textWidth) / 2;
            y = context.getScaledWindowHeight() - 55;
            int maxY = context.getScaledWindowHeight() - textHeight - 25;
            if (y > maxY) {
                y = maxY;
            }
        }
        if (y < 5) {
            y = 5;
        }

        int[] off = HudPositions.getOffset("fire-timer");
        x += off[0];
        y += off[1];

        if (HudMoveState.active) {
            HudMoveState.elementBounds.put("fire-timer", new int[]{x, y, textWidth + 2, textHeight + 2});
            HudHelper.drawBorder(context, x - 1, y - 1, textWidth + 2, textHeight + 2, 0xFFFFFFFF);
        }

        HudHelper.drawBoxedText(context, font, text, x, y, BG_COLOR, TEXT_COLOR);
    }
}
