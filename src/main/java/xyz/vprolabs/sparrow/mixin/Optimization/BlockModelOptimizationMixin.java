package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
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
        if (!cull) return;
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighborState = world.getBlockState(neighborPos);
        if (Block.isFaceFullSquare(neighborState.getCullingShape(), direction.getOpposite())) {
            cir.setReturnValue(false);
            if (!sparrow_blockOptLogged) {
                sparrow_blockOptLogged = true;
                SparrowLogger.debug("BlockModelOptimizationMixin: early face culling active");
            }
        }
    }
}
