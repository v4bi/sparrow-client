/*
 * Sparrow Mod - Thin mixin that drives the fire-timer HUD overlay.
 * Refactored to match the StorageTooltipMixin pattern: state in
 * FireTimerState, draw in FireTimerRenderer, this class is just
 * the lifecycle hook.
 * Made By: vProLabs (https://www.vprolabs.xyz)
 * Discord: discord.gg/SNzUYWbc5Q
 * Donations: ko-fi.com/v4bi
 */

package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudState;
import xyz.vprolabs.sparrow.tweaks.FireTimerRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class FireTimerMixin {
@Inject(method = "render", at = @At("TAIL"))
    private void sparrow_renderFireTimer(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
if (!Modules.fireTimer.child("fire-timer-enabled").isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        InGameHud self = (InGameHud) (Object) this;
        LivingEntity living = mc.player;
        if (living == null && mc.getCameraEntity() instanceof LivingEntity le) {
            living = le;
        }
        if (living == null) {
            return;
        }
        if (living.isRemoved() || !living.isAlive() || living.isSpectator()) {
            return;
        }

        int ticks = living.getFireTicks();
        if (ticks <= 0) {
            HudState.reset();
            return;
        }

        if (!HudState.logged) {
            HudState.logged = true;
            SparrowLogger.debug("FireTimerMixin: fire timer enabled, showing fire ticks: " + ticks);
        }

        TextRenderer font = self.getTextRenderer();
        FireTimerRenderer.render(context, font, ticks);
}

}
