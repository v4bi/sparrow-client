package xyz.vprolabs.sparrow.module;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.state.HudPositions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persistence for the module system. Writes modules.json in the game dir
 * (same contract as the old config.json: user.dir at runtime).
 *
 * Deferred-init contract (inherited from ConfigReader, see MinecraftClientMixin):
 * load() runs on the first render frame, NOT at mod init. Until then,
 * requestSave() is a no-op so defaults can never overwrite a saved file.
 *
 * Migration: if modules.json is absent but the legacy config.json exists, its
 * values are applied (same dash→underscore key format) and written out as
 * modules.json, so no user settings are lost on the switch.
 */
public final class ModuleManager {
    private static final String FILE_NAME = "modules.json";
    private static final String LEGACY_FILE_NAME = "config.json";

    // Old display-string values -> their space-free terminal Names. These were
    // settable by the pre-2026-08-01 ui picker and would otherwise survive in
    // modules.json forever (untypeable in the console: spaces break args).
    // Keys match case-insensitively on load; the values are the current Names.
    private static final Map<String, String> LEGACY_OPTIONS = Map.of(
        "Sparrow Menu", "menu",
        "Terminal [LEGACY]", "terminal");

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Module> registry = new LinkedHashMap<>();
    private static volatile boolean loaded;
    private static volatile long lastSave;

    // Save throttle: values can change every frame (zoom scroll wheel), and
    // disk I/O on every change would destroy FPS. 500ms matches the throttle
    // the old MouseScrollMixin applied to zoom saves.
    private static final long SAVE_THROTTLE_MS = 500;

    public static void register(Module m) {
        registry.put(m.id(), m);
    }

    public static Module get(String id) {
        return registry.get(id);
    }

    /** All modules, insertion-ordered (definition order = console/GUI order). */
    public static Map<String, Module> all() {
        return registry;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void load() {
        if (registry.isEmpty()) {
            // Deferred-init contract: load() may be the very first touch of
            // the module system (MinecraftClientMixin first-render hook).
            // Force the Modules registry to populate before applying values,
            // or every module would silently stay at defaults.
            Modules.ensureRegistered();
        }

        File dir = new File(System.getProperty("user.dir"));
        File file = new File(dir, FILE_NAME);
        Map<String, Object> map;

        if (file.exists()) {
            map = readJson(file);
            if (map == null) {
                map = new HashMap<>();
            }
        } else {
            // First run of the module system: migrate from the legacy config.
            File legacy = new File(dir, LEGACY_FILE_NAME);
            map = legacy.exists() ? readJson(legacy) : new HashMap<>();
            if (map == null) map = new HashMap<>();
            SparrowLogger.info("Migrating legacy " + LEGACY_FILE_NAME + " values into " + FILE_NAME);
        }

        // Apply before marking loaded: ModuleHooks.requestSave() checks the
        // flag, so applying values can never trigger an early write-back.
        applyToModules(map);
        HudPositions.loadFromMap(map);
        loaded = true;
        saveNow();
    }

    private static Map<String, Object> readJson(File file) {
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = GSON.fromJson(reader, type);
            if (map == null) return new HashMap<>();
            return map;
        } catch (Exception e) {
            SparrowLogger.warn("Failed to parse " + file.getName() + " (" + e.getMessage() + ") — backing up and using defaults");
            try {
                File bak = new File(file.getParentFile(), file.getName() + ".bak." + System.currentTimeMillis());
                Files.move(file.toPath(), bak.toPath());
                SparrowLogger.info("Backed up malformed " + file.getName() + " to: " + bak.getAbsolutePath());
            } catch (Exception moveEx) {
                SparrowLogger.error("Failed to back up malformed " + file.getName() + ": " + moveEx.getMessage());
            }
            return new HashMap<>();
        }
    }

