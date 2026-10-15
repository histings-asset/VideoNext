package com.videonext.floatcontrol;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置页：引导用户授予三项权限，并控制悬浮球开关。
 *
 *   1) 悬浮窗权限（必须）：否则无法显示悬浮球。
 *   2) 通知使用权（推荐）：用于读取媒体会话，精确控制播放中的 App。
 *   3) 使用情况访问（推荐）：用于判断前台是哪个 App，让前台视频优先于后台音乐受控。
 */
public class MainActivity extends Activity {

    /** 悬浮球的「设置」用它把本页拉起来；正常点图标启动不带这个标记。 */
    public static final String EXTRA_SHOW_SETTINGS = "show_settings";

    private TextView overlayStatus;
    private TextView listenerStatus;
    private TextView usageStatus;
    private Button toggleButton;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 正常启动（点应用图标）且已有悬浮窗权限：直接把悬浮球放出来并退出本页，
        // 用户要的就是那个球，不该先撞上一屏设置。
        // 从悬浮球的「设置」进来、或还没授权时，才显示这一页。
        boolean showSettings = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_SHOW_SETTINGS, false);
        if (!showSettings && Settings.canDrawOverlays(this)) {
            startFloatingService();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        overlayStatus = findViewById(R.id.overlay_status);
        listenerStatus = findViewById(R.id.listener_status);
        usageStatus = findViewById(R.id.usage_status);
        toggleButton = findViewById(R.id.toggle_button);
        Button overlayButton = findViewById(R.id.overlay_button);
        Button listenerButton = findViewById(R.id.listener_button);
        Button usageButton = findViewById(R.id.usage_button);

        overlayButton.setOnClickListener(v -> requestOverlayPermission());
        listenerButton.setOnClickListener(v -> openListenerSettings());
        usageButton.setOnClickListener(v -> openUsageAccessSettings());
        toggleButton.setOnClickListener(v -> toggleService());

        setUpSettings();

        requestNotificationPermissionIfNeeded();
    }

    private void setUpSettings() {
        setUpAutoCollapseSpinner();
        setUpCollapsedActionSpinner();
        setUpThemeSpinners();

        CheckBox haptics = findViewById(R.id.haptics_checkbox);
        haptics.setChecked(Prefs.hapticsEnabled(this));
        haptics.setOnCheckedChangeListener(
                (buttonView, isChecked) -> Prefs.setHapticsEnabled(this, isChecked));

        Button keepaliveButton = findViewById(R.id.keepalive_button);
        keepaliveButton.setOnClickListener(v -> openAppDetailsSettings());
    }

    /** 收起态单击小圆做什么。改完立刻对正在运行的悬浮球生效。 */
    private void setUpCollapsedActionSpinner() {
        Spinner spinner = findViewById(R.id.collapsed_action_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.collapsed_action_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Prefs.collapsedActionIndex(this));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setCollapsedAction(
                        MainActivity.this, Prefs.COLLAPSED_ACTIONS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** 悬浮球的底色与透明度。 */
    private void setUpThemeSpinners() {
        Spinner theme = findViewById(R.id.theme_spinner);
        ArrayAdapter<CharSequence> themeAdapter = ArrayAdapter.createFromResource(
                this, R.array.color_options, android.R.layout.simple_spinner_item);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        theme.setAdapter(themeAdapter);
        theme.setSelection(Prefs.colorIndex(this));
        theme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setColorIndex(MainActivity.this, position);
                // 示意图用当前配色绘制，颜色一变要重画
                findViewById(R.id.wheel_guide).invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner alpha = findViewById(R.id.alpha_spinner);
        ArrayAdapter<CharSequence> alphaAdapter = ArrayAdapter.createFromResource(
                this, R.array.alpha_options, android.R.layout.simple_spinner_item);
        alphaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        alpha.setAdapter(alphaAdapter);
        alpha.setSelection(Prefs.alphaIndex(this));
        alpha.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setAlphaPercent(MainActivity.this, Prefs.ALPHA_OPTIONS[position]);
                findViewById(R.id.wheel_guide).invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /** 跳到本应用的系统详情页，用户在那里能加自启动 / 电池优化白名单。 */
    private void openAppDetailsSettings() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** 波轮展开后停手多久自动缩回。改完立刻对正在运行的悬浮球生效。 */
    private void setUpAutoCollapseSpinner() {
        Spinner spinner = findViewById(R.id.auto_collapse_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.auto_collapse_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Prefs.autoCollapseIndex(this));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setAutoCollapseMs(
                        MainActivity.this, Prefs.AUTO_COLLAPSE_OPTIONS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        boolean overlayGranted = canDrawOverlays();
        overlayStatus.setText(overlayGranted ? R.string.status_granted : R.string.status_not_granted);
        overlayStatus.setTextColor(overlayGranted ? Color.parseColor("#2E7D32")
                : Color.parseColor("#C62828"));

        boolean listenerGranted = isNotificationAccessGranted();
        listenerStatus.setText(listenerGranted ? R.string.status_granted
                : R.string.status_not_granted);
        listenerStatus.setTextColor(listenerGranted ? Color.parseColor("#2E7D32")
                : Color.parseColor("#C62828"));

        boolean usageGranted = ForegroundApp.isGranted(this);
        usageStatus.setText(usageGranted ? R.string.status_granted
                : R.string.status_not_granted);
        usageStatus.setTextColor(usageGranted ? Color.parseColor("#2E7D32")
                : Color.parseColor("#C62828"));

        toggleButton.setText(FloatingService.isRunning ? R.string.btn_stop : R.string.btn_start);
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    private boolean isNotificationAccessGranted() {
        String flat = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void requestOverlayPermission() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Throwable t2) {
                Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openUsageAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Throwable t) {
            Toast.makeText(this, R.string.open_settings_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void toggleService() {
        if (FloatingService.isRunning) {
            stopService(new Intent(this, FloatingService.class));
        } else {
            if (!canDrawOverlays()) {
                Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show();
                requestOverlayPermission();
                return;
            }
            startFloatingService();
        }
        // 服务启停是异步的，稍后再刷新按钮文案。
        handler.postDelayed(this::refreshUi, 350);
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}
