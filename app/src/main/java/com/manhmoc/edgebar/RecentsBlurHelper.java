package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Phủ lớp mờ lên thẻ app trong Recents (Quickstep TaskView). Event-driven, 1 cửa sổ duy nhất. */
public class RecentsBlurHelper {
    private final AccessibilityService svc;
    private final WindowManager wm;
    private final SharedPreferences prefs;
    private final Runnable onSubscriptionChanged;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable scanRunnable = this::scan;

    private CoverView cover;
    private boolean recentsVisible = false;
    private String lastSig = "";
    private final Map<String, String> pkgToLabel = new HashMap<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final List<Object[]> hits = new ArrayList<>(); // {RectF, pkg}

    public RecentsBlurHelper(AccessibilityService svc, WindowManager wm, SharedPreferences prefs, Runnable onSubscriptionChanged) {
        this.svc = svc; this.wm = wm; this.prefs = prefs; this.onSubscriptionChanged = onSubscriptionChanged;
    }

    private boolean isEnabled() {
        if (!prefs.getString("recents_blur_list", "").isEmpty()) return true;
        return prefs.getBoolean("recents_blur_locklist_en", false) && !prefs.getString("applock_list", "").isEmpty();
    }

// [FIX] Subscribe ngay khi feature bật — không chờ recentsVisible=true nữa
public boolean wantsScrollEvents() { return isEnabled(); }

    private void rebuildLabelsIfNeeded() {
        String a = prefs.getString("recents_blur_list", "");
        String b = prefs.getBoolean("recents_blur_locklist_en", false) ? prefs.getString("applock_list", "") : "";
        String sig = a + "|" + b;
        if (sig.equals(lastSig)) return;
        lastSig = sig; pkgToLabel.clear(); iconCache.clear();
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
    private boolean scanPending = false;
    private int discoveryRetries = 0;

    private boolean isLauncherPkg(String p) {
        return p.contains("launcher") || p.contains("quickstep") || p.contains("recents");
    }

    private void requestScan(long delayMs) {
        if (scanPending) return;          // throttle, không debounce -> cover bám theo khi vuốt
        scanPending = true;
        h.postDelayed(scanRunnable, delayMs);
    }

    public void onEvent(AccessibilityEvent ev) {
        if (!isEnabled()) { if (cover != null || recentsVisible) hide(); return; }
        int t = ev.getEventType();
        String p = ev.getPackageName() != null ? ev.getPackageName().toString() : "";
        boolean launcher = isLauncherPkg(p);
        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || t == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (launcher || p.isEmpty()) { discoveryRetries = 3; requestScan(150); }
            else if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !p.contains("systemui") && !p.contains("inputmethod")
                    && !p.equals(svc.getPackageName())) hide();
        } else if (launcher && (t == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || t == AccessibilityEvent.TYPE_VIEW_SCROLLED)) {
            requestScan(80);
        }
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

    private void addHit(RectF r, String pkg) {
        for (Object[] it : hits) {
            if (!pkg.equals(it[1])) continue;
            RectF ex = (RectF) it[0];
            if (ex.contains(r)) return;
            if (r.contains(ex)) { it[0] = r; return; }
        }
        hits.add(new Object[]{r, pkg});
    }

    private void scan() {
        scanPending = false;
        if (!isEnabled()) { hide(); return; }
        rebuildLabelsIfNeeded();
        if (pkgToLabel.isEmpty()) { hide(); return; }
        android.os.PowerManager pw = (android.os.PowerManager) svc.getSystemService(Context.POWER_SERVICE);
        if (pw != null && !pw.isInteractive()) { removeCover(); return; }

        hits.clear();
        android.util.DisplayMetrics dm = svc.getResources().getDisplayMetrics();
        int minCard = Math.round(Math.min(dm.widthPixels, dm.heightPixels) * 0.30f); // thẻ Recents cao >> icon
        Rect r = new Rect();
        try {
            List<android.view.accessibility.AccessibilityWindowInfo> windows = svc.getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo w : windows) {
                    if (w.getType() != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo root = w.getRoot();
                    if (root == null) continue;
                    CharSequence rp = root.getPackageName();
                    if (rp != null && isLauncherPkg(rp.toString())) {
                        for (Map.Entry<String, String> e : pkgToLabel.entrySet()) {
                            List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(e.getValue());
                            if (found == null) continue;
                            for (AccessibilityNodeInfo nd : found) {
                                CharSequence cs = nd.getContentDescription();
                                if (cs == null || cs.length() == 0) cs = nd.getText();
                                if (matchesLabel(cs, e.getValue())) {
                                    nd.getBoundsInScreen(r);
                                    boolean full = r.width() >= dm.widthPixels * 0.95f
                                                && r.height() >= dm.heightPixels * 0.9f;
                                    if (r.width() > 80 && r.height() >= minCard && !full)
                                        addHit(new RectF(r), e.getKey());
                                }
                                nd.recycle();
                            }
                        }
                    }
                    root.recycle();
                }
            }
        } catch (Exception ignored) {}

        boolean nowVisible = !hits.isEmpty();
        if (nowVisible != recentsVisible) {
            recentsVisible = nowVisible;
            if (onSubscriptionChanged != null) onSubscriptionChanged.run();
        }
        if (hits.isEmpty()) {
            removeCover();
            // content-description của thẻ nạp bất đồng bộ -> thử lại tối đa 3 lần
            if (discoveryRetries > 0) { discoveryRetries--; requestScan(300); }
        } else showCover();
    }

    private void showCover() {
        if (cover == null) {
            cover = new CoverView(svc);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            try { wm.addView(cover, lp); } catch (Exception e) { cover = null; return; }
        }
        cover.setItems(hits);
    }

    private void removeCover() {
        if (cover == null) return;
        try { wm.removeView(cover); } catch (Exception ignored) {}
        cover = null;
    }

    public void hide() {
        h.removeCallbacks(scanRunnable);
        scanPending = false; discoveryRetries = 0;

        removeCover();
        if (recentsVisible) {
            recentsVisible = false;
            if (onSubscriptionChanged != null) onSubscriptionChanged.run();
        }
    }

    public void destroy() { hide(); iconCache.clear(); pkgToLabel.clear(); }

    private Drawable getIcon(String pkg) {
        Drawable d = iconCache.get(pkg);
        if (d != null) return d;
        try { d = svc.getPackageManager().getApplicationIcon(pkg); iconCache.put(pkg, d); } catch (Exception ignored) {}
        return d;
    }

    private class CoverView extends View {
        private final List<Object[]> items = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tmp = new RectF();
        private final int[] loc = new int[2];
        CoverView(android.content.Context c) { super(c); }
        void setItems(List<Object[]> src) { items.clear(); items.addAll(src); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            getLocationOnScreen(loc);
            p.setColor(Color.argb(prefs.getInt("recents_blur_alpha", 235), 32, 33, 36));
            for (Object[] it : items) {
                RectF r = (RectF) it[0];
                tmp.set(r.left - loc[0], r.top - loc[1], r.right - loc[0], r.bottom - loc[1]);
                c.drawRoundRect(tmp, 36f, 36f, p);
                Drawable d = getIcon((String) it[1]);
                if (d != null) {
                    int s = (int) Math.min(160f, Math.min(tmp.width(), tmp.height()) * 0.3f);
                    int cx = (int) tmp.centerX(), cy = (int) tmp.centerY();
                    d.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                    d.draw(c);
                }
            }
        }
    }
}
