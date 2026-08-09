package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Access to AtlasManager.entriesByDefinitionId (Map<Identifier, Entry>).
 * The Entry type is package-private and unnameable, so the accessor returns
 * the erased Map with Object values: Mixin matches accessor field types by
 * erasure (Map == Map), unlike the failing Object-return for the Entry-typed
 * CompletableEntry.entry field. The map keys are the SAME definition
 * identifiers that key the Stitch's preparations map (prepareSharedState
 * iterates entriesByDefinitionId and keys both structures by that id), so a
 * key from this map bridges directly to the public
 * Stitch.getPreparations(Identifier) to fetch the per-atlas future.
 */
@Mixin(AtlasManager.class)
public interface AtlasManagerAccessor {

    @Accessor("entriesByDefinitionId")
    Map<Identifier, Object> getEntriesByDefinitionId();
}
