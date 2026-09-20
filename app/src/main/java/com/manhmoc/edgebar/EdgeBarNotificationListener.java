
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
            if (prefs.getBoolean("blacklist_lock_active", false)) return;
            // vừa khôi phục xong thì không revoke lại ngay (tránh thông báo cập nhật gây vòng lặp)
            if (System.currentTimeMillis() - prefs.getLong("blacklist_lock_restore_ts", 0) < 10000) return;

            Notification n = sbn.getNotification();
            boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
            boolean callLike = n.fullScreenIntent != null
                || (Notification.CATEGORY_CALL.equals(n.category) && !ongoing);
            if (!callLike) return;

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean lockedOrOff = (km != null && km.isKeyguardLocked()) || (pm != null && !pm.isInteractive());
            if (!lockedOrOff) return;

// CODE MỚI:
// [FIX] Bỏ PAUSE_WM_OPS ở đây — nó ẩn sạch Lock bar/corner cũ TRƯỚC khi LockEbService
// (bên trong begin()) kịp vẽ overlay thay thế, tạo khoảng trống "0 overlay" khiến app
// Blacklist tưởng bị lộ và tự kill cuộc gọi. Lock bar cũ cứ hiển thị bình thường cho
// tới khi Trợ năng thực sự bị thu hồi (EdgeBarService.onDestroy tự dọn nó).
BlacklistLockWatchdogService.begin(this, pkg, false); // false = app CHƯA lên foreground

        } catch (Exception ignored) {}
    }
}
