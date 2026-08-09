package xyz.vprolabs.sparrow.mixin.Tweaks;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vprolabs.sparrow.module.Modules;

/**
 * Disables the totem pop animation (the big totem item that flies up and
 * blocks the center of the screen when you pop a totem).
 */
@Mixin(GameRenderer.class)
public class NoTotemPopMixin {

    // The totem pop in 1.21.11 is GameRenderer.showFloatingItem(ItemStack):
    // ClientPlayNetworkHandler.onEntityStatus (entity status 35) resolves the
    // player's active death protector (totem) and hands it to
    // showFloatingItem, which starts the full-screen floating-item animation
    // (floatingItemTimer countdown rendered by GameRenderer.render).
    // VERIFIED via javap: showFloatingItem has exactly ONE caller in the
    // whole game — the totem pop path — so canceling here cannot break any
    // other feature (it is NOT the held-item-switch tooltip; that is
    // InGameHud.renderHeldItemTooltip, untouched).
    // Kept running deliberately: the totem sound, the green particles around
    // the player and the entity handleStatus(35) chain all still fire, so
    // the player keeps the pop feedback without the vision-blocking overlay.
    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void sparrow_blockTotemPop(ItemStack stack, CallbackInfo ci) {
        if (Modules.noTotemPop.isEnabled()) {
            ci.cancel();
        }
    }
}
