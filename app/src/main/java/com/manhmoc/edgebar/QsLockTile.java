
package com.manhmoc.edgebar;

import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent; import android.provider.Settings;

public class QsLockTile extends TileService {

    private boolean isAccOn() { String pref = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return pref != null && pref.contains(getPackageName() + "/" + EdgeBarService.class.getName()); }

    @Override public void onStartListening() { Tile t = getQsTile(); t.setState(isAccOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile(); }

    @Override public void onClick() {

        boolean en = isAccOn();

        try { String mySvc = getPackageName() + "/" + EdgeBarService.class.getName(); String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); if (cur == null) cur = ""; if (en) cur = cur.replace(":" + mySvc, "").replace(mySvc + ":", "").replace(mySvc, ""); else cur = cur.isEmpty() ? mySvc : cur + ":" + mySvc; Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cur); Settings.Secure.putString(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1"); Tile t = getQsTile(); t.setState(!en ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile(); } 

        catch (Exception e) { Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivityAndCollapse(i); }

    }

}

