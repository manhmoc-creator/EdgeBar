      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class AppLockHelper {
    private static final Map<String, Long> lastUnlock = new HashMap<>();
    public static String currentlyActiveLockedApp = "";
    // Ân hạn tối thiểu khi có sự kiện "nhiễu" xen giữa (bàn phím, dialog hệ thống...)
    private static final long TRANSIENT_GRACE_MS = 2500;

    public static void markUnlocked(String pkg) {
        lastUnlock.put(pkg, System.currentTimeMillis());
        currentlyActiveLockedApp = pkg;
    }

    // Package hệ thống/tạm thời — KHÔNG coi là "đã rời khỏi app đang khoá"
    private static boolean isTransientPkg(String pkg) {
        return pkg.contains("systemui") || pkg.contains("inputmethod")
            || pkg.contains("launcher") || pkg.equals("android")
            || pkg.contains("permissioncontroller") || pkg.contains("packageinstaller");
    }

    public static void check(Context ctx, SharedPreferences prefs, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return;

        String lockList = prefs.getString("applock_list", "");
        if (lockList.isEmpty()) return;

        boolean isLocked = false;
        for (String p : lockList.split(",")) {
            if (p.trim().equals(pkg)) { isLocked = true; break; }
        }

        if (!isLocked) {
            if (isTransientPkg(pkg)) return; // bỏ qua nhiễu, giữ nguyên trạng thái
            currentlyActiveLockedApp = "";
            return;
        }

        if (pkg.equals(currentlyActiveLockedApp)) {
            lastUnlock.put(pkg, System.currentTimeMillis());
            return;
        }

        long graceMs = Math.max(TRANSIENT_GRACE_MS, prefs.getInt("applock_grace_sec", 0) * 1000L);
        Long last = lastUnlock.get(pkg);

        if (last != null && (System.currentTimeMillis() - last) < graceMs) {
            currentlyActiveLockedApp = pkg;
            lastUnlock.put(pkg, System.currentTimeMillis());
            return;
        }

        Intent lockIntent = new Intent(ctx, LockOverlayActivity.class);
        lockIntent.putExtra("lock_pkg", pkg);
        lockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        ctx.startActivity(lockIntent);
    }

    public static void clearAll() {
        lastUnlock.clear();
        currentlyActiveLockedApp = "";
    }
}
