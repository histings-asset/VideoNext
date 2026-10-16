package com.videonext.floatcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 媒体控制工具类。
 *
 * 控制策略（两种方式自动择优）：
 *   1) 首选 MediaSessionManager 拿到目标 MediaController，直接向具体的播放 App
 *      发送 skipToNext / skipToPrevious / play / pause。精准、可靠。
 *   2) 若拿不到会话（用户未授予通知使用权，或目标 App 未暴露媒体会话），
 *      则退化为向系统派发媒体按键事件（KEYCODE_MEDIA_NEXT 等）。
 *
 * 目标怎么选（关键）：前台应用优先。
 * 后台音乐长期处于 PLAYING，而抖音这类短视频应用的会话状态恒为 STOPPED，
 * 且系统会把媒体按键路由给音乐而不是抖音。所以「挑一个正在播放的会话」必然选错，
 * 只有先判断前台是哪个 App 再直取它的会话，才能让前台视频优先受控。
 */
public final class MediaControls {

    private MediaControls() {
    }

    /**
     * 状态不可信时（如抖音恒报 STOPPED）用它自维护一个播放/暂停开关，
     * 否则按钮只能单向「播放」。
     */
    private static String togglePackage = null;
    private static boolean toggleAssumedPlaying = false;

    /**
     * 本次操作应当发往的全部会话，按「最近更新」排序，靠前的更可能是活的那个。
     *
     * 返回多个而不是一个，是因为抖音这类应用会不断新建 MediaSession 而旧的仍残留在
     * 系统里（实测同时存在 /151 与 /155 两个同包会话，其中 /151 已死）。只挑一个等于
     * 赌它是不是活的，赌错就毫无反应；全部发一遍，死的会忽略，活的会响应。
     */
    private static List<MediaController> targets(Context context) {
        List<MediaController> result = new ArrayList<>();
        try {
            MediaSessionManager manager =
                    (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return result;
            }
            ComponentName listener =
                    new ComponentName(context, MediaNotificationListener.class);
            List<MediaController> sessions = manager.getActiveSessions(listener);
            if (sessions == null || sessions.isEmpty()) {
                return result;
            }

            // 1) 前台应用优先。这里刻意不看 state —— 抖音等应用的会话恒为 STOPPED，
            //    任何按状态筛选的写法都会把它排除掉。
            String foreground = ForegroundApp.current(context);
            if (foreground != null) {
                for (MediaController controller : sessions) {
                    if (foreground.equals(controller.getPackageName())) {
                        result.add(controller);
                    }
                }
            }

            if (result.isEmpty()) {
                // 2) 前台应用没有会话（桌面、未授予使用情况访问、或该应用不暴露会话）。
                MediaController best = mostActive(sessions);
                if (best != null) {
                    result.add(best);
                }
            } else {
                Collections.sort(result, (a, b) -> Long.compare(lastUpdate(b), lastUpdate(a)));
            }
        } catch (SecurityException e) {
            // 未授予「通知使用权」时会走到这里。
        } catch (Throwable t) {
            // 忽略，按空结果处理
        }
        return result;
    }

    private static long lastUpdate(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return (state == null) ? 0L : state.getLastPositionUpdateTime();
    }

