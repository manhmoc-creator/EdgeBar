package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.provider.Settings; import android.os.Build;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        // [TỐI ƯU PIXEL 2XL] Đan chéo Intent mới gọi trực tiếp qua ADB với Zero-Overhead
        if ("com.manhmoc.edgebar.TOGGLE_ACC".equals(action)) {
            // [FIX CRASH] Gửi lệnh Pause WM Ops trước khi toggle để tránh BadTokenException
            c.sendBroadcast(new Intent("com.manhmoc.edgebar.PAUSE_WM_OPS"));
            toggleAcc(c, c.getPackageName() + "/" + EdgeBarService.class.getName());
        } else if ("com.manhmoc.edgebar.TOGGLE_APP".equals(action)) {
            String data = i.getDataString();
            if (data != null && data.startsWith("acc://")) toggleAcc(c, data.substring(6));
        }
    }
    private boolean isAccEnabled(Context c) {
    String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return s != null && s.contains(c.getPackageName() + "/" + EdgeBarService.class.getName());
}
    // [THÊM] method toggleAcc() mới — PAUSE/RESUME WM safeguard:
/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * Fix Bug 6: PAUSE HomescreenService WM operations trước khi ghi Settings.
 * Tránh crash "WindowManager token is no longer valid" khi AccessibilityService
 * restart do thay đổi ENABLED_ACCESSIBILITY_SERVICES.
 *
 * Timeline:
 *   t=0ms   : broadcast PAUSE_WM_OPS → HomescreenService ẩn tất cả bars
 *   t=150ms : ghi Settings (trigger AccessibilityService restart)
 *   t=650ms : broadcast RESUME_WM_OPS → HomescreenService vẽ lại bars
 */
private void toggleAcc(Context c, String mySvc) {
    try {
        c.sendBroadcast(new android.content.Intent("com.manhmoc.edgebar.PAUSE_WM_OPS"));

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                String cur = android.provider.Settings.Secure.getString(
                    c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (cur == null) cur = "";

                String[] parts = cur.split(":");
                java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
                for (String pt : parts) { if (!pt.trim().isEmpty()) set.add(pt.trim()); }

                boolean wasEnabled = set.contains(mySvc);

                // ✅ SỬA: gọi ngay tại đây, sau khi wasEnabled đã có giá trị
                if (wasEnabled) HomebWatchdogReceiver.scheduleImmediate(c);

                if (wasEnabled) set.remove(mySvc);
                else set.add(mySvc);

                String newVal = android.text.TextUtils.join(":", set);
                android.provider.Settings.Secure.putString(
                    c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newVal);
                android.provider.Settings.Secure.putString(
                    c.getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    c.sendBroadcast(
                        new android.content.Intent("com.manhmoc.edgebar.RESUME_WM_OPS")
                            .putExtra("acc_cache_reset", true));
                    c.sendBroadcast(
                        new android.content.Intent("com.manhmoc.edgebar.SYNC_STATE")
                            .putExtra("acc_cache_reset", true));
                }, 500);

            } catch (Exception e) {}
        }, 150);

    } catch (Exception e) {}
 }
}
