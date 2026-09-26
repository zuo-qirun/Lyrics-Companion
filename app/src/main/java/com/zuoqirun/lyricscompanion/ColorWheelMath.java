package com.zuoqirun.lyricscompanion;

/**
 * 圆形调色盘的取色数学（issue #60）。
 *
 * <p>原来的圆盘把 HSV 的 {@code value} 钉死为 1，所以拖不出纯黑与各种深色，用户以为「设不了黑色」。
 * 这里把「色相 / 饱和度 / 明度 → RGB」抽成纯函数，顺带自己实现 HSV→RGB（{@code Color.HSVToColor}
 * 在 JVM 单测里是 android.jar 的桩，算不出真值），这样亮度轴可以单独测。
 */
final class ColorWheelMath {
    private ColorWheelMath() { }

    /** 触点相对圆心的饱和度：圆心 0、边缘 1，超出圆外按 1 收口。 */
    static float saturationFor(float dx, float dy, float radius) {
        float safeRadius = Math.max(1f, radius);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return Math.max(0f, Math.min(1f, distance / safeRadius));
    }

    /** 触点相对圆心的色相（0–360）：与圆盘绘制方向一致（逆时针为色相增加方向）。 */
    static float hueFor(float dx, float dy) {
        return (float) ((Math.toDegrees(-Math.atan2(dy, dx)) + 360d) % 360d);
    }

    /** 颜色本身的明度：取最亮的通道（HSV 的 value 定义）。 */
    static float valueFromColorComponents(int red, int green, int blue) {
        int max = Math.max(red, Math.max(green, blue));
        return Math.max(0f, Math.min(255, max)) / 255f;
    }

    /** HSV → RGB；{@code value = 0} 时永远是纯黑，与色相 / 饱和度无关。 */
    static int rgbFor(float hue, float saturation, float value) {
        float h = ((hue % 360f) + 360f) % 360f;
        float s = Math.max(0f, Math.min(1f, saturation));
        float v = Math.max(0f, Math.min(1f, value));
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60f) { r = c; g = x; b = 0f; }
        else if (h < 120f) { r = x; g = c; b = 0f; }
        else if (h < 180f) { r = 0f; g = c; b = x; }
        else if (h < 240f) { r = 0f; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0f; b = c; }
        else { r = c; g = 0f; b = x; }
        int red = Math.round((r + m) * 255f);
        int green = Math.round((g + m) * 255f);
        int blue = Math.round((b + m) * 255f);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /** 用圆盘上的触点（相对圆心）取色，明度由外部传入（亮度轴）。 */
    static int colorAt(float dx, float dy, float radius, float value) {
        return rgbFor(hueFor(dx, dy), saturationFor(dx, dy, radius), value);
    }

    static float normalizeValue(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }
}
