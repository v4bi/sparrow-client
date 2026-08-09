package xyz.vprolabs.sparrow.state;

import xyz.vprolabs.sparrow.module.Modules;

/**
 * Adaptive render resolution: shrink the internal framebuffer when the game
 * can't hold 60 FPS, restore it when headroom returns.
 *
 * <p>Seam (verified 2026-08-09 via javap): the frame graph sizes ALL its
 * intermediate targets (translucent, particles, weather, clouds, ...) from
 * {@code client.getFramebuffer().textureWidth/Height} at graph-build time in
 * {@code WorldRenderer.render} (SimpleFramebufferFactory(textureWidth,
 * textureHeight, ...)), and the "main" target IS the client framebuffer.
 * {@code MinecraftClient.onResolutionChanged} is the only resize path (besides
 * the 4096x4096 panorama captures, which are untouched). So scaling the
 * framebuffer scales the entire render, and the present path stretches it
 * back to the window - exactly like a render-scale setting.
 *
 * <p>Decision logic: exponential moving average of frame time, drop one
 * ladder step when the EMA exceeds the 60 FPS budget (~20ms) sustained for
 * ~0.5s, restore one step when the EMA drops below ~17.5ms (just above the
 * vsync 60 Hz quantum, so vsync-quantized frames can restore) sustained for
 * ~1.5s. Hysteresis + time gates prevent oscillation and texture-realloc
 * churn. Scale ladder: 1.0 / 0.83 / 0.66 / 0.5, filtered by
 * Modules.resolutionMin.
 */
public final class AdaptiveResolutionState {

    // FRAME BUDGET: 20ms sustained = drop a step.
    // Source: 60 FPS target = 16.67ms; +20% headroom so a healthy 50-55 FPS
    // game does NOT downscale (stutter-free bound is ~40 FPS per G1 tuning
    // lessons, but the USER-visible target here is a locked 60).
    // Too LOW (e.g. 16.67): any 1-frame hiccup on a low-end iGPU (UHD 620)
    // triggers downscales constantly, causing visible resolution flicker.
    // Too HIGH (e.g. 33ms): the game sits at 30 FPS looking terrible while
    // the GPU has spare capacity to upscale again.
    // Tolerance: EMA smoothing (alpha 0.05) absorbs single hiccups; the
    // sustained-frame gate absorbs transients (chunk loads). The window
    // between 20ms drop and 12.5ms restore is wide enough to be oscillation-free.
    private static final double FRAME_BUDGET_NS = 20_000_000.0; // 50 FPS floor

    // RESTORE THRESHOLD: 17.5ms = ~57 FPS capability.
    // Source: the frame interval measured at MinecraftClient.render HEAD is
    // the PRESENTED frame time. Under vsync (Window.enableVsync), a capable
    // GPU quantizes to exactly the 60 FPS vblank quantum (16.67ms), so a
    // restore threshold BELOW the quantum (e.g. the old 12.5ms = 80 FPS) can
    // never fire and the game stays downscaled forever. 17.5ms sits above the
    // quantum with 2.5ms of margin below the 20ms drop band.
    // Too LOW (< 16.67): vsync-quantized frames never restore (stuck scaled).
    // Too HIGH (>= 20ms): restores while the GPU is still over budget → the
    // very next drop band re-triggers within a second (visible flicker).
    // Tolerance: the 20ms drop / 17.5ms restore band is 2.5ms wide; EMA
    // smoothing + the 90-frame sustained gate absorb transients. Margin above
    // the quantum assumes vsync drifts <= 0.8ms (typical monitor tolerance).
    private static final double RESTORE_THRESHOLD_NS = 17_500_000.0; // ~57 FPS

    // EMA smoothing: alpha 0.05 per frame = ~20 frames time constant (~0.33s).
    // Too HIGH: reacts to single-frame spikes (chunk load stutters) → flicker.
    // Too LOW: takes seconds to notice a sustained GPU bottleneck → long
    // stretches of unplayable FPS before the downscale lands.
    private static final double EMA_ALPHA = 0.05;

    // Sustained-frame gates: how many consecutive frames past the threshold
    // before acting. Drop 30 frames (~0.5s at 60 FPS), restore 90 frames
    // (~1.5s). Combined with the 20ms/12.5ms band this is the hysteresis.
    private static final int DROP_FRAMES = 30;
    private static final int RESTORE_FRAMES = 90;

    // Ladder steps. 0.83 = 5/6 (mild, first step down), 0.66 = 2/3 (the
    // default floor), 0.5 = half (absolute minimum, only if user sets
    // resolution-min that low). Derived from classic render-scale ratios
    // (0.5/0.667/0.75/1.0 used by OptiFine-style implementations).
    private static final double[] LADDER = {1.0, 0.83, 0.66, 0.5};

    private static double emaNs = 0.0;
    private static int overFrames = 0;
    private static int underFrames = 0;
    private static int ladderIndex = 0;
    private static boolean hasSample = false;
    private static boolean enabled = false;

    private AdaptiveResolutionState() {
    }

    /**
     * Call once per frame from the render thread with the measured frame
     * interval. Returns true when the scale CHANGED this frame (caller must
     * trigger a framebuffer resize).
     *
     * <p>Must be called every frame while the module is enabled; when the
     * module is disabled the mixin skips this entirely and scale() returns
     * 1.0 unconditionally.
     */
    public static boolean update(double frameIntervalNs) {
        if (!hasSample) {
            emaNs = frameIntervalNs;
            hasSample = true;
            return false;
        }
        emaNs = emaNs + EMA_ALPHA * (frameIntervalNs - emaNs);

        double minScale = Modules.resolutionMin.floatValue();
        double current = LADDER[ladderIndex];

        if (emaNs > FRAME_BUDGET_NS && current > minScale) {
            overFrames++;
            underFrames = 0;
            if (overFrames >= DROP_FRAMES) {
                if (ladderIndex < LADDER.length - 1
                        && LADDER[ladderIndex + 1] >= minScale) {
                    ladderIndex++;
                    overFrames = 0;
                    return true;
                }
                overFrames = 0;
            }
        } else if (emaNs < RESTORE_THRESHOLD_NS && current < 1.0) {
            underFrames++;
            overFrames = 0;
            if (underFrames >= RESTORE_FRAMES) {
                if (ladderIndex > 0) {
                    ladderIndex--;
                    underFrames = 0;
                    return true;
                }
                underFrames = 0;
            }
        } else {
            overFrames = 0;
            underFrames = 0;
        }
        return false;
    }

    /**
     * Current render scale. Returns 1.0 when the module is disabled or the
     * state has never sampled a frame.
     */
    public static double scale() {
        if (!enabled) return 1.0;
        if (!hasSample) return 1.0;
        return LADDER[ladderIndex];
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            hasSample = false;
            emaNs = 0.0;
            ladderIndex = 0;
            overFrames = 0;
            underFrames = 0;
        }
    }
}
