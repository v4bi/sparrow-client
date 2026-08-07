package xyz.vprolabs.sparrow.mixin.Optimization.Cull;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import com.mojang.blaze3d.systems.VertexSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public class SectionBuilderCullingMixin {

    @Unique
    private static boolean sparrow_sectionCullLogged = false;

    @Inject(method = "build", at = @At("HEAD"), cancellable = true)
    private void sparrow_cullUnderground(
        ChunkSectionPos sectionPos,
        ChunkRendererRegion region,
        VertexSorter vertexSorter,
        BlockBufferAllocatorStorage allocatorStorage,
        CallbackInfoReturnable<SectionBuilder.RenderData> cir
    ) {
            // GATED (2026-08-02): off by default, must be opted into. When off
            // the underground section build stays intact and no ghost geometry
            // appears from returning an empty RenderData below the cut Y.
            if (!Modules.sectionCulling.isEnabled()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            double playerY = client.player.getY();
            if (playerY <= 400.0) return;

            int sectionMinY = sectionPos.getMinY();
            int cutoffY = (int) playerY - 320;

            if (sectionMinY < cutoffY) {
                if (!sparrow_sectionCullLogged) {
                    sparrow_sectionCullLogged = true;
                    SparrowLogger.debug("SectionBuilderCullingMixin: skipping low section build (playerY=" + playerY + ", cutoffY=" + cutoffY + ")");
                }
                cir.setReturnValue(new SectionBuilder.RenderData());
            }
    }
}
