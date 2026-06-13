      // File: app/src/main/java/com/manhmoc/edgebar/QsMorseTile.java
// [THAY] thêm import Build vào:
package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

// Tile riêng: CHỈ bật/tắt MorseLock overlay
// Yêu cầu: Accessibility BẬT + UsageStats + Display + Admin
public class QsMorseTile extends TileService {

    // [THAY] bằng — thêm kiểm tra WRITE_SECURE_SETTINGS:
/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * Kiểm tra đồng thời 2 điều kiện bắt buộc của MorseLock:
 * 1. Accessibility Service đang bật
 * 2. Có quyền WRITE_SECURE_SETTINGS (cấp bằng ADB 1 lần trọn đời)
 *
 * Pixel 2XL opt: thử ghi/xóa key test thay vì checkSelfPermission
 * vì WRITE_SECURE_SETTINGS không thể kiểm tra bằng PackageManager
 * sau khi cấp qua ADB — phải thử thực tế mới biết.
 */
private boolean isAccEnabled() {
    try {
        String s = Settings.Secure.getString(getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(
            getPackageName() + "/" + EdgeBarService.class.getName());
    } catch (Exception e) { return false; }
}

private boolean hasWriteSecureSettings() {
    try {
        // Thử ghi test key — nếu không có quyền sẽ throw SecurityException
        Settings.Secure.putString(getContentResolver(),
            "eb_morse_perm_test", "1");
        Settings.Secure.putString(getContentResolver(),
            "eb_morse_perm_test", null);
        return true;
    } catch (SecurityException e) {
        return false;
    } catch (Exception e) {
        return false;
    }
}
    private boolean isMorseOn() {
        return getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE)
            .getBoolean("morse_mode_en", false);
    }
@Override public void onStartListening() {
    Tile t = getQsTile();
    if (t == null) return;

    if (!isAccEnabled()) {
        // [MỤC 8] Dùng STATE_INACTIVE thay UNAVAILABLE để tile KHÔNG bị
        // hệ thống tự ẩn khỏi status bar / DND.
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel("MorseLock (Acc)");
    } else if (!hasWriteSecureSettings()) {
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel("MorseLock (ADB)");
    } else {
        // Cấp độ 3: Đủ quyền → hiển thị trạng thái thực
        boolean morseOn = isMorseOn();
        t.setState(morseOn ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(morseOn ? "MorseLock ON" : "MorseLock OFF");
        updateMorseNotification(morseOn);
    }
    t.updateTile();
}
@Override public void onClick() {
    // Điều kiện 1: Accessibility phải bật
    if (!isAccEnabled()) {
        // Mở trang Accessibility để user bật
        Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(i);
        return;
    }
    // Điều kiện 2: ADB WRITE_SECURE_SETTINGS phải được cấp
    if (!hasWriteSecureSettings()) {
        Tile t = getQsTile();
        if (t != null) {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("MorseLock (cần ADB)");
            t.updateTile();
        }
        return;
    }

        // Toggle MorseLock — KHÔNG đụng HomescreenService hay AccHome
        boolean newState = !isMorseOn();

        // [MỤC 1/3/7] MorseLock cần HomescreenService sống để vẽ morseContainer
        // Bật trước khi đổi pref, để service kịp khởi tạo overlay
        if (newState && !HomescreenService.isRunning) {
            Intent homeIntent = new Intent(this, HomescreenService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(homeIntent);
            else startService(homeIntent);
        }

        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_MORSE"));

        Tile t = getQsTile();
        t.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(newState ? "MorseLock ON" : "MorseLock OFF");
        t.updateTile();
        updateMorseNotification(newState);
    }

    private void updateMorseNotification(boolean morseOn) {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (nm == null) return;
    String cid = "eb_morse_status";
    if (Build.VERSION.SDK_INT >= 26) {
        if (nm.getNotificationChannel(cid) == null) {
            NotificationChannel nc = new NotificationChannel(
                cid, "Trạng thái MorseLock", NotificationManager.IMPORTANCE_HIGH);
            nc.setSound(null, null);         // không phát âm dù HIGH
            nc.enableLights(false);
            nc.enableVibration(false);
            nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(nc);
        }
    }
    if (morseOn) {
        Notification n = new Notification.Builder(this, cid)
            .setContentTitle("MorseLock đang bảo vệ")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build();
        nm.notify(79, n);
    } else {
        nm.cancel(79);
    }
  }
}
