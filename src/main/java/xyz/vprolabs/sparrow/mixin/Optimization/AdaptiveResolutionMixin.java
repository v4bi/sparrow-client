package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.AdaptiveResolutionState;

/**
 * Adaptive render resolution driver. Measures the presented frame interval
 * at {@code MinecraftClient.render} HEAD, feeds {@link AdaptiveResolutionState}
 * (EMA + hysteresis ladder), and resizes the client framebuffer when the
 * scale changes. The downscale never applies while a GUI is open, the
 * tablist is up, or a scoreboard is registered: only the plain world view
 * may be scaled. See AdaptiveResolutionState for the seam analysis and the
 * decision math.
 */
@Mixin(MinecraftClient.class)
public class AdaptiveResolutionMixin {

    // Baseline of the frame-interval measurement; 0 = no previous sample yet
    // (first frame, or after an unfocused/title-screen pause).
    @Unique
    private long sparrow_lastFrameNs = 0L;

    // Render scale must stay even: Framebuffer.initFbo asserts even
    // dimensions and texture reallocation for odd sizes wastes a pass.
    // Math.round first (0.66 * 1366 = 901.6 -> 902), then clear the LSB
    // (902 -> 902; 507 -> 506). Never below 2 even when the window is tiny.
    @Unique
    private static int sparrow_evenRound(double value) {
        return Math.max(2, ((int) Math.round(value)) & ~1);
    }

