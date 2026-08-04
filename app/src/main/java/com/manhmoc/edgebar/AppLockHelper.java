      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Dùng chung cho cả EdgeBarService (có Accessibility) và HomescreenService (Homeb). */
public class AppLockHelper {
    private static final Map<String, Long> lastUnlock = new HashMap<>();

    public static void markUnlocked(String pkg) {
        lastUnlock.put(pkg, System.currentTimeMillis());
    }

    public static void check(Context ctx, SharedPreferences prefs, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return;
        String lockList = prefs.getString("applock_list", "");
        if (lockList.isEmpty()) return;
        boolean isLocked = false;
        for (String p : lockList.split(",")) if (p.trim().equals(pkg)) { isLocked = true; break; }
        if (!isLocked) return;

        long graceMs = prefs.getInt("applock_grace_sec", 0) * 1000L;
        Long last = lastUnlock.get(pkg);
        if (last != null && (System.currentTimeMillis() - last) < graceMs) return;

        Intent lockIntent = new Intent(ctx, LockOverlayActivity.class);
        lockIntent.putExtra("lock_pkg", pkg);
        lockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        ctx.startActivity(lockIntent);
    }

    public static void clearAll() { lastUnlock.clear(); }
}
