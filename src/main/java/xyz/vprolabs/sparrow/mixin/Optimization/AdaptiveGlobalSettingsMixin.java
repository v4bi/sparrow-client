package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.vprolabs.sparrow.state.AdaptiveResolutionState;

/**
 * Keep the global {@code u_Size} uniform consistent with the (possibly
 * scaled) framebuffer size so full-screen shader passes normalize
 * gl_FragCoord against the real render target.
 */
@Mixin(GameRenderer.class)
public class AdaptiveGlobalSettingsMixin {

    // GameRenderer.render feeds GlobalSettings.set(window.getFramebufferWidth(),
    // window.getFramebufferHeight(), ...) once per frame; that width/height
    // becomes the u_Size uniform that full-screen post-effect shaders
    // (creeper/spider/fov/distortion/blur passes, damage overlay) use for
    // gl_FragCoord.xy / u_Size math. When the framebuffer is scaled, u_Size
    // must match the scaled target or those effects sample only part of
    // their input texture.
    // Rejected: leaving it at window size - the post-processor renders into
    // the scaled framebuffer, so a window-sized u_Size produces half-covered
    // screenshake/damage overlays.
    @Redirect(method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlobalSettings;set(IIDJLnet/minecraft/client/render/RenderTickCounter;ILnet/minecraft/client/render/Camera;Z)V"))
    private void sparrow_scaleGlobalSettings(GlobalSettings settings, int width, int height,
            double tickDelta, long frameTimeNanos, RenderTickCounter tickCounter,
            int renderDistance, Camera camera, boolean renderBlockOutline) {
        double scale = AdaptiveResolutionState.scale();
        if (scale >= 1.0) {
            settings.set(width, height, tickDelta, frameTimeNanos, tickCounter, renderDistance, camera, renderBlockOutline);
            return;
        }
        settings.set((int) Math.round(width * scale), (int) Math.round(height * scale),
                tickDelta, frameTimeNanos, tickCounter, renderDistance, camera, renderBlockOutline);
    }
}
