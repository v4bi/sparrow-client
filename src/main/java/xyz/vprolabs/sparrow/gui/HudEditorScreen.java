package xyz.vprolabs.sparrow.gui;

import xyz.vprolabs.sparrow.module.ModuleManager;
import xyz.vprolabs.sparrow.state.HudPositions;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD Editor (2026-08-01): opened from the grid icon at the right end of the
 * ClickGuiScreen category bar. Shows every HudPositions element as a draggable
 * box; dragging writes back through HudPositions.setOffset() and the offsets
 * persist via ModuleManager.saveNow() (HudPositions.putToMap runs inside it).
 *
 * Base positions are approximations of where each element actually renders —
 * the drag offsets are added on top of them at draw time by each HUD feature.
 */
public class HudEditorScreen extends Screen {

    private static final int BOX_W = 110;
    private static final int BOX_H = 20;

    private static final String[] KEYS = {
        "coords", "ping", "desync", "fire-timer", "ghost-block",
        "knockback", "shield"
    };

    private final Map<String, int[]> bases = new LinkedHashMap<>();
    private String dragging;
    private int grabDx, grabDy;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override public boolean shouldPause() { return true; }

    private int[] base(String key) {
        int[] b = bases.get(key);
        if (b != null) return b;
        // Annotations MUST match where each feature actually renders (bugfix
        // 2026-08-02: the old bases were guesses, so a drag session saved
        // offsets anchored to nonexistent positions — text landed tens of px
        // away from where the editor box showed). These mirror the renderers:
        //   HudRenderer.java:42  coords  (PADDING=4, scaledH-55)
        //   HudRenderer.java:57  ping    (scaledW - tw - 5, 8)
        //   HudRenderer.java:72  desync  (center, scaledH/2 - 70)
        //   FireTimerRenderer    fire    (center, scaledH - 52) bottom-center
        //   GhostBlockRenderer   ghost   (5, 5)
        //   KnockbackRenderer    kb      (5, 30)
        //   ShieldChargeRenderer shield  ((w-8)/2, h-46)
        // Widths: boxes are BOX_W wide even when the text is shorter; the box
        // is anchored to the text's actual top-left so the offset math matches.
        int[] def;
        switch (key) {
            case "coords":        def = new int[]{4, height - 55}; break;
            case "ping":          def = new int[]{width - 90, 8}; break;
            case "desync":        def = new int[]{width / 2 - 60, height / 2 - 70}; break;
            case "fire-timer":    def = new int[]{width / 2 - 60, height - 52}; break;
            case "ghost-block":   def = new int[]{5, 5}; break;
            case "knockback":     def = new int[]{5, 30}; break;
            case "shield":        def = new int[]{width / 2 - 4, height - 46}; break;
            case "hotbar":        def = new int[]{width / 2 - 91, height - 22}; break;
            case "status-bars":   def = new int[]{width / 2 - 50, height - 44}; break;
            default:              def = new int[]{width / 2 - BOX_W / 2, height / 2};
        }
        bases.put(key, def);
        return def;
    }

    private int boxX(String key) {
        int[] b = base(key);
        int[] off = HudPositions.getOffset(key);
        return Math.max(0, Math.min(width - BOX_W, b[0] + off[0]));
    }

    private int boxY(String key) {
        int[] b = base(key);
        int[] off = HudPositions.getOffset(key);
        return Math.max(0, Math.min(height - BOX_H, b[1] + off[1]));
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() == 0) {
            for (String key : KEYS) {
                int x = boxX(key), y = boxY(key);
                if (click.x() >= x && click.x() <= x + BOX_W
                        && click.y() >= y && click.y() <= y + BOX_H) {
                    dragging = key;
                    grabDx = (int) click.x() - x;
                    grabDy = (int) click.y() - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            int nx = Math.max(0, Math.min(width - BOX_W, (int) click.x() - grabDx));
            int ny = Math.max(0, Math.min(height - BOX_H, (int) click.y() - grabDy));
            int[] b = base(dragging);
            HudPositions.setOffset(dragging, nx - b[0], ny - b[1]);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            // Persist offsets immediately — HudPositions.putToMap runs inside
            // ModuleManager.saveNow(), so a crash never loses the layout.
            ModuleManager.saveNow();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        ModuleManager.saveNow();
        super.close();
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Editor backdrop: 60% dim — translucent like the click GUI so the game
        // stays visible while repositioning HUD elements.
        ctx.fill(0, 0, width, height, 0x99101016);
        ctx.drawText(textRenderer, Text.literal("\u00a77HUD Editor \u00a78— drag elements, ESC saves & exits"),
            8, 8, 0xFFC5C6C7, false);

        for (String key : KEYS) {
            int x = boxX(key), y = boxY(key);
            boolean over = mouseX >= x && mouseX <= x + BOX_W && mouseY >= y && mouseY <= y + BOX_H;
            int bg = key.equals(dragging) ? 0xFF2A2A3A : (over ? 0xFF26262F : 0xCC1C1C24);
            ctx.fill(x, y, x + BOX_W, y + BOX_H, bg);
            ctx.fill(x, y, x + 2, y + BOX_H, 0xFF58A6FF);
            ctx.fill(x, y + BOX_H - 1, x + BOX_W, y + BOX_H, 0xFF3A3A4A);
            int tw = textRenderer.getWidth(key);
            ctx.drawText(textRenderer, Text.literal(key),
                x + (BOX_W - tw) / 2, y + (BOX_H - 8) / 2, 0xFFC5C6C7, false);
        }
    }
}
