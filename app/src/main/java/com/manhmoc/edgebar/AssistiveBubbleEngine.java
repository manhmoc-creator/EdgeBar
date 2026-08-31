package com.manhmoc.edgebar;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.*;

public class AssistiveBubbleEngine {
    private Context ctx; private WindowManager wm; private SharedPreferences prefs; private boolean isAnyMode;
    private View bubbleView; private WindowManager.LayoutParams bubbleLp;
    private FrameLayout menuOverlay; private WindowManager.LayoutParams menuLp;
    private LinearLayout panelCard;
    private final FrameLayout[] nodeButtons = new FrameLayout[8];
    private Integer selectedIdx = null;
    private static final String[] DEFAULT_ORDER = {"APP","SHORTCUT","SYSTEM","INTENT","MACRO","PANEL","UTILITY","TRIGGER"};
    
    // Cờ cho Gestures
    private long lastTapUpTime = 0;
    private boolean isHolding = false;
    private final Handler tapHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTapRunnable = null;
    private float sx, sy, lastX, lastY;

    public AssistiveBubbleEngine(Context ctx, WindowManager wm, SharedPreferences prefs, boolean isAnyMode) {
        this.ctx = ctx; this.wm = wm; this.prefs = prefs; this.isAnyMode = isAnyMode;
    }

    public void rebuild() {
        boolean want = prefs.getBoolean("bubble_en", false);
        if (want && bubbleView == null) buildBubble();
        if (!want && bubbleView != null) destroyAll();
    }

    public void onPrefChanged(String key) {
        if (key == null) return;
        if (key.equals("bubble_en") || key.equals("bubble_size")) { destroyAll(); rebuild(); }
    }

    public void destroy() { destroyAll(); }

