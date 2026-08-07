package xyz.vprolabs.sparrow.state;

import java.util.HashMap;
import java.util.Map;

public class HudPositions {
    private static final Map<String, int[]> offsets = new HashMap<>();

    private static final String[] KNOWN_KEYS = {
        "coords", "ping", "desync", "fire-timer", "ghost-block", "knockback", "shield",
        "hotbar", "status-bars"
    };

    public static int[] getOffset(String key) {
        return offsets.getOrDefault(key, new int[]{0, 0});
    }

    public static void setOffset(String key, int x, int y) {
        offsets.put(key, new int[]{x, y});
    }

    public static void resetAll() {
        offsets.clear();
    }

    public static void loadFromMap(Map<String, Object> map) {
        offsets.clear();
        if (map == null) return;
        for (String key : KNOWN_KEYS) {
            // Config keys are prefixed "hud_" — the unprefixed names collide
            // with module ids (coords/ping/desync/fire-timer are toggles!).
            // Pre-2026-08-01, saveNow wrote module values first and then
            // HudPositions OVERWROTE e.g. fire_timer=true with [0,0], so
            // those toggles silently reset to false every restart. Old
            // unprefixed keys are still read as a migration fallback.
            String cfgKey = "hud_" + key.replace('-', '_');
            Object val = map.get(cfgKey);
            if (val == null) val = map.get(key.replace('-', '_'));
            if (val instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) val;
                if (list.size() >= 2 && list.get(0) instanceof Number && list.get(1) instanceof Number) {
                    offsets.put(key, new int[]{((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue()});
                    continue;
                }
            }
            offsets.put(key, new int[]{0, 0});
        }
    }

    public static void putToMap(Map<String, Object> map) {
        for (Map.Entry<String, int[]> e : offsets.entrySet()) {
            // hud_ prefix keeps offsets off the module-id keys (see loadFromMap).
            String cfgKey = "hud_" + e.getKey().replace('-', '_');
            int[] off = e.getValue();
            map.put(cfgKey, java.util.Arrays.asList(off[0], off[1]));
        }
    }
}
