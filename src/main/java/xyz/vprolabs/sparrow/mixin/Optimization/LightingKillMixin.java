package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightingProvider.class)
public class LightingKillMixin {
    @Unique private static boolean sparrow_lightingLogged = false;

    @Inject(method = "doLightUpdates", at = @At("HEAD"), cancellable = true)
    private void sparrow_killDoLightUpdates(CallbackInfoReturnable<Integer> cir) {
        if (!Modules.lightingKillTarget.isEnabled()) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        cir.setReturnValue(0);
        if (!sparrow_lightingLogged) {
            sparrow_lightingLogged = true;
            SparrowLogger.debug("LightingKillMixin: client-side lighting disabled");
        }
    }

    @Inject(method = "hasUpdates", at = @At("HEAD"), cancellable = true)
    private void sparrow_killHasUpdates(CallbackInfoReturnable<Boolean> cir) {
        if (!Modules.lightingKillTarget.isEnabled()) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        cir.setReturnValue(false);
    }

    @Inject(method = "propagateLight", at = @At("HEAD"), cancellable = true)
    private void sparrow_killPropagateLight(CallbackInfo ci) {
        if (!Modules.lightingKillTarget.isEnabled()) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        ci.cancel();
    }

    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    private void sparrow_killCheckBlock(CallbackInfo ci) {
        if (!Modules.lightingKillTarget.isEnabled()) return;
        if (!MinecraftClient.getInstance().isOnThread()) return;
        ci.cancel();
    }
}
