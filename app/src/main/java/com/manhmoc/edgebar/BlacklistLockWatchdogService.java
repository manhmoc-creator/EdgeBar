      package com.manhmoc.edgebar;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
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
 * Watchdog Blacklist-tại-Lock (bản chủ động):
 *  - begin(): gọi từ EdgeBarService / NotificationListener / cử chỉ -> bật FGS rồi TẮT Trợ năng NGAY.
 *  - pkgAlreadyForeground=false (cuộc gọi đến, cử chỉ mở app): app CHƯA hiện -> chỉ coi là "đã rời"
 *    khi app từng hiện rồi biến mất, hoặc quá APP_APPEAR_TIMEOUT_MS mà không hiện.
 *  - Khôi phục Trợ năng khi: app đã rời (2 lần poll liên tiếp) / tắt màn hình mà không trong cuộc gọi / timeout.
 */
public class BlacklistLockWatchdogService extends Service {

    private static final String TAG = "EB_BLWD";
    private static final long POLL_INTERVAL_MS = 700;
    private static final long POLL_INTERVAL_SCREEN_OFF_MS = 3000;
    private static final long MIN_HOLD_MS = 2500;
    private static final int  LEFT_CONFIRM_POLLS = 2;
    private static final long MAX_ACTIVE_MS = 10 * 60 * 1000L;
    private static final long MAX_ACTIVE_CALL_MS = 3 * 60 * 60 * 1000L;
    private static final long NO_USAGE_PERM_MAX_MS = 90 * 1000L;
    private static final long APP_APPEAR_TIMEOUT_MS = 20 * 1000L; // chờ app hiện (đổ chuông / chờ mở khoá)
    private static final int NOTIF_ID = 97;
    private static final String CHANNEL_ID = "eb_bl_lock_watchdog";

    private Handler handler;
    private SharedPreferences prefs;
    private PowerManager pm;
    private AudioManager audio;

