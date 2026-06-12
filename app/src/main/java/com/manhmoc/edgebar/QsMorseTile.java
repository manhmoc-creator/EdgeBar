      // File: app/src/main/java/com/manhmoc/edgebar/QsMorseTile.java
package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Tile riêng: CHỈ bật/tắt MorseLock overlay
// Yêu cầu: Accessibility BẬT + UsageStats + Display + Admin
public class QsMorseTile extends TileService {

    private boolean isAccEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(
            getPackageName() + "/" + EdgeBarService.class.getName());
    }

    private boolean isMorseOn() {
        return getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
            .getBoolean("morse_mode_en", false);
    }

    @Override public void onStartListening() {
        Tile t = getQsTile();
        if (!isAccEnabled()) {
            t.setState(Tile.STATE_UNAVAILABLE);
            t.setLabel("MorseLock (cần Acc)");
        } else {
            t.setState(isMorseOn() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            t.setLabel(isMorseOn() ? "MorseLock ON" : "MorseLock OFF");
        }
        t.updateTile();
        // Notification icon liên tục trên status bar
        updateMorseNotification(isMorseOn());
    }

    @Override public void onClick() {
        // Yêu cầu: Accessibility phải BẬT mới hoạt động
        if (!isAccEnabled()) return;

        // Toggle MorseLock — KHÔNG đụng HomescreenService hay AccHome
        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_MORSE"));

        boolean newState = !isMorseOn();
        Tile t = getQsTile();
        t.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(newState ? "MorseLock ON" : "MorseLock OFF");
        t.updateTile();
        updateMorseNotification(newState);
    }

    private void updateMorseNotification(boolean morseOn) {
        String cid = "eb_morse_status";
        NotificationChannel nc = new NotificationChannel(
            cid, "Trạng thái MorseLock", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        if (morseOn) {
            Notification n = new Notification.Builder(this, cid)
                .setContentTitle("MorseLock is On")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
            getSystemService(NotificationManager.class).notify(79, n);
        } else {
            getSystemService(NotificationManager.class).cancel(79);
        }
    }
}
