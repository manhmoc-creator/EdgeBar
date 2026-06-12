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
        // Tạo Notification siêu nhẹ để duy trì Foreground, tránh bị hệ thống dọn dẹp RAM
        if (Build.VERSION.SDK_INT >= 26) {
            String cid = "acc_home_core";
            NotificationChannel nc = new NotificationChannel(
                    cid, "Động cơ Trợ năng", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(nc);
            Notification n = new Notification.Builder(this, cid)
                    .setContentTitle("Động cơ Trợ năng đang chạy")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
            startForeground(88, n);
        }
    }
@Override
public int onStartCommand(Intent intent, int flags, int startId) {
    isRunning = true;
    // FIX BUG 0: Delay 300ms đảm bảo EdgeBarService.onServiceConnected()
    // đã chạy xong và accHomeReceiver đã được registerReceiver() trước khi
    // nhận broadcast ACC_HOME_DRAW.
    // Pixel 2XL opt: dùng MainLooper handler — zero thread overhead
    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
        sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW"));
    }, 300);
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