    private static void applyToModules(Map<String, Object> map) {
        if (map == null) return;
        for (Module m : registry.values()) {
            // LOCK-1 (2026-08-10): BUGGY-locked modules are forced OFF on
            // every startup and their saved value is never applied — a stale
            // modules.json from before the lock must not re-enable the broken
            // atlas-cache feature. setEnabled(false) is a no-op here (already
            // off), so the skip also saves the JSON key lookups.
            if (m.isLocked()) {
                m.setEnabled(false);
                continue;
            }
            String key = m.id().replace('-', '_');
            if (m.isComposite()) {
                // Composite parents are MASTER TOGGLES since 2026-08-09
                // (e.g. `zoom` gates ZoomMixin). Load the parent's own key
                // when it's a boolean (written by saveNow). Legacy numeric
                // values under the parent key (old standalone `zoom`) are NOT
                // booleans and flow into the children fallback below.
                Object parentVal = map.get(key);
                if (parentVal instanceof Boolean pb) m.setEnabled(pb);
                // Composite parents hold no value of their own; children save
                // under their own keys. Legacy migration: pre-merge configs
                // saved the PARENT id with a plain value (e.g. "fire_timer":
                // true). If a child's own key is absent, the parent key value
                // applies to ONE type-compatible child — the old code handed
                // it to EVERY child missing its own key, so a legacy
                // "crosshair": "off" overwrote BOTH crosshair-mode AND
                // crosshair-color (B3).
                boolean fallbackUsed = false;
                for (Module c : m.children().values()) {
                    String ck = c.id().replace('-', '_');
                    Object cv = map.containsKey(ck) ? map.get(ck) : null;
                    if (cv == null && !fallbackUsed) cv = map.get(key);
                    if (cv == null) continue;
                    boolean applied = false;
                    if (c.isToggleable() && cv instanceof Boolean b) { c.setEnabled(b); applied = true; }
                    else if (c.isNumeric() && cv instanceof Number n) { c.setValue(n.doubleValue()); applied = true; }
                    else if (c.isString() && cv instanceof String s) {
                        String v = normalizeString(c, s);
                        if (v != null) { c.setStringValue(v); applied = true; }
                    }
                    if (applied && !map.containsKey(ck)) fallbackUsed = true;
                }
                continue;
            }
            Object val = map.get(key);
            if (val == null) continue;
            if (m.isToggleable()) {
                if (val instanceof Boolean b) m.setEnabled(b);
                else warnType(key, "boolean", val);
            } else if (m.isNumeric()) {
                if (val instanceof Number n) m.setValue(n.doubleValue());
                else warnType(key, "number", val);
            } else if (m.isString()) {
                if (val instanceof String s) {
                    String v = normalizeString(m, s);
                    if (v != null) m.setStringValue(v);
                    else warnType(key, "one of " + m.options(), s);
                } else warnType(key, "string", val);
            }
        }
    }

    /** Maps a loaded string value onto a valid value for the module.
     *  1. LEGACY_OPTIONS display strings -> their space-free Names.
     *  2. Case-insensitive match against the module's fixed options.
     *  3. Fixed-option modules with no match -> the module default.
     *  Free-form string modules accept any value (returns null only for
     *  null input). Validation here mirrors the console command validation
     *  so the saved file can never hold an option the terminal can't set. */
    private static String normalizeString(Module m, String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        for (Map.Entry<String, String> e : LEGACY_OPTIONS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(trimmed)) return e.getValue();
        }
        List<String> options = m.options();
        if (options == null || options.isEmpty()) return trimmed;
        for (String opt : options) {
            if (opt.equalsIgnoreCase(trimmed)) return opt;
        }
        SparrowLogger.warn("Config: key '" + m.id() + "' has unknown value '" + raw
            + "' — using default '" + m.stringValue() + "'");
        return m.stringValue();
    }

    private static void warnType(String key, String expected, Object got) {
        SparrowLogger.warn("Config: key '" + key + "' should be " + expected + ", got " + got.getClass().getSimpleName());
    }

    /** Throttled save; safe to call from hot paths (scroll wheel, GUI drag). */
    public static void requestSave() {
        if (!loaded) return;
        long now = System.currentTimeMillis();
        if (now - lastSave < SAVE_THROTTLE_MS) return;
        lastSave = now;
        saveNow();
    }

    public static void saveNow() {
        File file = new File(System.getProperty("user.dir"), FILE_NAME);
        Map<String, Object> map = new HashMap<>();
        map.put("version", "1");
        for (Module m : registry.values()) {
            String key = m.id().replace('-', '_');
            if (m.isComposite()) {
                // Composite MASTER TOGGLE state (2026-08-09): `zoom` gates
                // ZoomMixin; without this the tile would flip to OFF on every
                // restart even though the user disabled it. Children still
                // save under their own keys below.
                map.put(key, m.isEnabled());
                continue;
            }
            if (m.isToggleable()) map.put(key, m.isEnabled());
            else if (m.isNumeric()) {
                // NAN-1: never persist NaN/Infinity — Gson throws on them and
                // the old write order had already truncated the file, so a
                // reload found an empty config and silently reset everything.
                // Module.setValue guards writes; this is defense in depth.
                if (!Double.isFinite(m.value())) continue;
                map.put(key, m.isInteger() ? m.intValue() : m.floatValue());
            }
            else if (m.isString()) map.put(key, m.stringValue());
        }
        HudPositions.putToMap(map);
        // Serialize BEFORE opening the file, write to a temp file, then
        // rename: the old code truncated modules.json first and serialized
        // into it — an exception mid-write left a half-written (or empty)
        // file that reloaded as defaults, silently resetting all settings.
        try {
            String json = PRETTY_GSON.toJson(map);
            File tmp = new File(file.getPath() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            SparrowLogger.info("Modules saved to: " + file.getAbsolutePath());
        } catch (Exception e) {
            SparrowLogger.error("Failed to save modules: " + e.getMessage());
        }
    }
}
