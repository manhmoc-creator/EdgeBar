package com.manhmoc.edgebar;
import android.service.quicksettings.TileService;
import android.content.Intent;
public class Tile1 extends TileService {
    @Override public void onClick() { super.onClick(); Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION"); ipc.putExtra("act", getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getString("tile_1_act", "NONE")); sendBroadcast(ipc); getQsTile().setState(android.service.quicksettings.Tile.STATE_INACTIVE); getQsTile().updateTile(); }
}
