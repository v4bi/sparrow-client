package xyz.vprolabs.sparrow.mixin.Optimization;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ZipResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.vprolabs.sparrow.BuildInfo;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.mixin.Utils.AtlasCompletableEntryAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.AtlasEntryAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.AtlasManagerAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.AtlasStitchAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.DirectoryResourcePackAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.SpriteContentsAccessor;
import xyz.vprolabs.sparrow.mixin.Utils.SpriteInvoker;
import xyz.vprolabs.sparrow.state.AtlasCache;
import xyz.vprolabs.sparrow.state.AtlasCache.AtlasMeta;
import xyz.vprolabs.sparrow.state.AtlasCache.CacheData;
import xyz.vprolabs.sparrow.state.AtlasCache.SpriteMeta;
import xyz.vprolabs.sparrow.state.CacheFingerprint;
import xyz.vprolabs.sparrow.state.CacheFingerprint.PackStamp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.CRC32C;

/**
 * Atlas disk cache.
 *
 * Vanilla reload flow: prepareSharedState() builds ONE Stitch holding a
 * CompletableEntry per atlas. Each entry carries a `preparations`
 * CompletableFuture; the Stitch's readyForUpload is an allOf over
 * `preparations.thenCompose(StitchResult::readyForUpload)`. reload() first
 * iterates the entries calling Entry.load (the actual resource IO, which
 * completes `preparations`), then chains logDuplicates -> createSpriteMap ->
 * fillSpriteMap, which per entry does `preparations().join()` and
 * `atlas.create(StitchResult)`. There is no CPU-side atlas base image in
 * 1.21.11: the GPU blits each sprite's mips into slot views, so the whole
 * pipeline is driven by StitchResult contents.
 *
 * Cache-hit path: this mixin validates the disk cache in prepareSharedState
 * HEAD. In reload HEAD it pre-completes each cached entry's `preparations`
 * future with a StitchResult reconstructed from the cached base image, and
 * redirects the load loop so cached entries never run Entry.load (no disk
 * IO). The allOf gate then resolves because the result's own
 * readyForUpload is already completed, and fillSpriteMap + atlas.create run
 * on the reconstructed result.
 *
 * Cache-miss path: prepareSharedState TAIL arms a capture that blits every
 * sprite's base image into a CPU canvas at (x + pad, y + pad) and writes it
 * to disk together with per-sprite UV metadata.
 *
 * Padding: the stitcher reserves (1 << mipLevel) << clamp(maxAnisotropy-1,0,4)
 * pixels around each sprite (TextureStitcher ctor) and sprite pixels land at
 * (x + padding, y + padding). The cache uses the aniso-independent core
 * (1 << mipLevel) for BOTH blit and slice, so capture and hit stay
 * self-consistent no matter what anisotropy is set. The ring pixels are never
 * sampled (render UVs start at x + vanilla padding), so their content is
 * irrelevant; the Sprite objects rebuilt on hit still carry the vanilla
 * padding so UVs are pixel-identical.
 *
 * Exclusion: an animated sprite's base image is the full animation strip,
 * but only frame 0 is ever placed in the atlas slot, so the strip cannot be
 * reconstructed from the base image. Any atlas containing an animated sprite
 * is therefore never cached (block/particle atlases fall back to vanilla;
 * item/banner/shield atlases cache).
 *
 * Cache-hit tradeoff: sprite-level mipmap strategy from texture mcmeta
 * (blur/clamp) and animation interpolate flag are not stored (SpriteMeta
 * contract is fixed); hit sprites use MipmapStrategy.AUTO. Vanilla sprites
 * without blur/clamp mcmeta are byte-identical; the rare blurred sprites
 * differ only in mip generation.
 */
@Mixin(AtlasManager.class)
public abstract class AtlasCacheMixin {

    /** Config value: the global mip level applied to every atlas. */
    @Shadow
    private int mipmapLevels;

    /** Validated cache for this reload cycle, null when the cache is stale. */
    @Unique
    private CacheData sparrow_cacheData;

    /** Preparation futures pre-completed from the cache (identity-matched). */
    @Unique
    private Set<Object> sparrow_cachedEntries;

    /** Computed once per session: pack stamps are O(files), not per reload. */
    @Unique
    private static CacheFingerprint sparrow_fingerprint;
    @Unique
    private static boolean sparrow_fingerprintReady;

