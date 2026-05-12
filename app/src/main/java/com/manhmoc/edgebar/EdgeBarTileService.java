package com.manhmoc.edgebar;

import android.content.ComponentName;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class EdgeBarTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        try {
            String serviceString = getPackageName() + "/" + EdgeBarService.class.getName();
            String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            
            if (enabledServices == null) enabledServices = "";

            boolean isEnabled = enabledServices.contains(serviceString);
            
            if (isEnabled) {
                // ĐANG BẬT -> TẮT (Xóa khỏi danh sách)
                enabledServices = enabledServices.replace(serviceString, "").replace("::", ":");
                if (enabledServices.endsWith(":")) enabledServices = enabledServices.substring(0, enabledServices.length() - 1);
            } else {
                // ĐANG TẮT -> BẬT (Thêm vào danh sách)
                if (enabledServices.isEmpty()) {
                    enabledServices = serviceString;
                } else {
                    enabledServices += ":" + serviceString;
                }
                // Đảm bảo cờ Accessibility chung được bật
                Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }

            // Ghi đè vào hệ thống
            Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServices);
            
            // Cập nhật giao diện nút
            Tile tile = getQsTile();
            tile.setState(isEnabled ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
            tile.updateTile();

        } catch (SecurityException e) {
            Log.e("EdgeBar", "CHƯA CẤP QUYỀN ADB WRITE_SECURE_SETTINGS!");
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Cập nhật trạng thái nút khi kéo thanh trạng thái xuống
        String serviceString = getPackageName() + "/" + EdgeBarService.class.getName();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean isEnabled = enabledServices != null && enabledServices.contains(serviceString);
        
        Tile tile = getQsTile();
        tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
