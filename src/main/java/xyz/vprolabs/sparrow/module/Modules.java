package xyz.vprolabs.sparrow.module;

/**
 * Static registry of ALL modules — the single source of truth, mirroring the
 * old ConfigRegister entries one-for-one (same field names, same ids, same
 * categories, same defaults) so consumer rewiring stays mechanical.
 *
 * Slider bounds below match the validation ranges the console commands used:
 * view-model floats were unbounded, culling/nether/console entries had
 * explicit min-max, glint channels are 0-255.
 */
public final class Modules {

    // ── Sparrow (UI) ──────────────────────────────────────────────────
    // Defined first so the Sparrow panel is the first one in the click GUI.
    // The ui options use the Name/DisplayName pair: the NAME is what the
    // terminal accepts (`sparrow ui terminal`) — space-free, no illegal
    // chars — while the DisplayName is what the GUI cycle row and `list`
    // show ("Sparrow Menu" / "Terminal [LEGACY]"). The old display-string
    // values ("Sparrow Menu", "Terminal [LEGACY]") are still accepted on
    // load and migrated to their names via ModuleManager.LEGACY_OPTIONS.

    public static final Module ui = Module.withLabels("ui", "Sparrow", "menu",
        "menu", "Sparrow Menu",
        "terminal", "Terminal [LEGACY]");
    public static final Module guiFps = new Module("gui-fps", "Sparrow", 60, 5, 240, 1, true);

    // ── Visual ─────────────────────────────────────────────────────────

    public static final Module smallTotem       = new Module("small-totem", "Visual", false);
    public static final Module oldPotions       = new Module("old-potions", "Visual", false);
    public static final Module customGlint      = new Module("custom-glint", "Visual", false);
    public static final Module noMiscOverlays   = new Module("no-misc-overlays", "Visual", false);
    public static final Module removeShadows    = new Module("remove-shadows", "Visual", false);
    public static final Module storageTooltip   = new Module("storage-tooltip", "Visual", false);
    public static final Module coords           = new Module("coords", "Visual", false);
    public static final Module ping             = new Module("ping", "Visual", false);
    public static final Module desync           = new Module("desync", "Visual", false);
    public static final Module hitmarker        = new Module("hitmarker", "Visual", false);
    public static final Module shieldStatus     = new Module("shield-status", "Visual", false);
    public static final Module playerHitEnabled  = new Module("player-hit-toggle", "Visual", false);
    public static final Module playerHitType    = new Module("player-hit-type", "Visual", "hit",
        "hit", "abletohit");
    public static final Module playerHitColor   = new Module("player-hit-color", "Visual", "ff0000");
    // Player-hit panel (2026-08-02): one GUI row opening a popup with the
    // toggle + type + color. The composite id reuses "player-hit"; the old
    // leaf toggle moved to the "player-hit-toggle" child for config migration
    // (legacy player_hit boolean applies to it via ModuleManager).
    public static final Module playerHit        = Module.group("player-hit", "Visual",
        playerHitEnabled, playerHitType, playerHitColor);

    public static final Module crosshairMode    = new Module("crosshair-mode", "Visual", "off",
        "off", "plus", "heart", "tiny", "dot", "x", "clover");
    public static final Module crosshairColor   = new Module("crosshair-color", "Visual", "ffffff");
    // Crosshair panel (2026-08-02): one GUI row (mode + color). The old
    // `crosshair` leaf id becomes the composite parent; mode moved to the
    // "crosshair-mode" child so the saved legacy `crosshair` string value
    // migrates onto it (ModuleManager parent-key fallback).
    public static final Module crosshair        = Module.group("crosshair", "Visual",
        crosshairMode, crosshairColor);

    // ── View Model: ONE module in the GUI (popup: X/Y/Z/Scale). The four
    // children stay registered standalone — console commands (view-x, ...),
    // config keys and ViewModelMixin keep working unchanged.
    public static final Module viewModelX       = new Module("view-x", "Visual", 0.0, -10.0, 10.0, 0.05, false);
    public static final Module viewModelY       = new Module("view-y", "Visual", 0.0, -10.0, 10.0, 0.05, false);
    public static final Module viewModelZ       = new Module("view-z", "Visual", 0.0, -10.0, 10.0, 0.05, false);
    public static final Module viewModelSize    = new Module("view-size", "Visual", 1.0, 0.05, 5.0, 0.05, false);
    public static final Module viewModel        = Module.group("view-model", "Visual",
        viewModelX, viewModelY, viewModelZ, viewModelSize);

