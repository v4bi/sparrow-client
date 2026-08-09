package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vignette is drawn by the private renderVignetteOverlay, called once from
// renderMiscOverlays (javap verified), gated by the vanilla vignette option.
// The always-on cancel below became the per-overlay toggle: master
// (noMiscOverlays) gates the child (noVignette). Injecting at the method HEAD
// is safe: updateVignetteDarkness keeps running, only the draw is skipped.
@Mixin(InGameHud.class)
public class VignetteDisableMixin {

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void skipVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (!Modules.noMiscOverlays.isEnabled() || !Modules.noVignette.isEnabled()) return;
        ci.cancel();
    }
}