    private String targetPkg = "";
    private long startMs;
    private long lastQueryMs;
    private boolean seenFgAtStart = true;
    private boolean everSawKeepFg = false;
    private int leftStreak = 0;
    private long ignoreLeftUntilMs = 0;
    private boolean restoreDone = false;
    private boolean disabledByUs = false;
    private boolean receiverRegistered = false;
    public static volatile boolean isRunning = false;
    private final java.util.Set<String> keepPkgs = new java.util.HashSet<>();
    private final java.util.Set<String> fgKeep = new java.util.HashSet<>();

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() { poll(); }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (handler == null || restoreDone) return;
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                ignoreLeftUntilMs = System.currentTimeMillis() + 1500;
                leftStreak = 0;
            }
            handler.removeCallbacks(pollRunnable);
            handler.post(pollRunnable);
        }
    };

    // ==================== API DÙNG CHUNG ====================
    /** Bắt đầu phiên revoke. Trả về false nếu đang có phiên khác hoặc không khởi động được FGS. */
    public static boolean begin(Context c, String pkg, boolean pkgAlreadyForeground) {
        if (pkg == null || pkg.isEmpty()) return false;
        SharedPreferences p = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (p.getBoolean("blacklist_lock_active", false)
                && (isRunning || now - p.getLong("blacklist_lock_start_ms", 0) < 5000)) return false;

        p.edit().putBoolean("blacklist_lock_active", true)
            .putString("blacklist_lock_pkg", pkg)
            .putLong("blacklist_lock_start_ms", now)
            .putBoolean("blacklist_lock_seen_fg", pkgAlreadyForeground)
            .apply();
        try {
            Intent wd = new Intent(c, BlacklistLockWatchdogService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(wd); else c.startService(wd);
        } catch (Exception e) {
            p.edit().putBoolean("blacklist_lock_active", false)
                .remove("blacklist_lock_pkg").remove("blacklist_lock_start_ms")
                .remove("blacklist_lock_seen_fg").apply();
            return false;
        }

                // [FIX] LUÔN bật LockEb, KHÔNG còn kiểm tra isKeyguardLocked() — app gọi điện
        // Blacklist hay tự dismiss màn khoá ngay khi chuông reo, nên đúng lúc gọi tới đây
        // khoá có thể đã mất dù bản chất vẫn là phiên "Blacklist-tại-khoá". Bỏ điều kiện
        // để LockEb không bao giờ bị bỏ sót -> không còn khoảng trống "0 overlay".
        try {
            Intent lockEb = new Intent(c, LockEbService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(lockEb); else c.startService(lockEb);
        } catch (Exception ignored) {}

        // [FIX] Rút 500ms -> 150ms: LockEb chạy process riêng (:lockeb), khởi động rất
        // nhanh, không cần chờ lâu. Giữ Trợ năng sống thêm 500ms chính là khoảng thời gian
        // 2 tầng cảm ứng (EdgeBarService + LockEb) có thể tranh chấp, gây giật/kill cuộc gọi.
        new Handler(Looper.getMainLooper()).postDelayed(() -> revokeAccessibilityNow(c), 150);
        return true;

    }

    /** Gỡ EdgeBarService khỏi danh sách Trợ năng. Idempotent. */
    public static void revokeAccessibilityNow(Context c) {
        try {
            String mySvc = c.getPackageName() + "/" + EdgeBarService.class.getName();
            String cur = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            if (cur.contains(mySvc)) {
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (String part : cur.split(":")) {
                    String t = part.trim();
                    if (!t.isEmpty() && !t.equals(mySvc)) set.add(t);
                }
                Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", set));
                // Không còn dịch vụ nào -> báo tắt hẳn để app "ghét trợ năng" không đọc nhầm cờ cũ
                if (set.isEmpty()) Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, "0");
            }
        } catch (Exception e) { Log.w(TAG, "revoke acc failed", e); }
        try { c.stopService(new Intent(c, AccessibleHomeService.class)); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handler != null) return START_NOT_STICKY;

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
        seenFgAtStart = prefs.getBoolean("blacklist_lock_seen_fg", true);
        everSawKeepFg = seenFgAtStart;
        lastQueryMs = startMs - 3000;
        isRunning = true;

        keepPkgs.clear(); fgKeep.clear();
        keepPkgs.add(targetPkg);
        if (seenFgAtStart) fgKeep.add(targetPkg); // reactive: app đang ở foreground thật
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

        revokeAccessibilityNow(this); // idempotent (begin() thường đã tắt rồi)
        disabledByUs = true;
        Log.d(TAG, "START target=" + targetPkg + " seenFg=" + seenFgAtStart);

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
                if (!inCall) { finishAndRestore("screen_off"); return; }
            } else if (hasUsageAccess()) {
                refreshUsageState(now);
                boolean left = now >= ignoreLeftUntilMs && hasLeftTarget(now);
                if (inCall) left = false; // đang đổ chuông/đang gọi: tuyệt đối không trả Trợ năng
                leftStreak = left ? leftStreak + 1 : 0;
                Log.d(TAG, "poll inCall=" + inCall + " fgKeep=" + fgKeep + " seen=" + everSawKeepFg + " streak=" + leftStreak);
                if (leftStreak >= LEFT_CONFIRM_POLLS) { finishAndRestore("app_left"); return; }
            } else if (elapsed > NO_USAGE_PERM_MAX_MS) {
                finishAndRestore("no_usage_permission");
                return;
            }
        }
        handler.postDelayed(pollRunnable, interactive ? POLL_INTERVAL_MS : POLL_INTERVAL_SCREEN_OFF_MS);
    }

    private void refreshUsageState(long now) {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            UsageEvents events = usm.queryEvents(lastQueryMs, now);
            UsageEvents.Event ev = new UsageEvents.Event();
            // app chưa hiện lúc bắt đầu: nhận cả sự kiện foreground xảy ra ngay TRƯỚC startMs (chạy đua)
            long floor = startMs - (seenFgAtStart ? 0 : 2500);
            while (events.hasNextEvent()) {
                events.getNextEvent(ev);
                if (ev.getTimeStamp() <= floor) continue;
                String p = ev.getPackageName();
                if (p == null) continue;
                int type = ev.getEventType();
                boolean keep = keepPkgs.contains(p);
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (keep) { fgKeep.add(p); everSawKeepFg = true; }
                    else if (!isIgnorablePkg(p)) fgKeep.clear();
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (keep) fgKeep.remove(p);
                }
            }
            lastQueryMs = now - 300;
        } catch (Exception ignored) {}
    }

    /** Đã rời khi: từng hiện rồi không còn app nào của phiên ở foreground; hoặc quá hạn mà app không hề hiện. */
    private boolean hasLeftTarget(long now) {
        if (everSawKeepFg) return fgKeep.isEmpty();
        return fgKeep.isEmpty() && (now - startMs) > APP_APPEAR_TIMEOUT_MS;
    }

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

    private void finishAndRestore(String reason) {
        if (restoreDone) return;
        restoreDone = true;
        if (handler != null) handler.removeCallbacks(pollRunnable);
        Log.d(TAG, "RESTORE reason=" + reason);

                // [MỚI] Gỡ LockEb TRƯỚC khi bật lại Trợ năng -> không bao giờ có 2 bộ Lock bar
        // chồng nhau, và EdgeBarService.onServiceConnected() sẽ tự vẽ lại Lock sạch sẽ.
        // Gửi broadcast để LockEbService tự dọn dẹp View và thoát êm, tránh rò rỉ bộ nhớ.
        sendBroadcast(new Intent("com.manhmoc.edgebar.STOP_LOCK_EB"));
        try { stopService(new Intent(this, LockEbService.class)); } catch (Exception ignored) {}

        prefs.edit()

            .putLong("blacklist_lock_restore_ts", System.currentTimeMillis())
            .putBoolean("blacklist_lock_active", false)
            .remove("blacklist_lock_pkg")
            .remove("blacklist_lock_start_ms")
            .remove("blacklist_lock_seen_fg")
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
        if (disabledByUs && !restoreDone) finishAndRestore("service_destroyed");
        super.onDestroy();
    }
}
