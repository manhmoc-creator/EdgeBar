      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

/**
 * Service sống SUỐT vòng đời app, KHÔNG phụ thuộc Accessibility bật/tắt,
 * KHÔNG bị bất kỳ watchdog Homacc/Homeb nào đụng tới.
 * Chứa Panel (Lenap) + Bubble (Assistive Touch) — 2 tính năng phải luôn sống
 * bất kể đang ở Lock/Homacc/Homeb.
 */
public class CoreFeaturesService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private SharedPreferences prefs;
    private PanelEngine panelEngine;
    private AssistiveBubbleEngine bubbleEngine;
    private BroadcastReceiver receiver;

    @Override public IBinder onBind(Intent i) { return null; }
        private final android.os.Handler panelDebounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable panelDebounceRunnable = null;
    private static final long PANEL_DEBOUNCE_MS = 120;

    private final SharedPreferences.OnSharedPreferenceChangeListener coreFeaturesPrefListener = (p, k) -> {
        if (k == null) return;
        if (k.startsWith("bubble_")) {
            bubbleEngine.onPrefChanged(k);
            return;
        }
        if (k.startsWith("pack_panel_") || (k.startsWith("shortcut_") && k.endsWith("_icon_override"))) {
            if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
            panelDebounceRunnable = () -> panelEngine.onPrefChanged(k);
            panelDebounceHandler.postDelayed(panelDebounceRunnable, PANEL_DEBOUNCE_MS);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        String cid = "eb_core_features";
        NotificationChannel c = new NotificationChannel(cid, "Core Features", NotificationManager.IMPORTANCE_LOW);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("EdgeBar Core")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true).build();
        startForeground(88, n);

        // isAnyMode=false -> luôn dùng TYPE_APPLICATION_OVERLAY (SYSTEM_ALERT_WINDOW),
        // không phụ thuộc AccessibilityService còn sống hay không.
        panelEngine = new PanelEngine(this, wm, prefs, false);
        bubbleEngine = new AssistiveBubbleEngine(this, wm, prefs, false);
        panelEngine.rebuildAll();
        bubbleEngine.rebuild();
        prefs.registerOnSharedPreferenceChangeListener(coreFeaturesPrefListener);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String act = i.getAction();
                if ("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED".equals(act)) {
                    panelEngine.rebuildAll();
                } else if ("com.manhmoc.edgebar.OPEN_PANEL_REQUEST".equals(act)) {
                    String id = i.getStringExtra("panel_id");
                    if (id != null) panelEngine.togglePanel(id);
                } else if ("com.manhmoc.edgebar.PANEL_TEST_TOGGLE".equals(act)) {
                    String id = i.getStringExtra("panel_id");
                    if (id != null) panelEngine.setForceTest(id, i.getBooleanExtra("on", false));
                                } else if ("com.manhmoc.edgebar.SYNC_STATE".equals(act)
                        || Intent.ACTION_SCREEN_ON.equals(act)
                        || Intent.ACTION_USER_PRESENT.equals(act)
                        || Intent.ACTION_SCREEN_OFF.equals(act)) {
                    panelEngine.rebuildAll(); // Panel tự đọc KeyguardManager để show/hide đúng
                } else if ("com.manhmoc.edgebar.BUBBLE_SET_TOUCHABLE".equals(act)) {
                    bubbleEngine.setBubbleTouchable(i.getBooleanExtra("touchable", true));
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("com.manhmoc.edgebar.BUBBLE_SET_TOUCHABLE"); 
        f.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
        f.addAction("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
        f.addAction("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
        f.addAction("com.manhmoc.edgebar.SYNC_STATE");
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

        @Override public void onDestroy() {
        isRunning = false;
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        prefs.unregisterOnSharedPreferenceChangeListener(coreFeaturesPrefListener);
        if (bubbleEngine != null) bubbleEngine.destroy();
        super.onDestroy();
    }
}
EOF      
# 22. TẠO ShortcutScanner.java MỚI 
# ==============================================================================
echo -e "${YELLOW}👉 TẠO ShortcutScanner.java MỚI ${NC}"
cat << 'EOF' > app/src/main/java/com/manhmoc/edgebar/ShortcutScanner.java
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
