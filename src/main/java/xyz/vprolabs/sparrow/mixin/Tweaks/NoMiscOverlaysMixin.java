package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Per-overlay replacement for the old NoNauseaMixin wholesale renderMiscOverlays cancel.
// Discriminators verified via javap on InGameHud (1.21.11):
//   - renderSpyglassOverlay(DrawContext, float): private method, single call site inside
//     renderMiscOverlays (bytecode offset 98), gated by isFirstPerson && isUsingSpyglass.
//   - renderOverlay(DrawContext, Identifier, float): private method, exactly two call sites,
//     both inside renderMiscOverlays (offsets 206 and 231). The camera-overlay call passes
//     equipable cameraOverlay identifier transformed by method_65023 into "textures/misc/
//     pumpkinblur.png" (Items registry constant pool: cameraOverlay "misc/pumpkinblur").
//     The other call passes POWDER_SNOW_OUTLINE = "textures/misc/powder_snow_outline.png"
//     (verified in InGameHud static initializer). Dispatch on the path substring.
//   - renderPortalOverlay(DrawContext, float) and renderNauseaOverlay(DrawContext, float):
//     private methods, one call site each inside renderMiscOverlays (offsets 280 and 334);
//     the portal draw runs while nauseaIntensity > 0, the wobble draw while the effect
//     fades. Both are the nausea visuals, so both answer to Modules.noNausea.
// Each handler gates on the composite master (Modules.noMiscOverlays) AND its child:
// master off = vanilla rendering for every overlay (old saved configs stay correct since
// children default true). Rejected: cancelling renderMiscOverlays at HEAD per overlay,
// because the overlays share that method and there is no per-overlay branch point before
// the draws; method-level and call-site discriminators are the only stable ones.
@Mixin(InGameHud.class)
public class NoMiscOverlaysMixin {

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void onOverlayTexture(DrawContext context, Identifier texture, float alpha, CallbackInfo ci) {
        String path = texture.getPath();
        if (path.contains("pumpkinblur")) {
            if (!Modules.noMiscOverlays.isEnabled() || !Modules.noPumpkin.isEnabled()) return;
            ci.cancel();
        } else if (path.contains("powder_snow_outline")) {
            if (!Modules.noMiscOverlays.isEnabled() || !Modules.noPowderSnow.isEnabled()) return;
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onPortalOverlay(DrawContext context, float intensity, CallbackInfo ci) {
        if (!Modules.noMiscOverlays.isEnabled() || !Modules.noNausea.isEnabled()) return;
        ci.cancel();
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void onNauseaWobble(DrawContext context, float intensity, CallbackInfo ci) {
        if (!Modules.noMiscOverlays.isEnabled() || !Modules.noNausea.isEnabled()) return;
        ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        if (!Modules.noMiscOverlays.isEnabled() || !Modules.noSpyglass.isEnabled()) return;
        ci.cancel();
    }
}
