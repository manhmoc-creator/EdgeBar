      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * DUAL-SOUL MANAGER — chỉ còn quản lý Homeb. Homacc KHÔNG còn bật/tắt thủ
 * công nữa, nó bám hoàn toàn theo vòng đời Accessibility (xem
 * EdgeBarService.onServiceConnected/onDestroy), y hệt cách Lock hoạt động.
 *
 * Bất biến:
 *   - Accessibility TẮT -> Homeb BẬT (Homacc/Lock không thể chạy)
 *   - Accessibility BẬT -> Homeb TẮT hẳn, Homacc BẬT (giống Lock)
 * turnOnHomeb() chủ động tắt Accessibility để đảm bảo bất biến trên luôn
 * đúng — loại trừ khả năng 2 overlay engine cùng tồn tại (nguồn gốc crash cũ).
 */
public class HomeEngineManager {

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
    }

    public static void turnOnHomeb(Context c) {
        disableAccessibilityService(c);
        prefs(c).edit().putBoolean("shortcut_home_on", true).apply();
        if (!HomescreenService.isRunning) {
            Intent i = new Intent(c, HomescreenService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        }
    }

    public static void turnOffHomeb(Context c) {
        prefs(c).edit().putBoolean("shortcut_home_on", false).apply();
        if (HomescreenService.isRunning) {
            c.stopService(new Intent(c, HomescreenService.class));
        }
    }

    /** Cần quyền WRITE_SECURE_SETTINGS (cấp qua ADB 1 lần, xem showPremiumDialog()).
     *  Chưa có quyền thì bỏ qua êm — Homeb vẫn hiện, chỉ là Accessibility/Homacc
     *  không tự tắt theo được. */
    private static void disableAccessibilityService(Context c) {
    try {
        String mySvc = c.getPackageName() + "/" + EdgeBarService.class.getName();
        String cur = android.provider.Settings.Secure.getString(
            c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null || !cur.contains(mySvc)) return;

        String[] parts = cur.split(":");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String pt : parts) {
            String t = pt.trim();
            if (!t.isEmpty() && !t.equals(mySvc)) set.add(t);
        }
        android.provider.Settings.Secure.putString(
            c.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            android.text.TextUtils.join(":", set));

        // [FIX] AccessibleHomeService KHÔNG bị hệ thống tự dừng khi ta tắt Accessibility
        // bằng Settings.Secure (nó không phải AccessibilityService) — phải tự tay dừng,
        // nếu không thông báo "EB Lacck" (foreground notif id=99 của nó) sẽ đứng mãi.
        if (AccessibleHomeService.isRunning) {
            c.stopService(new Intent(c, AccessibleHomeService.class));
        }
     } catch (Exception ignored) {}
  }
}
