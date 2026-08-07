package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.mixin.Utils.DebugRendererAccessor;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererSkipMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skipWhenEmpty(Frustum frustum, double camX, double camY, double camZ, float tickDelta, CallbackInfo ci) {
            // GATED (2026-08-02): off by default so the vanilla debug renderer
            // always behaves as expected; only skips the whole render pass when
            // the mixin's module is enabled AND there are no renderers anyway.
            if (!Modules.debugRenderSkip.isEnabled()) return;
            if (((DebugRendererAccessor)(Object)this).getRenderers().isEmpty()) {
                ci.cancel();
            }
    }
}
