package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.tweaks.DeathSoundPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Pattern;

/**
 * MULTIPLAYER death trigger (2026-08-10 rewrite).
 *
 * Duels/boxing servers kill the local player WITHOUT syncing health 0 and
 * without sending DeathMessageS2CPacket (no death screen), so the old
 * packet hooks never fired — observed live: "v4bi was slain by
 * Potato_Loxy" at 02:04:12/37 with the module enabled and zero playback.
 * The only client-observable death signal there is the [System] chat line,
 * which arrives via onGameMessage (GameMessageS2CPacket), so this is the
 * single multiplayer trigger. Singleplayer is covered separately by
 * DeathScreenMixin.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class DeathSoundMixin {

    // Vanilla death.attack phrase list (English server broadcasts). Two
    // shapes are matched:
    //   1. TRANSLATABLE component with a "death." key (vanilla sends
    //      death.attack.player with args [victim, killer]) — args[0] must
    //      be the local player so other players' deaths stay silent.
    //   2. LITERAL server string like "v4bi was slain by Potato_Loxy"
    //      (duels plugins pre-format the text) — require the local player's
    //      name AND one of the vanilla death phrases.
    // Limitation: English-only phrases for the literal branch; servers that
    // send the real translatable key work in any language.
    @Unique
    private static final Pattern SPARROW_DEATH_PATTERN = Pattern.compile(
        "was (slain|killed|blown up|shot|pricked|struck|impaled|fireballed|roasted|squashed|smashed|flamed|frozen|stung|obliterated|slammed|bonked|pummelled|kaboomed) by"
        + "|died|drowned|burned to death|fell from|fell off|hit the ground too hard|went off with a bang|blew up");

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void sparrow_onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        // Action-bar (overlay) messages never carry death lines; skip them.
        if (packet.overlay()) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;
        Text text = packet.content();

        // Branch 1: vanilla-style translatable death key, victim = self.
        if (text.getContent() instanceof TranslatableTextContent t
                && t.getKey().startsWith("death.")
                && t.getArgs().length > 0) {
            String victim = t.getArgs()[0].toString();
            if (victim.equals(player.getName().getString())
                    || victim.equals(player.getDisplayName().getString())) {
                DeathSoundPlayer.tryPlay("chat-translatable");
                return;
            }
        }

        // Branch 2: pre-formatted literal message naming the local player.
        String msg = text.getString();
        String self = player.getName().getString();
        if (msg.contains(self) && SPARROW_DEATH_PATTERN.matcher(msg).find()) {
            DeathSoundPlayer.tryPlay("chat-literal");
        }
    }
}
