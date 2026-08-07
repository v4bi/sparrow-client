package xyz.vprolabs.sparrow.mixin.NotVanilla;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class AlwaysDayMixin {

    @Inject(method = "getSkyDarkness", at = @At("HEAD"), cancellable = true)
    private void onGetSkyDarkness(float tickDelta, CallbackInfoReturnable<Float> cir) {
            if (!Modules.alwaysDay.isEnabled()) return;
            cir.setReturnValue(0.0f);
    }
}
