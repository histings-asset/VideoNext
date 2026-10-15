package com.videonext.floatcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * 悬浮球前台服务。
 *
 * 收起态：一个小圆，单击执行设定动作，长按展开。
 * 展开态：iPod 式圆形波轮 —— 环上四个符号没有各自的圆底，直接贴在盘面上；
 *         中心是唯一有底的圆键（对应 iPod 的 Select 位），单击收起，长按 1 秒退出 App。
 *         上=设置 · 右=下一首 · 下=播放暂停 · 左=上一首
 */
public class FloatingService extends Service {

    private static final String CHANNEL_ID = "videonext_overlay";
    private static final int NOTIFICATION_ID = 1001;

    // 尺寸（dp）。环上按钮的尺寸是「隐形点击区」，图标只占中间一部分。
    private static final int COLLAPSED_SIZE = 44;
    private static final int WHEEL_SIZE = 152;
    private static final int RING_BUTTON = 48;
    private static final int HUB_BUTTON = 52;

    /** 按键震动时长（毫秒）。 */
    private static final int BUZZ_TAP_MS = 22;
    private static final int BUZZ_LONG_MS = 45;

    /**
     * 中心键长按退出的两个时间点。比系统默认的 500ms 长得多，
     * 并在临近触发前先震一下预告，避免「想收起却按久了」直接退出。
     */
    private static final long EXIT_WARN_MS = 600L;
    private static final long EXIT_HOLD_MS = 1000L;

    /** 停靠时离屏幕边缘的距离（dp）。 */
    private static final int EDGE_MARGIN = 4;

    /** 供界面判断服务是否在运行。 */
    public static volatile boolean isRunning = false;

    private WindowManager windowManager;
    private DraggableLayout rootView;
    private WindowManager.LayoutParams layoutParams;
    private Vibrator vibrator;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private ImageButton btnCollapsed;
    private View wheel;
    private ImageButton btnPrev;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnSettings;
    private ImageButton btnCollapse;

