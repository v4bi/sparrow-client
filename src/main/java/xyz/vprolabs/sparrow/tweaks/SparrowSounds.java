package xyz.vprolabs.sparrow.tweaks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Death sound playback.
 *
 * CRITICAL (2026-08-09): these SoundEvents are NEVER registered into
 * Registries.SOUND_EVENT. Two failed attempts preceded this:
 *   1. Registration at the mod entrypoint -> "Registry is already frozen"
 *      crash (1.21.11 freezes registries before client entrypoints run).
 *   2. Registration at Bootstrap.initialize() -> shifted EVERY vanilla
 *      sound event raw ID by 2. Servers without fabric registry sync send
 *      vanilla sounds as raw registry IDs, so the client resolved every
 *      vanilla sound to the wrong clip ("every sound is mixed up"), and the
 *      raw-ID-0 slot (vanilla "minecraft:none") became rbd_short, playing
 *      garbage on kills/hits.
 *
 * Registration is not needed for playback: SoundManager resolves a sound
 * instance purely by its event id against the sounds.json-derived map
 * (SoundManager.get(Identifier), verified via javap), so an unregistered
 * SoundEvent object with the right id plays the right file as long as
 * assets/sparrow-mod/sounds.json lists it. The events below are plain
 * id-holders, nothing more.
 */
public final class SparrowSounds {

    private static final SoundEvent RBD_SHORT = SoundEvent.of(Identifier.of("sparrow-mod", "rbd_short"));
    private static final SoundEvent RBD_FULL = SoundEvent.of(Identifier.of("sparrow-mod", "rbd_full"));

    private SparrowSounds() {}

    /**
     * Play the death sound for the given variant name ("short"/"full") at
     * linear volume 0.0-1.0. Unknown variants fall back to the short clip
     * (ModuleManager normalizes fixed-option values on load, so this only
     * guards against stale in-memory values).
     */
    public static void playDeath(String variant, float volume) {
        // ui(SoundEvent, pitch, volume): javap of the 3-arg factory shows
        // fload_1 -> pitch, fload_2 -> volume in the ctor (verified against
        // PositionedSoundInstance ui(SoundEvent,float,float) bytecode).
        SoundEvent event = "full".equals(variant) ? RBD_FULL : RBD_SHORT;
        MinecraftClient.getInstance().getSoundManager()
            .play(PositionedSoundInstance.ui(event, 1.0F, Math.max(0.0F, Math.min(1.0F, volume))));
    }
}
