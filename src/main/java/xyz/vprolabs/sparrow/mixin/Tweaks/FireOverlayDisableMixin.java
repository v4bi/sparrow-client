package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fire is NOT drawn in InGameHud.renderMiscOverlays in 1.21.11: it lives in
// InGameOverlayRenderer.renderOverlays, which calls the private static
// renderFireOverlay when the player isOnFire (javap verified). The always-on
// cancel below became the per-overlay toggle: master (noMiscOverlays) gates
// the child (noFireOverlay). Rejected cancelling renderOverlays at HEAD: that
// method also draws the in-wall and underwater overlays, which would vanish
// whenever the player is on fire (e.g. lava + underwater).
@Mixin(InGameOverlayRenderer.class)
public class FireOverlayDisableMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void skipFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        if (!Modules.noMiscOverlays.isEnabled() || !Modules.noFireOverlay.isEnabled()) return;
        ci.cancel();
    }
}