    private void buildBubble() {
        if (bubbleView != null) return;
        ImageView iv = new ImageView(ctx);
        int size = prefs.getInt("bubble_size", 120);
        try { iv.setImageDrawable(ctx.getPackageManager().getApplicationIcon(ctx.getPackageName())); }
        catch (Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#DD202124"));
        bg.setStroke(4, Color.parseColor("#8AB4F8"));
        iv.setBackground(bg);
        iv.setPadding(20, 20, 20, 20);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        // Priority ngang bằng overlay thông thường để không block (Yêu cầu 5)
        bubbleLp = new WindowManager.LayoutParams(size, size, wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleLp.x = prefs.getInt("bubble_x", 40);
        bubbleLp.y = prefs.getInt("bubble_y", 600);
        try { wm.addView(iv, bubbleLp); bubbleView = iv; } catch (Exception e) { return; }
        attachDragTouch();
    }

    private void attachDragTouch() {
        final float[] downRaw = new float[2];
        final int[] startPos = new int[2];
        final boolean[] dragging = {false};
        
        Runnable holdCheck = () -> {
            isHolding = true;
            fireAction("bubble_long");
        };

        bubbleView.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = e.getRawX(); downRaw[1] = e.getRawY();
                    startPos[0] = bubbleLp.x; startPos[1] = bubbleLp.y;
                    sx = e.getRawX(); sy = e.getRawY();
                    dragging[0] = false; isHolding = false;
                    tapHandler.postDelayed(holdCheck, prefs.getInt("sim_long_dur", 600));
                    if (pendingTapRunnable != null) { tapHandler.removeCallbacks(pendingTapRunnable); pendingTapRunnable = null; }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRaw[0], dy = e.getRawY() - downRaw[1];
                    if (!dragging[0] && (Math.abs(dx) > 22 || Math.abs(dy) > 22)) {
                        dragging[0] = true;
                        tapHandler.removeCallbacks(holdCheck);
                    }
                    if (dragging[0]) {
                        bubbleLp.x = startPos[0] + (int) dx;
                        bubbleLp.y = startPos[1] + (int) dy;
                        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    tapHandler.removeCallbacks(holdCheck);
                    if (dragging[0]) {
                        prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
                    } else if (!isHolding) {
                        long now = System.currentTimeMillis();
                        if (now - lastTapUpTime <= 280) {
                            lastTapUpTime = 0;
                            if (pendingTapRunnable != null) tapHandler.removeCallbacks(pendingTapRunnable);
                            fireAction("bubble_dtap");
                        } else {
                            lastTapUpTime = now;
                            pendingTapRunnable = () -> {
                                lastTapUpTime = 0;
                                toggleMenu(); // 1 Tap luôn mở Panel
                            };
                            tapHandler.postDelayed(pendingTapRunnable, 300);
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    private void fireAction(String baseKey) {
        String acts = prefs.getString(baseKey + "_acts", "");
        if (acts.isEmpty() || acts.equals("NONE")) return;
        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        for (String act : acts.split(",")) {
            if (act.trim().isEmpty()) continue;
            ipc.putExtra("act", act.trim());
            ctx.sendBroadcast(ipc);
        }
    }

    private void destroyAll() {
        closeMenu();
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }

    private void toggleMenu() { if (menuOverlay != null) closeMenu(); else openMenu(); }

    private void openMenu() {
        if (menuOverlay != null) return;
        selectedIdx = null;

        // Hiệu ứng bay lên đỉnh (Yêu cầu 4)
        bubbleLp.y = 0;
        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}

        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setOnClickListener(v -> closeMenu());
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        
        // Bật WATCH_OUTSIDE_TOUCH để tap ra ngoài sẽ đóng panel (Yêu cầu 5)
        menuLp = new WindowManager.LayoutParams(-1, -1, wmType,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        
        overlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) { closeMenu(); return true; }
            return false;
        });

        panelCard = buildPanelCard();
        panelCard.setOnClickListener(v -> {}); // Chặn chạm xuyên
        
        // Vị trí mở ngay dưới bong bóng và cách đều viền (Yêu cầu 3)
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
            prefs.getInt("bubble_bg_w", 800), FrameLayout.LayoutParams.WRAP_CONTENT);
        
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        int marginX = bubbleLp.x;
        // Đảm bảo card nằm giữa hoặc cách đều 2 cạnh
        if (bubbleLp.x > dm.widthPixels / 2) {
            clp.gravity = Gravity.TOP | Gravity.RIGHT;
            clp.rightMargin = dm.widthPixels - bubbleLp.x - bSize;
        } else {
            clp.gravity = Gravity.TOP | Gravity.LEFT;
            clp.leftMargin = bubbleLp.x;
        }
        clp.topMargin = bSize + 20; // Nằm ngay dưới bubble
        
        overlay.addView(panelCard, clp);
        try { wm.addView(overlay, menuLp); menuOverlay = overlay; } catch (Exception e) { return; }
    }

    private void closeMenu() {
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        selectedIdx = null;
        // Phục hồi lại vị trí cũ của bong bóng
        bubbleLp.y = prefs.getInt("bubble_y", 600);
        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch(Exception ignored){}
    }

    private List<String> getOrder() {
        String csv = prefs.getString("bubble_node_order", "");
        List<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        if (out.size() != 8) { out.clear(); Collections.addAll(out, DEFAULT_ORDER); }
        return out;
    }

    private LinearLayout buildPanelCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(prefs.getInt("bubble_bg_alpha", 160), 40, 40, 40));
        bg.setCornerRadius(prefs.getInt("bubble_bg_radius", 40));
        card.setBackground(bg);
        card.setPadding(30, 40, 30, 40);

        // Layout 8 ô: Hàng 1 (3 ô), Hàng 2 (Tìm kiếm + 2 ô), Hàng 3 (3 ô) -> Tổng 8
        card.addView(buildNodeRow(0, 1, 2));
        
        LinearLayout middleRow = new LinearLayout(ctx);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        middleRow.setGravity(Gravity.CENTER);
        middleRow.addView(buildSearchBar());
        middleRow.addView(buildNodeButton(6, true));
        middleRow.addView(buildNodeButton(7, true));
        card.addView(middleRow);
        
        card.addView(buildNodeRow(3, 4, 5));
        return card;
    }

    private LinearLayout buildNodeRow(int... idxs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(idxs.length);
        for (int idx : idxs) row.addView(buildNodeButton(idx, false));
        return row;
    }

    private FrameLayout buildNodeButton(int idx, boolean withBg) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(10, 10, 10, 10);
        box.setLayoutParams(lp);

