package xyz.vprolabs.sparrow;

import xyz.vprolabs.sparrow.BuildInfo;
import xyz.vprolabs.sparrow.config.ModCompatibilityChecker;
import xyz.vprolabs.sparrow.config.SodiumCompat;
import xyz.vprolabs.sparrow.console.SparrowConsolePlugin;
import xyz.vprolabs.sparrow.crash.SparrowCrashHandler;
import xyz.vprolabs.sparrow.logging.SparrowLogger;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SparrowMod implements ClientModInitializer {

    public static final KeyBinding.Category SPARROW_CATEGORY = KeyBinding.Category.create(Identifier.of("sparrow-mod", "sparrow"));

    public static final KeyBinding ZOOM_KEY = new KeyBinding(
        "key.sparrow.zoom",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        SPARROW_CATEGORY
    );

    public static final KeyBinding STORAGE_PREVIEW_KEY = new KeyBinding(
        "key.sparrow.storage_preview",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_CONTROL,
        SPARROW_CATEGORY
    );

    /**
     * Storage preview key — only active when an inventory screen (HandledScreen) is open
     * so it never conflicts with the vanilla sprint key (also LEFT_CONTROL by default).
     */
    public static boolean isPreviewKeyPressed() {
        if (!(net.minecraft.client.MinecraftClient.getInstance().currentScreen
            instanceof net.minecraft.client.gui.screen.ingame.HandledScreen)) return false;
        return STORAGE_PREVIEW_KEY.isPressed();
    }

    public static final KeyBinding TOGGLE_SNEAK_KEY = new KeyBinding(
        "key.sparrow.toggle_sneak",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_L,
        SPARROW_CATEGORY
    );

    public static final KeyBinding CONSOLE_KEY = new KeyBinding(
        "key.sparrow.console",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_RIGHT_SHIFT,
        SPARROW_CATEGORY
    );

    public static final KeyBinding HUD_MOVE_KEY = new KeyBinding(
        "key.sparrow.hud_move",
        InputUtil.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        SPARROW_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        SparrowLogger.init();
        SodiumCompat.init();
        ModCompatibilityChecker.check();
        SparrowCrashHandler.register();
        SparrowLogger.info("=== Sparrow Mod " + BuildInfo.BUILD_TAG + " initializing ===");
        SparrowLogger.info("Java: " + System.getProperty("java.version"));
        SparrowLogger.info("Working dir: " + System.getProperty("user.dir", "."));
        KeyBinding.updateKeysByCode();
        SparrowLogger.info("Console: sparrow console system initialized");
        SparrowLogger.info("Sparrow Mod init complete -- deferred config load on first render");

        // Discover SparrowConsolePlugin entrypoints from other mods
        try {
            net.fabricmc.loader.api.FabricLoader.getInstance()
                .getEntrypoints("sparrow-console", SparrowConsolePlugin.class)
                .forEach(plugin -> {
                    try {
                        plugin.registerSparrowCommands();
                        SparrowLogger.info("Loaded console plugin: " + plugin.getClass().getName());
                    } catch (Exception e) {
                        SparrowLogger.error("Failed to load console plugin: " + plugin.getClass().getName() + " - " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            SparrowLogger.warn("No SparrowConsolePlugin entrypoints discovered");
        }
    }
}
