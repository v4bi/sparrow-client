package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class FloatingItemScaleMixin {
@Inject(method = "renderFloatingItem", at = @At("HEAD"))
    private void sparrow_scaleFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue commandQueue, CallbackInfo ci) {
    if (Modules.smallTotem.isEnabled()) {
                float s = 0.5f;
                matrices.scale(s, s, s);
            }
}

}
