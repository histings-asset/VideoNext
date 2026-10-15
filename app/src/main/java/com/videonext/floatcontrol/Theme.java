package com.videonext.floatcontrol;

import android.content.Context;

/**
 * 由「基色 + 透明度」推导出整套配色。
 *
 * 关键点：图标颜色**不由用户选**，而是按基色的相对亮度自动取黑或白。
 * 这样无论选黑、白、红、橙、品红还是青，图标都落在对比度更高的那一侧，
 * 「不管什么颜色都看得清」才成立。
 */
final class Theme {

    /**
     * 黑白图标的分界点。取的是 WCAG 里「与黑对比度 = 与白对比度」的那个亮度值：
     * sqrt(1.05 × 0.05) − 0.05 ≈ 0.179，跨过它说明换一种图标色更清楚。
     */
    private static final double LIGHT_THRESHOLD = 0.179;

    private Theme() {
    }

    /** 圆盘与收起态小圆的底色：基色叠加透明度。 */
    static int surface(Context context) {
        int alpha = Math.round(Prefs.alphaPercent(context) * 2.55f);
        return (alpha << 24) | (Prefs.baseRgb(context) & 0xFFFFFF);
    }

    /** 贴在底色上的图标色：亮底用深色，暗底用白色。 */
    static int foreground(Context context) {
        return isLight(Prefs.baseRgb(context)) ? 0xFF101820 : 0xFFFFFFFF;
    }

    /**
     * 中心键的底色。中心键没有图标，必须和圆盘有明度差才看得出「这里有个按键」，
     * 所以暗底往白里混、亮底往黑里混。纯黑纯白也能得到可见的灰阶差。
     */
    static int hub(Context context) {
        int alpha = Math.round(Prefs.alphaPercent(context) * 2.55f);
        int rgb = Prefs.baseRgb(context) & 0xFFFFFF;
        int shifted = isLight(rgb)
                ? blend(rgb, 0x000000, 0.22f)
                : blend(rgb, 0xFFFFFF, 0.30f);
        return (alpha << 24) | shifted;
    }

    /** 缺权限时「设置」图标的警示色，取与底色对比度足够的一侧。 */
    static int warning(Context context) {
        return isLight(Prefs.baseRgb(context)) ? 0xFFD32F2F : 0xFFFFB300;
    }

    /** 圆盘描边，只要一点点，用来在浅色画面上收边。 */
    static int stroke(Context context) {
        return isLight(Prefs.baseRgb(context)) ? 0x33000000 : 0x33FFFFFF;
    }

    static boolean isLight(int rgb) {
        return luminance(rgb) > LIGHT_THRESHOLD;
    }

    /** WCAG 相对亮度。 */
    static double luminance(int rgb) {
        double r = channel(((rgb >> 16) & 0xFF) / 255.0);
        double g = channel(((rgb >> 8) & 0xFF) / 255.0);
        double b = channel((rgb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(double value) {
        return (value <= 0.03928)
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** 把 rgb 往 target 混 amount 比例，保持色相、只改明度。 */
    private static int blend(int rgb, int target, float amount) {
        int r = mix((rgb >> 16) & 0xFF, (target >> 16) & 0xFF, amount);
        int g = mix((rgb >> 8) & 0xFF, (target >> 8) & 0xFF, amount);
        int b = mix(rgb & 0xFF, target & 0xFF, amount);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int from, int to, float amount) {
        return Math.round(from * (1 - amount) + to * amount);
    }
}
