package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.tweaks.AlphaVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class FluidTranslucencyMixin {
@Unique
    private static final ThreadLocal<Boolean> SPARROW_NEEDS_ALPHA = ThreadLocal.withInitial(() -> Boolean.FALSE); // per-render-frame flag, reset every cycle via .remove()

    @Inject(method = "render", at = @At("HEAD"))
    private void sparrow_detectFluid(BlockRenderView world, BlockPos pos, VertexConsumer vertices,
                                     net.minecraft.block.BlockState state, FluidState fluidState,
                                     CallbackInfo ci) {
    SPARROW_NEEDS_ALPHA.set(fluidState.isIn(FluidTags.WATER) || fluidState.isIn(FluidTags.LAVA));
}

    @Inject(method = "render", at = @At("TAIL"))
    private void sparrow_resetState(CallbackInfo ci) {
    SPARROW_NEEDS_ALPHA.remove();
}

    // BUGFIX (2026-08-02): this was @ModifyArg on the FIRST FluidRenderer.vertex
    // call only (ordinal 0), so a water quad spanning several vertex() calls got
    // the alpha wrapper on its first vertex and the raw consumer on the rest —
    // the surface turned specular-solid instead of translucent. Wrapping the
    // `vertices` PARAMETER at render HEAD replaces the consumer for the WHOLE
    // quad (every subsequent vertex call receives the wrapper).
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private VertexConsumer sparrow_wrapConsumer(VertexConsumer consumer) {
            boolean needsAlpha = Boolean.TRUE.equals(SPARROW_NEEDS_ALPHA.get());
            if (!needsAlpha) return consumer;
            return new AlphaVertexConsumer(consumer, 0.5f);
    }

}