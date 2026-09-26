package com.zuoqirun.lyricscompanion;

/**
 * 判断几块屏幕是不是同一块物理屏（issue #23）。
 *
 * <p>有的车机把同一块物理屏暴露成多个 Display（投屏通道），例如
 * {@code shared_fission_bg_XDJAScreenProjection_0}（ID 3）与
 * {@code shared_fission_bg_XDJAScreenProjection_1}（ID 4）。两块都开启时，歌词会叠在同一块屏上
 * 画两层：样式相同时肉眼只是“更粗更亮”，同时还白跑一遍渲染。
 *
 * <p>本类只回答“像不像同一块屏”，不做任何拦截、合并或隐藏——用户明知重叠仍想两块同时开时，提示要
 * 让路。判断只用名字、分辨率与像素密度，所以这里不引用任何 {@code android.*} 类型，可以在 JVM 上单测。
 *
 * <p>判定口径：名字完全相同，或名字去掉尾部编号后相同且两侧都带编号，算强信号（不必比对分辨率）；
 * 名字相关（底座相同，或一方包含另一方）且分辨率与像素密度完全一致，算弱信号，措辞里会讲清依据。
 * 编号写法里 {@code _0} / {@code #1} / {@code -2} / {@code (3)} 是投屏通道的典型写法，直接用强信号；
 * 只靠空格分隔的 {@code Display 1}、{@code HDMI 2} 也可能是真外接屏的序号，因此降到弱信号，要求
 * 分辨率也一致才提示。
 *
 * <p>{@link #duplicateReason(Screen, Screen)} 非空 ⟺ 疑似同一块屏；{@link #likelySamePanel} 就是它的
 * 布尔形式（强、弱信号都算“疑似”）。
 */
final class DisplayIdentity {
    /** 尾部编号最多几位数字：通道号只有个位数，免得把 {@code Track_2024} 当成通道。 */
    private static final int MAX_TAIL_DIGITS = 3;
    /** 去掉编号后名字至少这么长，免得把 {@code A4} / {@code A5} 当成同一块屏的两个通道。 */
    private static final int MIN_BASE_LENGTH = 2;
    /** “一方包含另一方”时较短的名字至少这么长，免得 {@code TV} 命中 {@code TVBOX}。 */
    private static final int MIN_RELATED_LENGTH = 3;

    /** 一块屏幕的识别信息快照，不含任何 Android 类型。 */
    static final class Screen {
        /** 屏幕名字（{@code Display.getName()}），构造时去掉首尾空白。 */
        final String name;
        /** 分辨率宽（像素，{@code Display.getRealSize()}）；未知时填 0。 */
        final int widthPx;
        /** 分辨率高（像素）；未知时填 0。 */
        final int heightPx;
        /** 像素密度（{@code Display.getDensityDpi()}）；未知时填 0。 */
        final int densityDpi;
        /** 显示 id（{@code Display.getDisplayId()}）；未知时填 -1（issue #71）。 */
        final int displayId;

        Screen(String name, int widthPx, int heightPx, int densityDpi) {
            this(name, widthPx, heightPx, densityDpi, -1);
        }

        Screen(String name, int widthPx, int heightPx, int densityDpi, int displayId) {
            this.name = name == null ? "" : name.trim();
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.densityDpi = densityDpi;
            this.displayId = displayId;
        }
    }

    private DisplayIdentity() {}

    /**
     * 去掉车机给投屏通道加的尾部编号（{@code _0} / {@code #1} / {@code -2} / {@code (3)} 等），保留
     * 真正的屏幕名。名字里本来就像编号的部分（{@code A4}、{@code 1920x720}、{@code Track_2024}）原样
     * 保留；只去掉一层编号。
     */
    static String baseName(String name) {
        return split(name).base;
    }

    /** 两块屏看起来是否就是同一块物理屏（强、弱信号都算，依据见 {@link #duplicateReason}）。 */
    static boolean likelySamePanel(Screen a, Screen b) {
        return !duplicateReason(a, b).isEmpty();
    }

