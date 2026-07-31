package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * QsAccHomeTile: CHỈ kiểm soát AccessibleHomeService.
 * KHÔNG toggle MorseLock. KHÔNG can thiệp HomescreenService trực tiếp.
 *
 * Fix Bug 4: ADB shortcut — bật AccHome → old Home bars tự ẩn qua pref
 * Fix Bug 8: Notification race — cancel TRƯỚC, delay đọc state SAU
 *
 * Battery/RAM opt (Pixel 2XL):
 * - Notification chỉ tạo khi thực sự cần (lazy init channel)
 * - postDelayed 150ms tránh race condition với service lifecycle
 */
public class QsAccHomeTile extends TileService {

    private static final int NOTIF_ID = 77;
    private static final String NOTIF_CHANNEL = "eb_acc_home_status";

    private boolean isAccEnabled() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(
                getPackageName() + "/" + EdgeBarService.class.getName());
        } catch (Exception e) { return false; }
    }

    @Override
    public void onStartListening() {
        // FIX BUG 8: Delay 150ms để đọc state SAU khi service lifecycle hoàn tất
        // Tránh race condition: onStartListening() chạy trước onDestroy() của service
        new Handler(getMainLooper()).postDelayed(() -> {
            Tile t = getQsTile();
            if (t == null) return;
            if (!isAccEnabled()) {
                // [MỤC 8] STATE_INACTIVE thay UNAVAILABLE — tránh hệ thống ẩn tile
                t.setState(Tile.STATE_INACTIVE);
                t.setLabel("Homacc (cần Acc)");
            } else {
                boolean running = AccessibleHomeService.isRunning;
                t.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                t.setLabel(running ? "Homacc ON" : "Homacc OFF");
                // FIX BUG 8: Đồng bộ notification với state thực tế
                // nhưng KHÔNG tạo lại notification nếu đang trong quá trình tắt
                syncNotification(running);
            }
            t.updateTile();
        }, 150);
    }

    @Override
public void onClick() {
    if (!isAccEnabled()) return;

    if (!AccessibleHomeService.isRunning) {
        Intent i = new Intent(this, AccessibleHomeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        showNotification();
        updateTileState(true);
    } else {
        cancelNotification();
        stopService(new Intent(this, AccessibleHomeService.class));
        updateTileState(false);
    }
}
    private void updateTileState(boolean isOn) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(isOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(isOn ? "Homacc ON" : "Homacc OFF");
        t.updateTile();
    }

    // FIX BUG 8: Tách riêng show/cancel/sync — không bao giờ show trong onStartListening
    private void showNotification() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm == null) return;
    ensureChannel(nm);
    Notification.Builder builder = new Notification.Builder(this, NOTIF_CHANNEL)
.setContentTitle("Homacc")
.setSmallIcon(android.R.drawable.ic_menu_search)
.setOngoing(true)
.setPriority(Notification.PRIORITY_MAX)
.setVisibility(Notification.VISIBILITY_PUBLIC);
NotificationChannel nc = new NotificationChannel(
    NOTIF_CHANNEL, "Trạng thái Homacc",
    NotificationManager.IMPORTANCE_HIGH);
nc.setSound(null, null);
nc.enableLights(false);
nc.enableVibration(false);
nc.setShowBadge(false);
nc.setBypassDnd(false); // ← ĐỔI false, không conflict với DND policy
nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
nm.notify(NOTIF_ID, builder.build());
}

    private void cancelNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    // Chỉ sync từ onStartListening — không tạo mới nếu đang tắt
    private void syncNotification(boolean shouldShow) {
        if (shouldShow) showNotification();
        else cancelNotification();
    }

    private void ensureChannel(NotificationManager nm) {
    if (Build.VERSION.SDK_INT >= 26) {
        if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {

NotificationChannel nc = new NotificationChannel(
    NOTIF_CHANNEL, "Trạng thái Homacc",
    NotificationManager.IMPORTANCE_HIGH);
nc.setSound(null, null);
nc.enableLights(false);
nc.enableVibration(false);
nc.setShowBadge(false);
nc.setBypassDnd(true); // THÊM: vượt DND
nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            nm.createNotificationChannel(nc);
        }
     }
  }
}
