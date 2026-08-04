      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Dùng chung cho cả EdgeBarService (có Accessibility) và HomescreenService (Homeb). */
public class AppLockHelper {
    private static final Map<String, Long> lastUnlock = new HashMap<>();
    // [MỚI] Ghi nhớ app bị khoá ĐANG HIỆN HÀNH để không khoá lặp lại khi thao tác bên trong app
    public static String currentlyActiveLockedApp = "";

    public static void markUnlocked(String pkg) {
        lastUnlock.put(pkg, System.currentTimeMillis());
        currentlyActiveLockedApp = pkg;
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
            // User đã thoát ra một app bình thường (không bị khoá).
            // Gỡ bộ nhớ active để khi họ quay lại app khoá, thời gian ân hạn sẽ bắt đầu được tính.
            currentlyActiveLockedApp = "";
            return;
        }

        // --- TỪ ĐÂY TRỞ XUỐNG: USER ĐANG TRONG APP BỊ KHOÁ ---
        
        if (pkg.equals(currentlyActiveLockedApp)) {
            // Đã mở khoá và đang thao tác bên trong app này -> LIÊN TỤC làm mới mốc thời gian.
            // Điều này triệt tiêu hoàn toàn lỗi "hỏi mật khẩu liên tục khi chuyển tab".
            lastUnlock.put(pkg, System.currentTimeMillis());
            return;
        }

        // Trạng thái: User vừa chuyển TỪ ngoài VÀO app bị khoá. Bắt đầu kiểm tra thời gian ân hạn.
        // Ép tối thiểu 500ms ân hạn để chống dội (bounce-back) khi Activity nhập mật khẩu vừa đóng lại.
        long graceMs = Math.max(500L, prefs.getInt("applock_grace_sec", 0) * 1000L);
        Long last = lastUnlock.get(pkg);
        
        if (last != null && (System.currentTimeMillis() - last) < graceMs) {
            // Vẫn trong thời gian ân hạn -> cho phép vào thẳng, đánh dấu là đang active.
            currentlyActiveLockedApp = pkg;
            lastUnlock.put(pkg, System.currentTimeMillis());
            return;
        }

        // Hết thời gian ân hạn hoặc chưa mở khoá lần nào -> Khởi động màn hình Khoá.
        Intent lockIntent = new Intent(ctx, LockOverlayActivity.class);
        lockIntent.putExtra("lock_pkg", pkg);
        String fastBioList = prefs.getString("applock_fastbio_list", "");
        boolean allowFinger = ("," + fastBioList + ",").contains("," + pkg + ",");
        lockIntent.putExtra("allow_fingerprint", allowFinger);
        lockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        ctx.startActivity(lockIntent);
    }
    public static void clearAll() { 
        lastUnlock.clear(); 
        currentlyActiveLockedApp = "";
    }
}
