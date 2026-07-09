package xyz.vprolabs.sparrow.mixin.Utils;

import xyz.vprolabs.sparrow.SparrowMod;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptions.class)
public class GameOptionsMixin {

    @Shadow @Final @Mutable
    private KeyBinding[] allKeys;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void registerSparrowKeybinds(CallbackInfo ci) {
            KeyBinding[] extended = new KeyBinding[this.allKeys.length + 5];
            System.arraycopy(this.allKeys, 0, extended, 0, this.allKeys.length);
            extended[this.allKeys.length] = SparrowMod.ZOOM_KEY;
            extended[this.allKeys.length + 1] = SparrowMod.STORAGE_PREVIEW_KEY;
            extended[this.allKeys.length + 2] = SparrowMod.TOGGLE_SNEAK_KEY;
            extended[this.allKeys.length + 3] = SparrowMod.CONSOLE_KEY;
            extended[this.allKeys.length + 4] = SparrowMod.HUD_MOVE_KEY;
            this.allKeys = extended;
            KeyBinding.updateKeysByCode();
    }
}
