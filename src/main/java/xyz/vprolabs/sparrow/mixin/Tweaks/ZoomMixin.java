package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.SparrowMod;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.tweaks.SparrowZoomState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class ZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void sparrow_applyZoom(Camera camera, float tickDelta, boolean useSetting, CallbackInfoReturnable<Float> cir) {
            // Master toggle (2026-08-09): the `zoom` composite parent is a
            // real ON/OFF switch — OFF = vanilla FOV, the key does nothing.
            // Keeps the GUI tile's ON|OFF honest instead of a display-only
            // pill. Persisted by ModuleManager like any toggle.
            if (!Modules.zoom.isEnabled()) return;
            boolean isPressed = SparrowMod.ZOOM_KEY.isPressed();
            // Reset Zoom On Activation (zoom-reset toggle): a fresh key press
            // wipes the scroll-wheel target back to the configured zoom level.
            // Edge detection via wasKeyHeld instead of KeyBinding.wasPressed()
            // so no other key-state consumer is stolen from. Toggle OFF keeps
            // the legacy behavior (scrolled level persists across activations).
            // 2026-08-09: the reset target is the dedicated zoom-reset-value
            // module (user spec: "reset to which level, e.g. 2.0") instead of
            // the base zoom level, so the two can differ.
            if (isPressed && !SparrowZoomState.wasKeyHeld && Modules.zoomReset.isEnabled()) {
                SparrowZoomState.targetZoom = Modules.zoomResetValue.value();
            }
            SparrowZoomState.wasKeyHeld = isPressed;
            double target = isPressed ? SparrowZoomState.targetZoom : 1.0;
            double step = 1.0 / Math.max(0.1, Modules.zoomSmoothness.floatValue());

            double diff = target - SparrowZoomState.currentZoom;
            if (Math.abs(diff) <= step) {
                SparrowZoomState.currentZoom = target;
            } else {
                SparrowZoomState.currentZoom += Math.signum(diff) * step;
            }
            if (SparrowZoomState.currentZoom < 0.01) {
                SparrowZoomState.currentZoom = 0.01;
            }

            float fov = cir.getReturnValue();
            cir.setReturnValue(fov / (float) SparrowZoomState.currentZoom);
    }
}
