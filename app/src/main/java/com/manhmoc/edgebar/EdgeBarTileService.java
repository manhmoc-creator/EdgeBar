package com.manhmoc.edgebar;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class EdgeBarTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        ToggleHelper.toggle(this);
        updateTileState();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    private void updateTileState() {
        String serviceString = getPackageName() + "/" + EdgeBarService.class.getName();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean isEnabled = enabledServices != null && enabledServices.contains(serviceString);
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }
}
