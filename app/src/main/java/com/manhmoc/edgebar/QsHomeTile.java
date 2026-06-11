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
        if (HomescreenService.isRunning) {
            // 1. Tắt hẳn service để giải phóng bộ nhớ nền
            stopService(new Intent(this, HomescreenService.class));
            // 2. Xóa cờ shortcut để hệ thống không tự reload lại
            getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                .edit().putBoolean("shortcut_home_on", false).apply();
        } else {
            // Chỉ đánh thức khi Động cơ Trợ năng đang ngủ
            if (!AccessibleHomeService.isRunning) {
                sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, HomescreenService.class));
                else startService(new Intent(this, HomescreenService.class));
                getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
                    .edit().putBoolean("shortcut_home_on", true).apply();
            }
        }
        Tile t = getQsTile();
        t.setState(HomescreenService.isRunning ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }
}
