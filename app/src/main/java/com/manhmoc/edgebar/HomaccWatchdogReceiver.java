package com.manhmoc.edgebar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Watchdog cực nhẹ — chạy vài ms rồi kết thúc ngay, KHÔNG giữ process sống.
 * Mục đích: nếu Homacc bị OOM-kill mà AccessibleHomeService không tự hồi sinh
 * kịp qua đường AccessibilityService reconnect, watchdog này (bắn mỗi 5 phút
 * qua AlarmManager.setInexactRepeating — OS tự gộp batch, không tốn pin thêm)
 * sẽ phát hiện và khởi động lại.
 */
public class HomaccWatchdogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        // [FIX] "shortcut_acc_home_on" không hề được set true ở bất kỳ đâu trong app,
        // khiến watchdog cũ luôn no-op. Đổi sang đúng bản chất: Homacc PHẢI sống bất cứ
        // khi nào Accessibility đang bật (đúng vòng đời đã thiết kế).
        if (isAccEnabled(c) && !AccessibleHomeService.isRunning) {
            Intent svc = new Intent(c, AccessibleHomeService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        }
    }
    private boolean isAccEnabled(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/" + EdgeBarService.class.getName());
    }
}
