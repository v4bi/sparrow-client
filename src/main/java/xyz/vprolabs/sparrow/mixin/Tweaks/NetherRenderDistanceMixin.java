package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class NetherRenderDistanceMixin {
    @Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void sparrow_capNetherChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
            int cap = Modules.netherRenderCap.intValue();
            if (cap <= 0) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            if (client.world.getRegistryKey() != World.NETHER) return;

            ClientPlayerEntity player = client.player;
            if (player == null) return;

            double dx = (x * 16.0 + 8.0) - player.getX();
            double dz = (z * 16.0 + 8.0) - player.getZ();
            double maxDist = cap * 16.0;

            if ((dx * dx + dz * dz) >= maxDist * maxDist) {
                ci.cancel();
            }
    }

}
