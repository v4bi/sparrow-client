package xyz.vprolabs.sparrow.config;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry for ALL configurable features.
 * One line adds a feature with: name, category, default.
 * Auto-wires: console command, config save/load, display string.
 *
 * Usage:
 *   // Read
 *   if (ConfigRegister.fireTimer.get()) { ... }
 *   float z = ConfigRegister.viewModelZ.get();
 *   int r = ConfigRegister.glintR.get();
 *   String mode = ConfigRegister.particleMode.get();
 *
 *   // Write
 *   ConfigRegister.fireTimer.set(true);
 */
public final class ConfigRegister {

    // ── Entry interface ───────────────────────────────────────────────

    public interface Entry {
        String name();
        String category();
    }

    // ── Toggle (boolean) ──────────────────────────────────────────────

    public static final class Toggle implements Entry {
        private final String key, cat;
        private volatile boolean value;

        public Toggle(String name, String category, boolean defaultValue) {
            this.key = name; this.cat = category; this.value = defaultValue;
            all.put(name, this);
        }
        @Override public String name() { return key; }
        @Override public String category() { return cat; }
        public boolean get() { return value; }
        public void set(boolean v) { this.value = v; }
        public String display() { return value ? "§aON" : "§cOFF"; }
    }

    // ── SetEntry (float) ──────────────────────────────────────────────

    public static final class SetEntry implements Entry {
        private final String key, cat;
        private volatile float value;

        public SetEntry(String name, String category, float defaultValue) {
            this.key = name; this.cat = category; this.value = defaultValue;
            all.put(name, this);
        }
        @Override public String name() { return key; }
        @Override public String category() { return cat; }
        public float get() { return value; }
        public void set(float v) { this.value = v; }
        public String display() { return String.valueOf(value); }
    }

    // ── IntEntry (integer) ────────────────────────────────────────────

    public static final class IntEntry implements Entry {
        private final String key, cat;
        private volatile int value;

        public IntEntry(String name, String category, int defaultValue) {
            this.key = name; this.cat = category; this.value = defaultValue;
            all.put(name, this);
        }
        @Override public String name() { return key; }
        @Override public String category() { return cat; }
        public int get() { return value; }
        public void set(int v) { this.value = v; }
        public String display() { return String.valueOf(value); }
    }

    // ── StringEntry ───────────────────────────────────────────────────

    public static final class StringEntry implements Entry {
        private final String key, cat;
        private volatile String value;

        public StringEntry(String name, String category, String defaultValue) {
            this.key = name; this.cat = category; this.value = defaultValue;
            all.put(name, this);
        }
        @Override public String name() { return key; }
        @Override public String category() { return cat; }
        public String get() { return value; }
        public void set(String v) { this.value = v; }
        public String display() { return value; }
    }

    // ── Storage ───────────────────────────────────────────────────────

    private static final LinkedHashMap<String, Entry> all = new LinkedHashMap<>();

    public static Entry get(String name) { return all.get(name); }
    public static Collection<Entry> getAll() { return all.values(); }

    // ── Serialisation ─────────────────────────────────────────────────

    public static void putToMap(Map<String, Object> map) {
        if (map == null) return;
        for (Entry e : all.values()) {
            String key = e.name().replace('-', '_');
            if (e instanceof Toggle t) map.put(key, t.value);
            else if (e instanceof SetEntry s) map.put(key, s.value);
            else if (e instanceof IntEntry i) map.put(key, i.value);
            else if (e instanceof StringEntry s) map.put(key, s.value);
        }
    }

    public static void loadFromMap(Map<String, Object> map) {
        if (map == null) return;
        for (Entry e : all.values()) {
            String key = e.name().replace('-', '_');
            Object val = map.get(key);
            if (val == null) continue;
            if (e instanceof Toggle t) {
                if (val instanceof Boolean b) t.value = b;
                else SparrowLogger.warn("Config: key '" + key + "' should be boolean, got " + val.getClass().getSimpleName());
            } else if (e instanceof SetEntry s) {
                if (val instanceof Number n) s.value = n.floatValue();
                else SparrowLogger.warn("Config: key '" + key + "' should be number, got " + val.getClass().getSimpleName());
            } else if (e instanceof IntEntry i) {
                if (val instanceof Number n) i.value = n.intValue();
                else SparrowLogger.warn("Config: key '" + key + "' should be int, got " + val.getClass().getSimpleName());
            } else if (e instanceof StringEntry s) {
                if (val instanceof String str) s.value = str;
                else SparrowLogger.warn("Config: key '" + key + "' should be string, got " + val.getClass().getSimpleName());
            }
        }
    }

    // ── Console command registration ──────────────────────────────────

    @FunctionalInterface
    public interface FeatureConsumer {
        void acceptToggle(String name, String category, Runnable toggle, java.util.function.BooleanSupplier getter);
        default void acceptSet(String name, String category, java.util.function.DoubleSupplier getter, java.util.function.Consumer<Float> setter) {}
        default void acceptInt(String name, String category, java.util.function.IntSupplier getter, java.util.function.Consumer<Integer> setter) {}
        default void acceptString(String name, String category, java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {}
    }

