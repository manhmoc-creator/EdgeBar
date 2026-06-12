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
                t.setState(Tile.STATE_UNAVAILABLE);
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
            // === BẬT HOMACC ===
            // FIX BUG 4: Ghi pref trước — HomescreenService sẽ đọc
            // và ẩn old bars ngay khi nhận SYNC_STATE
            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("shortcut_acc_home_on", true)
                .putBoolean("shortcut_home_on", false)
                .apply();

            // Signal HomescreenService ẩn old bars (deep sleep, KHÔNG stopService)
            sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));

            // Bật AccHome
            Intent accIntent = new Intent(this, AccessibleHomeService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(accIntent);
            else startService(accIntent);

            // FIX BUG 8: Hiện notification NGAY — không chờ onStartListening
            showNotification();

            // Cập nhật tile
            updateTileState(true);

        } else {
            // === TẮT HOMACC ===
            // FIX BUG 8: Cancel notification TRƯỚC KHI stopService
            // để tránh onStartListening() tạo lại notification
            cancelNotification();

            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("shortcut_acc_home_on", false)
                .apply();

            // Gỡ overlay
            sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
            stopService(new Intent(this, AccessibleHomeService.class));

            // Nếu old Home đã từng bật → wake up lại
            boolean oldHomeWasOn = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .getBoolean("shortcut_home_on", false);
            if (oldHomeWasOn && !HomescreenService.isRunning) {
                Intent homeIntent = new Intent(this, HomescreenService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(homeIntent);
                else startService(homeIntent);
                sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
            }

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
        Notification n = new Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Homacc đang chạy")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .build();
        nm.notify(NOTIF_ID, n);
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
            // Lazy init: chỉ tạo channel 1 lần
            if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
                NotificationChannel nc = new NotificationChannel(
                    NOTIF_CHANNEL, "Trạng thái Homacc",
                    NotificationManager.IMPORTANCE_LOW);
                nm.createNotificationChannel(nc);
            }
        }
    }
}
