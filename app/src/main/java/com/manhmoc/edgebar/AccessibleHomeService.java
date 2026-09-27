       package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
public class AccessibleHomeService extends Service {
    public static boolean isRunning = false;
    @Override
    public void onCreate() {
        super.onCreate();
String cid = "eb_lacck_status";
NotificationManager nmAcc = getSystemService(NotificationManager.class);
if (nmAcc.getNotificationChannel(cid) == null) {
    NotificationChannel c = new NotificationChannel(cid, "EB Lacck Status", NotificationManager.IMPORTANCE_LOW);
    c.setShowBadge(false);
    nmAcc.createNotificationChannel(c);
}
Notification n = new Notification.Builder(this, cid)
        .setContentTitle("EB Lacck")
        .setSmallIcon(android.R.drawable.stat_notify_voicemail)
        .setOngoing(true)
        .build();
if (Build.VERSION.SDK_INT >= 34) {
    startForeground(99, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
} else {
    startForeground(99, n);
}
scheduleWatchdog();
    }
    private void scheduleWatchdog() {
        android.app.AlarmManager am =
            (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, HomaccWatchdogReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            this, 501, i,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME,
    android.os.SystemClock.elapsedRealtime() + 15*60*1000, 15*60*1000, pi);
    }
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    isRunning = true;
    Handler h = new Handler(android.os.Looper.getMainLooper());
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 300);
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 1500);
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 4000);
    return START_STICKY;
}
    @Override
    public void onDestroy() {
        isRunning = false;
        sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_REMOVE"));
        sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
            sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE")), 200);
        super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