        String type = getOrder().get(idx);
        ImageView iv = new ImageView(ctx);
        int iconSize = prefs.getInt("bubble_icon_size", 100);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        
        Drawable d = null;
        try {
            if (type.equals("SYSTEM")) d = ctx.getDrawable(android.R.drawable.ic_menu_preferences);
            else if (type.equals("UTILITY")) d = ctx.getDrawable(android.R.drawable.ic_menu_manage);
            else if (type.equals("APP")) d = ctx.getDrawable(android.R.drawable.sym_def_app_icon);
            else if (type.equals("SHORTCUT")) d = ctx.getDrawable(android.R.drawable.ic_menu_send);
            else if (type.equals("TRIGGER")) d = ctx.getDrawable(android.R.drawable.ic_menu_directions);
            else if (type.equals("INTENT")) d = ctx.getDrawable(android.R.drawable.ic_menu_compass);
            else d = ctx.getDrawable(android.R.drawable.ic_menu_view); // MACRO/PANEL
            
            if (d != null) {
                d = d.mutate(); d.setTint(Color.WHITE);
                Bitmap norm = PanelEngine.normalizeIconBitmap(d, iconSize, 0.77f);
                if (norm != null) iv.setImageBitmap(norm);
                else iv.setImageDrawable(d);
            }
        } catch (Exception ignored) {}

        if (withBg || (selectedIdx != null && selectedIdx == idx)) {
            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setCornerRadius(20f);
            boxBg.setColor(selectedIdx != null && selectedIdx == idx ? Color.parseColor("#8AB4F8") : Color.parseColor("#33000000"));
            box.setBackground(boxBg);
        }
        
        box.addView(iv);
        nodeButtons[idx] = box;
        
