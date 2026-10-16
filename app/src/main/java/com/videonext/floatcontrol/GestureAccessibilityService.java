package com.videonext.floatcontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

/**
 * 只做一件事：往屏幕上注入手势。
 *
 * 刻意**不读屏幕内容**（配置里 canRetrieveWindowContent=false）——我们不需要知道屏幕上有什么，
 * 只要能在你指定的 App 里划一下。所以这个服务看不到你的任何界面数据。
 *
 * 为什么需要它：红果、头条这类 App 不响应任何媒体接口（实测连媒体按键都收着不理），
 * 只接受屏幕手势。要控制它们，就只能真的在屏幕上划一下。
 */
public class GestureAccessibilityService extends AccessibilityService {

    private static volatile GestureAccessibilityService instance;

    /** 服务是否已连接。没连上时任何手势都发不出去。 */
    public static boolean isReady() {
        return instance != null;
    }

    /**
     * 在屏幕中间竖直划一下。
     *
     * 参数照搬实测能让红果切集的那次注入：x 取屏幕中线，y 取 70% → 22%，历时 120ms。
     * 比例而非绝对值，所以横竖屏、不同分辨率都能用。
     */
    public static boolean swipe(Context context, boolean up) {
        GestureAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float x = dm.widthPixels / 2f;
        float fromY = dm.heightPixels * (up ? 0.70f : 0.22f);
        float toY = dm.heightPixels * (up ? 0.22f : 0.70f);

        Path path = new Path();
        path.moveTo(x, fromY);
        path.lineTo(x, toY);
        return service.dispatch(path, 120);
    }

    /** 点屏幕正中一下。短视频 App 里这一下通常是播放/暂停。 */
    public static boolean tapCenter(Context context) {
        GestureAccessibilityService service = instance;
        if (service == null) {
            return false;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float x = dm.widthPixels / 2f;
        float y = dm.heightPixels / 2f;

        Path path = new Path();
        path.moveTo(x, y);
        // 必须有位移，纯 moveTo 的路径会被系统判为无效
        path.lineTo(x + 1f, y);
        return service.dispatch(path, 50);
    }

    private boolean dispatch(Path path, long durationMs) {
        try {
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不处理任何事件
    }

    @Override
    public void onInterrupt() {
    }
}
