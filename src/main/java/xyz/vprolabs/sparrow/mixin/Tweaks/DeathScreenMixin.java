package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.tweaks.DeathSoundPlayer;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SINGLEPLAYER death trigger (2026-08-10 rewrite).
 *
 * The integrated server always opens the death screen (it sends
 * DeathMessageS2CPacket over the internal connection), so DeathScreen
 * creation fires on EVERY singleplayer death — the old health-0 packet
 * hook is not needed there. Multiplayer duels servers never open the death
 * screen, so this hook never fires for them; that case is covered by
 * DeathSoundMixin's chat trigger. The shared dedup latch in
 * DeathSoundPlayer absorbs the double-fire when both triggers hit the same
 * death (vanilla-style multiplayer: chat line + death screen in one tick).
 */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sparrow_onDeathScreen(Text message, boolean beReal, ClientPlayerEntity player, CallbackInfo ci) {
        DeathSoundPlayer.tryPlay("death-screen");
    }
}
