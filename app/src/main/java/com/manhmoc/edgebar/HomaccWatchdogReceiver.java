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
        SharedPreferences p = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        boolean wantOn = p.getBoolean("shortcut_acc_home_on", false);
        if (wantOn && !AccessibleHomeService.isRunning) {
            Intent svc = new Intent(c, AccessibleHomeService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        }
    }
}
