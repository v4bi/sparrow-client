package xyz.vprolabs.sparrow.mixin.Console;
import xyz.vprolabs.sparrow.console.SparrowConsoleScreen;
import xyz.vprolabs.sparrow.gui.ClickGuiScreen;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class SparrowConsoleKeyMixin {

    @Unique
    private static Screen sparrow_previousScreen = null;

    // 2026-08-01: RShift now opens whichever UI the Sparrow -> "ui" module
    // selects (default "menu" = click GUI; "terminal" = old
    // console screen). The old separate GUI_KEY (G) binding was removed at
    // the user's request — RShift is the single entry point.
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (keyInput.getKeycode() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                if (client.currentScreen instanceof ClickGuiScreen
                    || client.currentScreen instanceof SparrowConsoleScreen) {
                    // Close either UI, restore previous screen
                    Screen prev = sparrow_previousScreen;
                    sparrow_previousScreen = null;
                    client.setScreen(prev);
                    ci.cancel();
                } else if (client.currentScreen == null || client.currentScreen instanceof TitleScreen) {
                    // Open the selected UI (in-game or from main menu)
                    boolean legacy = "terminal".equals(Modules.ui.stringValue());
                    sparrow_previousScreen = client.currentScreen;
                    client.setScreen(legacy ? new SparrowConsoleScreen() : new ClickGuiScreen());
                    ci.cancel();
                }
                // else: chat, settings, sign editing, typing screens — let Right Shift pass through
            }
        } catch (Exception ignored) {
            // intentional — disconnection edge case, keypress not critical
        }
    }
}
