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
        // [FIX] BẮT BUỘC gọi startForeground() vì service này được khởi động bằng
        // startForegroundService() (QsAccHomeTile, HomaccWatchdogReceiver) và Manifest
        // đã khai báo foregroundServiceType="specialUse". Không gọi trong 5s sẽ khiến
        // hệ thống crash TOÀN BỘ tiến trình app (kéo cả EdgeBarService/overlay theo).
        // Ngoài ra, nếu KHÔNG phải Foreground Service thật, Android sẽ tự dừng service
        // này sau một khoảng chạy nền (Background Execution Limits) — đây chính là
        // nguyên nhân Homacc "biến mất tự nhiên" dù không có thao tác gì.
// [FIX] Dùng CHUNG id 99 + channel "eb_lacck_status" với EdgeBarService —
// hệ thống chỉ giữ 1 notification duy nhất cho cùng (pkg, id), nên "Homacc" không
// còn hiện thành dòng riêng, chỉ còn "EB Lacck" như trước.
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
        // inexact = OS tự gộp cùng các báo thức khác của hệ thống → tiết kiệm pin,
        // sai số vài phút không ảnh hưởng vì mục đích chỉ là "tự phục hồi", không cần chính xác
                am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 60*1000, 60*1000, pi);
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
        // [FIX RACE] HomescreenService đọc AccessibleHomeService.isRunning để quyết
        // định có vẽ Homeb hay không. Broadcast SYNC_STATE bắn ra từ turnOnHomeb()
        // (ngay khi gọi stopService) có thể tới TRƯỚC khi onDestroy() này thực sự
        // chạy xong — lúc đó isRunning vẫn còn true, khiến Homeb không bao giờ
        // được vẽ lại. Bắn thêm 1 lần SYNC_STATE NGAY tại đây (đúng thời điểm
        // isRunning đã chắc chắn = false) để đảm bảo HomescreenService luôn tính
        // đúng, dù broadcast đầu có bị race hay không.
        sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        // Gửi thêm 1 lần trễ 200ms để vượt qua SYNC_THROTTLE_MS (150ms) của
        // HomescreenService — phòng trường hợp broadcast SYNC_STATE vừa bắn ở trên
        // trùng thời điểm quá gần với broadcast từ turnOnHomeb() nên bị throttle nuốt mất.
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() ->
            sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE")), 200);
        super.onDestroy();
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
