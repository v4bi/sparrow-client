package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gl.DynamicUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DynamicUniforms.class)
public class DynamicUniformsMixin {

    @Unique
    private static boolean sparrow_preallocated = false;

    @Unique
    private static final int SPARROW_UBO_CAPACITY = 16384;

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gl/DynamicUniformStorage;<init>(Ljava/lang/String;II)V"
        ),
        index = 2
    )
    private int sparrow_preallocateUbo(int initialCapacity) {
            // GATED (2026-08-02): UBO prealloc to 16K is a memory-hungry
            // optimization. Off by default; enable via module for big worlds.
            if (!Modules.dynamicUboPrealloc.isEnabled()) return initialCapacity;
            if (!sparrow_preallocated) {
                sparrow_preallocated = true;
                SparrowLogger.debug("DynamicUniformsMixin: pre-allocating UBOs to " + SPARROW_UBO_CAPACITY);
            }
            return SPARROW_UBO_CAPACITY;
    }
}
