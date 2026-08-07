package xyz.vprolabs.sparrow.util;

import java.util.regex.Pattern;

public final class ColorUtil {
    // Precompiled patterns (P5): parseArgb/parseRgb24 run per entity per
    // frame (PlayerHitColorMixin); String.matches() recompiles every call.
    private static final Pattern DECIMAL_RGB = Pattern.compile("\\d{3,9}");
    private static final Pattern HEX_6 = Pattern.compile("[0-9a-fA-F]{6}");
    private static final Pattern HEX_1_6 = Pattern.compile("[0-9a-fA-F]{1,6}");

    private ColorUtil() {}

    public static int parseRgb24(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        // HEX-1: strip BOTH lowercase and uppercase 0x prefixes — "0XFF0000"
        // previously fell through to the hex branch and returned fallback.
        String s = raw.trim().replace("#", "").replace("0x", "").replace("0X", "");
        try {
            if (s.contains(",")) {
                String[] p = s.split(",");
                if (p.length == 3) {
                    return (Integer.parseInt(p[0].trim()) << 16)
                         | (Integer.parseInt(p[1].trim()) << 8)
                         | Integer.parseInt(p[2].trim());
                }
            }
            if (s.contains(".")) {
                String[] p = s.split("\\.");
                if (p.length == 3) {
                    return (Integer.parseInt(p[0].trim()) << 16)
                         | (Integer.parseInt(p[1].trim()) << 8)
                         | Integer.parseInt(p[2].trim());
                }
            }
            if (DECIMAL_RGB.matcher(s).matches()) {
                int[] rgb = parseDecimalRgb(s);
                if (rgb != null) return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                return fallback;
            }
            if (s.length() < 6) {
                StringBuilder sb = new StringBuilder(s);
                while (sb.length() < 6) sb.append('0');
                s = sb.toString();
            }
            if (s.length() > 6) s = s.substring(0, 6);
            if (!HEX_6.matcher(s).matches()) return fallback;
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int parseArgb(String raw, int alpha, int fallback) {
        int rgb = parseRgb24(raw, fallback & 0xFFFFFF);
        if ((rgb & 0xFFFFFF) == (fallback & 0xFFFFFF) && raw != null && !raw.isEmpty()
            && !raw.replace("#","").replace("0x","").replace("0X","").isEmpty()) {
            String s = raw.replace("#", "").replace("0x", "").replace("0X", "");
            if (!HEX_1_6.matcher(s).matches() && !s.contains(",") && !s.contains(".") && !DECIMAL_RGB.matcher(s).matches()) {
                return fallback;
            }
        }
        return (clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static String normalizeHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("#", "").replace("0x", "").replace("0X", "");
        if (s.contains(",") || s.contains(".")) return null;
        if (DECIMAL_RGB.matcher(s).matches()) return null;
        try {
            if (HEX_1_6.matcher(s).matches()) {
                StringBuilder sb = new StringBuilder(s);
                while (sb.length() < 6) sb.append('0');
                Integer.parseInt(sb.toString(), 16);
                return sb.toString();
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int[] parseDecimalRgb(String s) {
        int len = s.length(), ptr = 0;
        int[] rgb = new int[3];
        try {
            for (int comp = 0; comp < 3; comp++) {
                if (ptr >= len) { rgb[comp] = 0; continue; }
                if (s.charAt(ptr) == '0') {
                    rgb[comp] = 0; ptr++;
                } else {
                    int remaining = len - ptr;
                    int remainingComps = 2 - comp;
                    int maxTake = remainingComps > 0 ? Math.min(3, remaining - remainingComps) : Math.min(3, remaining);
                    if (maxTake < 1) maxTake = remaining - remainingComps;
                    int end = Math.min(ptr + maxTake, len);
                    rgb[comp] = Integer.parseInt(s.substring(ptr, end));
                    ptr = end;
                }
            }
            for (int v : rgb) if (v < 0 || v > 255) return null;
            return rgb;
        } catch (NumberFormatException e) { return null; }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }
}
