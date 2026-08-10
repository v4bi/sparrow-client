package xyz.vprolabs.sparrow.mixin.Tweaks;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces sparrow-mod sounds to play regardless of the user's sound-settings.
 *
 * WHY (2026-08-10): the death sound was inaudible despite correct playback.
 * Root cause: SparrowSounds plays via PositionedSoundInstance.ui() =
 * SoundCategory.UI, and this user's game/options.txt has soundCategory_ui:0.0
 * (UI slider muted; master 0.26). SoundSystem.play() computes the final
 * volume via getAdjustedVolume(float, SoundCategory) (call site verified in
 * bytecode: play() offset 189) as
 *   clamp(vol) * clamp(options.getSoundVolume(category)) * volumes[category],
 * so a muted category yields 0.0 and play() silently returns NOT_STARTED with
 * a DEBUG-only log ("Skipped playing sound {}, volume was zero.", offset 338)
 * — invisible at INFO level. The user explicitly wants the death sound to
 * play even when the game is muted.
 *
 * MECHANISM: a @Redirect on that exact call inside play(SoundInstance) — the
 * handler replaces the getAdjustedVolume(float, SoundCategory) invocation. A
 * flag (set at play() HEAD when the instance id is in the sparrow-mod
 * namespace) makes the handler return the raw clamped instance volume,
 * bypassing both the option sliders and the internal volumes map. The
 * instance's own volume (the death-sound-volume module) still controls
 * loudness, because the flag only changes WHICH multipliers apply, and the
 * value passed in IS the instance volume. Non-sparrow sounds reproduce the
 * vanilla computation identically (same operands, same order) via @Shadow
 * access to the two private fields.
 *
 * REJECTED ALTERNATIVES:
 *   - Switching SparrowSounds to the PLAYERS category (reference-mod style):
 *     works today (soundCategory_player:1.0) but dies the moment master or
 *     players get muted — the request is to override settings, not pick a
 *     category that happens to be loud.
 *   - @Inject RETURN on getAdjustedVolume with a (float, SoundCategory)
 *     handler: Mixin resolves the overloaded name "getAdjustedVolume" to the
 *     FIRST declared overload (the SoundInstance one) and rejects the handler
 *     with InvalidInjectionException ("Expected (SoundInstance, CIR) but
 *     found (float, SoundCategory, CIR)"). The float overload is unreachable
 *     by name, and full descriptors in @Inject(method=...) are corrupted by
 *     Loom remapping (project lesson). @Redirect with a full descriptor in
 *     the @At INVOKE target is the established working pattern in this
 *     codebase (PalettedContainerSafetyMixin, WindowTitleMixin, ...).
 *   - @Shadow-free pass-through (calling the private method from the handler)
 *     is a javac error — private methods of SoundSystem aren't callable from
 *     the mixin class at compile time.
 *
 * ASSUMPTIONS: sparrow-mod namespace == our sounds (only death sounds exist).
 * play() has a single getAdjustedVolume(float, SoundCategory) call site
 * (offset 189), so the @Redirect is unambiguous. The only other caller of the
 * float overload is getAdjustedVolume(SoundInstance), used by tick() for
 * TICKABLE sounds only — our death sound is a static PositionedSoundInstance,
 * so per-tick re-zeroing cannot happen. The volume-zero skip check (offset
 * 302) reads the redirected value, so a forced > 0 value passes it and is the
 * value applied to the channel source. If play() throws before RETURN the
 * flag stays set until the next play() call — SoundSystem.play does not
 * throw in practice.
 */
@Mixin(SoundSystem.class)
public class SoundOverrideMixin {

    @Shadow
    @Final
    private GameOptions options;

    @Shadow
    @Final
    private Object2FloatMap<SoundCategory> volumes;

    @Unique
    private boolean sparrow_forceSparrowVolume;

    // play(SoundInstance) returns PlayResult (non-void) -> CallbackInfoReturnable
    // is required even for the HEAD inject (Mixin rule: non-void target = CIR,
    // regardless of cancel/setReturnValue usage). Mixin resolves the overloaded
    // name "play" to the first declared overload, play(SoundInstance); the 2-arg
    // play(SoundInstance, int) is not targeted (return type differs).
    @Inject(method = "play", at = @At("HEAD"))
    private void sparrow_markSparrow(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
        sparrow_forceSparrowVolume = "sparrow-mod".equals(instance.getId().getNamespace());
    }

    @Inject(method = "play", at = @At("RETURN"))
    private void sparrow_clearMark(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
        sparrow_forceSparrowVolume = false;
    }

    // Replaces play()'s getAdjustedVolume(float, SoundCategory) invocation
    // (bytecode offset 189). The handler receives (this, volume, category)
    // where volume is the value play() computed as max(0, instance volume).
    @Redirect(method = "play", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"))
    private float sparrow_forceVolume(SoundSystem system, float volume, SoundCategory category) {
        if (sparrow_forceSparrowVolume) {
            return MathHelper.clamp(volume, 0.0F, 1.0F);
        }
        return MathHelper.clamp(volume, 0.0F, 1.0F)
            * MathHelper.clamp(this.options.getSoundVolume(category), 0.0F, 1.0F)
            * this.volumes.getFloat(category);
    }
}
