       package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(act);
        boolean isReplaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(act);
        if (!isBoot && !isReplaced) return;

        SharedPreferences prefs = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        // [MỚI] User đã bấm "Dừng vĩnh viễn" -> máy có reboot cũng KHÔNG tự hồi sinh service nào.
        // Đặt TRƯỚC mọi lệnh startForegroundService() để Zero-IPC, Zero-WakeLock, Zero-Alarm
        // khi cờ đang bật — đúng tinh thần tiết kiệm pin tuyệt đối cho Pixel 2XL.
        if (prefs.getBoolean("edgebar_permanently_stopped", false)) return;
        if (VolumeButtonService.hasAnyRule(prefs)) {

            Intent svc = new Intent(c, VolumeButtonService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        }
        if (ProximityWaveService.hasAnyRule(prefs)) {
    Intent px = new Intent(c, ProximityWaveService.class);
    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(px);
    else c.startService(px);
        }
        if (isReplaced) return; // cài đè: chỉ cần khôi phục 2 service cảm biến/phím, không đụng Homeb
        // [AUTO-HOMEB] Máy vừa boot mà Trợ năng đang tắt sẵn -> tự bật Homeb
        // ngay từ đầu, không cần chờ user mở app lên mới phát hiện.
        if (!isAccEnabled(c) && !prefs.getBoolean("shortcut_home_on", false)) {
            prefs.edit()
                .putBoolean("shortcut_home_on", true)
                .putBoolean("home_auto_by_acc_off", true)
                .apply();
            Intent home = new Intent(c, HomescreenService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(home);
            else c.startService(home);
        }
// [MỚI] Đảm bảo watchdog định kỳ luôn được lên lịch lại sau mỗi lần khởi động máy
        HomebWatchdogReceiver.scheduleRepeating(c);
    }
    private boolean isAccEnabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/" + EdgeBarService.class.getName());
    }
}
