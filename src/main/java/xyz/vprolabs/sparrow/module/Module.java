package xyz.vprolabs.sparrow.module;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;

/**
 * A single configurable feature of the Sparrow client.
 *
 * Three kinds:
 *  - toggle module:          enabled/disabled flag (e.g. fullbright)
 *  - value module:           a numeric setting with min/max/step (e.g. view-x), always "active"
 *  - string module:          a string setting, optionally restricted to fixed options (e.g. particles)
 *
 * Every module registers itself into ModuleManager's registry on construction.
 * Setters call ModuleHooks.onChanged() so config side-effects (glint refresh,
 * crosshair color -> heart) fire no matter who changes the value (GUI, console, mixin).
 */
public final class Module {
    private final String id;
    private final String category;
    private final String displayName;

    private volatile boolean enabled;
    private final boolean toggleable;

    private final boolean numeric;
    private final boolean integer;
    private volatile double value;
    private final double min, max, step;

    private final boolean stringly;
    private volatile String stringValue;
    private final List<String> options;
    // name -> pretty label for fixed-option string modules. NULL when every
    // option's Name equals its DisplayName (the common case). See withLabels().
    private final Map<String, String> optionDisplay;

    // Factory defaults — captured so the GUI Reset button can restore them.
    private final boolean defaultEnabledVal;
    private final double defaultNumberVal;
    private final String defaultStringVal;

    // Composite module: a GUI group of EXISTING registered modules shown as
    // ONE row ("View Model" = x/y/z/scale, "Fire Timer" = toggle + position).
    // Children stay registered standalone (console commands, config keys and
    // mixin references all keep working); the GUI hides children of a
    // composite and a click on the composite row opens a popup with controls
    // for each child. See Module.group().
    private final boolean composite;
    private Module parent;
    private Map<String, Module> children;

    // Color-editable module (2026-08-09 user spec: "RGB color selector, add
    // it to all places where you can select custom HEX/RGB color"). Marked
    // on string-hex modules (crosshair-color) and composite groups whose
    // children are RGB channels (glint). The GUI renders an RGB picker
    // (swatch + sliders + hex) for isColor() modules.
    private boolean color;

    // Conditional popup rows (2026-08-09): a child set to visibleWhen(sibling)
    // only appears in the composite popup while the SIBLING module is enabled.
    // "zoom-reset-value" is hidden until the "zoom-reset" toggle is on — a
    // setting that has no meaning while its trigger is disabled.
    private String visibleWhenSibling;

    // Hover description shown in the click GUI tooltip. NULL = no tooltip.
    private String description;

    // BUGGY LOCK (2026-08-10): the atlas-cache feature renders invisible
    // sprites on cache-hit boots, root cause not yet found. A locked module
    // can NEVER be enabled: setEnabled(true) is coerced back to false at the
    // lowest layer, so every path (GUI tile, popup row, console command,
    // config load, reset) is blocked in one place. The GUI paints a red
    // "BUGGY" badge instead of the ON/OFF pill and the console refuses.
    private volatile boolean locked;

    // Toggle module
    public Module(String id, String category, boolean defaultEnabled) {
        this(id, category, defaultEnabled, false, false, 0, 0, 0, 1, false, false, null, null, null);
    }

    // Numeric module (float or int; int when integer=true)
    public Module(String id, String category, double defaultValue, double min, double max, double step, boolean integer) {
        this(id, category, false, true, integer, defaultValue, min, max, step, false, false, null, null, null);
    }

    // String module (options = allowed values; null/empty = free-form text).
    // Each option IS its own display label (Name == DisplayName).
    public Module(String id, String category, String defaultValue, String... options) {
        this(id, category, false, false, false, 0, 0, 0, 1, true, false, defaultValue, options, null);
    }