        box.setOnLongClickListener(v -> { selectedIdx = idx; refreshPanelCard(); return true; });
        box.setOnClickListener(v -> onNodeClick(idx));
        return box;
    }

    private void refreshPanelCard() {
        if (panelCard != null) {
            panelCard.removeAllViews();
            
            panelCard.addView(buildNodeRow(0, 1, 2));
            LinearLayout middleRow = new LinearLayout(ctx);
            middleRow.setOrientation(LinearLayout.HORIZONTAL);
            middleRow.setGravity(Gravity.CENTER);
            middleRow.addView(buildSearchBar());
            middleRow.addView(buildNodeButton(6, true));
            middleRow.addView(buildNodeButton(7, true));
            panelCard.addView(middleRow);
            panelCard.addView(buildNodeRow(3, 4, 5));
        }
    }

    private void onNodeClick(int idx) {
        if (selectedIdx != null) {
            if (selectedIdx != idx) {
                List<String> order = new ArrayList<>(getOrder());
                Collections.swap(order, selectedIdx, idx);
                prefs.edit().putString("bubble_node_order", TextUtils.join(",", order)).apply();
            }
            selectedIdx = null;
            refreshPanelCard();
            return;
        }
        showGridInPlace(getOrder().get(idx), "");
    }

    private EditText buildSearchBar() {
        EditText et = new EditText(ctx);
        et.setHint("🔍 Mở rộng chiều ngang...");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#33000000"));
        bg.setCornerRadius(20f);
        et.setBackground(bg);
        et.setPadding(28, 14, 28, 14);
        
        // Kéo dài theo trục ngang (Yêu cầu 2)
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        lp.setMargins(10, 10, 10, 10);
        et.setLayoutParams(lp);
        
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim();
                if (!q.isEmpty()) showGridInPlace("APP", q); // Default search apps
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });
        return et;
    }

    // ==================== LƯỚI DANH SÁCH IN-PLACE (Yêu cầu 9) ====================
    private void showGridInPlace(String type, String query) {
        if (panelCard == null) return;
        panelCard.removeAllViews(); // Thay thế 8 nút bằng kết quả
        
        EditText searchHead = buildSearchBar();
        searchHead.setText(query);
        panelCard.addView(searchHead);

        List<String[]> items = buildItems(type);
        String q = query.toLowerCase(Locale.ROOT);
        List<String[]> shown = new ArrayList<>();
        for (String[] it : items) if (q.isEmpty() || it[0].toLowerCase(Locale.ROOT).contains(q)) shown.add(it);

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(10, 10, 10, 10);

        if (shown.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Không tìm thấy kết quả"); empty.setTextColor(Color.GRAY);
            list.addView(empty);
        }
        
        for (String[] item : shown) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 22, 16, 22);
            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(item[0]); tvLabel.setTextColor(Color.WHITE); tvLabel.setTextSize(15f);
            row.addView(tvLabel);
            final String ref = item[1];
            row.setOnClickListener(v -> { runItem(ref); closeMenu(); });
            list.addView(row);
        }
        scroll.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, prefs.getInt("bubble_bg_h", 700));
        panelCard.addView(scroll, slp);
    }

    private List<String[]> buildItems(String type) {
        List<String[]> out = new ArrayList<>();
        switch (type) {
            case "APP": {
                PackageManager pm = ctx.getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                for (ResolveInfo ri : pm.queryIntentActivities(i, 0))
                    out.add(new String[]{ri.loadLabel(pm).toString(), "app:" + ri.activityInfo.packageName});
                out.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
                break;
            }
            case "SHORTCUT": {
                // Quét toàn bộ máy (Yêu cầu 7)
                for (String id : csv(prefs.getString("shortcut_ids", "")))
                    out.add(new String[]{prefs.getString("shortcut_" + id + "_name", "Shortcut"), "act:RUN_SHORTCUT_" + id});
                break;
            }
            case "SYSTEM": {
                String[][] sys = { {"BACK","Quay lại"},{"HOME","Màn chính"},{"RECENTS","Đa nhiệm"},{"SCREEN_OFF","Tắt màn hình"},{"FLASH","Đèn pin"},{"SCREENSHOT","Chụp màn hình"},{"CAMERA","Camera"},{"VOLUME","Âm lượng"},{"POWER_DIALOG","Menu nguồn"},{"NOTIFICATIONS","Thông báo"},{"QUICK_SETTINGS","Cài đặt nhanh"},{"SPLIT_SCREEN","Chia đôi màn hình"},{"SCREEN_RECORD","Quay màn hình"},{"AUTO_ROTATE_TOGGLE","Tự động xoay"} };
                for (String[] s : sys) out.add(new String[]{s[1], "act:" + s[0]});
                break;
            }
            case "UTILITY": { // (Yêu cầu 8)
                String[][] utl = { {"TOGGLE_OVERLAY","Bật/tắt Trợ năng"},{"TOGGLE_RECORD","Bật/tắt Ghi âm"},{"PAUSE_RECORD","Dừng/Tiếp Ghi âm"},{"YTDL_DOWNLOAD","Tải YTDLnis"},{"TOGGLE_WORK_PROFILE","Bật/tắt Hồ sơ CV"},{"OPEN_STORAGE_SCAN","Quét Dung Lượng"},{"SCAN_QR","Quét QR"},{"PLAY_MY_PLAYLIST","Phát My Playlist"} };
                for (String[] s : utl) out.add(new String[]{s[1], "act:" + s[0]});
                break;
            }
            case "TRIGGER": { // (Yêu cầu 8)
                String[][] trg = { {"TRIGGER_TAP","Tap"},{"TRIGGER_DTAP","Double Tap"},{"TRIGGER_LONG","Long Press"},{"TRIGGER_UP","Vuốt Lên"},{"TRIGGER_DOWN","Vuốt Xuống"},{"TRIGGER_LEFT","Vuốt Trái"},{"TRIGGER_RIGHT","Vuốt Phải"} };
                for (String[] s : trg) out.add(new String[]{s[1], "act:" + s[0]});
                break;
            }
            case "INTENT": {
                for (String id : csv(prefs.getString("intent_ids", "")))
                    out.add(new String[]{prefs.getString("intent_" + id + "_name", "Intent"), "act:INTENT_" + id});
                break;
            }
            case "MACRO": {
                for (String id : csv(prefs.getString("macro_ids", "")))
                    out.add(new String[]{prefs.getString("macro_" + id + "_name", "Macro"), "act:MACRO_" + id});
                break;
            }
            case "PANEL": {
                for (String id : csv(prefs.getString("pack_panel_ids", "")))
                    out.add(new String[]{prefs.getString("pack_panel_" + id + "_name", "Panel"), "act:PANEL_" + id});
                break;
            }
        }
        return out;
    }

    private List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) for (String x : s.split(",")) if (!x.trim().isEmpty()) out.add(x.trim());
        return out;
    }

    private void runItem(String ref) {
        if (ref.startsWith("app:")) {
            try {
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(ref.substring(4));
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
            } catch (Exception ignored) {}
        } else if (ref.startsWith("act:")) {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.putExtra("act", ref.substring(4));
            ctx.sendBroadcast(ipc);
        }
    }
}
