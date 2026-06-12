package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Yêu cầu 7: Tile bật/tắt MorseLock + Accessible Home overlay
public class QsAccHomeTile extends TileService {

    private boolean isAccEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(
            getPackageName() + "/" + EdgeBarService.class.getName());
    }

    @Override public void onStartListening() {
        Tile t = getQsTile();
        if (!isAccEnabled()) {
            t.setState(Tile.STATE_UNAVAILABLE);
        } else {
            t.setState(AccessibleHomeService.isRunning
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        t.updateTile();
        // Yêu cầu 7: Hiện icon "x" trên status bar khi tắt
        updateStatusBarNotification(!AccessibleHomeService.isRunning);
    }

    @Override public void onClick() {
    // YC6: Tile này quản lý CẢ MorseLock state VÀ AccHome
    if (!isAccEnabled()) return;

    if (!AccessibleHomeService.isRunning) {
        // YC3: Dùng ADB permission (WRITE_SECURE_SETTINGS) để chuyển mode
        // Bước 1: Tắt hoàn toàn Home overlay cũ (sleep mode → giải phóng RAM)
        stopService(new Intent(this, HomescreenService.class));
        getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("shortcut_home_on", false)
            .putBoolean("shortcut_acc_home_on", true)
            .apply();
        // Bước 2: Bật AccHome
        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_ON"));
        // Bước 3: Kích hoạt lại MorseLock theo AccHome
        sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
    } else {
    // Tắt AccHome → sleep mode → TỰ ĐỘNG bật lại Home cũ (YC5)
    sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
    getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
        .edit()
        .putBoolean("shortcut_acc_home_on", false)
        .putBoolean("shortcut_home_on", true) // Wake Home cũ
        .apply();
    // Khởi động lại HomescreenService nếu chưa chạy
    if (!HomescreenService.isRunning) {
        Intent homeIntent = new Intent(this, HomescreenService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26)
            startForegroundService(homeIntent);
        else startService(homeIntent);
    }
    updateStatusBarNotification(true);
}
    Tile t = getQsTile();
    t.setState(AccessibleHomeService.isRunning ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
}

    private void updateStatusBarNotification(boolean showX) {
        String cid = "eb_acc_home_status";
        NotificationChannel nc = new NotificationChannel(
            cid, "Trạng thái Acc Home", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        if (showX) {
            // Yêu cầu 7: Hiện icon "x" khi Homacc On
            Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Homacc On")
                .setSmallIcon(android.R.drawable.ic_delete) // icon "x"
                .setOngoing(true)
                .build();
            getSystemService(NotificationManager.class).notify(77, n);
        } else {
            getSystemService(NotificationManager.class).cancel(77);
        }
    }
}
