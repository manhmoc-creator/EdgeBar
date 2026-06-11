package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Yêu cầu 7: Tile bật/tắt MorseLock + Accessible Home overlay
public class QsAccHomeTile extends TileService {

    private boolean isAccEnabled() {
        String s = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(
            getPackageName() + "/" + EdgeBarService.class.getName());
    }

    @Override public void onStartListening() {
        Tile t = getQsTile();
        if (!isAccEnabled()) {
            t.setState(Tile.STATE_UNAVAILABLE);
        } else {
            t.setState(AccessibleHomeService.isRunning
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        t.updateTile();
        // Yêu cầu 7: Hiện icon "x" trên status bar khi tắt
        updateStatusBarNotification(!AccessibleHomeService.isRunning);
    }

    @Override public void onClick() {
        if (!isAccEnabled()) return; // Yêu cầu 4: Không làm gì nếu Acc tắt

        // Yêu cầu 8: Khi bật Acc Home → tắt Home overlay cũ
        if (!AccessibleHomeService.isRunning) {
            stopService(new Intent(this, HomescreenService.class));
            sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_ON"));
        } else {
            sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
        }

        Tile t = getQsTile();
        t.setState(AccessibleHomeService.isRunning
            ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }

    private void updateStatusBarNotification(boolean showX) {
        String cid = "eb_acc_home_status";
        NotificationChannel nc = new NotificationChannel(
            cid, "Trạng thái Acc Home", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        if (showX) {
            // Yêu cầu 7: Hiện icon "x" khi Acc Home đang tắt
            Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Acc Home đang TẮT")
                .setSmallIcon(android.R.drawable.ic_delete) // icon "x"
                .setOngoing(true)
                .build();
            getSystemService(NotificationManager.class).notify(77, n);
        } else {
            getSystemService(NotificationManager.class).cancel(77);
        }
    }
}
