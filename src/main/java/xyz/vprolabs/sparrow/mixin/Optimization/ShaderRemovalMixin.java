package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.ObjectAllocator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostEffectProcessor.class)
public class ShaderRemovalMixin {

    @Unique
    private static boolean sparrow_shaderLogged = false;

    @Inject(method = "render(Lnet/minecraft/client/render/FrameGraphBuilder;IILnet/minecraft/client/gl/PostEffectProcessor$FramebufferSet;)V", at = @At("HEAD"), cancellable = true)
    private void preventShaderRender(FrameGraphBuilder builder, int width, int height, PostEffectProcessor.FramebufferSet framebufferSet, CallbackInfo ci) {
            if (!Modules.shaderRemoval.isEnabled()) return;
            if (!sparrow_shaderLogged) {
                sparrow_shaderLogged = true;
                SparrowLogger.debug("ShaderRemovalMixin: blocking PostEffectProcessor.render(FrameGraphBuilder,...)");
            }
            ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/gl/Framebuffer;Lnet/minecraft/client/util/ObjectAllocator;)V", at = @At("HEAD"), cancellable = true)
    private void preventShaderRender(Framebuffer framebuffer, ObjectAllocator allocator, CallbackInfo ci) {
            if (!Modules.shaderRemoval.isEnabled()) return;
            if (!sparrow_shaderLogged) {
                sparrow_shaderLogged = true;
                SparrowLogger.debug("ShaderRemovalMixin: blocking PostEffectProcessor.render(Framebuffer,...)");
            }
            ci.cancel();
    }
}
