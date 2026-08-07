package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.mixin.Utils.PlayerEntityAccessor;
import xyz.vprolabs.sparrow.state.AttackCooldownPreserve;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BUGFIX (2026-08-02): the old handler zeroed the attack charge whenever the
 * selected slot changed, by clamping ticksSinceLastAttack to the full period
 * only AFTER `ticks > ceil(period)` — i.e. when the cooldown was ALREADY done.
 * Bad (Case: ticks < period gives the same) — it manifested as "swap resets
 * your charge mid-swing". Now we ARM a preserve request when the change is
 * mid-swing (0 < ticks < period); AttackCooldownPreserveMixin re-applies it
 * when PlayerEntity.resetTicksSince() actually zeroes the counter (the exact
 * vanilla swap path). This keeps the cooldown progress across a hotbar swap.
 */
@Mixin(PlayerInventory.class)
public class AttackCooldownMixin {

    @Unique
    private int sparrow_lastSlot = -1;

    @Inject(method = "setSelectedSlot", at = @At("TAIL"))
    private void sparrow_onSlotChange(int slot, CallbackInfo ci) {
            if (slot == sparrow_lastSlot) return;
            sparrow_lastSlot = slot;
            PlayerEntity player = ((PlayerInventory)(Object)this).player;
            int ticks = ((PlayerEntityAccessor) player).getTicksSinceLastAttack();
            // getAttackCooldownProgressPerTick() returns the full cooldown
            // PERIOD in ticks (vanilla: 1/ATTACK_SPEED*20), proven via javap
            // on PlayerEntity.class (bytecode computes 1/attr*20). A charge is
            // mid-windup while 0 < ticks < period; only preserve then — a swap
            // after the cooldown finished has nothing left to preserve.
            float period = player.getAttackCooldownProgressPerTick();
            int fullTicks = (int) Math.ceil(period);
            if (fullTicks > 1 && ticks > 0 && ticks < fullTicks) {
                AttackCooldownPreserve.arm(ticks);
            } else {
                AttackCooldownPreserve.disarm();
            }
    }
}