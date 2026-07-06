package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.gui.DrawContext;
import xyz.vprolabs.sparrow.config.ConfigRegister;

public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    public static void render(DrawContext context, int width, int height) {
        String type = ConfigRegister.crosshair.get();
        if (type.equals("off")) return;

        String colorStr = ConfigRegister.crosshairColor.get();
        int color = parseColorHex(colorStr);
        int argb = 0xFF000000 | color;

        int cx = width / 2;
        int cy = height / 2;

        context.getMatrices().pushMatrix();
        try {
            switch (type.toLowerCase()) {
                case "heart" -> renderHeart(context, cx, cy, argb);
                case "tiny" -> renderTiny(context, cx, cy, argb);
                case "dot" -> renderDot(context, cx, cy, argb);
                case "x" -> renderX(context, cx, cy, argb);
                case "clover" -> renderClover(context, cx, cy, argb);
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private static int parseColorHex(String s) {
        if (s == null || s.isEmpty()) return 0xFFFFFF;
        s = s.trim().replace("#", "").replace("0x", "");
        if (s.contains(",")) {
            String[] p = s.split(",");
            if (p.length == 3) {
                return (Integer.parseInt(p[0].trim()) << 16)
                     | (Integer.parseInt(p[1].trim()) << 8)
                     | Integer.parseInt(p[2].trim());
            }
        }
        if (s.contains(".")) {
            String[] p = s.split("\\.");
            if (p.length == 3) {
                return (Integer.parseInt(p[0].trim()) << 16)
                     | (Integer.parseInt(p[1].trim()) << 8)
                     | Integer.parseInt(p[2].trim());
            }
        }
        if (s.length() == 9 && s.matches("\\d{9}")) {
            return (Integer.parseInt(s.substring(0, 3)) << 16)
                 | (Integer.parseInt(s.substring(3, 6)) << 8)
                 | Integer.parseInt(s.substring(6, 9));
        }
        if (s.matches("[0-9a-fA-F]{6}")) {
            return Integer.parseInt(s, 16);
        }
        return 0xFFFFFF;
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
        int gap = 3;
        int arm = 5;
        ctx.fill(cx, cy - gap - arm, cx + 1, cy - gap, color);
        ctx.fill(cx, cy + gap, cx + 1, cy + gap + arm, color);
        ctx.fill(cx - gap - arm, cy, cx - gap, cy + 1, color);
        ctx.fill(cx + gap, cy, cx + gap + arm, cy + 1, color);
    }

    private static void renderDot(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
    }

    private static void renderX(DrawContext ctx, int cx, int cy, int color) {
        for (int i = -4; i <= 4; i++) {
            ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            ctx.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
        }
    }

    private static void renderClover(DrawContext ctx, int cx, int cy, int color) {
        int g = 5;
        ctx.fill(cx - 1, cy - g - 1, cx + 2, cy - g + 2, color);
        ctx.fill(cx - 1, cy + g - 1, cx + 2, cy + g + 2, color);
        ctx.fill(cx - g - 1, cy - 1, cx - g + 2, cy + 2, color);
        ctx.fill(cx + g - 1, cy - 1, cx + g + 2, cy + 2, color);
    }
}