    /** 中文原因（用于给用户看的提示），不像同一块屏时返回空字符串。 */
    static String duplicateReason(Screen a, Screen b) {
        if (a == null || b == null) return "";
        Name left = split(a.name);
        Name right = split(b.name);
        boolean sameSize = sameSizeAndDensity(a, b);
        if (!a.name.isEmpty() && a.name.equalsIgnoreCase(b.name)) {
            // 名字一模一样：车机给同一块屏开两个通道时最常见的写法，不必再比分辨率。
            // 但如果两块屏的 Display ID 都已知且不同，那也可能是两块真的同名屏（哈弗 H6 会同时挂两块
            // 「HDMI 屏幕」，id 1 / id 2），这时只给一条带保留的提示（issue #71）。
            if (a.displayId >= 0 && b.displayId >= 0 && a.displayId != b.displayId) {
                return sameSize
                        ? "名称与分辨率都相同，但 Display ID 不同（" + a.displayId + " / "
                        + b.displayId + "），可能是两块屏"
                        : "屏幕名称相同但分辨率不同（Display " + a.displayId + " / "
                        + b.displayId + "），可能是两块屏";
            }
            return sameSize ? "名称与分辨率都相同" : "屏幕名称完全相同";
        }
        if (left.base.isEmpty() || !left.base.equalsIgnoreCase(right.base)) {
            // 底座不同：只有“一方包含另一方”才算相关，且必须分辨率也一致。
            return sameSize && related(left.base, right.base)
                    ? "名称相近且分辨率、像素密度相同" : "";
        }
        if (left.numbered && right.numbered) {
            if (left.explicit || right.explicit) {
                return "名称只差通道编号 " + left.suffix + " / " + right.suffix;
            }
            // 只有空格分隔的编号（Display 1 / Display 2）也可能是两块真屏，要求分辨率一致。
            return sameSize ? "名称只差尾部编号 " + left.suffix + " / " + right.suffix
                    + "，分辨率、像素密度也相同" : "";
        }
        if (left.numbered || right.numbered) {
            return sameSize ? "名称只差尾部编号 "
                    + (left.numbered ? left.suffix : right.suffix) + "，分辨率、像素密度也相同" : "";
        }
        return sameSize ? "名称与分辨率都相同" : "";
    }

    /** 分辨率与像素密度都一致；分辨率未知（0）时不算一致，免得空数据互相“撞车”。 */
    private static boolean sameSizeAndDensity(Screen a, Screen b) {
        return a.widthPx > 0 && a.heightPx > 0
                && a.widthPx == b.widthPx && a.heightPx == b.heightPx
                && a.densityDpi == b.densityDpi;
    }

    /** 名字相关：一方包含另一方（忽略大小写），且都不算太短。 */
    private static boolean related(String left, String right) {
        if (left.length() < MIN_RELATED_LENGTH || right.length() < MIN_RELATED_LENGTH) return false;
        return containsIgnoreCase(left, right) || containsIgnoreCase(right, left);
    }

    private static boolean containsIgnoreCase(String value, String part) {
        int limit = value.length() - part.length();
        for (int at = 0; at <= limit; at++) {
            if (value.regionMatches(true, at, part, 0, part.length())) return true;
        }
        return false;
    }

    /** 一个名字拆出来的结果：底座名字 + 原样的尾部编号。 */
    private static final class Name {
        final String base;
        /** 原样的尾部编号，如 {@code _0}；没有编号时为空串。 */
        final String suffix;
        final boolean numbered;
        /** 编号带明确分隔符（{@code _} / {@code #} / {@code -} / 括号）——投屏通道的典型写法。 */
        final boolean explicit;

        Name(String base, String suffix, boolean numbered, boolean explicit) {
            this.base = base;
            this.suffix = suffix;
            this.numbered = numbered;
            this.explicit = explicit;
        }
    }

    private static Name split(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) return new Name("", "", false, false);
        int end = name.length();
        char last = name.charAt(end - 1);
        if (last == ')' || last == '）') {
            // “投屏 (3)” / “投屏（3）”
            char open = last == ')' ? '(' : '（';
            int at = name.lastIndexOf(open, end - 2);
            if (at >= 0) {
                String inner = name.substring(at + 1, end - 1).trim();
                String base = trimBase(name.substring(0, at));
                if (isDigits(inner) && base.length() >= MIN_BASE_LENGTH) {
                    return new Name(base, name.substring(at), true, true);
                }
            }
            return new Name(name, "", false, false);
        }

        int digits = end;
        while (digits > 0 && isDigit(name.charAt(digits - 1))) digits--;
        if (digits == end || end - digits > MAX_TAIL_DIGITS) return new Name(name, "", false, false);

        // 分隔符与数字之间可以有空格：“投屏 _ 0”。
        int probe = digits - 1;
        while (probe >= 0 && isSpace(name.charAt(probe))) probe--;
        boolean explicit;
        int tailStart;
        if (probe >= 0 && isSeparator(name.charAt(probe))) {
            explicit = true;
            tailStart = probe;
        } else if (probe != digits - 1) {
            // 只有空格：“Display 2”。
            explicit = false;
            tailStart = probe + 1;
        } else {
            // 直接贴着的：“ScreenProjection0”。
            explicit = false;
            tailStart = digits;
        }
        String base = trimBase(name.substring(0, tailStart));
        if (base.length() < MIN_BASE_LENGTH) return new Name(name, "", false, false);
        return new Name(base, name.substring(tailStart).trim(), true, explicit);
    }

    private static String trimBase(String value) {
        int end = value.length();
        while (end > 0 && (isSpace(value.charAt(end - 1)) || isSeparator(value.charAt(end - 1)))) end--;
        return value.substring(0, end).trim();
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty() || value.length() > MAX_TAIL_DIGITS) return false;
        for (int at = 0; at < value.length(); at++) {
            if (!isDigit(value.charAt(at))) return false;
        }
        return true;
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isSpace(char value) {
        return value == ' ' || value == '\t' || value == '\u3000';
    }

    private static boolean isSeparator(char value) {
        return value == '_' || value == '-' || value == '#'
                || value == '＿' || value == '－' || value == '＃';
    }
}
