package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityShadowRemoverMixin {
    @Inject(method = "getShadowRadius", at = @At("RETURN"), cancellable = true)
    private void sparrowRemoveShadowRadius(EntityRenderState state, CallbackInfoReturnable<Float> cir) {
            if (!Modules.removeShadows.isEnabled()) return;
            cir.setReturnValue(0.0f);
    }

    @Inject(method = "getShadowOpacity", at = @At("RETURN"), cancellable = true)
    private void sparrowRemoveShadowOpacity(EntityRenderState state, CallbackInfoReturnable<Float> cir) {
            if (!Modules.removeShadows.isEnabled()) return;
            cir.setReturnValue(0.0f);
    }

}
