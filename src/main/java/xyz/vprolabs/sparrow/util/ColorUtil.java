package xyz.vprolabs.sparrow.util;

public final class ColorUtil {
    private ColorUtil() {}

    public static int parseRgb24(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        String s = raw.trim().replace("#", "").replace("0x", "");
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
            if (s.matches("\\d{3,9}")) {
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
            if (!s.matches("[0-9a-fA-F]{6}")) return fallback;
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int parseArgb(String raw, int alpha, int fallback) {
        int rgb = parseRgb24(raw, fallback & 0xFFFFFF);
        if ((rgb & 0xFFFFFF) == (fallback & 0xFFFFFF) && raw != null && !raw.isEmpty()
            && !raw.replace("#","").replace("0x","").isEmpty()) {
            String s = raw.replace("#", "").replace("0x", "");
            if (!s.matches("[0-9a-fA-F]{1,6}") && !s.contains(",") && !s.contains(".") && !s.matches("\\d{3,9}")) {
                return fallback;
            }
        }
        return (clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static String normalizeHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("#", "").replace("0x", "");
        if (s.contains(",") || s.contains(".")) return null;
        if (s.matches("\\d{3,9}")) return null;
        try {
            if (s.matches("[0-9a-fA-F]{1,6}")) {
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
