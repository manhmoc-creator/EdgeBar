package com.manhmoc.edgebar;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

/**
 * V19.12.3.6.15 - EDGE PANEL GENESIS
 * Hỗ trợ tối đa 3 panel độc lập, mỗi panel có: vị trí (9 kiểu), màu, size/thick/alpha,
 * lưới app + hành động tùy chọn.
 *
 * TỐI ƯU PIN/RAM CHO PIXEL 2XL:
 * 1. LAZY VIEW: panel nào panelN_en=false thì KHÔNG addView gì cả — zero surface layer.
 * 2. ICON CACHE tĩnh (static, dùng chung mọi lần renderPanel) giới hạn 80 icon —
 *    tránh gọi getApplicationIcon() lặp lại (rất tốn CPU decode) và tránh giữ Drawable
 *    trùng lặp trong RAM khi cùng 1 app xuất hiện ở nhiều panel.
 * 3. ICON LOAD TRÊN BACKGROUND THREAD — getApplicationIcon() có thể chậm nếu app chưa
 *    từng mở, tránh block main thread gây giật khi mở panel lần đầu.
 * 4. DEBOUNCE 400ms cho mọi thay đổi pref (kéo slider) — chỉ rebuild view sau khi user
 *    dừng tay, giống cơ chế Homacc đã có, tránh spam updateViewLayout().
 * 5. updateViewLayout() thay vì removeView/addView khi chỉ đổi màu/size — không cấp
 *    phát surface mới, không GC pressure.
 * 6. TỰ DỪNG SERVICE khi cả 3 panel đều tắt (checkSelfStop) — giải phóng toàn bộ RAM.
 * 7. Panel ẨN (KHÔNG removeView) khi màn hình tắt để tránh giữ animation chạy ngầm,
 *    hiện lại khi USER_PRESENT.
 */
public class SidePanelService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private SharedPreferences prefs;

    private View[] handles = new View[3];       // tay cầm mở panel, index 0..2 = panel 1..3
    private LinearLayout[] panels = new LinearLayout[3];
    private boolean[] panelOpen = new boolean[3];
    // CŨ (giữ nguyên, chỉ thêm bên dưới):
private BroadcastReceiver r;

// MỚI — thêm ngay sau:
private final java.util.concurrent.atomic.AtomicInteger[] renderGen = {
    new java.util.concurrent.atomic.AtomicInteger(0),
    new java.util.concurrent.atomic.AtomicInteger(0),
    new java.util.concurrent.atomic.AtomicInteger(0)
};

    // Icon cache tĩnh dùng chung toàn service — giới hạn cứng 80 entry để chặn phình RAM
    private static final int ICON_CACHE_LIMIT = 80;
    private static final LinkedHashMap<String, Drawable> iconCache =
        new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) {
                return size() > ICON_CACHE_LIMIT;
            }
        };
    private final String[] POS_NAMES_KEY = {"bc","bl","br","lt","lc","lb","rt","rc","rb"};
    // Ghi chú: dùng chung bởi buildPanelIfEnabled/renderPanelGrid, KHÔNG share giữa panel
    // ==== THÊM MỚI: Idle Teardown Optimizer ====
private final Handler idleHandler = new Handler(Looper.getMainLooper());
private Runnable idleTeardownRunnable;
private static final long IDLE_TIMEOUT_MS = 15 * 60 * 1000L; // 15 phút không thao tác
    // Cache label app — tránh gọi getApplicationLabel() lặp lại mỗi lần render (tốn CPU/IPC)
private static final java.util.Map<String,String> labelCache = new java.util.HashMap<>();
private String getCachedAppLabel(String pkg) {
    String c = labelCache.get(pkg); if (c != null) return c;
    try { String l = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();
        labelCache.put(pkg, l); return l; } catch (Exception e) { return pkg; }
}
   // THÊM MỚI trong SidePanelService.java:
