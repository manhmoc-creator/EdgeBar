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
        if (HomescreenService.isRunning) {
            stopService(new Intent(this, HomescreenService.class));
            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("shortcut_home_on", false)
                .apply();
        } else {
            boolean accHomeWasOn = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .getBoolean("shortcut_acc_home_on", false);

            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("shortcut_home_on", true)
                .putBoolean("shortcut_acc_home_on", false)
                .apply();

            if (accHomeWasOn) {
                sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
            }

            Intent homeIntent = new Intent(this, HomescreenService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(homeIntent);
            else startService(homeIntent);
        }

        Tile t = getQsTile();
        if (t == null) return;
        t.setState(HomescreenService.isRunning
            ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }
}
