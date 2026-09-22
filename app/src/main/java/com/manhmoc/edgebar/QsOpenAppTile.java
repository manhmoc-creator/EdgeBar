      package com.manhmoc.edgebar;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** QS Tile TĨNH — luôn có sẵn, không phụ thuộc cấu hình nào, không thể lỗi.
 *  Zero-RAM: không đọc prefs, không giữ state, chỉ mở MainActivity. */
public class QsOpenAppTile extends TileService {
    @Override public void onStartListening() {
        Tile t = getQsTile();
        if (t != null) { t.setState(Tile.STATE_INACTIVE); t.setLabel("Mở Edge Bar"); t.updateTile(); }
    }
    @Override public void onClick() {
        super.onClick();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityAndCollapse(i);
    }
}
