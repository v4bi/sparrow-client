package xyz.vprolabs.sparrow.state;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PlayerHitState {
    private PlayerHitState() {}

    private static final HashMap<Integer, Long> recentlyHit = new HashMap<>();
    private static final long HIT_DURATION_MS = 500;
    public static volatile int ableToHitEntityId = -1;
    public static volatile double ableToHitDistance = 0.0;

    public static void registerHit(int entityId) {
        recentlyHit.put(entityId, System.currentTimeMillis());
    }

    public static boolean isRecentlyHit(int entityId) {
        Long time = recentlyHit.get(entityId);
        if (time == null) return false;
        if (System.currentTimeMillis() - time > HIT_DURATION_MS) {
            recentlyHit.remove(entityId);
            return false;
        }
        return true;
    }

    public static void setAbleToHit(int entityId, double distance) {
        ableToHitEntityId = entityId;
        ableToHitDistance = distance;
    }

    public static void clearAbleToHit() {
        ableToHitEntityId = -1;
        ableToHitDistance = 0.0;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = recentlyHit.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > HIT_DURATION_MS) {
                it.remove();
            }
        }
    }
}
