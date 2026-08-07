package xyz.vprolabs.sparrow.mixin.Tweaks;

import xyz.vprolabs.sparrow.mixin.Utils.ParticleManagerAccessor;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(ParticleManager.class)
public class ParticleLimiterMixin {
@Unique private static final int MAX_TOTAL = 100;
    @Unique private static final int MAX_PER_TYPE = 30;

    @Unique private static int sparrow_liveCount = 0;
    @Unique private static final Map<Class<?>, Integer> sparrow_liveByType = new IdentityHashMap<>();
    @Unique private static final Map<Class<?>, Integer> sparrow_reconcileScratch = new IdentityHashMap<>();

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void sparrow_filterParticle(Particle particle, CallbackInfo ci) {
            String mode = Modules.particleMode.stringValue();
            if (mode == null || mode.equals("on")) {
                sparrow_liveCount++;
                sparrow_liveByType.merge(particle.getClass(), 1, Integer::sum);
                return;
            }

            if (mode.equals("off")) {
                ci.cancel();
                return;
            }

            if (!mode.equals("minimal")) return;

            if (sparrow_liveCount >= MAX_TOTAL) {
                ci.cancel();
                return;
            }
            Class<?> cls = particle.getClass();
            Integer tc = sparrow_liveByType.get(cls);
            if (tc != null && tc >= MAX_PER_TYPE) {
                ci.cancel();
                return;
            }
            sparrow_liveCount++;
            sparrow_liveByType.merge(cls, 1, Integer::sum);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sparrow_reconcileCounters(CallbackInfo ci) {
            int total = 0;
            sparrow_reconcileScratch.clear();
            for (ParticleRenderer<?> renderer : ((ParticleManagerAccessor)(Object)this).getParticles().values()) {
                for (Particle p : renderer.getParticles()) {
                    total++;
                    sparrow_reconcileScratch.merge(p.getClass(), 1, Integer::sum);
                }
            }
            sparrow_liveCount = total;
            sparrow_liveByType.clear();
            sparrow_liveByType.putAll(sparrow_reconcileScratch);
    }

    @Inject(method = "getDebugString", at = @At("RETURN"), cancellable = true)
    private void sparrow_debugMode(CallbackInfoReturnable<String> cir) {
            String mode = Modules.particleMode.stringValue();
            if (mode != null && !mode.equals("on")) {
                String existing = cir.getReturnValue();
                cir.setReturnValue((existing == null ? "" : existing) + " [" + mode + "]");
            }
    }

}
