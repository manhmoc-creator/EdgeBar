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
    private BroadcastReceiver r;

    // Icon cache tĩnh dùng chung toàn service — giới hạn cứng 80 entry để chặn phình RAM
    private static final int ICON_CACHE_LIMIT = 80;
    private static final LinkedHashMap<String, Drawable> iconCache =
        new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) {
                return size() > ICON_CACHE_LIMIT;
            }
        };

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable = null;
    private static final long DEBOUNCE_MS = 400;

    private final String[] POS_NAMES_KEY = {"bc","bl","br","lt","lc","lb","rt","rc","rb"};
    // ==== THÊM MỚI: Idle Teardown Optimizer ====
private final Handler idleHandler = new Handler(Looper.getMainLooper());
private Runnable idleTeardownRunnable;
private static final long IDLE_TIMEOUT_MS = 15 * 60 * 1000L; // 15 phút không thao tác

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

    // TẦNG 6: debounce mọi thay đổi panelN_* — chỉ rebuild sau khi user dừng kéo slider
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
        if (k == null || !k.startsWith("panel")) return;
        if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = this::rebuildAll;
        debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_MS);
    };

    private void rebuildAll() {
        for (int i = 0; i < 3; i++) {
            removePanel(i);
            buildPanelIfEnabled(i);
        }
        checkSelfStop();
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

    private void removePanel(int idx) {
        try { if (handles[idx] != null) wm.removeView(handles[idx]); } catch (Exception ignored) {}
        try { if (panels[idx] != null) wm.removeView(panels[idx]); } catch (Exception ignored) {}
        handles[idx] = null; panels[idx] = null; panelOpen[idx] = false;
    }

    // TẦNG 1: LAZY VIEW — panel tắt thì không tạo view, zero cost
    private void buildPanelIfEnabled(int idx) {
        String px = "panel" + (idx+1) + "_";
        if (!prefs.getBoolean(px+"en", false)) return;

        int pos = prefs.getInt(px+"pos", 0);
        int colorIdx = prefs.getInt(px+"color_idx", 0);
        int size = prefs.getInt(px+"size", 500);
        int thick = prefs.getInt(px+"thick", 6);
        int alpha = prefs.getInt(px+"alpha", 200);
        int color = parsePanelColor(colorIdx);

        String edge = posToEdge(pos);      // "left" | "right" | "bottom"
        int gravity = posToGravity(pos);

        // --- HANDLE (tay cầm) — dùng đúng kiểu bo góc/màu như các thanh bar hiện có ---
        View handle = new View(this);
        GradientDrawable hgd = new GradientDrawable();
        hgd.setColor(Color.argb(Math.min(255, alpha+30), Color.red(color), Color.green(color), Color.blue(color)));
        hgd.setCornerRadius(20f);
        hgd.setStroke(Math.max(2, thick/2), Color.argb(255, Color.red(color), Color.green(color), Color.blue(color)));
        handle.setBackground(hgd);

        int hw = edge.equals("bottom") ? 160 : 24;
        int hh = edge.equals("bottom") ? 24 : 160;
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

        // --- PANEL (khối lưới) — viền + nền dùng lại đúng tông màu handle ---
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pgd = new GradientDrawable();
        pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        pgd.setStroke(thick, Color.argb(255, Color.red(color), Color.green(color), Color.blue(color)));
        pgd.setCornerRadius(28f);
        panel.setBackground(pgd);
        panel.setVisibility(View.GONE);

        int pw = edge.equals("bottom") ? WindowManager.LayoutParams.MATCH_PARENT : size;
        int ph = edge.equals("bottom") ? size : WindowManager.LayoutParams.MATCH_PARENT;
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
    private void renderPanelGrid(int idx) {
        String px = "panel" + (idx+1) + "_";
        LinearLayout panel = panels[idx];
        if (panel == null) return;
        int cols = prefs.getInt(px+"cols", 4);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cols);
        grid.setPadding(30, 40, 30, 40);
        panel.addView(grid);

        List<String> apps = csvToList(prefs.getString(px+"apps", ""));
        List<String> acts = csvToList(prefs.getString(px+"acts", ""));

        new Thread(() -> {
            List<Object[]> loaded = new ArrayList<>(); // {label placeholder(Drawable/emoji), type, ref}
            for (String pkg : apps) {
                Drawable d = getCachedIcon(pkg);
                if (d != null) loaded.add(new Object[]{d, "APP", pkg});
            }
            for (String act : acts) loaded.add(new Object[]{null, "ACT", act});

            new Handler(Looper.getMainLooper()).post(() -> {
                for (Object[] item : loaded) {
                    View cell = buildCell((String) item[1], item[0], (String) item[2]);
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

    private View buildCell(String type, Object payload, String ref) {
        if (type.equals("APP")) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable((Drawable) payload);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    int w = v.getWidth()==0?130:v.getWidth(), h = v.getHeight()==0?130:v.getHeight();
                    o.setRoundRect(0,0,w,h, w*0.22f);
                }
            });
            iv.setOnClickListener(v -> {
                Intent li = getPackageManager().getLaunchIntentForPackage(ref);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                closeAllPanels();
            });
            return iv;
        } else {
            TextView tv = new TextView(this);
            tv.setText(actEmoji(ref));
            tv.setTextSize(26);
            tv.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.argb(180, 96,125,139));
            bg.setCornerRadius(30f);
            tv.setBackground(bg);
            tv.setOnClickListener(v -> {
                Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                ipc.putExtra("act", ref);
                sendBroadcast(ipc);
                closeAllPanels();
            });
            return tv;
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

    private void openPanel(int idx) {
        if (panels[idx] == null) return;
        closeAllPanels(); // chỉ 1 panel mở tại 1 thời điểm — tiết kiệm overdraw
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
