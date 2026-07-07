package xyz.vprolabs.sparrow.console;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

public class SparrowConsoleScreen extends Screen {

	private static final int BG       = 0xFF0B0C10;  // launcher BG
	private static final int INPUT_BG = 0xFF000000;
	private static final int FG       = 0xFFC5C6C7;  // launcher FG
	private static final int DIM      = 0xFF555555;  // launcher DIM
	private static final int PROMPT   = 0xFF58A6FF;
	private static final int CURSOR   = 0xFFAAAAAA;
	private static final int LETTER_GAP = 3;          // launcher letter-spacing
	private static final int PAD      = 12;
	private static final int INPUT_H  = 26;

	public SparrowConsoleScreen() {
		super(Text.literal("Sparrow Terminal"));
	}

	@Override public boolean shouldPause() { return true; }

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0x88000000);
	}

	@Override public void removed() { super.removed(); SparrowConsoleState.close(); }

	@Override
	public boolean keyPressed(KeyInput in) {
		if (SparrowConsoleInput.keyPressed(in.getKeycode(), in.modifiers())) return true;
		return super.keyPressed(in);
	}

	@Override
	public boolean charTyped(CharInput in) {
		SparrowConsoleInput.onChar((char) in.codepoint());
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (v > 0) SparrowConsoleState.scrollOffset += 3;
		else if (v < 0) SparrowConsoleState.scrollOffset = Math.max(0, SparrowConsoleState.scrollOffset - 3);
		return true;
	}

	@Override
	public boolean mouseClicked(Click click, boolean bl) { return super.mouseClicked(click, bl); }
	@Override public boolean mouseDragged(Click click, double dx, double dy) { return super.mouseDragged(click, dx, dy); }

	// ── Render ──────────────────────────────────────────────────────

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int panelW = (int)(width * 0.8);
		int panelH = (int)(height * 0.75);
		int panelX = (width - panelW) / 2;
		int panelY = (height - panelH) / 2;
		int outX   = panelX + PAD;
		int outW   = panelW - PAD * 2;
		int lineH  = textRenderer.fontHeight + 2;

		// ── Build wrapped-line cache (once per frame) ──
		List<OrderedText> lines  = new ArrayList<>();
		List<Integer>     colors = new ArrayList<>();
		for (SparrowConsoleState.ConsoleLine e : SparrowConsoleState.history) {
			for (OrderedText line : wrap(e.text(), outW)) {
				lines.add(line);
				colors.add(e.color());
			}
		}

		// ── Background overlay ──
		context.fill(0, 0, width, height, 0x88000000);

		// ── Panel background ──
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG);

		// ── Title (launcher-style: letter-spacing) ──────────────
		String  title    = "SPARROW";
		int     titleW   = 0;
		for (int i = 0; i < title.length(); i++) {
			titleW += textRenderer.getWidth(String.valueOf(title.charAt(i)));
			if (i < title.length() - 1) titleW += LETTER_GAP;
		}
		int     titleX   = panelX + (panelW - titleW) / 2;
		int     titleY   = panelY + PAD + 6;
		int     cx       = titleX;
		for (int i = 0; i < title.length(); i++) {
			String c = String.valueOf(title.charAt(i));
			context.drawText(textRenderer, c, cx, titleY, FG, false);
			cx += textRenderer.getWidth(c) + LETTER_GAP;
		}

		int     sepY     = titleY + textRenderer.fontHeight + 6;
		int     outY     = sepY + 3;
		int     outH     = panelH - PAD - (outY - panelY) - INPUT_H - PAD;
		int     vis      = Math.max(1, outH / lineH);

		// ── Separator ──────────────────────────────────────────
		context.fill(outX, sepY, panelX + panelW - PAD, sepY + 1, DIM);

		// ── Cap scrollOffset so PageUp overflow doesn't orphan us ──
		int maxOff = Math.max(0, lines.size() - vis);
		if (SparrowConsoleState.scrollOffset > maxOff) SparrowConsoleState.scrollOffset = maxOff;

		// ── Output lines ──
		int start  = Math.max(0, lines.size() - vis - SparrowConsoleState.scrollOffset);
		int end    = Math.min(lines.size(), start + vis);
		int drawY  = outY;
		for (int i = start; i < end; i++) {
			context.drawText(textRenderer, lines.get(i), outX, drawY, colors.get(i), false);
			drawY += lineH;
		}

		// ── Input bar ──
		int inputY   = panelY + panelH - INPUT_H - PAD;
		int inputTY  = inputY + (INPUT_H - textRenderer.fontHeight) / 2;
		context.fill(panelX, inputY, panelX + panelW, inputY + INPUT_H, INPUT_BG);

		context.drawText(textRenderer, "> ", outX, inputTY, PROMPT, false);
		String raw = SparrowConsoleState.inputBuffer.toString();
		int textX  = outX + textRenderer.getWidth("> ");
		int maxTW  = outW - textRenderer.getWidth("> ") - 4;
		String disp = raw;
		if (textRenderer.getWidth(raw) > maxTW) {
			while (textRenderer.getWidth("..." + disp) > maxTW && disp.length() > 1)
				disp = disp.substring(1);
			disp = "..." + disp;
		}
		context.drawText(textRenderer, disp, textX, inputTY, FG, false);

		// ── Static cursor (no animation) ──
		String pre = raw.substring(0, Math.min(SparrowConsoleState.cursorPos, raw.length()));
		int curX = textX + textRenderer.getWidth(pre);
		context.fill(curX, inputTY, curX + 1, inputTY + textRenderer.fontHeight, CURSOR);

		// ── Suggestion box ──
		List<String> sugs = SparrowConsoleState.suggestions;
		if (!sugs.isEmpty()) {
			int sugH = sugs.size() * (textRenderer.fontHeight + 2) + 4;
			int sugY = inputY - sugH - 2;
			context.fill(panelX, sugY, panelX + panelW, sugY + sugH, 0xEE0D1117);
			int sy = sugY + 2;
			for (int i = 0; i < sugs.size(); i++) {
				int clr = (i == SparrowConsoleState.suggestionIndex) ? PROMPT : DIM;
				context.drawText(textRenderer, sugs.get(i), outX, sy, clr, false);
				sy += textRenderer.fontHeight + 2;
			}
		}
	}

	// ── Text wrapping ───────────────────────────────────────────────

	private List<OrderedText> wrap(String text, int maxW) {
		List<OrderedText> out = new ArrayList<>();
		if (text == null || text.isEmpty()) { out.add(OrderedText.EMPTY); return out; }
		for (String seg : text.split("\n", -1)) {
			if (seg.isEmpty()) out.add(OrderedText.EMPTY);
			else out.addAll(textRenderer.wrapLines(Text.literal(seg), maxW));
		}
		return out;
	}
}