    public static void forEachFeature(FeatureConsumer consumer) {
        for (Entry e : all.values()) {
            if (e instanceof Toggle t) {
                consumer.acceptToggle(t.name(), t.category(),
                    () -> t.set(!t.get()), t::get);
            } else if (e instanceof SetEntry s) {
                consumer.acceptSet(s.name(), s.category(), s::get, s::set);
            } else if (e instanceof IntEntry i) {
                consumer.acceptInt(i.name(), i.category(), i::get, i::set);
            } else if (e instanceof StringEntry s) {
                consumer.acceptString(s.name(), s.category(), s::get, s::set);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FEATURE DEFINITIONS — one line each
    // ══════════════════════════════════════════════════════════════════

    // ── Visual toggles ────────────────────────────────────────────────
    public static final Toggle smallTotem       = new Toggle("small-totem", "Visual", false);
    public static final Toggle oldPotions       = new Toggle("old-potions", "Visual", false);
    public static final Toggle customGlint      = new Toggle("custom-glint", "Visual", false);
    public static final Toggle fireTimer        = new Toggle("fire-timer", "Visual", false);
    public static final Toggle noMiscOverlays   = new Toggle("no-misc-overlays", "Visual", false);
    public static final Toggle removeShadows    = new Toggle("remove-shadows", "Visual", false);
    public static final Toggle storageTooltip   = new Toggle("storage-tooltip", "Visual", false);
    public static final Toggle coords           = new Toggle("coords", "Visual", false);
    public static final Toggle ping             = new Toggle("ping", "Visual", false);
    public static final Toggle desync           = new Toggle("desync", "Visual", false);
    public static final Toggle hitmarker        = new Toggle("hitmarker", "Visual", false);

    public static final Toggle shieldStatus     = new Toggle("shield-status", "Visual", false);

    // ── Visual float/int/string settings ──────────────────────────────
    public static final StringEntry crosshair     = new StringEntry("crosshair", "Visual", "off");
    public static final StringEntry crosshairColor = new StringEntry("crosshair-color", "Visual", "ffffff");
    public static final SetEntry viewModelX      = new SetEntry("view-x", "Visual", 0.0f);
    public static final SetEntry viewModelY      = new SetEntry("view-y", "Visual", 0.0f);
    public static final SetEntry viewModelZ      = new SetEntry("view-z", "Visual", 0.0f);
    public static final SetEntry viewModelSize   = new SetEntry("view-size", "Visual", 1.0f);
    public static final SetEntry utilityScale    = new SetEntry("utility-scale", "Visual", 0.65f);
    public static final IntEntry glintR          = new IntEntry("glint-r", "Visual", 0);
    public static final IntEntry glintG          = new IntEntry("glint-g", "Visual", 255);
    public static final IntEntry glintB          = new IntEntry("glint-b", "Visual", 0);
    public static final StringEntry fireTimerPos = new StringEntry("fire-timer-pos", "Visual", "BOTTOM_CENTER");
    public static final StringEntry particleMode = new StringEntry("particles", "Visual", "off");
    public static final Toggle playerHit           = new Toggle("player-hit", "Visual", false);
    public static final StringEntry playerHitType  = new StringEntry("player-hit-type", "Visual", "hit");
    public static final StringEntry playerHitColor = new StringEntry("player-hit-color", "Visual", "ff0000");

    // ── World ─────────────────────────────────────────────────────────
    public static final Toggle fullbright        = new Toggle("fullbright", "World", false);
    public static final Toggle noMiningFatigue   = new Toggle("no-mining-fatigue", "World", false);
    public static final Toggle alwaysDay         = new Toggle("always-day", "World", false);
    public static final Toggle disableEntityAI   = new Toggle("disable-entity-ai", "World", false);
    public static final IntEntry netherRenderCap = new IntEntry("nether-render-cap", "World", 6);
    public static final StringEntry blockLodMode = new StringEntry("block-lod-mode", "Optimization", "OFF");

    // ── Camera ────────────────────────────────────────────────────────
    public static final SetEntry zoomLevel        = new SetEntry("zoom", "Camera", 4.0f);
    public static final SetEntry zoomSmoothness   = new SetEntry("zoom-smoothness", "Camera", 8.0f);
    public static final SetEntry zoomMin          = new SetEntry("zoom-min", "Camera", 1.0f);
    public static final SetEntry zoomMax          = new SetEntry("zoom-max", "Camera", 15.0f);
    public static final Toggle disableMouseWheel  = new Toggle("disable-mouse-wheel", "Camera", false);

    // ── Optimization ──────────────────────────────────────────────────
    public static final SetEntry itemCullingDistance   = new SetEntry("item-culling-distance", "Optimization", 40.0f);
    public static final SetEntry entityCullingDistance = new SetEntry("entity-culling-distance", "Optimization", 128.0f);

    // ── Console ───────────────────────────────────────────────────────
    public static final IntEntry consoleFps = new IntEntry("console-fps", "Console", 60);

    private ConfigRegister() {}
}
