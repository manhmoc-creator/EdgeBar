      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.LinkedHashSet;

/**
 * Watchdog tạm thu hồi Trợ năng khi app Blacklist mở ở màn khoá.
 * - Sống độc lập sau khi EdgeBarService bị tắt (FGS riêng).
 * - Poll UsageStats mỗi 500ms (chỉ trong lúc active — Zero cost khi không dùng).
 * - Tự nhả + tự stopSelf khi app Blacklist đóng hoặc quá timeout an toàn.
 */
public class BlacklistLockWatchdogService extends Service {

    private static final long POLL_INTERVAL_MS = 500;
    private static final long MAX_ACTIVE_MS = 5 * 60 * 1000; // 5 phút chốt an toàn
    private static final int NOTIF_ID = 97;
    private static final String CHANNEL_ID = "eb_bl_lock_watchdog";

    private Handler handler;
    private Runnable pollRunnable;
    private SharedPreferences prefs;
    private long startMs;
    private boolean restoreScheduled = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler != null) return START_NOT_STICKY; // đã chạy rồi

        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        startMs = prefs.getLong("blacklist_lock_start_ms", System.currentTimeMillis());

        startForegroundQuiet();

        handler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override public void run() {
                if (shouldRestoreNow()) {
                    restoreAccessibility();
                    stopSelf();
                    return;
                }
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        return START_NOT_STICKY;
    }

    private void startForegroundQuiet() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Blacklist Lock Watchdog", NotificationManager.IMPORTANCE_MIN);
        ch.setSound(null, null);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Đang tạm thu hồi Trợ năng")
            .setContentText("Sẽ tự khôi phục khi bạn thoát app")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET) // ẩn khỏi lockscreen
            .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private boolean shouldRestoreNow() {
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed > MAX_ACTIVE_MS) return true; // timeout safety

        String pkg = prefs.getString("blacklist_lock_pkg", "");
        if (pkg.isEmpty()) return true; // flag đã bị xoá bởi nơi khác

        String fg = getForegroundPkg();
        if (fg == null || fg.isEmpty()) return false; // không xác định được → chờ thêm

        // Vẫn đang trong app Blacklist → tiếp tục giữ trạng thái thu hồi
        if (fg.equals(pkg)) return false;

        // Bất kỳ package nào khác (kể cả lockscreen/launcher) → restore
        return true;
    }

    private String getForegroundPkg() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(now - 4000, now);
            UsageEvents.Event ev = new UsageEvents.Event();
            String fg = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(ev);
                if (ev.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    fg = ev.getPackageName();
                }
            }
            return fg;
        } catch (Exception e) { return null; }
    }

    private void restoreAccessibility() {
        if (restoreScheduled) return;
        restoreScheduled = true;

        try {
            String mySvc = getPackageName() + "/" + EdgeBarService.class.getName();
            String cur = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            if (!cur.contains(mySvc)) {
                String newVal = cur.isEmpty() ? mySvc : cur + ":" + mySvc;
                Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newVal);
                Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            }
        } catch (Exception ignored) {}

        prefs.edit()
            .putBoolean("blacklist_lock_active", false)
            .remove("blacklist_lock_pkg")
            .remove("blacklist_lock_start_ms")
            .apply();
    }

    @Override public void onDestroy() {
        if (handler != null && pollRunnable != null) handler.removeCallbacks(pollRunnable);
        // Nếu service bị OS kill trước khi kịp restore → cố gắng restore lần cuối
        if (!restoreScheduled) restoreAccessibility();
        super.onDestroy();
    }
}
