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
        return "folia".equals(style) || isLegacyStyle(style);
    }

    static boolean isLegacyStyle(String style) {
        if (style == null || !style.startsWith("folia_")) return false;
        for (String value : VALUES) if (style.equals("folia_" + value)) return true;
        return false;
    }

    static String modeForStyle(String style) {
        return isLegacyStyle(style) ? style.substring(6) : "claddagh";
    }

    static String labelForStyle(String style) {
        return "Folia";
    }

    static boolean settingsVisible(int slot, String style) {
        return (slot == 0 || slot == 1) && isStyle(style);
    }

    static String[] append(String[] original, boolean labels) {
        String[] result = java.util.Arrays.copyOf(original, original.length + 1);
        result[original.length] = labels ? "Folia" : "folia";
        return result;
    }
}