    // String module with separate terminal Name and pretty DisplayName per
    // option. withLabels(id, cat, default, name1, label1, name2, label2, ...):
    //   - options() returns the NAMES (space-free, terminal-usable: `sparrow ui menu`)
    //   - displayOption(name) returns the label (GUI cycle row + `list` output)
    // The terminal MUST never see the labels — spaces break console args
    // (2026-08-01: "Terminal [LEGACY]" was untypeable, user was trapped).
    public static Module withLabels(String id, String category, String defaultValue, String... pairs) {
        List<String> names = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            names.add(pairs[i]);
            labels.put(pairs[i], pairs[i + 1]);
        }
        return new Module(id, category, false, false, false, 0, 0, 0, 1, true, false,
            defaultValue, names.toArray(new String[0]), labels);
    }

    // Composite module grouping EXISTING registered modules under one GUI row.
    // The children keep their own ids/console commands/config keys — nothing
    // downstream changes. A child may only belong to one composite.
    public static Module group(String id, String category, Module... children) {
        return group(id, category, false, children);
    }

    // Composite parent with a MASTER TOGGLE: the GUI tile shows ON/OFF and a
    // click flips the whole feature (e.g. `zoom` gates ZoomMixin). The parent
    // state persists via ModuleManager (composites now save their own key).
    public static Module group(String id, String category, boolean defaultEnabled, Module... children) {
        Module m = new Module(id, category, defaultEnabled, false, false, 0, 0, 0, 1, false, true,
            null, null, null);
        m.children = new LinkedHashMap<>();
        for (Module c : children) {
            c.parent = m;
            m.children.put(c.id(), c);
        }
        return m;
    }

    private Module(String id, String category, boolean defaultEnabled,
                   boolean numeric, boolean integer, double defaultValue,
                   double min, double max, double step,
                   boolean stringly, boolean composite, String stringValue,
                   String[] options, Map<String, String> optionDisplay) {
        this.id = id;
        this.category = category;
        this.displayName = toDisplayName(id);
        this.composite = composite;
        this.toggleable = !numeric && !stringly && !composite;
        this.enabled = defaultEnabled;
        this.numeric = numeric;
        this.integer = integer;
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.stringly = stringly;
        this.stringValue = stringValue;
        this.defaultEnabledVal = defaultEnabled;
        this.defaultNumberVal = defaultValue;
        this.defaultStringVal = stringValue;
        if (options != null && options.length > 0) {
            this.options = new ArrayList<>(List.of(options));
        } else {
            this.options = null;
        }
        this.optionDisplay = optionDisplay;
        ModuleManager.register(this);
    }

    public String id() { return id; }
    public String category() { return category; }
    public String displayName() { return displayName; }
    public boolean isToggleable() { return toggleable; }
    public boolean isNumeric() { return numeric; }
    public boolean isString() { return stringly; }
    public boolean isInteger() { return integer; }
    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public List<String> options() { return options; }
    public boolean isComposite() { return composite; }
    public boolean hasParent() { return parent != null; }
    public Map<String, Module> children() { return children; }
    public Module child(String id) { return children == null ? null : children.get(id); }

    /** Hover description for the click GUI tooltip; null = no tooltip. */
    public String description() { return description; }

    /** Fluent: attach a hover description, e.g.
     *  {@code new Module("fullbright", "Visual", false).withDescription("...")}. */
    public Module withDescription(String desc) {
        this.description = desc;
        return this;
    }

    /** Conditional popup row: only shown in the composite popup while the
     *  named SIBLING module (same composite) is enabled. */
    public Module withVisibleWhen(String siblingId) {
        this.visibleWhenSibling = siblingId;
        return this;
    }

    /** Fluent: mark this module as a color (hex string or RGB channel
     *  composite) so the GUI offers the RGB picker for it. */
    public Module withColor() {
        this.color = true;
        return this;
    }

    /** Fluent: lock this module as BUGGY. Forced off at construction and
     *  impossible to enable from any surface (see the locked field comment).
     *  Chain AFTER the default-enabled arg, e.g.
     *  {@code new Module("atlas-cache", "Experimental", false).withLocked()}. */
    public Module withLocked() {
        this.locked = true;
        this.enabled = false;
        return this;
    }

    /** True when the module is locked as broken (BUGGY badge, no toggling). */
    public boolean isLocked() { return locked; }

    /** True when this module is a color (see withColor()). */
    public boolean isColor() { return color; }

    /** Sibling module id this row is conditional on, or null for always-on. */
    public String visibleWhen() { return visibleWhenSibling; }

    /** True when the module currently holds its factory default — the GUI
     *  dims the Reset icon for rows that have nothing to reset. */
    public boolean isAtDefault() {
        // Locked modules are always "at default": they can never deviate
        // (always off), so the Reset icon must stay dimmed.
        if (locked) return true;
        if (composite) {
            for (Module c : children.values()) {
                if (!c.isAtDefault()) return false;
            }
            return true;
        }
        if (toggleable) return enabled == defaultEnabledVal;
        if (numeric) return value == defaultNumberVal;
        return stringValue == null ? defaultStringVal == null : stringValue.equals(defaultStringVal);
    }

    /** Restore the factory default (recurses into composite children) and
     *  fire ModuleHooks so side-effects (glint refresh, crosshair color)
     *  run exactly as they would for a manual change. */
    public void resetToDefault() {
        if (composite) {
            for (Module c : children.values()) c.resetToDefault();
            return;
        }
        if (toggleable) setEnabled(defaultEnabledVal);
        else if (numeric) setValue(defaultNumberVal);
        else if (stringly) setStringValue(defaultStringVal);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        // LOCK-1: locked modules cannot be enabled, from ANY caller. Coerce
        // to false so a stale modules.json, a GUI click or a console command
        // can never turn the broken feature on (2026-08-10).
        if (locked) v = false;
        if (this.enabled == v) return;
        this.enabled = v;
        ModuleHooks.onChanged(this);
    }

    public double value() { return value; }
    public float floatValue() { return (float) value; }
    public int intValue() { return (int) Math.round(value); }
    public void setValue(double v) {
        // NAN-1: NaN escapes Math.min/max (NaN comparisons are false), so a
        // single bad input would poison the value AND the saved file. Console
        // commands pre-validate; this is the last line of defense.
        if (!Double.isFinite(v)) return;
        double clamped = Math.max(min, Math.min(max, v));
        if (integer) {
            double steps = Math.round((clamped - min) / step);
            clamped = min + steps * step;
        }
        if (this.value == clamped) return;
        this.value = clamped;
        ModuleHooks.onChanged(this);
    }

    /** Snap a raw value onto this module's step grid (min + round((v-min)/step)*step).
     *  Used by the GUI sliders so dragging/scroll moves in whole steps (e.g.
     *  +0.05 for view-model X/Y/Z/scale). Console set commands deliberately
     *  stay continuous for precision typing. */
    public double stepValue(double v) {
        if (step <= 0) return v;
        return min + Math.round((v - min) / step) * step;
    }

    public String stringValue() { return stringValue; }
    public void setStringValue(String v) {
        if (v == null) return;
        // STRING-1: fixed-option modules must reject unknown values here, not
        // only in the console. Without this a stale value makes CycleRow's
        // indexOf return -1, so the GUI displays/cycles a value the module
        // does not hold. Console/GUI/load all validate before calling.
        if (options != null && !options.contains(v)) return;
        if (stringValue != null && stringValue.equals(v)) return;
        this.stringValue = v;
        ModuleHooks.onChanged(this);
    }

    /** Pretty label for a fixed-option value (falls back to the raw name). */
    public String displayOption(String name) {
        if (optionDisplay != null) {
            String label = optionDisplay.get(name);
            if (label != null) return label;
        }
        return name;
    }

    /** GUI/console display string for the current value. */
    public String displayValue() {
        if (toggleable) return enabled ? "\u00a7aON" : "\u00a7cOFF";
        if (numeric) {
            if (integer) return String.valueOf(intValue());
            return String.format(Locale.ROOT, "%.2f", value);
        }
        return displayOption(stringValue);
    }

    private static String toDisplayName(String id) {
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : id.toCharArray()) {
            if (c == '-') {
                sb.append(' ');
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
