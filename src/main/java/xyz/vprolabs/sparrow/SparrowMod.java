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
     * Storage preview key is a "combo" trigger — it accepts either left or right Control.
     * The KeyBinding itself is bound to LEFT_CONTROL (the default the user sees in Options),
     * but the {@code isPreviewKeyPressed()} helper below checks both so left-handed users
     * (and anyone rebinding to a different physical control) get the same UX.
     */
    public static boolean isPreviewKeyPressed() {
        if (STORAGE_PREVIEW_KEY.isPressed()) return true;
        long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
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
