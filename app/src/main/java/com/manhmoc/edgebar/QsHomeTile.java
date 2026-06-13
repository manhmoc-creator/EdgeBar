package com.manhmoc.edgebar;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.Intent;
import android.os.Build;

/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * QsHomeTile: CHỈ kiểm soát HomescreenService (old Home overlay).
 * KHÔNG đụng AccHome domain, KHÔNG đụng MorseLock domain.
 * Battery/RAM opt: chỉ đọc static flag, zero I/O.
 */
public class QsHomeTile extends TileService {

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(HomescreenService.isRunning
            ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
public void onClick() {
    android.content.SharedPreferences prefs =
        getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

    if (HomescreenService.isRunning) {
        // TẮT old Home
        stopService(new Intent(this, HomescreenService.class));
        prefs.edit().putBoolean("shortcut_home_on", false).apply();
        // Wake Homacc nếu đang được bật trong pref
        if (prefs.getBoolean("shortcut_acc_home_on", false)
                && AccessibleHomeService.isRunning) {
            sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_WAKE"));
        }
    } else {
        // BẬT old Home → Homacc vào deep sleep (service vẫn sống, view ẩn)
        prefs.edit().putBoolean("shortcut_home_on", true).apply();
        if (AccessibleHomeService.isRunning) {
            sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_SLEEP"));
        }
        Intent homeIntent = new Intent(this, HomescreenService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(homeIntent);
        else startService(homeIntent);
    }

    // Cập nhật tile — KHÔNG đụng MorseLock, KHÔNG đụng morse_mode_en
    Tile t = getQsTile();
    if (t == null) return;
    t.setState(HomescreenService.isRunning
        ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
 }
}
