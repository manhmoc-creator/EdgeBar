package com.manhmoc.edgebar;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Bong bóng chat kiểu AssistiveTouch: nút nổi di chuyển tự do + menu 6 nút hình
 *  lục giác (đỉnh trên/dưới, 2 hàng chéo, thanh tìm kiếm ở giữa). Dùng chung 1 class
 *  cho cả EdgeBarService (Lock+Homacc, isAnyMode=true) và HomescreenService (Homeb,
 *  isAnyMode=false) — đúng khuôn mẫu PanelEngine đã có sẵn trong app. */
public class AssistiveBubbleEngine {
    private Context ctx; private WindowManager wm; private SharedPreferences prefs; private boolean isAnyMode;
    private View bubbleView; private WindowManager.LayoutParams bubbleLp;
    private FrameLayout menuOverlay; private WindowManager.LayoutParams menuLp;
    private FrameLayout gridOverlay; private WindowManager.LayoutParams gridLp;
    private final TextView[] nodeButtons = new TextView[6];
    private Integer selectedIdx = null;
    private static final String[] DEFAULT_ORDER = {"APP","SHORTCUT","SYSTEM","INTENT","MACRO","PANEL"};

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
        if (key.equals("bubble_en")) rebuild();
        // Kích thước/độ mờ/bo góc chỉ cần áp dụng ở lần mở menu kế tiếp — không cần live update
    }

    public void destroy() { destroyAll(); }

    // ==================== BONG BÓNG CHAT ====================
    private void buildBubble() {
        if (bubbleView != null) return;
        ImageView iv = new ImageView(ctx);
        int size = 120;
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
        bubbleLp = new WindowManager.LayoutParams(size, size, wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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
        bubbleView.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = e.getRawX(); downRaw[1] = e.getRawY();
                    startPos[0] = bubbleLp.x; startPos[1] = bubbleLp.y;
                    dragging[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRaw[0], dy = e.getRawY() - downRaw[1];
                    if (!dragging[0] && (Math.abs(dx) > 22 || Math.abs(dy) > 22)) dragging[0] = true;
                    if (dragging[0]) {
                        bubbleLp.x = startPos[0] + (int) dx;
                        bubbleLp.y = startPos[1] + (int) dy;
                        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging[0]) toggleMenu();
                    else prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
                    return true;
            }
            return false;
        });
    }

    private void destroyAll() {
        closeGrid();
        closeMenu();
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }

    // ==================== MENU LỤC GIÁC ====================
    private void toggleMenu() { if (menuOverlay != null) closeMenu(); else openMenu(); }

    private void openMenu() {
        if (menuOverlay != null) return;
        selectedIdx = null;
        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setOnClickListener(v -> closeMenu());
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        menuLp = new WindowManager.LayoutParams(-1, -1, wmType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        LinearLayout card = buildHexCard();
        card.setOnClickListener(v -> {});
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
            prefs.getInt("bubble_bg_w", 500), prefs.getInt("bubble_bg_h", 700));
        clp.gravity = Gravity.CENTER;
        overlay.addView(card, clp);
        try { wm.addView(overlay, menuLp); menuOverlay = overlay; } catch (Exception e) { return; }
        if (bubbleView != null) bubbleView.setVisibility(View.GONE);
    }

    private void closeMenu() {
        closeGrid();
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        if (bubbleView != null) bubbleView.setVisibility(View.VISIBLE);
        selectedIdx = null;
    }

    private List<String> getOrder() {
        String csv = prefs.getString("bubble_node_order", "");
        List<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        if (out.size() != 6) { out.clear(); Collections.addAll(out, DEFAULT_ORDER); }
        return out;
    }

    private LinearLayout buildHexCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(prefs.getInt("bubble_bg_alpha", 160), 40, 40, 40));
        bg.setCornerRadius(prefs.getInt("bubble_bg_radius", 40));
        card.setBackground(bg);
        card.setPadding(20, 30, 20, 30);
        card.addView(buildNodeRow(0));
        card.addView(buildNodeRow(1, 2));
        card.addView(buildSearchBar());
        card.addView(buildNodeRow(3, 4));
        card.addView(buildNodeRow(5));
        return card;
    }

    private LinearLayout buildNodeRow(int... idxs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int idx : idxs) row.addView(buildNodeButton(idx));
        return row;
    }

    private TextView buildNodeButton(int idx) {
        String type = getOrder().get(idx);
        TextView tv = new TextView(ctx);
        tv.setText(emojiFor(type));
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(100, 100);
        lp.setMargins(14, 14, 14, 14);
        tv.setLayoutParams(lp);
        tv.setBackground(nodeBg(false));
        nodeButtons[idx] = tv;
        tv.setOnLongClickListener(v -> { selectedIdx = idx; refreshNodeHighlight(); return true; });
        tv.setOnClickListener(v -> onNodeClick(idx));
        return tv;
    }

    private GradientDrawable nodeBg(boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(selected ? "#8AB4F8" : "#33FFFFFF"));
        return bg;
    }

    private void refreshNodeHighlight() {
        for (int i = 0; i < 6; i++) {
            if (nodeButtons[i] == null) continue;
            nodeButtons[i].setBackground(nodeBg(selectedIdx != null && selectedIdx == i));
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
            closeMenu(); openMenu();
            return;
        }
        openSubmenu(getOrder().get(idx));
    }

    private String emojiFor(String type) {
        switch (type) {
            case "APP": return "📱";
            case "SHORTCUT": return "🔗";
            case "SYSTEM": return "⚙️";
            case "INTENT": return "⚡";
            case "MACRO": return "🤖";
            case "PANEL": return "📦";
            default: return "•";
        }
    }

    private EditText buildSearchBar() {
        EditText et = new EditText(ctx);
        et.setHint("🔍 Tìm ứng dụng…");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#33000000"));
        bg.setCornerRadius(100f);
        et.setBackground(bg);
        et.setPadding(28, 14, 28, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(360, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 10, 0, 10);
        et.setLayoutParams(lp);
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim();
                if (q.isEmpty()) closeGrid(); else showGrid("APP", q);
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });
        return et;
    }

    // ==================== LƯỚI DANH SÁCH (APP/SHORTCUT/SYSTEM/INTENT/MACRO/PANEL) ====================
    private void openSubmenu(String type) { showGrid(type, ""); }

    private void showGrid(String type, String query) {
        closeGrid();
        List<String[]> items = buildItems(type);
        String q = query.toLowerCase(Locale.ROOT);
        List<String[]> shown = new ArrayList<>();
        for (String[] it : items) if (q.isEmpty() || it[0].toLowerCase(Locale.ROOT).contains(q)) shown.add(it);

        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setOnClickListener(v -> closeGrid());
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        gridLp = new WindowManager.LayoutParams(-1, -1, wmType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 24, 24, 24);
        GradientDrawable listBg = new GradientDrawable();
        listBg.setColor(Color.argb(230, 25, 25, 25));
        listBg.setCornerRadius(30f);
        list.setBackground(listBg);
        list.setOnClickListener(v -> {});

        if (shown.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Không có mục nào"); empty.setTextColor(Color.GRAY); empty.setPadding(20,20,20,20);
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
            row.setOnClickListener(v -> { runItem(ref); closeGrid(); closeMenu(); });
            list.addView(row);
        }
        scroll.addView(list);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
            (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.8f),
            (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.55f));
        slp.gravity = Gravity.CENTER;
        overlay.addView(scroll, slp);
        try { wm.addView(overlay, gridLp); gridOverlay = overlay; } catch (Exception ignored) {}
    }

    private void closeGrid() {
        if (gridOverlay != null) { try { wm.removeView(gridOverlay); } catch (Exception ignored) {} gridOverlay = null; }
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
                for (String id : csv(prefs.getString("panel_shortcut_ids", "")))
                    out.add(new String[]{prefs.getString("shortcut_" + id + "_name", "Shortcut"), "act:RUN_SHORTCUT_" + id});
                break;
            }
            case "SYSTEM": {
                String[][] sys = {
                    {"BACK","Quay lại"},{"HOME","Màn chính"},{"RECENTS","Đa nhiệm"},{"SCREEN_OFF","Tắt màn hình"},
                    {"FLASH","Đèn pin"},{"SCREENSHOT","Chụp màn hình"},{"CAMERA","Camera"},{"VOLUME","Âm lượng"},
                    {"POWER_DIALOG","Menu nguồn"},{"NOTIFICATIONS","Thông báo"},{"QUICK_SETTINGS","Cài đặt nhanh"},
                    {"SPLIT_SCREEN","Chia đôi màn hình"},{"SCREEN_RECORD","Quay màn hình"},{"AUTO_ROTATE_TOGGLE","Tự động xoay"}
                };
                for (String[] s : sys) out.add(new String[]{s[1], "act:" + s[0]});
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
