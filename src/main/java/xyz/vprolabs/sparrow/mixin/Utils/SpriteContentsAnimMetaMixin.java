package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.resource.metadata.TextureResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.vprolabs.sparrow.state.SpriteAnimCapture;

import java.util.List;
import java.util.Optional;

/**
 * Thin capture hook for the atlas-cache animation support (2026-08-09).
 * At the TAIL of the SpriteContents constructor, snapshots the animation
 * metadata the constructor received into SpriteAnimCapture (state package).
 * Deliberately has NO static API of its own: any direct reference from
 * another class makes Fabric Loader try to mixin-transform this class as a
 * normal class, which fails with "Illegal classload request ... cannot be
 * referenced directly" (2026-08-09). All state lives in SpriteAnimCapture.
 */
@Mixin(SpriteContents.class)
public abstract class SpriteContentsAnimMetaMixin {

    @Inject(method = "<init>(Lnet/minecraft/util/Identifier;Lnet/minecraft/client/texture/SpriteDimensions;Lnet/minecraft/client/texture/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("TAIL"))
    private void sparrow_captureAnimMeta(Identifier id, SpriteDimensions dimensions, NativeImage image,
                                         Optional<AnimationResourceMetadata> animation,
                                         List<ResourceMetadataSerializer.Value<?>> additionalMetadata,
                                         Optional<TextureResourceMetadata> texture,
                                         CallbackInfo ci) {
        if (animation != null && animation.isPresent()) {
            SpriteAnimCapture.put((SpriteContents) (Object) this, animation.get());
        }
    }
}
