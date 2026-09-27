package com.manhmoc.edgebar;
import android.service.quicksettings.Tile; import android.service.quicksettings.TileService; import android.content.Intent; import android.provider.Settings;
public class QsLockTile extends TileService {
    private boolean isAccOn() { String pref = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); return pref != null && pref.contains(getPackageName() + "/" + EdgeBarService.class.getName()); }
    @Override public void onStartListening() { Tile t = getQsTile(); t.setState(isAccOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile(); }
@Override public void onClick() {
        // Kiểm tra có quyền WRITE_SECURE_SETTINGS chưa
        boolean hasSecureWrite = false;
        try {
            Settings.Secure.putString(getContentResolver(), "edge_bar_test_key", "1");
            Settings.Secure.putString(getContentResolver(), "edge_bar_test_key", null);
            hasSecureWrite = true;
        } catch (Exception e) {
            hasSecureWrite = false;
        }
        if (!hasSecureWrite) {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(i);
            return;
        }
        // Đã có quyền -> Giao cho ToggleReceiver xử lý chuẩn kịch bản
        Intent toggleIntent = new Intent("com.manhmoc.edgebar.TOGGLE_ACC");
        toggleIntent.setPackage(getPackageName());
        sendBroadcast(toggleIntent);
        boolean en = isAccOn();
        Tile t = getQsTile();
        if (t != null) {
            t.setState(!en ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.updateTile();
        }
    }
}
