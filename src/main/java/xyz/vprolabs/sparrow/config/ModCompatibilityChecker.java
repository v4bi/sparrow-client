package xyz.vprolabs.sparrow.config;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Set;

public final class ModCompatibilityChecker {
    private static final Set<String> CRITICAL = Set.of("sodium", "iris", "optifine", "canvas");
    private static final Set<String> WARN = Set.of("phosphor", "starlight", "entityculling", "moreculling");

    private ModCompatibilityChecker() {}

    public static void check() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String mod : CRITICAL) {
            if (loader.isModLoaded(mod)) {
                SparrowLogger.warn("Incompatible mod detected: " + mod + " — Sparrow features may silently no-op or crash. Consider removing it.");
            }
        }
        for (String mod : WARN) {
            if (loader.isModLoaded(mod)) {
                SparrowLogger.warn("Potential conflict: " + mod + " — some Sparrow features may not work as expected (redundant or opposite goals).");
            }
        }
    }
}
