      package com.manhmoc.edgebar;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashSet;

/**
 * Watchdog Blacklist-tại-Lock (bản ổn định):
 *  - CHÍNH service này tắt Trợ năng SAU KHI đã lên FGS -> không bao giờ kẹt Trợ năng ở trạng thái tắt.
 *  - Theo dõi app Blacklist bằng UsageEvents TĂNG DẦN (mỗi lần chỉ query ~0.7s gần nhất, không quét lại 4s).
 *  - Chỉ khôi phục khi: (a) app đã rời đi 2 lần poll liên tiếp, (b) tắt màn hình và KHÔNG trong cuộc gọi,
 *    (c) quá timeout. Có thời gian giữ tối thiểu để không "nháy rồi khôi phục".
 *  - Khôi phục xong ghi mốc blacklist_lock_restore_ts để EdgeBarService KHÔNG "đá/kill" app.
 */
public class BlacklistLockWatchdogService extends Service {

    private static final String TAG = "EB_BLWD";
    private static final long POLL_INTERVAL_MS = 700;
    private static final long POLL_INTERVAL_SCREEN_OFF_MS = 3000;   // màn tắt: poll thưa để tiết kiệm pin
    private static final long MIN_HOLD_MS = 2500;                   // giữ tối thiểu, chống "nháy"
    private static final int  LEFT_CONFIRM_POLLS = 2;               // phải "đã rời app" 2 lần liên tiếp
    private static final long MAX_ACTIVE_MS = 10 * 60 * 1000L;
    private static final long MAX_ACTIVE_CALL_MS = 3 * 60 * 60 * 1000L; // đang gọi: cho phép rất lâu
    private static final long NO_USAGE_PERM_MAX_MS = 90 * 1000L;    // chưa cấp Usage Access: chỉ giữ 90s
    private static final int NOTIF_ID = 97;
    private static final String CHANNEL_ID = "eb_bl_lock_watchdog";

    private Handler handler;
    private SharedPreferences prefs;
    private PowerManager pm;
    private AudioManager audio;

    private String targetPkg = "";
    private long startMs;
    private long lastQueryMs;
    private long targetFgTs, targetBgTs, otherFgTs;
    private int leftStreak = 0;
    private long ignoreLeftUntilMs = 0;
    private boolean restoreDone = false;
    private boolean disabledByUs = false;
    private boolean receiverRegistered = false;
    public static volatile boolean isRunning = false;
private final java.util.Set<String> keepPkgs = new java.util.HashSet<>(); // mọi app thuộc "phiên" Blacklist (Blacklist + Dialer/Telecom)
private final java.util.Set<String> fgKeep = new java.util.HashSet<>();   // app trong keepPkgs đang ở foreground

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() { poll(); }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (handler == null || restoreDone) return;
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                // Màn vừa sáng lại: cho app kịp resume, tránh đọc nhầm trạng thái "background"
                ignoreLeftUntilMs = System.currentTimeMillis() + 1500;
                leftStreak = 0;
            }
            handler.removeCallbacks(pollRunnable);
            handler.post(pollRunnable);
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler != null) return START_NOT_STICKY; // đã chạy rồi

        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        startForegroundQuiet(); // BẮT BUỘC gọi trước mọi thứ khác

        targetPkg = prefs.getString("blacklist_lock_pkg", "");
        if (targetPkg.isEmpty() || !prefs.getBoolean("blacklist_lock_active", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startMs = prefs.getLong("blacklist_lock_start_ms", System.currentTimeMillis());
        targetFgTs = startMs;      // coi như app đang foreground từ lúc kích hoạt
        targetBgTs = 0;
        otherFgTs = 0;
        lastQueryMs = startMs - 3000;
        isRunning = true;
keepPkgs.clear(); fgKeep.clear();
keepPkgs.add(targetPkg);
fgKeep.add(targetPkg); // coi như đang foreground từ lúc kích hoạt
for (String p : prefs.getString("blacklist", "").split(",")) {
    String t = p.trim();
    if (!t.isEmpty()) keepPkgs.add(t);
}
try {
    android.telecom.TelecomManager tm = (android.telecom.TelecomManager) getSystemService(TELECOM_SERVICE);
    String d = tm != null ? tm.getDefaultDialerPackage() : null;
    if (d != null) keepPkgs.add(d);
} catch (Exception ignored) {}
keepPkgs.add("com.android.server.telecom");
keepPkgs.add("com.android.incallui");
keepPkgs.add("com.google.android.dialer");
keepPkgs.add("com.android.dialer");

        handler = new Handler(Looper.getMainLooper());

        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, f);
            receiverRegistered = true;
        } catch (Exception ignored) {}

        // FGS đã lên -> mới an toàn để thu hồi Trợ năng
        disableAccessibilityNow();
        Log.d(TAG, "START target=" + targetPkg);

        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        return START_NOT_STICKY;
    }

    // ==================== VÒNG KIỂM TRA ====================
    private void poll() {
        if (restoreDone) return;
        long now = System.currentTimeMillis();
        long elapsed = now - startMs;
        boolean interactive = pm != null && pm.isInteractive();
        boolean inCall = audio != null && audio.getMode() != AudioManager.MODE_NORMAL;

        if (elapsed > (inCall ? MAX_ACTIVE_CALL_MS : MAX_ACTIVE_MS)) { finishAndRestore("timeout"); return; }

        if (elapsed >= MIN_HOLD_MS) {
            if (!interactive) {
                // Tắt màn: nếu KHÔNG phải đang gọi/đổ chuông (áp tai làm tắt màn) -> trả Lock về ngay
                if (!inCall) { finishAndRestore("screen_off"); return; }
            } else if (hasUsageAccess()) {
    refreshUsageState(now);
    boolean left = now >= ignoreLeftUntilMs && hasLeftTarget();
    if (inCall) left = false; // đang đổ chuông / đang gọi: TUYỆT ĐỐI không trả Trợ năng
    leftStreak = left ? leftStreak + 1 : 0;
    Log.d(TAG, "poll inCall=" + inCall + " fgKeep=" + fgKeep + " left=" + left + " streak=" + leftStreak);
    if (leftStreak >= LEFT_CONFIRM_POLLS) { finishAndRestore("app_left"); return; }
} else if (elapsed > NO_USAGE_PERM_MAX_MS) {

                finishAndRestore("no_usage_permission");
                return;
            }
        }
        handler.postDelayed(pollRunnable, interactive ? POLL_INTERVAL_MS : POLL_INTERVAL_SCREEN_OFF_MS);
    }

    /** Chỉ đọc sự kiện MỚI kể từ lần poll trước — rẻ hơn hẳn việc quét lại cả 4 giây mỗi lần. */
    private void refreshUsageState(long now) {
    try {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = usm.queryEvents(lastQueryMs, now);
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            if (ev.getTimeStamp() <= startMs) continue;
            String p = ev.getPackageName();
            if (p == null) continue;
            int type = ev.getEventType();
            boolean keep = keepPkgs.contains(p);
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (keep) fgKeep.add(p);
                else if (!isIgnorablePkg(p)) fgKeep.clear(); // app lạ chiếm foreground -> đã rời phiên
            } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                if (keep) fgKeep.remove(p);
            }
        }
        lastQueryMs = now - 300;
    } catch (Exception ignored) {}
}