    // The ONLY game resize path (window drag-resize) is
    // onResolutionChanged -> client framebuffer.resize(window dims). The
    // constructor and the 4096x4096 panorama captures use different call
    // sites, so this redirect cannot touch them. Without it, dragging the
    // window would resize the framebuffer to FULL size, then the render-HEAD
    // inject would immediately re-shrink it - a double texture realloc and a
    // one-frame full-res blip on every resize event. Scaling here keeps the
    // framebuffer at the adaptive size through the whole drag.
    // Rejected: patching MinecraftClient.<init> to scale the initial
    // framebuffer - the module starts disabled (scale 1.0), so the initial
    // full-size framebuffer is already correct.
    // Assumes AdaptiveResolutionState.scale() is 1.0 whenever the module is
    // disabled, making this redirect a no-op pass-through in the default
    // state.
    @Redirect(method = "onResolutionChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;resize(II)V"))
    private void sparrow_scaleWindowResize(Framebuffer framebuffer, int width, int height) {
        double scale = AdaptiveResolutionState.scale();
        if (scale >= 1.0) {
            framebuffer.resize(width, height);
        } else {
            framebuffer.resize(sparrow_evenRound(width * scale), sparrow_evenRound(height * scale));
        }
    }

    // Frame-interval sampling gate:
    // - Module disabled: restore the framebuffer to full window size (once,
    //   guarded by the size check) so disabling mid-game unwinds cleanly,
    //   and reset the state + baseline. scale() returns 1.0 when disabled,
    //   so this branch also covers the not-yet-sampled first frame.
    // - world == null or window unfocused: reset the baseline and skip
    //   sampling. The title screen and alt-tab render at unrelated rates; a
    //   long unfocused interval would look like a GPU bottleneck and trigger
    //   a bogus downscale.
    // - Any GUI open, tablist visible, or scoreboard registered: restore the
    //   framebuffer to full size and reset the baseline. A downscaled
    //   framebuffer is bilinear-stretched to the window at present time
    //   (AdaptivePresentMixin), so EVERY text element renders blurry: GUIs
    //   (inventory, pause, chat, the Sparrow menu), the tablist, and the
    //   scoreboard must never be downscaled. Sampling is skipped here for
    //   the same reason as the unfocused branch: a slow GUI/tablist frame
    //   must not look like a GPU bottleneck to the EMA.
    // On a scale change, resize the client framebuffer immediately. The
    // frame graph sizes ALL its targets from the framebuffer's texture dims
    // at graph-build time inside this same render() call, so the new size
    // applies THIS frame; the present path (AdaptivePresentMixin) stretches
    // it back to the window. The resize is a texture delete + realloc, but
    // update() returns true at most once per sustained gate (>= 30 frames),
    // so reallocation can never happen more than ~twice per second.
    @Inject(method = "render", at = @At("HEAD"))
    private void sparrow_adaptiveResolutionTick(boolean tick, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        Window window = client.getWindow();
        Framebuffer framebuffer = client.getFramebuffer();
        int baseW = window.getFramebufferWidth();
        int baseH = window.getFramebufferHeight();

        if (!Modules.adaptiveResolution.isEnabled()) {
            AdaptiveResolutionState.setEnabled(false);
            sparrow_restoreFullSize(framebuffer, baseW, baseH);
            sparrow_lastFrameNs = 0L;
            return;
        }
        AdaptiveResolutionState.setEnabled(true);
        if (client.world == null || !client.isWindowFocused()) {
            sparrow_lastFrameNs = 0L;
            return;
        }
        if (client.currentScreen != null
                || client.options.playerListKey.isPressed()
                || sparrow_hasScoreboard(client)) {
            // Text-legibility gate: while any GUI is open (inventory, pause,
            // chat, the Sparrow menu), while the tablist is up, or while a
            // scoreboard exists, the downscale must not be active, because
            // the present-path stretch blurs all text. Restore to full size
            // once (the size guard inside sparrow_restoreFullSize makes this
            // a no-op when already full) and drop the sampling baseline so a
            // slow GUI/tablist frame cannot feed the EMA a bogus bottleneck.
            // Tablist: PlayerListHud exposes no visibility getter in 1.21.11
            // (javap: private boolean visible, only setVisible), so the
            // faithful same-frame predictor is the vanilla driver itself,
            // InGameHud.render reading playerListKey.isPressed() (verified
            // in InGameHud bytecode). The multiplayer pause-menu tablist is
            // already covered by the currentScreen check.
            sparrow_restoreFullSize(framebuffer, baseW, baseH);
            sparrow_lastFrameNs = 0L;
            return;
        }

        long now = System.nanoTime();
        if (sparrow_lastFrameNs != 0L && AdaptiveResolutionState.update(now - sparrow_lastFrameNs)) {
            double scale = AdaptiveResolutionState.scale();
            if (scale >= 1.0) {
                sparrow_restoreFullSize(framebuffer, baseW, baseH);
            } else {
                int scaledW = sparrow_evenRound(baseW * scale);
                int scaledH = sparrow_evenRound(baseH * scale);
                if (framebuffer.textureWidth != scaledW || framebuffer.textureHeight != scaledH) {
                    framebuffer.resize(scaledW, scaledH);
                }
            }
        }
        sparrow_lastFrameNs = now;
    }

    // True when the server has registered any scoreboard objective. Broad on
    // purpose: LIST-slot objectives render inside the tablist (already
    // skipped above), and the requirement is "never blur a scoreboard". The
    // accepted trade-off is that servers with a permanent scoreboard
    // effectively disable the downscale. Rejected getObjectiveForSlot(SIDEBAR):
    // precise (it is the exact check InGameHud uses for the sidebar) but an
    // objective registered in another slot would let the blur through, and
    // the user explicitly accepted the broad behavior. The null guard is
    // defensive only: ClientWorld always owns a Scoreboard, and the caller
    // already verified world != null.
    @Unique
    private static boolean sparrow_hasScoreboard(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();
        return scoreboard != null && !scoreboard.getObjectives().isEmpty();
    }

    // One restore path shared by the disabled branch, the full-scale branch,
    // and the GUI/tablist/scoreboard skip branch, so they cannot diverge.
    // The size guard makes this a no-op unless the framebuffer is actually
    // scaled, so calling it every frame while a GUI is open costs a
    // width/height compare and never triggers a realloc.
    @Unique
    private static void sparrow_restoreFullSize(Framebuffer framebuffer, int baseW, int baseH) {
        if (framebuffer.textureWidth != baseW || framebuffer.textureHeight != baseH) {
            framebuffer.resize(baseW, baseH);
        }
    }
}
