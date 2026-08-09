package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.texture.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Access to AtlasManager.Stitch (public class, package-private ctor/fields).
 * Stitch is the per-reload bundle put under AtlasManager.stitchKey: the
 * entries list drives both the load loop (reload()) and the sprite-map build
 * (createSpriteMap -> fillSpriteMap -> atlas.create). The atlas cache reads
 * the entries to pre-complete their preparation futures with cached results
 * and reads readyForUpload to gate its capture.
 */
@Mixin(AtlasManager.Stitch.class)
public interface AtlasStitchAccessor {

    @Accessor("entries")
    List<?> getEntries();

    @Accessor("readyForUpload")
    CompletableFuture<?> getReadyForUpload();
}
