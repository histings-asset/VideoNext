package com.videonext.floatcontrol;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** 本应用的本地设置。 */
public final class Prefs {

    private static final String FILE = "videonext";

    private static final String KEY_AUTO_COLLAPSE = "auto_collapse_ms";
    private static final String KEY_HAPTICS = "haptics_enabled";
    private static final String KEY_COLLAPSED_ACTION = "collapsed_action";
    private static final String KEY_DOCK_LEFT = "dock_left";
    private static final String KEY_Y_RATIO = "y_ratio";
    private static final String KEY_HINT_SHOWS = "hint_shows";
    private static final String KEY_HAS_EXPANDED = "has_expanded";
    private static final String KEY_COLOR_INDEX = "theme_color_index";
    private static final String KEY_ALPHA = "theme_alpha";
    private static final String KEY_GESTURE_MODE = "gesture_mode";
    private static final String KEY_GESTURE_PACKAGES = "gesture_packages";

    /** 收起态单击的动作。 */
    public static final int ACTION_NEXT = 0;
    public static final int ACTION_PLAY_PAUSE = 1;
    public static final int ACTION_PREV = 2;
    public static final int[] COLLAPSED_ACTIONS = {ACTION_NEXT, ACTION_PLAY_PAUSE, ACTION_PREV};

    /**
     * 可选底色。图标颜色不跟着变，而是由底色亮度自动算（见 {@link Theme}），
     * 所以浅色和深色都保证看得清。
     */
    public static final int[] COLORS = {
            0xFF000000, // 黑
            0xFFFFFFFF, // 白
            0xFFE53935, // 红
            0xFFFB8C00, // 橙
            0xFFFDD835, // 黄
            0xFF43A047, // 绿
            0xFF00ACC1, // 青
            0xFF1E88E5, // 蓝
            0xFF7C4DFF, // 紫
            0xFFE91E63, // 品红
            0xFF607D8B, // 灰
    };

    /** 默认底色：黑。 */
    public static final int DEFAULT_COLOR_INDEX = 0;

    /** 透明度档位（百分数）。 */
    public static final int[] ALPHA_OPTIONS = {100, 85, 70, 50};

    /** 默认值：展开后停手多久自动缩回。 */
    public static final long DEFAULT_AUTO_COLLAPSE_MS = 3000L;

    /**
     * 可选档位（毫秒），与 strings 里的 auto_collapse_options 一一对应。
     * 0 表示不自动缩回。
     */
    public static final long[] AUTO_COLLAPSE_OPTIONS = {2000L, 3000L, 5000L, 10000L, 0L};

    /** 悬浮球默认停在屏幕高度这个比例处。 */
    public static final float DEFAULT_Y_RATIO = 0.15f;

    private Prefs() {
    }

    public static long autoCollapseMs(Context context) {
        return store(context).getLong(KEY_AUTO_COLLAPSE, DEFAULT_AUTO_COLLAPSE_MS);
    }

    public static void setAutoCollapseMs(Context context, long ms) {
        store(context).edit().putLong(KEY_AUTO_COLLAPSE, ms).apply();
    }

    /** 当前设置对应到档位列表里的下标。 */
    public static int autoCollapseIndex(Context context) {
        long current = autoCollapseMs(context);
        for (int i = 0; i < AUTO_COLLAPSE_OPTIONS.length; i++) {
            if (AUTO_COLLAPSE_OPTIONS[i] == current) {
                return i;
            }
        }
        return 1;
    }

    public static boolean hapticsEnabled(Context context) {
        return store(context).getBoolean(KEY_HAPTICS, true);
    }

    public static void setHapticsEnabled(Context context, boolean enabled) {
        store(context).edit().putBoolean(KEY_HAPTICS, enabled).apply();
    }

    public static int collapsedAction(Context context) {
        return store(context).getInt(KEY_COLLAPSED_ACTION, ACTION_NEXT);
    }

    public static void setCollapsedAction(Context context, int action) {
        store(context).edit().putInt(KEY_COLLAPSED_ACTION, action).apply();
    }

    /** 当前设置对应到动作列表里的下标。 */
    public static int collapsedActionIndex(Context context) {
        int current = collapsedAction(context);
        for (int i = 0; i < COLLAPSED_ACTIONS.length; i++) {
            if (COLLAPSED_ACTIONS[i] == current) {
                return i;
            }
        }
        return 0;
    }

    public static boolean dockLeft(Context context) {
        return store(context).getBoolean(KEY_DOCK_LEFT, false);
    }

    public static void setDockLeft(Context context, boolean left) {
        store(context).edit().putBoolean(KEY_DOCK_LEFT, left).apply();
    }

    public static float yRatio(Context context) {
        return store(context).getFloat(KEY_Y_RATIO, DEFAULT_Y_RATIO);
    }

    public static void setYRatio(Context context, float ratio) {
        store(context).edit().putFloat(KEY_Y_RATIO, ratio).apply();
    }

    public static int hintShows(Context context) {
        return store(context).getInt(KEY_HINT_SHOWS, 0);
    }

    public static void setHintShows(Context context, int count) {
        store(context).edit().putInt(KEY_HINT_SHOWS, count).apply();
    }

    public static boolean hasExpanded(Context context) {
        return store(context).getBoolean(KEY_HAS_EXPANDED, false);
    }

    public static void setHasExpanded(Context context, boolean value) {
        store(context).edit().putBoolean(KEY_HAS_EXPANDED, value).apply();
    }

    public static int colorIndex(Context context) {
        int index = store(context).getInt(KEY_COLOR_INDEX, DEFAULT_COLOR_INDEX);
        return (index < 0 || index >= COLORS.length) ? DEFAULT_COLOR_INDEX : index;
    }

    public static void setColorIndex(Context context, int index) {
        store(context).edit().putInt(KEY_COLOR_INDEX, index).apply();
    }

    /** 选中的底色，不含透明度。 */
    public static int baseRgb(Context context) {
        return COLORS[colorIndex(context)];
    }

    public static int alphaPercent(Context context) {
        return store(context).getInt(KEY_ALPHA, 100);
    }

    public static void setAlphaPercent(Context context, int percent) {
        store(context).edit().putInt(KEY_ALPHA, percent).apply();
    }

    public static int alphaIndex(Context context) {
        int current = alphaPercent(context);
        for (int i = 0; i < ALPHA_OPTIONS.length; i++) {
            if (ALPHA_OPTIONS[i] == current) {
                return i;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // 手势模式
    // ------------------------------------------------------------------

    public static boolean gestureMode(Context context) {
        return store(context).getBoolean(KEY_GESTURE_MODE, false);
    }

    public static void setGestureMode(Context context, boolean enabled) {
        store(context).edit().putBoolean(KEY_GESTURE_MODE, enabled).apply();
    }

    /** 手势模式生效的 App 包名。刻意不用默认值——默认必须是「一个都不生效」。 */
    public static Set<String> gesturePackages(Context context) {
        Set<String> stored = store(context).getStringSet(KEY_GESTURE_PACKAGES, null);
        return (stored == null) ? new HashSet<String>() : new HashSet<String>(stored);
    }

    public static void addGesturePackage(Context context, String packageName) {
        Set<String> packages = gesturePackages(context);
        packages.add(packageName);
        store(context).edit().putStringSet(KEY_GESTURE_PACKAGES, packages).apply();
    }

    public static void clearGesturePackages(Context context) {
        store(context).edit().remove(KEY_GESTURE_PACKAGES).apply();
    }

    /** 供服务监听设置变化，改了设置立刻生效、不用重启。 */
    static SharedPreferences raw(Context context) {
        return store(context);
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
