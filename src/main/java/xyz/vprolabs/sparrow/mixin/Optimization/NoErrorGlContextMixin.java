package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.util.Window;

@Mixin(Window.class)
public class NoErrorGlContextMixin {

    @Unique
    private static boolean sparrow_noErrorLogged = false;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private void sparrow_setNoErrorHint(CallbackInfo ci) {
        if (!Modules.noErrorGlContext.isEnabled()) return;
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", shift = At.Shift.AFTER))
    private void sparrow_checkNoErrorHandle(CallbackInfo ci) {
        if (!sparrow_noErrorLogged) {
            sparrow_noErrorLogged = true;
            SparrowLogger.debug("NoErrorGlContextMixin: GL_NO_ERROR context requested");
        }
    }
}
