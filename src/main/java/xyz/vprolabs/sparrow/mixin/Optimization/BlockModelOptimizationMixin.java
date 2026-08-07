package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public class BlockModelOptimizationMixin {

    @Unique
    private static boolean sparrow_blockOptLogged = false;

    @Inject(method = "shouldDrawFace", at = @At("HEAD"), cancellable = true)
    private static void sparrow_earlyCullFace(BlockRenderView world, BlockState state, boolean cull, Direction direction, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!Modules.blockModelOptimization.isEnabled()) return;
        if (!cull) return;
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighborState = world.getBlockState(neighborPos);
        // 2026-08-02 xray bug: vanilla 1.21.11 shouldDrawFace is
        //   !cull || !state.isSideInvisible(neighbor, direction)
        // — NO isFaceFullSquare check (decompiled via javap). The old check culled
        // faces vanilla still draws — solid face against a transparent neighbor with
        // a full culling shape (glass, ice, ...) — creating permanent xray holes that
        // F3+A cannot fix (deterministic at geometry build).
        // Fix: early-out ONLY when vanilla's own decision is already "hidden", so the
        // mixin is a pure no-divergence fast path. Gated, default OFF.
        if (state.isSideInvisible(neighborState, direction)) {
            if (!sparrow_blockOptLogged) {
                sparrow_blockOptLogged = true;
                SparrowLogger.debug("BlockModelOptimizationMixin: early face culling active");
            }
            cir.setReturnValue(false);
        }
    }
}