private static final java.util.Map<String,String> ACT_LABEL_MAP = new java.util.HashMap<>();
static {
    ACT_LABEL_MAP.put("FLASH", "Đèn pin");
    ACT_LABEL_MAP.put("SCREEN_OFF", "Tắt màn hình");
    ACT_LABEL_MAP.put("SCREENSHOT", "Chụp màn hình");
    ACT_LABEL_MAP.put("CAMERA", "Camera");
    ACT_LABEL_MAP.put("VOLUME", "Âm lượng");
    ACT_LABEL_MAP.put("NOTIFICATIONS", "Thông báo");
    ACT_LABEL_MAP.put("BACK", "Quay lại");
    ACT_LABEL_MAP.put("HOME", "Màn chính");
    ACT_LABEL_MAP.put("RECENTS", "Đa nhiệm");
    ACT_LABEL_MAP.put("VOICE_RECORD", "Ghi âm");
    ACT_LABEL_MAP.put("TOGGLE_MORSE", "Khóa Morse");
}
private String getActionLabelForPanel(String key) {
    String l = ACT_LABEL_MAP.get(key);
    return l != null ? l : key;
}
// MỚI:
private float getPanelIconRadiusPercent(String px) {
    int shape = prefs.getInt(px + "icon_shape", 0);
    switch (shape) { case 1: return 0.28f; case 2: return 0.5f; case 3: return 0.12f; default: return 0.5f; }
}
// Bọc icon + optional label, dùng ViewOutlineProvider (rẻ, không cấp Bitmap mới -> nhẹ RAM/GPU)
private View wrapIconCell(String px, Drawable icon, String emoji, float radiusPct, View.OnClickListener onClick, String label) {
    int iconSize = prefs.getInt(px + "icon_size", 110);
    LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
    View core;
    if (icon != null) {
        ImageView iv = new ImageView(this); iv.setImageDrawable(icon); core = iv;
    } else {
        TextView tv = new TextView(this); tv.setText(emoji); tv.setTextSize(26); tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(180,96,125,139)); tv.setBackground(bg);
        core = tv;
    }
    core.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize)); 
    core.setClipToOutline(true);
    core.setOutlineProvider(new ViewOutlineProvider() {
        @Override public void getOutline(View v, Outline o) {
            int w = v.getWidth()==0?110:v.getWidth(), h = v.getHeight()==0?110:v.getHeight();
            o.setRoundRect(0,0,w,h, w*radiusPct);
        }
    });
    box.addView(core);
    boolean showName = prefs.getInt(px + "show_name", 0) == 1;
    if (showName && label != null) {
        TextView tvLabel = new TextView(this); tvLabel.setText(label); tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(9); tvLabel.setMaxLines(1); tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvLabel.setGravity(Gravity.CENTER); box.addView(tvLabel);
    }
    // TouchDelegate mở rộng vùng chạm — trả lời trực tiếp mục 4 bên dưới
    box.setOnClickListener(onClick);
    return box;
}
private void scheduleIdleTeardown() {
    if (idleTeardownRunnable != null) idleHandler.removeCallbacks(idleTeardownRunnable);
    idleTeardownRunnable = () -> {
        // Không panel nào đang mở + đã quá 15 phút → gỡ toàn bộ view khỏi WindowManager,
        // giải phóng surface + Drawable cache, chỉ giữ service sống (foreground vẫn chạy nhẹ)
        boolean anyOpen = panelOpen[0] || panelOpen[1] || panelOpen[2];
        if (!anyOpen) {
            for (int i = 0; i < 3; i++) removePanel(i);
            synchronized (iconCache) { iconCache.clear(); }
        }
    };
    idleHandler.postDelayed(idleTeardownRunnable, IDLE_TIMEOUT_MS);
}

private void cancelIdleTeardown() {
    if (idleTeardownRunnable != null) idleHandler.removeCallbacks(idleTeardownRunnable);
}

// Khi cần vẽ lại panel đã bị teardown (handle bị bấm nhưng view đã gỡ) → build lại on-demand
private void ensurePanelAlive(int idx) {
    if (handles[idx] == null && prefs.getBoolean("panel" + (idx + 1) + "_en", false)) {
        buildPanelIfEnabled(idx);
    }
}
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        startForegroundQuiet();

        for (int i = 0; i < 3; i++) buildPanelIfEnabled(i);

        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String act = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                    for (int idx=0; idx<3; idx++) if (panelOpen[idx]) closePanel(idx);
                    for (View h : handles) if (h != null) h.setVisibility(View.GONE);
                } else if (Intent.ACTION_USER_PRESENT.equals(act)) {
                    for (View h : handles) if (h != null) h.setVisibility(View.VISIBLE);
                } else if ("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED".equals(act)) {
                    rebuildAll();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }
    // MỚI — THAY bằng:
