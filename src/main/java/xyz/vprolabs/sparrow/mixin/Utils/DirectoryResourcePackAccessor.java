package xyz.vprolabs.sparrow.mixin.Utils;

import net.minecraft.resource.DirectoryResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

/**
 * DirectoryResourcePack.root is private. The atlas-cache fingerprint stamps
 * every file inside folder packs (mtime does not bump on in-place edits, so
 * per-file stamps are the only reliable change signal), which requires the
 * pack directory path.
 */
@Mixin(DirectoryResourcePack.class)
public interface DirectoryResourcePackAccessor {

    @Accessor("root")
    Path getRoot();
}
