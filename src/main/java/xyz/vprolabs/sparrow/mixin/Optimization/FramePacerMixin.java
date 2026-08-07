package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.gui.ClickGuiScreen;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.locks.LockSupport;

@Mixin(MinecraftClient.class)
public class FramePacerMixin {

    private static final long MAX_FRAME_TIME_NS = 33_333_334L; // 30 FPS floor for the world
    private static final long MIN_FRAME_TIME_NS = 1_000_000L;  // 1000 FPS ceiling

    @Unique
    private long sparrow_frameStartNs = 0L;

    @Unique
    private boolean sparrow_logged = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void sparrow_recordFrameStart(boolean tick, CallbackInfo ci) {
        sparrow_frameStartNs = System.nanoTime();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sparrow_paceFrame(boolean tick, CallbackInfo ci) {
        // Read user's configured maxFps; target a FIXED frame time (not a moving average)
        MinecraftClient client = (MinecraftClient) (Object) this;
        int maxFps = client.options.getMaxFps().getValue();

        // While the click GUI is open, cap the GAME to "Sparrow -> gui-fps"
        // (default 60). This is the "Max Gui FPS" the user asked for: the GUI
        // renders every frame (screens can't skip renders — the framebuffer is
        // cleared each frame, skipping flickers), so the FPS saving lives here
        // instead. Covers Unlimited (<=0) and 240+ settings: both get locked
        // to the GUI cap. A lower user setting (e.g. 30) is respected — the cap
        // never raises the user's own choice.
        boolean inGui = client.currentScreen instanceof ClickGuiScreen;
        if (inGui) {
            int guiCap = Modules.guiFps.intValue();
            if (maxFps <= 0 || maxFps > guiCap) maxFps = guiCap;
        }

        // Unlimited or extremely high? Skip pacing entirely (unless GUI cap applied above).
        if (maxFps <= 0 || maxFps >= 1000) return;

        long targetNs = 1_000_000_000L / maxFps;

        // BUGFIX (2026-08-02): the hard 30 FPS floor was applied to the GUI cap
        // too, so "gui-fps" set to 5-29 was silently raised to 30 — the module
        // lied. Only clamp the WORLD target (the 0.1 FPS spiral guard); honor
        // the GUI cap exactly as configured.
        if (!inGui && targetNs > MAX_FRAME_TIME_NS) targetNs = MAX_FRAME_TIME_NS;
        if (targetNs < MIN_FRAME_TIME_NS) return; // 1000 FPS+ target, no pacing needed

        long now = System.nanoTime();
        long elapsed = now - sparrow_frameStartNs;
        long remaining = targetNs - elapsed;

        if (remaining > 0) {
            if (remaining > 1_000_000L) {
                LockSupport.parkNanos(remaining - 500_000L);
            }
            // Precise spin for the remaining sub-millisecond
            while (System.nanoTime() - sparrow_frameStartNs < targetNs) {
                Thread.onSpinWait();
            }
        }

        if (!sparrow_logged) {
            sparrow_logged = true;
            long fps = 1_000_000_000L / targetNs;
            SparrowLogger.info("FramePacerMixin: pacing to ~" + fps + " FPS");
        }
    }
}