package xyz.vprolabs.sparrow.console;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class SparrowConsoleCommand {

	@FunctionalInterface
	public interface Command {
		String execute(String[] args);
		default String getDescription() { return ""; }
		default List<String> tabComplete(String[] args) { return Collections.emptyList(); }
	}

	private static final LinkedHashMap<String, Command> COMMANDS = new LinkedHashMap<>();

	public static void register(String name, Command cmd) {
		COMMANDS.put(name.toLowerCase(Locale.ROOT), cmd);
	}

	private SparrowConsoleCommand() {}

	public static String dispatch(String input) {
		String[] parts = input.trim().split("\\s+");
		if (parts.length == 0 || parts[0].isEmpty()) return "";
		Command cmd = COMMANDS.get(parts[0].toLowerCase(Locale.ROOT));
		if (cmd == null) return "\u00a7cUnknown command. Try help";
		return cmd.execute(parts);
	}

	public static List<String> getCompletions(String prefix) {
		ArrayList<String> results = new ArrayList<>();
		String lower = prefix.toLowerCase(Locale.ROOT);
		for (String name : COMMANDS.keySet()) {
			if (name.startsWith(lower)) results.add(name);
		}
		return results;
	}

	public static List<String> getSubCompletions(String cmdName, String[] partialArgs) {
		Command cmd = COMMANDS.get(cmdName);
		if (cmd == null) return Collections.emptyList();
		List<String> all = cmd.tabComplete(partialArgs);
		if (partialArgs.length == 0) return all;
		String last = partialArgs[partialArgs.length - 1].toLowerCase(Locale.ROOT);
		if (last.isEmpty()) return all;
		List<String> filtered = new ArrayList<>();
		for (String s : all) {
			if (s.toLowerCase(Locale.ROOT).startsWith(last)) filtered.add(s);
		}
		return filtered;
	}

	public static Collection<String> getCommandNames() {
		return COMMANDS.keySet();
	}

	static {
		register("help", new Command() {
			@Override public String execute(String[] args) {
				StringBuilder sb = new StringBuilder("\u00a77\u2501\u2501\u2501 Sparrow Help \u2501\u2501\u2501");
				sb.append("\n\u00a77Type any feature name to toggle it. Use \u00a7flist \u00a77for full listing.");
				sb.append("\n");
				sb.append("\n\u00a7f  help, list");
				sb.append("\n");
				sb.append("\n\u00a77\u2501\u2501 Commands \u2501\u2501");
				sb.append("\n\u00a7f  glint \u00a77\u2014 Custom enchant glint color");
				sb.append("\n\u00a7f  view \u00a77\u2014 View model position/size");
				sb.append("\n\u00a7f  zoom \u00a77\u2014 Zoom settings");
				sb.append("\n\u00a7f  fire-timer \u00a77\u2014 Fire timer overlay");
				sb.append("\n\u00a7f  particles \u00a77\u2014 Particle mode");
				sb.append("\n\u00a7f  block-lod \u00a77\u2014 Block LOD mode");
				sb.append("\n\u00a7f  sneak \u00a77\u2014 Toggle-sneak");
				sb.append("\n\u00a7f  clear \u00a77\u2014 Clear the console");
				sb.append("\n\u00a77  ...and more (coords, ping, desync, etc.)");
				sb.append("\n\u00a77Use \u00a7flist \u00a77to see all features.");
				return sb.toString();
			}
			@Override public String getDescription() { return "Show this help message"; }
		});

		register("clear", new Command() {
			@Override public String execute(String[] args) {
				SparrowConsoleState.clear();
				return "";
			}
			@Override public String getDescription() { return "Clear the console"; }
		});

		register("list", new Command() {
			@Override public String execute(String[] args) {
				StringBuilder sb = new StringBuilder();
				java.util.List<FeatureRegistry.FeatureItem> features = FeatureRegistry.getFeatures();

				java.util.LinkedHashMap<String, java.util.List<FeatureRegistry.FeatureItem>> grouped = new java.util.LinkedHashMap<>();
				for (FeatureRegistry.FeatureItem f : features) {
					grouped.computeIfAbsent(f.category(), k -> new java.util.ArrayList<>()).add(f);
				}

				int catIdx = 0;
				for (String cat : grouped.keySet()) {
					java.util.List<FeatureRegistry.FeatureItem> items = grouped.get(cat);
					sb.append("\u00a77=== ").append(cat).append(" ===");

					for (FeatureRegistry.FeatureItem f : items) {
						sb.append("\n\u00a77  ").append(f.name()).append(": ").append(f.display().get());
					}

					if (catIdx < grouped.size() - 1) sb.append("\n");
					catIdx++;
				}
				return sb.toString();
			}
			@Override public String getDescription() { return "List all features and values"; }
		});

		register("gui", new Command() {
			@Override public String execute(String[] args) {
				// Open the click GUI from the console; the keybind (G) toggles
				// it directly without going through the console.
				net.minecraft.client.MinecraftClient.getInstance()
					.setScreen(new xyz.vprolabs.sparrow.gui.ClickGuiScreen());
				return "\u00a77Sparrow GUI opened. Drag panels by their header, scroll to browse.";
			}
			@Override public String getDescription() { return "Open the click GUI"; }
		});

		FeatureRegistry.registerAllCommands();
	}
}
