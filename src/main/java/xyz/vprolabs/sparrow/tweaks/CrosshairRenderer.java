package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.util.ColorUtil;

public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    private static String cachedColorStr = "";
    private static int cachedArgb = 0xFFFFFFFF;
    private static String cachedType = "";

    public static void render(DrawContext context, int width, int height) {
        String type = Modules.crosshairMode.stringValue();
        if (type.equals("off")) return;

        if (!type.equals(cachedType)) cachedType = type;

        String colorStr = Modules.crosshairColor.stringValue();
        if (!colorStr.equals(cachedColorStr)) {
            int color = ColorUtil.parseRgb24(colorStr, 0xFFFFFF);
            cachedArgb = 0xFF000000 | color;
            cachedColorStr = colorStr;
        }

        int cx = width / 2;
        int cy = height / 2;

        switch (cachedType) {
            case "heart" -> renderHeart(context, cx, cy, cachedArgb);
            case "tiny" -> renderTiny(context, cx, cy, cachedArgb);
            case "dot" -> renderDot(context, cx, cy, cachedArgb);
            case "x" -> renderX(context, cx, cy, cachedArgb);
            case "clover" -> renderClover(context, cx, cy, cachedArgb);
            case "plus", "default" -> renderPlus(context, cx, cy, cachedArgb);
        }
    }

    private static void renderHeart(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - 2, cy - 2, cx, cy - 1, color);
        ctx.fill(cx + 1, cy - 2, cx + 3, cy - 1, color);
        ctx.fill(cx - 2, cy - 1, cx + 3, cy, color);
        ctx.fill(cx - 2, cy, cx + 3, cy + 1, color);
        ctx.fill(cx - 2, cy + 1, cx + 3, cy + 2, color);
        ctx.fill(cx - 1, cy + 2, cx + 2, cy + 3, color);
        ctx.fill(cx, cy + 3, cx + 1, cy + 4, color);
    }

    private static void renderTiny(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx, cy - 8, cx + 1, cy - 3, color);
        ctx.fill(cx, cy + 3, cx + 1, cy + 8, color);
        ctx.fill(cx - 8, cy, cx - 3, cy + 1, color);
        ctx.fill(cx + 3, cy, cx + 8, cy + 1, color);
    }

    private static void renderDot(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx, cy - 1, cx + 1, cy, color);
        ctx.fill(cx - 1, cy, cx, cy + 1, color);
        ctx.fill(cx + 1, cy, cx + 2, cy + 1, color);
        ctx.fill(cx, cy + 1, cx + 1, cy + 2, color);
    }

    private static void renderX(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - 4, cy - 4, cx - 2, cy - 2, color);
        ctx.fill(cx - 2, cy - 2, cx, cy, color);
        ctx.fill(cx, cy, cx + 2, cy + 2, color);
        ctx.fill(cx + 2, cy + 2, cx + 4, cy + 4, color);
        ctx.fill(cx + 2, cy - 4, cx + 4, cy - 2, color);
        ctx.fill(cx, cy - 2, cx + 2, cy, color);
        ctx.fill(cx - 2, cy, cx, cy + 2, color);
        ctx.fill(cx - 4, cy + 2, cx - 2, cy + 4, color);
    }

    private static void renderClover(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - 1, cy - 6, cx + 2, cy - 3, color);
        ctx.fill(cx - 1, cy + 3, cx + 2, cy + 6, color);
        ctx.fill(cx - 6, cy - 1, cx - 3, cy + 2, color);
        ctx.fill(cx + 3, cy - 1, cx + 6, cy + 2, color);
    }

    private static void renderPlus(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx, cy - 4, cx + 1, cy + 5, color);
        ctx.fill(cx - 5, cy, cx + 6, cy + 1, color);
    }

}
