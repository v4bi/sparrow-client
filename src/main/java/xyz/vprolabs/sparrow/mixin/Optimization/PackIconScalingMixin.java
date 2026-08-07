package xyz.vprolabs.sparrow.mixin.Optimization;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

@Mixin(PackScreen.class)
public class PackIconScalingMixin {

    @Unique
    private static boolean sparrow_packIconLogged = false;

    @Redirect(
        method = "loadPackIcon",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/texture/NativeImage;read(Ljava/io/InputStream;)Lnet/minecraft/client/texture/NativeImage;"
        )
    )
    private NativeImage sparrow_scalePackIcon(InputStream is) throws IOException {
            if (!Modules.packIconScaling.isEnabled()) return NativeImage.read(is);
            NativeImage image = NativeImage.read(is);
            if (image == null) return null;

            int w = image.getWidth();
            int h = image.getHeight();

            if (w <= 64 && h <= 64) return image;

            int newW, newH;
            if (w >= h) {
                newW = 64;
                newH = Math.max(1, h * 64 / w);
            } else {
                newH = 64;
                newW = Math.max(1, w * 64 / h);
            }

            NativeImage scaled = new NativeImage(newW, newH, false);
            image.resizeSubRectTo(0, 0, w, h, scaled);
            image.close();

            if (!sparrow_packIconLogged) {
                sparrow_packIconLogged = true;
                SparrowLogger.info("[Sparrow MemoryFix] Resource pack icon scaling enabled");
            }
            SparrowLogger.info("[Sparrow MemoryFix] Scaled pack icon from " + w + "x" + h + " to " + newW + "x" + newH);

            return scaled;
    }
}
