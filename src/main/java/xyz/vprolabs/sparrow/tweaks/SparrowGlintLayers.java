package xyz.vprolabs.sparrow.tweaks;

import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.NativeImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;

@Environment(EnvType.CLIENT)
public class SparrowGlintLayers {

    private static final Identifier VANILLA_ITEM_GLINT = Identifier.of("minecraft", "textures/misc/enchanted_glint_item.png");
    private static final Identifier VANILLA_ARMOR_GLINT = Identifier.of("minecraft", "textures/misc/enchanted_glint_armor.png");
    private static NativeImageBackedTexture sparrowItemTexture;
    private static NativeImageBackedTexture sparrowArmorTexture;
    private static int sparrow_lastTintKey = Integer.MIN_VALUE;

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        SparrowLogger.debug("SparrowGlintLayers: init()");
        refresh();
    }

    public static synchronized void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) return;

        int tintKey = tintKey();
        if (tintKey == sparrow_lastTintKey) return;
        sparrow_lastTintKey = tintKey;

        if (Modules.customGlint.isEnabled()) {
            int r = clampChannel(Modules.glintR.intValue());
            int g = clampChannel(Modules.glintG.intValue());
            int b = clampChannel(Modules.glintB.intValue());
            replaceGlint(client, VANILLA_ITEM_GLINT, sparrowItemTexture, r, g, b, tex -> sparrowItemTexture = tex);
            replaceGlint(client, VANILLA_ARMOR_GLINT, sparrowArmorTexture, r, g, b, tex -> sparrowArmorTexture = tex);
            SparrowLogger.info("SparrowGlintLayers: applied tint RGB(" + r + "," + g + "," + b + ")");
        } else {
            restoreGlint(client, VANILLA_ITEM_GLINT, sparrowItemTexture, tex -> sparrowItemTexture = tex);
            restoreGlint(client, VANILLA_ARMOR_GLINT, sparrowArmorTexture, tex -> sparrowArmorTexture = tex);
            SparrowLogger.info("SparrowGlintLayers: restored vanilla glint");
        }
    }

    private static void replaceGlint(MinecraftClient client, Identifier id, NativeImageBackedTexture existing, int r, int g, int b, java.util.function.Consumer<NativeImageBackedTexture> setter) {
        if (existing != null) {
            existing.close();
        }
        try (InputStream input = client.getResourceManager().open(id)) {
            try (NativeImage source = NativeImage.read(input)) {
                NativeImage tinted = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        int argb = source.getColorArgb(x, y);
                        int alpha = (argb >>> 24) & 0xFF;
                        if (alpha == 0) {
                            tinted.setColorArgb(x, y, 0);
                        } else {
                            tinted.setColorArgb(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                        }
                    }
                }
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "sparrow_glint_" + id.getPath().replace('/', '_'), tinted);
                client.getTextureManager().registerTexture(id, tex);
                setter.accept(tex);
            }
        } catch (IOException e) {
            SparrowLogger.error("SparrowGlintLayers: failed to load " + id + ": " + e.getMessage());
        }
    }

    private static void restoreGlint(MinecraftClient client, Identifier id, NativeImageBackedTexture existing, java.util.function.Consumer<NativeImageBackedTexture> setter) {
        if (existing != null) {
            existing.close();
        }
        try (InputStream input = client.getResourceManager().open(id)) {
            try (NativeImage source = NativeImage.read(input)) {
                NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
                for (int y = 0; y < source.getHeight(); y++) {
                    for (int x = 0; x < source.getWidth(); x++) {
                        copy.setColorArgb(x, y, source.getColorArgb(x, y));
                    }
                }
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "sparrow_glint_" + id.getPath().replace('/', '_'), copy);
                client.getTextureManager().registerTexture(id, tex);
                setter.accept(tex);
            }
        } catch (IOException e) {
            SparrowLogger.error("SparrowGlintLayers: failed to restore " + id + ": " + e.getMessage());
        }
    }

    private static int clampChannel(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    private static int tintKey() {
        int base = (Modules.customGlint.isEnabled() ? 1 : 0) << 24;
        if (!Modules.customGlint.isEnabled()) return base;
        return base | (clampChannel(Modules.glintR.intValue()) << 16) | (clampChannel(Modules.glintG.intValue()) << 8) | clampChannel(Modules.glintB.intValue());
    }

    /**
     * Release the static references to the custom glint textures. Called from
     * {@code DisconnectClearMixin} when the player leaves a world. Without this,
     * the ~8 KB of native image data stays referenced across sessions even though
     * the world they were tinted for is gone.
     */
    public static synchronized void clearStaticTextures() {
        sparrow_lastTintKey = Integer.MIN_VALUE;
        // The textures themselves are owned by the TextureManager; closing them
        // here would double-close if the texture manager also closes them on
        // world unload. We just null the static refs so they can be GC'd.
        sparrowItemTexture = null;
        sparrowArmorTexture = null;
    }

}
