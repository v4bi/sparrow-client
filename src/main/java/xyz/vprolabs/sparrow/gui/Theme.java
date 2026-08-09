package xyz.vprolabs.sparrow.gui;

/**
 * Midnight Sakura palette (2026-08-08 user spec): replaces the GitHub-dark/
 * blue "deepseek AI" look (0xFF58A6FF accent, navy greys) that was used in
 * ClickGuiScreen, HudEditorScreen and SparrowConsoleScreen.
 *
 * Design notes:
 *  - Deep indigo/plum neutrals + hot sakura-pink accent + soft lavender text.
 *  - The panel bg stays translucent (alpha 0x9C ≈ 61%) — the user rejected a
 *    near-opaque slab as a "huge black bar"; the game must stay visible.
 *  - ACCENT/ACCENT2 are a gradient pair: fillGradient(ACCENT, ACCENT2) gives
 *    the pink->violet wash used on the panel's left edge and active tab
 *    underline. fillGradient exists in 1.21.11 DrawContext [VERIFIED javap].
 *  - All constants are static final ints: javac inlines them, so ProGuard
 *    obfuscation of this class cannot break a caller (no runtime refs).
 *  - This class lives in gui/, NOT the mixin package: Fabric Loader tries to
 *    mixin-transform every class under the mixin root (2026-06-21 lesson).
 */
public final class Theme {

    public static final int PANEL_BG    = 0x9C151026; // translucent indigo-plum
    public static final int TAB_BG      = 0xA8241B38;
    public static final int TAB_BG_HOV  = 0xFF332850;
    public static final int TAB_BG_ACT  = 0xFF382C5E;
    public static final int BORDER      = 0xFF453A68;
    public static final int FG          = 0xFFDCD2F0; // soft lavender text
    public static final int DIM         = 0xFF978AB8; // muted lavender
    public static final int ACCENT      = 0xFFFF5CA8; // hot sakura pink
    public static final int ACCENT2     = 0xFFB16CFF; // violet (gradient partner)
    public static final int ROW_BG      = 0xFF201830;
    public static final int ROW_BG_HOV  = 0xFF2F2448;
    public static final int ON_BG       = 0xFFFF5CA8; // ON pill = accent fill
    public static final int OFF_BG      = 0xFF3A3350;
    public static final int ON_TEXT     = 0xFF1A1025; // dark plum on the pink pill
    public static final int TRACK_BG    = 0xFF191326;
    public static final int THUMB_BG    = 0xFF4A3D6E;
    public static final int DANGER      = 0xFFFF6B6B; // popup close hover
    public static final int RESET_DIM   = 0xFF4A3D6E; // reset icon, at default

    private Theme() {}
}
