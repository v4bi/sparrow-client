package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.mixin.Utils.PlayerEntityAccessor;
import xyz.vprolabs.sparrow.state.AttackCooldownPreserve;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BUGFIX (2026-08-02): vanilla PlayerEntity.resetTicksSince() zeroes
 * ticksSinceLastAttack whenever the held/hotbar item changes (bytecode shows
 * resetTicksSince called from tick() when selectedItem diverges). That was the
 * "swap resets your charge" bug. When AttackCooldownMixin armed a preserve
 * request for a mid-swing swap, re-apply the ticks AFTER the reset. The attack
 * path (resetTicksSinceLastAttack / beforePlayerAttack) is left untouched and
 * also disarms, so a real fresh swing starts clean.
 */
@Mixin(PlayerEntity.class)
public class AttackCooldownPreserveMixin {

    @Inject(method = "resetTicksSince", at = @At("TAIL"))
    private void sparrow_preserveAfterReset(CallbackInfo ci) {
        // resetTicksSince sets ticksSinceLastAttack (and the hand-equip timer)
        // to 0 for an item change; re-apply the preserved charge AFTER the
        // reset so the swap doesn't zero a mid-swing windup.
        int saved = AttackCooldownPreserve.isArmed()
            ? AttackCooldownPreserve.takeArmed() : -1;
        if (saved > 0) {
            ((PlayerEntityAccessor) this).setTicksSinceLastAttack(saved);
        }
    }

    @Inject(method = "resetTicksSinceLastAttack", at = @At("HEAD"))
    private void sparrow_disarmOnAttack(CallbackInfo ci) {
        // A real attack must start fresh; drop any stale preserve request.
        AttackCooldownPreserve.disarm();
    }
}