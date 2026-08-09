package xyz.vprolabs.sparrow.mixin.UI.HUD;

import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudState;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class FireTimerTickMixin {

    // FIRE_TIMER_DEFAULT_TICKS = 160 (8.0s), the client-side estimate for
    // ignitions that never touch the client's collision path.
    // Source (verified in 1.21.11 bytecode): AbstractFireBlock.SET_ON_FIRE_SECONDS = 8,
    // applied via Entity.setOnFireFor(8.0f) -> setOnFireForTicks(160) -> setFireTicks(160).
    // Derivation: 8s * 20 ticks/s = 160. The server applies the same 160 through
    // LivingEntity.setOnFireForTicks, scaled by the BURNING_TIME attribute
    // (fire aspect): ceil(160 * burningTime), which the synced client-side
    // attribute container reproduces, so the estimate matches the server for
    // flint-and-steel / fire-block ignitions as well as fire aspect.
    // Too LOW -> the HUD under-runs the real burn and hides while the player
    // is still taking fire damage (worst case: fire aspect II at 320 ticks).
    // Too HIGH -> the HUD keeps counting after the server extinguished the
    // player; harmless (vanilla fire is max 15s from lava, so error stays
    // within one second-level bucket, and the flag gate hides it when the
    // server stops burning).
    // Tolerance: the countdown is only an estimate anyway; the server never
    // sends the true remaining duration (only the boolean ON_FIRE flag), so
    // a wrong initial value shifts the display, it cannot freeze it.
    private static final int FIRE_TIMER_DEFAULT_TICKS = 160;

    // The CLIENT's fireTicks cannot be used as a countdown source in 1.21.11:
    // Entity.baseTick() calls extinguish() on every client tick, zeroing the
    // field before movement. The only client-side writers are movement
    // collision events (AbstractFireBlock.igniteEntity -> setFireTicks(160),
    // Entity.igniteByLava -> setFireTicks(300)), which re-fire EVERY tick
    // while the player is inside fire/lava. Reading getFireTicks() at render
    // time therefore produced two bugs: a frozen "8.0s" while standing in
    // fire (the value is 160 every frame), and nothing at all for ignitions
    // with no client-side collision (flint & steel, fire aspect, arrows),
    // which only sync the boolean ON_FIRE flag.
    // This mixin runs at tick() HEAD, before baseTick() wipes the field, so
    // the collision-set value survives one tick and can be captured. It then
    // counts down once per tick in lockstep with the server's real burn.
    // Rejected: injecting into Entity.setFireTicks to catch every client
    // write (the collision handler re-sets 160 every tick, which would freeze
    // the countdown at 8.0s again); decrementing in the render mixin (render
    // rate != tick rate, the countdown would drift at high FPS).
    @Inject(method = "tick", at = @At("HEAD"))
    private void sparrow_updateFireCountdown(CallbackInfo ci) {
        if (!Modules.fireTimer.child("fire-timer-enabled").isEnabled()) {
            return;
        }
        LivingEntity living = (LivingEntity) (Object) this;
        // Captured BEFORE the wipe: > 0 only while standing inside fire
        // (160 x burningTime) or lava (300 x burningTime).
        int captured = living.getFireTicks();

        if (HudState.fireTicks < 0) {
            if (!living.isOnFire()) {
                return;
            }
            // Ignition: use the collision-set duration when visible, else
            // estimate from the synced BURNING_TIME attribute. Note
            // isOnFire() is true here via fireTicks > 0 even before the
            // server-synced flag arrives, so block ignitions start instantly.
            HudState.fireTicks = captured > 0 ? captured : estimateFireTicks(living);
        } else if (!living.isOnFire()) {
            // Burn ended, or stale state after respawn: hide.
            HudState.reset();
            return;
        } else if (captured > 0 && HudState.fireTicks <= 0) {
            // Countdown expired while still inside fire/lava: start the next
            // burn cycle. Matches vanilla, which re-ignites every 8s while
            // the player stays in the block.
            HudState.fireTicks = captured;
        }
        if (HudState.fireTicks > 0) {
            HudState.fireTicks--;
        }
    }

    private static int estimateFireTicks(LivingEntity living) {
        double burning = living.getAttributeValue(EntityAttributes.BURNING_TIME);
        return Math.max(1, (int) Math.ceil(FIRE_TIMER_DEFAULT_TICKS * burning));
    }
}