private final Handler debounceHandler = new Handler(Looper.getMainLooper());
private final Runnable[] debounceRunnables = new Runnable[3];
private static final long DEBOUNCE_MS = 500;

private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
    if (k == null) return;
    int idx;
    if (k.startsWith("panel1_")) idx = 0;
    else if (k.startsWith("panel2_")) idx = 1;
    else if (k.startsWith("panel3_")) idx = 2;
    else return;

    final int fIdx = idx;
    if (debounceRunnables[fIdx] != null) debounceHandler.removeCallbacks(debounceRunnables[fIdx]);
    debounceRunnables[fIdx] = () -> rebuildOne(fIdx);
    debounceHandler.postDelayed(debounceRunnables[fIdx], DEBOUNCE_MS);
};

// Chỉ gỡ + build lại ĐÚNG 1 panel — 2 panel còn lại giữ nguyên, không tốn IPC
private void rebuildOne(int idx) {
    removePanel(idx);
    buildPanelIfEnabled(idx);
    checkSelfStop();
}

// Dùng cho broadcast PANEL_CONFIG_CHANGED (không rõ panel nào đổi) — vẫn an toàn
// vì mỗi rebuildOne() độc lập theo generation riêng
private void rebuildAll() {
    for (int i = 0; i < 3; i++) rebuildOne(i);
}
    private void checkSelfStop() {
        boolean any = prefs.getBoolean("panel1_en",false) || prefs.getBoolean("panel2_en",false) || prefs.getBoolean("panel3_en",false);
        if (!any) stopSelf();
    }

    private void startForegroundQuiet() {
        String cid = "eb_panel";
        NotificationChannel c = new NotificationChannel(cid, "Edge Panel", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Panel")
                .setOngoing(true).setSmallIcon(android.R.drawable.ic_menu_more).build();
        startForeground(94, n);
    }

    // MỚI:
private void removePanel(int idx) {
    renderGen[idx].incrementAndGet(); // huỷ mọi thread nạp icon nền còn sót của panel này
    try { if (handles[idx] != null) wm.removeView(handles[idx]); } catch (Exception ignored) {}
    try { if (panels[idx] != null) wm.removeView(panels[idx]); } catch (Exception ignored) {}
    handles[idx] = null; panels[idx] = null; panelOpen[idx] = false;
}
    // TẦNG 1: LAZY VIEW — panel tắt thì không tạo view, zero cost
    private void buildPanelIfEnabled(int idx) {
    // MỚI — bỏ dòng currentRenderPx = px;
String px = "panel" + (idx+1) + "_";
if (!prefs.getBoolean(px+"en", false)) return;
        int pos = prefs.getInt(px+"pos", 0);
        int colorIdx = prefs.getInt(px+"color_idx", 0);
        // MỚI:
int size = prefs.getInt(px+"size", 700);
int thick = prefs.getInt(px+"thick", 40);
int alpha = prefs.getInt(px+"alpha", 200);
int handleAlpha = prefs.getInt(px+"handle_alpha", 255);
float handleR = prefs.getInt(px+"handle_radius", 28);
float panelR = prefs.getInt(px+"panel_radius", 24);
int color = parsePanelColor(colorIdx);
        String edge = posToEdge(pos);      // "left" | "right" | "bottom"
        int gravity = posToGravity(pos);

        // --- HANDLE (tay cầm) — dùng đúng kiểu bo góc/màu như các thanh bar hiện có ---
View handle = new View(this);
GradientDrawable hgd = new GradientDrawable();
hgd.setColor(Color.argb(handleAlpha, Color.red(color), Color.green(color), Color.blue(color)));
// Bo góc tay cầm theo slider riêng, không hardcode 28f
float[] handleRadii = edge.equals("left")
    ? new float[]{0,0, handleR,handleR, handleR,handleR, 0,0}
    : edge.equals("right")
    ? new float[]{handleR,handleR, 0,0, 0,0, handleR,handleR}
    : new float[]{handleR,handleR, handleR,handleR, 0,0, 0,0};
hgd.setCornerRadii(handleRadii);
hgd.setStroke(Math.max(3, thick/6), Color.argb(255, Color.red(color), Color.green(color), Color.blue(color)));
handle.setBackground(hgd);
        // MỚI — đảm bảo cạnh ngắn (chiều dày để bấm) luôn tối thiểu 90px dù slider "thick" chỉnh nhỏ hơn,
// chỉ phần vẽ (background) mới theo đúng "thick" nhìn mắt thường:
int MIN_TOUCH_PX = 90;
int visualThick = Math.max(20, thick / 3); // giảm tỉ lệ vẽ để khỏi chiếm quá nhiều màn hình, xem mục 3
int hw = edge.equals("bottom") ? 200 : Math.max(MIN_TOUCH_PX, visualThick);
int hh = edge.equals("bottom") ? Math.max(MIN_TOUCH_PX, visualThick) : 200;
        // THÊM sau khi setBackground(hgd) — set padding để phần vẽ vẫn mảnh nhưng vùng chạm rộng:
int padExtra = Math.max(0, (MIN_TOUCH_PX - visualThick) / 2);
if (edge.equals("bottom")) handle.setPadding(0, padExtra, 0, padExtra);
else handle.setPadding(padExtra, 0, padExtra, 0);
        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(hw, hh,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        hp.gravity = gravity;
        try { wm.addView(handle, hp); handles[idx] = handle; } catch (Exception e) { return; }
final int fIdx = idx;
handle.setOnClickListener(v -> togglePanel(fIdx));
// MỚI — ép kích thước WindowManager tối thiểu 96px bất kể slider thickness đặt bao nhiêu,
// đảm bảo vùng chạm luôn đủ lớn để bấm trúng trên Pixel 2XL (fix mục 4):
// (xóa hẳn onTouchListener chết ở trên, không cần TouchDelegate vì sửa trực tiếp kích thước layout)
        // --- PANEL (khối lưới) — viền + nền dùng lại đúng tông màu handle ---
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        // MỚI — panel bo tròn cả 4 góc theo slider panel_radius riêng (yêu cầu mục 3: "bo tất cả 4 góc panel cho đẹp hơn"),
// và KHÔNG dùng "thick" của handle làm viền panel nữa (viền panel giữ mỏng cố định 4px cho gọn):
GradientDrawable pgd = new GradientDrawable();
pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
pgd.setStroke(4, Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
pgd.setCornerRadius(panelR);
        panel.setBackground(pgd);
        panel.setVisibility(View.GONE);

        int itemCount = csvToList(prefs.getString(px+"apps","")).size() + csvToList(prefs.getString(px+"acts","")).size();
int cols = Math.max(1, prefs.getInt(px+"cols", 4));
int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
// MỚI:
int iconSize = prefs.getInt(px+"icon_size", 110);
int cellPx = iconSize + 40; // icon + margin/label
 // 110 icon + margin, khớp wrapIconCell
// size từ slider giờ là GIỚI HẠN TỐI ĐA, không phải giá trị cố định — tránh panel to hơn nội dung
int computedCross = Math.min(size, (edge.equals("bottom") ? rows : cols) * cellPx + 80);
int computedMain  = (edge.equals("bottom") ? cols : rows) * cellPx + 80;
int pw = edge.equals("bottom") ? Math.min(computedMain, getResources().getDisplayMetrics().widthPixels) : computedCross;
int ph = edge.equals("bottom") ? computedCross : Math.min(computedMain, getResources().getDisplayMetrics().heightPixels);
        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(pw, ph,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        pp.gravity = edge.equals("left") ? (Gravity.LEFT|Gravity.CENTER_VERTICAL)
                   : edge.equals("right") ? (Gravity.RIGHT|Gravity.CENTER_VERTICAL)
                   : (Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        try { wm.addView(panel, pp); panels[idx] = panel; } catch (Exception e) { return; }
        panel.setOnTouchListener((v,e) -> { closePanel(fIdx); return true; });

        renderPanelGrid(idx);
    }

    // TẦNG 3: nạp icon trên background thread, tránh giật khi mở panel lần đầu
    // MỚI — THAY bằng:
private void renderPanelGrid(int idx) {
    String px = "panel" + (idx+1) + "_";
    LinearLayout panel = panels[idx];
    if (panel == null) return;
    final int myGen = renderGen[idx].incrementAndGet(); // tem thời gian riêng cho lần build này

    int cols = prefs.getInt(px+"cols", 4);
    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(cols);
    grid.setPadding(30, 40, 30, 40);
    panel.addView(grid);

    List<String> apps = csvToList(prefs.getString(px+"apps", ""));
    List<String> acts = csvToList(prefs.getString(px+"acts", ""));

    new Thread(() -> {
        List<Object[]> loaded = new ArrayList<>();
        for (String pkg : apps) {
            if (renderGen[idx].get() != myGen) return; // panel đã bị huỷ/build lại → dừng ngay, đỡ CPU/pin
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
        synchronized (iconCache) {
            Drawable cached = iconCache.get(pkg);
            if (cached != null) return cached;
        }
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            synchronized (iconCache) { iconCache.put(pkg, d); }
            return d;
        } catch (Exception e) { return null; }
    }

    // MỚI — action icon giờ dùng chung wrapIconCell với icon_shape/show_name của đúng panel (fix mục 1 và mục 2):
private View buildCell(String px, String type, Object payload, String ref) {
    float radius = getPanelIconRadiusPercent(px);
    if (type.equals("APP")) {
        return wrapIconCell(px, (Drawable) payload, null, radius, v -> {
            Intent li = getPackageManager().getLaunchIntentForPackage(ref);
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
            closeAllPanels();
        }, getCachedAppLabel(ref));
    } else {
        String label = getActionLabelForPanel(ref); // xem hàm mới bên dưới, mục 2
        return wrapIconCell(px, null, actEmoji(ref), radius, v -> {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.putExtra("act", ref);
            sendBroadcast(ipc);
            closeAllPanels();
        }, label);
    }
}
    private String actEmoji(String key) {
        switch (key) {
            case "FLASH": return "🔦";
            case "SCREEN_OFF": return "📴";
            case "SCREENSHOT": return "📸";
            case "CAMERA": return "📷";
            case "VOLUME": return "🔊";
            case "NOTIFICATIONS": return "🔔";
            case "BACK": return "⬅️";
            case "HOME": return "🏠";
            case "RECENTS": return "🗂️";
            case "VOICE_RECORD": return "🎙️";
            case "TOGGLE_MORSE": return "🔐";
            default: return "⚡";
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

    private String posToEdge(int pos) {
        if (pos <= 2) return "bottom";      // 0,1,2 = Bottom Center/Left/Right
        if (pos <= 5) return "left";        // 3,4,5 = Left Top/Center/Bottom
        return "right";                     // 6,7,8 = Right Top/Center/Bottom
    }

    private int posToGravity(int pos) {
        switch (pos) {
            case 0: return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case 1: return Gravity.BOTTOM | Gravity.LEFT;
            case 2: return Gravity.BOTTOM | Gravity.RIGHT;
            case 3: return Gravity.LEFT | Gravity.TOP;
            case 4: return Gravity.LEFT | Gravity.CENTER_VERTICAL;
            case 5: return Gravity.LEFT | Gravity.BOTTOM;
            case 6: return Gravity.RIGHT | Gravity.TOP;
            case 7: return Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            default: return Gravity.RIGHT | Gravity.BOTTOM;
        }
    }

    private void togglePanel(int idx) {
    cancelIdleTeardown();
    ensurePanelAlive(idx);              // ← THÊM: hồi sinh view nếu đã bị teardown
    if (panelOpen[idx]) closePanel(idx); else openPanel(idx);
}

    // MỚI:
private void openPanel(int idx) {
    if (panels[idx] == null) return;
    // 3 panel giờ mở/đóng HOÀN TOÀN ĐỘC LẬP — không còn ép đóng lẫn nhau
    panelOpen[idx] = true;
        panels[idx].setVisibility(View.VISIBLE);
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
    }

    private void closeAllPanels() {
    for (int i=0;i<3;i++) closePanel(i);
    scheduleIdleTeardown();             // ← THÊM: hẹn giờ dọn dẹp sau khi đóng hết panel
}

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override public void onDestroy() {
        isRunning = false;
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener); } catch (Exception e) {}
        try { unregisterReceiver(r); } catch (Exception e) {}
        for (int i=0;i<3;i++) removePanel(i);
        synchronized (iconCache) { iconCache.clear(); } // trả RAM ngay khi service chết
        super.onDestroy();
    }
}
