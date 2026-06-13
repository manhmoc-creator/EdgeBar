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
    public void onStartListening(){
        Tile t= getQsTile();
        if(t== null) return;
        boolean oldHomeOn = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getBoolean("shortcut_home_on", false);
        t.setState(oldHomeOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick(){
        android.content.SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        boolean oldHomeOn = prefs.getBoolean("shortcut_home_on", false);

        if (oldHomeOn) {
            // === TẮT old Home ===
            // [MỤC 0] CHỈ tắt cờ old Home, KHÔNG đụng MorseLock.
            prefs.edit().putBoolean("shortcut_home_on", false).apply();
            boolean morseOn = prefs.getBoolean("morse_mode_en", false);
            boolean accHomeOn = prefs.getBoolean("shortcut_acc_home_on", false);
            if (!morseOn) {
                // Không service nào khác cần HomescreenService → dừng để tiết kiệm RAM
                stopService(new Intent(this, HomescreenService.class));
            } else {
                sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
            }
            // [MỤC 5] Homacc thức dậy nếu đã từng bật
            if (accHomeOn) sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_WAKE"));
        } else {
            // === BẬT old Home ===
            boolean accHomeWasOn = prefs.getBoolean("shortcut_acc_home_on", false);
            prefs.edit().putBoolean("shortcut_home_on", true).apply();
            // [MỤC 5] Homacc vào deep-sleep (ẩn view, GIỮ service sống)
            if (accHomeWasOn) sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_SLEEP"));
            Intent homeIntent = new Intent(this, HomescreenService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(homeIntent);
            else startService(homeIntent);
        }

        Tile t = getQsTile();
        if (t == null) return;
        boolean newState = !oldHomeOn;
        t.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
