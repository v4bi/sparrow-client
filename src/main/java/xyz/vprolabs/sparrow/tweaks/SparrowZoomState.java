package xyz.vprolabs.sparrow.tweaks;

/**
 * Live zoom state shared between ZoomMixin and MouseScrollMixin.
 * targetZoom starts at the config value but is adjusted at runtime via scroll.
 */
public class SparrowZoomState {
    public static double currentZoom = 1.0;
    // Inside the `zoom` camera module default (Modules.java). Keep in sync:
    // ModuleHooks.onChanged("zoom") mirrors the live module value here, so a
    // GUI/console change or config load re-picks the saved zoom on the next
    // FOV tick. Previously hardcoded 4.0, which silently ignored both the
    // saved value and the default until the scroll wheel was used.
    public static double targetZoom = 2.0;
    // Edge-detection latch for ZoomMixin: tracks whether the zoom key was
    // held on the previous FOV tick so the zoom-reset toggle can fire exactly
    // once per fresh key press (isPressed() alone stays true for the whole
    // hold duration).
    public static boolean wasKeyHeld = false;

    private SparrowZoomState() {}
}
