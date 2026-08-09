package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.texture.SpriteLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/**
 * Access to AtlasManager$CompletableEntry (package-private record, cannot be
 * named in source). A CompletableEntry pairs a vanilla Entry with the future
 * that fillSpriteMap() joins and then feeds to atlas.create(StitchResult).
 * The atlas cache completes that future with a cached StitchResult, which
 * resolves the stitch's readyForUpload allOf (each element is
 * preparations.thenCompose(StitchResult::readyForUpload), and the cached
 * result's readyForUpload is already completed).
 *
 * String-form mixin target: the class is package-private, so it cannot be
 * referenced by name; Loom remaps the string to the obfuscated inner class.
 *
 * NOTE: there is deliberately NO accessor for the `entry` field. Its type
 * (AtlasManager$Entry) is package-private and unnameable, and Mixin accessors
 * match fields by EXACT type descriptor: returning Object for an Entry-typed
 * field throws "No candidates were found matching entry:Ljava/lang/Object;"
 * at mixin apply time (verified crash 2026-08-09). The atlas for each entry
 * is instead resolved through AtlasManagerAccessor.entriesByDefinitionId,
 * which pairs each definition id with its Entry and needs no unnameable
 * type. The future exposed here is the SAME instance stored in the Stitch's
 * preparations map (method_73031 puts one CompletableFuture into both), so
 * identity matching against getPreparations(Identifier) results works.
 */
@Mixin(targets = "net.minecraft.client.texture.AtlasManager$CompletableEntry")
public interface AtlasCompletableEntryAccessor {

    @Accessor("preparations")
    CompletableFuture<SpriteLoader.StitchResult> getPreparations();
}
