package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
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
    private boolean taskViewSeen = false;
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

    public boolean wantsScrollEvents() { return recentsVisible; }

    public void onEvent(AccessibilityEvent ev) {
        if (!isEnabled()) { if (cover != null || recentsVisible) hide(); return; }
        int t = ev.getEventType();
        String p = ev.getPackageName() != null ? ev.getPackageName().toString() : "";
        boolean isLauncher = p.contains("launcher") || p.contains("quickstep");
        if (isLauncher) {
            h.removeCallbacks(scanRunnable);
            h.postDelayed(scanRunnable, t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ? 200 : 90);
        } else if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !p.isEmpty()
                && !p.contains("systemui") && !p.contains("inputmethod") && !p.equals(svc.getPackageName())) {
            hide(); // đã vào 1 app thật -> rời Recents
        }
    }

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

    private void scan() {
        if (!isEnabled()) { hide(); return; }
        rebuildLabelsIfNeeded();
        if (pkgToLabel.isEmpty()) { hide(); return; }
        hits.clear(); taskViewSeen = false;
        AccessibilityNodeInfo root = null;
        try {
            root = svc.getRootInActiveWindow();
            if (root != null) collect(root, 0);
        } catch (Exception ignored) {
        } finally { if (root != null) root.recycle(); }

        if (taskViewSeen != recentsVisible) {
            recentsVisible = taskViewSeen;
            if (onSubscriptionChanged != null) onSubscriptionChanged.run();
        }
        if (hits.isEmpty()) { removeCover(); return; }
        showCover();
    }

    private void collect(AccessibilityNodeInfo n, int depth) {
        if (depth > 14) return;
        CharSequence cn = n.getClassName();
        if (cn != null && cn.toString().contains("TaskView")) {
            taskViewSeen = true;
            if (n.isVisibleToUser()) {
                CharSequence d = n.getContentDescription();
                if (d != null) {
                    String low = d.toString().toLowerCase(Locale.ROOT);
                    for (Map.Entry<String, String> e : pkgToLabel.entrySet()) {
                        if (low.contains(e.getValue())) {
                            Rect r = new Rect(); n.getBoundsInScreen(r);
                            hits.add(new Object[]{new RectF(r), e.getKey()});
                            break;
                        }
                    }
                }
            }
            return; // không đi sâu vào trong thẻ
        }
        int c = n.getChildCount();
        for (int i = 0; i < c; i++) {
            AccessibilityNodeInfo ch = n.getChild(i);
            if (ch == null) continue;
            collect(ch, depth + 1);
            ch.recycle();
        }
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
