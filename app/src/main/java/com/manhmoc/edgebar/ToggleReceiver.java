package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.provider.Settings; import android.os.Build; import android.content.SharedPreferences;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        // [TỐI ƯU PIXEL 2XL] Đan chéo Intent mới gọi trực tiếp qua ADB với Zero-Overhead
        if ("com.manhmoc.edgebar.TOGGLE_ACC".equals(action)) {
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
/**
 * KỊCH BẢN 1: Đang ở app trong Blacklist -> đẩy về Home TRƯỚC (không cần Trợ năng
 * vì "HOME" đã được HomescreenService.exec() xử lý riêng, không phụ thuộc Accessibility)
 * rồi mới toggle Accessibility, tránh app Blacklist "lộ" ra khi Accessibility vừa bật.
 *
 * KỊCH BẢN 2: Đang ở Home / Quick Settings / app thường (không trong Blacklist)
 * -> toggle ngay lập tức, không cần đẩy về Home trước — mượt hơn, tiết kiệm 1 vòng
 * Handler.postDelayed(200ms) không cần thiết.
 */
private void toggleAcc(Context c, String mySvc) {
    try {
        String cur0 = android.provider.Settings.Secure.getString(c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean currentlyEnabled = cur0 != null && cur0.contains(mySvc);

        if (!currentlyEnabled) {
            // Sắp BẬT Trợ năng => hiện đang ở Homeb. Đọc field static — ZERO chi phí
            // UsageStats/IPC vì Homeb đang chạy và field này luôn cập nhật qua SYNC_STATE.
            String fgPkg = HomescreenService.liveForegroundPkg;
            SharedPreferences sp = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
            String bl = sp.getString("blacklist", "");
            boolean isBlacklisted = false;
            if (fgPkg != null && !fgPkg.isEmpty() && !bl.isEmpty())
                for (String p : bl.split(",")) if (p.trim().equals(fgPkg)) { isBlacklisted = true; break; }

            if (isBlacklisted) {
                Intent home = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                home.putExtra("act", "HOME");
                c.sendBroadcast(home);
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(() -> doToggleAccSwitch(c, mySvc), 200);
                return;
            }
            // KỊCH BẢN 2: không nằm trong Blacklist -> toggle ngay
        }
        doToggleAccSwitch(c, mySvc);
    } catch (Exception e) {}
}

/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * Fix Bug 6: PAUSE HomescreenService + EdgeBarService WM operations trước khi ghi Settings.
 * Tránh crash "WindowManager token is no longer valid" khi AccessibilityService
 * restart do thay đổi ENABLED_ACCESSIBILITY_SERVICES.
 *
 * Timeline:
 *   t=0ms   : broadcast PAUSE_WM_OPS → cả 2 service ẩn tất cả bars/corners
 *   t=150ms : ghi Settings (trigger AccessibilityService restart)
 *   t=650ms : broadcast RESUME_WM_OPS → vẽ lại bars/corners theo trạng thái mới
 */
private void doToggleAccSwitch(Context c, String mySvc) {
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
