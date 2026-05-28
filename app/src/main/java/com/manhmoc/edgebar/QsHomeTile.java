package com.manhmoc.edgebar; import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent;
public class QsHomeTile extends TileService {
    @Override public void onStartListening() { Tile t = getQsTile(); t.setState(HomescreenService.isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile(); }
    @Override public void onClick() { Intent i = new Intent(this, HomescreenService.class); if(HomescreenService.isRunning) stopService(i); else { if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } Tile t = getQsTile(); t.setState(HomescreenService.isRunning ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE); t.updateTile(); }
}
