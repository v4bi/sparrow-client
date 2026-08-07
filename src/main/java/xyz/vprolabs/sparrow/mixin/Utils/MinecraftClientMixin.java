package xyz.vprolabs.sparrow.mixin.Utils;

// DEFERRED-INIT WARNING:
// The module system (Modules/ModuleManager) is loaded here on the first render
// frame, not in SparrowMod.onInitializeClient(). Any code that runs in a
// constructor, static initializer, or early mixin (before the first render
// tick) must NOT read Modules — it will see Java defaults.
// Contributors: do not move Modules reads earlier than this point.

import xyz.vprolabs.sparrow.module.ModuleManager;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.mixin.Utils.SimpleOptionAccessor;
import xyz.vprolabs.sparrow.tweaks.SparrowGlintLayers;
import xyz.vprolabs.sparrow.state.VersionCheck;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Unique
    private boolean sparrow_initialized = false;

    @Unique
    private long sparrow_lastFullbrightCheck = 0;

    @Inject(method = "render", at = @At("HEAD"))
    private void sparrow_onFirstRender(boolean tick, CallbackInfo ci) {
            if (sparrow_initialized) return;
            sparrow_initialized = true;

            SparrowLogger.info("=== Sparrow: deferred init (post-game-load) ===");

            MinecraftClient client = MinecraftClient.getInstance();

            // Gamma fullbright — always on
            ((SimpleOptionAccessor)(Object)client.options.getGamma()).setValue(15.0);
            SparrowLogger.info("Forced gamma to 15.0 (fullbright)");

            ModuleManager.load();
            SparrowGlintLayers.init();

            // Version check runs once per session, from here (first render
            // frame). It used to ride on ServerSafety.sync(), whose only
            // entry point (isFeatureDisabled) has no consumers left, so the
            // update notice never fired.
            VersionCheck.checkOnce();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sparrow_checkFullbright(boolean tick, CallbackInfo ci) {
        if (!sparrow_initialized) return;

        long now = System.currentTimeMillis();
        if (now - sparrow_lastFullbrightCheck < 60000) return;
        sparrow_lastFullbrightCheck = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        double gamma = client.options.getGamma().getValue();
        if (gamma < 15.0) {
            ((SimpleOptionAccessor)(Object) client.options.getGamma()).setValue(15.0);
            SparrowLogger.info("Fullbright has been disabled, fixing now.");
        }
    }
}
