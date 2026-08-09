package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Access to AtlasManager$Entry (package-private record, cannot be named in
 * source). The atlas field is the SpriteAtlasTexture this entry feeds; the
 * atlas cache uses it to map entries to cache entries and to let the vanilla
 * fillSpriteMap -> atlas.create path run on synthesized results.
 */
@Mixin(targets = "net.minecraft.client.texture.AtlasManager$Entry")
public interface AtlasEntryAccessor {

    @Accessor("atlas")
    SpriteAtlasTexture getAtlas();
}
