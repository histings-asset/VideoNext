package com.videonext.floatcontrol;

import android.service.notification.NotificationListenerService;

/**
 * 一个「空」的通知监听服务。
 *
 * 它的存在本身就是目的：只有当本应用拥有被用户授予的 NotificationListenerService，
 * MediaSessionManager.getActiveSessions(ComponentName) 才被允许调用。
 * 有了活跃媒体会话列表，我们就能精确控制当前正在播放的那个 App。
 */
public class MediaNotificationListener extends NotificationListenerService {
    // 无需任何回调实现。
}
