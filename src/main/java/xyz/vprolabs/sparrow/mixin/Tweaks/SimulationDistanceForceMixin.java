package xyz.vprolabs.sparrow.mixin.Tweaks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.SimulationDistanceS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Simulation distance force-to-1 (2026-08-09 user spec): the OPTION loads 1
// from options.txt fine, but the CLIENT WORLD's simulationDistance field is
// overwritten by the join packet with the SERVER's value (12 on typical
// servers), which is what F3 shows and what the user saw as "the game reset
// my setting to 12". The client can legally run a LOWER simulation distance
// than the server: ClientWorld.setSimulationDistance(int) is public and the
// client-side effect is reduced entity simulation beyond that radius (a
// performance win, same intent as the user setting the option to 1).
// This mixin re-asserts 1 after both packet paths that write the field:
// onGameJoin (initial join) and onSimulationDistance (mid-game server
// changes). Always-on by user demand — no module gate.
@Mixin(ClientPlayNetworkHandler.class)
public class SimulationDistanceForceMixin {

    @Unique
    private static final int SPARROW_FORCED_SIM_DISTANCE = 1;

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void sparrow_forceSimDistanceOnJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        forceSimDistance();
    }

    @Inject(method = "onSimulationDistance", at = @At("TAIL"))
    private void sparrow_forceSimDistanceOnUpdate(SimulationDistanceS2CPacket packet, CallbackInfo ci) {
        forceSimDistance();
    }

    @Unique
    private static void forceSimDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return;
        client.world.setSimulationDistance(SPARROW_FORCED_SIM_DISTANCE);
    }
}