    /** 主要目标会话（最近的第一个），仅用于读取状态；拿不到则返回 null。 */
    public static MediaController getActiveController(Context context) {
        List<MediaController> all = targets(context);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 旧策略：优先正在播放的会话，其次最近更新过状态的。 */
    private static MediaController mostActive(List<MediaController> sessions) {
        MediaController best = null;
        long bestTime = Long.MIN_VALUE;
        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return controller;
            }
            long t = (state == null) ? 0L : state.getLastPositionUpdateTime();
            if (t >= bestTime) {
                bestTime = t;
                best = controller;
            }
        }
        return best;
    }

    /**
     * 播放状态三态：{@code TRUE} / {@code FALSE} / {@code null}（无法判断）。
     *
     * 前台应用的会话若报告 STOPPED，说明它根本不维护播放状态（抖音等），
     * 此时返回 null，让调用方保持原图标，而不是显示一个假状态。
     */
    public static Boolean isPlayingOrNull(Context context) {
        MediaController controller = getActiveController(context);
        if (controller == null) {
            return Boolean.FALSE;
        }
        PlaybackState state = controller.getPlaybackState();
        if (state == null) {
            return null;
        }
        if (state.getState() == PlaybackState.STATE_STOPPED && isForeground(context, controller)) {
            return null;
        }
        return state.getState() == PlaybackState.STATE_PLAYING;
    }

    private static boolean isForeground(Context context, MediaController controller) {
        String foreground = ForegroundApp.current(context);
        return foreground != null && foreground.equals(controller.getPackageName());
    }

    /**
     * 手势模式是否对当前前台应用生效。
     *
     * 刻意要求「用户把该 App 明确加进列表」——盲滑很危险，
     * 在微信、桌面这类地方划一下可能触发意料之外的操作。
     */
    private static boolean useGestures(Context context) {
        if (!Prefs.gestureMode(context) || !GestureAccessibilityService.isReady()) {
            return false;
        }
        String foreground = ForegroundApp.current(context);
        return foreground != null && Prefs.gesturePackages(context).contains(foreground);
    }

    public static void next(Context context) {
        if (useGestures(context) && GestureAccessibilityService.swipe(context, true)) {
            return;
        }
        if (dispatch(context, PlaybackState.ACTION_SKIP_TO_NEXT)) {
            return;
        }
        sendKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static void previous(Context context) {
        if (useGestures(context) && GestureAccessibilityService.swipe(context, false)) {
            return;
        }
        if (dispatch(context, PlaybackState.ACTION_SKIP_TO_PREVIOUS)) {
            return;
        }
        sendKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static void playPause(Context context) {
        if (useGestures(context) && GestureAccessibilityService.tapCenter(context)) {
            return;
        }
        List<MediaController> all = targets(context);
        if (all.isEmpty()) {
            sendKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return;
        }
        Boolean playing = isPlayingOrNull(context);
        long action;
        if (playing != null) {
            action = playing ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        } else {
            // 状态不可信：不能猜当前是播还是停，用自维护的开关决定发哪个，
            // 否则按钮只能单向「播放」。
            action = assumedPlaying(all.get(0))
                    ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY;
        }
        if (dispatch(context, action)) {
            if (playing == null) {
                toggleAssumedPlaying = (action == PlaybackState.ACTION_PLAY);
            }
            return;
        }
        sendKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    /** 把动作依次发给所有目标会话；任意一个发出成功即返回 true。 */
    private static boolean dispatch(Context context, long action) {
        boolean sent = false;
        for (MediaController controller : targets(context)) {
            if (!supports(controller, action)) {
                continue;
            }
            try {
                MediaController.TransportControls controls = controller.getTransportControls();
                if (action == PlaybackState.ACTION_SKIP_TO_NEXT) {
                    controls.skipToNext();
                } else if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) {
                    controls.skipToPrevious();
                } else if (action == PlaybackState.ACTION_PAUSE) {
                    controls.pause();
                } else if (action == PlaybackState.ACTION_PLAY) {
                    controls.play();
                } else {
                    continue;
                }
                sent = true;
            } catch (Throwable ignored) {
                // 个别会话已失效，继续尝试其余会话
            }
        }
        return sent;
    }

    /** 换了一个 App 就重置假设：前台视频通常正在播放，首次按下应当是「暂停」。 */
    private static boolean assumedPlaying(MediaController controller) {
        String pkg = controller.getPackageName();
        if (!pkg.equals(togglePackage)) {
            togglePackage = pkg;
            toggleAssumedPlaying = true;
        }
        return toggleAssumedPlaying;
    }

    /**
     * 目标会话是否可能处理这个动作。
     *
     * 会话**没上报状态**时（红果免费漫剧这类应用，state 恒为 null、能力位也就无从判断）
     * 一律放行、直接试着发：要是不支持，对方自己会忽略；而拦下来一定什么都不发生。
     * 只有拿到状态、且明确不支持时，才拦下并让调用方走媒体按键兜底。
     */
    private static boolean supports(MediaController controller, long action) {
        PlaybackState state = controller.getPlaybackState();
        return state == null || (state.getActions() & action) != 0;
    }

    private static void sendKey(Context context, int keyCode) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        try {
            audio.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(
                    new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Throwable ignored) {
        }
    }
}
