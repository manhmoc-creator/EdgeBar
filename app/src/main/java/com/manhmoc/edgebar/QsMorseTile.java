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
        // Cấp độ 1: Chưa bật Accessibility
        t.setState(Tile.STATE_UNAVAILABLE);
        t.setLabel("MorseLock (cần Acc)");
    } else if (!hasWriteSecureSettings()) {
        // Cấp độ 2: Chưa cấp ADB
        // STATE_UNAVAILABLE để user biết cần chạy lệnh ADB
        t.setState(Tile.STATE_UNAVAILABLE);
        t.setLabel("MorseLock (cần ADB)");
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
        // Không có quyền ADB → tile không làm gì, chỉ update label
        Tile t = getQsTile();
        if (t != null) {
            t.setState(Tile.STATE_UNAVAILABLE);
            t.setLabel("MorseLock (cần ADB)");
            t.updateTile();
        }
        return;
    }


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
