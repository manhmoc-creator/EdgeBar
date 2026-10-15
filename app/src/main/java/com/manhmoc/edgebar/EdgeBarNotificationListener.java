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
import android.os.Build;
import java.util.List;
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
        if (pkg == null) return;   // [P7] trước đây: || pkg.equals(getPackageName()) => thông báo Play My Playlist không bao giờ được nuốt
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
    private final java.util.HashMap<String, String> labelCache = new java.util.HashMap<>();   // [P5] không gọi PackageManager cho mỗi thông báo
    private String appLabel(String pkg) {
        String c = labelCache.get(pkg);
        if (c != null) return c;
        try {
            PackageManager pm = getPackageManager();
            String l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            labelCache.put(pkg, l);
            return l;
        } catch (Exception e) { return pkg; }
    }
        /* UX: dò action snooze trong notification — title khác nhau tuỳ ROM/app
       ("Snooze", "Snooze 1 hour", "Remind me", "Tạm hoãn", "Nhắc lại"). Không
       có semantic constant chính thức nên phải dò title. */
    private Notification.Action findSnoozeAction(Notification n) {
        if (n.actions == null) return null;
        for (Notification.Action a : n.actions) {
            if (a == null || a.actionIntent == null) continue;
            String t = a.title == null ? "" : a.title.toString().toLowerCase(java.util.Locale.ROOT);
            if (t.contains("snooze") || t.contains("tạm hoãn") || t.contains("remind") || t.contains("nhắc")) return a;
        }
        return null;
    }
    private void handleNotic(SharedPreferences prefs, StatusBarNotification sbn) {
        NoticEngine eng = NoticEngine.instance;
        if (eng == null || !prefs.getBoolean("notic_en", false) || prefs.getBoolean("notic_paused", false)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        if (("," + prefs.getString("notic_exclude_apps", "") + ",").contains("," + pkg + ",")) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        String cat = n.category;
        String tpl = ex != null ? ex.getString(Notification.EXTRA_TEMPLATE) : null;
        boolean call = Notification.CATEGORY_CALL.equals(cat) || Notification.CATEGORY_ALARM.equals(cat);
        boolean media = Notification.CATEGORY_TRANSPORT.equals(cat) || (tpl != null && tpl.contains("MediaStyle"))
            || (ex != null && ex.containsKey(Notification.EXTRA_MEDIA_SESSION));   // [P7] mọi trình phát có MediaSession, không chỉ Google
        if (pkg.equals(getPackageName()) && !media) return;                        // thông báo khác của chính app vẫn bỏ qua
        boolean msg = Notification.CATEGORY_MESSAGE.equals(cat) || (tpl != null && tpl.contains("MessagingStyle"));
        boolean timer = Notification.CATEGORY_REMINDER.equals(cat) || pkg.contains("deskclock") || pkg.contains(".clock");
        boolean incoming = Notification.CATEGORY_CALL.equals(cat)
    && (n.fullScreenIntent != null || (tpl != null && tpl.contains("CallStyle")));
if (incoming) {
    PowerManager pwr = (PowerManager) getSystemService(POWER_SERVICE);
    KeyguardManager kg = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
    // Không dùng máy (tắt màn / đang khoá) -> bỏ qua, để hệ thống tự mở app gọi điện toàn màn hình
    if (pwr == null || !pwr.isInteractive() || (kg != null && kg.isKeyguardLocked())) return;
    if (n.fullScreenIntent != null) return;
}
        // [P3] Cuộc gọi ĐANG diễn ra (Grab, Zalo...): thông báo bị cập nhật mỗi giây, trước đây mỗi lần bị coi là thông báo mới -> kêu liên tục. Bỏ qua hẳn.
        if (Notification.CATEGORY_CALL.equals(cat) && !incoming) return;
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
        if (it.largeIcon == null && ex != null && Build.VERSION.SDK_INT >= 28) {   // lấy avatar của NGƯỜI NHẮN CUỐI CÙNG
            try {
                android.os.Parcelable[] arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (arr != null && arr.length > 0) {
                    List<Notification.MessagingStyle.Message> ms =
                        Notification.MessagingStyle.Message.getMessagesFromBundleArray(arr);
                    if (ms != null && !ms.isEmpty()) {
                        android.app.Person ps = ms.get(ms.size() - 1).getSenderPerson();
                        if (ps != null && ps.getIcon() != null) it.largeIcon = ps.getIcon();
                    }
                }
            } catch (Exception ignored) {}
        }
        it.actions = n.actions;
        it.snooze = findSnoozeAction(n);
        it.reply = reply;
        it.media = media;
        it.call = incoming;
        it.person = msg || Notification.CATEGORY_CALL.equals(cat);
        it.msg = msg;
        it.ongoing = !clearable;   // [P3]       
if (media && ex != null) it.token = ex.getParcelable(Notification.EXTRA_MEDIA_SESSION);
        int mode = prefs.getInt("notic_sound_mode", 0);
        boolean sysSound = haveRank && rk.getImportance() >= NotificationManager.IMPORTANCE_DEFAULT;
        final boolean wake = mode == 2 && !media && clearable;                 // [P3] ongoing không bao giờ bật màn / kêu
        final boolean sound = mode >= 1 && !sysSound && !media && clearable;
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
