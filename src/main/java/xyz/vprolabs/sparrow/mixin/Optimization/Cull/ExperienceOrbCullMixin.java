package xyz.vprolabs.sparrow.mixin.Optimization.Cull;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.render.entity.ExperienceOrbEntityRenderer;
import net.minecraft.client.render.entity.state.ExperienceOrbEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrbEntityRenderer.class)
public class ExperienceOrbCullMixin {

    @Unique
    private static boolean sparrow_orbLogged = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sparrow_cullDistantOrb(ExperienceOrbEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState camera, CallbackInfo ci) {
        if (camera.pos == null) return;

        // BUGFIX (2026-08-02): this used a hardcoded SPARROW_MAX_ORB_DIST = 32,
        // so orbs vanished at 32 blocks even when "entity-culling-distance" was
        // set to 128 — the two culling paths disagreed and the hard cap won.
        // Use the same module distance as EntityCullPrepassMixin so orbs obey
        // the configured culling distance consistently.
        double cullDist = Modules.entityCullingDistance.floatValue();
        double distSqLimit = cullDist * cullDist;

        double dx = state.x - camera.pos.x;
        double dy = state.y - camera.pos.y;
        double dz = state.z - camera.pos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > distSqLimit) {
            if (!sparrow_orbLogged) {
                sparrow_orbLogged = true;
                SparrowLogger.debug("ExperienceOrbCullMixin: culling orbs > " + (int) cullDist + " blocks");
            }
            ci.cancel();
        }
    }
}
