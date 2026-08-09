package xyz.vprolabs.sparrow.mixin.Optimization.Cull;

import net.minecraft.client.particle.BillboardParticleRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.ParticleCullState;

/**
 * Distance-culls particles: beyond the configured gate they are (a) not simulated
 * in tick (markDead instead of Particle.tick) and (b) rejected at render time.
 *
 * <p>DEVIATION from the original spec: in 1.21.11 the particle renderers were
 * extracted OUT of ParticleManager - they are top-level classes
 * ({@code net.minecraft.client.particle.ParticleRenderer} abstract,
 * {@code net.minecraft.client.particle.BillboardParticleRenderer}), so the spec's
 * {@code ParticleManager$...} string targets do not exist (javap: class not found).
 * Using class literals instead of strings is safe here because all three targets
 * are nameable top-level classes; the multi-target require = 0 rule is kept anyway.
 */
@Mixin({
        ParticleManager.class,
        ParticleRenderer.class,
        BillboardParticleRenderer.class
})
public class ParticleCullingMixin {

    @Unique
    private static boolean sparrow_logged = false;

    /**
     * Capture the camera + module threshold once per frame. addToBatch runs from
     * WorldRenderer.render (render thread) and is the only per-frame particle entry,
     * so the tick-side checks below see at most a one-frame-old camera. This is the
     * cheapest capture point: the camera is already materialized as a Vec3d argument.
     */
    @Inject(method = "addToBatch", at = @At("HEAD"), require = 0)
    private void sparrow_captureCamera(SubmittableBatch batch, Frustum frustum, Camera camera, float tickDelta, CallbackInfo ci) {
        ParticleCullState.updateCamera(camera.getCameraPos());

        if (!sparrow_logged) {
            sparrow_logged = true;
            SparrowLogger.debug("ParticleCullingMixin: distance culling active (" + Modules.particleCullDistance.floatValue() + " blocks)");
        }
    }

    /**
     * Skip simulation for far particles.
     *
     * <p>DEVIATION: the spec's redirect at {@code method = "tick"} is impossible -
     * javap shows {@code ParticleRenderer.tick()} does NOT invoke Particle.tick()
     * directly; it calls the private {@code tickParticle(Particle)} helper, and the
     * {@code Particle.tick()} invoke lives there. Redirect targets the helper.
     *
     * <p>DEVIATION: Particle.x/y/z are PROTECTED in 1.21.11 (javap: no public
     * position getter, only getBoundingBox()), so the handler reads the box center
     * instead. The box trails fast particles by at most one tick of movement
     * (see ParticleCullState for the derivation) - irrelevant at the default 96
     * blocks, bounded by ~0.5 blocks at the 5.0 minimum.
     *
     * <p>markDead() sets the dead flag; the calling loop in tick() then sees
     * isAlive() == false and removes the particle from the queue, firing the group
     * callback - identical to vanilla particle death, no extra cleanup needed.
     * The try/catch crash-report wrapper in tickParticle still guards this call.
     */
    @Redirect(method = "tickParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;tick()V"), require = 0)
    private static void sparrow_cullFarParticle(Particle particle) {
        Box box = particle.getBoundingBox();
        double px = (box.minX + box.maxX) * 0.5;
        double py = (box.minY + box.maxY) * 0.5;
        double pz = (box.minZ + box.maxZ) * 0.5;
        if (ParticleCullState.isFar(px, py, pz)) {
            particle.markDead();
        } else {
            particle.tick();
        }
    }

    /**
     * Reject far particles at render submission. The handler receives the exact
     * live x/y/z already on the stack for the intersectPoint call (javap shows
     * BillboardParticleRenderer.render loads particle.x/y/z fields right before
     * the invoke), so the render-side gate is exact - no box staleness here.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Frustum;intersectPoint(DDD)Z"), require = 0)
    private static boolean sparrow_cullFarBillboard(Frustum frustum, double x, double y, double z) {
        if (ParticleCullState.isFar(x, y, z)) {
            return false;
        }
        return frustum.intersectPoint(x, y, z);
    }
}
