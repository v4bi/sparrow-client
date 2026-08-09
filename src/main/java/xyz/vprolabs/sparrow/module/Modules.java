package xyz.vprolabs.sparrow.module;

/**
 * Static registry of ALL modules — the single source of truth, mirroring the
 * old ConfigRegister entries one-for-one (same field names, same ids, same
 * categories, same defaults) so consumer rewiring stays mechanical.
 *
 * Slider bounds below match the validation ranges the console commands used:
 * view-model floats were unbounded, culling/nether/console entries had
 * explicit min-max, glint channels are 0-255.
 *
 * Every module carries a withDescription(...) hover text shown in the click
 * GUI tooltip — the user-mandated rule (2026-08-09): everything toggleable
 * or unclear gets one.
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
        "terminal", "Terminal [LEGACY]")
        .withDescription("Which menu the Right Shift key opens: the Sparrow Menu (click GUI) or the legacy terminal.");
    public static final Module guiFps = new Module("gui-fps", "Sparrow", 60, 5, 240, 1, true)
        .withDescription("Frame cap while the Sparrow Menu is open. Keeps the GUI from running uncapped; lower = less GPU load.");
    // GUI scale (2026-08-08 user spec, range tightened 0.5-1.5 same day):
    // scales the GUI's ELEMENTS (tabs/rows/icons/margins) — 1.0 = v3 default.
    // 2026-08-09 correction: the panel itself is ALWAYS fullscreen-minus-
    // margin; the old centered float-window below 1.0 was removed.
    // Steps of 0.05 so the slider stays usable.
    public static final Module guiScale = new Module("gui-scale", "Sparrow", 1.0, 0.5, 1.5, 0.05, false)
        .withDescription("Scales the menu's elements (tabs, rows, icons). The panel always fills the screen.");
    // Theme (2026-08-10 user spec): palette for the Sparrow Menu. Options use
    // the Name/DisplayName pair — terminal accepts "default"/"hanami"
    // (space-free), the GUI cycle row shows "Default"/"Hanami". "Default"
    // keeps the current Midnight Sakura look; "Hanami" is the light Japanese
    // palette (Theme.apply()). Applies instantly from any entry point via
    // ModuleHooks; the click GUI and HUD editor read Theme.* per frame.
    public static final Module theme = Module.withLabels("theme", "Sparrow", "default",
        "default", "Default",
        "hanami", "Hanami")
        .withDescription("Sparrow Menu color theme: Default (Midnight Sakura, dark) or Hanami (light Japanese).");

    // ── Visual ─────────────────────────────────────────────────────────

    public static final Module smallTotem       = new Module("small-totem", "Visual", false)
        .withDescription("Small totem pop: shrinks the totem animation and moves it to the corner of the screen.");
    public static final Module noTotemPop       = new Module("no-totem-pop", "Visual", false)
        .withDescription("Removes the totem pop animation entirely — nothing blocks your vision when you pop.");
    public static final Module oldPotions       = new Module("old-potions", "Visual", false)
        .withDescription("Restores the old pre-1.9 potion colors (the classic saturated look).");
    public static final Module customGlint      = new Module("custom-glint", "Visual", false)
        .withDescription("Replaces the vanilla purple enchantment glint with your RGB color below.");
    // Per-overlay toggles (2026-08-09 user spec: "add config for each overlay
    // an toggle"). no-misc-overlays became a composite: the master toggle
    // keeps the OLD behavior (kill every misc overlay), each child disables
    // ONE overlay. Children default ON so a saved no_misc_overlays=true still
    // removes everything after the split — no behavior change for existing
    // configs. Mixins read master + the specific child.
    public static final Module noNausea        = new Module("no-nausea", "Visual", true)
        .withDescription("Removes the nausea wobble screen overlay.");
    public static final Module noFireOverlay   = new Module("no-fire-overlay", "Visual", true)
        .withDescription("Removes the fire screen overlay.");
    public static final Module noPumpkin       = new Module("no-pumpkin", "Visual", true)
        .withDescription("Removes the pumpkin blur screen overlay.");
    public static final Module noPowderSnow    = new Module("no-powder-snow", "Visual", true)
        .withDescription("Removes the powder snow screen overlay.");
    public static final Module noVignette      = new Module("no-vignette", "Visual", true)
        .withDescription("Removes the damage vignette overlay.");
    public static final Module noSpyglass      = new Module("no-spyglass", "Visual", true)
        .withDescription("Removes the spyglass black border overlay.");
    public static final Module noMiscOverlays   = Module.group("no-misc-overlays", "Visual",
        noNausea, noFireOverlay, noPumpkin, noPowderSnow, noVignette, noSpyglass)
        .withDescription("Removes screen overlays (nausea, fire, pumpkin, powder snow, vignette, spyglass) — each overlay toggles individually.");
    public static final Module removeShadows    = new Module("remove-shadows", "Visual", false)
        .withDescription("Removes entity and block shadows.");
    public static final Module storageTooltip   = new Module("storage-tooltip", "Visual", false)
        .withDescription("Shows the contents of chests, barrels, hoppers and other storage you're looking at.");
    public static final Module coords           = new Module("coords", "Visual", false)
        .withDescription("Shows your XYZ coordinates on the HUD (bottom-left).");
    public static final Module ping             = new Module("ping", "Visual", false)
        .withDescription("Shows your latency to the server on the HUD (top-right).");
    public static final Module desync           = new Module("desync", "Visual", false)
        .withDescription("Shows a temporary warning when the server rubber-bands you (desync detected).");
    public static final Module hitmarker        = new Module("hitmarker", "Visual", false)
        .withDescription("Shows a hit marker when your attack connects with a player.");
    public static final Module shieldStatus     = new Module("shield-status", "Visual", false)
        .withDescription("Shows your shield's charge and cooldown status on the HUD.");
    public static final Module playerHitEnabled  = new Module("player-hit-toggle", "Visual", false)
        .withDescription("Enables the player-hit indicator (colors players you can hit).");
    public static final Module playerHitType    = new Module("player-hit-type", "Visual", "hit",
        "hit", "abletohit")
        .withDescription("When the player-hit indicator shows: only when you're able to hit, or always.");
    public static final Module playerHitColor   = new Module("player-hit-color", "Visual", "ff0000")
        .withDescription("Hex color (RRGGBB) of the player-hit indicator.");
    // Player-hit panel (2026-08-02): one GUI row opening a popup with the
    // toggle + type + color. The composite id reuses "player-hit"; the old
    // leaf toggle moved to the "player-hit-toggle" child for config migration
    // (legacy player_hit boolean applies to it via ModuleManager).
    public static final Module playerHit        = Module.group("player-hit", "Visual",
        playerHitEnabled, playerHitType, playerHitColor)
        .withDescription("Player-hit indicator: colors players you can hit. Toggle, when it shows, and color.");

    public static final Module crosshairMode    = new Module("crosshair-mode", "Visual", "off",
        "off", "plus", "heart", "tiny", "dot", "x", "clover")
        .withDescription("Custom crosshair style: off, plus, heart, tiny, dot, x, clover.");
    public static final Module crosshairColor   = new Module("crosshair-color", "Visual", "ffffff")
        .withColor()
        .withDescription("Hex color (RRGGBB) of the custom crosshair.");
    // Crosshair panel (2026-08-02): one GUI row (mode + color). The old
    // `crosshair` leaf id becomes the composite parent; mode moved to the
    // "crosshair-mode" child so the saved legacy `crosshair` string value
    // migrates onto it (ModuleManager parent-key fallback).
    public static final Module crosshair        = Module.group("crosshair", "Visual",
        crosshairMode, crosshairColor)
        .withDescription("Custom crosshair: style and color.");

    // ── View Model: ONE module in the GUI (popup: X/Y/Z/Scale). The four
    // children stay registered standalone — console commands (view-x, ...),
    // config keys and ViewModelMixin keep working unchanged.
    public static final Module viewModelX       = new Module("view-x", "Visual", 0.0, -10.0, 10.0, 0.05, false)
        .withDescription("Moves the held item left/right in first person (negative = left).");
    public static final Module viewModelY       = new Module("view-y", "Visual", 0.0, -10.0, 10.0, 0.05, false)
        .withDescription("Moves the held item up/down in first person (negative = down).");
    public static final Module viewModelZ       = new Module("view-z", "Visual", 0.0, -10.0, 10.0, 0.05, false)
        .withDescription("Moves the held item forward/back in first person (negative = closer).");
    public static final Module viewModelSize    = new Module("view-size", "Visual", 1.0, 0.05, 5.0, 0.05, false)
        .withDescription("Scales the held item in first person (1.0 = normal size).");
    public static final Module viewModel        = Module.group("view-model", "Visual",
        viewModelX, viewModelY, viewModelZ, viewModelSize)
        .withDescription("First-person view model: position (X/Y/Z) and size of your held item.");

    public static final Module utilityScale     = new Module("utility-scale", "Visual", 0.65, 0.1, 2.0, 0.05, false)
        .withDescription("Extra scale for utility items held in the hand (torches etc.), applied on top of view-size.");
    public static final Module glintR           = new Module("glint-r", "Visual", 0, 0, 255, 1, true)
        .withDescription("Red channel (0-255) of the custom enchantment glint color.");
    public static final Module glintG           = new Module("glint-g", "Visual", 255, 0, 255, 1, true)
        .withDescription("Green channel (0-255) of the custom enchantment glint color.");
    public static final Module glintB           = new Module("glint-b", "Visual", 0, 0, 255, 1, true)
        .withDescription("Blue channel (0-255) of the custom enchantment glint color.");
    // Glint panel (2026-08-02): custom-glint (toggle) + the three RGB channels
    // collapse to ONE GUI row. The leaf ids stay registered standalone, so
    // console commands (glint-r/...), config keys and SparrowGlintLayers all
    // keep working. Parent id "glint" is new and collides with nothing.
    public static final Module glint            = Module.group("glint", "Visual",
        customGlint, glintR, glintG, glintB)
        .withColor()
        .withDescription("Enchantment glint: enable custom color and set its RGB channels.");
    // Fire Timer: ONE module in the GUI (popup: toggle + position). The
    // toggle lives under its own id (fire-timer-enabled) so the parent id
    // "fire-timer" can be the composite; ModuleManager migrates the old
    // fire_timer boolean onto the child. FireTimerMixin/Renderer read the
    // child through Modules.fireTimer.child("fire-timer-enabled").
    public static final Module fireTimerEnabled = new Module("fire-timer-enabled", "Visual", false)
        .withDescription("Shows a bar with the time until your attack cooldown resets.");
    public static final Module fireTimerPos     = new Module("fire-timer-pos", "Visual", "BOTTOM_CENTER",
        "TOP_LEFT", "TOP_RIGHT", "BOTTOM_CENTER")
        .withDescription("Where the fire timer appears on the HUD.");
    public static final Module fireTimer        = Module.group("fire-timer", "Visual",
        fireTimerEnabled, fireTimerPos)
        .withDescription("Fire timer: attack-cooldown bar and its HUD position.");
    public static final Module particleMode     = new Module("particles", "Visual", "off",
        "off", "minimal", "on")
        .withDescription("Particle amount: off = none, minimal = reduced, on = vanilla.");

    // ── World ──────────────────────────────────────────────────────────

    public static final Module fullbright        = new Module("fullbright", "Visual", false)
        .withDescription("Maximum brightness — everything is fully lit, day and night.");
    public static final Module noMiningFatigue   = new Module("no-mining-fatigue", "Visual", false)
        .withDescription("Removes the Mining Fatigue mining speed penalty.");
    public static final Module alwaysDay         = new Module("always-day", "World", false)
        .withDescription("Locks the sky to day time (client-side).");
    public static final Module disableEntityAI   = new Module("disable-entity-ai", "World", false)
        .withDescription("Disables entity AI and goals — mobs stand still (big CPU win).");
    public static final Module netherRenderCap   = new Module("nether-render-cap", "World", 6, 2, 20, 1, true)
        .withDescription("Max chunk render distance in the Nether (lower = more FPS).");

    // ── Camera ─────────────────────────────────────────────────────────
    // Zoom semantics (2026-08-01 user spec): 1.0 = the player's normal FOV,
    // 2.0 = 2x zoom, 0.6 = the hard floor (inverse zoom — FOV *wider* than
    // normal). Scroll wheel while holding the zoom key steps +1 per notch
    // (MouseScrollMixin), bounded by zoom-min/zoom-max. GUI slider steps +1.
    public static final Module zoomLevel        = new Module("zoom-level", "Misc", 2.0, 0.6, 100.0, 1.0, false)
        .withDescription("Zoom strength: 2.0 = 2x zoom, 1.0 = normal FOV, below 1.0 widens the FOV (inverse zoom).");
    public static final Module zoomSmoothness   = new Module("zoom-smoothness", "Misc", 8.0, 1.0, 100.0, 0.5, false)
        .withDescription("How smoothly the zoom animates between levels (higher = smoother).");
    public static final Module zoomMin          = new Module("zoom-min", "Misc", 0.6, 0.1, 10.0, 0.1, false)
        .withDescription("Lowest zoom level the scroll wheel can reach while zooming.");
    public static final Module zoomMax          = new Module("zoom-max", "Misc", 100.0, 10.0, 200.0, 1.0, false)
        .withDescription("Highest zoom level the scroll wheel can reach while zooming.");
    // Reset Zoom On Activation (2026-08-08 user spec): when ON, every fresh
    // press of the zoom key wipes the scroll-wheel adjustment (targetZoom)
    // back to the configured `zoom` level. OFF keeps the old behavior where
    // the scrolled level persists across zoom re-activations. Toggle-only,
    // so it auto-registers the `sparrow zoom-reset` console command.
    public static final Module zoomReset        = new Module("zoom-reset", "Misc", false)
        .withDescription("Reset the scrolled zoom level back to the configured one every time you press the zoom key.");
    // The exact level zoom resets to when zoom-reset is ON (2026-08-09 user
    // spec): a separate value so the reset target can differ from the base
    // zoom level. Only shown in the GUI while zoom-reset is enabled
    // (withVisibleWhen) — a dead setting while its trigger is off.
    public static final Module zoomResetValue   = new Module("zoom-reset-value", "Misc", 2.0, 0.6, 100.0, 1.0, false)
        .withDescription("The exact zoom level the zoom key snaps back to when Zoom Reset is on (e.g. 2.0 = always reset to 2x).")
        .withVisibleWhen("zoom-reset");
    // Zoom: ONE module in the GUI (2026-08-09 user request), popup with
    // level/smoothness/min/max/reset. The old `zoom` leaf id becomes the
    // composite parent; the level moved to the "zoom-level" child so a saved
    // legacy "zoom" number migrates onto it via ModuleManager's composite
    // parent-key fallback (first type-compatible child missing its own key).
    // Field names (zoomLevel etc.) are unchanged, so ZoomMixin, MouseScrollMixin
    // and the hardcoded `sparrow zoom` command keep working as-is.
    // The parent is a MASTER TOGGLE (default ON): the GUI tile shows ON/OFF and
    // ZoomMixin is gated on it, so "Zoom" really turns off. Composites persist
    // their own enabled key since ModuleManager save change 2026-08-09.
    public static final Module zoom             = Module.group("zoom", "Misc", true,
        zoomLevel, zoomSmoothness, zoomMin, zoomMax, zoomReset, zoomResetValue)
        .withDescription("Smooth zoom while holding the zoom key; scroll wheel adjusts the level. Click to toggle, right-click for settings. All zoom settings in one place.");

    // ── Optimization ───────────────────────────────────────────────────

    public static final Module blockLodMode            = new Module("block-lod-mode", "Optimization", "OFF",
        "OFF", "LOW", "PVP", "AGGRESSIVE")
        .withDescription("Distance-based block LOD: OFF/LOW/PVP/AGGRESSIVE. Skips translucent sorting and culls distant chunk rendering earlier (higher = more aggressive).");
    public static final Module blockModelOptimization  = new Module("block-model-optimization", "Optimization", false)
        .withDescription("Culls hidden block faces early (faster chunk meshing).");
    public static final Module animationCulling        = new Module("animation-culling", "Optimization", false)
        .withDescription("Skips animations for off-screen or distant entities and blocks.");
    public static final Module sectionCulling          = new Module("section-culling", "Optimization", false)
        .withDescription("Culls block sections you can't see (faster chunk rendering).");
    public static final Module debugRenderKill         = new Module("debug-render-kill", "Optimization", false)
        .withDescription("Disables the per-frame F3 debug rendering (wireframes).");
    public static final Module shaderRemoval           = new Module("shader-removal", "Optimization", false)
        .withDescription("Removes expensive shader passes (outline, post-processing).");
    public static final Module debugRenderSkip         = new Module("debug-render-skip", "Optimization", false)
        .withDescription("Skips the entire F3 debug renderer (entity paths, chunk bounds).");
    public static final Module dynamicUboPrealloc       = new Module("dynamic-ubo-prealloc", "Optimization", false)
        .withDescription("Pre-allocates dynamic uniform buffers (fewer per-frame allocations).");
    public static final Module lightingKillTarget       = new Module("lighting-cull", "Optimization", false)
        .withDescription("Skips light updates when blocks change — faster updates, no light recalc.");
    public static final Module noErrorGlContext         = new Module("gl-no-error-context", "Optimization", false)
        .withDescription("Requests an OpenGL context without error checking (small FPS gain).");
    public static final Module packIconScaling          = new Module("pack-icon-scaling", "Optimization", false)
        .withDescription("Scales down large resource pack icons (faster GUI, less memory).");
    public static final Module itemCullingDistance     = new Module("item-culling-distance", "Optimization", 40.0, 5.0, 200.0, 1.0, false)
        .withDescription("Distance beyond which dropped items are not rendered (lower = more FPS).");
    public static final Module entityCullingDistance   = new Module("entity-culling-distance", "Optimization", 128.0, 5.0, 500.0, 1.0, false)
        .withDescription("Distance beyond which entities are not rendered (lower = more FPS).");
    // Particles beyond this distance are not ticked (markDead, CPU win) and
    // not submitted for rendering (GPU win). Frustum alone only kills ~1/12
    // of particles at normal FOV; distance is the real cull. 96 = 1.5 chunks:
    // far fireworks remain visible, field particles die instantly.
    public static final Module particleCullDistance    = new Module("particle-cull-distance", "Optimization", 96.0, 5.0, 200.0, 1.0, false)
        .withDescription("Distance beyond which particles are culled — not ticked or rendered (lower = more FPS).");
    // Adaptive render resolution: shrink the internal framebuffer when the
    // game can't hold 60 FPS, restore it when headroom returns. The HUD
    // renders inside the scaled framebuffer (softens at low scales), so
    // resolution-min floors the ladder: 0.83 is barely noticeable, 0.66 is
    // the default, 0.5 is the absolute minimum. Ladder steps below the
    // configured minimum are never used.
    public static final Module adaptiveResolution      = new Module("adaptive-resolution", "Optimization", false)
        .withDescription("Shrinks the render resolution when FPS drops below 60, restores it when FPS recovers. Smoothness for smoothness.");
    public static final Module resolutionMin           = new Module("resolution-min", "Optimization", 0.66, 0.5, 1.0, 0.01, false)
        .withDescription("Lowest render scale adaptive-resolution may drop to: 0.5 = half resolution, 0.83 = barely noticeable, 1.0 = full.");
    // ── Experimental ──────────────────────────────────────────────────
    // Atlas disk cache (2026-08-09, user request). BUGGY-LOCKED 2026-08-10:
    // cache-HIT boots render invisible GUI sprites (atlas dump showed the
    // button region fully transparent even though the blob pixels are
    // correct) and the root cause is still unknown. Locked = default OFF,
    // impossible to enable from GUI/console/config, forced off on every
    // startup by ModuleManager (withLocked()). The mixin gates on
    // isEnabled() so it is now dead code until the feature is fixed.
    public static final Module atlasCache              = new Module("atlas-cache", "Experimental", false).withLocked()
        .withDescription("BUGGY: renders invisible sprites on cache-hit boots. Locked until fixed.");

    // ── Misc ───────────────────────────────────────────────────────────

    public static final Module disableMouseWheel = new Module("disable-mouse-wheel", "Misc", false)
        .withDescription("Disables the scroll wheel entirely (no hotbar switching, no zoom stepping).");
    public static final Module ghostBlock        = new Module("ghost-block", "Misc", false)
        .withDescription("Detects ghost blocks (client/server desync after break or place) and fixes them.");

    // ── Misc: Death Sound (composite) ─────────────────────────────────
    // Return-by-death sound (2026-08-09 user spec): plays when the CLIENT
    // player dies. ONE module in the GUI: the master toggle is the ON|OFF
    // gate, the popup holds the variant (Short|Full) and volume (1-100%).
    // Legacy config keys survive: the old "death_sound" boolean applies to
    // the master via ModuleManager's parent-key fallback, and the old
    // "death_sound_variant" string keeps its own key on the child (child id
    // unchanged). Audio category removed 2026-08-09 — lives in Misc now.
    public static final Module deathSoundVariant = Module.withLabels("death-sound-variant", "Misc", "short",
        "short", "Short", "full", "Full")
        .withDescription("Death sound variant: Short or Full.");
    public static final Module deathSoundVolume  = new Module("death-sound-volume", "Misc", 100.0, 1.0, 100.0, 1.0, false)
        .withDescription("Death sound volume, 1-100%.");
    public static final Module deathSound        = Module.group("death-sound", "Misc", false,
        deathSoundVariant, deathSoundVolume)
        .withDescription("Plays a sound effect when you die (Return by Death). Toggle to enable, right-click for variant and volume.");

    // ── Console ────────────────────────────────────────────────────────

    public static final Module consoleFps = new Module("console-fps", "Sparrow", 60, 5, 240, 1, true)
        .withDescription("Frame cap while the legacy terminal is open.");

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
