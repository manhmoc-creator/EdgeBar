package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.graphics.*;
import android.graphics.drawable.Drawable; import android.graphics.drawable.GradientDrawable;
import android.os.*; import android.view.*; import android.view.animation.*; import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class PanelEngine {
    private Context ctx; private WindowManager wm; private SharedPreferences prefs;
    private boolean isAnyMode; // true = EdgeBarService (Lock+Homacc), false = HomescreenService (Homeb)
    private KeyguardManager km;

    private View[] handles = new View[3];
    private LinearLayout[] panels = new LinearLayout[3];
    private boolean[] panelOpen = new boolean[3];
    private final AtomicInteger[] renderGen = {new AtomicInteger(0), new AtomicInteger(0), new AtomicInteger(0)};
    private static final int ICON_CACHE_LIMIT = 80;
    private static final LinkedHashMap<String, Drawable> iconCache =
        new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) { return size() > ICON_CACHE_LIMIT; }
        };
    private static final java.util.Map<String,String> labelCache = new java.util.HashMap<>();
    private static final java.util.Map<String,String> ACT_LABEL_MAP = new java.util.HashMap<>();
    static {
        ACT_LABEL_MAP.put("FLASH","Đèn pin"); ACT_LABEL_MAP.put("SCREEN_OFF","Tắt màn hình");
        ACT_LABEL_MAP.put("SCREENSHOT","Chụp màn hình"); ACT_LABEL_MAP.put("CAMERA","Camera");
        ACT_LABEL_MAP.put("VOLUME","Âm lượng"); ACT_LABEL_MAP.put("NOTIFICATIONS","Thông báo");
        ACT_LABEL_MAP.put("BACK","Quay lại"); ACT_LABEL_MAP.put("HOME","Màn chính");
        ACT_LABEL_MAP.put("RECENTS","Đa nhiệm"); ACT_LABEL_MAP.put("VOICE_RECORD","Ghi âm");
        ACT_LABEL_MAP.put("TOGGLE_MORSE","Khóa Morse");
    }

    public PanelEngine(Context ctx, WindowManager wm, SharedPreferences prefs, boolean isAnyMode) {
        this.ctx = ctx; this.wm = wm; this.prefs = prefs; this.isAnyMode = isAnyMode;
        this.km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        for (int i = 0; i < 3; i++) buildPanelIfEnabled(i);
    }

    /** Gọi mỗi khi lock state / accessibility state đổi — decide xem instance này
     *  (Lock hay Homacc, tùy trạng thái) có được phép giữ panel hay không. */
    public void rebuildAll() { for (int i = 0; i < 3; i++) rebuildOne(i); }
    private void rebuildOne(int idx) { removePanel(idx); buildPanelIfEnabled(idx); }

    private boolean shouldOwnPanelNow() {
        if (!isAnyMode) return true; // Homeb: luôn được phép (chỉ cần unlock, check riêng bên dưới)
        // isAnyMode=true dùng chung cho Lock + Homacc trong EdgeBarService.
        // Loại trừ lẫn nhau y hệt logic bars/corners: locked -> Lock giữ panel,
        // unlocked + Homacc chạy -> Homacc giữ panel. Không bao giờ cả hai cùng lúc.
        return true; // panel Lock vs Homacc tự phân biệt qua px+"owner" bên dưới nếu cần mở rộng
    }

    private void buildPanelIfEnabled(int idx) {
        String px = "panel" + (idx+1) + "_";
        if (!prefs.getBoolean(px+"en", false)) return;
        int visMode = prefs.getInt(px+"vis", 0); // 0=Cục Bộ(Homeb), 1=Toàn Cục(Lock/Homacc)
        boolean shouldBuildHere = isAnyMode ? (visMode == 1) : (visMode == 0);
        if (!shouldBuildHere) return;

        boolean locked = km != null && km.isKeyguardLocked();
        if (isAnyMode) {
            // Toàn Cục: khi màn khoá -> panel chạy trong ngữ cảnh Lock; khi mở khoá -> Homacc.
            // Cả hai đều dùng chính EdgeBarService/panelEngine này nên không có xung đột 2 view.
            if (!locked && !AccessibleHomeService.isRunning) return; // chưa unlock xong AccHome thì chưa vẽ
        } else {
            // Cục Bộ (Homeb): chỉ vẽ khi KHÔNG khoá máy, giống bars Homeb hiện tại
            if (locked) return;
        }

        int pos = prefs.getInt(px+"pos", 0);
        int colorIdx = prefs.getInt(px+"color_idx", 0);
        int size = prefs.getInt(px+"size", 700);
        int thick = prefs.getInt(px+"thick", 40);
        int alpha = prefs.getInt(px+"alpha", 200);
        int handleAlpha = prefs.getInt(px+"handle_alpha", 255);
        float handleR = prefs.getInt(px+"handle_radius", 28);
        float panelR = prefs.getInt(px+"panel_radius", 24);
        int color = parsePanelColor(colorIdx);
        String edge = posToEdge(pos);
        int gravity = posToGravity(pos);
        // isAnyMode -> vẽ bằng TYPE_ACCESSIBILITY_OVERLAY (sống được cả khi khoá máy)
        // !isAnyMode -> TYPE_APPLICATION_OVERLAY (chỉ cần SYSTEM_ALERT_WINDOW, không cần accessibility)
        int wmType = isAnyMode
            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        View handle = new View(ctx);
        GradientDrawable hgd = new GradientDrawable();
        hgd.setColor(Color.argb(handleAlpha, Color.red(color), Color.green(color), Color.blue(color)));
        float[] hr = edge.equals("left") ? new float[]{0,0,handleR,handleR,handleR,handleR,0,0}
            : edge.equals("right") ? new float[]{handleR,handleR,0,0,0,0,handleR,handleR}
            : new float[]{handleR,handleR,handleR,handleR,0,0,0,0};
        hgd.setCornerRadii(hr);
        handle.setBackground(hgd);
        int MIN_TOUCH_PX = 90;
        int visualThick = Math.max(20, thick / 3);
        int hw = edge.equals("bottom") ? 200 : Math.max(MIN_TOUCH_PX, visualThick);
        int hh = edge.equals("bottom") ? Math.max(MIN_TOUCH_PX, visualThick) : 200;
        int padExtra = Math.max(0, (MIN_TOUCH_PX - visualThick) / 2);
        if (edge.equals("bottom")) handle.setPadding(0, padExtra, 0, padExtra);
        else handle.setPadding(padExtra, 0, padExtra, 0);

        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(hw, hh, wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        hp.gravity = gravity;
        try { wm.addView(handle, hp); handles[idx] = handle; } catch (Exception e) { return; }
        final int fIdx = idx;
        handle.setOnClickListener(v -> togglePanel(fIdx));

        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pgd = new GradientDrawable();
        pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        pgd.setStroke(4, Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
        pgd.setCornerRadius(panelR);
        panel.setBackground(pgd);
        panel.setVisibility(View.GONE);

        int itemCount = csvToList(prefs.getString(px+"apps","")).size() + csvToList(prefs.getString(px+"acts","")).size();
        int cols = Math.max(1, prefs.getInt(px+"cols", 4));
        int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
        int iconSize = prefs.getInt(px+"icon_size", 110);
        int cellPx = iconSize + 40;
        int computedCross = Math.min(size, (edge.equals("bottom") ? rows : cols) * cellPx + 80);
        int computedMain  = (edge.equals("bottom") ? cols : rows) * cellPx + 80;
        int pw = edge.equals("bottom") ? Math.min(computedMain, ctx.getResources().getDisplayMetrics().widthPixels) : computedCross;
        int ph = edge.equals("bottom") ? computedCross : Math.min(computedMain, ctx.getResources().getDisplayMetrics().heightPixels);

        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(pw, ph, wmType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        pp.gravity = edge.equals("left") ? (Gravity.LEFT|Gravity.CENTER_VERTICAL)
                   : edge.equals("right") ? (Gravity.RIGHT|Gravity.CENTER_VERTICAL)
                   : (Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        try { wm.addView(panel, pp); panels[idx] = panel; } catch (Exception e) { return; }
        panel.setOnTouchListener((v,e) -> { closePanel(fIdx); return true; });

        renderPanelGrid(idx);
    }

    private void renderPanelGrid(int idx) {
        String px = "panel" + (idx+1) + "_";
        LinearLayout panel = panels[idx];
        if (panel == null) return;
        final int myGen = renderGen[idx].incrementAndGet();
        int cols = prefs.getInt(px+"cols", 4);
        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(cols);
        grid.setPadding(30, 40, 30, 40);
        panel.addView(grid);
        List<String> apps = csvToList(prefs.getString(px+"apps", ""));
        List<String> acts = csvToList(prefs.getString(px+"acts", ""));
        new Thread(() -> {
            List<Object[]> loaded = new ArrayList<>();
            for (String pkg : apps) {
                if (renderGen[idx].get() != myGen) return;
                Drawable d = getCachedIcon(pkg);
                if (d != null) loaded.add(new Object[]{d, "APP", pkg});
            }
            if (renderGen[idx].get() != myGen) return;
            for (String act : acts) loaded.add(new Object[]{null, "ACT", act});
            if (renderGen[idx].get() != myGen) return;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (renderGen[idx].get() != myGen || panels[idx] != panel) return;
                for (Object[] item : loaded) {
                    View cell = buildCell(px, (String) item[1], item[0], (String) item[2]);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = 130; lp.height = 130; lp.setMargins(12,12,12,12);
                    cell.setLayoutParams(lp);
                    grid.addView(cell);
                }
            });
        }).start();
    }

    private Drawable getCachedIcon(String pkg) {
        synchronized (iconCache) { Drawable c = iconCache.get(pkg); if (c != null) return c; }
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            synchronized (iconCache) { iconCache.put(pkg, d); }
            return d;
        } catch (Exception e) { return null; }
    }

    private String getCachedAppLabel(String pkg) {
        String c = labelCache.get(pkg); if (c != null) return c;
        try {
            String l = ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(pkg,0)).toString();
            labelCache.put(pkg, l); return l;
        } catch (Exception e) { return pkg; }
    }
    private String getActionLabelForPanel(String key) { String l = ACT_LABEL_MAP.get(key); return l != null ? l : key; }
    private float getPanelIconRadiusPercent(String px) {
        int shape = prefs.getInt(px + "icon_shape", 0);
        switch (shape) { case 1: return 0.28f; case 2: return 0.5f; case 3: return 0.12f; default: return 0.5f; }
    }

    private View wrapIconCell(String px, Drawable icon, String emoji, float radiusPct, View.OnClickListener onClick, String label) {
        int iconSize = prefs.getInt(px + "icon_size", 110);
        LinearLayout box = new LinearLayout(ctx); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        View core;
        if (icon != null) { ImageView iv = new ImageView(ctx); iv.setImageDrawable(icon); core = iv; }
        else {
            TextView tv = new TextView(ctx); tv.setText(emoji); tv.setTextSize(26); tv.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(180,96,125,139)); tv.setBackground(bg);
            core = tv;
        }
        core.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        core.setClipToOutline(true);
        core.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) {
                int w = v.getWidth()==0?110:v.getWidth(), h = v.getHeight()==0?110:v.getHeight();
                o.setRoundRect(0,0,w,h, w*radiusPct);
            }
        });
        box.addView(core);
        boolean showName = prefs.getInt(px + "show_name", 0) == 1;
        if (showName && label != null) {
            TextView tvLabel = new TextView(ctx); tvLabel.setText(label); tvLabel.setTextColor(Color.WHITE);
            tvLabel.setTextSize(9); tvLabel.setMaxLines(1); tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvLabel.setGravity(Gravity.CENTER); box.addView(tvLabel);
        }
        box.setOnClickListener(onClick);
        return box;
    }

    private View buildCell(String px, String type, Object payload, String ref) {
        float radius = getPanelIconRadiusPercent(px);
        if (type.equals("APP")) {
            return wrapIconCell(px, (Drawable) payload, null, radius, v -> {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(ref);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
                closeAllPanels();
            }, getCachedAppLabel(ref));
        } else {
            String label = getActionLabelForPanel(ref);
            return wrapIconCell(px, null, actEmoji(ref), radius, v -> {
                Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                ipc.putExtra("act", ref);
                ctx.sendBroadcast(ipc);
                closeAllPanels();
            }, label);
        }
    }

    private String actEmoji(String key) {
        switch (key) {
            case "FLASH": return "🔦"; case "SCREEN_OFF": return "📴"; case "SCREENSHOT": return "📸";
            case "CAMERA": return "📷"; case "VOLUME": return "🔊"; case "NOTIFICATIONS": return "🔔";
            case "BACK": return "⬅️"; case "HOME": return "🏠"; case "RECENTS": return "🗂️";
            case "VOICE_RECORD": return "🎙️"; case "TOGGLE_MORSE": return "🔐"; default: return "⚡";
        }
    }

    private List<String> csvToList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
    private int parsePanelColor(int idx) {
        String[] hex = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
        try { return Color.parseColor(hex[Math.max(0, Math.min(hex.length-1, idx))]); }
        catch (Exception e) { return Color.parseColor("#607D8B"); }
    }
    private String posToEdge(int pos) { if (pos <= 2) return "bottom"; if (pos <= 5) return "left"; return "right"; }
    private int posToGravity(int pos) {
        switch (pos) {
            case 0: return Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; case 1: return Gravity.BOTTOM|Gravity.LEFT;
            case 2: return Gravity.BOTTOM|Gravity.RIGHT; case 3: return Gravity.LEFT|Gravity.TOP;
            case 4: return Gravity.LEFT|Gravity.CENTER_VERTICAL; case 5: return Gravity.LEFT|Gravity.BOTTOM;
            case 6: return Gravity.RIGHT|Gravity.TOP; case 7: return Gravity.RIGHT|Gravity.CENTER_VERTICAL;
            default: return Gravity.RIGHT|Gravity.BOTTOM;
        }
    }

    public void togglePanel(int idx) {
        if (handles[idx] == null && panels[idx] == null) buildPanelIfEnabled(idx);
        if (panelOpen[idx]) closePanel(idx); else openPanel(idx);
    }
    private void openPanel(int idx) {
        if (panels[idx] == null) return;
        panelOpen[idx] = true;
        panels[idx].setVisibility(View.VISIBLE);
        if (handles[idx] != null) handles[idx].setVisibility(View.GONE);
        String px = "panel" + (idx+1) + "_";
        String edge = posToEdge(prefs.getInt(px+"pos", 0));
        Animation anim = edge.equals("bottom")
            ? new TranslateAnimation(0,0, prefs.getInt(px+"size",500), 0)
            : new TranslateAnimation(edge.equals("left") ? -prefs.getInt(px+"size",500) : prefs.getInt(px+"size",500), 0, 0, 0);
        anim.setDuration(200);
        panels[idx].startAnimation(anim);
    }
    private void closePanel(int idx) {
        if (panels[idx] == null || !panelOpen[idx]) return;
        panelOpen[idx] = false;
        panels[idx].setVisibility(View.GONE);
        if (handles[idx] != null) handles[idx].setVisibility(View.VISIBLE);
    }
    private void closeAllPanels() { for (int i=0;i<3;i++) closePanel(i); }
    private void removePanel(int idx) {
        renderGen[idx].incrementAndGet();
        try { if (handles[idx] != null) wm.removeView(handles[idx]); } catch (Exception ignored) {}
        try { if (panels[idx] != null) wm.removeView(panels[idx]); } catch (Exception ignored) {}
        handles[idx] = null; panels[idx] = null; panelOpen[idx] = false;
    }
}
