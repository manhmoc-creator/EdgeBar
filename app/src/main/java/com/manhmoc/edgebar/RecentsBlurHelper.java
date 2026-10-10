package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecentsBlurHelper {
    private static final long SCAN_STEP_MS = 100;
    private static final int  SCAN_RETRIES = 6;          // ~600ms chờ thẻ Recents dựng xong
    private static final int  MISS_TO_HIDE = 3;          // 3 lần quét liên tiếp không thấy Recents -> gỡ
    private static final long BITMAP_FREE_DELAY_MS = 20000;

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Runnable onSubscriptionChanged;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Map<String, String> pkgToLabel = new HashMap<>();
    private String lastSig = "";

    private String lastAppPkg = "";      // app thường ở foreground gần nhất
    private String originPkg = "";       // app đứng trước khi vào launcher
    private boolean launcherActive = false;
    private boolean sessionActive = false; // origin thuộc danh sách -> đang theo dõi
    private boolean covered = false;
    private boolean scanScheduled = false;
    private int retriesLeft = 0, missCount = 0;

    private CoverView cover;
    private Bitmap coverBmp;
    private String coverBmpKey = "";
    private boolean decoding = false;
    private final ExecutorService decodeExec = Executors.newSingleThreadExecutor();

    private final Runnable scanRunnable = () -> { scanScheduled = false; scan(); };
    private final Runnable freeBmpRunnable = () -> {
        if (!covered) { coverBmp = null; coverBmpKey = ""; }
    };

    public RecentsBlurHelper(AccessibilityService svc, WindowManager wm, SharedPreferences prefs, Runnable onSubscriptionChanged) {
        this.svc = svc; this.wm = wm; this.prefs = prefs; this.onSubscriptionChanged = onSubscriptionChanged;
    }

    private boolean isEnabled() {
        if (!prefs.getString("recents_blur_list", "").isEmpty()) return true;
        return prefs.getBoolean("recents_blur_locklist_en", false) && !prefs.getString("applock_list", "").isEmpty();
    }

    /** Chỉ nhận CONTENT_CHANGED/SCROLLED trong lúc có phiên Recents của app Blurlist. */
    public boolean wantsScrollEvents() { return sessionActive && isEnabled(); }

    private boolean isLauncherPkg(String p) {
        return p.contains("launcher") || p.contains("quickstep") || p.contains("recents");
    }
    private boolean isNoise(String p) {
        return p.isEmpty() || p.contains("systemui") || p.contains("inputmethod")
            || p.equals("android") || p.equals(svc.getPackageName());
    }

    private void rebuildLabelsIfNeeded() {
        String a = prefs.getString("recents_blur_list", "");
        String b = prefs.getBoolean("recents_blur_locklist_en", false) ? prefs.getString("applock_list", "") : "";
        String sig = a + "|" + b;
        if (sig.equals(lastSig)) return;
        lastSig = sig; pkgToLabel.clear();
        PackageManager pm = svc.getPackageManager();
        for (String csv : new String[]{a, b}) {
            for (String pk : csv.split(",")) {
                String t = pk.trim();
                if (t.isEmpty() || pkgToLabel.containsKey(t)) continue;
                try {
                    String lb = pm.getApplicationLabel(pm.getApplicationInfo(t, 0)).toString().toLowerCase(Locale.ROOT);
                    if (!lb.isEmpty()) pkgToLabel.put(t, lb);
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== SỰ KIỆN ====================
    public void onEvent(AccessibilityEvent ev) {
        if (!isEnabled()) { if (cover != null || sessionActive || launcherActive) reset(); return; }
        int t = ev.getEventType();
        CharSequence pc = ev.getPackageName();
        String p = pc != null ? pc.toString() : "";

        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isLauncherPkg(p)) {
                if (!launcherActive) {              // app -> launcher: chốt "app xuất phát"
                    launcherActive = true;
                    originPkg = lastAppPkg;
                    lastAppPkg = "";
                    rebuildLabelsIfNeeded();
                    setSession(pkgToLabel.containsKey(originPkg));
                }
                if (sessionActive) { retriesLeft = SCAN_RETRIES; missCount = 0; scheduleScan(0); }
            } else if (!isNoise(p)) {               // 1 app thật lên foreground -> gỡ NGAY
                launcherActive = false;
                lastAppPkg = p;
                originPkg = "";
                if (sessionActive || cover != null) endSession();
            }
        } else if (sessionActive
                && ((t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && isLauncherPkg(p))
                    || t == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            scheduleScan(covered ? 250 : 150);      // dùng để phát hiện "đã thoát Recents về Home"
        }
    }

    private void setSession(boolean on) {
        if (sessionActive == on) return;
        sessionActive = on;
        if (onSubscriptionChanged != null) onSubscriptionChanged.run();
    }

    private void endSession() {
        originPkg = "";
        setSession(false);
        removeCover();
    }

    private void scheduleScan(long delay) {
        if (scanScheduled) return;
        scanScheduled = true;
        h.postDelayed(scanRunnable, delay);
    }

    // ==================== QUÉT: ĐANG Ở RECENTS HAY Ở HOME? ====================
    private void scan() {
        if (!sessionActive || !isEnabled()) return;
        android.os.PowerManager pw = (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
        if (pw != null && !pw.isInteractive()) { endSession(); return; }

        if (findRecentsCard()) {
            missCount = 0;
            if (!covered) showCover();
            return;
        }
        if (covered) {
            if (++missCount >= MISS_TO_HIDE) endSession(); else scheduleScan(180);
        } else if (retriesLeft-- > 0) {
            scheduleScan(SCAN_STEP_MS);
        } else {
            endSession();                           // launcher nhưng không có thẻ = Home, không phải Recents
        }
    }

    private boolean findRecentsCard() {
        android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
        int minCard = Math.round(Math.min(dm.widthPixels, dm.heightPixels) * 0.30f);
        Rect r = new Rect();
        try {
            List<AccessibilityWindowInfo> ws = svc.getWindows();
            if (ws == null) return false;
            for (AccessibilityWindowInfo w : ws) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                boolean hit = false;
                CharSequence rp = root.getPackageName();
                if (rp != null && isLauncherPkg(rp.toString())) {
                    String originLabel = pkgToLabel.get(originPkg);
                    hit = originLabel != null && matchCard(root, originLabel, dm, minCard, r);
                    if (!hit) for (String lb : pkgToLabel.values()) {
                        if (matchCard(root, lb, dm, minCard, r)) { hit = true; break; }
                    }
                }
                root.recycle();
                if (hit) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean matchCard(AccessibilityNodeInfo root, String label, android.util.DisplayMetrics dm, int minCard, Rect r) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(label);
        if (found == null) return false;
        boolean hit = false;
        for (AccessibilityNodeInfo nd : found) {
            if (!hit) {
                CharSequence cs = nd.getContentDescription();
                if (cs == null || cs.length() == 0) cs = nd.getText();
                if (matchesLabel(cs, label)) {
                    nd.getBoundsInScreen(r);
                    boolean full = r.width() >= dm.widthPixels * 0.95f && r.height() >= dm.heightPixels * 0.9f;
                    if (r.width() > 80 && r.height() >= minCard && !full) hit = true;
                }
            }
            nd.recycle();
        }
        return hit;
    }

    private boolean matchesLabel(CharSequence cs, String label) {
        if (cs == null || cs.length() == 0) return false;
        String s = cs.toString().toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int i = s.indexOf(label, from);
            if (i < 0) return false;
            int e = i + label.length();
            boolean okL = i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1));
            boolean okR = e >= s.length() || !Character.isLetterOrDigit(s.charAt(e));
            if (okL && okR) return true;
            from = i + 1;
        }
    }

    // ==================== LỚP PHỦ TĨNH ====================
    private void showCover() {
        if (cover == null) {
            cover = new CoverView(svc);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // chạm xuyên xuống Recents
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            try { wm.addView(cover, lp); } catch (Exception e) { cover = null; return; }
        }
        covered = true;
        h.removeCallbacks(freeBmpRunnable);
        ensureBitmap();
        cover.invalidate();
    }

    private void removeCover() {
        covered = false;
        if (cover != null) { try { wm.removeView(cover); } catch (Exception ignored) {} cover = null; }
        h.removeCallbacks(freeBmpRunnable);
        if (coverBmp != null) h.postDelayed(freeBmpRunnable, BITMAP_FREE_DELAY_MS); // nhả RAM ảnh sau 20s
    }

    private void ensureBitmap() {
        String uriStr = prefs.getString("recents_blur_image_uri", "");
        if (uriStr.isEmpty()) { coverBmp = null; coverBmpKey = ""; return; }
        if (uriStr.equals(coverBmpKey) && coverBmp != null) return;
        if (decoding) return;
        decoding = true;
        final String key = uriStr;
        decodeExec.execute(() -> {
            Bitmap b = decodeScaled(Uri.parse(key));
            h.post(() -> {
                decoding = false;
                coverBmp = b; coverBmpKey = b != null ? key : "";
                if (cover != null) cover.invalidate();
            });
        });
    }

    /** Giải mã ở ~1/3 độ phân giải màn hình + RGB_565 -> ~1.5MB, đủ cho lớp phủ. */
    private Bitmap decodeScaled(Uri uri) {
        try {
            android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
            int target = Math.max(dm.widthPixels, dm.heightPixels) / 3;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream is = svc.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(is, null, o); }
            int s = 1, maxSide = Math.max(o.outWidth, o.outHeight);
            while (maxSide / (s * 2) >= target) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream is2 = svc.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(is2, null, o2);
            }
        } catch (Exception e) { return null; }
    }

    private class CoverView extends View {
        private final Paint base = new Paint();
        private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final RectF dst = new RectF();
        CoverView(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            if (w <= 0 || hh <= 0) return;
            int a = Math.max(0, Math.min(255, prefs.getInt("recents_blur_alpha", 235)));
            base.setColor(Color.argb(a, 24, 24, 26));
            c.drawRect(0, 0, w, hh, base);
            Bitmap b = coverBmp;
            if (b != null && !b.isRecycled()) {
                float s = Math.max(w / (float) b.getWidth(), hh / (float) b.getHeight()); // center-crop
                float bw = b.getWidth() * s, bh = b.getHeight() * s;
                dst.set((w - bw) / 2f, (hh - bh) / 2f, (w + bw) / 2f, (hh + bh) / 2f);
                img.setAlpha(a);
                c.drawBitmap(b, null, dst, img);
            }
        }
    }

    // ==================== DỌN DẸP ====================
    private void reset() {
        h.removeCallbacksAndMessages(null);
        scanScheduled = false;
        launcherActive = false; lastAppPkg = ""; originPkg = "";
        boolean was = sessionActive;
        sessionActive = false;
        removeCover();
        if (was && onSubscriptionChanged != null) onSubscriptionChanged.run();
    }

    public void hide() { reset(); }

    public void destroy() {
        reset();
        coverBmp = null; coverBmpKey = "";
        try { decodeExec.shutdownNow(); } catch (Exception ignored) {}
    }
}
