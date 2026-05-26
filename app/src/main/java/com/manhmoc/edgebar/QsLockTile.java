package com.manhmoc.edgebar; import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent; import android.provider.Settings;
public class QsLockTile extends TileService {
    @Override public void onClick() { Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivityAndCollapse(i); }
}
