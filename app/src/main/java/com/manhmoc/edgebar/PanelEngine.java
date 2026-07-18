package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.graphics.*;
import android.graphics.drawable.Drawable; import android.graphics.drawable.AdaptiveIconDrawable; import android.graphics.drawable.GradientDrawable;
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
private String[] lastSignature = new String[3];
private boolean[] forceTestOn = new boolean[3]; // Bật/tắt từ checkbox TEST trong màn Design
    private int[] lastIconSizeCache = new int[]{-1,-1,-1};
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
    private void rebuildOne(int idx) {
    String sig = computeSignature(idx);
    boolean shouldExist = shouldPanelExistNow(idx);
    boolean exists = (handles[idx] != null || panels[idx] != null);
    // CHỈ rebuild khi thật sự có thay đổi (bật/tắt hoặc đổi cấu hình) —
    // đây là fix gốc cho lỗi nháy tay cầm mỗi 1-2 giây, đồng thời giảm hẳn
    // số lần gọi wm.addView/removeView -> tiết kiệm pin/CPU cho Pixel 2XL.
    if (exists == shouldExist && sig.equals(lastSignature[idx])) return;
    removePanel(idx);
    if (shouldExist) buildPanelIfEnabled(idx);
    lastSignature[idx] = sig;
}
    /** Gọi từ prefListener khi 1 key panelN_xxx đổi — quyết định rebuild nặng hay update nhẹ. */
    public void onPrefChanged(String key) {
        if (key == null || !key.startsWith("panel") || key.length() < 7) return;
        int idx;
        try { idx = Character.getNumericValue(key.charAt(5)) - 1; } catch (Exception e) { return; }
        if (idx < 0 || idx > 2) return;
        boolean structural = key.endsWith("_apps") || key.endsWith("_acts") || key.endsWith("_cols")
            || key.endsWith("_icon_shape") || key.endsWith("_show_name") || key.endsWith("_en")
            || key.endsWith("_vis") || key.endsWith("_pos") || key.endsWith("_color_idx");
        if (structural) { rebuildOne(idx); return; }
        liveUpdateCosmetic(idx);
    }

    /** Update tại chỗ (KHÔNG removeView/addView) cho opacity/length/width/radius/icon size. */
    private void liveUpdateCosmetic(int idx) {
        View handle = handles[idx]; LinearLayout panel = panels[idx];
        if (handle == null && panel == null) return; // chưa build -> để rebuildAll() lo sau
        String px = "panel" + (idx + 1) + "_";
        int color = parsePanelColor(prefs.getInt(px + "color_idx", 0));
        String edge = posToEdge(prefs.getInt(px + "pos", 0));

        if (handle != null) {
            int handleAlpha = prefs.getInt(px + "handle_alpha", 255);
            float handleR = prefs.getInt(px + "handle_radius", 28);
            int thick = prefs.getInt(px + "thick", 40);
            int handleWidth = prefs.getInt(px + "handle_width", 56);
            GradientDrawable hgd = new GradientDrawable();
            hgd.setColor(Color.argb(handleAlpha, Color.red(color), Color.green(color), Color.blue(color)));
            float[] hr = edge.equals("left") ? new float[]{0,0,handleR,handleR,handleR,handleR,0,0}
                : edge.equals("right") ? new float[]{handleR,handleR,0,0,0,0,handleR,handleR}
                : new float[]{handleR,handleR,handleR,handleR,0,0,0,0};
            hgd.setCornerRadii(hr);
            handle.setBackground(hgd);
            int handleLength = Math.max(80, thick);
            int hw = edge.equals("bottom") ? handleLength : handleWidth;
            int hh = edge.equals("bottom") ? handleWidth : handleLength;
            WindowManager.LayoutParams hp = (WindowManager.LayoutParams) handle.getLayoutParams();
            if (hp.width != hw || hp.height != hh) {
                hp.width = hw; hp.height = hh;
                try { wm.updateViewLayout(handle, hp); } catch (Exception ignored) {}
            }
        }
        if (panel != null) {
            int alpha = prefs.getInt(px + "alpha", 200);
            float panelR = prefs.getInt(px + "panel_radius", 24);
            int size = prefs.getInt(px + "size", 700);
            int iconSize = prefs.getInt(px + "icon_size", 110);

            GradientDrawable pgd = new GradientDrawable();
            pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            pgd.setStroke(4, Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            pgd.setCornerRadius(panelR);
            panel.setBackground(pgd);

            int cols = Math.max(1, prefs.getInt(px + "cols", 4));
            int itemCount = Math.max(1, csvToList(prefs.getString(px+"apps","")).size()
                + csvToList(prefs.getString(px+"acts","")).size());
            int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
            int cellPx = iconSize + CELL_EXTRA;
            int contentMain = (edge.equals("bottom") ? cols : rows) * cellPx + 48;
            int userLength = prefs.getInt(px + "panel_length", contentMain);
            int mainAxis = Math.max(userLength, contentMain);
            boolean showNameOn = prefs.getInt(px+"show_name", 0) == 1;
int labelExtra = showNameOn ? 40 : 0;
int cross = Math.max(size, iconSize + 48 + labelExtra);
            WindowManager.LayoutParams pp = (WindowManager.LayoutParams) panel.getLayoutParams();
            if (edge.equals("bottom")) {
                pp.height = cross;
                pp.width = Math.min(mainAxis, ctx.getResources().getDisplayMetrics().widthPixels);
            } else {
                pp.width = cross;
                pp.height = Math.min(mainAxis, ctx.getResources().getDisplayMetrics().heightPixels);
            }
            try { wm.updateViewLayout(panel, pp); } catch (Exception ignored) {}

            if (lastIconSizeCache[idx] != iconSize) {
                lastIconSizeCache[idx] = iconSize;
                renderPanelGrid(idx); // chỉ vẽ lại grid con, không đụng handle/panel view
            }
        }
    }
private String computeSignature(int idx) {
    String px = "panel" + (idx+1) + "_";
    return prefs.getBoolean(px+"en", false) + "|" + prefs.getInt(px+"vis", 0) + "|"
        + prefs.getInt(px+"pos", 0) + "|" + prefs.getInt(px+"color_idx", 0) + "|"
        + prefs.getInt(px+"size", 700) + "|" + prefs.getInt(px+"thick", 40) + "|"
        + prefs.getInt(px+"alpha", 200) + "|" + prefs.getInt(px+"handle_alpha", 255) + "|"
        + prefs.getInt(px+"handle_radius", 28) + "|" + prefs.getInt(px+"panel_radius", 24) + "|"
        + prefs.getInt(px+"icon_size", 110) + "|" + prefs.getInt(px+"cols", 4) + "|"
        + prefs.getInt(px+"icon_shape", 0) + "|" + prefs.getInt(px+"show_name", 0) + "|"
        + prefs.getString(px+"apps","") + "|" + prefs.getString(px+"acts","") + "|"
        + forceTestOn[idx];
}
private boolean shouldPanelExistNow(int idx) {
    String px = "panel" + (idx+1) + "_";
    if (!prefs.getBoolean(px+"en", false)) return false;

    int visMode = prefs.getInt(px+"vis", 0); // 0 = Cục Bộ, 1 = Toàn Cục

    if (visMode == 0) {
        // Cục Bộ: KHÔNG hiện thật ở Home/Lock — chỉ hiện khi user đang đứng
        // trong tab Design > Panel để xem thử (đồng bộ preview_lock/preview_morse)
        return prefs.getBoolean("preview_panel", false);
    }

    // Toàn Cục: hiện thật ở cả Lock lẫn Home. Panel nội dung không đổi gì cả.
    boolean locked = km != null && km.isKeyguardLocked();
    if (isAnyMode) {
        if (!locked && !AccessibleHomeService.isRunning) return false;
    } else {
        if (locked) return false;
    }
    return true;
}
// Gọi từ Activity (qua broadcast) khi bật/tắt checkbox TEST
public void setForceTest(int idx, boolean on) {
    forceTestOn[idx] = on;
    rebuildOne(idx);
}
    private boolean shouldOwnPanelNow() {
        if (!isAnyMode) return true; // Homeb: luôn được phép (chỉ cần unlock, check riêng bên dưới)
        // isAnyMode=true dùng chung cho Lock + Homacc trong EdgeBarService.
        // Loại trừ lẫn nhau y hệt logic bars/corners: locked -> Lock giữ panel,
        // unlocked + Homacc chạy -> Homacc giữ panel. Không bao giờ cả hai cùng lúc.
        return true; // panel Lock vs Homacc tự phân biệt qua px+"owner" bên dưới nếu cần mở rộng
    }
    private void buildPanelIfEnabled(int idx) {
    String px = "panel" + (idx+1) + "_";
    if (!shouldPanelExistNow(idx)) return;
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
        // Length (thick) = chiều dài tay cầm dọc cạnh; Width (handle_width) = độ dày —
// cả hai giờ do người dùng chỉnh (trước đây width là hằng số cố định 56).
int handleWidth = prefs.getInt(px+"handle_width", 56);
int handleLength = Math.max(80, thick);
int hw = edge.equals("bottom") ? handleLength : handleWidth;
int hh = edge.equals("bottom") ? handleWidth : handleLength;
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
if (itemCount == 0) itemCount = 1;
int cols = Math.max(1, Math.min(prefs.getInt(px+"cols", 4), itemCount));
int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
int iconSize = prefs.getInt(px+"icon_size", 110);
int cellPx = iconSize + CELL_EXTRA;
int panelPadding = 48;
int contentMain = (edge.equals("bottom") ? cols : rows) * cellPx + panelPadding;
// "Length" (panel_length) người dùng chỉnh — không cho nhỏ hơn nội dung cần, tránh cắt icon
int userLength = prefs.getInt(px+"panel_length", contentMain);
int mainAxis = Math.max(userLength, contentMain);
// "size" = Width (bề dày panel), giữ nguyên key cũ để không phá cấu hình đã lưu
boolean showNameOn = prefs.getInt(px+"show_name", 0) == 1;
int labelExtra = showNameOn ? 40 : 0; // chừa thêm chỗ hiển thị tên
int crossFixed = Math.max(size, iconSize + panelPadding + labelExtra);
int pw = edge.equals("bottom") ? Math.min(mainAxis, ctx.getResources().getDisplayMetrics().widthPixels) : crossFixed;
int ph = edge.equals("bottom") ? crossFixed : Math.min(mainAxis, ctx.getResources().getDisplayMetrics().heightPixels);
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

        lastIconSizeCache[idx] = iconSize;
        renderPanelGrid(idx);
    }
    // Hằng số DÙNG CHUNG giữa renderPanelGrid() và buildPanelIfEnabled() —
// đảm bảo kích thước panel tính toán KHỚP 100% với kích thước cell thực vẽ,
// đây chính là fix gốc cho lỗi lệch trái/phải khi đổi icon size.
private static final int CELL_INNER_PAD = 16; // đệm 8px mỗi bên quanh icon
private static final int CELL_MARGIN   = 16;  // margin 8px mỗi bên giữa các cell
private static final int CELL_EXTRA    = CELL_INNER_PAD + CELL_MARGIN; // = 32

private void renderPanelGrid(int idx) {
    String px = "panel" + (idx+1) + "_";
    LinearLayout panel = panels[idx];
    if (panel == null) return;
    final int myGen = renderGen[idx].incrementAndGet();
    int cols = Math.max(1, prefs.getInt(px+"cols", 4));
    int iconSize = prefs.getInt(px+"icon_size", 110);
    int cellSize = iconSize + CELL_INNER_PAD;

    // Dùng LinearLayout nhiều HÀNG thay GridLayout — mỗi hàng Gravity.CENTER_HORIZONTAL
    // để hàng cuối (thiếu ô) tự canh giữa, KHÔNG dồn về bên trái như GridLayout mặc định
    // (đây là nguyên nhân "bên phải trống nhiều hơn bên trái").
    LinearLayout gridContainer = new LinearLayout(ctx);
    gridContainer.setOrientation(LinearLayout.VERTICAL);
    gridContainer.setGravity(Gravity.CENTER_HORIZONTAL);
    gridContainer.setPadding(16, 24, 16, 24);
    panel.addView(gridContainer);

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
            LinearLayout row = null;
            for (int i = 0; i < loaded.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_HORIZONTAL);
                    gridContainer.addView(row);
                }
                Object[] item = loaded.get(i);
                View cell = buildCell(px, (String) item[1], item[0], (String) item[2]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(8, 8, 8, 8);
                cell.setLayoutParams(lp);
                row.addView(cell);
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
    // 0=Tròn, 1=Vuông bo góc Google, 2=Pebble (bất đối xứng, giống Material You)
    private float[] getPanelIconCornerRadii(String px) {
        int shape = prefs.getInt(px + "icon_shape", 0);
        if (shape == 1) return new float[]{0.22f, 0.22f, 0.22f, 0.22f};
        if (shape == 2) return new float[]{0.55f, 0.28f, 0.5f, 0.32f}; // TL,TR,BR,BL
        return new float[]{0.5f, 0.5f, 0.5f, 0.5f};
    }
    private View wrapIconCell(String px, Drawable icon, String emoji, float[] radii, View.OnClickListener onClick, String label) {
    int iconSize = prefs.getInt(px + "icon_size", 110);
    LinearLayout box = new LinearLayout(ctx); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);

    int shape = prefs.getInt(px + "icon_shape", 0);
    boolean useSystemMask = shape == 3 && icon != null
        && Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;

    if (useSystemMask) {
        // Dùng thẳng AdaptiveIconDrawable gốc — để chính OS tự vẽ đúng hình dạng
        // thiết bị (vuông tròn/squircle/pebble tuỳ máy) thay vì mình tự đoán path.
        // Vừa khớp pixel-perfect, vừa ÍT lệnh vẽ hơn clipToOutline thủ công
        // => nhẹ GPU hơn cho Adreno 540 trên Pixel 2XL.
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(icon);
        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        box.addView(iv);
    } else {
        FrameLayout shapeBox = new FrameLayout(ctx);
        GradientDrawable backdrop = new GradientDrawable();
        backdrop.setColor(Color.argb(235, 255, 255, 255));
        backdrop.setCornerRadii(new float[]{
            iconSize*radii[0], iconSize*radii[0],
            iconSize*radii[1], iconSize*radii[1],
            iconSize*radii[2], iconSize*radii[2],
            iconSize*radii[3], iconSize*radii[3]
        });
        shapeBox.setBackground(backdrop);
        shapeBox.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        shapeBox.setClipToOutline(true);
        final float maxR = Math.max(Math.max(radii[0],radii[1]), Math.max(radii[2],radii[3]));
        shapeBox.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) {
                int w = v.getWidth()==0?iconSize:v.getWidth(), h = v.getHeight()==0?iconSize:v.getHeight();
                o.setRoundRect(0,0,w,h, w*maxR);
            }
        });
        View core;
        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            core = iv;
        } else {
            TextView tv = new TextView(ctx); tv.setText(emoji); tv.setTextSize(26); tv.setGravity(Gravity.CENTER);
            core = tv;
        }
        shapeBox.addView(core, new FrameLayout.LayoutParams(iconSize, iconSize));
        box.addView(shapeBox);
    }

    boolean showName = prefs.getInt(px + "show_name", 0) == 1;
    if (showName && label != null) {
        TextView tvLabel = new TextView(ctx); tvLabel.setText(label); tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(9); tvLabel.setMaxLines(1); tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(iconSize, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(tvLabel);
    }
    box.setOnClickListener(onClick);
    return box;
}
    private View buildCell(String px, String type, Object payload, String ref) {
        float[] radii = getPanelIconCornerRadii(px);
        if (type.equals("APP")) {
            return wrapIconCell(px, (Drawable) payload, null, radii, v -> {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(ref);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
                closeAllPanels();
            }, getCachedAppLabel(ref));
        } else {
            String label = getActionLabelForPanel(ref);
            return wrapIconCell(px, null, actEmoji(ref), radii, v -> {
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
