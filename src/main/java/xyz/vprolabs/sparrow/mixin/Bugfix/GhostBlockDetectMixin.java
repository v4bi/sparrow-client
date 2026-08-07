package xyz.vprolabs.sparrow.mixin.Bugfix;

import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.GhostBlockState;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class GhostBlockDetectMixin {

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void sparrow_detectBlockChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
            // Opt-in (2026-08-02): this fired on EVERY block type change —
            // mining, placing, piston pushes, explosions — so the HUD counter
            // was permanently nonzero noise, and it ran even for blocks the
            // player themselves placed. Gate the detection behind the module;
            // default OFF means no counter until the user enables it.
            if (!Modules.ghostBlock.isEnabled()) return;
            ClientWorld world = (ClientWorld) (Object) this;
            BlockState existing = world.getBlockState(pos);
            if (existing.getBlock() != state.getBlock()) {
                GhostBlockState.markGhost(pos);
            }
    }
}
