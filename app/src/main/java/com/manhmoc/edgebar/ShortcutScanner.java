      package com.manhmoc.edgebar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

/**
 * Quét danh sách app cung cấp "Create Shortcut" (ACTION_CREATE_SHORTCUT) —
 * đúng cơ chế Tasker/KWGT dùng, KHÔNG cần làm default launcher, KHÔNG cần
 * quyền đặc biệt. Đây là API "legacy shortcut" (vẫn hoạt động tới Android 14).
 *
 * Battery/RAM Pixel 2XL:
 * - Cache danh sách provider 10 phút (giống cachedAppList trong MainActivity)
 * - Icon shortcut lưu ra FILE PNG trong /files/shortcut_icons/, KHÔNG lưu base64
 *   trong SharedPreferences — base64 làm phình prefs, chậm mỗi lần prefs.getAll(),
 *   và buộc decode lại mỗi lần đọc string. File PNG chỉ decode khi thực sự vẽ UI.
 */
public class ShortcutScanner {

    private static List<ResolveInfo> cachedProviders = null;
    private static long cachedTs = 0;
    private static final long CACHE_MS = 10 * 60 * 1000; // 10 phút — danh sách provider gần như tĩnh

    public static List<ResolveInfo> getProviders(Context ctx) {
        long now = System.currentTimeMillis();
        if (cachedProviders != null && (now - cachedTs) < CACHE_MS) return cachedProviders;
        PackageManager pm = ctx.getPackageManager();
        Intent i = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        // Sắp xếp theo tên hiển thị — zero alloc thêm, chỉ sort reference có sẵn
        list.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        cachedProviders = list;
        cachedTs = now;
        return list;
    }

    /** Lưu icon shortcut ra file, trả về đường dẫn tuyệt đối. Trả "" nếu lỗi. */
    public static String saveIconToFile(Context ctx, Bitmap bmp, String shortcutId) {
        if (bmp == null) return "";
        try {
            File dir = new File(ctx.getFilesDir(), "shortcut_icons");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, shortcutId + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            // Nén PNG chất lượng vừa đủ hiển thị icon nhỏ — không cần full-res
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
            return f.getAbsolutePath();
        } catch (Exception e) { return ""; }
    }

    /** Xoá file icon khi shortcut bị xoá — tránh rác tích luỹ trong /files/ */
    public static void deleteIconFile(String path) {
        if (path == null || path.isEmpty()) return;
        try { new File(path).delete(); } catch (Exception ignored) {}
    }
}
