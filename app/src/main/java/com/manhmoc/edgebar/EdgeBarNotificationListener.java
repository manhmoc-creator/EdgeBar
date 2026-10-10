
      package com.manhmoc.edgebar;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Bắt cuộc gọi đến của app Blacklist (Viettel Tammi, Vcall...) ở màn khoá/tắt màn
 * và tắt Trợ năng TRƯỚC khi app kịp mở màn hình cuộc gọi.
 * Zero chi phí khi tính năng tắt: chỉ 1 lệnh đọc prefs rồi return.
 */
public class EdgeBarNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
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
            // [MỚI] Đang pre-emptive (Trợ năng đã tắt lúc tắt màn) -> chỉ đánh dấu "có cuộc gọi đến"
            // để Watchdog biết mà KHÔNG trả Trợ năng khi bật màn. Key "bl_call_ts" cố ý không bắt đầu
            // bằng "blacklist" nên không đánh thức prefListener của các service khác.
            if (prefs.getBoolean("blacklist_lock_active", false)) {
                if (prefs.getBoolean("blacklist_lock_preempt", false))
                    prefs.edit().putLong("bl_call_ts", System.currentTimeMillis()).apply();
                return;
            }
            // vừa khôi phục xong thì không revoke lại ngay (tránh thông báo cập nhật gây vòng lặp)
            if (System.currentTimeMillis() - prefs.getLong("blacklist_lock_restore_ts", 0) < 10000) return;

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean lockedOrOff = (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
            if (!lockedOrOff) return;
        } catch (Exception ignored) {}
    }
}
