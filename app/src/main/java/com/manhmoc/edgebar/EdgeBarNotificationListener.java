package com.manhmoc.edgebar;
import android.app.KeyguardManager;
import android.os.PowerManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class EdgeBarNotificationListener extends NotificationListenerService {
    private final Handler main = new Handler(Looper.getMainLooper());

    private static volatile EdgeBarNotificationListener inst;
    @Override public void onListenerConnected() { inst = this; }
    @Override public void onListenerDisconnected() { if (inst == this) inst = null; }

    /** Xoá thông báo thật khỏi bảng thông báo hệ thống (Notic gọi khi người dùng vuốt xoá). true = đã gửi lệnh xoá. */
    static boolean cancel(String key) {
        EdgeBarNotificationListener l = inst;
        if (l == null || key == null) return false;
        try { l.cancelNotification(key); return true; } catch (Exception e) { return false; }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        try { handleBlacklist(prefs, sbn); } catch (Exception ignored) {}
        try { handleNotic(prefs, sbn); } catch (Exception ignored) {}
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        final String key = sbn.getKey();
        if (NoticEngine.consumeSelfCancel(key)) return;
        main.post(() -> {
            NoticEngine e = NoticEngine.instance;
            if (e != null) e.onRemoved(key);
        });
    }

    private void handleBlacklist(SharedPreferences prefs, StatusBarNotification sbn) {
        if (!prefs.getBoolean("blacklist_lock_revoke_acc_en", false)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        String bl = prefs.getString("blacklist", "");
        if (bl.isEmpty() || !("," + bl + ",").contains("," + pkg + ",")) return;
        Notification n = sbn.getNotification();
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean callLike = n.fullScreenIntent != null
            || (Notification.CATEGORY_CALL.equals(n.category) && !ongoing);
        if (!callLike) return;
        if (prefs.getBoolean("blacklist_lock_active", false)
                && prefs.getBoolean("blacklist_lock_preempt", false))
            prefs.edit().putLong("bl_call_ts", System.currentTimeMillis()).apply();
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    private void handleNotic(SharedPreferences prefs, StatusBarNotification sbn) {
        NoticEngine eng = NoticEngine.instance;
        if (eng == null || !prefs.getBoolean("notic_en", false) || prefs.getBoolean("notic_paused", false)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        if (("," + prefs.getString("notic_exclude_apps", "") + ",").contains("," + pkg + ",")) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        String cat = n.category;
        String tpl = ex != null ? ex.getString(Notification.EXTRA_TEMPLATE) : null;
        boolean call = Notification.CATEGORY_CALL.equals(cat) || Notification.CATEGORY_ALARM.equals(cat);
        boolean media = Notification.CATEGORY_TRANSPORT.equals(cat) || (tpl != null && tpl.contains("MediaStyle"));
        boolean msg = Notification.CATEGORY_MESSAGE.equals(cat) || (tpl != null && tpl.contains("MessagingStyle"));
        boolean timer = Notification.CATEGORY_REMINDER.equals(cat) || pkg.contains("deskclock") || pkg.contains(".clock");
        boolean incoming = Notification.CATEGORY_CALL.equals(cat)
    && (n.fullScreenIntent != null || (tpl != null && tpl.contains("CallStyle")));
if (incoming) {
    PowerManager pwr = (PowerManager) getSystemService(POWER_SERVICE);
    KeyguardManager kg = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    // Không dùng máy (tắt màn / đang khoá) -> bỏ qua, để hệ thống tự mở app gọi điện toàn màn hình
    if (pwr == null || !pwr.isInteractive() || (kg != null && kg.isKeyguardLocked())) return;
}
        Ranking rk = new Ranking();
        boolean haveRank = getCurrentRanking().getRanking(sbn.getKey(), rk);
        if (haveRank && rk.getImportance() <= NotificationManager.IMPORTANCE_MIN && !media) return;
        if (prefs.getBoolean("notic_dnd_skip", true) && (eng.dndBlocks() || (haveRank && !rk.matchesInterruptionFilter()))) return;

        boolean exempt = call || media || msg || timer;
        boolean clearable = (n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR)) == 0;
        if (!clearable && !exempt) return;

        CharSequence ti = ex != null ? ex.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence tx = ex != null ? ex.getCharSequence(Notification.EXTRA_TEXT) : null;
        if (tx == null && ex != null) tx = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String label = appLabel(pkg);
        String title = ti != null && ti.length() > 0 ? ti.toString() : label;
        String text = tx != null ? tx.toString() : "";

        Notification.Action reply = null;
        if (n.actions != null) {
            for (Notification.Action a : n.actions) {
                if (a != null && a.actionIntent != null && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) { reply = a; break; }
            }
        }

        final NoticEngine.Item it = new NoticEngine.Item();
        it.key = sbn.getKey();
        it.pkg = pkg;
        it.appName = label;
        it.title = title;
        it.text = text;
        it.sig = title + "|" + text;
        it.intent = n.contentIntent;
        it.largeIcon = n.getLargeIcon();
        it.actions = n.actions;
        it.reply = reply;
        it.media = media;
        it.call = incoming;
if (media && ex != null) it.token = ex.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        int mode = prefs.getInt("notic_sound_mode", 0);
        boolean sysSound = haveRank && rk.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT;
        final boolean wake = mode == 2 && !media;
        final boolean sound = mode >= 1 && !sysSound && !media;
        final boolean hide = prefs.getBoolean("notic_hide_system", false) && clearable && !call && !media;
        final String key = sbn.getKey();

        main.post(() -> {
            NoticEngine e = NoticEngine.instance;
            if (e == null) return;
            boolean ok = e.post(it, wake, sound);
            if (ok && hide) {
                NoticEngine.markSelfCancel(key);
                cancelNotification(key);
            }
        });
    }
}
