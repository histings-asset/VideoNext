# 保持 NotificationListenerService 与前台服务不被混淆（本工程默认不开启混淆）。
-keep class com.videonext.floatcontrol.MediaNotificationListener { *; }
-keep class com.videonext.floatcontrol.FloatingService { *; }
