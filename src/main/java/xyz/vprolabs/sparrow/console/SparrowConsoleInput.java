package xyz.vprolabs.sparrow.console;

import org.lwjgl.glfw.GLFW;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SparrowConsoleInput {

	private SparrowConsoleInput() {}

	public static boolean keyPressed(int keyCode, int modifiers) {
		return onKey(keyCode, GLFW.GLFW_PRESS);
	}

	public static boolean onKey(int key, int action) {
		if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false;

		switch (key) {
			case GLFW.GLFW_KEY_ENTER:
			case GLFW.GLFW_KEY_KP_ENTER:
				executeCommand();
				return true;

			case GLFW.GLFW_KEY_BACKSPACE:
				if (SparrowConsoleState.cursorPos > 0 && SparrowConsoleState.inputBuffer.length() > 0) {
					SparrowConsoleState.inputBuffer.deleteCharAt(SparrowConsoleState.cursorPos - 1);
					SparrowConsoleState.cursorPos--;
					updateSuggestions();
				}
				return true;

			case GLFW.GLFW_KEY_DELETE:
				if (SparrowConsoleState.cursorPos < SparrowConsoleState.inputBuffer.length()) {
					SparrowConsoleState.inputBuffer.deleteCharAt(SparrowConsoleState.cursorPos);
					updateSuggestions();
				}
				return true;

			case GLFW.GLFW_KEY_LEFT:
				if (SparrowConsoleState.cursorPos > 0) SparrowConsoleState.cursorPos--;
				return true;

			case GLFW.GLFW_KEY_RIGHT:
				if (SparrowConsoleState.cursorPos < SparrowConsoleState.inputBuffer.length()) SparrowConsoleState.cursorPos++;
				return true;

			case GLFW.GLFW_KEY_UP:
				if (!SparrowConsoleState.suggestions.isEmpty()) {
					navigateSuggestion(-1);
					return true;
				}
				navigateHistory(-1);
				updateSuggestions();
				return true;

			case GLFW.GLFW_KEY_DOWN:
				if (!SparrowConsoleState.suggestions.isEmpty()) {
					navigateSuggestion(1);
					return true;
				}
				navigateHistory(1);
				updateSuggestions();
				return true;

			case GLFW.GLFW_KEY_PAGE_UP:
				SparrowConsoleState.scrollOffset += 5;
				return true;

			case GLFW.GLFW_KEY_PAGE_DOWN:
				SparrowConsoleState.scrollOffset = Math.max(0, SparrowConsoleState.scrollOffset - 5);
				return true;

			case GLFW.GLFW_KEY_HOME:
				SparrowConsoleState.cursorPos = 0;
				return true;

			case GLFW.GLFW_KEY_END:
				SparrowConsoleState.cursorPos = SparrowConsoleState.inputBuffer.length();
				return true;

			case GLFW.GLFW_KEY_TAB:
				tabComplete();
				return true;
		}
		return false;
	}

	public static void onChar(char c) {
		if (c < 32 || c == 127) return;
		SparrowConsoleState.inputBuffer.insert(SparrowConsoleState.cursorPos, c);
		SparrowConsoleState.cursorPos++;
		updateSuggestions();
	}

	private static void executeCommand() {
		String input = SparrowConsoleState.inputBuffer.toString().trim();
		SparrowConsoleState.inputBuffer.setLength(0);
		SparrowConsoleState.cursorPos = 0;
		SparrowConsoleState.suggestions.clear();

		if (input.isEmpty()) return;

		SparrowConsoleState.commandHistory.add(input);
		if (SparrowConsoleState.commandHistory.size() > SparrowConsoleState.MAX_CMD_HISTORY) {
			SparrowConsoleState.commandHistory.remove(0);
		}
		SparrowConsoleState.historyIndex = -1;

		SparrowConsoleState.addLine("\u00a77> \u00a7f" + input, 0xFF5A6A8A);

		String cmdInput = input;
		if (cmdInput.toLowerCase(Locale.ROOT).startsWith("sparrow ")) {
			cmdInput = cmdInput.substring(8).trim();
		} else if (cmdInput.equalsIgnoreCase("sparrow")) {
			cmdInput = "help";
		}

		String result = SparrowConsoleCommand.dispatch(cmdInput);
		if (!result.isEmpty()) {
			SparrowConsoleState.addLine("  " + result, 0xFFE8EDF5);
		}
	}

	private static void navigateHistory(int direction) {
		if (SparrowConsoleState.commandHistory.isEmpty()) return;

		int newIndex = SparrowConsoleState.historyIndex + direction;

		if (newIndex < -1) return;
		if (newIndex >= SparrowConsoleState.commandHistory.size()) return;

		if (newIndex == -1) {
			SparrowConsoleState.inputBuffer.setLength(0);
		} else {
			String entry = SparrowConsoleState.commandHistory.get(newIndex);
			SparrowConsoleState.inputBuffer.setLength(0);
			SparrowConsoleState.inputBuffer.append(entry);
		}

		SparrowConsoleState.historyIndex = newIndex;
		SparrowConsoleState.cursorPos = SparrowConsoleState.inputBuffer.length();
	}

	// ── Suggestion preview ───────────────────────────────────────────

	private static void updateSuggestions() {
		List<String> completions = computeCompletions();
		SparrowConsoleState.suggestions.clear();
		if (!completions.isEmpty()) {
			int count = Math.min(5, completions.size());
			SparrowConsoleState.suggestions.addAll(completions.subList(0, count));
		}
		SparrowConsoleState.suggestionIndex = SparrowConsoleState.suggestions.isEmpty() ? -1 : 0;
	}

	private static List<String> computeCompletions() {
		String current = SparrowConsoleState.inputBuffer.toString();
		if (current.trim().isEmpty()) return Collections.emptyList();

		String processed = current;
		if (processed.toLowerCase(Locale.ROOT).startsWith("sparrow ")) {
			processed = processed.substring(8);
		} else if (processed.equalsIgnoreCase("sparrow")) {
			processed = "";
		}

		boolean trailingSpace = current.endsWith(" ");
		String[] parts = processed.trim().split("\\s+");

		if (parts.length == 0 || (parts.length == 1 && parts[0].isEmpty())) {
			return Collections.emptyList();
		}

		if (parts.length == 1 && !trailingSpace) {
			return SparrowConsoleCommand.getCompletions(parts[0]);
		} else {
			String cmd = parts[0].toLowerCase(Locale.ROOT);
			String[] subArgs;
			if (trailingSpace) {
				subArgs = new String[parts.length + 1];
				System.arraycopy(parts, 0, subArgs, 0, parts.length);
				subArgs[parts.length] = "";
			} else {
				subArgs = parts;
			}
			return SparrowConsoleCommand.getSubCompletions(cmd, subArgs);
		}
	}

	private static void navigateSuggestion(int direction) {
		int size = SparrowConsoleState.suggestions.size();
		int idx = SparrowConsoleState.suggestionIndex + direction;
		if (idx < 0) idx = size - 1;
		if (idx >= size) idx = 0;
		SparrowConsoleState.suggestionIndex = idx;
	}

	// ── Tab completion (picks selected suggestion) ──────────────────

	private static void tabComplete() {
		String current = SparrowConsoleState.inputBuffer.toString();
		if (current.trim().isEmpty()) return;

		String processed = current;
		if (processed.toLowerCase(Locale.ROOT).startsWith("sparrow ")) {
			processed = processed.substring(8);
		} else if (processed.equalsIgnoreCase("sparrow")) {
			processed = "";
		}

		boolean trailingSpace = current.endsWith(" ");
		String[] parts = processed.trim().split("\\s+");

		if (parts.length == 0 || (parts.length == 1 && parts[0].isEmpty())) return;

		List<String> completions;
		String prefixToReplace;

		if (parts.length == 1 && !trailingSpace) {
			String partial = parts[0];
			int partialStart = current.length() - partial.length();
			if (partialStart < 0) partialStart = 0;
			prefixToReplace = current.substring(0, partialStart);
			completions = SparrowConsoleCommand.getCompletions(partial);
		} else {
			String cmd = parts[0].toLowerCase(Locale.ROOT);
			String[] subArgs;
			if (trailingSpace) {
				subArgs = new String[parts.length + 1];
				System.arraycopy(parts, 0, subArgs, 0, parts.length);
				subArgs[parts.length] = "";
				prefixToReplace = current;
			} else {
				subArgs = parts;
				String partial = parts[parts.length - 1];
				int partialStart = current.length() - partial.length();
				if (partialStart < 0) partialStart = 0;
				prefixToReplace = current.substring(0, partialStart);
			}
			completions = SparrowConsoleCommand.getSubCompletions(cmd, subArgs);
		}

		if (completions.isEmpty()) return;
		if (!SparrowConsoleState.suggestions.isEmpty()) {
			int idx = Math.max(0, SparrowConsoleState.suggestionIndex);
			String completed = SparrowConsoleState.suggestions.get(idx);
			SparrowConsoleState.inputBuffer.setLength(0);
			SparrowConsoleState.inputBuffer.append(prefixToReplace).append(completed).append(" ");
			SparrowConsoleState.cursorPos = SparrowConsoleState.inputBuffer.length();
			SparrowConsoleState.suggestions.clear();
			return;
		}

		String completed = completions.get(0);
		SparrowConsoleState.inputBuffer.setLength(0);
		SparrowConsoleState.inputBuffer.append(prefixToReplace).append(completed).append(" ");
		SparrowConsoleState.cursorPos = SparrowConsoleState.inputBuffer.length();
		SparrowConsoleState.suggestions.clear();
	}
}
