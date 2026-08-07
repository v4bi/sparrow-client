package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.tweaks.ShieldChargeRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class ShieldChargeMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void sparrow_renderShieldCharge(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!Modules.shieldStatus.isEnabled()) return;
        if (MinecraftClient.getInstance().options.hudHidden) return;
            ShieldChargeRenderer.render(context);
    }
}
