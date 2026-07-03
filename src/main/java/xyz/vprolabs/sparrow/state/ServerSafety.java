package xyz.vprolabs.sparrow.state;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

public class ServerSafety {
    private static final String CDN_URL =
        "https://raw.githubusercontent.com/stfulua/sparrow-bfsl/main/features-server-block.json";

    private static final Map<String, Set<String>> LOCAL_FALLBACK = Map.of(
        "minemen", Set.of("click-relay", "shield-desync-fix")
    );

    private static String lastHost;
    private static final Set<String> disabled = new HashSet<>();
    private static boolean warned;

    private static boolean remoteChecked;
    private static volatile Map<String, Set<String>> remoteBlocklist;
    private static volatile boolean remoteReady;

    public static boolean isFeatureDisabled(String feature) {
        sync();
        return disabled.contains(feature);
    }

    private static void sync() {
        String host = getHost();
        VersionCheck.checkOnce();
        if (host == null) {
            lastHost = null;
            disabled.clear();
            warned = false;
            return;
        }
        if (host.equals(lastHost)) return;
        lastHost = host;
        disabled.clear();
        warned = false;

        applyMap(LOCAL_FALLBACK, host);

        if (remoteReady && remoteBlocklist != null) {
            applyMap(remoteBlocklist, host);
        }

        if (!remoteChecked) {
            remoteChecked = true;
            fetchRemote();
        }

        if (!disabled.isEmpty() && !warned) {
            warned = true;
            showWarning();
        }
    }

    private static String getHost() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry == null) return null;
        return entry.address.toLowerCase();
    }

    private static void applyMap(Map<String, Set<String>> map, String host) {
        for (var e : map.entrySet()) {
            if (host.contains(e.getKey())) {
                disabled.addAll(e.getValue());
            }
        }
    }

    private static void fetchRemote() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(CDN_URL).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (var r = new InputStreamReader(conn.getInputStream())) {
                    char[] buf = new char[4096];
                    int n;
                    while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
                }
                remoteBlocklist = parseJson(sb.toString());
                remoteReady = true;
                lastHost = null;
            } catch (Exception ignored) {
            }
        }, "Sparrow-CDN").start();
    }

    private static Map<String, Set<String>> parseJson(String json) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject servers = root.getAsJsonObject("servers");
        for (var entry : servers.entrySet()) {
            Set<String> features = new HashSet<>();
            var obj = entry.getValue().getAsJsonObject();
            if (obj.has("disable")) {
                for (var feat : obj.getAsJsonArray("disable")) {
                    features.add(feat.getAsString());
                }
            }
            map.put(entry.getKey(), features);
        }
        return map;
    }

    private static void showWarning() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String list = String.join(", ", disabled);
        client.player.sendMessage(Text.literal(
            "§7[Sparrow] §eAuto-disabled for this server: §c" + list
        ), false);
    }
}
