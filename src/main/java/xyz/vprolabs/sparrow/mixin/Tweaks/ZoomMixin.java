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
            boolean isPressed = SparrowMod.ZOOM_KEY.isPressed();
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