/** Đã rời khi KHÔNG còn app nào của phiên (Blacklist/Dialer/Tammi) ở foreground. */
private boolean hasLeftTarget() { return fgKeep.isEmpty(); }

private boolean isIgnorablePkg(String p) {
    return p.equals(getPackageName()) || p.equals("android")
        || p.contains("systemui") || p.contains("inputmethod");
}


    private boolean hasUsageAccess() {
        try {
            AppOpsManager aom = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            return aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    // ==================== TẮT / BẬT TRỢ NĂNG ====================
    private void disableAccessibilityNow() {
        try {
            String mySvc = getPackageName() + "/" + EdgeBarService.class.getName();
            String cur = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            if (cur.contains(mySvc)) {
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (String part : cur.split(":")) {
                    String t = part.trim();
                    if (!t.isEmpty() && !t.equals(mySvc)) set.add(t);
                }
                Settings.Secure.putString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", set));
            }
            disabledByUs = true;
        } catch (Exception e) {
            Log.w(TAG, "disable acc failed", e);
        }
        // Homacc gắn liền vòng đời Trợ năng: dừng luôn để đỡ tốn RAM/pin (onServiceConnected sẽ bật lại)
        try { stopService(new Intent(this, AccessibleHomeService.class)); } catch (Exception ignored) {}
    }

    private void finishAndRestore(String reason) {
        if (restoreDone) return;
        restoreDone = true;
        if (handler != null) handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "RESTORE reason=" + reason);

        // Ghi mốc TRƯỚC khi bật Trợ năng để EdgeBarService biết đây là lần khôi phục nội bộ -> không "đá" app
        prefs.edit()
            .putLong("blacklist_lock_restore_ts", System.currentTimeMillis())
            .putBoolean("blacklist_lock_active", false)
            .remove("blacklist_lock_pkg")
            .remove("blacklist_lock_start_ms")
            .apply();

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
        stopSelf();
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
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override public void onDestroy() {
        isRunning = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        // Bị OS kill giữa chừng mà ĐÃ tắt Trợ năng -> bắt buộc trả lại
        if (disabledByUs && !restoreDone) finishAndRestore("service_destroyed");
        super.onDestroy();
    }
}
