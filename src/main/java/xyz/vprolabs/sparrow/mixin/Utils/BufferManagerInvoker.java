package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.gl.BufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * BufferManager.setupBlitFramebuffer is package-private (net.minecraft.client.gl),
 * so AdaptivePresentMixin cannot re-issue the present blit with a stretched
 * destination rect directly. This @Invoker produces the exact vanilla call:
 * bind GL_READ_FRAMEBUFFER (36008) to readFb, GL_DRAW_FRAMEBUFFER (36009) to
 * drawFb, then glBlitFramebuffer(srcRect, dstRect, mask, filter). Being an
 * @Invoker on the abstract declaration, it dispatches virtually to the
 * DefaultBufferManager implementation and stays byte-identical to vanilla
 * when the mixin passes through the original arguments.
 */
@Mixin(BufferManager.class)
public interface BufferManagerInvoker {

    @Invoker("setupBlitFramebuffer")
    void invokeSetupBlitFramebuffer(int readFb, int drawFb, int srcX0, int srcY0, int srcX1, int srcY1,
                                    int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
}
