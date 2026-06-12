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
    // YC5: Tắt Home cũ → deep sleep → TỰ ĐỘNG wake AccHome nếu Acc bật
    stopService(new Intent(this, HomescreenService.class));
    getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
        .edit()
        .putBoolean("shortcut_home_on", false)
        .putBoolean("shortcut_acc_home_on", true)
        .apply();
    showSleepNotification(true);
    // Wake AccHome nếu Accessibility đang bật
    String accSvcs = android.provider.Settings.Secure.getString(
        getContentResolver(),
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    boolean accOn = accSvcs != null && accSvcs.contains(
        getPackageName() + "/" + EdgeBarService.class.getName());
    if (accOn && !AccessibleHomeService.isRunning) {
        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_ON"));
    }
} else {
    // YC4: Bật Home cũ → ép tắt AccHome deep sleep
    if (AccessibleHomeService.isRunning) {
        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
    }
    if (android.os.Build.VERSION.SDK_INT >= 26)
        startForegroundService(new Intent(this, HomescreenService.class));
    else startService(new Intent(this, HomescreenService.class));
    getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
        .edit()
        .putBoolean("shortcut_home_on", true)
        .putBoolean("shortcut_acc_home_on", false)
        .apply();
    showSleepNotification(false);
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
            .setContentTitle("Home Overlay Ngủ")
            .setSmallIcon(android.R.drawable.ic_dialog_map) // icon 💤
            .setOngoing(true)
            .build();
        getSystemService(android.app.NotificationManager.class).notify(78, n);
    } else {
        getSystemService(android.app.NotificationManager.class).cancel(78);
    }
  }
}
