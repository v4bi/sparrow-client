package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.BufferManager;
import net.minecraft.client.gl.GlCommandEncoder;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.vprolabs.sparrow.mixin.Utils.BufferManagerInvoker;
import xyz.vprolabs.sparrow.state.AdaptiveResolutionState;

/**
 * Present-time stretch: when the client framebuffer is scaled below the
 * window size, expand it across the whole window at present time.
 */
@Mixin(GlCommandEncoder.class)
public class AdaptivePresentMixin {

    // presentTexture (the blit that shows the client framebuffer on screen):
    //   viewport(0,0,texW,texH); disableScissorTest; depthMask(true);
    //   colorMask(all true); setupFramebuffer(temporaryFb2, colorAttachment, 0,0,0)
    //     -> attaches the framebuffer's color texture to a temp fbo; then:
    //   setupBlitFramebuffer(readFb=temporaryFb2, drawFb=0 (default framebuffer),
    //     srcRect=(0,0,texW,texH), dstRect=(0,0,texW,texH), mask=0x4000
    //     (GL_COLOR_BUFFER_BIT), filter=9728 (GL_NEAREST))
    //   -> a 1:1 color blit. With a scaled framebuffer that 1:1 blit would
    //   letterbox the top-left corner of the window and leave the rest black.
    // Rewrite the dst rect to the full window and switch the filter to 9729
    // (GL_LINEAR) so the upscale is smooth instead of a hard nearest-neighbor
    // pixel blow-up. glBlitFramebuffer is not affected by the viewport or
    // scissor set earlier in this method (it takes explicit rects), and the
    // scissor is disabled anyway.
    // Rejected: a full-screen quad shader pass instead of a blit - it needs
    // new pipeline/state setup and adds a draw call on every frame; the blit
    // path is already exact for scale >= 1.0. When the module is disabled
    // (or at full res) this handler is a byte-identical pass-through.
    @Redirect(method = "presentTexture",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/BufferManager;setupBlitFramebuffer(IIIIIIIIIIII)V"))
    private void sparrow_stretchPresent(BufferManager bufferManager, int readFb, int drawFb,
            int srcX0, int srcY0, int srcX1, int srcY1,
            int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        double scale = AdaptiveResolutionState.scale();
        if (scale >= 1.0) {
            ((BufferManagerInvoker) bufferManager).invokeSetupBlitFramebuffer(
                    readFb, drawFb, srcX0, srcY0, srcX1, srcY1,
                    dstX0, dstY0, dstX1, dstY1, mask, filter);
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        ((BufferManagerInvoker) bufferManager).invokeSetupBlitFramebuffer(
                readFb, drawFb, srcX0, srcY0, srcX1, srcY1,
                0, 0, window.getFramebufferWidth(), window.getFramebufferHeight(), mask, 9729);
    }
}
