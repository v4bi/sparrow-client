package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class DesyncDetectMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void onPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
            if (!Modules.desync.isEnabled()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            var pi = client.player.input.playerInput;
            if (pi.left() || pi.right() || pi.forward() || pi.backward()) {
                HudState.lastDesyncTime = System.currentTimeMillis();
            }
    }
}
