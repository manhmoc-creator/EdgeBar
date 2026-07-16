      package com.manhmoc.edgebar;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.os.UserHandle;
import android.os.UserManager;
import java.util.*;

/**
 * Deep Scan — dùng StorageStatsManager.queryStatsForUid() (API 26+),
 * đọc trực tiếp từ hệ thống quản lý dung lượng theo UID (giống cách
 * SDMaid2 đạt độ chính xác cao hơn duyệt file thủ công), quét cả
 * profile phụ (Island) qua UserManager.getUserProfiles().
 *
 * Battery/RAM Pixel 2XL: chạy trên background thread do người gọi tự
 * quản lý (không tự spawn thread ở đây); không giữ Bitmap icon —
 * chỉ trả về text (pkg, label, bytes) để tiết kiệm RAM tối đa.
 */
public class StorageScanner {

    public static class AppStorageInfo {
        public String pkg, label;
        public long totalBytes;
        public boolean isIsland;
        public AppStorageInfo(String pkg, String label, long bytes, boolean isIsland) {
            this.pkg = pkg; this.label = label; this.totalBytes = bytes; this.isIsland = isIsland;
        }
    }

    // Gọi hàm này TRÊN BACKGROUND THREAD — không gọi từ main thread
    public static List<AppStorageInfo> scanAll(Context ctx) {
        List<AppStorageInfo> result = new ArrayList<>();
        StorageStatsManager ssm = (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
        UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
        LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (ssm == null || um == null || la == null) return result;

        try {
            for (UserHandle profile : um.getUserProfiles()) {
                boolean isIsland = !profile.equals(android.os.Process.myUserHandle());
                for (android.content.pm.LauncherActivityInfo info : la.getActivityList(null, profile)) {
                    String pkg = info.getApplicationInfo().packageName;
                    // tránh quét trùng nếu 1 app có nhiều activity launcher
                    boolean already = false;
                    for (AppStorageInfo a : result) if (a.pkg.equals(pkg) && a.isIsland == isIsland) { already = true; break; }
                    if (already) continue;
                    try {
                        StorageStats stats = ssm.queryStatsForUid(
                            info.getApplicationInfo().storageUuid, info.getApplicationInfo().uid);
                        long total = stats.getAppBytes() + stats.getDataBytes() + stats.getCacheBytes();
                        result.add(new AppStorageInfo(pkg, info.getLabel().toString(), total, isIsland));
                    } catch (Exception ignored) { /* app không cấp quyền đọc / lỗi UID */ }
                }
            }
        } catch (Exception ignored) {}

        result.sort((a, b) -> Long.compare(b.totalBytes, a.totalBytes)); // to nhất trước
        return result;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024*1024) return String.format("%.1f KB", bytes/1024f);
        if (bytes < 1024*1024*1024) return String.format("%.1f MB", bytes/1024f/1024f);
        return String.format("%.2f GB", bytes/1024f/1024f/1024f);
    }
}
