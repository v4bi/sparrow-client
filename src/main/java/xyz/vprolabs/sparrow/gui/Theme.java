package xyz.vprolabs.sparrow.gui;

/**
 * GUI palettes for the Sparrow Menu. The Theme module (Modules.theme,
 * "Sparrow" category) switches between palettes at runtime via apply().
 *
 * Palettes:
 *  - "default": Midnight Sakura (2026-08-08 user spec) — deep indigo/plum
 *    neutrals + hot sakura-pink accent + soft lavender text. The panel bg
 *    stays translucent (alpha 0x9C ≈ 61%) — the user rejected a near-opaque
 *    slab as a "huge black bar"; the game must stay visible.
 *  - "hanami": light Japanese — washi-paper neutrals, warm ink text, sakura
 *    pink accent (the "Light Japanese" theme the user requested 2026-08-10,
 *    named after the cherry-blossom viewing festival).
 *
 * NOTE 2026-08-10: fields are NO LONGER static final. Runtime theme
 * switching requires callers to read the current value every frame, so
 * javac no longer inlines them. Callers that copy a color into a static
 * final at class load (SparrowConsoleScreen.FG etc.) stay frozen on the
 * palette active at load time — acceptable for the legacy terminal; the
 * click GUI and HUD editor read Theme.* per frame and switch live.
 *
 * This class lives in gui/, NOT the mixin package: Fabric Loader tries to
 * mixin-transform every class under the mixin root (2026-06-21 lesson).
 */
public final class Theme {

    public static int PANEL_BG    = 0x9C151026; // translucent indigo-plum
    public static int TAB_BG      = 0xA8241B38;
    public static int TAB_BG_HOV  = 0xFF332850;
    public static int TAB_BG_ACT  = 0xFF382C5E;
    public static int BORDER      = 0xFF453A68;
    public static int FG          = 0xFFDCD2F0; // soft lavender text
    public static int DIM         = 0xFF978AB8; // muted lavender
    public static int ACCENT      = 0xFFFF5CA8; // hot sakura pink
    public static int ACCENT2     = 0xFFB16CFF; // violet (gradient partner)
    public static int ROW_BG      = 0xFF201830;
    public static int ROW_BG_HOV  = 0xFF2F2448;
    public static int ON_BG       = 0xFFFF5CA8; // ON pill = accent fill
    public static int OFF_BG      = 0xFF3A3350;
    public static int ON_TEXT     = 0xFF1A1025; // dark plum on the pink pill
    public static int TRACK_BG    = 0xFF191326;
    public static int THUMB_BG    = 0xFF4A3D6E;
    public static int DANGER      = 0xFFFF6B6B; // popup close hover
    public static int ERR         = 0xFFFF6B6B; // BUGGY tag on locked modules (2026-08-10)
    public static int RESET_DIM   = 0xFF4A3D6E; // reset icon, at default

    // Hanami palette (light Japanese, 2026-08-10): washi-paper cream
    // neutrals + warm dark-ink text + sakura-pink accent. Contrast checked
    // for text on these fills: FG (#4A3F35) on ROW_BG (#F4ECE0) ≈ 9:1, DIM
    // (#8A7A6B) ≈ 4.5:1 — readable on the light fills. ON pill keeps white
    // text on sakura pink. Alpha on PANEL_BG keeps the translucent-panel
    // contract of the dark theme (game visible behind the menu).
    private static final int[] HANAMI = {
        0xA8F7F1E6, // PANEL_BG — translucent warm paper
        0xE8F0E9DC, // TAB_BG
        0xFFF2E3C4, // TAB_BG_HOV
        0xFFFBE9E0, // TAB_BG_ACT — blush
        0xFFD8C4B0, // BORDER — warm tan
        0xFF4A3F35, // FG — warm ink
        0xFF8A7A6B, // DIM — muted tan
        0xFFE05A96, // ACCENT — sakura pink
        0xFFB08AE8, // ACCENT2 — soft wisteria
        0xFFF4ECE0, // ROW_BG
        0xFFEADFCE, // ROW_BG_HOV
        0xFFE05A96, // ON_BG
        0xFFC9BCA9, // OFF_BG
        0xFFFFFFFF, // ON_TEXT
        0xFFE3D8C4, // TRACK_BG
        0xFF9C8B76, // THUMB_BG
        0xFFFF6B6B, // DANGER
        0xFFFF6B6B, // ERR
        0xFF9C8B76, // RESET_DIM
    };

    // Order must match the HANAMI array (assignments below are positional).
    private static final int[] MIDNIGHT = {
        0x9C151026, 0xA8241B38, 0xFF332850, 0xFF382C5E, 0xFF453A68,
        0xFFDCD2F0, 0xFF978AB8, 0xFFFF5CA8, 0xFFB16CFF, 0xFF201830,
        0xFF2F2448, 0xFFFF5CA8, 0xFF3A3350, 0xFF1A1025, 0xFF191326,
        0xFF4A3D6E, 0xFFFF6B6B, 0xFFFF6B6B, 0xFF4A3D6E,
    };

    /** Apply a palette by module option name ("default" / "hanami").
     *  Unknown names fall back to the Midnight Sakura default — defensive;
     *  ModuleManager already validates the module's fixed options. */
    public static void apply(String name) {
        int[] p = "hanami".equals(name) ? HANAMI : MIDNIGHT;
        PANEL_BG = p[0];
        TAB_BG = p[1];
        TAB_BG_HOV = p[2];
        TAB_BG_ACT = p[3];
        BORDER = p[4];
        FG = p[5];
        DIM = p[6];
        ACCENT = p[7];
        ACCENT2 = p[8];
        ROW_BG = p[9];
        ROW_BG_HOV = p[10];
        ON_BG = p[11];
        OFF_BG = p[12];
        ON_TEXT = p[13];
        TRACK_BG = p[14];
        THUMB_BG = p[15];
        DANGER = p[16];
        ERR = p[17];
        RESET_DIM = p[18];
    }

    private Theme() {}
}
