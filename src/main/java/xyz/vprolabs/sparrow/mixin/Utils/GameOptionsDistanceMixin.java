package xyz.vprolabs.sparrow.mixin.Utils;
import xyz.vprolabs.sparrow.config.SodiumCompat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptions.class)
public class GameOptionsDistanceMixin {

    @Shadow @Final @Mutable
    private SimpleOption<Integer> viewDistance;

    @Shadow @Final @Mutable
    private SimpleOption<Integer> simulationDistance;

    @Unique
    private static int capturedRenderDistance = 0;

    @Unique
    private static int capturedSimulationDistance = 0;

    @Unique
    private static boolean captureReady = false;

    @Inject(method = "load", at = @At("RETURN"))
    private void onLoadReturn(CallbackInfo ci) {
        capturedRenderDistance = this.viewDistance.getValue();
        capturedSimulationDistance = this.simulationDistance.getValue();
        captureReady = true;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void expandDistances(CallbackInfo ci) {
        int capView = captureReady ? capturedRenderDistance : 8;
        int capSim = captureReady ? capturedSimulationDistance : 8;

        boolean sodiumLoaded = SodiumCompat.isSodiumLoaded();

        if (!sodiumLoaded) {
            int oldRender = capView;
            if (oldRender > 8 || oldRender < 2) oldRender = 8;

            this.viewDistance = new SimpleOption<>(
                "options.renderDistance",
                SimpleOption.emptyTooltip(),
                (optionText, value) -> GameOptions.getGenericValueText(optionText, value),
                new SimpleOption.ValidatingIntSliderCallbacks(1, 64, false),
                oldRender,
                value -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client == null) return;
                    client.options.write();
                    if (client.worldRenderer != null) {
                        client.worldRenderer.reload();
                    }
                }
            );
        }

        int oldSim = capSim;
        this.simulationDistance = new SimpleOption<>(
            "options.simulationDistance",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> GameOptions.getGenericValueText(optionText, value),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32, false),
            oldSim,
            value -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null) return;
                client.options.write();
                if (client.worldRenderer != null) {
                    client.worldRenderer.reload();
                }
            }
        );
    }
}
