package xyz.vprolabs.sparrow.tweaks;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;

/**
 * Shared death-sound playback gate (2026-08-10 rewrite).
 *
 * Previously the mixin itself held the dedup latch and module check, and
 * DeathSoundMixin carried THREE packet triggers (health-0, death-message,
 * death chat). Evidence from a real session (2026-08-10 02:04): a duels
 * server killed the local player TWICE ("v4bi was slain by Potato_Loxy")
 * while the module was enabled, and neither the health-0 packet nor
 * DeathMessageS2CPacket ever arrived, so no sound played. The player's own
 * death on such servers is ONLY observable via the system chat line.
 *
 * The rewrite splits triggers by environment:
 *   - MULTIPLAYER: DeathSoundMixin hooks ClientPlayNetworkHandler.onGameMessage
 *     (the [System] chat path) and matches a vanilla-format death message
 *     that names the local player. Works on duels servers (observed), vanilla
 *     servers, and most minigame servers.
 *   - SINGLEPLAYER: DeathScreenMixin hooks DeathScreen.<init> — the
 *     integrated server always opens the death screen, so this fires on
 *     every singleplayer death. Also covers vanilla-style multiplayer
 *     deaths as a bonus.
 * Both triggers funnel into tryPlay(), so a double-fire (vanilla MP: chat
 * line + death screen in the same tick) is deduplicated by the latch.
 *
 * Why NOT keep the old health-0 packet hook: it demonstrably misses duels
 * deaths, which is the user's primary server type. Why NOT use only the
 * death screen: duels servers never open it. The pair above is the minimal
 * set that covers both worlds with no packet that can be suppressed.
 */
public final class DeathSoundPlayer {

    private DeathSoundPlayer() {}

    // Dedup latch: the chat line and the death screen fire for the same
    // death on vanilla servers. 1000ms window absorbs the double-fire while
    // still allowing death -> respawn -> death cycles.
    private static long lastDeathSoundTime = 0L;

    /**
     * Play the configured death sound if the module is enabled and no death
     * sound played within the dedup window.
     *
     * @param reason where the trigger came from ("chat" / "death-screen"),
     *               only used for the FINE log line so silent failures stay
     *               diagnosable in sparrow-client.log
     */
    public static void tryPlay(String reason) {
        if (!Modules.deathSound.isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastDeathSoundTime < 1000) return;
        lastDeathSoundTime = now;
        // Children read via Module.child() (composite pattern): variant is
        // Short|Full, volume is 1-100 scaled to 0-1 for the SoundInstance.
        String variant = Modules.deathSound.child("death-sound-variant").stringValue();
        float volume = (float) Modules.deathSound.child("death-sound-volume").value() / 100.0F;
        SparrowLogger.debug("Death sound playing: reason=" + reason + " variant=" + variant + " volume=" + volume);
        SparrowSounds.playDeath(variant, volume);
    }
}
