# Keep the NotificationListenerService — it is instantiated by the system.
-keep class com.example.notifbypass.MyNotificationListener { *; }
-keep class com.example.notifbypass.KeepAliveService { *; }
-keep class com.example.notifbypass.BootReceiver { *; }
