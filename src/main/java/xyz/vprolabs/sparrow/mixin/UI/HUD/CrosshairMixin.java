package xyz.vprolabs.sparrow.mixin.UI.HUD;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vprolabs.sparrow.config.ConfigRegister;
import xyz.vprolabs.sparrow.tweaks.CrosshairRenderer;

@Mixin(InGameHud.class)
public class CrosshairMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void sparrow_cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        String mode = ConfigRegister.crosshair.get();
        if (!mode.equals("off")) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sparrow_renderCustomCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        CrosshairRenderer.render(context, context.getScaledWindowWidth(), context.getScaledWindowHeight());
    }
}
