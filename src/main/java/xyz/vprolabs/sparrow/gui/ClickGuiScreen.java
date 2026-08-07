package xyz.vprolabs.sparrow.gui;

import xyz.vprolabs.sparrow.module.Module;
import xyz.vprolabs.sparrow.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Click GUI v3 (2026-08-01): the panel MAXIMIZES to the full screen (8px
 * margin), category tabs span the top edge, options of the active category
 * fill the rest. Stable — never dragged. v2 was a 200px centered box which
 * the user found unusable ("MAKE THE FUCKING BOX BIGGER, MAXIMIZE IT TO THE
 * FULLSCREEN"); v2's event-driven render was also dropped — MC clears the
 * framebuffer every frame, so a screen that skips renders flickers (the
 * panorama showed through). This screen paints EVERY frame; FPS saving is
 * delegated to FramePacerMixin, which locks the game to "Sparrow -> gui-fps"
 * (default 60) while this screen is open.
 *
 * v3.1 (2026-08-01 session 3):
 *  - Scissor-clipped rows: MC does NOT clip child widgets, so scrolled rows
 *    painted outside the panel and covered the category bar at the bottom of
 *    a long list (user report: "the GUI just OVERRODE the categories bar").
 *    Rows render inside an enableScissor region; tabs are redrawn on top.
 *  - Reset icon (textures/gui/reset.png) in the gutter right of every row;
 *    clicking restores the module's factory default (dimmed when at default).
 *  - Composite modules ("View Model", "Fire Timer") show as ONE row; clicking
 *    opens a modal popup with the children's controls (sliders/cycles/toggle).
 *  - HUD Editor button (grid glyph) at the right end of the category bar.
 */
public class ClickGuiScreen extends Screen {

    // Launcher palette (same family as SparrowConsoleScreen)
    // Panel is translucent (0x9C ≈ 61% alpha) — user rejected the near-opaque
    // slab (0xF2) as a "huge black bar"; game must stay visible behind the GUI.
    // Rows keep opaque backgrounds so text stays readable on the glass.
    private static final int PANEL_BG    = 0x9C101016;
    private static final int TAB_BG      = 0xA8202028;
    private static final int TAB_BG_HOV  = 0xFF26262F;
    private static final int TAB_BG_ACT  = 0xFF2A2A3A;
    private static final int BORDER      = 0xFF3A3A4A;
    private static final int FG          = 0xFFC5C6C7;
    private static final int DIM         = 0xFF8B949E;
    private static final int ACCENT      = 0xFF58A6FF;
    private static final int ROW_BG      = 0xFF1C1C24;
    private static final int ROW_BG_HOV  = 0xFF2A2A35;
    private static final int ON_BG       = 0xFF1F5E2E;
    private static final int OFF_BG      = 0xFF3A3A44;
    private static final int TRACK_BG    = 0xFF15151B;
    private static final int THUMB_BG    = 0xFF3A3A4A;

    private static final int MARGIN    = 8;
    private static final int TAB_H     = 24;
    private static final int ROW_H     = 22;
    private static final int ROW_PITCH = ROW_H + 5; // 5px breathing room between rows (2026-08-02 request)
    private static final int SCROLL_W  = 4;
    private static final int ICON_W    = 16;   // reset icon size
    private static final int ICON_GAP  = 5;    // gap between row edge and icon

    // Reset glyph is drawn with rects (paintResetIcon) — no texture asset.
    // The PNG refused to load at runtime and added a missing-resource warning.

    private final List<String> categories = new ArrayList<>();
    private final List<TabButton> tabs = new ArrayList<>();
    private final List<ClickableWidget> rows = new ArrayList<>();
    private HudButton hudButton;
    private String activeCategory;
    private int scrollOffset;
    private int panelX, panelY, panelW, panelH, contentTop, contentH;
    private boolean scrollDragging;
    private int dragStartY, dragStartOffset;

    // Modal popup for composite modules (view-model, fire-timer)
    private Module popupModule;
    private final List<ClickableWidget> popupRows = new ArrayList<>();
    private int popupScroll;
    private PopupSlider popupDragSlider;
    private int popupX, popupY, popupW, popupH, popupContentTop, popupContentH;
    // Widget that currently owns keyboard input inside the popup (the hex
    // color field). Popup rows are painted manually, NOT registered children,
    // so Screen's own focus routing never reaches them (POPUP-1).
    private ClickableWidget popupFocused;

    public ClickGuiScreen() {
        super(Text.literal("Sparrow GUI"));
    }

    @Override public boolean shouldPause() { return true; }

    @Override
    protected void init() {
        // Categories come from the registry in definition order (Modules.java
        // defines "Sparrow" first, so it is the first tab) — no hardcoded list.
        categories.clear();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Module m : ModuleManager.all().values()) seen.add(m.category());
        categories.addAll(seen);
        if (categories.isEmpty()) return;
        if (activeCategory == null || !categories.contains(activeCategory))
            activeCategory = categories.get(0);

        layout();
        buildTabs();
        rebuildRows();
    }

    // Panel = fullscreen minus a margin, stable in place (never dragged).
    private void layout() {
        panelX = MARGIN;
        panelY = MARGIN;
        panelW = Math.max(240, width - MARGIN * 2);
        panelH = Math.max(160, height - MARGIN * 2);
        contentTop = panelY + TAB_H;
        contentH = panelH - TAB_H - MARGIN;
    }

    private List<Module> modules(String category) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all().values()) {
            // Children of a composite are hidden — they surface inside the
            // composite row's popup instead (one row per feature).
            if (m.hasParent()) continue;
            if (m.category().equals(category)) out.add(m);
        }
        return out;
    }

    private void buildTabs() {
        for (ClickableWidget w : tabs) remove(w);
        tabs.clear();
        // 32px reserved on the right end for the HUD Editor button.
        int n = Math.max(1, categories.size());
        int tabW = (panelW - 32) / n;
        for (int i = 0; i < categories.size(); i++) {
            TabButton b = new TabButton(panelX + i * tabW, panelY, tabW, categories.get(i));
            tabs.add(b);
            addDrawableChild(b);
        }
        // BUGFIX (2026-08-02): the HudButton was added as a child but its
        // return value never assigned to the `hudButton` field, so the render
        // re-draw after scissor-blit (`hudButton != null` line) never happened
        // and the scissored button never appeared — inert but still clickable.
        // Assign THEN add so the field tracks the actual child in the list.
        hudButton = addDrawableChild(new HudButton(panelX + panelW - 28, panelY, 28, TAB_H));
    }

    private void switchCategory(String category) {
        if (category.equals(activeCategory)) return;
        activeCategory = category;
        scrollOffset = 0;
        rebuildRows();
    }

    private void rebuildRows() {
        for (ClickableWidget w : rows) remove(w);
        rows.clear();
        for (Module m : modules(activeCategory)) rows.add(createRow(m));
        for (ClickableWidget w : rows) addDrawableChild(w);
        positionRows();
    }

    private void positionRows() {
        // Row width leaves room for the reset icon gutter + scrollbar.
        int rowW = panelW - 12 - SCROLL_W - 8 - (ICON_GAP + ICON_W + 4);
        for (int i = 0; i < rows.size(); i++) {
            ClickableWidget w = rows.get(i);
            w.setX(panelX + 6);
            w.setY(contentTop + i * ROW_PITCH - scrollOffset);
            w.setWidth(rowW);
            if (w instanceof TextFieldWidget field) {
                // Label is painted by paintChrome(); field takes the right part.
                field.setX(panelX + 200);
                field.setWidth(rowW - 194);
            }
        }
    }

    private int maxScroll() {
        return Math.max(0, modules(activeCategory).size() * ROW_PITCH - contentH);
    }

    // ── Reset icon helpers ─────────────────────────────────────────────

    private boolean hitResetIcon(ClickableWidget w, double x, double y) {
        int ix = w.getRight() + ICON_GAP;
        int iy = w.getY() + (ROW_H - ICON_W) / 2;
        return x >= ix && x <= ix + ICON_W && y >= iy && y <= iy + ICON_W;
    }

    private void paintResetIcon(DrawContext ctx, ClickableWidget w, int mouseX, int mouseY) {
        int ix = w.getRight() + ICON_GAP;
        int iy = w.getY() + (ROW_H - ICON_W) / 2;
        boolean hover = mouseX >= ix && mouseX <= ix + ICON_W && mouseY >= iy && mouseY <= iy + ICON_W;
        Module m = moduleOf(w);
        if (hover) ctx.fill(ix - 1, iy - 1, ix + ICON_W + 1, iy + ICON_W + 1, 0xAA2A2A35);
        // Reset glyph drawn with rects, NOT a texture: the PNG refused to load at
        // runtime ("Missing resource") and a drawTexturedQuad with width/height
        // passed as x2/y2 painted a giant inverted black quad over the panel.
        // Rect drawing has zero resource dependency and cannot fail.
        int c = (m == null || m.isAtDefault()) ? 0xFF3D444D : 0xFF58A6FF;
        ctx.fill(ix + 3, iy + 3, ix + 13, iy + 5, c);   // ring top
        ctx.fill(ix + 3, iy + 11, ix + 13, iy + 13, c); // ring bottom
        ctx.fill(ix + 3, iy + 3, ix + 5, iy + 13, c);   // ring left
        ctx.fill(ix + 11, iy + 3, ix + 13, iy + 13, c); // ring right
        ctx.fill(ix + 8, iy + 1, ix + 12, iy + 3, c);   // arrow base
        ctx.fill(ix + 11, iy + 0, ix + 13, iy + 2, c);  // arrow tip
        if (hover) ctx.fill(ix, iy, ix + 1, iy + ICON_W, c);
    }

    private Module moduleOf(ClickableWidget w) {
        if (w instanceof ToggleRow r) return r.module;
        if (w instanceof ModuleSlider s) return s.module;
        if (w instanceof CycleRow c) return c.module;
        if (w instanceof HexField f) return f.module;
        if (w instanceof CompositeRow c) return c.module;
        if (w instanceof PopupSlider s) return s.module;
        return null;
    }

    // Refresh a row's visual state after an external value change (reset).
    private void refreshRow(ClickableWidget w) {
        if (w instanceof ModuleSlider s) s.syncFromModule();
        else if (w instanceof PopupSlider s) s.syncFromModule();
        else if (w instanceof CycleRow c) c.refreshIndex();
        else if (w instanceof HexField f) f.setText(f.module.stringValue());
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (popupModule != null) {
            if (click.button() == 0) {
                // Close ("X") button in the popup header — mouse dismissal
                // in addition to ESC (2026-08-02).
                int bx = popupX + popupW - 20, by = popupY + 6;
                if (click.x() >= bx && click.x() <= bx + 14 && click.y() >= by && click.y() <= by + 14) {
                    closePopup();
                    return true;
                }
                // Reset icons of popup rows first
                for (ClickableWidget w : popupRows) {
                    if (hitResetIcon(w, click.x(), click.y())) {
                        Module m = moduleOf(w);
                        if (m != null) m.resetToDefault();
                        refreshRow(w);
                        return true;
                    }
                }
                // Row dispatch
                for (ClickableWidget w : popupRows) {
                    if (click.x() >= w.getX() && click.x() <= w.getRight()
                            && click.y() >= w.getY() && click.y() <= w.getBottom()) {
                        if (w instanceof ToggleRow || w instanceof CycleRow) {
                            popupFocused = null;
                            w.onClick(click, bl);
                        } else if (w instanceof PopupSlider s) {
                            popupFocused = null;
                            popupDragSlider = s;
                            s.dragTo(click.x());
                        } else if (w instanceof HexField) {
                            // POPUP-1: popup rows are not registered children,
                            // so Screen never routed clicks to them and the
                            // hex color field was unreachable. Route it
                            // explicitly; onClick focuses + positions the
                            // caret (1.21.11 TextFieldWidget API).
                            popupFocused = w;
                            w.onClick(click, bl);
                        }
                        return true;
                    }
                }
            }
            return true; // modal: swallow clicks while the popup is open
        }
        if (click.button() == 0) {
            // Reset icons before rows (icons live in the gutter, but a click
            // on a row's icon must not toggle the row itself)
            for (ClickableWidget w : rows) {
                if (hitResetIcon(w, click.x(), click.y())) {
                    Module m = moduleOf(w);
                    if (m != null) m.resetToDefault();
                    refreshRow(w);
                    return true;
                }
            }
            if (hitScrollbar(click.x(), click.y())) {
                scrollDragging = true;
                dragStartY = (int) click.y();
                dragStartOffset = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (popupModule != null) {
            popupDragSlider = null;
            return true;
        }
        scrollDragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (popupModule != null) {
            if (popupDragSlider != null) {
                popupDragSlider.dragTo(click.x());
                return true;
            }
            return true;
        }
        if (scrollDragging) {
            int thumb = thumbHeight();
            int travel = contentH - thumb;
            if (travel > 0) {
                scrollOffset = dragStartOffset + (int) (click.y() - dragStartY) * maxScroll() / travel;
                if (scrollOffset < 0) scrollOffset = 0;
                int max = maxScroll();
                if (scrollOffset > max) scrollOffset = max;
                positionRows();
            }
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popupModule != null) {
            for (ClickableWidget w : popupRows) {
                if (w instanceof PopupSlider s && mx >= s.getX() && mx <= s.getRight()
                        && my >= s.getY() && my <= s.getBottom()) {
                    Module m = s.module;
                    double d = v > 0 ? m.step() : -m.step();
                    m.setValue(m.stepValue(m.value() + d));
                    s.syncFromModule();
                    return true;
                }
            }
            if (mx >= popupX && mx <= popupX + popupW
                    && my >= popupContentTop && my <= popupContentTop + popupContentH) {
                popupScroll += v > 0 ? -ROW_PITCH * 2 : ROW_PITCH * 2;
                popupScroll = Math.max(0, Math.min(popupScroll, popupMaxScroll()));
            }
            return true;
        }
        boolean handled = super.mouseScrolled(mx, my, h, v);
        if (!handled && mx >= panelX && mx <= panelX + panelW
                && my >= contentTop && my <= contentTop + contentH) {
            // Wheel over a slider row = step the module's value by its step (e.g.
            // +0.05 for view-model X/Y/Z/scale); anywhere else = scroll the list.
            for (ClickableWidget w : rows) {
                if (w instanceof ModuleSlider s && mx >= s.getX() && mx <= s.getRight()
                        && my >= s.getY() && my <= s.getBottom()) {
                    Module m = s.module;
                    double d = v > 0 ? m.step() : -m.step();
                    m.setValue(m.stepValue(m.value() + d));
                    s.syncFromModule();
                    return true;
                }
            }
            // v>0 = wheel up = scroll towards earlier entries (offset 0 = top)
            scrollOffset += v > 0 ? -ROW_PITCH * 2 : ROW_PITCH * 2;
            if (scrollOffset < 0) scrollOffset = 0;
            int max = maxScroll();
            if (scrollOffset > max) scrollOffset = max;
            positionRows();
            handled = true;
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyInput key) {
        // ESC while a popup is open closes only the popup, not the GUI
        if (popupModule != null && key.getKeycode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closePopup();
            return true;
        }
        if (popupModule != null && popupFocused instanceof TextFieldWidget f && f.isFocused()) {
            // POPUP-1: backspace/delete/enter inside the popup hex field.
            return f.keyPressed(key);
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean charTyped(CharInput c) {
        if (popupModule != null) {
            // POPUP-1: typing was swallowed entirely while a popup was open,
            // so the hex color field could not be edited. Forward to the
            // focused popup row (the field), ignore elsewhere.
            if (popupFocused instanceof TextFieldWidget f && f.isFocused()) {
                f.charTyped(c);
            }
            return true;
        }
        return super.charTyped(c);
    }

    // ── Popup (composite module editor) ────────────────────────────────

    private void openPopup(Module m) {
        popupModule = m;
        popupScroll = 0;
        // layoutPopup() normally runs in paintPopup, but mouseClicked() can fire
        // before the first render — the close-button hit-test needs popupX/W/Y
        // defined immediately, or a click at (0..14, 0..14) closes the popup
        // spuriously. Compute the geometry up front (2026-08-02).
        layoutPopup();
        popupRows.clear();
        for (Module c : m.children().values()) {
            ClickableWidget w;
            if (c.isToggleable()) w = new ToggleRow(c);
            else if (c.isNumeric()) w = new PopupSlider(c);
            else if (c.options() != null) w = new CycleRow(c);
            else w = new HexField(c); // free-form string: hex field, like the main grid
            popupRows.add(w);
        }
    }

    private void closePopup() {
        popupModule = null;
        popupDragSlider = null;
        popupFocused = null;
        popupRows.clear();
    }

    private int popupMaxScroll() {
        return Math.max(0, popupRows.size() * ROW_PITCH - popupContentH);
    }

    private void layoutPopup() {
        popupW = Math.min(380, panelW - 160);
        popupX = panelX + (panelW - popupW) / 2;
        popupY = panelY + 40;
        int headerH = 30;
        popupContentH = Math.min(popupRows.size() * ROW_PITCH, panelH - 40 - headerH - 60);
        popupContentTop = popupY + headerH;
        popupH = headerH + popupContentH + 2;
    }

    private void paintPopup(DrawContext ctx, int mouseX, int mouseY) {
        if (popupModule == null) return;
        layoutPopup();
        // Modal dim only needs to hint focus — 40% alpha, not a second blackout
        // layer on top of the already-translucent panel.
        ctx.fill(0, 0, width, height, 0x66101016);
        ctx.fill(popupX, popupY, popupX + popupW, popupY + popupH, PANEL_BG);
        ctx.fill(popupX, popupY, popupX + 2, popupY + popupH, ACCENT);
        ctx.fill(popupX, popupY + popupH - 1, popupX + popupW, popupY + popupH, BORDER);
        ctx.drawText(textRenderer,
            Text.literal(popupModule.displayName() + " \u00a78— ESC to close"),
            popupX + 8, popupY + 6, FG, false);
        // Close button ("X", 2026-08-02): a plain click target so the popup
        // can be dismissed with the mouse, not just ESC. Rect-drawn like the
        // reset icon — no texture asset dependency.
        int cx = popupX + popupW - 20, cy = popupY + 6;
        boolean chov = mouseX >= cx && mouseX <= cx + 14 && mouseY >= cy && mouseY <= cy + 14;
        ctx.fill(cx, cy, cx + 14, cy + 14, chov ? 0xFF3A3A4A : 0xFF20202A);
        ctx.drawText(textRenderer, Text.literal("\u00d7"), cx + 3, cy + 2, chov ? 0xFFFF5555 : DIM, false);

        for (int i = 0; i < popupRows.size(); i++) {
            ClickableWidget w = popupRows.get(i);
            w.setX(popupX + 6);
            w.setY(popupContentTop + i * ROW_PITCH - popupScroll);
            w.setWidth(popupW - 12 - (ICON_GAP + ICON_W + 4));
        }
        ctx.enableScissor(popupX, popupContentTop, popupX + popupW, popupContentTop + popupContentH);
        for (ClickableWidget w : popupRows) {
            w.render(ctx, mouseX, mouseY, 0);
            paintResetIcon(ctx, w, mouseX, mouseY);
        }
        ctx.disableScissor();
    }

    // ── Scrollbar ──────────────────────────────────────────────────────

    private boolean hitScrollbar(double x, double y) {
        int trackX = panelX + panelW - SCROLL_W - 8;
        return x >= trackX && x <= panelX + panelW - 8
            && y >= contentTop && y <= contentTop + contentH;
    }

    // Thumb size = visible fraction of the total content (min 18px so it
    // stays grabbable on long lists); 0 when nothing overflows.
    private int thumbHeight() {
        int total = modules(activeCategory).size() * ROW_PITCH;
        if (total <= contentH) return 0;
        return Math.min(Math.max(18, contentH * contentH / total), contentH);
    }

    // ── Render (every frame — MC clears the framebuffer, skipping = flicker) ──

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        paintChrome(context);
        // Scissor-clip the rows: MC does not clip child widgets, so without
        // this a scrolled-out row paints over the tab bar (2026-08-01 bug).
        // Tabs are children too, so they are redrawn on top after disabling.
        context.enableScissor(panelX, contentTop, panelX + panelW, contentTop + contentH);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();
        // Tabs + HUD button live above the content area, so they were clipped
        // out by the scissor — redraw them on top.
        for (TabButton t : tabs) t.render(context, mouseX, mouseY, delta);
        if (hudButton != null) hudButton.render(context, mouseX, mouseY, delta);
        paintResetIcons(context, mouseX, mouseY);
        paintForeground(context);
        paintPopup(context, mouseX, mouseY);
    }

    private void paintChrome(DrawContext context) {
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        context.fill(panelX, panelY, panelX + panelW, contentTop, TAB_BG);
        context.fill(panelX, panelY, panelX + 2, panelY + panelH, ACCENT);
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, BORDER);
        // Active tab underline
        int idx = categories.indexOf(activeCategory);
        if (idx >= 0) {
            int tabW = (panelW - 32) / Math.max(1, categories.size());
            context.fill(panelX + idx * tabW, contentTop - 2,
                panelX + (idx + 1) * tabW, contentTop, ACCENT);
        }
        // Free-form hex row labels (the field itself is a child widget)
        for (ClickableWidget w : rows) {
            if (w instanceof HexField) {
                context.drawText(textRenderer, w.getMessage(),
                    w.getX(), w.getY() + (ROW_H - 8) / 2, FG, false);
            }
        }
    }

    private void paintResetIcons(DrawContext context, int mouseX, int mouseY) {
        for (ClickableWidget w : rows) {
            paintResetIcon(context, w, mouseX, mouseY);
        }
    }

    private void paintForeground(DrawContext context) {
        // B7: after a window resize contentH shrinks but scrollOffset keeps
        // its old value — the thumb would paint past the bottom of the track
        // and rows would stay shifted. Clamp every frame (same pattern as
        // the terminal scroll-overflow lesson); rows reposition on change.
        int max = maxScroll();
        if (scrollOffset > max) {
            scrollOffset = max;
            positionRows();
        }
        if (thumbHeight() == 0) return;
        int trackX = panelX + panelW - SCROLL_W - 8;
        context.fill(trackX, contentTop, trackX + SCROLL_W, contentTop + contentH, TRACK_BG);
        int thumb = thumbHeight();
        int ty = contentTop + (max > 0 ? scrollOffset * (contentH - thumb) / max : 0);
        context.fill(trackX, ty, trackX + SCROLL_W, ty + thumb, THUMB_BG);
    }

    // ── Row factory ────────────────────────────────────────────────────

    private ClickableWidget createRow(Module m) {
        if (m.isComposite()) return new CompositeRow(m);
        if (m.isToggleable()) return new ToggleRow(m);
        if (m.isNumeric()) return new ModuleSlider(m);
        if (m.options() != null) return new CycleRow(m);
        return new HexField(m);
    }

    // ── Tab button ─────────────────────────────────────────────────────

    private final class TabButton extends ClickableWidget {
        private final String category;

        TabButton(int x, int y, int w, String category) {
            super(x, y, w, TAB_H, Text.literal(category));
            this.category = category;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            boolean active = category.equals(activeCategory);
            int bg = active ? TAB_BG_ACT : (isHovered() ? TAB_BG_HOV : TAB_BG);
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            int tw = textRenderer.getWidth(getMessage());
            ctx.drawText(textRenderer, getMessage(),
                getX() + (getWidth() - tw) / 2, getY() + (TAB_H - 8) / 2,
                active ? ACCENT : DIM, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            switchCategory(category);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── HUD Editor button (grid glyph, right end of the category bar) ──

    private final class HudButton extends ClickableWidget {
        HudButton(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal("HUD Editor"));
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int bg = isHovered() ? TAB_BG_HOV : TAB_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            // 2x2 grid glyph
            int g = 6, gap = 3;
            int ox = getX() + (getWidth() - g * 2 - gap) / 2;
            int oy = getY() + (getHeight() - g * 2 - gap) / 2;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 2; c++) {
                    ctx.fill(ox + c * (g + gap), oy + r * (g + gap),
                        ox + c * (g + gap) + g, oy + r * (g + gap) + g, ACCENT);
                }
            }
        }

        @Override
        public void onClick(Click click, boolean bl) {
            MinecraftClient.getInstance().setScreen(new HudEditorScreen());
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Composite row (click opens the popup) ──────────────────────────

    private final class CompositeRow extends ClickableWidget {
        private final Module module;

        CompositeRow(Module m) {
            super(0, 0, 300, ROW_H, Text.literal(m.displayName()));
            this.module = m;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? ROW_BG_HOV : ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            ctx.drawText(textRenderer, getMessage(), getX() + 6, getY() + (ROW_H - 8) / 2, FG, false);
            StringBuilder sb = new StringBuilder();
            for (Module c : module.children().values()) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(c.id()).append("=").append(c.displayValue());
            }
            String summary = sb.toString();
            int tw = textRenderer.getWidth(summary);
            ctx.drawText(textRenderer, Text.literal(summary),
                getRight() - tw - 8, getY() + (ROW_H - 8) / 2, DIM, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            openPopup(module);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Toggle row (custom paint) ──────────────────────────────────────

    private static final class ToggleRow extends ClickableWidget {
        private final Module module;

        ToggleRow(Module m) {
            super(0, 0, 300, ROW_H, Text.literal(m.displayName()));
            this.module = m;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? ROW_BG_HOV : ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            ctx.drawText(textRenderer, getMessage(), getX() + 6, getY() + (ROW_H - 8) / 2, FG, false);
            boolean on = module.isEnabled();
            int pillW = 30;
            int px = getRight() - pillW - 6;
            int py = getY() + (ROW_H - 12) / 2;
            ctx.fill(px, py, px + pillW, py + 12, on ? ON_BG : OFF_BG);
            String label = on ? "ON" : "OFF";
            int tw = textRenderer.getWidth(label);
            ctx.drawText(textRenderer, Text.literal(label),
                px + (pillW - tw) / 2, py + 2, on ? 0xFF7EE787 : DIM, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            module.setEnabled(!module.isEnabled());
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Slider row (vanilla SliderWidget) ──────────────────────────────

    private static final class ModuleSlider extends SliderWidget {
        private final Module module;

        ModuleSlider(Module m) {
            super(0, 0, 300, ROW_H, Text.literal(m.displayName()),
                  (m.value() - m.min()) / (m.max() - m.min()));
            this.module = m;
            updateMessage();
        }

        void syncFromModule() {
            this.value = (module.value() - module.min()) / (module.max() - module.min());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(module.displayName() + ": " + module.displayValue()));
        }

        @Override
        protected void applyValue() {
            module.setValue(module.stepValue(module.min() + this.value * (module.max() - module.min())));
            this.value = (module.value() - module.min()) / (module.max() - module.min());
            updateMessage();
        }
    }

    // ── Popup slider (custom drag, no vanilla drag plumbing needed) ────

    private static final class PopupSlider extends ClickableWidget {
        private final Module module;
        private double ratio;

        PopupSlider(Module m) {
            super(0, 0, 300, ROW_H, Text.literal(m.displayName()));
            this.module = m;
            this.ratio = (m.value() - m.min()) / (m.max() - m.min());
        }

        void syncFromModule() {
            this.ratio = (module.value() - module.min()) / (module.max() - module.min());
        }

        void dragTo(double x) {
            double w = Math.max(1, getWidth() - 8);
            ratio = Math.max(0.0, Math.min(1.0, (x - getX() - 4) / w));
            module.setValue(module.stepValue(module.min() + ratio * (module.max() - module.min())));
            ratio = (module.value() - module.min()) / (module.max() - module.min());
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? ROW_BG_HOV : ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            int trackX = getX() + 4, trackW = getWidth() - 8;
            ctx.fill(trackX, getY() + ROW_H / 2 - 2, trackX + trackW, getY() + ROW_H / 2 + 2, TRACK_BG);
            int fx = trackX + (int) (ratio * trackW);
            ctx.fill(trackX, getY() + ROW_H / 2 - 2, fx, getY() + ROW_H / 2 + 2, ACCENT);
            ctx.fill(fx - 2, getY() + 4, fx + 2, getY() + ROW_H - 4, ACCENT);
            ctx.drawText(textRenderer, Text.literal(module.displayName() + ": " + module.displayValue()),
                getX() + 6, getY() + 2, FG, false);
            // Value at the right end (before the reset icon gutter)
            String val = module.displayValue();
            int tw = textRenderer.getWidth(val);
            ctx.drawText(textRenderer, Text.literal(val),
                getRight() - tw - 26, getY() + (ROW_H - 8) / 2, ACCENT, false);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Cycle row (string module with fixed options) ───────────────────

    private static final class CycleRow extends ClickableWidget {
        private final Module module;
        private int index;

        CycleRow(Module m) {
            super(0, 0, 300, ROW_H, Text.literal(m.displayName()));
            this.module = m;
            this.index = Math.max(0, m.options().indexOf(m.stringValue()));
        }

        void refreshIndex() {
            this.index = Math.max(0, module.options().indexOf(module.stringValue()));
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? ROW_BG_HOV : ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            ctx.drawText(textRenderer, getMessage(), getX() + 6, getY() + (ROW_H - 8) / 2, FG, false);
            String val = module.displayOption(module.stringValue());
            int tw = textRenderer.getWidth(val);
            ctx.drawText(textRenderer, Text.literal(val),
                getRight() - tw - 8, getY() + (ROW_H - 8) / 2, ACCENT, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            List<String> options = module.options();
            index = (index + 1) % options.size();
            module.setStringValue(options.get(index));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Hex color field row (vanilla TextFieldWidget) ──────────────────

    private static final Pattern HEX6 = Pattern.compile("[0-9a-fA-F]{6}");

    private static final class HexField extends TextFieldWidget {
        private final Module module;

        HexField(Module m) {
            super(MinecraftClient.getInstance().textRenderer, 200, 0, 300, ROW_H,
                  Text.literal(m.displayName()));
            this.module = m;
            setMaxLength(6);
            setPlaceholder(Text.literal("ffffff"));
            setText(m.stringValue());
            setChangedListener(text -> {
                if (HEX6.matcher(text).matches()) {
                    module.setStringValue(text.toLowerCase(Locale.ROOT));
                }
            });
        }
    }
}
