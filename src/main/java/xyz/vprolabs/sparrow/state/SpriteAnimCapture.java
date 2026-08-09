package xyz.vprolabs.sparrow.state;

import net.minecraft.client.resource.metadata.AnimationFrameResourceMetadata;
import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.SpriteContents;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Animation metadata capture buffer for the atlas cache (2026-08-09).
 *
 * The atlas cache stores animated sprites as full animation strips, but a
 * strip alone is not enough to rebuild the SpriteContents: the derived
 * Animation object (frame count, per-frame times, interpolate, width/height
 * overrides) can only come from the original metadata. The SpriteContents
 * 6-arg constructor derives `animation` from its Optional<AnimationResourceMetadata>
 * parameter (verified in bytecode: the ctor maps the optional through
 * method_65869 -> createAnimation), so the cache hit path re-passes the
 * captured metadata and gets an identical Animation.
 *
 * MUST live outside the mixin package: this class is referenced directly by
 * AtlasCacheMixin, and Fabric Loader mixin-transforms every class under the
 * mixin package, so a public static helper there dies with "Illegal
 * classload request ... cannot be referenced directly" (2026-08-09).
 * The thin SpriteContentsAnimMetaMixin (mixin package) only calls put();
 * AtlasCacheMixin calls reset()/take() on this class.
 *
 * The capture map is keyed by SpriteContents identity and drained by
 * AtlasCacheMixin.sparrow_animMeta() right after each atlas's load completes
 * (the stitch's readyForUpload gate). Entries survive only between a sprite's
 * construction and the capture that drains it; the map is cleared before each
 * reload cycle (SpriteContents objects are per-reload, so stale entries from
 * the previous cycle can never be matched).
 */
public final class SpriteAnimCapture {

    /** Immutable snapshot of the animation metadata a SpriteContents was built from. */
    public static final class SpriteAnimMeta {
        public final boolean interpolate;
        public final int defaultFrameTime;
        public final int width;      // -1 = mcmeta absent
        public final int height;     // -1 = mcmeta absent
        public final List<AnimationFrameResourceMetadata> frames; // may be empty

        public SpriteAnimMeta(AnimationResourceMetadata m) {
            this.interpolate = m.interpolate();
            this.defaultFrameTime = m.defaultFrameTime();
            this.width = m.width().orElse(-1);
            this.height = m.height().orElse(-1);
            this.frames = m.frames().orElse(List.of());
        }
    }

    private static final ConcurrentMap<SpriteContents, SpriteAnimMeta> CAPTURED = new ConcurrentHashMap<>();

    private SpriteAnimCapture() { }

    /** Clears the previous reload's captures (called per prepareSharedState cycle). */
    public static void reset() {
        CAPTURED.clear();
    }

    /** Records the metadata a freshly constructed SpriteContents was given. */
    public static void put(SpriteContents contents, AnimationResourceMetadata animation) {
        // Identity key: a sprite and its metadata have the same lifetime,
        // and SpriteContents has no equals() override worth relying on.
        CAPTURED.put(contents, new SpriteAnimMeta(animation));
    }

    /** Removes and returns the metadata for the given contents, or null. */
    public static SpriteAnimMeta take(SpriteContents contents) {
        return CAPTURED.remove(contents);
    }
}
