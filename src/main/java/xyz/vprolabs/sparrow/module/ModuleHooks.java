package xyz.vprolabs.sparrow.module;

import xyz.vprolabs.sparrow.tweaks.SparrowGlintLayers;
import xyz.vprolabs.sparrow.tweaks.SparrowZoomState;

/**
 * Side-effects that fire whenever a module's value changes — regardless of
 * whether the change came from the click GUI, the console, or a mixin.
 * Centralising them here guarantees every entry point behaves identically.
 *
 * Matches the old per-command wiring in FeatureRegistry; keep in sync if a
 * new module ever needs a side effect.
 */
public final class ModuleHooks {
    private ModuleHooks() {}

    public static void onChanged(Module m) {
        switch (m.id()) {
        case "custom-glint", "glint-r", "glint-g", "glint-b" -> {
            // Enchant glint color is baked into a cached layer list; a change
            // must rebuild it or the new color only applies on resource reload.
            // refresh() is safe pre-init: it no-ops when the client or texture
            // manager is not available yet (SparrowGlintLayers itself guards).
            SparrowGlintLayers.refresh();
        }
        case "zoom-level" -> {
            // ZoomMixin uses SparrowZoomState.targetZoom as its target while
            // the zoom key is held. Console/GUI/load changes to the zoom level
            // set only Modules.zoomLevel, leaving the state stuck on whatever
            // the scroll wheel last wrote. Mirror every change into the state
            // so saved and adjusted values actually take effect without
            // requiring a wheel notch. (2026-08-09: this case used to read
            // "zoom" — the leaf id — which the 2026-08-09 composite rename
            // made the PARENT id; the parent's setEnabled would then zero
            // targetZoom through value()==0. The case must follow the leaf.)
            SparrowZoomState.targetZoom = m.value();
        }
        case "crosshair-color" -> {
            // Console contract (FeatureRegistry.setCrosshairColor): setting
            // a color forces the heart crosshair so the color is visible.
            // Guard with isLoaded(): during config load applyToModules calls
            // setStringValue and onChanged fires with loaded yet false, which
            // would clobber a saved crosshair style (plus/off/x) with "heart"
            // every launch. The force applies only to live runtime changes.
            if (ModuleManager.isLoaded()) {
                Module cross = Modules.crosshairMode;
                if (!"heart".equals(cross.stringValue())) {
                    cross.setStringValue("heart");
                }
            }
        }
        default -> { }
        }
        ModuleManager.requestSave();
    }
}
