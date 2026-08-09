package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.tweaks.SparrowSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class DeathSoundMixin {

    // Dedup latch (2026-08-09): the health hook and the death-message hook
    // both fire for the same death on vanilla servers. 1000ms window absorbs
    // the double-fire while still allowing death -> respawn -> death cycles.
    @Unique
    private static long sparrow_lastDeathSoundTime = 0L;

    // Per-connection alive flag (2026-08-09): duels servers (and many
    // minigame servers) never send DeathMessageS2CPacket — the death screen
    // never opens and the death message arrives as plain system chat — so
    // the packet hook alone never fires. Health dropping to 0 is the only
    // server-agnostic death signal. sparrow_wasAlive is an instance field on
    // the handler: each connection starts alive, goes false on the 0-health
    // update, and flips back true on the respawn health update.
    @Unique
    private boolean sparrow_wasAlive = true;

    // Primary trigger: health update to 0 = player death, works on ANY
    // server (vanilla, paper, duels, etc.) because the server must sync the
    // player's health. Fires at TAIL so the packet is already applied.
    @Inject(method = "onHealthUpdate", at = @At("TAIL"))
    private void sparrow_onHealthUpdate(HealthUpdateS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        boolean alive = packet.getHealth() > 0.0F;
        if (!alive && sparrow_wasAlive) {
            sparrow_wasAlive = false;
            tryPlayDeathSound("health-based");
        } else if (alive) {
            sparrow_wasAlive = true;
        }
    }

    // Secondary trigger: the vanilla death message packet (opens the death
    // screen). Kept for singleplayer and vanilla-style servers; the shared
    // dedup latch prevents double playback with the health hook.
    @Inject(method = "onDeathMessage", at = @At("HEAD"))
    private void sparrow_onDeath(DeathMessageS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (packet.playerId() != player.getId()) return;
        tryPlayDeathSound("death-message");
    }

    @Unique
    private void tryPlayDeathSound(String reason) {
        if (!Modules.deathSound.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - sparrow_lastDeathSoundTime < 1000) return;
        sparrow_lastDeathSoundTime = now;
        // Children read via Module.child() (composite pattern, same as
        // FireTimer): variant is Short|Full, volume is 1-100 scaled to 0-1
        // for the SoundInstance. FINE log so a silent failure is diagnosable
        // in sparrow-client.log (2026-08-09: user reported no playback).
        String variant = Modules.deathSound.child("death-sound-variant").stringValue();
        float volume = (float) Modules.deathSound.child("death-sound-volume").value() / 100.0F;
        SparrowLogger.debug("Death sound playing: reason=" + reason + " variant=" + variant + " volume=" + volume);
        SparrowSounds.playDeath(variant, volume);
    }
}
