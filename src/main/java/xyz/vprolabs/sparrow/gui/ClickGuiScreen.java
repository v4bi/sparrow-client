package xyz.vprolabs.sparrow.gui;

import xyz.vprolabs.sparrow.module.Module;
import xyz.vprolabs.sparrow.module.ModuleManager;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.tweaks.SparrowSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
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
 *
 * v3.2 (2026-08-09 user request): hover tooltips on EVERY row. Each module
 * carries a description (Module.withDescription); hovering a row (or a popup
 * row) paints a box near the cursor with the name + wrapped description,
 * flipped when it would leave the screen. The HUD Editor glyph gets one too.
 *
 * v4 (2026-08-09 user request): the main list becomes a TILE GRID, 4 tiles
 * per row — a category with 12 features now spans 3 rows, not 12 ("Easier +
 * Faster navigation"). Each tile shows ONLY the feature name + state:
 *   - toggle/composite -> ON/OFF pill, nothing else ("Zoom shows ON|OFF, no
 *     config data"); composite tiles get a three-dot gear + right-click to
 *     open the settings popup, LEFT click flips the master toggle (the
 *     `zoom` parent now really gates ZoomMixin)
 *   - numeric/string -> name + current value; click opens a single-row popup
 * Popup sliders grow a second line: the label sits ABOVE the track (was
 * painted directly on it — "Zoom Level: inside the slider", user report).
 * Popup rows may be conditional: children with withVisibleWhen(sibling) only
 * show while the sibling toggle is ON (zoom-reset-value follows zoom-reset).
 *
 * v4.1 (2026-08-09, TODO line 6/7/9): spacing + popup sizing overhaul.
 *  - 12s gap under the tab bar and between tile rows, 10s between columns
 *    ("modules are way too close to eachother", user report).
 *  - popupContentH cap no longer wastes an extra 60s margin — the old
 *    formula shrank the content window to ~14px on small screens and
 *    "sucked in" glint's sliders; the cap is now popupY-offset + header +
 *    border, floor 42s guarantees one slider row.
 *  - Popup labels (ToggleRow/CycleRow/HexField) truncate against their
 *    right-side elements — long names overlapped the pill/value or ran off
 *    the popup ("left side of configuration is invisible", user report).
 *  - HexField moved from x+150 to x+130 so 6 hex chars fit at 200px popups.
 */
public class ClickGuiScreen extends Screen {

    // Palette: Midnight Sakura, single source = gui.Theme (2026-08-08).
    // Panel is translucent (alpha 0x9C ≈ 61%) — user rejected the near-opaque
    // slab as a "huge black bar"; game must stay visible behind the GUI.
    // Rows keep opaque backgrounds so text stays readable on the glass.

    // GUI scaling (2026-08-08, `gui-scale` module in Sparrow): the base
    // metrics below are multiplied by the scale in layout(). 2026-08-09 user
    // correction: scale affects each ELEMENT (tabs/rows/icons/margins), NOT
    // the panel itself — the panel is always fullscreen-minus-margin. Font
    // size stays fixed (8px) — true text zoom would need matrix transforms.

    private int margin, tabH, rowH, rowPitch, scrollW, iconW, iconGap, hudBtnW;
    private int tileGap, tileW, popupRowH, perRow;
    private float scale = 1f;
    // v4: target 4 columns ("4 features per row", user spec 2026-08-09) — but
    // only when tiles stay wide enough. At the DEFAULT 854x480 window (GUI
    // scale 2 -> 427x240 screen) 4 fixed columns give 94px tiles and names
    // like "Zoom Smoothness" (112px) spill over the ON/OFF pill and into the
    // neighbor tile ("FIX SCALING WHEN MY WINDOW IS 854x400", 2026-08-09).
    // perRow shrinks to 2 below ~155px tiles; the big screen keeps 4.
    private static final int GRID_COLS = 4;
    private static final int MIN_TILE_W = 150;

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
    // True while the open popup contains an RGB picker (swatch/channel rows).
    // The per-frame paint loop uses it to mirror module changes into the
    // popup hex field — slider drags must not leave stale text in the field,
    // and plain hex fields (no picker) must keep their existing behavior.
    private boolean popupColorPicker;

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

    // Panel = always fullscreen-minus-margin (2026-08-09 user correction: the
    // scale must affect each element, not the panel — the old centered float
    // window below 1.0 is gone). All internal metrics derive from `scale`, so
    // no other method recomputes it.
    private void layout() {
        scale = Math.max(0.5f, Math.min(1.5f, Modules.guiScale.floatValue()));
        margin   = Math.round(8 * scale);
        tabH     = Math.round(24 * scale);
        rowH     = Math.round(22 * scale);
        // v4.1: 12s pitch (was 8s) — user: "modules are way too close to
        // eachother". 12px at scale 1 reads as clear separation between
        // tiles; 8px was indistinguishable from the tile's own padding.
        rowPitch = rowH + Math.round(12 * scale);
        scrollW  = Math.max(3, Math.round(4 * scale));
        iconW    = Math.round(16 * scale);
        iconGap  = Math.round(5 * scale);
        hudBtnW  = Math.round(28 * scale);
        int maxW = Math.max(240, width - margin * 2);
        int maxH = Math.max(160, height - margin * 2);
        panelW = maxW;
        panelH = maxH;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        // 2026-08-09: a 12s gap under the tab bar — the flush layout glued
        // the first tile row to the tabs ("spacing is fucked up").
        contentTop = panelY + tabH + Math.round(12 * scale);
        contentH = panelH - (tabH + Math.round(12 * scale)) - margin;
        // Tile grid: N columns across the content area, leaving the scrollbar
        // track + reset-icon gutter clear. Column count ADAPTS to the window:
        // keep GRID_COLS while each tile keeps >= MIN_TILE_W, else drop
        // columns (a 94px tile cannot hold a 112px feature name + pill).
        tileGap = Math.round(10 * scale);
        int avail = panelW - scrollW - margin - Math.round(6 * scale);
        int minW = Math.round(MIN_TILE_W * scale);
        perRow = Math.max(1, (avail + tileGap) / (minW + tileGap));
        tileW = (avail - tileGap * (perRow - 1)) / perRow;
        // Popup slider rows are two-line (label above track), so they are
        // taller than the one-line toggle/cycle/hex rows.
        popupRowH = Math.round(34 * scale);
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
        // hudBtnW reserved on the right end for the HUD Editor button.
        int n = Math.max(1, categories.size());
        int tabW = (panelW - hudBtnW) / n;
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
        hudButton = addDrawableChild(new HudButton(panelX + panelW - hudBtnW, panelY, hudBtnW, tabH));
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
        positionTiles();
    }

    // v4 grid placement: index i -> column i%perRow, row i/perRow.
    private void positionTiles() {
        for (int i = 0; i < rows.size(); i++) {
            ClickableWidget w = rows.get(i);
            w.setX(panelX + Math.round(6 * scale) + (i % perRow) * (tileW + tileGap));
            w.setY(contentTop + (i / perRow) * rowPitch - scrollOffset);
            w.setWidth(tileW);
        }
    }

    private int gridRowCount() {
        return (rows.size() + perRow - 1) / perRow;
    }

    private int maxScroll() {
        return Math.max(0, gridRowCount() * rowPitch - contentH);
    }

    // ── Reset icon helpers ─────────────────────────────────────────────

    // Grid tiles keep the icon INSIDE their right edge (tiles sit flush in a
    // grid, there is no gutter between neighbours); popup rows keep the
    // outside gutter (their width computation reserves it).
    private int resetIconX(ClickableWidget w) {
        if (w instanceof ModuleTile) return w.getRight() - iconW - Math.round(3 * scale);
        return w.getRight() + iconGap;
    }

    private boolean hitResetIcon(ClickableWidget w, double x, double y) {
        int ix = resetIconX(w);
        int iy = w.getY() + (w.getHeight() - iconW) / 2;
        return x >= ix && x <= ix + iconW && y >= iy && y <= iy + iconW;
    }

    private void paintResetIcon(DrawContext ctx, ClickableWidget w, int mouseX, int mouseY) {
        int ix = resetIconX(w);
        int iy = w.getY() + (w.getHeight() - iconW) / 2;
        boolean hover = mouseX >= ix && mouseX <= ix + iconW && mouseY >= iy && mouseY <= iy + iconW;
        Module m = moduleOf(w);
        // Hover ring follows the theme (was hardcoded 0xAA2F2448). Midnight
        // ROW_BG_HOV is the exact same hue as the old ring; Hanami gets a
        // warm paper halo. Opaque instead of translucent — reads cleaner on
        // both light and dark panels.
        if (hover) ctx.fill(ix - 1, iy - 1, ix + iconW + 1, iy + iconW + 1, Theme.ROW_BG_HOV);
        // Reset glyph drawn with rects, NOT a texture: the PNG refused to load at
        // runtime ("Missing resource") and a drawTexturedQuad with width/height
        // passed as x2/y2 painted a giant inverted black quad over the panel.
        // Rect drawing has zero resource dependency and cannot fail.
        int c = (m == null || m.isAtDefault()) ? Theme.RESET_DIM : Theme.ACCENT;
        ctx.fill(ix + 3, iy + 3, ix + 13, iy + 5, c);   // ring top
        ctx.fill(ix + 3, iy + 11, ix + 13, iy + 13, c); // ring bottom
        ctx.fill(ix + 3, iy + 3, ix + 5, iy + 13, c);   // ring left
        ctx.fill(ix + 11, iy + 3, ix + 13, iy + 13, c); // ring right
        ctx.fill(ix + 8, iy + 1, ix + 12, iy + 3, c);   // arrow base
        ctx.fill(ix + 11, iy + 0, ix + 13, iy + 2, c);  // arrow tip
        if (hover) ctx.fill(ix, iy, ix + 1, iy + iconW, c);
    }

    private Module moduleOf(ClickableWidget w) {
        if (w instanceof ModuleTile t) return t.module;
        if (w instanceof ToggleRow r) return r.module;
        if (w instanceof CycleRow c) return c.module;
        if (w instanceof HexField f) return f.module;
        if (w instanceof PopupSlider s) return s.module;
        // SwatchRow: the color module it previews — the reset icon on the
        // swatch resets the whole color, which is what the swatch represents.
        if (w instanceof SwatchRow r) return r.module;
        return null;
    }

    // Refresh a row's visual state after an external value change (reset).
    private void refreshRow(ClickableWidget w) {
        if (w instanceof PopupSlider s) s.syncFromModule();
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
                int bx = popupX + popupW - Math.round(20 * scale), by = popupY + Math.round(6 * scale);
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
                            if (w instanceof ToggleRow) {
                                // Conditional rows (withVisibleWhen) may have
                                // appeared/disappeared — rebuild so e.g.
                                // zoom-reset-value follows zoom-reset.
                                buildPopupRows();
                            }
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
                        } else if (w instanceof PlaySoundRow) {
                            popupFocused = null;
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
        } else if (click.button() == 1) {
            // Right-click on a composite tile = settings popup. Handled at
            // SCREEN level: widget dispatch may not route button 1 to
            // onClick on every MC version, so don't rely on ModuleTile.
            for (ClickableWidget w : rows) {
                if (w instanceof ModuleTile t && t.module.isComposite()
                        && click.x() >= w.getX() && click.x() <= w.getRight()
                        && click.y() >= w.getY() && click.y() <= w.getBottom()) {
                    openPopup(t.module);
                    return true;
                }
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
                positionTiles();
            }
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popupModule != null) {
            for (ClickableWidget w : popupRows) {
                if (w instanceof RgbSliderRow rs && mx >= rs.getX() && mx <= rs.getRight()
                        && my >= rs.getY() && my <= rs.getBottom()) {
                    // v4.2: channel sliders step one RGB unit per notch. MUST
                    // be checked BEFORE the PopupSlider branch — RgbSliderRow
                    // IS-A PopupSlider, and the module-based branch below
                    // would step the backing module, which is the wrong
                    // source for a binding-backed row.
                    rs.stepBy(v > 0 ? 1 : -1);
                    return true;
                }
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
                popupScroll += v > 0 ? -rowPitch * 2 : rowPitch * 2;
                popupScroll = Math.max(0, Math.min(popupScroll, popupMaxScroll()));
            }
            return true;
        }
        boolean handled = super.mouseScrolled(mx, my, h, v);
        if (!handled && mx >= panelX && mx <= panelX + panelW
                && my >= contentTop && my <= contentTop + contentH) {
            // v4: the main grid is all tiles — no inline sliders to step;
            // wheel anywhere over the content area scrolls the tile grid.
            scrollOffset += v > 0 ? -rowPitch * 2 : rowPitch * 2;
            if (scrollOffset < 0) scrollOffset = 0;
            int max = maxScroll();
            if (scrollOffset > max) scrollOffset = max;
            positionTiles();
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
        // buildPopupRows FIRST so layoutPopup() (which needs the row count for
        // popupContentH) sees the final list; the close-button hit-test in
        // mouseClicked() needs popupX/W/Y before the first render (2026-08-02).
        buildPopupRows();
        layoutPopup();
    }

    // Rebuilds the popup row list from the module's children. v4: called for
    // composites AND single value modules (numeric/string tiles open a
    // one-row popup). Rows with withVisibleWhen(sibling) are skipped while
    // the sibling toggle is off — rebuilt live when a toggle flips.
    private void buildPopupRows() {
        popupRows.clear();
        popupColorPicker = false;
        if (popupModule == null) return;
        // RGB picker (2026-08-09 user spec: "add an RGB color selector to all
        // places where you can select custom HEX/RGB color"). A color STRING
        // module (crosshair-color) swaps its lone hex field for swatch + three
        // channel sliders + hex field; a color COMPOSITE (glint) gets a live
        // swatch prepended above its normal children rows. Any other isColor()
        // shape falls back to the plain rows below (no picker).
        if (popupModule.isColor()) {
            if (popupModule.isString()) {
                addColorPicker(popupModule);
                return;
            }
            if (popupModule.isComposite()) {
                ChannelBinding cb = ChannelBinding.tryCreate(popupModule);
                if (cb != null) {
                    // Swatch only: the children rows (toggle + channel
                    // sliders) already edit the channel modules directly; the
                    // swatch reads them live every frame, so both stay in
                    // sync without touching the children rows.
                    popupRows.add(new SwatchRow(popupModule, cb));
                    popupColorPicker = true;
                }
            }
        }
        Iterable<Module> children = popupModule.isComposite()
            ? popupModule.children().values()
            : List.of(popupModule);
        for (Module c : children) {
            if (c.visibleWhen() != null) {
                Module sib = popupModule.child(c.visibleWhen());
                if (sib == null || !sib.isEnabled()) continue;
            }
            ClickableWidget w;
            if (c.isToggleable()) w = new ToggleRow(c);
            else if (c.isNumeric()) w = new PopupSlider(c);
            else if (c.options() != null) w = new CycleRow(c);
            else if (c.isColor()) {
                // Color string child inside a plain composite (crosshair-color
                // in the crosshair popup): same picker as a standalone color
                // module. The user asked for the picker EVERYWHERE a color is
                // edited, and this row is one of those places.
                addColorPicker(c);
                continue;
            }
            else w = new HexField(c); // free-form string: hex field, like the main grid
            popupRows.add(w);
        }
        // Test-play button for the death sound popup (2026-08-10 user spec):
        // "add a button, which is a Sound icon... plays the Short or Full sound
        // whichever I have currently selected". Only the death-sound composite
        // has variant+volume children that SparrowSounds can play; the row is
        // rebuilt with the popup, so it always reads the CURRENT values.
        if ("death-sound".equals(popupModule.id())) {
            Module variant = popupModule.child("death-sound-variant");
            Module volume = popupModule.child("death-sound-volume");
            if (variant != null && volume != null) {
                popupRows.add(new PlaySoundRow(variant, volume));
            }
        }
    }

    // Swatch + R/G/B sliders + typed hex field, in that order, all bound to
    // ONE hex-string color module through a single RgbBinding. The hex field
    // stays for typed entry — it writes the module via its change listener,
    // and the per-frame sync in paintPopup mirrors slider edits back into it.
    private void addColorPicker(Module colorModule) {
        RgbBinding b = new HexBinding(colorModule);
        popupRows.add(new SwatchRow(colorModule, b));
        popupRows.add(new RgbSliderRow(b, 0, "Red", colorModule));
        popupRows.add(new RgbSliderRow(b, 1, "Green", colorModule));
        popupRows.add(new RgbSliderRow(b, 2, "Blue", colorModule));
        popupRows.add(new HexField(colorModule));
        popupColorPicker = true;
    }

    private void closePopup() {
        popupModule = null;
        popupDragSlider = null;
        popupFocused = null;
        popupColorPicker = false;
        popupRows.clear();
    }

    // Slider rows are two-line (popupRowH); toggle/cycle/hex rows are one-line.
    private int popupRowHeight(ClickableWidget w) {
        return w instanceof PopupSlider ? popupRowH : rowH;
    }

    private int popupMaxScroll() {
        int total = 0;
        for (ClickableWidget w : popupRows) total += popupRowHeight(w) + Math.round(8 * scale);
        return Math.max(0, total - popupContentH);
    }

    private void layoutPopup() {
        // The 380s target shrinks on narrow panels (160s reserved for side
        // chrome); a hard floor keeps the popup usable at small windows —
        // 108px-wide popups "sucked in" their sliders (854x480 @ GUI scale 3,
        // 2026-08-09 report). Clamped to the panel so popupX stays on-screen.
        popupW = Math.min(panelW, Math.max(
            Math.min(Math.round(380 * scale), panelW - Math.round(160 * scale)),
            Math.round(200 * scale)));
        popupX = panelX + (panelW - popupW) / 2;
        popupY = panelY + Math.round(40 * scale);
        int headerH = Math.round(30 * scale);
        // v4: rows have mixed heights (sliders two-line) — estimate the stack.
        int rowsH = 0;
        for (ClickableWidget w : popupRows) rowsH += popupRowHeight(w) + Math.round(8 * scale);
        // Cap the stack to the panel but keep a floor: a zero/negative
        // contentH pushed every row outside the scissor and the popup
        // rendered blank ("doesn't render the slider", 2026-08-09). The floor
        // guarantees at least one slider row is fully visible; longer stacks
        // scroll (popupMaxScroll + wheel already handle that).
        // v4.1: the cap is popupY offset (40s) + header + bottom border —
        // the old formula subtracted ANOTHER 60s, which shrank the content
        // window to ~14px on small screens and "sucked in" the glint sliders
        // (854x480 @ GUI scale 3, 2026-08-09 report). With the corrected cap
        // a 4-row glint popup shows ~3 rows on that screen instead of 1.
        popupContentH = Math.max(Math.round(42 * scale),
            Math.min(rowsH, Math.max(0, panelH - Math.round(74 * scale))));
        popupContentTop = popupY + headerH;
        popupH = headerH + popupContentH + 2;
    }

    private void paintPopup(DrawContext ctx, int mouseX, int mouseY) {
        if (popupModule == null) return;
        layoutPopup();
        // Modal dim only needs to hint focus — 40% alpha, not a second blackout
        // layer on top of the already-translucent panel.
        ctx.fill(0, 0, width, height, 0x66151026);
        ctx.fill(popupX, popupY, popupX + popupW, popupY + popupH, Theme.PANEL_BG);
        ctx.fill(popupX, popupY, popupX + 2, popupY + popupH, Theme.ACCENT);
        ctx.fill(popupX, popupY + popupH - 1, popupX + popupW, popupY + popupH, Theme.BORDER);
        ctx.drawText(textRenderer,
            Text.literal(popupModule.displayName() + " \u00a78— ESC to close"),
            popupX + Math.round(8 * scale), popupY + Math.round(6 * scale), Theme.FG, false);
        // Close button ("X", 2026-08-02): a plain click target so the popup
        // can be dismissed with the mouse, not just ESC. Rect-drawn like the
        // reset icon — no texture asset dependency.
        int cx = popupX + popupW - Math.round(20 * scale), cy = popupY + Math.round(6 * scale);
        boolean chov = mouseX >= cx && mouseX <= cx + 14 && mouseY >= cy && mouseY <= cy + 14;
        // Non-hover uses Theme.ROW_BG, not hardcoded 0xFF241C38: that dark
        // plum square stayed dark in the light Hanami theme while the rest
        // of the popup went paper-white (theme-blind color audit,
        // 2026-08-10). Midnight ROW_BG (0xFF201830) is visually identical.
        ctx.fill(cx, cy, cx + 14, cy + 14, chov ? Theme.THUMB_BG : Theme.ROW_BG);
        ctx.drawText(textRenderer, Text.literal("\u00d7"), cx + 3, cy + 2, chov ? Theme.DANGER : Theme.DIM, false);

        // v4: rows stack with per-row heights (sliders are two-line) and
        // conditional rows come/go — rebuild the running Y each frame.
        if (popupScroll > popupMaxScroll()) popupScroll = popupMaxScroll();
        int y = popupContentTop - popupScroll;
        for (ClickableWidget w : popupRows) {
            int h = popupRowHeight(w);
            w.setX(popupX + Math.round(6 * scale));
            w.setY(y);
            w.setWidth(Math.max(0, popupW - Math.round(12 * scale) - (iconGap + iconW + 4)));
            if (w instanceof HexField f) {
                // POPUP-2: popup hex fields paint their label to the LEFT of
                // the widget (below); labelStart also feeds the tooltip hit
                // test, so hovering the label still shows the tooltip.
                // v4.1: field moved from x+150 to x+130 so a 6-char hex value
                // fits at narrow popup widths (was 19px wide at 200px popups).
                f.labelStart = popupX + Math.round(8 * scale);
                f.setX(popupX + Math.round(130 * scale));
                // Negative width (tiny popups) made the vanilla text-field
                // background draw an inverted quad — a giant artifact over
                // the popup. Floor at 0; the label is still painted.
                f.setWidth(Math.max(0, popupW - Math.round(136 * scale) - (iconGap + iconW + 4)));
                // v4.2 RGB picker live mirror: slider drags write the module
                // behind the field's back, so push the module value back into
                // the field text every frame — but NEVER while the user is
                // typing there (a focused field is the input, not a mirror;
                // syncing mid-typing would clobber partial entries like "ff"
                // that the user is still composing).
                if (popupColorPicker && !f.isFocused()) {
                    String sv = f.module.stringValue();
                    if (sv != null && !f.getText().equals(sv)) f.setText(sv);
                }
            }
            y += h + Math.round(8 * scale);
        }
        ctx.enableScissor(popupX, popupContentTop, popupX + popupW, popupContentTop + popupContentH);
        for (ClickableWidget w : popupRows) {
            if (w instanceof HexField f) {
                // Label for the hex field (the field itself only paints its
                // text/placeholder — getMessage would otherwise never show).
                // v4.1: truncated to the label gutter — an unclipped long
                // label ran under the field ("left side text invisible",
                // 2026-08-09).
                drawTruncated(ctx, f.getMessage().getString(),
                    f.labelStart, f.getY() + (rowH - 8) / 2,
                    Math.max(0, f.getX() - f.labelStart - Math.round(6 * scale)), Theme.FG);
            }
            w.render(ctx, mouseX, mouseY, 0);
            paintResetIcon(ctx, w, mouseX, mouseY);
        }
        ctx.disableScissor();
    }

    // ── Tooltips (v3.2, 2026-08-09) ────────────────────────────────────

    // Wrap on word boundaries to a max pixel width. Font is fixed 8px, so
    // ~48 chars ≈ 240px; scale only the box width target, not the font
    // (consistent with the rest of the GUI — font never zooms).
    private static List<String> wrapTooltipText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (MinecraftClient.getInstance().textRenderer.getWidth(probe) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    // Title (accent) + wrapped description, in a near-opaque box with the
    // accent left edge. Positioned 12/10px off the cursor, flipped left/up
    // when it would leave the screen. Drawn AFTER the popup so it always
    // sits on top; the box is plain rects — no texture dependency.
    private void paintTooltip(DrawContext ctx, int mouseX, int mouseY, String title, String desc) {
        if (desc == null) return;
        List<String> lines = wrapTooltipText(desc, Math.round(240 * scale));
        int lineH = 9;
        int pad = 5;
        int w = textRenderer.getWidth(title);
        for (String l : lines) w = Math.max(w, textRenderer.getWidth(l));
        w += pad * 2;
        int h = pad * 2 + lineH + lines.size() * lineH;
        int x = mouseX + 12;
        int y = mouseY + 10;
        if (x + w > width - 4) x = Math.max(4, mouseX - w - 12);
        if (y + h > height - 4) y = Math.max(4, mouseY - h - 10);
        // Box = Theme.PANEL_BG (was hardcoded 0xF0151026 midnight plum —
        // the tooltip ignored the theme, staying dark purple over the light
        // Hanami GUI). Same translucent-panel look as the popup, so tooltips
        // match their theme in both palettes.
        ctx.fill(x, y, x + w, y + h, Theme.PANEL_BG);
        ctx.fill(x, y, x + 2, y + h, Theme.ACCENT);
        ctx.fill(x, y + h - 1, x + w, y + h, Theme.BORDER);
        ctx.drawText(textRenderer, Text.literal(title), x + pad, y + pad, Theme.ACCENT, false);
        int ty = y + pad + lineH;
        for (String l : lines) {
            ctx.drawText(textRenderer, Text.literal(l), x + pad, ty, Theme.FG, false);
            ty += lineH;
        }
    }

    // First described module whose row is under the cursor. HexField rows
    // extend the hit area left over their painted label (the label sits left
    // of the field widget, which only occupies the right part of the row).
    private Module tooltipTarget(List<ClickableWidget> list, int mx, int my) {
        for (ClickableWidget w : list) {
            boolean hit;
            if (w instanceof HexField f) {
                hit = mx >= f.labelStart && mx <= w.getRight()
                    && my >= w.getY() && my <= w.getBottom();
            } else {
                hit = mx >= w.getX() && mx <= w.getRight() && my >= w.getY() && my <= w.getBottom();
            }
            if (hit) {
                Module m = moduleOf(w);
                if (m != null && m.description() != null) return m;
            }
        }
        return null;
    }

    // ── Scrollbar ──────────────────────────────────────────────────────

    private boolean hitScrollbar(double x, double y) {
        int trackX = panelX + panelW - scrollW - margin;
        return x >= trackX && x <= panelX + panelW - 8
            && y >= contentTop && y <= contentTop + contentH;
    }

    // Thumb size = visible fraction of the total content (min 18px so it
    // stays grabbable on long lists); 0 when nothing overflows. Uses the GRID
    // row count — the old linear count overestimated content when columns
    // dropped (perRow < GRID_COLS), making the thumb visibly undersized.
    private int thumbHeight() {
        int total = gridRowCount() * rowPitch;
        if (total <= contentH) return 0;
        return Math.min(Math.max(18, contentH * contentH / total), contentH);
    }

    // True while the Gui Scale slider itself is held down (v4: it lives in a
    // popup now — no inline sliders in the tile grid). The render loop defers
    // relayout during a drag — see render() (2026-08-09).
    private boolean guiScaleDragging() {
        return popupDragSlider != null && popupDragSlider.module == Modules.guiScale;
    }

    // ── Render (every frame — MC clears the framebuffer, skipping = flicker) ──

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Live relayout when the gui-scale module changes (slider in the
        // Sparrow tab). layout() is pure arithmetic, so recompute only on an
        // actual change; init() re-derives tabs/rows from the new geometry.
        // Deferred while the Gui Scale slider itself is dragged: rebuilding
        // rows mid-drag destroys the widget under the cursor and the drag
        // dies after one step (2026-08-09). Relayout fires on mouse release.
        float s = Modules.guiScale.floatValue();
        if (s != scale && !guiScaleDragging()) {
            init();
        }        paintChrome(context);
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
        // Tooltips LAST so they draw above everything (rows, popup, dims).
        Module tip = popupModule != null
            ? tooltipTarget(popupRows, mouseX, mouseY)
            : tooltipTarget(rows, mouseX, mouseY);
        if (tip != null) {
            paintTooltip(context, mouseX, mouseY, tip.displayName(), tip.description());
        } else if (hudButton != null && hudButton.isHovered()) {
            paintTooltip(context, mouseX, mouseY, "HUD Editor",
                "Open the HUD position editor: drag HUD elements into place.");
        }
    }

    private void paintChrome(DrawContext context) {
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, Theme.PANEL_BG);
        // v4.2: fill ONLY the strip the tab buttons occupy (panelY..tabH).
        // The old fill ran down to contentTop, painting the 12s spacing gap
        // as a full-width TAB_BG band — a glaring near-white "bar" between
        // the tabs and the grid in the Hanami theme. The gap now shows
        // PANEL_BG, i.e. the panel's own background (user: "make it
        // invisible, keep the background color"). The active-tab underline
        // still anchors flush at panelY+tabH, so it is unaffected.
        context.fill(panelX, panelY, panelX + panelW, panelY + tabH, Theme.TAB_BG);
        // Pink->violet gradient edge: the panel's identity line (fillGradient
        // is vertical top->bottom in 1.21.11; 2px wide, spans the panel).
        context.fillGradient(panelX, panelY, panelX + 2, panelY + panelH, Theme.ACCENT, Theme.ACCENT2);
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, Theme.BORDER);
        // Active tab underline — gradient so the active tab reads as "lit".
        int idx = categories.indexOf(activeCategory);
        if (idx >= 0) {
            int tabW = (panelW - hudBtnW) / Math.max(1, categories.size());
            // Flush with the tab bar bottom: contentTop now sits 8s lower
            // (spacing gap), so the old contentTop-2 anchor floated mid-gap.
            context.fillGradient(panelX + idx * tabW, panelY + tabH - 2,
                panelX + (idx + 1) * tabW, panelY + tabH, Theme.ACCENT, Theme.ACCENT2);
        }
        // v4: no inline HexFields in the grid anymore (strings edit via popup);
        // popup hex labels are painted in paintPopup.
    }

    private void paintResetIcons(DrawContext context, int mouseX, int mouseY) {
        for (ClickableWidget w : rows) {
            paintResetIcon(context, w, mouseX, mouseY);
        }
    }

    // Draw a string clipped to maxW with an ellipsis. Needed because the
    // tile grid's column count adapts to the window: at small windows a tile
    // can be narrower than its feature name, and ctx.drawText does not clip.
    private void drawTruncated(DrawContext ctx, String s, int x, int y, int maxW, int color) {
        // v4.1: a non-positive budget draws nothing — the old code drew a
        // bare "…" at the x origin, which could land outside the widget.
        if (maxW <= 0) return;
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer.getWidth(s) <= maxW) {
            ctx.drawText(textRenderer, Text.literal(s), x, y, color, false);
            return;
        }
        while (!s.isEmpty() && textRenderer.getWidth(s + "\u2026") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        ctx.drawText(textRenderer, Text.literal(s + "\u2026"), x, y, color, false);
    }

    // Two-line variant for tiles: when the name does not fit, wrap the
    // remainder onto a second dim line (word-based, like wrapTooltipText)
    // instead of discarding it in an ellipsis. Line 2 sits at y+8 — the 8px
    // font keeps it inside a rowH (22s) tile. A single word longer than maxW
    // still gets the ellipsis treatment.
    private void drawTruncatedWrap(DrawContext ctx, String s, int x, int y, int maxW, int color, int color2) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        // v4.1: zero/negative budget = nothing to draw (mirrors drawTruncated).
        if (maxW <= 0) return;
        if (textRenderer.getWidth(s) <= maxW) {
            ctx.drawText(textRenderer, Text.literal(s), x, y, color, false);
            return;
        }
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String probe = cur.length() == 0 ? word : cur + " " + word;
            if (textRenderer.getWidth(probe) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(probe);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        int ly = y;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = lines.get(i);
            if (textRenderer.getWidth(line) > maxW) {
                while (!line.isEmpty() && textRenderer.getWidth(line + "\u2026") > maxW) {
                    line = line.substring(0, line.length() - 1);
                }
                line += "\u2026";
            }
            ctx.drawText(textRenderer, Text.literal(line), x, ly, i == 0 ? color : color2, false);
            ly += 8;
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
            positionTiles();
        }
        if (thumbHeight() == 0) return;
        int trackX = panelX + panelW - scrollW - margin;
        context.fill(trackX, contentTop, trackX + scrollW, contentTop + contentH, Theme.TRACK_BG);
        int thumb = thumbHeight();
        int ty = contentTop + (max > 0 ? scrollOffset * (contentH - thumb) / max : 0);
        context.fill(trackX, ty, trackX + scrollW, ty + thumb, Theme.THUMB_BG);
    }

    // ── Row factory ────────────────────────────────────────────────────

    // v4: every feature is a tile in the 4-column grid. Toggle tiles flip on
    // click; composite tiles flip their master toggle on LEFT click and open
    // the settings popup via the gear / right-click; numeric + string tiles
    // open a single-row popup with their control.
    private ClickableWidget createRow(Module m) {
        return new ModuleTile(m);
    }

    // ── Tab button ─────────────────────────────────────────────────────

    private final class TabButton extends ClickableWidget {
        private final String category;

        TabButton(int x, int y, int w, String category) {
            super(x, y, w, tabH, Text.literal(category));
            this.category = category;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            boolean active = category.equals(activeCategory);
            int bg = active ? Theme.TAB_BG_ACT : (isHovered() ? Theme.TAB_BG_HOV : Theme.TAB_BG);
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            int tw = textRenderer.getWidth(getMessage());
            ctx.drawText(textRenderer, getMessage(),
                getX() + (getWidth() - tw) / 2, getY() + (tabH - 8) / 2,
                active ? Theme.ACCENT : Theme.DIM, false);
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
            int bg = isHovered() ? Theme.TAB_BG_HOV : Theme.TAB_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            // 2x2 grid glyph
            int g = 6, gap = 3;
            int ox = getX() + (getWidth() - g * 2 - gap) / 2;
            int oy = getY() + (getHeight() - g * 2 - gap) / 2;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 2; c++) {
                    ctx.fill(ox + c * (g + gap), oy + r * (g + gap),
                        ox + c * (g + gap) + g, oy + r * (g + gap) + g, Theme.ACCENT);
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

    // ── Module tile (v4: the ONLY main-grid widget — 4 per row) ────────

    private final class ModuleTile extends ClickableWidget {
        private final Module module;

        ModuleTile(Module m) {
            super(0, 0, 300, rowH, Text.literal(m.displayName()));
            this.module = m;
        }

        // Right inset reserved for the reset icon, which sits INSIDE the
        // tile's right edge (tiles are flush in the grid — no gutter between
        // neighbours, unlike the full-width popup rows).
        private int rightInset() { return iconW + iconGap + Math.round(3 * scale); }

        // Three-dot "more" glyph (composite tiles): opens the settings popup.
        // Rect-drawn like the reset icon — no texture dependency.
        private int gearX() { return getRight() - rightInset() - Math.round(14 * scale); }

        private boolean hitGear(double x, double y) {
            int gx = gearX();
            return x >= gx && x <= gx + Math.round(10 * scale)
                && y >= getY() && y <= getBottom();
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            // Name only — NO config data on the tile (2026-08-09 user spec:
            // "Zoom shows ON|OFF, nothing else, no config data"). Values live
            // in the popup; numeric/string tiles show just their current value.
            // The name is TRUNCATED to the space left of the right-side
            // elements: without this, narrow tiles (small windows, perRow<4)
            // let text spill over the pill and into the neighbor tile.
            int rightReserve = rightInset() + Math.round(6 * scale);
            boolean pill = module.isComposite() || module.isToggleable();
            if (pill) {
                rightReserve += Math.round(30 * scale) + Math.round(4 * scale); // pill
                if (module.isComposite()) rightReserve += Math.round(16 * scale); // gear room
            }
            // Value width first (it feeds the name's budget): the name must
            // never run under a long value ("Terminal [LEGACY]") — the old
            // order let text spill over the pill / into the neighbor tile.
            String val = pill ? null
                : (module.isString() ? module.displayOption(module.stringValue())
                                     : module.displayValue());
            int valueW = val == null ? 0 : textRenderer.getWidth(val);
            int maxValW = Math.max(0, getWidth() - Math.round(6 * scale) - rightInset());
            if (val != null && valueW > maxValW) {
                while (!val.isEmpty() && textRenderer.getWidth(val + "\u2026") > maxValW) {
                    val = val.substring(0, val.length() - 1);
                }
                val += "\u2026";
                valueW = textRenderer.getWidth(val);
            }
            // Two-line wrap for the name: truncated names continue on a dim
            // second line (y+8, inside the rowH tile) instead of dying in an
            // ellipsis ("text goes out of the module frame", 2026-08-09).
            drawTruncatedWrap(ctx, getMessage().getString(), getX() + Math.round(6 * scale),
                getY() + (rowH - 8) / 2,
                Math.max(0, getWidth() - Math.round(6 * scale) - rightReserve - valueW - Math.round(6 * scale)),
                Theme.FG, Theme.DIM);
            if (pill) {
                if (module.isLocked()) {
                    // BUGGY badge (2026-08-10): locked modules render a red
                    // warning tag instead of the ON/OFF pill — the user must
                    // SEE that the feature is broken, not just un-toggleable.
                    String tag = "BUGGY";
                    int tw = textRenderer.getWidth(tag);
                    int px = getRight() - rightInset() - tw;
                    if (module.isComposite()) px -= Math.round(16 * scale); // gear room
                    ctx.drawText(textRenderer, Text.literal(tag), px, getY() + (getHeight() - 8) / 2,
                        Theme.ERR, false);
                } else {
                    boolean on = module.isEnabled();
                    int pillW = Math.round(30 * scale);
                    int pillH = Math.round(12 * scale);
                    int px = getRight() - rightInset() - pillW;
                    if (module.isComposite()) px -= Math.round(16 * scale); // gear room
                    int py = getY() + (getHeight() - pillH) / 2;
                    ctx.fill(px, py, px + pillW, py + pillH, on ? Theme.ON_BG : Theme.OFF_BG);
                    String label = on ? "ON" : "OFF";
                    int tw = textRenderer.getWidth(label);
                    ctx.drawText(textRenderer, Text.literal(label),
                        px + (pillW - tw) / 2, py + 2, on ? Theme.ON_TEXT : Theme.DIM, false);
                }
            } else {
                ctx.drawText(textRenderer, Text.literal(val),
                    getRight() - rightInset() - valueW - Math.round(6 * scale),
                    getY() + (rowH - 8) / 2, Theme.ACCENT, false);
            }
            if (module.isComposite()) {
                // Three-dot gear (hover lights up) = "settings in here".
                int gx = gearX();
                int gy = getY() + (getHeight() - 8) / 2;
                boolean gh = hitGear(mouseX, mouseY);
                int dot = Math.round(3 * scale), step = Math.round(5 * scale);
                for (int d = 0; d < 3; d++) {
                    ctx.fill(gx + d * step, gy, gx + d * step + dot, gy + 8, gh ? Theme.ACCENT : Theme.DIM);
                }
            }
        }

        @Override
        public void onClick(Click click, boolean bl) {
            // LOCK-1 (2026-08-10): BUGGY-locked modules are dead — no toggle,
            // no popup. Module.setEnabled also refuses, this is belt & braces.
            if (module.isLocked()) return;
            if (module.isComposite()) {
                // Gear or right-click = settings; LEFT click on the tile body
                // flips the master toggle (the parent REALLY gates the feature,
                // e.g. `zoom` -> ZoomMixin).
                if (hitGear(click.x(), click.y()) || click.button() != 0) {
                    openPopup(module);
                    return;
                }
                module.setEnabled(!module.isEnabled());
                return;
            }
            if (module.isToggleable()) {
                module.setEnabled(!module.isEnabled());
                return;
            }
            // numeric / string: single-row popup with the control
            openPopup(module);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Toggle row (custom paint) ──────────────────────────────────────

    private final class ToggleRow extends ClickableWidget {
        private final Module module;

        ToggleRow(Module m) {
            super(0, 0, 300, rowH, Text.literal(m.displayName()));
            this.module = m;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            // v4.1: label truncated to the pill — an unclipped long name ran
            // under the ON/OFF pill and out of the popup ("left side of
            // configuration is invisible", 2026-08-09).
            drawTruncated(ctx, getMessage().getString(), getX() + 6, getY() + (rowH - 8) / 2,
                Math.max(0, getWidth() - Math.round(30 * scale) - Math.round(12 * scale)), Theme.FG);
            if (module.isLocked()) {
                // BUGGY tag in place of the ON/OFF pill (2026-08-10): locked
                // modules are always OFF and cannot be toggled; the red tag
                // says why the pill is missing.
                String tag = "BUGGY";
                int tw = textRenderer.getWidth(tag);
                int px = getRight() - tw - Math.round(6 * scale);
                ctx.drawText(textRenderer, Text.literal(tag), px, getY() + (rowH - 8) / 2,
                    Theme.ERR, false);
                return;
            }
            boolean on = module.isEnabled();
            int pillW = Math.round(30 * scale);
            int pillH = Math.round(12 * scale);
            int px = getRight() - pillW - Math.round(6 * scale);
            int py = getY() + (getHeight() - pillH) / 2;
            ctx.fill(px, py, px + pillW, py + pillH, on ? Theme.ON_BG : Theme.OFF_BG);
            String label = on ? "ON" : "OFF";
            int tw = textRenderer.getWidth(label);
            ctx.drawText(textRenderer, Text.literal(label),
                px + (pillW - tw) / 2, py + 2, on ? Theme.ON_TEXT : Theme.DIM, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            // LOCK-1 (2026-08-10): BUGGY-locked modules are dead in popups too.
            if (module.isLocked()) return;
            module.setEnabled(!module.isEnabled());
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Popup slider (custom drag, no vanilla drag plumbing needed) ────
    // v4.2: NOT final anymore — RgbSliderRow subclasses it to reuse the
    // track paint and the mouseClicked/mouseDragged dispatch (instanceof +
    // virtual dragTo), overriding only the value source (binding, not module).

    private class PopupSlider extends ClickableWidget {
        private final Module module;
        // v4.2: protected (was private) so RgbSliderRow, which subclasses
        // this for the picker, can re-derive the thumb position from its
        // binding without duplicating the track painting. The ratio is the
        // only slider state the subclass needs; `module` stays private.
        protected double ratio;

        PopupSlider(Module m) {
            // Two-line row (label above track): height = popupRowH so the hit
            // rect covers the whole row — the old rowH height left the track's
            // bottom 2px outside the widget, where clicks fell through.
            super(0, 0, 300, popupRowH, Text.literal(m.displayName()));
            this.module = m;
            this.ratio = (m.value() - m.min()) / (m.max() - m.min());
        }

        void syncFromModule() {
            this.ratio = (module.value() - module.min()) / (module.max() - module.min());
        }

        void dragTo(double x) {
            double w = Math.max(1, getWidth() - Math.round(8 * scale));
            ratio = Math.max(0.0, Math.min(1.0, (x - getX() - Math.round(4 * scale)) / w));
            module.setValue(module.stepValue(module.min() + ratio * (module.max() - module.min())));
            ratio = (module.value() - module.min()) / (module.max() - module.min());
        }

        // v4.2: label text extracted into a method so RgbSliderRow can paint
        // "Red: 255" — a channel row is NOT its backing module, and showing
        // the module's name there would mislabel every R/G/B row.
        protected String sliderLabel() {
            return module.displayName() + ": " + module.displayValue();
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            // Label ABOVE the track (2026-08-09 user report: "Zoom Level:
            // inside of the slider"): the old single-line row painted the
            // label at the top of a rowH-tall widget, directly on top of the
            // vertically-centered track. Two-line rows — name line, then
            // track — keep the text clear of the slider.
            // Label truncated to the row width: a long name + value
            // ("Adaptive Resolution Min Scale: 0.83") spilled past the row
            // edge and under the reset icon at narrow popups.
            drawTruncated(ctx, sliderLabel(),
                getX() + 6, getY() + 2, Math.max(0, getWidth() - Math.round(12 * scale)), Theme.FG);
            int trackY = getY() + popupRowH - Math.round(14 * scale);
            int trackX = getX() + Math.round(4 * scale);
            int trackW = Math.max(0, getWidth() - Math.round(8 * scale));
            ctx.fill(trackX, trackY, trackX + trackW, trackY + 4, Theme.TRACK_BG);
            int fx = trackX + (int) (ratio * trackW);
            ctx.fill(trackX, trackY, fx, trackY + 4, Theme.ACCENT);
            ctx.fill(fx - 2, trackY - 6, fx + 2, trackY + 10, Theme.ACCENT);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // ── Cycle row (string module with fixed options) ───────────────────

    private final class CycleRow extends ClickableWidget {
        private final Module module;
        private int index;

        CycleRow(Module m) {
            super(0, 0, 300, rowH, Text.literal(m.displayName()));
            this.module = m;
            this.index = Math.max(0, m.options().indexOf(m.stringValue()));
        }

        void refreshIndex() {
            this.index = Math.max(0, module.options().indexOf(module.stringValue()));
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            String val = module.displayOption(module.stringValue());
            int tw = textRenderer.getWidth(val);
            // v4.1: label truncated against the right-aligned value — a long
            // name ("Fire Timer Position") overlapped the value text in the
            // popup; the value always wins (it is the current state).
            drawTruncated(ctx, getMessage().getString(), getX() + 6, getY() + (getHeight() - 8) / 2,
                Math.max(0, getWidth() - tw - Math.round(14 * scale)), Theme.FG);
            ctx.drawText(textRenderer, Text.literal(val),
                getRight() - tw - Math.round(8 * scale), getY() + (getHeight() - 8) / 2, Theme.ACCENT, false);
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

    // ── Death sound test button (v4.3, 2026-08-10 user spec) ───────────
    //
    // Plays the currently selected death-sound variant at the current
    // volume, so the sound can be auditioned without dying. The speaker
    // glyph is drawn with rects like the reset icon — zero texture asset
    // dependency (the reset.png lesson: a missing texture painted a giant
    // black quad, so UI icons are rect-drawn, period).
    private final class PlaySoundRow extends ClickableWidget {
        private final Module variant;
        private final Module volume;

        PlaySoundRow(Module variant, Module volume) {
            super(0, 0, 300, rowH, Text.literal("Test sound"));
            this.variant = variant;
            this.volume = volume;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            int ix = getX() + Math.round(6 * scale);
            int iy = getY() + (getHeight() - iconW) / 2;
            int c = isHovered() ? Theme.ACCENT : Theme.FG;
            // Speaker: box + cone, then two sound waves.
            ctx.fill(ix + 0, iy + 5, ix + 5, iy + 11, c);
            ctx.fill(ix + 5, iy + 3, ix + 7, iy + 13, c);
            ctx.fill(ix + 9, iy + 6, ix + 10, iy + 10, c);
            ctx.fill(ix + 11, iy + 4, ix + 12, iy + 12, c);
            String label = "Test sound (" + variant.displayOption(variant.stringValue()) + ")";
            ctx.drawText(textRenderer, Text.literal(label),
                ix + Math.round(16 * scale), getY() + (getHeight() - 8) / 2, c, false);
        }

        @Override
        public void onClick(Click click, boolean bl) {
            // Volume module is 1-100 (double), SparrowSounds wants linear
            // 0.0-1.0 — scale at call time so the button always matches the
            // slider the user just dragged.
            SparrowSounds.playDeath(variant.stringValue(),
                (float) (volume.value() / 100.0));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }
    //
    // A color is either a hex STRING module (crosshair-color: "rrggbb") or a
    // COMPOSITE of three 0-255 numeric children (glint: glint-r/g/b). Both
    // are edited through one RgbBinding so the picker widgets never care
    // which shape the underlying module has. The binding is the single read
    // source (swatch + slider thumbs repaint from it EVERY frame — no cached
    // color, slider edits show instantly) and the single write path (sliders
    // go through set(), which formats three ints, so the module can never be
    // left with an invalid value).

    private interface RgbBinding {
        // Channel values 0-255; -1 = the source currently holds no valid
        // color (e.g. a partial hex string). Callers must never write -1.
        int getR();
        int getG();
        int getB();
        // Write all three channels at once (read-modify-write callers pass
        // the two untouched channels back through).
        void set(int r, int g, int b);
        // True when the source currently parses to a valid color.
        boolean isValid();
    }

    // Binds a free-form hex STRING module ("rrggbb"). Reads parse the string
    // only when it matches HEX6; partial or foreign values read as -1 so the
    // swatch can dim and the sliders park at 0 instead of showing garbage.
    // Writes format three ints — valid by construction.
    private static final class HexBinding implements RgbBinding {
        private final Module module;

        HexBinding(Module m) { this.module = m; }

        private int channel(int shift) {
            String s = module.stringValue();
            if (s == null || !HEX6.matcher(s).matches()) return -1;
            return (Integer.parseInt(s, 16) >> shift) & 0xFF;
        }

        @Override public int getR() { return channel(16); }
        @Override public int getG() { return channel(8); }
        @Override public int getB() { return channel(0); }

        @Override public void set(int r, int g, int b) {
            // Lowercase output keeps config.json values canonical no matter
            // how the value got in; masking keeps stray bits out of the hex.
            module.setStringValue(String.format(Locale.ROOT, "%02x%02x%02x", r & 0xFF, g & 0xFF, b & 0xFF));
        }

        @Override public boolean isValid() {
            String s = module.stringValue();
            return s != null && HEX6.matcher(s).matches();
        }
    }

    // Binds a composite whose children are the glint-r/g/b channels (the
    // current color-composite shape). Child ids are FIXED — that is the shape
    // Module.withColor() documents for composite colors. tryCreate() returns
    // null when the composite does not match, and the caller falls back to
    // plain rows (no picker) instead of rendering a broken swatch.
    private static final class ChannelBinding implements RgbBinding {
        private final Module r, g, b;

        private ChannelBinding(Module r, Module g, Module b) {
            this.r = r; this.g = g; this.b = b;
        }

        static ChannelBinding tryCreate(Module composite) {
            Module r = composite.child("glint-r");
            Module g = composite.child("glint-g");
            Module b = composite.child("glint-b");
            if (r == null || g == null || b == null) return null;
            if (!r.isNumeric() || !g.isNumeric() || !b.isNumeric()) return null;
            if (r.min() < 0 || r.max() > 255 || g.min() < 0 || g.max() > 255
                    || b.min() < 0 || b.max() > 255) return null;
            return new ChannelBinding(r, g, b);
        }

        @Override public int getR() { return r.intValue(); }
        @Override public int getG() { return g.intValue(); }
        @Override public int getB() { return b.intValue(); }

        @Override public void set(int rv, int gv, int bv) {
            // setValue clamps to the module bounds (0-255) and fires
            // ModuleHooks — the glint refresh runs exactly as for a console
            // set, no extra plumbing here.
            r.setValue(rv); g.setValue(gv); b.setValue(bv);
        }

        @Override public boolean isValid() { return true; }
    }

    // Live color preview row (rowH, one line): swatch rect + module name +
    // current hex. Repaints from the binding EVERY frame — no cached color —
    // so slider drags and hex typing appear instantly.
    private final class SwatchRow extends ClickableWidget {
        private final Module module;
        private final RgbBinding binding;

        SwatchRow(Module m, RgbBinding b) {
            super(0, 0, 300, rowH, Text.literal(m.displayName()));
            this.module = m;
            this.binding = b;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int bg = isHovered() ? Theme.ROW_BG_HOV : Theme.ROW_BG;
            ctx.fill(getX(), getY(), getRight(), getBottom(), bg);
            int r = binding.getR(), g = binding.getG(), b = binding.getB();
            boolean valid = binding.isValid();
            int s = Math.round(12 * scale);
            int sx = getX() + Math.round(6 * scale);
            int sy = getY() + (getHeight() - s) / 2;
            // Border ring + fill. A dim lavender swatch signals "no valid
            // color in the module" (partial hex) instead of black-on-black.
            ctx.fill(sx - 1, sy - 1, sx + s + 1, sy + s + 1, Theme.BORDER);
            ctx.fill(sx, sy, sx + s, sy + s,
                valid ? 0xFF000000 | (r << 16) | (g << 8) | b : Theme.DIM);
            String hex = valid ? String.format(Locale.ROOT, "%02x%02x%02x", r, g, b) : "invalid";
            int tw = textRenderer.getWidth(hex);
            // Name truncates against the right-aligned hex (same rule as
            // CycleRow: the value always wins over the label).
            drawTruncated(ctx, getMessage().getString(),
                sx + s + Math.round(8 * scale), getY() + (rowH - 8) / 2,
                Math.max(0, getWidth() - Math.round(12 * scale) - tw - Math.round(14 * scale)), Theme.FG);
            ctx.drawText(textRenderer, Text.literal(hex),
                getRight() - tw - Math.round(8 * scale), getY() + (rowH - 8) / 2,
                valid ? Theme.ACCENT : Theme.DIM, false);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) { }
    }

    // One RGB channel slider (popupRowH, two lines like PopupSlider). Writes
    // through the binding's read-modify-write set(), keeping the two other
    // channels intact. SUBCLASSES PopupSlider (rather than duplicating its
    // paint/hit code) so the existing dispatch works untouched: mouseClicked
    // and mouseDragged reach it via `w instanceof PopupSlider` + virtual
    // dragTo(), popupRowHeight() sizes it as a two-line row, refreshRow()
    // resyncs it via virtual syncFromModule(). Only mouseScrolled needs an
    // explicit branch — the module-based wheel code would step the BACKING
    // module, which is the wrong source for a binding-backed row.
    private final class RgbSliderRow extends PopupSlider {
        private final RgbBinding binding;
        private final int channel;        // 0 = R, 1 = G, 2 = B
        private final String channelName; // "Red" / "Green" / "Blue"

        RgbSliderRow(RgbBinding b, int channel, String channelName, Module backing) {
            // `backing` only feeds PopupSlider's module plumbing (reset icon,
            // tooltip via moduleOf); the row's actual value lives in the
            // binding. Super's ratio init is meaningless here (a string
            // module has 0/0 range = NaN) — syncFromBinding() overrides it.
            super(backing);
            this.binding = b;
            this.channel = channel;
            this.channelName = channelName;
            syncFromBinding();
        }

        // Clamped 0-255 read for one channel index; -1 (invalid hex) parks
        // at 0 so a malformed value can never leak into a set() write.
        private int channelVal(int idx) {
            int v = idx == 0 ? binding.getR() : idx == 1 ? binding.getG() : binding.getB();
            return v < 0 ? 0 : Math.min(255, v);
        }

        private int channelValue() { return channelVal(channel); }

        @Override
        protected String sliderLabel() {
            return channelName + ": " + channelValue();
        }

        @Override
        void syncFromModule() {
            syncFromBinding();
        }

        private void syncFromBinding() {
            ratio = channelValue() / 255.0;
        }

        @Override
        void dragTo(double x) {
            double w = Math.max(1, getWidth() - Math.round(8 * scale));
            double r = Math.max(0.0, Math.min(1.0, (x - getX() - Math.round(4 * scale)) / w));
            int chan = Math.max(0, Math.min(255, (int) Math.round(r * 255)));
            binding.set(channel == 0 ? chan : channelVal(0),
                        channel == 1 ? chan : channelVal(1),
                        channel == 2 ? chan : channelVal(2));
            syncFromBinding();
        }

        // Wheel stepping (mouseScrolled): one RGB unit per notch.
        void stepBy(int dir) {
            int chan = Math.max(0, Math.min(255, channelValue() + dir));
            binding.set(channel == 0 ? chan : channelVal(0),
                        channel == 1 ? chan : channelVal(1),
                        channel == 2 ? chan : channelVal(2));
            syncFromBinding();
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // Live-follow: hex typing and sibling sliders change the binding
            // between frames; re-derive the thumb every render so the track
            // never shows a stale channel.
            syncFromBinding();
            super.renderWidget(ctx, mouseX, mouseY, delta);
        }
    }

    // ── Hex color field row (vanilla TextFieldWidget) ──────────────────

    private static final Pattern HEX6 = Pattern.compile("[0-9a-fA-F]{6}");

    private final class HexField extends TextFieldWidget {
        private final Module module;
        // POPUP-2: x where the painted label starts (set by paintPopup during
        // positioning). Used for the tooltip hit test so hovering the label
        // still shows the description. MIN_VALUE = not positioned yet.
        int labelStart = Integer.MIN_VALUE;

        HexField(Module m) {
            super(MinecraftClient.getInstance().textRenderer, 200, 0, 300, rowH,
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
