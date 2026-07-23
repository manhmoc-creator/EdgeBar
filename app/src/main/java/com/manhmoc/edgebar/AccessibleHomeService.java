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
        // Nâng LOW thay vì MIN — giúp process không bị xếp vào cache-tier
        // thấp nhất khi RAM căng trên Pixel 2XL (4GB, dễ bị OOM sớm)
       if (Build.VERSION.SDK_INT >= 26) {
    try {
        String cid = "acc_home_core";
        NotificationChannel nc = new NotificationChannel(
                cid, "Động cơ Trợ năng", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Động cơ Trợ năng")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(88, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(88, n);
        }
    } catch (Exception e) {
        // Thiếu <property> FGS subtype hoặc bị OS chặn → dừng NGAY thay vì để
        // Exception phá process → tránh vòng lặp crash-restart tốn pin/CPU.
        isRunning = false;
        stopSelf();
        return;
    }
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
        // inexact = OS tự gộp cùng các báo thức khác của hệ thống → tiết kiệm pin,
        // sai số vài phút không ảnh hưởng vì mục đích chỉ là "tự phục hồi", không cần chính xác
        am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 5*60*1000, 5*60*1000, pi);
    }
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    isRunning = true;
    Handler h = new Handler(android.os.Looper.getMainLooper());
    // Bắn nhiều lần với delay tăng dần — cực rẻ (chỉ 1 broadcast, EdgeBarService
    // tự bỏ qua nếu đã vẽ rồi do check isHomaccDrawn ở đầu drawAccessibleHome())
    // nhưng đảm bảo bắt được thời điểm accHomeReceiver đã sẵn sàng dù process
    // vừa hồi sinh sau OOM-kill và hệ thống đang bận I/O.
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 300);
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 1500);
    h.postDelayed(() -> sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW")), 4000);
    return START_STICKY;
}
    @Override
    public void onDestroy() {
        isRunning = false;
        // Bắn tín hiệu sang EdgeBarService để gỡ toàn bộ View, trả lại RAM
        sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_REMOVE"));
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
