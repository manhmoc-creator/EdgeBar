package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
public class QsAccHomeTile extends TileService {
    private static final int NOTIF_ID = 77;
    private static final String NOTIF_CHANNEL = "eb_acc_home_status";
    private boolean isAccEnabled() {
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(
                getPackageName() + "/" + EdgeBarService.class.getName());
        } catch (Exception e) { return false; }
    }
    @Override
    public void onStartListening() {
        new Handler(getMainLooper()).postDelayed(() -> {
            Tile t = getQsTile();
            if (t == null) return;
            if (!isAccEnabled()) {
                t.setState(Tile.STATE_INACTIVE);
                t.setLabel("Homacc (cần Acc)");
            } else {
                boolean running = AccessibleHomeService.isRunning;
                t.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                t.setLabel(running ? "Homacc ON" : "Homacc OFF");
                syncNotification(running);
            }
            t.updateTile();
        }, 150);
    }
    @Override
public void onClick() {
    if (!isAccEnabled()) return;
    if (!AccessibleHomeService.isRunning) {
        Intent i = new Intent(this, AccessibleHomeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        showNotification();
        updateTileState(true);
    } else {
        cancelNotification();
        stopService(new Intent(this, AccessibleHomeService.class));
        updateTileState(false);
    }
}
    private void updateTileState(boolean isOn) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(isOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(isOn ? "Homacc ON" : "Homacc OFF");
        t.updateTile();
    }
    private void showNotification() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm == null) return;
    ensureChannel(nm);
    Notification.Builder builder = new Notification.Builder(this, NOTIF_CHANNEL)
.setContentTitle("Homacc")
.setSmallIcon(android.R.drawable.ic_menu_search)
.setOngoing(true)
.setPriority(Notification.PRIORITY_MAX)
.setVisibility(Notification.VISIBILITY_PUBLIC);
NotificationChannel nc = new NotificationChannel(
    NOTIF_CHANNEL, "Trạng thái Homacc",
    NotificationManager.IMPORTANCE_HIGH);
nc.setSound(null, null);
nc.enableLights(false);
nc.enableVibration(false);
nc.setShowBadge(false);
nc.setBypassDnd(false);
nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
nm.notify(NOTIF_ID, builder.build());
}
    private void cancelNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }
    private void syncNotification(boolean shouldShow) {
        if (shouldShow) showNotification();
        else cancelNotification();
    }
    private void ensureChannel(NotificationManager nm) {
    if (Build.VERSION.SDK_INT >= 26) {
        if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
NotificationChannel nc = new NotificationChannel(
    NOTIF_CHANNEL, "Trạng thái Homacc",
    NotificationManager.IMPORTANCE_HIGH);
nc.setSound(null, null);
nc.enableLights(false);
nc.enableVibration(false);
nc.setShowBadge(false);
nc.setBypassDnd(true);
nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(nc);
        }
     }
  }
}
