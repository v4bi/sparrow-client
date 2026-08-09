package xyz.vprolabs.sparrow.state;

import net.minecraft.util.math.Vec3d;
import xyz.vprolabs.sparrow.module.Modules;

/**
 * Squared-distance gate for particle culling.
 *
 * <p>Vanilla only frustum-culls particles at render time; tick simulation has no
 * distance gate, so at a normal FOV roughly 11 of every 12 particles are ticked,
 * queued and submitted for rendering while invisible. This state supplies the
 * camera anchor + threshold for {@code ParticleCullingMixin} to skip both.
 */
public final class ParticleCullState {

    // SQUARED-DISTANCE CULL GATE (default 96 blocks = Modules.particleCullDistance default).
    // Source: research 2026-08-09, javap of ParticleManager/ParticleRenderer/BillboardParticleRenderer:
    //   tick() calls private tickParticle(Particle) -> Particle.tick() with no distance check,
    //   render() gates each particle only on Frustum.intersectPoint. ~1/12 visible at 70 FOV.
    // Derivation: squared compare avoids a sqrt in the hottest path. distanceSq is recomputed
    //   once per frame at camera capture (addToBatch HEAD), so the per-particle check is two
    //   subtractions, three multiplies, one add, one compare against a plain double.
    // Too LOW (e.g. 5.0 minimum): visible particles pop out of existence at the gate edge.
    //   Tick-side uses the bounding-box center (fields x/y/z are protected in 1.21.11), which
    //   trails fast particles by at most one tick of movement (~0.5 blocks) - the effective
    //   gate can under-run by that much, killing briefly-visible fast particles.
    // Too HIGH (e.g. 200 max): gate exceeds render distance and the vanilla frustum already
    //   rejects everything culled - the optimization degrades to nothing.
    // Tolerance: camera is captured from the render side (addToBatch), so tick-side checks run
    //   with at most one-frame-old camera; a sprinting player (<= ~0.5 blocks/frame) keeps the
    //   gate within +/-0.5 blocks of the configured value. The default 96 sits far above
    //   vanilla's own particle spawn radius (mostly <= 32), so 96-128 is the practical sweet spot.

    private static final double DEFAULT_DISTANCE_SQ = 96.0 * 96.0;

    private static double cameraX = 0.0;
    private static double cameraY = 0.0;
    private static double cameraZ = 0.0;
    private static double distanceSq = DEFAULT_DISTANCE_SQ;

    private ParticleCullState() {
    }

    /**
     * Called once per frame from {@code ParticleManager.addToBatch} HEAD (render thread,
     * same thread that ticks particles), so no synchronization is needed.
     */
    public static void updateCamera(Vec3d pos) {
        cameraX = pos.x;
        cameraY = pos.y;
        cameraZ = pos.z;
        double d = Modules.particleCullDistance.floatValue();
        distanceSq = d * d;
    }

    /**
     * True when the point is farther from the camera than the configured gate.
     * Equal distance is kept (strictly greater than).
     */
    public static boolean isFar(double x, double y, double z) {
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        return dx * dx + dy * dy + dz * dz > distanceSq;
    }
}
