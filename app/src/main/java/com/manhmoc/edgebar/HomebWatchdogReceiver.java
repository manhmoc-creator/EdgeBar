      package com.manhmoc.edgebar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Watchdog phục hồi Homeb khi Accessibility bị tắt (kể cả tắt qua ADB write secure setting —
 * ngữ cảnh "background" mà AccessibilityService.onDestroy() không được phép tự gọi
 * startForegroundService() trực tiếp). Kích hoạt qua AlarmManager — broadcast do Alarm kích
 * hoạt được hệ thống miễn trừ giới hạn khởi động FGS, y hệt HomaccWatchdogReceiver.
 */
public class HomebWatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SharedPreferences p = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        if (isAccEnabled(c)) return; // Accessibility đang bật -> không cần Homeb tự động

        // Accessibility TẮT -> Homacc không thể hoạt động -> dừng hẳn để tiết kiệm RAM/pin
        if (AccessibleHomeService.isRunning) {
            c.stopService(new Intent(c, AccessibleHomeService.class));
        }

        boolean oldHomeOn = p.getBoolean("shortcut_home_on", false);
        if (!oldHomeOn) {
            p.edit()
                .putBoolean("shortcut_home_on", true)
                .putBoolean("home_auto_by_acc_off", true)
                .apply();
        }

        if (!HomescreenService.isRunning) {
            Intent svc = new Intent(c, HomescreenService.class);
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
                else c.startService(svc);
            } catch (Exception ignored) {
                // Hiếm khi vẫn bị từ chối -> watchdog định kỳ 5 phút bên dưới sẽ tự thử lại
            }
        }
    }

    private boolean isAccEnabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/" + EdgeBarService.class.getName());
    }

    /** Kiểm tra gần như tức thì (300ms) — gọi ngay khi vừa phát hiện Accessibility tắt. */
    public static void scheduleImmediate(Context c) {
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, HomebWatchdogReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            c, 502, i,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        // [FIX] setExactAndAllowWhileIdle() cần quyền SCHEDULE_EXACT_ALARM trên Android 13+.
        // App chưa xin quyền này -> lệnh có thể ném SecurityException và bị catch() nuốt im
        // lặng, khiến Homeb KHÔNG BAO GIỜ được kích hoạt tức thời (chỉ tự phục hồi sau 5 phút
        // qua watchdog định kỳ). Đổi sang setAndAllowWhileIdle() — KHÔNG cần quyền đặc biệt,
        // vẫn chạy được khi máy đang Doze, và tiết kiệm pin hơn vì hệ thống được phép dồn
        // chung với báo thức khác thay vì phải đánh thức CPU đúng mili giây (setExact).
        try {
            am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 150, pi);
        } catch (Exception ignored) {}
    }
    /** Watchdog định kỳ dự phòng (5 phút/lần) — OS tự gộp batch, gần như không tốn pin thêm. */
    public static void scheduleRepeating(Context c) {
        android.app.AlarmManager am = (android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, HomebWatchdogReceiver.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
            c, 503, i,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 5*60*1000, 5*60*1000, pi);
    }
}
