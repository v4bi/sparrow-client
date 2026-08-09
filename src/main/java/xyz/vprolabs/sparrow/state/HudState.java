package xyz.vprolabs.sparrow.state;

public final class HudState {
    private HudState() {}

    // ── Base HudState ────────────────────────────────────────────────
    public static int currentPing = 0;
    public static long lastDesyncTime = 0;
    public static final long DESYNC_HIDE_DURATION = 3000;

    // ── Hud string caches ─────────────────────────────────────────────
    // HUD render runs ~60x/sec; the coords/ping strings were rebuilt via
    // String.format / concat on EVERY frame (heap garbage). Bucket by the
    // integer-truncated value like FireTimer does: walking at 1 block/sec
    // changes the int bucket ~1x/sec, so the cached string is returned for
    // the ~59 frames in between. Rejected: caching by exact double would
    // rebuild every frame (position changes continuously) — pointless.
    // Rejected: Math.round() bucketing — changes 2x per block, halves the
    // cache hit rate for no visual gain; truncation matches the spec.
    private static int lastCoordX = Integer.MIN_VALUE;
    private static int lastCoordY = Integer.MIN_VALUE;
    private static int lastCoordZ = Integer.MIN_VALUE;
    private static String cachedCoords = null;
    private static int lastPing = -1;
    private static String cachedPing = null;

    // Sentinel-safe: no valid HUD value equals MIN_VALUE / -1 (ping render
    // path already guards currentPing > 0), so the first call always builds.
    public static String coordsString(double x, double y, double z) {
        int ix = (int) x, iy = (int) y, iz = (int) z;
        if (ix == lastCoordX && iy == lastCoordY && iz == lastCoordZ) return cachedCoords;
        lastCoordX = ix; lastCoordY = iy; lastCoordZ = iz;
        // Keep String.format ("%.0f", HALF_UP) on rebuild so the emitted
        // text is byte-identical to the old per-frame formatting; only the
        // update cadence changes (1/sec while walking, per FireTimer pattern).
        cachedCoords = String.format("XYZ: %.0f / %.0f / %.0f", x, y, z);
        return cachedCoords;
    }

    public static String pingString(int ping) {
        if (ping == lastPing) return cachedPing;
        lastPing = ping;
        cachedPing = "Ping: " + ping + "ms";
        return cachedPing;
    }

    // ── FireTimerState ───────────────────────────────────────────────
    // fireTicks is the CLIENT-SIDE countdown, driven once per tick by
    // FireTimerTickMixin. It cannot be read from the entity: vanilla 1.21.11
    // wipes the client's fireTicks to 0 in Entity.baseTick() every tick (see
    // FireTimerTickMixin for the full mechanism), so the mixin captures the
    // value at tick() HEAD before the wipe and counts down from there.
    // -1 = not burning (hidden), 0 = burned out but flag still true,
    // > 0 = active countdown. lastFireTicks* below are the RENDERER's string
    // cache and are unrelated to the countdown.
    public static int fireTicks = -1;
    public static int lastFireTicks = -1;
    public static String lastFireText = null;
    public static int lastFireWidth = 0;
    public static boolean logged = false;

    public static void reset() {
        fireTicks = -1;
        lastFireTicks = -1;
        lastFireText = null;
        lastFireWidth = 0;
        logged = false;
    }

    // ── CooldownResetState ───────────────────────────────────────────
    public static boolean cooldownWasReset = false;
    public static long resetTime = 0;
    public static final long RESET_DISPLAY_MS = 400;

    public static void markReset() {
        cooldownWasReset = true;
        resetTime = System.currentTimeMillis();
    }

    public static boolean isResetShowing() {
        return cooldownWasReset && (System.currentTimeMillis() - resetTime < RESET_DISPLAY_MS);
    }

    public static void tickReset() {
        if (cooldownWasReset && !isResetShowing()) {
            cooldownWasReset = false;
        }
    }

    // ── HitConfirmState ──────────────────────────────────────────────
    public static long lastHitTime = 0;
    public static boolean hitConfirmed = false;
    public static int lastAttackedEntityId = -1;
    public static final long HIT_DISPLAY_DURATION = 300;

    public static void registerAttack(int entityId) {
        lastAttackedEntityId = entityId;
    }

    public static void confirmHit(int entityId) {
        if (entityId == lastAttackedEntityId) {
            lastHitTime = System.currentTimeMillis();
            hitConfirmed = true;
            lastAttackedEntityId = -1;
        }
    }

    public static boolean isShowing() {
        return hitConfirmed && (System.currentTimeMillis() - lastHitTime < HIT_DISPLAY_DURATION);
    }

    // ── ShieldChargeState ────────────────────────────────────────────
    public static final long SHIELD_WINDUP_MS = 250;
    private static long shieldRaisedTime = 0;
    private static boolean isCharging = false;
    private static boolean isActive = false;

    public static void update(boolean isUsingItem) {
        if (isUsingItem) {
            if (shieldRaisedTime == 0) {
                shieldRaisedTime = System.currentTimeMillis();
                isCharging = true;
                isActive = false;
            } else {
                long elapsed = System.currentTimeMillis() - shieldRaisedTime;
                if (elapsed >= SHIELD_WINDUP_MS) {
                    isCharging = false;
                    if (!isActive) {
                        isActive = true;
                    }
                }
            }
        } else {
            shieldRaisedTime = 0;
            isCharging = false;
            isActive = false;
        }
    }

    public static boolean isChargingNow() {
        return isCharging;
    }

    public static boolean isActiveNow() {
        return isActive;
    }

}
