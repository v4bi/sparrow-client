package xyz.vprolabs.sparrow.console;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SparrowConsoleState {
    public static boolean visible = false;
    public static final LinkedList<ConsoleLine> history = new LinkedList<>();
    public static final StringBuilder inputBuffer = new StringBuilder();
    public static final ArrayList<String> commandHistory = new ArrayList<>();
    public static int historyIndex = -1;
    public static int scrollOffset = 0;
    public static int cursorPos = 0;
    public static int consoleFps = 60;
    public static final java.util.List<String> suggestions = new java.util.ArrayList<>();
    public static int suggestionIndex = -1;

    public static final int MAX_HISTORY = 100;
    public static final int MAX_CMD_HISTORY = 50;

    private SparrowConsoleState() {}

    public static void toggle() { visible = !visible; }
    public static void open() { visible = true; }
    public static void close() { visible = false; }

    public static void clear() {
        history.clear();
        scrollOffset = 0;
    }

    public static void addLine(String text) {
        addLine(text, 0xFFE8EDF5);
    }

    public static void addLine(String text, int color) {
        if (history.size() >= MAX_HISTORY) {
            history.removeFirst();
        }
        history.add(new ConsoleLine(text, color));
        scrollOffset = 0;
    }

    public record ConsoleLine(String text, int color) {}
}
