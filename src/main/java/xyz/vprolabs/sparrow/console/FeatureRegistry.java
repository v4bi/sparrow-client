package xyz.vprolabs.sparrow.console;

import xyz.vprolabs.sparrow.module.Module;
import xyz.vprolabs.sparrow.module.ModuleManager;
import xyz.vprolabs.sparrow.module.Modules;
import xyz.vprolabs.sparrow.state.HudMoveState;
import xyz.vprolabs.sparrow.state.HudPositions;
import xyz.vprolabs.sparrow.state.ToggleSneakState;
import xyz.vprolabs.sparrow.tweaks.SparrowGlintLayers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FeatureRegistry {

	public record FeatureItem(String name, String category,
	                          java.util.function.Supplier<String> display) {}

	private static final List<FeatureItem> features = new ArrayList<>();

	public static List<FeatureItem> getFeatures() { return features; }

	private static void addFeature(String name, String category,
	                               java.util.function.Supplier<String> display) {
		features.add(new FeatureItem(name, category, display));
	}

	private static void save() {
		// Modules write through ModuleHooks (throttled); console commands want
		// the value on disk immediately, so force an unthrottled save.
		if (ModuleManager.isLoaded()) ModuleManager.saveNow();
	}

	private FeatureRegistry() {}

	// ── Standalone toggle command factory ──────────────────────────────

	private static void registerToggleCmd(String name,
	                                      java.util.function.BooleanSupplier getter,
	                                      java.util.function.Consumer<Boolean> setter) {
		SparrowConsoleCommand.register(name, new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length > 1) {
					String val = args[1].toLowerCase(Locale.ROOT);
					if (val.equals("on") || val.equals("true") || val.equals("1")) {
						setter.accept(true);
						save();
						return "\u00a77" + name + ": \u00a7aON";
					}
					if (val.equals("off") || val.equals("false") || val.equals("0")) {
						setter.accept(false);
						save();
						return "\u00a77" + name + ": \u00a7cOFF";
					}
					return "\u00a7cUse on/off for '" + name + "'";
				}
				boolean cur = getter.getAsBoolean();
				return "\u00a77" + name + ": " + (cur ? "\u00a7aON" : "\u00a7cOFF")
					+ " \u00a77(Use on/off to toggle)\u00a77";
			}
			@Override public String getDescription() { return "Toggle " + name; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("on", "off");
				return Collections.emptyList();
			}
		});
	}

	// ── Standalone float set command factory ───────────────────────────

	private static void registerFloatCmd(String name, java.util.function.Supplier<Float> getter,
	                                     java.util.function.Consumer<Float> setter,
	                                     float min, float max) {
		SparrowConsoleCommand.register(name, new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) return "\u00a77" + name + ": \u00a7f" + getter.get();
				try {
					float v = Float.parseFloat(args[1]);
					// NAN-1: NaN/Infinity pass every range check (NaN
					// comparisons are false) and would poison the module
					// value AND the saved file. Reject non-finite numbers.
					if (!Float.isFinite(v) || v < min || v > max)
						return "\u00a7cValue must be " + min + "-" + max;
					setter.accept(v);
					save();
					return "\u00a77" + name + ": \u00a7f" + getter.get();
				} catch (NumberFormatException e) {
					return "\u00a7cInvalid number";
				}
			}
			@Override public String getDescription() { return "Set " + name; }
		});
	}

	// ── Standalone int set command factory ─────────────────────────────

	private static void registerIntCmd(String name, java.util.function.IntSupplier getter,
	                                   java.util.function.Consumer<Integer> setter,
	                                   int min, int max) {
		SparrowConsoleCommand.register(name, new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) return "\u00a77" + name + ": \u00a7f" + getter.getAsInt();
				try {
					int v = Integer.parseInt(args[1]);
					if (v < min || v > max)
						return "\u00a7cValue must be " + min + "-" + max;
					setter.accept(v);
					save();
					return "\u00a77" + name + ": \u00a7f" + getter.getAsInt();
				} catch (NumberFormatException e) {
					return "\u00a7cInvalid integer";
				}
			}
			@Override public String getDescription() { return "Set " + name; }
		});
	}

	// ══════════════════════════════════════════════════════════════════
	//  registerAllCommands
	// ══════════════════════════════════════════════════════════════════

	public static void registerAllCommands() {
		java.util.Set<String> groupedNames = java.util.Set.of(
			"glint-r", "glint-g", "glint-b", "custom-glint",
			"view-x", "view-y", "view-z", "view-size", "utility-scale",
			"zoom", "zoom-level", "zoom-smoothness", "zoom-min", "zoom-max",
			"zoom-reset-value",
			"fire-timer", "fire-timer-pos",
			"particles", "block-lod-mode", "movehud",
            "crosshair", "crosshair-color",
            "player-hit", "player-hit-type", "player-hit-color"
        );

		// Every module that is not handled by a grouped command gets its own
		// standalone command. Numeric bounds come from the module definition,
		// so the console and the GUI can never disagree on valid values.
		for (Module m : ModuleManager.all().values()) {
			if (groupedNames.contains(m.id())) continue;
			if (m.isComposite()) continue; // composite parents get a grouped command below
			if (m.isToggleable()) {
				registerToggleCmd(m.id(), m::isEnabled, m::setEnabled);
			} else if (m.isNumeric()) {
				if (m.isInteger()) {
					if (m.id().equals("console-fps")) {
						registerConsoleFpsCmd();
					} else {
						registerIntCmd(m.id(), m::intValue, v -> m.setValue(v),
							(int) m.min(), (int) m.max());
					}
				} else {
					registerFloatCmd(m.id(), m::floatValue, v -> m.setValue(v),
						(float) m.min(), (float) m.max());
				}
			} else if (m.isString()) {
				SparrowConsoleCommand.register(m.id(), new SparrowConsoleCommand.Command() {
					@Override public String execute(String[] args) {
						if (args.length < 2)
							return "\u00a77" + m.id() + ": \u00a7f" + m.displayOption(m.stringValue());
						String v = args[1];
						// Fixed-option strings (e.g. ui): only accept an exact
						// NAME (space-free), case-insensitively — prevents a
						// typo from writing an invalid value the GUI can't
						// display. Names are what the terminal accepts; the
						// pretty labels (DisplayName) never appear here.
						if (m.options() != null) {
							String match = null;
							for (String opt : m.options()) {
								if (opt.equalsIgnoreCase(v)) { match = opt; break; }
							}
							if (match == null)
								return "\u00a7cInvalid option. Options: \u00a7f"
									+ String.join(", ", m.options());
							v = match;
						}
						m.setStringValue(v);
						save();
						return "\u00a77" + m.id() + ": \u00a7f" + m.displayOption(m.stringValue());
					}
					@Override public String getDescription() { return "Set " + m.id(); }
					@Override public List<String> tabComplete(String[] args) {
						// Offer the option NAMES (space-free, terminal-safe).
						// SparrowConsoleCommand.getSubCompletions() filters by
						// the typed partial, so returning the full list is fine.
						if (args.length == 2 && m.options() != null) return m.options();
						return Collections.emptyList();
					}
				});
			}
		}

		registerGlintCmd();
		registerViewCmd();
		registerZoomCmd();
		registerFireTimerCmd();
		registerParticlesCmd();
		registerBlockLodCmd();
		registerSneakCmd();
		registerCrosshairCmd();
		registerPlayerHitCmd();
		registerMoveHudCmd();

		// Composite modules ("View Model") get a grouped command:
		//   sparrow view-model            -> x=0.0 y=0.0 z=0.0 scale=1.0
		//   sparrow view-model x 2        -> set child value
		// Children named after their own ids; tab-complete offers them.
		java.util.Set<String> grouped = java.util.Set.of(
			"glint", "glint-r", "glint-g", "glint-b", "custom-glint",
			"view-x", "view-y", "view-z", "view-size", "utility-scale",
			"zoom", "zoom-level", "zoom-smoothness", "zoom-min", "zoom-max",
			"zoom-reset-value",
			"fire-timer", "fire-timer-pos",
			"particles", "block-lod-mode", "movehud",
			"crosshair", "crosshair-color",
			"player-hit", "player-hit-type", "player-hit-color"
		);
		for (Module m : ModuleManager.all().values()) {
			if (!m.isComposite() || grouped.contains(m.id())) continue;
			registerCompositeCmd(m);
		}
	}

	// ── Composite module grouped command ───────────────────────────────

	private static void registerCompositeCmd(Module m) {
		SparrowConsoleCommand.register(m.id(), new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					StringBuilder sb = new StringBuilder("\u00a77" + m.id() + ": \u00a7f");
					for (Module c : m.children().values()) {
						sb.append(c.id()).append("=").append(c.displayValue()).append("  ");
					}
					return sb.toString().trim();
				}
				Module c = m.child(args[1]);
				if (c == null) {
					StringBuilder opts = new StringBuilder();
					for (Module cc : m.children().values()) {
						if (opts.length() > 0) opts.append(", ");
						opts.append(cc.id());
					}
					return "\u00a7cUnknown value. Values: \u00a7f" + opts;
				}
				if (args.length < 3)
					return "\u00a77" + m.id() + "." + c.id() + ": \u00a7f" + c.displayValue();
				String v = args[2];
				if (c.isToggleable()) {
					if (!v.equalsIgnoreCase("on") && !v.equalsIgnoreCase("off"))
						return "\u00a7cUse on/off";
					c.setEnabled(v.equalsIgnoreCase("on"));
					save();
					return "\u00a77" + m.id() + "." + c.id() + ": \u00a7f" + c.displayValue();
				}
				if (c.isNumeric()) {
					try {
						double d = Double.parseDouble(v);
						// NAN-1: NaN passes c.min()/c.max() checks and escapes
						// Module.setValue's clamp — reject non-finite here.
						if (!Double.isFinite(d) || d < c.min() || d > c.max())
							return "\u00a7cValue must be " + c.min() + "-" + c.max();
						c.setValue(d);
						save();
						return "\u00a77" + m.id() + "." + c.id() + ": \u00a7f" + c.displayValue();
					} catch (NumberFormatException e) {
						return "\u00a7cInvalid number";
					}
				}
				if (c.options() != null) {
					String match = null;
					for (String opt : c.options()) {
						if (opt.equalsIgnoreCase(v)) { match = opt; break; }
					}
					if (match == null) {
						return "\u00a7cInvalid option. Options: \u00a7f"
							+ String.join(", ", c.options());
					}
					v = match;
				}
				c.setStringValue(v);
				save();
				return "\u00a77" + m.id() + "." + c.id() + ": \u00a7f" + c.displayOption(v);
			}
			@Override public String getDescription() { return "Set " + m.id() + " values"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) {
					List<String> out = new ArrayList<>();
					for (Module c : m.children().values()) out.add(c.id());
					return out;
				}
				return Collections.emptyList();
			}
		});
	}

	// ── console-fps (needs SparrowConsoleState.consoleFps sync) ────────

	private static void registerConsoleFpsCmd() {
		SparrowConsoleCommand.register("console-fps", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2)
					return "\u00a77console-fps: \u00a7f" + Modules.consoleFps.intValue();
				try {
					int v = Integer.parseInt(args[1]);
					if (v < (int) Modules.consoleFps.min() || v > (int) Modules.consoleFps.max())
						return "\u00a7cValue must be " + (int) Modules.consoleFps.min() + "-" + (int) Modules.consoleFps.max();
					Modules.consoleFps.setValue(v);
					SparrowConsoleState.consoleFps = v;
					save();
					return "\u00a77console-fps: \u00a7f" + Modules.consoleFps.intValue();
				} catch (NumberFormatException e) {
					return "\u00a7cInvalid integer";
				}
			}
			@Override public String getDescription() { return "Set console FPS"; }
		});
	}

	// ── glint ──────────────────────────────────────────────────────────

	private static void registerGlintCmd() {
		SparrowConsoleCommand.register("glint", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					return "\u00a77glint \u00a7fr=" + Modules.glintR.intValue()
						+ " \u00a7fg=" + Modules.glintG.intValue()
						+ " \u00a7fb=" + Modules.glintB.intValue()
						+ " \u00a77custom=" + (Modules.customGlint.isEnabled() ? "\u00a7aON" : "\u00a7cOFF");
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					Modules.customGlint.setEnabled(true);
					SparrowGlintLayers.refresh();
					save();
					return "\u00a77custom-glint: \u00a7aON";
				case "off":
					Modules.customGlint.setEnabled(false);
					SparrowGlintLayers.refresh();
					save();
					return "\u00a77custom-glint: \u00a7cOFF";
				case "r": {
					if (args.length < 3) return "\u00a7cUsage: glint r <0-255>";
					try {
						int v = Integer.parseInt(args[2]);
						if (v < 0 || v > 255) return "\u00a7cValue must be 0-255";
						Modules.glintR.setValue(v);
						SparrowGlintLayers.refresh();
						save();
						return "\u00a77glint-r: \u00a7f" + v;
					} catch (NumberFormatException e) {
						return "\u00a7cInvalid integer";
					}
				}
				case "g": {
					if (args.length < 3) return "\u00a7cUsage: glint g <0-255>";
					try {
						int v = Integer.parseInt(args[2]);
						if (v < 0 || v > 255) return "\u00a7cValue must be 0-255";
						Modules.glintG.setValue(v);
						SparrowGlintLayers.refresh();
						save();
						return "\u00a77glint-g: \u00a7f" + v;
					} catch (NumberFormatException e) {
						return "\u00a7cInvalid integer";
					}
				}
				case "b": {
					if (args.length < 3) return "\u00a7cUsage: glint b <0-255>";
					try {
						int v = Integer.parseInt(args[2]);
						if (v < 0 || v > 255) return "\u00a7cValue must be 0-255";
						Modules.glintB.setValue(v);
						SparrowGlintLayers.refresh();
						save();
						return "\u00a77glint-b: \u00a7f" + v;
					} catch (NumberFormatException e) {
						return "\u00a7cInvalid integer";
					}
				}
				default:
					return "\u00a7cUsage: glint [on|off|r|g|b] [value]";
				}
			}
			@Override public String getDescription() { return "Custom enchant glint color"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("on", "off", "r", "g", "b");
				return Collections.emptyList();
			}
		});
	}

	// ── view ───────────────────────────────────────────────────────────

	private static void registerViewCmd() {
		SparrowConsoleCommand.register("view", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					return "\u00a77view \u00a7fx=" + Modules.viewModelX.floatValue()
						+ " \u00a7fy=" + Modules.viewModelY.floatValue()
						+ " \u00a7fz=" + Modules.viewModelZ.floatValue()
						+ " \u00a7fsize=" + Modules.viewModelSize.floatValue()
						+ " \u00a7futility-scale=" + Modules.utilityScale.floatValue();
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				try {
					switch (sub) {
					case "x":
						if (args.length < 3) return "\u00a7cUsage: view x <float>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v)) return "\u00a7cInvalid number";
							Modules.viewModelX.setValue(v);
							save();
							return "\u00a77view-x: \u00a7f" + Modules.viewModelX.floatValue();
						}
					case "y":
						if (args.length < 3) return "\u00a7cUsage: view y <float>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v)) return "\u00a7cInvalid number";
							Modules.viewModelY.setValue(v);
							save();
							return "\u00a77view-y: \u00a7f" + Modules.viewModelY.floatValue();
						}
					case "z":
						if (args.length < 3) return "\u00a7cUsage: view z <float>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v)) return "\u00a7cInvalid number";
							Modules.viewModelZ.setValue(v);
							save();
							return "\u00a77view-z: \u00a7f" + Modules.viewModelZ.floatValue();
						}
					case "size":
						if (args.length < 3) return "\u00a7cUsage: view size <float (>= 0.01)>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v) || v < 0.01f) return "\u00a7cValue must be >= 0.01";
							Modules.viewModelSize.setValue(v);
							save();
							return "\u00a77view-size: \u00a7f" + Modules.viewModelSize.floatValue();
						}
					case "utility-scale":
						if (args.length < 3) return "\u00a7cUsage: view utility-scale <0.1-2.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v) || v < 0.1f || v > 2.0f) return "\u00a7cValue must be 0.1-2.0";
							Modules.utilityScale.setValue(v);
							save();
							return "\u00a77utility-scale: \u00a7f" + Modules.utilityScale.floatValue();
						}
					default:
						return "\u00a7cUsage: view [x|y|z|size|utility-scale] <value>";
					}
				} catch (NumberFormatException e) {
					return "\u00a7cInvalid number";
				}
			}
			@Override public String getDescription() { return "View model position/size"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("x", "y", "z", "size", "utility-scale");
				return Collections.emptyList();
			}
		});
	}

	// ── zoom ───────────────────────────────────────────────────────────

	private static void registerZoomCmd() {
		SparrowConsoleCommand.register("zoom", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					return "\u00a77zoom \u00a7f" + Modules.zoomLevel.floatValue() + "x"
						+ " \u00a77smoothness=" + Modules.zoomSmoothness.floatValue()
						+ " \u00a77min=" + Modules.zoomMin.floatValue()
						+ " \u00a77max=" + Modules.zoomMax.floatValue()
						+ " \u00a77reset=" + Modules.zoomResetValue.floatValue() + "x";
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				try {
					switch (sub) {
					case "reset-level":
						// Reset target of the zoom-reset toggle (2026-08-09):
						// independent of the base zoom level, defaults 2.0.
						if (args.length < 3) return "\u00a7cUsage: zoom reset-level <0.6-100.0>";
						{
							float v = Float.parseFloat(args[2]);
							float rmin = (float) Modules.zoomResetValue.min();
							if (!Float.isFinite(v) || v < rmin)
								return "\u00a7cZoom reset level must be >= " + rmin;
							Modules.zoomResetValue.setValue(v);
							save();
							return "\u00a77zoom-reset-level: \u00a7f" + Modules.zoomResetValue.floatValue() + "x";
						}
					case "smoothness":
						if (args.length < 3) return "\u00a7cUsage: zoom smoothness <float (>= 1.0)>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v) || v < 1.0f) return "\u00a7cValue must be >= 1.0";
							Modules.zoomSmoothness.setValue(v);
							save();
							return "\u00a77zoom-smoothness: \u00a7f" + Modules.zoomSmoothness.floatValue() + "x";
						}
					case "min":
						if (args.length < 3) return "\u00a7cUsage: zoom min <0.5-10.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v) || v < 0.5f || v > 10.0f) return "\u00a7cValue must be 0.5-10.0";
							Modules.zoomMin.setValue(v);
							save();
							return "\u00a77zoom-min: \u00a7f" + Modules.zoomMin.floatValue();
						}
					case "max":
						if (args.length < 3) return "\u00a7cUsage: zoom max <5.0-50.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (!Float.isFinite(v) || v < 5.0f || v > 50.0f) return "\u00a7cValue must be 5.0-50.0";
							Modules.zoomMax.setValue(v);
							save();
							return "\u00a77zoom-max: \u00a7f" + Modules.zoomMax.floatValue();
						}
					default:
						{
							float v = Float.parseFloat(sub);
							// ZOOM-1: the console hardcoded 1.0 as the zoom
							// floor while the module allows down to 0.6
							// (inverse zoom); the GUI slider could go below
							// 1.0 but the console rejected it. Ask the module
							// for the bound so they can never disagree.
							float minZoom = (float) Modules.zoomLevel.min();
							if (!Float.isFinite(v) || v < minZoom)
								return "\u00a7cZoom level must be >= " + minZoom;
							Modules.zoomLevel.setValue(v);
							save();
							return "\u00a77zoom: \u00a7f" + Modules.zoomLevel.floatValue() + "x";
						}
					}
				} catch (NumberFormatException e) {
					return "\u00a7cUsage: zoom [smoothness|min|max] <value>  or  zoom <level>";
				}
			}
			@Override public String getDescription() { return "Zoom settings"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("smoothness", "min", "max", "reset-level");
				return Collections.emptyList();
			}
		});
	}

	// ── fire-timer ─────────────────────────────────────────────────────

	private static void registerFireTimerCmd() {
		SparrowConsoleCommand.register("fire-timer", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				// fire-timer is now a composite: the toggle is the
				// "fire-timer-enabled" child (see Modules.java).
				Module toggle = Modules.fireTimer.child("fire-timer-enabled");
				if (args.length < 2) {
					return "\u00a77fire-timer: " + (toggle.isEnabled() ? "\u00a7aON" : "\u00a7cOFF")
						+ " \u00a77pos=" + Modules.fireTimerPos.stringValue();
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					toggle.setEnabled(true);
					save();
					return "\u00a77fire-timer: \u00a7aON";
				case "off":
					toggle.setEnabled(false);
					save();
					return "\u00a77fire-timer: \u00a7cOFF";
				case "pos":
					if (args.length < 3) return "\u00a7cUsage: fire-timer pos [TOP_LEFT|TOP_RIGHT|BOTTOM_CENTER]";
					{
						String v = args[2].toUpperCase(Locale.ROOT);
						if (v.equals("TOP_LEFT") || v.equals("TOP_RIGHT") || v.equals("BOTTOM_CENTER")) {
							Modules.fireTimerPos.setStringValue(v);
							save();
							return "\u00a77fire-timer-pos: \u00a7f" + v;
						}
						return "\u00a7cInvalid position. Use: TOP_LEFT, TOP_RIGHT, BOTTOM_CENTER";
					}
				default:
					return "\u00a7cUsage: fire-timer [on|off|pos]";
				}
			}
			@Override public String getDescription() { return "Fire timer overlay"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("on", "off", "pos");
				if (args.length == 3 && args[1].equalsIgnoreCase("pos"))
					return Arrays.asList("TOP_LEFT", "TOP_RIGHT", "BOTTOM_CENTER");
				return Collections.emptyList();
			}
		});
	}

	// ── particles ──────────────────────────────────────────────────────

	private static void registerParticlesCmd() {
		SparrowConsoleCommand.register("particles", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2)
					return "\u00a77particles: \u00a7f" + Modules.particleMode.stringValue();
				String v = args[1].toLowerCase(Locale.ROOT);
				if (v.equals("off") || v.equals("minimal") || v.equals("on")) {
					Modules.particleMode.setStringValue(v);
					save();
					return "\u00a77particles: \u00a7f" + v;
				}
				return "\u00a7cUsage: particles [off|minimal|on]";
			}
			@Override public String getDescription() { return "Particle mode"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("off", "minimal", "on");
				return Collections.emptyList();
			}
		});
	}

	// ── block-lod ──────────────────────────────────────────────────────

	private static void registerBlockLodCmd() {
		SparrowConsoleCommand.Command blockLodCmd = new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2)
					return "\u00a77block-lod: \u00a7f" + Modules.blockLodMode.stringValue();
				String v = args[1].toUpperCase(Locale.ROOT);
			if (v.equals("OFF") || v.equals("LOW") || v.equals("PVP") || v.equals("AGGRESSIVE")) {
				Modules.blockLodMode.setStringValue(v);
				save();
				return "\u00a77block-lod: \u00a7f" + v;
			}
			return "\u00a7cUsage: block-lod [off|low|pvp|aggressive]";
			}
			@Override public String getDescription() { return "Block LOD mode"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("off", "low", "pvp", "aggressive");
				return Collections.emptyList();
			}
		};
		SparrowConsoleCommand.register("block-lod", blockLodCmd);
		// B1 alias: the module is block-lod-mode (Modules.java) but the
		// console command only existed as block-lod, so `sparrow
		// block-lod-mode off` hit "Unknown command". Both work now.
		SparrowConsoleCommand.register("block-lod-mode", blockLodCmd);
	}

	// ── sneak ──────────────────────────────────────────────────────────

	private static void registerSneakCmd() {
		SparrowConsoleCommand.register("sneak", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length > 1) {
					String val = args[1].toLowerCase(Locale.ROOT);
					if (val.equals("on") || val.equals("true") || val.equals("1")) {
						ToggleSneakState.enabled = true;
						save();
						return "\u00a77sneak: \u00a7aON";
					}
					if (val.equals("off") || val.equals("false") || val.equals("0")) {
						ToggleSneakState.enabled = false;
						save();
						return "\u00a77sneak: \u00a7cOFF";
					}
					return "\u00a7cUse on/off for 'sneak'";
				}
				return "\u00a77sneak: " + (ToggleSneakState.enabled ? "\u00a7aON" : "\u00a7cOFF")
					+ " \u00a77(Use on/off to toggle)\u00a77";
			}
			@Override public String getDescription() { return "Toggle-sneak"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("on", "off");
				return Collections.emptyList();
			}
		});
	}

	// ── crosshair ────────────────────────────────────────────────────

	private static void registerCrosshairCmd() {
		SparrowConsoleCommand.register("crosshair", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					String type = Modules.crosshairMode.stringValue();
					String color = Modules.crosshairColor.stringValue();
					return "\u00a77crosshair: " + (type.equals("off") ? "\u00a7cOFF" : "\u00a7aON")
						+ "\u00a77 type=" + type + " \u00a77color=#" + color
						+ "\u00a77 (Usage: crosshair [on|off|type|color] [value])\u00a77";
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					if (Modules.crosshairMode.stringValue().equals("off"))
						Modules.crosshairMode.setStringValue("plus");
					save();
					return "\u00a77crosshair: \u00a7aON \u00a77type=" + Modules.crosshairMode.stringValue();
				case "off":
					Modules.crosshairMode.setStringValue("off");
					save();
					return "\u00a77crosshair: \u00a7cOFF";
				case "type":
					if (args.length < 3)
						return "\u00a7cUsage: crosshair type [heart|tiny|dot|x|clover]";
					{
						String v = args[2].toLowerCase(Locale.ROOT);
						if (v.equals("default")) v = "plus";
						if (v.equals("heart") || v.equals("tiny") || v.equals("dot") || v.equals("x") || v.equals("clover") || v.equals("plus")) {
							Modules.crosshairMode.setStringValue(v);
							save();
							return "\u00a77crosshair type: \u00a7f" + v;
						}
						return "\u00a7cInvalid type. Options: heart, tiny, dot, x, clover, plus";
					}
				case "color":
					if (args.length < 3)
						return "\u00a7cUsage: crosshair color [255,255,255 | 255.255.255 | 255255255 | ff0000]";
					{
						String raw = args[2];
						String canon = normalizeCrosshairColor(raw);
						if (canon == null)
							return "\u00a7cInvalid color format. Use: 255,255,255 | 255.255.255 | 255255255 | ff0000";
						Modules.crosshairColor.setStringValue(canon);
						Modules.crosshairMode.setStringValue("heart");
						save();
						return "\u00a77crosshair color: \u00a7f#" + canon;
					}
				default:
					return "\u00a7cUsage: crosshair [on|off|type|color] [value]";
				}
			}
			@Override public String getDescription() { return "Custom crosshair type and color"; }
			@Override public List<String> tabComplete(String[] args) {
			if (args.length == 2) return Arrays.asList("on", "off", "type", "color");
			if (args.length == 3 && args[1].equalsIgnoreCase("type"))
				return Arrays.asList("heart", "tiny", "dot", "x", "clover", "plus", "default");
				return Collections.emptyList();
			}
		});
	}

	private static String normalizeCrosshairColor(String raw) {
		if (raw == null) return null;
		String s = raw.trim().replace("#", "").replace("0x", "").replace("0X", "");
		int r, g, b;
		try {
			if (s.contains(",")) {
				String[] p = s.split(",");
				if (p.length != 3) return null;
				r = Integer.parseInt(p[0].trim());
				g = Integer.parseInt(p[1].trim());
				b = Integer.parseInt(p[2].trim());
			} else if (s.contains(".")) {
				String[] p = s.split("\\.");
				if (p.length != 3) return null;
				r = Integer.parseInt(p[0].trim());
				g = Integer.parseInt(p[1].trim());
				b = Integer.parseInt(p[2].trim());
			} else if (s.matches("\\d{3,9}")) {
				int[] rgb = parseDecimalRgb(s);
				if (rgb == null) return null;
				r = rgb[0]; g = rgb[1]; b = rgb[2];
			} else if (s.matches("[0-9a-fA-F]{6}")) {
				int rgb = Integer.parseInt(s, 16);
				r = (rgb >> 16) & 0xFF;
				g = (rgb >> 8) & 0xFF;
				b = rgb & 0xFF;
			} else {
				return null;
			}
			if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return null;
			return String.format("%02x%02x%02x", r, g, b);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int[] parseDecimalRgb(String s) {
		int len = s.length(), ptr = 0;
		int[] rgb = new int[3];
		try {
			for (int comp = 0; comp < 3; comp++) {
				if (ptr >= len) { rgb[comp] = 0; continue; }
				if (s.charAt(ptr) == '0') {
					rgb[comp] = 0; ptr++;
				} else {
					int remaining = len - ptr;
					int remainingComps = 2 - comp;
					int maxTake = remainingComps > 0 ? Math.min(3, remaining - remainingComps) : Math.min(3, remaining);
					if (maxTake < 1) maxTake = remaining - remainingComps;
					int end = Math.min(ptr + maxTake, len);
					rgb[comp] = Integer.parseInt(s.substring(ptr, end));
					ptr = end;
				}
			}
			for (int v : rgb) if (v < 0 || v > 255) return null;
			return rgb;
		} catch (NumberFormatException e) { return null; }
	}

	// ── playerhit ──────────────────────────────────────────────────────

	private static void registerPlayerHitCmd() {
		SparrowConsoleCommand.Command playerHitCmd = new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					String onoff = Modules.playerHitEnabled.isEnabled() ? "\u00a7aON" : "\u00a7cOFF";
					return "\u00a77playerhit: " + onoff
						+ " \u00a77type=" + Modules.playerHitType.stringValue()
						+ " \u00a77color=#" + Modules.playerHitColor.stringValue()
						+ "\u00a77 (Usage: playerhit [on|off|type|color] [value])\u00a77";
				}
				String sub = args[1].toLowerCase(java.util.Locale.ROOT);
				switch (sub) {
				case "on":
					Modules.playerHitEnabled.setEnabled(true);
					save();
					return "\u00a77player-hit: \u00a7aON";
				case "off":
					Modules.playerHitEnabled.setEnabled(false);
					save();
					return "\u00a77player-hit: \u00a7cOFF";
				case "type":
					if (args.length < 3) return "\u00a7cUsage: playerhit type [hit|abletohit]";
					{
						String v = args[2].toLowerCase(java.util.Locale.ROOT);
						if (v.equals("hit") || v.equals("abletohit")) {
							Modules.playerHitType.setStringValue(v);
							save();
							return "\u00a77player-hit type: \u00a7f" + v;
						}
						return "\u00a7cInvalid type. Options: hit, abletohit";
					}
				case "color":
					if (args.length < 3) return "\u00a7cUsage: playerhit color <hex>";
					{
						String raw = args[2];
						String canon = normalizePlayerHitColor(raw);
						if (canon == null)
							return "\u00a7cInvalid color. Use hex like ff0000 (red) or ff6666";
						Modules.playerHitColor.setStringValue(canon);
						save();
						return "\u00a77player-hit color: \u00a7f#" + canon;
					}
				default:
					return "\u00a7cUsage: playerhit [on|off|type|color] [value]";
				}
			}
			@Override public String getDescription() { return "Player hit color overlay"; }
			@Override public java.util.List<String> tabComplete(String[] args) {
				if (args.length == 2) return java.util.Arrays.asList("on", "off", "type", "color");
				if (args.length == 3 && args[1].equalsIgnoreCase("type"))
					return java.util.Arrays.asList("hit", "abletohit");
				return java.util.Collections.emptyList();
			}
		};
		SparrowConsoleCommand.register("playerhit", playerHitCmd);
		// B2 alias: the composite parent module is player-hit but the
		// console command only existed as playerhit; `sparrow player-hit`
		// fell through to "Unknown command" (grouped names skip standalone
		// registration). Both spellings work now.
		SparrowConsoleCommand.register("player-hit", playerHitCmd);
	}

	private static String normalizePlayerHitColor(String raw) {
		return xyz.vprolabs.sparrow.util.ColorUtil.normalizeHex(raw);
	}

	// ── movehud ────────────────────────────────────────────────────────

	private static void registerMoveHudCmd() {
		SparrowConsoleCommand.register("movehud", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					HudMoveState.activate();
					return "\u00a77movehud: \u00a7aMove mode activated. Drag elements. ENTER to save, ESC to discard.";
				}
				if (args[1].equalsIgnoreCase("reset")) {
					HudPositions.resetAll();
					save();
					return "\u00a77movehud: \u00a7aAll HUD positions reset to default.";
				}
				HudMoveState.activate();
				return "\u00a77movehud: \u00a7aMove mode activated. Drag elements. ENTER to save, ESC to discard.";
			}
			@Override public String getDescription() { return "Open HUD move mode / reset positions"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("reset");
				return Collections.emptyList();
			}
		});
	}

	// ── FeatureItem list (for 'list' command display) ──────────────────

	static {
		// Modules display through displayValue(); the two non-module features
		// (sneak session state, movehud action) are appended below.
		for (Module m : ModuleManager.all().values()) {
			addFeature(m.id(), m.category(), m::displayValue);
		}
		addFeature("sneak", "Movement",
			() -> ToggleSneakState.enabled ? "\u00a7aON" : "\u00a7cOFF");
		addFeature("movehud", "Visual",
			() -> "\u00a77Action");
	}
}
