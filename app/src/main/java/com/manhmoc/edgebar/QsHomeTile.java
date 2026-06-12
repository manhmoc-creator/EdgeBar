package com.manhmoc.edgebar;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.Intent;
import android.os.Build;

public class QsHomeTile extends TileService {
    @Override public void onStartListening() {
        Tile t = getQsTile();
        t.setState(HomescreenService.isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
    @Override public void onClick() {
    // YC4: Tile HOME chỉ bật/tắt bars cũ (display+adb)
    // KHÔNG đụng MorseLock (EdgeBarService) và KHÔNG đụng AccHome
    if (HomescreenService.isRunning) {
        // Sleep mode: stopService() giải phóng RAM hoàn toàn
        stopService(new Intent(this, HomescreenService.class));
        getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
            .edit().putBoolean("shortcut_home_on", false).apply();
        // YC5: Hiện notification icon "💤" trên status bar báo overlay đang ngủ
        showSleepNotification(true);
    } else {
        // Đánh thức: chỉ start khi AccHome KHÔNG chạy (YC8)
        if (!AccessibleHomeService.isRunning) {
            if (android.os.Build.VERSION.SDK_INT >= 26)
                startForegroundService(new Intent(this, HomescreenService.class));
            else startService(new Intent(this, HomescreenService.class));
            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit().putBoolean("shortcut_home_on", true).apply();
            showSleepNotification(false);
        }
    }
    Tile t = getQsTile();
    t.setState(HomescreenService.isRunning ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
}

// YC5: Notification icon liên tục trên status bar
private void showSleepNotification(boolean isSleeping) {
    String cid = "eb_home_sleep";
    android.app.NotificationChannel nc = new android.app.NotificationChannel(
        cid, "Trạng thái Home Overlay", android.app.NotificationManager.IMPORTANCE_LOW);
    getSystemService(android.app.NotificationManager.class).createNotificationChannel(nc);
    if (isSleeping) {
        android.app.Notification n = new android.app.Notification.Builder(this, cid)
            .setContentTitle("Home Overlay đang NGỦ")
            .setSmallIcon(android.R.drawable.ic_menu_day) // icon 💤
            .setOngoing(true)
            .build();
        getSystemService(android.app.NotificationManager.class).notify(78, n);
    } else {
        getSystemService(android.app.NotificationManager.class).cancel(78);
    }
  }
}
