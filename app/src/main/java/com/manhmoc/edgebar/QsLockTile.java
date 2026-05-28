package com.manhmoc.edgebar; import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent; import android.provider.Settings;
public class QsLockTile extends TileService {
    private boolean isAccOn() { String pref = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return pref != null && pref.contains(getPackageName() + "/" + EdgeBarService.class.getName()); }
    @Override public void onStartListening() { Tile t = getQsTile(); t.setState(isAccOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile(); }
    @Override public void onClick() { Intent i = new Intent("com.manhmoc.edgebar.TOGGLE_LOCK"); sendBroadcast(i); }
}