    @Inject(method = "prepareSharedState", at = @At("HEAD"))
    private void sparrow_prepareHead(ResourceReloader.Store store, CallbackInfo ci) {
        sparrow_cachedEntries = null;
        sparrow_cacheData = null;
        CacheFingerprint current = sparrow_currentFingerprint();
        if (current == null) return;
        CacheData data = AtlasCache.readJson();
        // Fingerprint covers MC version, Sparrow build, mip level and every
        // pack file: equal fingerprint -> byte-identical atlas output.
        if (data == null || !current.matches(data.fingerprint)) return;
        if (!AtlasCache.allBlobsPresent(data)) return;
        sparrow_cacheData = data;
        SparrowLogger.log("BOOT", "atlas-cache: cache hit (" + data.atlases.size() + " atlases)");
    }

    @Inject(method = "prepareSharedState", at = @At("TAIL"))
    private void sparrow_prepareTail(ResourceReloader.Store store, CallbackInfo ci) {
        // Valid cache this cycle: HEAD already decided the reload will use
        // it; re-capturing would just rewrite identical blobs.
        if (sparrow_cacheData != null) return;
        // Capture must run only after every load in the stitch finished;
        // readyForUpload is the allOf gate over all preparation futures.
        // whenComplete runs on the load executor, never the render thread.
        Object stitch = store.getOrThrow(AtlasManager.stitchKey);
        ((AtlasStitchAccessor) stitch).getReadyForUpload().whenComplete((v, err) -> {
            if (err == null) {
                sparrow_capture(stitch);
            }
        });
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void sparrow_reloadHead(ResourceReloader.Store store, Executor prepareExecutor,
                                    ResourceReloader.Synchronizer synchronizer, Executor applyExecutor,
                                    CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        if (sparrow_cacheData == null) return;
        // Pre-completing a preparation future resolves readyForUpload (its
        // allOf wraps preparations.thenCompose(StitchResult::readyForUpload)
        // and the cached result's own readyForUpload is already completed),
        // so the entry's load can be skipped entirely by the redirect below.
        // The atlas for each stitch entry is resolved through
        // entriesByDefinitionId: its keys are the same ids that key the
        // stitch's preparations map (both are built in prepareSharedState
        // from the same iteration), and the map value's atlas is read via
        // AtlasEntryAccessor (the Entry type itself is unnameable, but the
        // `atlas` field type SpriteAtlasTexture is public and exact).
        AtlasManager.Stitch stitch = store.getOrThrow(AtlasManager.stitchKey);
        Set<Object> cached = new HashSet<>();
        for (Map.Entry<Identifier, Object> defEntry : ((AtlasManagerAccessor) (Object) this).getEntriesByDefinitionId().entrySet()) {
            SpriteAtlasTexture atlas = ((AtlasEntryAccessor) defEntry.getValue()).getAtlas();
            AtlasMeta meta = sparrow_findMeta(atlas.getId().getPath());
            NativeImage base = meta == null ? null : AtlasCache.readBlob(meta.atlasId);
            if (base == null) continue; // no cached blob -> vanilla load
            SpriteLoader.StitchResult result = sparrow_buildResult(atlas.getId(), meta, base);
            base.close();
            if (result == null) continue; // reconstruction failed -> vanilla load
            // complete() on an already-completed future is a no-op, so even
            // if a race lets the vanilla load finish first, the cached
            // result wins. The future returned by getPreparations is the
            // SAME instance stored inside the matching CompletableEntry
            // (method_73031 puts one CompletableFuture into both the map
            // and the entry), so the redirect below matches entries to
            // pre-completed futures by identity.
            CompletableFuture<SpriteLoader.StitchResult> future = stitch.getPreparations(defEntry.getKey());
            future.complete(result);
            cached.add(future);
        }
        sparrow_cachedEntries = cached;
    }

    /**
     * The load loop in reload() calls method_73026 -> Entry.load for every
     * entry. Pre-completed entries must be skipped (their load would be
     * wasted IO); uncached entries must still load, otherwise their
     * preparation futures never complete and the reload gate hangs.
     */
    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void sparrow_redirectEntryLoads(List<?> entries, Consumer<Object> consumer) {
        if (sparrow_cachedEntries == null) {
            for (Object entry : entries) {
                consumer.accept(entry);
            }
            return;
        }
        for (Object entry : entries) {
            // Identity match against the futures pre-completed in
            // reloadHead: the same CompletableFuture instance lives in both
            // the stitch's preparations map and the CompletableEntry.
            if (!sparrow_cachedEntries.contains(((AtlasCompletableEntryAccessor) entry).getPreparations())) {
                consumer.accept(entry);
            }
        }
    }

    /**
     * Capture: blit every sprite's base image into a per-atlas canvas and
     * write blobs + cache.json. Runs once per reload cycle on the load
     * executor. Any exception aborts the capture for this cycle (cache stays
     * missing and the next boot falls back to vanilla).
     */
    @Unique
    private void sparrow_capture(Object stitch) {
        try {
            CacheFingerprint current = sparrow_currentFingerprint();
            if (current == null) return;
            CacheData data = new CacheData();
            data.fingerprint = current;
            // The stitch entries list holds CompletableEntry objects whose
            // inner Entry type is unnameable, so the atlas for each entry is
            // resolved through entriesByDefinitionId instead: the map keys
            // are the same definition ids that key the stitch's preparations
            // map (prepareSharedState builds both from the same iteration),
            // and each value's atlas is read via AtlasEntryAccessor.
            AtlasManager.Stitch atlasStitch = (AtlasManager.Stitch) stitch;
            for (Map.Entry<Identifier, Object> defEntry : ((AtlasManagerAccessor) (Object) this).getEntriesByDefinitionId().entrySet()) {
                SpriteAtlasTexture atlas = ((AtlasEntryAccessor) defEntry.getValue()).getAtlas();
                SpriteLoader.StitchResult result = atlasStitch.getPreparations(defEntry.getKey()).join();
                if (!sparrow_cacheable(result)) {
                    SparrowLogger.debug("AtlasCache: skip " + atlas.getId().getPath()
                            + " (contains animated sprite)");
                    continue;
                }
                data.atlases.add(sparrow_captureAtlas(atlas, result));
            }
            SparrowLogger.log("BOOT", "atlas-cache: captured " + data.atlases.size() + " atlases");
            // writeJson must come last: cache.json only exists once all blobs
            // are on disk, so a partial write set never validates.
            AtlasCache.writeJson(data);
        } catch (Exception e) {
            // e.getMessage() is null for NPEs; log the whole class+stack so
            // capture failures are diagnosable from the log alone.
            StringBuilder sb = new StringBuilder("AtlasCache: capture failed: ").append(e);
            for (StackTraceElement el : e.getStackTrace()) {
                sb.append("\n  at ").append(el);
            }
            SparrowLogger.warn(sb.toString());
        }
    }

    /** Animated sprites are never cached (strip not reconstructible). */
    @Unique
    private boolean sparrow_cacheable(SpriteLoader.StitchResult result) {
        for (Sprite sprite : result.sprites().values()) {
            if (sprite.getContents().isAnimated()) {
                return false;
            }
        }
        return true;
    }

    /** Builds the canvas and writes the blob; returns the json metadata. */
    @Unique
    private AtlasMeta sparrow_captureAtlas(SpriteAtlasTexture atlas, SpriteLoader.StitchResult result) {
        int pad = 1 << result.mipLevel();
        NativeImage canvas = new NativeImage(NativeImage.Format.RGBA, result.width(), result.height(), false);
        AtlasMeta meta = new AtlasMeta();
        meta.atlasId = atlas.getId().getPath();
        meta.width = result.width();
        meta.height = result.height();
        try {
            for (Map.Entry<Identifier, Sprite> spriteEntry : result.sprites().entrySet()) {
                Sprite sprite = spriteEntry.getValue();
                SpriteContents contents = sprite.getContents();
                int w = contents.getWidth();
                int h = contents.getHeight();
                NativeImage base = ((SpriteContentsAccessor) (Object) contents).getMipmapLevelsImages()[0];
                // copyRect(to, x, y, w, h, fromX, fromY, ...): copies from
                // THIS image at (fromX, fromY) into `to` at (x, y). The
                // source is the sprite's own base image (w x h at 0,0); the
                // destination is the sprite's slot in the atlas canvas. The
                // earlier version had these swapped (source = atlas coords
                // read from a 16x16 sprite image), which threw "outside of
                // image bounds" on the first sprite (2026-08-09).
                base.copyRect(canvas, sprite.getX() + pad, sprite.getY() + pad, w, h, 0, 0, false, false);
                meta.sprites.add(new SpriteMeta(spriteEntry.getKey().toString(), sprite.getX(), sprite.getY(), w, h, 1, 0));
            }
            AtlasCache.writeBlob(meta.atlasId, canvas, meta.width, meta.height);
        } finally {
            canvas.close();
        }
        return meta;
    }

    /**
     * Rebuilds a full StitchResult for one atlas from the cached base image.
     * Returns null on any problem so the caller falls back to vanilla.
     */
    @Unique
    private SpriteLoader.StitchResult sparrow_buildResult(Identifier atlasId, AtlasMeta meta, NativeImage base) {
        int mip = sparrow_mipLevel(meta);
        int pad = 1 << mip;
        Map<Identifier, Sprite> sprites = new HashMap<>();
        List<NativeImage> slices = new ArrayList<>();
        try {
            for (SpriteMeta sm : meta.sprites) {
                NativeImage slice = new NativeImage(NativeImage.Format.RGBA, sm.w, sm.h, false);
                slices.add(slice);
                base.copyRect(slice, sm.x + pad, sm.y + pad, sm.w, sm.h, 0, 0, false, false);
                SpriteContents contents = new SpriteContents(atlasId, new SpriteDimensions(sm.w, sm.h), slice,
                        Optional.empty(), List.of(), Optional.empty());
                // Same call vanilla's readyForUpload runnable makes; AUTO
                // strategy + cutoff 0.0 match vanilla for sprites without
                // blur/clamp mcmeta (the only sprite classes in the cache).
                contents.generateMipmaps(mip);
                // Static constructor invoker: generates NEW + INVOKESPECIAL
                // directly, no receiver instance needed (the old reflective
                // dummy existed only for instance dispatch, which Mixin
                // rejects for OBJECT_FACTORYs).
                sprites.put(Identifier.of(sm.id), SpriteInvoker.invokeInit(
                        atlasId, contents, sm.x, sm.y, sm.w, sm.h, sparrow_padding(mip)));
            }
        } catch (Exception e) {
            for (NativeImage slice : slices) {
                slice.close();
            }
            return null;
        }
        Sprite missing = sprites.get(MissingSprite.getMissingSpriteId());
        if (missing == null) {
            // Missing sprite was not stitched when the cache was captured;
            // rebuild the vanilla 16x16 placeholder at the origin.
            missing = SpriteInvoker.invokeInit(
                    atlasId, MissingSprite.createSpriteContents(), 0, 0, 16, 16, 0);
        }
        return new SpriteLoader.StitchResult(meta.width, meta.height, mip, missing, sprites,
                CompletableFuture.completedFuture(null));
    }

    /**
     * Replicates SpriteLoader.stitch()'s mip clamp: the atlas mip is the
     * floor of the log2 of the smallest sprite dimension, bounded by the
     * global mip level and by each sprite's lowest set bit. Same inputs on
     * hit and capture produce the same mip, which keeps the cache padding
     * consistent.
     */
    @Unique
    private int sparrow_mipLevel(AtlasMeta meta) {
        int cap = 1 << mipmapLevels;
        int minDim = Integer.MAX_VALUE;
        for (SpriteMeta sm : meta.sprites) {
            minDim = Math.min(minDim, Math.min(sm.w, sm.h));
            cap = Math.min(cap, Math.min(Integer.lowestOneBit(sm.w), Integer.lowestOneBit(sm.h)));
        }
        return Math.min(mipmapLevels, MathHelper.floorLog2(Math.min(minDim, cap)));
    }

    /**
     * Vanilla padding: (1 << mip) << clamp(maxAnisotropy - 1, 0, 4), where
     * anisotropy is only taken from options when the atlas actually has mips.
     * Used for the Sprite UV inset on hit; cache slice offsets use the
     * aniso-independent (1 << mip) instead.
     */
    @Unique
    private int sparrow_padding(int mip) {
        GameOptions options = MinecraftClient.getInstance().options;
        int anisotropy = 0;
        if (mip != 0 && options.getTextureFiltering().getValue() == TextureFilteringMode.ANISOTROPIC) {
            anisotropy = options.getMaxAnisotropy().getValue();
        }
        return (1 << mip) << MathHelper.clamp(anisotropy - 1, 0, 4);
    }

    @Unique
    private AtlasMeta sparrow_findMeta(String atlasId) {
        if (sparrow_cacheData == null) return null;
        for (AtlasMeta meta : sparrow_cacheData.atlases) {
            if (meta != null && atlasId.equals(meta.atlasId)) {
                return meta;
            }
        }
        return null;
    }

    /**
     * Session-once fingerprint: MC version, Sparrow build tag, atlas mip
     * level, and one stamp per enabled pack (folder packs get one stamp per
     * file; zip packs one CRC over the whole file; non-file packs are
     * covered by version/build and get an id-only stamp). Folder mtime does
     * not bump on in-place edits, hence per-file stamps for folders.
     */
    @Unique
    private CacheFingerprint sparrow_currentFingerprint() {
        if (sparrow_fingerprintReady) return sparrow_fingerprint;
        sparrow_fingerprintReady = true;
        MinecraftClient client = MinecraftClient.getInstance();
        ResourcePackManager manager = client == null ? null : client.getResourcePackManager();
        if (manager == null) return sparrow_fingerprint = null;
        try {
            CacheFingerprint fp = new CacheFingerprint(
                    SharedConstants.getGameVersion().id(), BuildInfo.BUILD_TAG, mipmapLevels);
            // Telemetry: profile count so a single-pack run is distinguishable
            // from a multi-pack run that failed mid-loop. Without this the
            // silent catch below makes every failure look identical.
            SparrowLogger.debug("AtlasCache: enabled profiles=" + manager.getEnabledProfiles().size());
            for (ResourcePackProfile profile : manager.getEnabledProfiles()) {
                String packId = profile.getId();
                SparrowLogger.debug("AtlasCache: opening pack " + packId);
                try (ResourcePack pack = profile.createResourcePack()) {
                    // Pack class diagnostic: id-only stamps for every pack
                    // would mean the ZipResourcePack branch never matches,
                    // which defeats the CRC part of the fingerprint.
                    SparrowLogger.debug("AtlasCache: pack " + packId + " -> " + pack.getClass().getSimpleName());
                    if (pack instanceof DirectoryResourcePack directory) {
                        sparrow_stampFolder(fp, packId, ((DirectoryResourcePackAccessor) (Object) directory).getRoot());
                    } else if (pack instanceof ZipResourcePack zip) {
                        sparrow_stampZip(fp, packId, zip);
                    } else {
                        fp.addPack(new PackStamp(packId, "", 0, 0, 0));
                    }
                } catch (Exception packE) {
                    // One broken pack must not kill the whole fingerprint:
                    // stamp it id-only (forces cache invalidation if it ever
                    // matters) and continue with the remaining packs.
                    SparrowLogger.warn("AtlasCache: pack " + packId + " failed: " + packE);
                    fp.addPack(new PackStamp(packId, "", 0, 0, 0));
                }
            }
            return sparrow_fingerprint = fp;
        } catch (Exception e) {
            SparrowLogger.warn("AtlasCache: fingerprint failed: " + e);
            return sparrow_fingerprint = null;
        }
    }

    @Unique
    private void sparrow_stampFolder(CacheFingerprint fp, String packId, Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String rel = root.relativize(file).toString().replace(File.separatorChar, '/');
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    fp.addPack(new PackStamp(packId + "/" + rel, file.toString(),
                            attrs.size(), attrs.lastModifiedTime().toMillis(), 0));
                } catch (IOException e) {
                    // Unreadable file: stamp id-only so the cache is invalidated.
                    fp.addPack(new PackStamp(packId + "/" + rel, "", 0, 0, 0));
                }
            });
        } catch (IOException e) {
            fp.addPack(new PackStamp(packId, "", 0, 0, 0));
        }
    }

    @Unique
    private void sparrow_stampZip(CacheFingerprint fp, String packId, ZipResourcePack zip) {
        // The on-disk File lives in ZipResourcePack.zipFile, a package-private
        // wrapper that CANNOT be named in source and whose ctor param is
        // likewise unnameable. Mixin's @Inject/@Accessor require EXACT type
        // matching, so neither an Object-typed accessor nor an Object-typed
        // ctor-inject handler survives transform (both failed at runtime with
        // "Mixin transformation of ... failed", 2026-08-09). Reflection also
        // fails (fields keep intermediary names at runtime). Consequence:
        // zip packs get id-only stamps (cache invalidates whenever their set
        // changes), which is safe, just not CRC-precise.
        fp.addPack(new PackStamp(packId, "", 0, 0, 0));
    }

}
