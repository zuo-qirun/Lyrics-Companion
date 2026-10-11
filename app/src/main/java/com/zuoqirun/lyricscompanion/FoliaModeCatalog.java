package com.zuoqirun.lyricscompanion;

/** Stable Folia ids, independent of translated names and the existing text-effect catalogue. */
final class FoliaModeCatalog {
    static final String[] VALUES = {"claddagh", "sonnet", "tempera", "lumiere", "classic",
            "cadenza", "partita", "fume", "tilt", "monet", "pendolo", "cappella", "diorama", "still"};
    static final String[] LABELS = {"回环", "商籁", "凝彩", "绘光", "流光", "心象", "云阶",
            "浮名", "倾诉", "莫奈", "时计", "群唱", "镜台", "静止"};

    private FoliaModeCatalog() { }

    static String normalize(String mode) {
        for (String value : VALUES) if (value.equals(mode)) return mode;
        return "claddagh";
    }

    static boolean animated(String mode) { return !"still".equals(normalize(mode)); }

    static boolean isStyle(String style) {
        if (style == null || !style.startsWith("folia_")) return false;
        for (String value : VALUES) if (style.equals("folia_" + value)) return true;
        return false;
    }

    static String modeForStyle(String style) {
        return isStyle(style) ? style.substring(6) : "claddagh";
    }

    static String labelForStyle(String style) {
        String mode = modeForStyle(style);
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(mode)) return "Folia · " + LABELS[i];
        return "Folia · 回环";
    }

    static String[] append(String[] original, boolean labels) {
        String[] result = java.util.Arrays.copyOf(original, original.length + VALUES.length);
        for (int i = 0; i < VALUES.length; i++) {
            result[original.length + i] = labels ? "Folia · " + LABELS[i] : "folia_" + VALUES[i];
        }
        return result;
    }
}
