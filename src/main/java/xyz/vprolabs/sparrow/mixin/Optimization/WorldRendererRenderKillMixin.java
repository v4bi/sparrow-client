package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererRenderKillMixin {

    @Unique
    private static boolean sparrow_worldRenderLogged = false;

    @Inject(method = "renderLateDebug", at = @At("HEAD"), cancellable = true)
    private void sparrow_stopRenderLateDebug(FrameGraphBuilder builder, CameraRenderState cameraRenderState, GpuBufferSlice gpuBufferSlice, Matrix4f matrix4f, CallbackInfo ci) {
            if (!Modules.debugRenderKill.isEnabled()) return;
            if (!sparrow_worldRenderLogged) {
                sparrow_worldRenderLogged = true;
                SparrowLogger.debug("WorldRendererRenderKillMixin: blocking renderLateDebug");
            }
            ci.cancel();
    }
}
