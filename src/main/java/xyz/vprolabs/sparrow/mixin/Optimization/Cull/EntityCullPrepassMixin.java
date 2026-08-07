package xyz.vprolabs.sparrow.mixin.Optimization.Cull;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.EntityAggregationState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prepass that runs once at the start of {@link WorldRenderer#fillEntityRenderStates}
 * to compute deterministic representative-entity (rep) ids and aggregated counts for
 * ItemEntity and ExperienceOrbEntity clusters.
 *
 * Why this exists: the original cull-and-aggregate flow picked the first-seen entity
 * in a bucket as the rep, which made the rep identity depend on iteration order and
 * chunk-boundary jitter. The rep could swap between frames, causing the visible
 * aggregate entity to flicker position and count.
 *
 * Fix: the prepass iterates ALL entities once, builds a per-bucket minimum id (the
 * stable rep), and stores the total count keyed by rep id. The cull mixin then uses
 * the rep id map as the gate; only the matching entity is allowed through, the rest
 * are culled. The aggregation redirect mixin reads the rep's count and substitutes
 * it into the rep's render state.
 *
 * Same bucket key as the original EntityRenderCullMixin (8-block cell, type-hash
 * fold) so behavior is consistent for single-bucket cases.
 */
@Mixin(WorldRenderer.class)
public abstract class EntityCullPrepassMixin {

    @Shadow private ClientWorld world;

    @Unique private static boolean sparrow_prepassLogged = false;

    @Unique
    private static long sparrow_bucketKey(int x, int z, int typeHash) {
        int bx = x >> 3;
        int bz = z >> 3;
        return ((long) bx << 32) | (bz & 0xFFFFFFFFL) ^ ((long) typeHash << 16);
    }

    // P1: two HashMaps were allocated per frame at HEAD of every render
    // frame (which cleared the state maps anyway). Reuse static temp maps —
    // single-threaded render path, cleared per frame, zero GC churn.
    @Unique private static final Map<Long, Integer> itemBucketTotals = new HashMap<>();
    @Unique private static final Map<Long, Integer> orbBucketTotals = new HashMap<>();

    @Inject(method = "fillEntityRenderStates", at = @At("HEAD"))
    private void sparrow_cullPrepass(Camera camera, Frustum frustum, RenderTickCounter tickCounter, WorldRenderState state, CallbackInfo ci) {
        if (this.world == null) return;

        Vec3d camPos = camera.getCameraPos();
        double px = camPos.x, py = camPos.y, pz = camPos.z;
        float itemDist = Modules.itemCullingDistance.floatValue();
        float entityDist = Modules.entityCullingDistance.floatValue();
        double itemDistSq = (double) itemDist * itemDist;
        double entityDistSq = (double) entityDist * entityDist;

        EntityAggregationState.clearPerFrame();
        itemBucketTotals.clear();
        orbBucketTotals.clear();

        for (Entity entity : this.world.getEntities()) {
            double dx = entity.getX() - px;
            double dy = entity.getY() - py;
            double dz = entity.getZ() - pz;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (entity instanceof ItemEntity item) {
                if (distSq > itemDistSq) {
                    EntityAggregationState.culledItemIds.add(entity.getId());
                    continue;
                }
                int itemHash = item.getStack().getItem().hashCode();
                long key = sparrow_bucketKey(entity.getBlockX(), entity.getBlockZ(), itemHash);
                int id = entity.getId();
                int currentMin = EntityAggregationState.itemBucketRepIds.getOrDefault(key, Integer.MAX_VALUE);
                if (id < currentMin) {
                    EntityAggregationState.itemBucketRepIds.put(key, id);
                }
                itemBucketTotals.merge(key, item.getStack().getCount(), Integer::sum);
                continue;
            }

            if (entity instanceof ExperienceOrbEntity orb) {
                if (distSq > entityDistSq) {
                    EntityAggregationState.culledOrbIds.add(entity.getId());
                    continue;
                }
                long key = sparrow_bucketKey(entity.getBlockX(), entity.getBlockZ(), 0);
                int id = entity.getId();
                int currentMin = EntityAggregationState.orbBucketRepIds.getOrDefault(key, Integer.MAX_VALUE);
                if (id < currentMin) {
                    EntityAggregationState.orbBucketRepIds.put(key, id);
                }
                orbBucketTotals.merge(key, orb.getValue(), Integer::sum);
                continue;
            }

            // Non-item / non-orb entity: distance cull only (no aggregation).
            if (distSq > entityDistSq) {
                EntityAggregationState.culledEntityIds.add(entity.getId());
            }
        }

        // Write rep-keyed aggregate counts. The aggregation redirect mixin reads
        // these in updateRenderState. Rep is the lowest id in the bucket so the
        // visual stays stable as long as the same set of entities is present.
        for (Map.Entry<Long, Integer> entry : itemBucketTotals.entrySet()) {
            long key = entry.getKey();
            int total = entry.getValue();
            Integer repId = EntityAggregationState.itemBucketRepIds.get(key);
            if (repId != null) {
                EntityAggregationState.aggregatedItemCounts.put(repId, total);
            }
        }
        for (Map.Entry<Long, Integer> entry : orbBucketTotals.entrySet()) {
            long key = entry.getKey();
            int total = entry.getValue();
            Integer repId = EntityAggregationState.orbBucketRepIds.get(key);
            if (repId != null) {
                EntityAggregationState.aggregatedOrbAmounts.put(repId, total);
            }
        }

        if (!sparrow_prepassLogged && !EntityAggregationState.itemBucketRepIds.isEmpty()) {
            sparrow_prepassLogged = true;
            SparrowLogger.debug("EntityCullPrepassMixin: prepass active, item buckets=" + EntityAggregationState.itemBucketRepIds.size());
        }
    }
}