    private boolean expanded = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            collapse();
        }
    };
    private final Runnable exitWarnRunnable = new Runnable() {
        @Override
        public void run() {
            buzz(BUZZ_LONG_MS);
        }
    };
    private final Runnable exitRunnable = new Runnable() {
        @Override
        public void run() {
            buzz(BUZZ_LONG_MS);
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        createNotificationChannel();
        startForegroundCompat();
        initVibrator();
        buildOverlay();
        applyTheme();

        // 设置页改动后立刻生效，不用重启悬浮球
        prefListener = (prefs, key) -> applyTheme();
        Prefs.raw(this).registerOnSharedPreferenceChangeListener(prefListener);

        maybeShowHint();
    }

    /** 在用户成功展开过波轮之前，每次开球提示一次，最多 3 次。 */
    private void maybeShowHint() {
        if (Prefs.hasExpanded(this) || Prefs.hintShows(this) >= 3) {
            return;
        }
        Prefs.setHintShows(this, Prefs.hintShows(this) + 1);
        Toast.makeText(this, R.string.hint_long_press, Toast.LENGTH_LONG).show();
    }

    private void runCollapsedAction() {
        int action = Prefs.collapsedAction(this);
        if (action == Prefs.ACTION_PLAY_PAUSE) {
            MediaControls.playPause(this);
        } else if (action == Prefs.ACTION_PREV) {
            MediaControls.previous(this);
        } else {
            MediaControls.next(this);
        }
    }

    private void initVibrator() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = getSystemService(VibratorManager.class);
                vibrator = (manager == null) ? null : manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 轻震一下。用户可在设置里关掉。 */
    private void buzz(int ms) {
        if (vibrator == null || !vibrator.hasVibrator() || !Prefs.hapticsEnabled(this)) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        rememberPosition();
        if (prefListener != null) {
            try {
                Prefs.raw(this).unregisterOnSharedPreferenceChangeListener(prefListener);
            } catch (Throwable ignored) {
            }
            prefListener = null;
        }
        if (rootView != null && windowManager != null) {
            try {
                windowManager.removeView(rootView);
            } catch (Throwable ignored) {
            }
            rootView = null;
        }
        super.onDestroy();
    }

    /** 记住球停在哪一侧、离顶部多远，下次启动回到原位。 */
    private void rememberPosition() {
        if (layoutParams == null || rootView == null) {
            return;
        }
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (dm.heightPixels <= 0 || dm.widthPixels <= 0) {
                return;
            }
            Prefs.setYRatio(this, layoutParams.y / (float) dm.heightPixels);
            Prefs.setDockLeft(this,
                    layoutParams.x + layoutParams.width / 2 < dm.widthPixels / 2);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------------
    // 配色
    // ---------------------------------------------------------------------

    private void applyTheme() {
        if (btnCollapsed == null) {
            return;
        }

        // 收起态小圆：底色跟随选中的基色，图标跟随「收起后单击」的设置
        int action = Prefs.collapsedAction(this);
        if (action == Prefs.ACTION_PLAY_PAUSE) {
            btnCollapsed.setImageResource(R.drawable.ic_play_pause);
        } else if (action == Prefs.ACTION_PREV) {
            btnCollapsed.setImageResource(R.drawable.ic_prev);
        } else {
            btnCollapsed.setImageResource(R.drawable.ic_next);
        }
        setDisc(btnCollapsed, Theme.surface(this));
        btnCollapsed.setColorFilter(Theme.foreground(this));

        // 波轮盘面
        if (wheel != null) {
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(Theme.surface(this));
            disc.setStroke(dp(1), Theme.stroke(this));
            wheel.setBackground(disc);
        }

        // 环上四个符号（无底色，直接贴在盘面上）
        int foreground = Theme.foreground(this);
        btnPrev.setColorFilter(foreground);
        btnNext.setColorFilter(foreground);
        btnPlayPause.setColorFilter(foreground);

        // 「设置」：缺权限时换成一个与底色对比度足够的警示色，别让功能静默降级
        boolean ready = ForegroundApp.isGranted(this) && isNotificationAccessGranted();
        btnSettings.setColorFilter(ready ? foreground : Theme.warning(this));

        // 中心键没有图标，只靠比盘面亮/暗一档的底色显示「这里有个按键」
        setDisc(btnCollapse, Theme.hub(this));
    }

    private boolean isNotificationAccessGranted() {
        try {
            String flat = Settings.Secure.getString(
                    getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // 悬浮窗
    // ---------------------------------------------------------------------

    private void buildOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        btnCollapsed = makeDiscButton(R.drawable.ic_next, COLLAPSED_SIZE);
        btnCollapsed.setOnClickListener(v -> {
            buzz(BUZZ_TAP_MS);
            runCollapsedAction();
        });
        btnCollapsed.setOnLongClickListener(v -> {
            buzz(BUZZ_LONG_MS);
            expand();
            return true;
        });

        // 环上四个符号：没有圆底，直接贴在盘面上
        btnSettings = makeIconButton(R.drawable.ic_settings, RING_BUTTON);
        btnNext = makeIconButton(R.drawable.ic_next, RING_BUTTON);
        btnPlayPause = makeIconButton(R.drawable.ic_play_pause, RING_BUTTON);
        btnPrev = makeIconButton(R.drawable.ic_prev, RING_BUTTON);
        // 中心键（iPod 的 Select 位）：单击收起，长按退出。没有图标，只靠底色区分。
        btnCollapse = makeCenterButton(HUB_BUTTON);

        btnSettings.setOnClickListener(v -> {
            buzz(BUZZ_TAP_MS);
            openSettings();
            collapse();
        });
        btnNext.setOnClickListener(v -> {
            buzz(BUZZ_TAP_MS);
            MediaControls.next(this);
            resetAutoCollapse();
        });
        btnPlayPause.setOnClickListener(v -> {
            buzz(BUZZ_TAP_MS);
            MediaControls.playPause(this);
            resetAutoCollapse();
        });
        btnPrev.setOnClickListener(v -> {
            buzz(BUZZ_TAP_MS);
            MediaControls.previous(this);
            resetAutoCollapse();
        });
        btnCollapse.setOnClickListener(v -> {
            handler.removeCallbacks(exitWarnRunnable);
            handler.removeCallbacks(exitRunnable);
            buzz(BUZZ_TAP_MS);
            collapse();
        });
        btnCollapse.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(exitWarnRunnable, EXIT_WARN_MS);
                handler.postDelayed(exitRunnable, EXIT_HOLD_MS);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(exitWarnRunnable);
                handler.removeCallbacks(exitRunnable);
            }
            return false;
        });

        WheelLayout wheelLayout = new WheelLayout(this);
        wheelLayout.addView(btnCollapse);     // 0 中心
        wheelLayout.addView(btnSettings);     // 1 上
        wheelLayout.addView(btnNext);         // 2 右
        wheelLayout.addView(btnPlayPause);    // 3 下
        wheelLayout.addView(btnPrev);         // 4 左
        wheelLayout.setVisibility(View.GONE);
        wheel = wheelLayout;

        rootView = new DraggableLayout(this);
        rootView.addView(btnCollapsed,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        rootView.addView(wheel,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int size = dp(COLLAPSED_SIZE);
        layoutParams = new WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        layoutParams.gravity = Gravity.TOP | Gravity.START;

        // 初始位置：回到上次停靠的那一侧和高度
        layoutParams.x = dockedX(Prefs.dockLeft(this), size);
        layoutParams.y = initialY();

        rootView.init(windowManager, layoutParams);
        windowManager.addView(rootView, layoutParams);

        // 手指一碰就重新计时，避免正在操作时被自动缩回打断
        rootView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                resetAutoCollapse();
            }
            return false;
        });
    }

    private void expand() {
        if (expanded) {
            return;
        }
        expanded = true;
        Prefs.setHasExpanded(this, true);
        btnCollapsed.setVisibility(View.GONE);
        wheel.setVisibility(View.VISIBLE);
        applyTheme();
        resizeWindow(dp(WHEEL_SIZE));
        resetAutoCollapse();
    }

    /** 重置「停手自动缩回」的计时。设为 0 表示一直展开。 */
    private void resetAutoCollapse() {
        handler.removeCallbacks(autoCollapseRunnable);
        if (!expanded) {
            return;
        }
        long delay = Prefs.autoCollapseMs(this);
        if (delay > 0) {
            handler.postDelayed(autoCollapseRunnable, delay);
        }
    }

    private void collapse() {
        handler.removeCallbacks(autoCollapseRunnable);
        handler.removeCallbacks(exitWarnRunnable);
        handler.removeCallbacks(exitRunnable);
        if (!expanded) {
            return;
        }
        expanded = false;
        wheel.setVisibility(View.GONE);
        btnCollapsed.setVisibility(View.VISIBLE);

        // 先看波轮此刻偏在屏幕哪半边，再缩到那一边的边缘
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int collapsed = dp(COLLAPSED_SIZE);
        int centerX = layoutParams.x + layoutParams.width / 2;
        int centerY = layoutParams.y + layoutParams.height / 2;

        layoutParams.width = collapsed;
        layoutParams.height = collapsed;
        layoutParams.x = dockedX(centerX < dm.widthPixels / 2, collapsed);
        layoutParams.y = clamp(centerY - collapsed / 2, 0, Math.max(0, dm.heightPixels - collapsed));
        updateLayout();
        rememberPosition();
    }

    /** 停靠到最近的左／右边缘。收起态的按钮功能不变，永远是设定的那个动作。 */
    private void dockToNearestEdge() {
        if (layoutParams == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int centerX = layoutParams.x + layoutParams.width / 2;
        layoutParams.x = dockedX(centerX < dm.widthPixels / 2, layoutParams.width);
        layoutParams.y = clamp(
                layoutParams.y, 0, Math.max(0, dm.heightPixels - layoutParams.height));
        updateLayout();
        rememberPosition();
    }

    private int dockedX(boolean left, int windowWidth) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        if (left) {
            return dp(EDGE_MARGIN);
        }
        return Math.max(0, dm.widthPixels - windowWidth - dp(EDGE_MARGIN));
    }

    private int initialY() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int size = dp(COLLAPSED_SIZE);
        int y = (int) (dm.heightPixels * Prefs.yRatio(this));
        return clamp(y, 0, Math.max(0, dm.heightPixels - size));
    }

    private void updateLayout() {
        if (windowManager == null || rootView == null || layoutParams == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(rootView, layoutParams);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 屏幕旋转后宽高互换，必须重新停靠，否则球会留在旧坐标跑到屏幕外。
     * Service 在运行期间会收到这个回调。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dockToNearestEdge();
    }

    /** 改窗口尺寸时保持视觉中心不动，并夹在屏幕内。 */
    private void resizeWindow(int size) {
        if (layoutParams == null) {
            return;
        }
        int centerX = layoutParams.x + layoutParams.width / 2;
        int centerY = layoutParams.y + layoutParams.height / 2;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        layoutParams.width = size;
        layoutParams.height = size;
        layoutParams.x = clamp(centerX - size / 2, 0, Math.max(0, dm.widthPixels - size));
        layoutParams.y = clamp(centerY - size / 2, 0, Math.max(0, dm.heightPixels - size));
        try {
            windowManager.updateViewLayout(rootView, layoutParams);
        } catch (Throwable ignored) {
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(MainActivity.EXTRA_SHOW_SETTINGS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------------
    // 视图构造
    // ---------------------------------------------------------------------

    /** 环上/中心的无底色图标键，图标颜色由 applyTheme() 着色。 */
    private ImageButton makeIconButton(int iconRes, int sizeDp) {
        ImageButton button = new ImageButton(this);
        int size = dp(sizeDp);
        button.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setBackground(null);
        int pad = dp(Math.max(6, sizeDp / 4));
        button.setPadding(pad, pad, pad, pad);
        return button;
    }

    /** 中心键：没有图标，只有底色（底色由 applyTheme 设置）。 */
    private ImageButton makeCenterButton(int sizeDp) {
        ImageButton button = new ImageButton(this);
        int size = dp(sizeDp);
        button.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setBackground(null);
        return button;
    }

    /** 带圆底的键：收起态小圆，以及波轮中心。 */
    private ImageButton makeDiscButton(int iconRes, int sizeDp) {
        ImageButton button = new ImageButton(this);
        int size = dp(sizeDp);
        button.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(Math.max(6, sizeDp / 4));
        button.setPadding(pad, pad, pad, pad);
        return button;
    }

    private void setDisc(ImageButton button, int color) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        circle.setStroke(dp(1), 0x66FFFFFF);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x66FFFFFF), circle, null));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    // ---------------------------------------------------------------------
    // 前台服务与通知
    // ---------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_channel_desc));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundCompat() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), piFlags);

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_next)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
