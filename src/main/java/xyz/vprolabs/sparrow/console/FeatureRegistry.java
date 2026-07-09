package xyz.vprolabs.sparrow.console;

import xyz.vprolabs.sparrow.config.ConfigReader;
import xyz.vprolabs.sparrow.config.ConfigRegister;
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
		if (ConfigReader.getInstance() != null) ConfigReader.saveFromCache();
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
					if (v < min || v > max)
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
			"zoom", "zoom-smoothness", "zoom-min", "zoom-max",
			"fire-timer", "fire-timer-pos",
			"particles", "block-lod-mode", "movehud",
            "crosshair", "crosshair-color",
            "player-hit", "player-hit-type", "player-hit-color"
        );

		for (ConfigRegister.Entry e : ConfigRegister.getAll()) {
			if (groupedNames.contains(e.name())) continue;
			if (e instanceof ConfigRegister.Toggle t) {
				registerToggleCmd(t.name(), t::get, t::set);
			} else if (e instanceof ConfigRegister.SetEntry s) {
				if (s.name().equals("item-culling-distance")) {
					registerFloatCmd(s.name(), s::get, s::set, 5.0f, 200.0f);
				} else if (s.name().equals("entity-culling-distance")) {
					registerFloatCmd(s.name(), s::get, s::set, 5.0f, 500.0f);
				}
			} else if (e instanceof ConfigRegister.IntEntry i) {
				if (i.name().equals("nether-render-cap")) {
					registerIntCmd(i.name(), i::get, i::set, 2, 20);
				} else if (i.name().equals("console-fps")) {
					registerConsoleFpsCmd();
				}
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
	}

	// ── console-fps (needs SparrowConsoleState.consoleFps sync) ────────

	private static void registerConsoleFpsCmd() {
		SparrowConsoleCommand.register("console-fps", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2)
					return "\u00a77console-fps: \u00a7f" + ConfigRegister.consoleFps.get();
				try {
					int v = Integer.parseInt(args[1]);
					if (v < 5) return "\u00a7cValue must be >= 5";
					ConfigRegister.consoleFps.set(v);
					SparrowConsoleState.consoleFps = v;
					save();
					return "\u00a77console-fps: \u00a7f" + ConfigRegister.consoleFps.get();
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
					return "\u00a77glint \u00a7fr=" + ConfigRegister.glintR.get()
						+ " \u00a7fg=" + ConfigRegister.glintG.get()
						+ " \u00a7fb=" + ConfigRegister.glintB.get()
						+ " \u00a77custom=" + (ConfigRegister.customGlint.get() ? "\u00a7aON" : "\u00a7cOFF");
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					ConfigRegister.customGlint.set(true);
					SparrowGlintLayers.refresh();
					save();
					return "\u00a77custom-glint: \u00a7aON";
				case "off":
					ConfigRegister.customGlint.set(false);
					SparrowGlintLayers.refresh();
					save();
					return "\u00a77custom-glint: \u00a7cOFF";
				case "r": {
					if (args.length < 3) return "\u00a7cUsage: glint r <0-255>";
					try {
						int v = Integer.parseInt(args[2]);
						if (v < 0 || v > 255) return "\u00a7cValue must be 0-255";
						ConfigRegister.glintR.set(v);
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
						ConfigRegister.glintG.set(v);
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
						ConfigRegister.glintB.set(v);
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
					return "\u00a77view \u00a7fx=" + ConfigRegister.viewModelX.get()
						+ " \u00a7fy=" + ConfigRegister.viewModelY.get()
						+ " \u00a7fz=" + ConfigRegister.viewModelZ.get()
						+ " \u00a7fsize=" + ConfigRegister.viewModelSize.get()
						+ " \u00a7futility-scale=" + ConfigRegister.utilityScale.get();
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				try {
					switch (sub) {
					case "x":
						if (args.length < 3) return "\u00a7cUsage: view x <float>";
						ConfigRegister.viewModelX.set(Float.parseFloat(args[2]));
						save();
						return "\u00a77view-x: \u00a7f" + ConfigRegister.viewModelX.get();
					case "y":
						if (args.length < 3) return "\u00a7cUsage: view y <float>";
						ConfigRegister.viewModelY.set(Float.parseFloat(args[2]));
						save();
						return "\u00a77view-y: \u00a7f" + ConfigRegister.viewModelY.get();
					case "z":
						if (args.length < 3) return "\u00a7cUsage: view z <float>";
						ConfigRegister.viewModelZ.set(Float.parseFloat(args[2]));
						save();
						return "\u00a77view-z: \u00a7f" + ConfigRegister.viewModelZ.get();
					case "size":
						if (args.length < 3) return "\u00a7cUsage: view size <float (>= 0.01)>";
						{
							float v = Float.parseFloat(args[2]);
							if (v < 0.01f) return "\u00a7cValue must be >= 0.01";
							ConfigRegister.viewModelSize.set(v);
							save();
							return "\u00a77view-size: \u00a7f" + ConfigRegister.viewModelSize.get();
						}
					case "utility-scale":
						if (args.length < 3) return "\u00a7cUsage: view utility-scale <0.1-2.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (v < 0.1f || v > 2.0f) return "\u00a7cValue must be 0.1-2.0";
							ConfigRegister.utilityScale.set(v);
							save();
							return "\u00a77utility-scale: \u00a7f" + ConfigRegister.utilityScale.get();
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
					return "\u00a77zoom \u00a7f" + ConfigRegister.zoomLevel.get() + "x"
						+ " \u00a77smoothness=" + ConfigRegister.zoomSmoothness.get()
						+ " \u00a77min=" + ConfigRegister.zoomMin.get()
						+ " \u00a77max=" + ConfigRegister.zoomMax.get();
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				try {
					switch (sub) {
					case "smoothness":
						if (args.length < 3) return "\u00a7cUsage: zoom smoothness <float (>= 1.0)>";
						{
							float v = Float.parseFloat(args[2]);
							if (v < 1.0f) return "\u00a7cValue must be >= 1.0";
							ConfigRegister.zoomSmoothness.set(v);
							save();
							return "\u00a77zoom-smoothness: \u00a7f" + ConfigRegister.zoomSmoothness.get() + "x";
						}
					case "min":
						if (args.length < 3) return "\u00a7cUsage: zoom min <0.5-10.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (v < 0.5f || v > 10.0f) return "\u00a7cValue must be 0.5-10.0";
							ConfigRegister.zoomMin.set(v);
							save();
							return "\u00a77zoom-min: \u00a7f" + ConfigRegister.zoomMin.get();
						}
					case "max":
						if (args.length < 3) return "\u00a7cUsage: zoom max <5.0-50.0>";
						{
							float v = Float.parseFloat(args[2]);
							if (v < 5.0f || v > 50.0f) return "\u00a7cValue must be 5.0-50.0";
							ConfigRegister.zoomMax.set(v);
							save();
							return "\u00a77zoom-max: \u00a7f" + ConfigRegister.zoomMax.get();
						}
					default:
						{
							float v = Float.parseFloat(sub);
							if (v < 1.0f) return "\u00a7cZoom level must be >= 1.0";
							ConfigRegister.zoomLevel.set(v);
							save();
							return "\u00a77zoom: \u00a7f" + ConfigRegister.zoomLevel.get() + "x";
						}
					}
				} catch (NumberFormatException e) {
					if (args.length >= 3 || (args.length == 2 && !sub.equals("smoothness")
					    && !sub.equals("min") && !sub.equals("max"))) {
						return "\u00a7cUsage: zoom [smoothness|min|max] <value>  or  zoom <level>";
					}
					return "\u00a7cUsage: zoom [smoothness|min|max] <value>  or  zoom <level>";
				}
			}
			@Override public String getDescription() { return "Zoom settings"; }
			@Override public List<String> tabComplete(String[] args) {
				if (args.length == 2) return Arrays.asList("smoothness", "min", "max");
				return Collections.emptyList();
			}
		});
	}

	// ── fire-timer ─────────────────────────────────────────────────────

	private static void registerFireTimerCmd() {
		SparrowConsoleCommand.register("fire-timer", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					return "\u00a77fire-timer: " + (ConfigRegister.fireTimer.get() ? "\u00a7aON" : "\u00a7cOFF")
						+ " \u00a77pos=" + ConfigRegister.fireTimerPos.get();
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					ConfigRegister.fireTimer.set(true);
					save();
					return "\u00a77fire-timer: \u00a7aON";
				case "off":
					ConfigRegister.fireTimer.set(false);
					save();
					return "\u00a77fire-timer: \u00a7cOFF";
				case "pos":
					if (args.length < 3) return "\u00a7cUsage: fire-timer pos [TOP_LEFT|TOP_RIGHT|BOTTOM_CENTER]";
					{
						String v = args[2].toUpperCase(Locale.ROOT);
						if (v.equals("TOP_LEFT") || v.equals("TOP_RIGHT") || v.equals("BOTTOM_CENTER")) {
							ConfigRegister.fireTimerPos.set(v);
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
					return "\u00a77particles: \u00a7f" + ConfigRegister.particleMode.get();
				String v = args[1].toLowerCase(Locale.ROOT);
				if (v.equals("off") || v.equals("minimal") || v.equals("on")) {
					ConfigRegister.particleMode.set(v);
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
		SparrowConsoleCommand.register("block-lod", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2)
					return "\u00a77block-lod: \u00a7f" + ConfigRegister.blockLodMode.get();
				String v = args[1].toUpperCase(Locale.ROOT);
			if (v.equals("OFF") || v.equals("LOW") || v.equals("PVP") || v.equals("AGGRESSIVE")) {
				ConfigRegister.blockLodMode.set(v);
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
		});
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
					String type = ConfigRegister.crosshair.get();
					String color = ConfigRegister.crosshairColor.get();
					return "\u00a77crosshair: " + (type.equals("off") ? "\u00a7cOFF" : "\u00a7aON")
						+ "\u00a77 type=" + type + " \u00a77color=#" + color
						+ "\u00a77 (Usage: crosshair [on|off|type|color] [value])\u00a77";
				}
				String sub = args[1].toLowerCase(Locale.ROOT);
				switch (sub) {
				case "on":
					if (ConfigRegister.crosshair.get().equals("off"))
						ConfigRegister.crosshair.set("plus");
					save();
					return "\u00a77crosshair: \u00a7aON \u00a77type=" + ConfigRegister.crosshair.get();
				case "off":
					ConfigRegister.crosshair.set("off");
					save();
					return "\u00a77crosshair: \u00a7cOFF";
				case "type":
					if (args.length < 3)
						return "\u00a7cUsage: crosshair type [heart|tiny|dot|x|clover]";
					{
						String v = args[2].toLowerCase(Locale.ROOT);
						if (v.equals("default")) v = "plus";
						if (v.equals("heart") || v.equals("tiny") || v.equals("dot") || v.equals("x") || v.equals("clover") || v.equals("plus")) {
							ConfigRegister.crosshair.set(v);
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
						ConfigRegister.crosshairColor.set(canon);
						ConfigRegister.crosshair.set("heart");
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
		String s = raw.trim().replace("#", "").replace("0x", "");
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
		SparrowConsoleCommand.register("playerhit", new SparrowConsoleCommand.Command() {
			@Override public String execute(String[] args) {
				if (args.length < 2) {
					String onoff = ConfigRegister.playerHit.get() ? "\u00a7aON" : "\u00a7cOFF";
					return "\u00a77playerhit: " + onoff
						+ " \u00a77type=" + ConfigRegister.playerHitType.get()
						+ " \u00a77color=#" + ConfigRegister.playerHitColor.get()
						+ "\u00a77 (Usage: playerhit [on|off|type|color] [value])\u00a77";
				}
				String sub = args[1].toLowerCase(java.util.Locale.ROOT);
				switch (sub) {
				case "on":
					ConfigRegister.playerHit.set(true);
					save();
					return "\u00a77player-hit: \u00a7aON";
				case "off":
					ConfigRegister.playerHit.set(false);
					save();
					return "\u00a77player-hit: \u00a7cOFF";
				case "type":
					if (args.length < 3) return "\u00a7cUsage: playerhit type [hit|abletohit]";
					{
						String v = args[2].toLowerCase(java.util.Locale.ROOT);
						if (v.equals("hit") || v.equals("abletohit")) {
							ConfigRegister.playerHitType.set(v);
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
						ConfigRegister.playerHitColor.set(canon);
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
		});
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
		for (ConfigRegister.Entry e : ConfigRegister.getAll()) {
			java.util.function.Supplier<String> display;
			if (e instanceof ConfigRegister.Toggle t) {
				display = t::display;
			} else if (e instanceof ConfigRegister.SetEntry s) {
				display = s::display;
			} else if (e instanceof ConfigRegister.IntEntry i) {
				display = i::display;
			} else if (e instanceof ConfigRegister.StringEntry s) {
				display = s::display;
			} else {
				continue;
			}
			addFeature(e.name(), e.category(), display);
		}
		addFeature("sneak", "Movement",
			() -> ToggleSneakState.enabled ? "\u00a7aON" : "\u00a7cOFF");
		addFeature("movehud", "Visual",
			() -> "\u00a77Action");
	}
}
