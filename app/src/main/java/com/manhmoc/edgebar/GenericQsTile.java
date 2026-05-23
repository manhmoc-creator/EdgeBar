package com.manhmoc.edgebar;
import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent; import android.content.SharedPreferences;
public class GenericQsTile extends TileService {
    private int tileId;
    public GenericQsTile(int id) { this.tileId = id; }
    @Override public void onStartListening() { Tile t = getQsTile(); if(t!=null){t.setState(Tile.STATE_ACTIVE); t.updateTile();} }
    @Override public void onClick() {
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String act = prefs.getString("tile_" + tileId + "_act", "NONE");
        if (!act.equals("NONE")) { Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION"); ipc.putExtra("act", act); sendBroadcast(ipc); }
        sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
}
