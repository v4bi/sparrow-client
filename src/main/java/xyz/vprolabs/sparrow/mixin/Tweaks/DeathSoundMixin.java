package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.tweaks.SparrowSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class DeathSoundMixin {

    // Dedup latch (2026-08-09): onDeathMessage can fire more than once per
    // death in edge cases (the vanilla handler itself guards repeats via
    // ClientPlayerEntity.showsDeathScreen). 1000ms window absorbs re-fires
    // while still allowing a legit death -> respawn -> death cycle.
    @Unique
    private static long sparrow_lastDeathSoundTime = 0L;

    @Inject(method = "onDeathMessage", at = @At("HEAD"))
    private void sparrow_onDeath(DeathMessageS2CPacket packet, CallbackInfo ci) {
        // Hook choice (2026-08-09, deviates from the original PlayerEntity
        // onDeath spec): javap proves 1.21.11 never invokes PlayerEntity
        // onDeath on the client. The client learns of its own death only via
        // the DeathMessageS2CPacket handled here (it opens the DeathScreen
        // directly). Server-side onDeath runs on the integrated server in
        // singleplayer as a ServerPlayerEntity, which the old instanceof
        // ClientPlayerEntity check would have discarded anyway. This packet
        // hook fires for the local player in singleplayer AND multiplayer.
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (packet.playerId() != player.getId()) return;
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
        SparrowLogger.debug("Death sound playing: variant=" + variant + " volume=" + volume);
        SparrowSounds.playDeath(variant, volume);
    }
}
