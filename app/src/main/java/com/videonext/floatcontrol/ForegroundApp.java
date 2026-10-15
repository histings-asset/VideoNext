package com.videonext.floatcontrol;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.List;

/**
 * 前台应用探测。
 *
 * Android 不向普通应用开放「现在前台是哪个 App」，只能靠用户在系统设置里授予
 * 「使用情况访问」（PACKAGE_USAGE_STATS）之后，用 UsageStatsManager 反推。
 * 未授权时一律返回 null，调用方必须能退回到不依赖前台的策略。
 */
public final class ForegroundApp {

    /** 回看窗口：只要用户不是长时间不切应用，24 小时足够覆盖最后一次切前台。 */
    private static final long LOOKBACK_MS = 24L * 60L * 60L * 1000L;

    private ForegroundApp() {
    }

    /** 前台应用的包名；判断不出来时返回 null。 */
    public static String current(Context context) {
        try {
            UsageStatsManager usm =
                    (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                return null;
            }
            long now = System.currentTimeMillis();
            List<UsageStats> stats =
                    usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - LOOKBACK_MS, now);
            if (stats == null || stats.isEmpty()) {
                return null;
            }
            String best = null;
            long bestTime = 0L;
            for (UsageStats s : stats) {
                if (s.getLastTimeUsed() > bestTime) {
                    bestTime = s.getLastTimeUsed();
                    best = s.getPackageName();
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 是否已授予「使用情况访问」。供设置页显示状态。 */
    public static boolean isGranted(Context context) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops != null) {
                int mode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mode = ops.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            Process.myUid(),
                            context.getPackageName());
                } else {
                    mode = checkOpNoThrowLegacy(ops, context);
                }
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    return true;
                }
            }
            // 部分 ROM 下 appop 读数是 default，但查询实际可用，用一次真实查询兜底。
            return current(context) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static int checkOpNoThrowLegacy(AppOpsManager ops, Context context) {
        return ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName());
    }
}
