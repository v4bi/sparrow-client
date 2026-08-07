package xyz.vprolabs.sparrow.state;

/**
 * One-shot "keep my attack charge across a hotbar swap" request, armed by
 * AttackCooldownMixin (slot change while 0 < ticks < period) and consumed by
 * AttackCooldownPreserveMixin when PlayerEntity.resetTicksSince() actually
 * zeroes the counter on an item change. Disarmed after attack starts, so a
 * genuine `resetTicksSinceLastAttack()` (the attack path) still works. Held
 * here (outside the mixin package) per the Fabric Loader rule that every
 * class under the mixin package gets mixin-transformed.
 */
public final class AttackCooldownPreserve {
    private static int armedTicks = -1;

    private AttackCooldownPreserve() {}

    public static void arm(int ticks) {
        armedTicks = ticks;
    }

    public static void disarm() {
        armedTicks = -1;
    }

    /** Returns the preserved ticks and consumes the request. */
    public static int takeArmed() {
        int t = armedTicks;
        armedTicks = -1;
        return t;
    }

    public static boolean isArmed() {
        return armedTicks > 0;
    }
}