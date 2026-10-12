package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class EdgeBarNotificationListener extends NotificationListenerService {

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        try { handleBlacklist(prefs, sbn); } catch (Exception ignored) {}
        try { handleNotic(prefs, sbn); } catch (Exception ignored) {}
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        if (NoticEngine.consumeSelfCancel(sbn.getKey())) return;
        NoticEngine e = NoticEngine.instance;
        if (e != null) e.onRemoved(sbn.getKey());
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
        if (prefs.getBoolean("blacklist_lock_active", false)) {
            if (prefs.getBoolean("blacklist_lock_preempt", false))
                prefs.edit().putLong("bl_call_ts", System.currentTimeMillis()).apply();
        }
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

        Ranking rk = new Ranking();
        boolean haveRank = getCurrentRanking().getRanking(sbn.getKey(), rk);
        if (haveRank && rk.getImportance() <= NotificationManager.IMPORTANCE_MIN) return;
        if (prefs.getBoolean("notic_dnd_skip", true) && (eng.dndBlocks() || (haveRank && !rk.matchesInterruptionFilter()))) return;

        Bundle ex = n.extras;
        String cat = n.category;
        String tpl = ex != null ? ex.getString(Notification.EXTRA_TEMPLATE) : null;
        boolean call = Notification.CATEGORY_CALL.equals(cat) || Notification.CATEGORY_ALARM.equals(cat);
        boolean media = Notification.CATEGORY_TRANSPORT.equals(cat) || (tpl != null && tpl.contains("MediaStyle"));
        boolean msg = Notification.CATEGORY_MESSAGE.equals(cat) || (tpl != null && tpl.contains("MessagingStyle"));
        boolean timer = Notification.CATEGORY_REMINDER.equals(cat) || pkg.contains("deskclock") || pkg.contains(".clock");
        boolean exempt = call || media || msg || timer;
        boolean clearable = (n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR)) == 0;
        if (!clearable && !exempt) return;
        if (!eng.hasHost()) return;

        CharSequence ti = ex != null ? ex.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence tx = ex != null ? ex.getCharSequence(Notification.EXTRA_TEXT) : null;
        if (tx == null && ex != null) tx = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String title = ti != null && ti.length() > 0 ? ti.toString() : appLabel(pkg);
        String text = tx != null ? tx.toString() : "";

        NoticEngine.Item it = new NoticEngine.Item();
        it.key = sbn.getKey();
        it.pkg = pkg;
        it.title = title;
        it.text = text;
        it.sig = title + "|" + text;
        it.icon = eng.appIcon(pkg);
        it.intent = n.contentIntent;

        int mode = prefs.getInt("notic_sound_mode", 0);
        boolean sysSound = haveRank && rk.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT;
        boolean wake = mode == 2 && !media;
        boolean sound = mode >= 1 && !sysSound && !media;
        eng.post(it, wake, sound);

        if (prefs.getBoolean("notic_hide_system", false) && clearable && !call && !media) {
            NoticEngine.markSelfCancel(sbn.getKey());
            cancelNotification(sbn.getKey());
        }
    }
}
