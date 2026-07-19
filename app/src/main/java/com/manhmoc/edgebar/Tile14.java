package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * V19.12.3.6.8 THE ETERNAL EGO — Tile14
 * Label + Icon động từ prefs. Auto-icon theo action.
 * ZERO RAM: Icon.createWithResource() chỉ lưu resource ID (4 bytes).
 * Receiver chỉ active khi QS panel mở → ZERO background battery drain.
 */
public class Tile14 extends TileService {

    // ICON_POOL phải khớp với TILE_ICON_NAMES trong MainActivity
    private static final int[] ICON_POOL = {
        android.R.drawable.ic_menu_compass,
        android.R.drawable.ic_menu_search,
        android.R.drawable.ic_lock_idle_lock,
        android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_crop,
        android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_send,
        android.R.drawable.ic_media_next,
        android.R.drawable.ic_menu_share,
        android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_menu_manage,
        android.R.drawable.ic_menu_send,
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_delete,
        android.R.drawable.ic_menu_add,
        android.R.drawable.ic_menu_close_clear_cancel,
        android.R.drawable.ic_menu_upload,
        android.R.drawable.ic_menu_view,
        android.R.drawable.star_on,
        android.R.drawable.ic_menu_mylocation
    };

    private BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateTileUI();
        }
    };

    @Override public void onStartListening() {
        try {
            registerReceiver(configReceiver,
                new IntentFilter("com.manhmoc.edgebar.TILE_CONFIG_CHANGED"));
        } catch (Exception ignored) {}
        updateTileUI();
    }

    @Override public void onStopListening() {
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) {}
    }

    private void updateTileUI() {
    Tile t = getQsTile();
    if (t == null) return;
    SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    // Label động
    String label = prefs.getString("tile_14_label", "Tile 14");
    t.setLabel(label);
    // Icon động — ZERO RAM: chỉ lưu resource ID
    int iconIdx = prefs.getInt("tile_14_icon_idx", 0);
    if (iconIdx < 0 || iconIdx >= ICON_POOL.length) iconIdx = 0;
    t.setIcon(Icon.createWithResource(this, ICON_POOL[iconIdx]));
    String action = prefs.getString("tile_14_act", "NONE");
    t.setState(action.equals("NONE") ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
}

@Override public void onClick() {
    super.onClick();
    SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String act = prefs.getString("tile_14_act", "NONE");
    if (!act.equals("NONE")) {
        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        ipc.putExtra("act", act);
        if (act.equals("LAUNCH_APP")) {
            ipc.putExtra("launch_pkg", prefs.getString("tile_14_launch_pkg", ""));
        }
        sendBroadcast(ipc);
    }
    Tile t = getQsTile();
    if (t != null) { t.setState(Tile.STATE_INACTIVE); t.updateTile(); }
  }
}
