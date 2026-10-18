package com.zuoqirun.lyricscompanion;

/** Stable Folia ids, independent of translated names and the existing text-effect catalogue. */
final class FoliaModeCatalog {
    static final String LOW_STYLE = "folia";
    static final String ORIGINAL_STYLE = "folia_original";
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
        return LOW_STYLE.equals(style) || isOriginalStyle(style) || isLegacyStyle(style);
    }

    static boolean isOriginalStyle(String style) { return ORIGINAL_STYLE.equals(style); }

    static String normalizeStyle(String style) {
        if (isOriginalStyle(style)) return ORIGINAL_STYLE;
        return LOW_STYLE;
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
        return isOriginalStyle(style) ? "还原版folia" : "低配版folia";
    }

    static boolean settingsVisible(int slot, String style) {
        return (slot == 0 || slot == 1) && isStyle(style);
    }

    static String[] append(String[] original, boolean labels) {
        String[] result = java.util.Arrays.copyOf(original, original.length + 2);
        result[original.length] = labels ? labelForStyle(LOW_STYLE) : LOW_STYLE;
        result[original.length + 1] = labels ? labelForStyle(ORIGINAL_STYLE) : ORIGINAL_STYLE;
        return result;
    }
}