    public static final Module utilityScale     = new Module("utility-scale", "Visual", 0.65, 0.1, 2.0, 0.05, false);
    public static final Module glintR           = new Module("glint-r", "Visual", 0, 0, 255, 1, true);
    public static final Module glintG           = new Module("glint-g", "Visual", 255, 0, 255, 1, true);
    public static final Module glintB           = new Module("glint-b", "Visual", 0, 0, 255, 1, true);
    // Glint panel (2026-08-02): custom-glint (toggle) + the three RGB channels
    // collapse to ONE GUI row. The leaf ids stay registered standalone, so
    // console commands (glint-r/...), config keys and SparrowGlintLayers all
    // keep working. Parent id "glint" is new and collides with nothing.
    public static final Module glint            = Module.group("glint", "Visual",
        customGlint, glintR, glintG, glintB);
    // Fire Timer: ONE module in the GUI (popup: toggle + position). The
    // toggle lives under its own id (fire-timer-enabled) so the parent id
    // "fire-timer" can be the composite; ModuleManager migrates the old
    // fire_timer boolean onto the child. FireTimerMixin/Renderer read the
    // child through Modules.fireTimer.child("fire-timer-enabled").
    public static final Module fireTimerEnabled = new Module("fire-timer-enabled", "Visual", false);
    public static final Module fireTimerPos     = new Module("fire-timer-pos", "Visual", "BOTTOM_CENTER",
        "TOP_LEFT", "TOP_RIGHT", "BOTTOM_CENTER");
    public static final Module fireTimer        = Module.group("fire-timer", "Visual",
        fireTimerEnabled, fireTimerPos);
    public static final Module particleMode     = new Module("particles", "Visual", "off",
        "off", "minimal", "on");

    // ── World ──────────────────────────────────────────────────────────

    public static final Module fullbright        = new Module("fullbright", "Visual", false);
    public static final Module noMiningFatigue   = new Module("no-mining-fatigue", "Visual", false);
    public static final Module alwaysDay         = new Module("always-day", "World", false);
    public static final Module disableEntityAI   = new Module("disable-entity-ai", "World", false);
    public static final Module netherRenderCap   = new Module("nether-render-cap", "World", 6, 2, 20, 1, true);

    // ── Camera ─────────────────────────────────────────────────────────
    // Zoom semantics (2026-08-01 user spec): 1.0 = the player's normal FOV,
    // 2.0 = 2x zoom, 0.6 = the hard floor (inverse zoom — FOV *wider* than
    // normal). Scroll wheel while holding the zoom key steps +1 per notch
    // (MouseScrollMixin), bounded by zoom-min/zoom-max. GUI slider steps +1.
    public static final Module zoomLevel        = new Module("zoom", "Camera", 2.0, 0.6, 100.0, 1.0, false);
    public static final Module zoomSmoothness   = new Module("zoom-smoothness", "Camera", 8.0, 1.0, 100.0, 0.5, false);
    public static final Module zoomMin          = new Module("zoom-min", "Camera", 0.6, 0.1, 10.0, 0.1, false);
    public static final Module zoomMax          = new Module("zoom-max", "Camera", 100.0, 10.0, 200.0, 1.0, false);

    // ── Optimization ───────────────────────────────────────────────────

    public static final Module blockLodMode            = new Module("block-lod-mode", "Optimization", "OFF",
        "OFF", "LOW", "PVP", "AGGRESSIVE");
    public static final Module blockModelOptimization  = new Module("block-model-optimization", "Optimization", false);
    public static final Module animationCulling        = new Module("animation-culling", "Optimization", false);
    public static final Module sectionCulling          = new Module("section-culling", "Optimization", false);
    public static final Module debugRenderKill         = new Module("debug-render-kill", "Optimization", false);
    public static final Module shaderRemoval           = new Module("shader-removal", "Optimization", false);
    public static final Module debugRenderSkip         = new Module("debug-render-skip", "Optimization", false);
    public static final Module dynamicUboPrealloc       = new Module("dynamic-ubo-prealloc", "Optimization", false);
    public static final Module lightingKillTarget       = new Module("lighting-cull", "Optimization", false);
    public static final Module noErrorGlContext         = new Module("gl-no-error-context", "Optimization", false);
    public static final Module packIconScaling          = new Module("pack-icon-scaling", "Optimization", false);
    public static final Module itemCullingDistance     = new Module("item-culling-distance", "Optimization", 40.0, 5.0, 200.0, 1.0, false);
    public static final Module entityCullingDistance   = new Module("entity-culling-distance", "Optimization", 128.0, 5.0, 500.0, 1.0, false);

    // ── Misc ───────────────────────────────────────────────────────────

    public static final Module disableMouseWheel = new Module("disable-mouse-wheel", "Misc", false);
    public static final Module ghostBlock        = new Module("ghost-block", "Misc", false);

    // ── Console ────────────────────────────────────────────────────────

    public static final Module consoleFps = new Module("console-fps", "Sparrow", 60, 5, 240, 1, true);

    /**
     * Force-register every module. Calling this static method triggers this
     * class's static initializer, which constructs all Module fields and
     * registers them in ModuleManager. Used by ModuleManager.load() so the
     * registry is guaranteed populated even if load() is the first touch of
     * the module system (deferred-init contract). Obfuscation-safe: this is a
     * plain static method call, not a reflection/string lookup.
     */
    public static void ensureRegistered() { }

    private Modules() {}
}
