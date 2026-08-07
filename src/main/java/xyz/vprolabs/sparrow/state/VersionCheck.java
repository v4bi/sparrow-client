package xyz.vprolabs.sparrow.state;

import xyz.vprolabs.sparrow.BuildInfo;
import xyz.vprolabs.sparrow.logging.SparrowLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

public class VersionCheck {
    private static final String VERSION_URL =
        "https://raw.githubusercontent.com/stfulua/sparrow-client/main/version.txt";

    private static boolean checked;

    public static void checkOnce() {
        if (checked) return;
        checked = true;

        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(VERSION_URL).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) return;

                String latest;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    latest = r.readLine();
                }
                if (latest == null || latest.isBlank()) return;
                final String fLatest = latest.trim();

                String current = BuildInfo.BUILD_TAG.trim();
                if (fLatest.equals(current)) return;

                // checkOnce() is called on the first render frame (title
                // screen), where player is null — a plain execute() would
                // drop the notice forever (checked stays true). Poll up to
                // 60s for a live player so the message actually reaches one.
                MinecraftClient client = MinecraftClient.getInstance();
                long deadline = System.currentTimeMillis() + 60_000;
                while (System.currentTimeMillis() < deadline) {
                    if (client.player != null) {
                        client.execute(() -> {
                            if (client.player == null) return;
                            client.player.sendMessage(Text.literal(
                                "§7[Sparrow] §eNew version available: §f" + fLatest +
                                " §7→ §7[§fhttps://github.com/stfulua/sparrow-client/releases§7]"
                            ), false);
                        });
                        return;
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                // F3: the silent catch hid every failure (offline, DNS, 5xx).
                SparrowLogger.warn("VersionCheck: could not check for updates (" + e.getMessage() + ")");
            }
        }, "Sparrow-VersionCheck").start();
    }
}
