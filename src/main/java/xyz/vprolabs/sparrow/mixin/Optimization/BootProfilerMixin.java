package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.client.MinecraftClient;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boot phase profiler: wall-clock duration of MinecraftClient construction
 * and the gap between constructor completion and the first rendered frame
 * (that gap covers the initial resource reload plus everything the render
 * thread does before drawing, e.g. shader compile on first use).
 *
 * Part of the boot-optimization measurement harness (2026-08-09): one line
 * per event, INFO level, no toggle. nanoTime, not currentTimeMillis: the
 * durations are sub-second and wall-clock can jump on NTP changes.
 *
 * First-frame is a one-shot: sparrow_firstFrameLogged latches after the
 * first render call so later frames never log again.
 */
@Mixin(MinecraftClient.class)
public class BootProfilerMixin {

    @Unique
    private static long sparrow_initStartNs = 0L;

    @Unique
    private static long sparrow_initEndNs = 0L;

    @Unique
    private static boolean sparrow_firstFrameLogged = false;

    // Static: HEAD of a constructor runs before super(), and Mixin rejects
    // non-static handlers there. Boot is single-threaded (render thread),
    // so static fields are safe.
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void sparrow_initHead(CallbackInfo ci) {
        sparrow_initStartNs = System.nanoTime();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private static void sparrow_initTail(CallbackInfo ci) {
        sparrow_initEndNs = System.nanoTime();
        SparrowLogger.log("BOOT", "mc-init=" + (sparrow_initEndNs - sparrow_initStartNs) / 1_000_000L + "ms");
    }

    @Inject(method = "render", at = @At("HEAD"))
    private static void sparrow_firstFrame(CallbackInfo ci) {
        if (sparrow_firstFrameLogged || sparrow_initEndNs == 0L) return;
        sparrow_firstFrameLogged = true;
        SparrowLogger.log("BOOT", "init-to-first-frame=" + (System.nanoTime() - sparrow_initEndNs) / 1_000_000L + "ms");
    }
}
