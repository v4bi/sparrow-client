package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceReloader.Synchronizer;
import net.minecraft.resource.ResourceReloader.Store;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

// Measurement probe for the atlas-cache feature: logs how long the texture
// reload spends in the shared-state (sprite stitch) phase vs the full reload.
// No toggles on purpose: it is a diagnostic line, one per reload, cost is a
// few field writes.
@Mixin(AtlasManager.class)
public class BootTimerMixin {

    // Wall-clock via nanoTime (monotonic, same source FramePacerMixin uses).
    // currentTimeMillis is wall-clock too but can jump with NTP/clock changes
    // and has ms granularity; nanoTime avoids both for sub-ms phases.
    @Unique
    private long sparrow_prepareStartNs = 0L;

    // Sentinels: -1 means "prepare never ran for this reload". The resource
    // reload pipeline calls prepareSharedState on the apply thread before the
    // returned future completes, so normally this is set by the time reload
    // TAIL fires; -1 guards the pathological out-of-order case so we never log
    // a stale measurement from a previous reload as if it were this one.
    @Unique
    private long sparrow_prepareElapsedNs = -1L;

    @Unique
    private long sparrow_reloadStartNs = 0L;

    // Once-per-reload guard: reload TAIL can fire multiple times (Mixin fires
    // once per method exit path); reset at HEAD so each reload logs exactly one
    // line while repeated TAIL exits of the same reload log nothing.
    @Unique
    private boolean sparrow_logged = false;

    @Inject(method = "prepareSharedState", at = @At("HEAD"))
    private void sparrow_prepareStart(Store store, CallbackInfo ci) {
        // HEAD of the shared-state phase: timestamp the start, and reset the
        // elapsed sentinel so a missing TAIL (exception path) can't leak a
        // stale value into the next reload's log line.
        sparrow_prepareStartNs = System.nanoTime();
        sparrow_prepareElapsedNs = -1L;
    }

    @Inject(method = "prepareSharedState", at = @At("TAIL"))
    private void sparrow_prepareEnd(Store store, CallbackInfo ci) {
        // TAIL captures the phase duration once the stitch/state work is done.
        // Stored, not logged here: the log line is emitted by reload TAIL which
        // knows both numbers (the format wants them on one line).
        sparrow_prepareElapsedNs = System.nanoTime() - sparrow_prepareStartNs;
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void sparrow_reloadStart(Store store, Executor preparationExecutor, Synchronizer synchronizer,
                                     Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        sparrow_reloadStartNs = System.nanoTime();
        sparrow_logged = false;
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private void sparrow_reloadEnd(Store store, Executor preparationExecutor, Synchronizer synchronizer,
                                   Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        // TAIL of reload fires when the returned CompletableFuture is built,
        // which in this version completes only after the synchronizer's apply
        // phase ran (the apply chain is part of the returned future), so TAIL
        // is effectively end-of-reload: that is the "texture-apply" total.
        // CallbackInfoReturnable (not CallbackInfo): reload is non-void, and
        // Mixin rejects a CallbackInfo against a non-void target even at HEAD.
        if (!sparrow_logged) {
            sparrow_logged = true;
            long prepareMs = sparrow_prepareElapsedNs >= 0L ? sparrow_prepareElapsedNs / 1_000_000L : 0L;
            long applyMs = (System.nanoTime() - sparrow_reloadStartNs) / 1_000_000L;
            SparrowLogger.log("BOOT", "texture-prepare=" + prepareMs + "ms texture-apply=" + applyMs + "ms");
        }
    }
}
