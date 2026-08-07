package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteAtlasTexture.class)
public class AnimationCullingMixin {
    @Unique private boolean sparrow_usedThisFrame = false;
    @Unique private boolean sparrow_animLogged = false;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sparrow_skipInactiveAtlas(CallbackInfo ci) {
        // Opt-in (2026-08-02): this mixin culled sprite-animation ticks when the
        // atlas wasn't touched — but `getSprite` is only the common entry point,
        // and static screens still require the atomizer to run. Gate it behind
        // the module (default OFF) so normal play never sees frozen animations.
        if (!Modules.animationCulling.isEnabled()) return;
        if (!sparrow_usedThisFrame) {
            ci.cancel();
            return;
        }
        sparrow_usedThisFrame = false;
        if (!sparrow_animLogged) {
            sparrow_animLogged = true;
            SparrowLogger.debug("AnimationCullingMixin: active, culling unused sprite atlas ticks");
        }
    }

    @Inject(method = "getSprite", at = @At("HEAD"))
    private void sparrow_markAtlasUsed(Identifier id, CallbackInfoReturnable<Sprite> cir) {
        if (Modules.animationCulling.isEnabled()) sparrow_usedThisFrame = true;
    }

    @Inject(method = "getMissingSprite", at = @At("HEAD"))
    private void sparrow_markAtlasUsedMissing(CallbackInfoReturnable<Sprite> cir) {
        if (Modules.animationCulling.isEnabled()) sparrow_usedThisFrame = true;
    }
}