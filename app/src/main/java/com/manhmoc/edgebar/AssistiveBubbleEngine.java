package com.manhmoc.edgebar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
    
    private int restoreBubbleX = -1, restoreBubbleY = -1;
    private ValueAnimator jumpAnim;

    private GestureDetector gestureDetector;
    private float sx, sy, lastX, lastY;
    private long lastTapUpTime = 0;
    private boolean isHolding = false;
    private final Handler tapHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTapRunnable = null;

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
        if (key.equals("bubble_en") || key.equals("bubble_size") || key.equals("bubble_icon_size")) { destroyAll(); rebuild(); }
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
        iv.setPadding(15, 15, 15, 15);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
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
        final boolean[] isDragging = {false};
        final int MARGIN = 30; // Cách 4 viền
        
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                moveToCenterAndOpenMenu();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                fireAction("bubble_dtap");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                fireAction("bubble_long");
            }
        });

        bubbleView.setOnTouchListener((v, e) -> {
            if (gestureDetector.onTouchEvent(e)) return true;
            
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw[0] = e.getRawX() - bubbleLp.x; 
                    downRaw[1] = e.getRawY() - bubbleLp.y;
                    isDragging[0] = false;
                    
                    // Phóng to nhẹ khi chạm vào (mượt)
                    bubbleView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float newX = e.getRawX() - downRaw[0];
                    float newY = e.getRawY() - downRaw[1];
                    if (!isDragging[0] && (Math.abs(newX - bubbleLp.x) > 15 || Math.abs(newY - bubbleLp.y) > 15)) {
                        isDragging[0] = true;
                    }
                    if (isDragging[0]) {
                        bubbleLp.x = (int) newX;
                        bubbleLp.y = (int) newY;
                        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    
                    if (isDragging[0]) {
                        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                        int bSize = prefs.getInt("bubble_size", 120);
                        
                        // Kẹp toạ độ để luôn cách 4 viền 1 khoảng MARGIN
                        int targetX = Math.max(MARGIN, Math.min(bubbleLp.x, dm.widthPixels - bSize - MARGIN));
                        int targetY = Math.max(MARGIN, Math.min(bubbleLp.y, dm.heightPixels - bSize - MARGIN));
                        
                        ValueAnimator snapAnim = ValueAnimator.ofFloat(0f, 1f);
                        snapAnim.setDuration(250);
                        snapAnim.setInterpolator(new DecelerateInterpolator(1.5f));
                        int startX = bubbleLp.x; int startY = bubbleLp.y;
                        snapAnim.addUpdateListener(a -> {
                            float val = (float) a.getAnimatedValue();
                            bubbleLp.x = (int) (startX + (targetX - startX) * val);
                            bubbleLp.y = (int) (startY + (targetY - startY) * val);
                            try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
                        });
                        snapAnim.addListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator animation) {
                                prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
                            }
                        });
                        snapAnim.start();
                    }
                    return true;
            }
            return false;
        });
    }

    private void fireAction(String baseKey) {
        String act = prefs.getString(baseKey + "_acts", "NONE");
        if (act.isEmpty() || act.equals("NONE")) return;
        
        if (prefs.getBoolean("bubble_vib", true)) {
            try {
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(30);
            } catch (Exception ignored) {}
        }
        if (prefs.getBoolean("bubble_anim", true)) {
            Intent anim = new Intent("com.manhmoc.edgebar.TEST_ANIM");
            anim.setPackage(ctx.getPackageName()); ctx.sendBroadcast(anim);
        }
        if (prefs.getBoolean("bubble_jump_on", true)) {
            int jumpDist = 120;
            ValueAnimator jump = ValueAnimator.ofFloat(0f, 1f, 0f);
            jump.setDuration(400);
            int startY = bubbleLp.y;
            jump.addUpdateListener(a -> {
                bubbleLp.y = startY - (int) ((float) a.getAnimatedValue() * jumpDist);
                try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
            });
            jump.start();
        }

        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        if (act.equals("LAUNCH_APP")) {
            ipc.putExtra("act", "LAUNCH_APP");
            ipc.putExtra("launch_pkg", prefs.getString(baseKey + "_launch_pkg", ""));
        } else if (act.equals("RUN_SHORTCUT")) {
            ipc.putExtra("act", "RUN_SHORTCUT_" + prefs.getString(baseKey + "_shortcut_id", ""));
        } else ipc.putExtra("act", act);
        ctx.sendBroadcast(ipc);
    }

    private void moveToCenterAndOpenMenu() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        int targetX = dm.widthPixels / 2 - bSize / 2; // Đường chia 2 nửa
        
        ValueAnimator centerAnim = ValueAnimator.ofFloat(0f, 1f);
        centerAnim.setDuration(250);
        centerAnim.setInterpolator(new OvershootInterpolator(1.0f));
        int startX = bubbleLp.x;
        centerAnim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            bubbleLp.x = (int) (startX + (targetX - startX) * val);
            try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
        });
        centerAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                prefs.edit().putInt("bubble_x", bubbleLp.x).apply();
                openMenu();
            }
        });
        centerAnim.start();
    }

    private void destroyAll() {
        closeMenu();
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }

    private void toggleMenu() { if (menuOverlay != null) closeMenu(); else openMenu(); }

    private void openMenu() {
        if (menuOverlay != null) return;
        selectedIdx = null;
        restoreBubbleX = bubbleLp.x; restoreBubbleY = bubbleLp.y;

        FrameLayout overlay = new FrameLayout(ctx);
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        
        menuLp = new WindowManager.LayoutParams(
            prefs.getInt("bubble_bg_w", 800), WindowManager.LayoutParams.WRAP_CONTENT, wmType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        int pw = menuLp.width;
        int margin = 40; 

        // Panel luôn cân giữa X nếu Bubble đang ở giữa
        menuLp.x = dm.widthPixels / 2 - pw / 2; 
        
        // Ngay bên dưới bong bóng chat
        int targetY = bubbleLp.y + bSize + 15;
        // Nếu cắn viền dưới thì đẩy Panel lên trên bong bóng
        if (targetY + 700 > dm.heightPixels - margin) { 
            targetY = bubbleLp.y - 700 - 15;
        }
        menuLp.y = Math.max(margin, targetY);
        menuLp.gravity = Gravity.TOP | Gravity.LEFT;

        overlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) { closeMenu(); return true; }
            return false;
        });

        panelCard = buildPanelCard();
        panelCard.setOnClickListener(v -> {}); // Chặn chạm xuyên
        
        panelCard.setAlpha(0f);
        panelCard.setScaleX(0.85f);
        panelCard.setScaleY(0.85f);
        panelCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.1f)).start();

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        overlay.addView(panelCard, clp);
        try { wm.addView(overlay, menuLp); menuOverlay = overlay; } catch (Exception e) { return; }
    }

    private void closeMenu() {
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        selectedIdx = null;
        
        if (jumpAnim != null) jumpAnim.cancel();
        if (restoreBubbleX != -1 && restoreBubbleY != -1) {
            jumpAnim = ValueAnimator.ofFloat(0f, 1f);
            jumpAnim.setDuration(200);
            int currentX = bubbleLp.x; int currentY = bubbleLp.y;
            jumpAnim.addUpdateListener(a -> {
                float val = (float) a.getAnimatedValue();
                bubbleLp.x = (int) (currentX + (restoreBubbleX - currentX) * val);
                bubbleLp.y = (int) (currentY + (restoreBubbleY - currentY) * val);
                try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
            });
            jumpAnim.start();
        }
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
        card.setPadding(20, 30, 20, 30);

        card.addView(buildNodeRow(0, 1, 2));
        card.addView(buildNodeRow(3, 4, 5));
        
        LinearLayout row3 = new LinearLayout(ctx);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setWeightSum(4f); 
        
        row3.addView(buildNodeButton(6, 1f));
        row3.addView(buildSearchBar(2f));
        row3.addView(buildNodeButton(7, 1f));
        card.addView(row3);
        
        return card;
    }

    private LinearLayout buildNodeRow(int... idxs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(idxs.length);
        for (int idx : idxs) row.addView(buildNodeButton(idx, 1f));
        return row;
    }

    private FrameLayout buildNodeButton(int idx, float weight) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(15, 15, 15, 15);
        box.setLayoutParams(lp);

        String type = getOrder().get(idx);
        ImageView iv = new ImageView(ctx);
        int iconSize = prefs.getInt("bubble_icon_size", 100);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        ivLp.setMargins(18, 18, 18, 18);
        iv.setLayoutParams(ivLp);
        
        Drawable d = null;
        try {
            if (type.equals("SYSTEM")) d = ctx.getDrawable(android.R.drawable.ic_menu_preferences);
            else if (type.equals("UTILITY")) d = ctx.getDrawable(android.R.drawable.ic_menu_manage);
            else if (type.equals("APP")) d = ctx.getDrawable(android.R.drawable.sym_def_app_icon);
            else if (type.equals("SHORTCUT")) d = ctx.getDrawable(android.R.drawable.ic_menu_send);
            else if (type.equals("TRIGGER")) d = ctx.getDrawable(android.R.drawable.ic_menu_directions);
            else if (type.equals("INTENT")) d = ctx.getDrawable(android.R.drawable.ic_menu_compass);
            else d = ctx.getDrawable(android.R.drawable.ic_menu_view);
            
            if (d != null) {
                d = d.mutate(); d.setTint(Color.WHITE);
                Bitmap norm = PanelEngine.normalizeIconBitmap(d, iconSize, 0.77f);
                if (norm != null) iv.setImageBitmap(norm);
                else iv.setImageDrawable(d);
            }
        } catch (Exception ignored) {}

        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setCornerRadius(36f); // System App Icon Shape
        boxBg.setColor(selectedIdx != null && selectedIdx == idx ? Color.parseColor("#8AB4F8") : Color.parseColor("#33000000"));
        box.setBackground(boxBg);
        
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
            panelCard.addView(buildNodeRow(3, 4, 5));
            LinearLayout row3 = new LinearLayout(ctx);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setWeightSum(4f);
            row3.addView(buildNodeButton(6, 1f));
            row3.addView(buildSearchBar(2f));
            row3.addView(buildNodeButton(7, 1f));
            panelCard.addView(row3);
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

    private EditText buildSearchBar(float weight) {
        EditText et = new EditText(ctx);
        et.setHint("Search…");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#33000000"));
        bg.setCornerRadius(100f);
        et.setBackground(bg);
        et.setPadding(28, 14, 28, 14);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(15, 15, 15, 15);
        et.setLayoutParams(lp);
        
        et.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim();
                if (!q.isEmpty()) showGridInPlace("APP", q);
            }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });
        return et;
    }

    private void showGridInPlace(String type, String query) {
        if (panelCard == null) return;
        panelCard.removeAllViews(); 
        
        LinearLayout searchRow = new LinearLayout(ctx);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.addView(buildSearchBar(1f));
        panelCard.addView(searchRow);

        List<String[]> items = buildItems(type);
        String q = query.toLowerCase(Locale.ROOT);
        List<String[]> shown = new ArrayList<>();
        for (String[] it : items) if (q.isEmpty() || it[0].toLowerCase(Locale.ROOT).contains(q)) shown.add(it);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(10, 10, 10, 10);

        if (shown.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Không tìm thấy kết quả"); empty.setTextColor(Color.GRAY);
            empty.setPadding(20, 20, 20, 20);
            list.addView(empty);
        }
        
        for (String[] item : shown) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 22, 16, 22);
            
            ImageView iv = new ImageView(ctx);
            int isize = 75;
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(isize, isize);
            ilp.setMargins(0, 0, 26, 0);
            iv.setLayoutParams(ilp);
            
            if (item[1].startsWith("app:")) {
                try { iv.setImageDrawable(ctx.getPackageManager().getApplicationIcon(item[1].substring(4))); }
                catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
            } else if (item[1].startsWith("act:RUN_SHORTCUT_")) {
                String scId = item[1].substring("act:RUN_SHORTCUT_".length());
                String path = prefs.getString("shortcut_" + scId + "_icon_path", "");
                if (!path.isEmpty()) iv.setImageBitmap(BitmapFactory.decodeFile(path));
                else iv.setImageResource(android.R.drawable.ic_menu_send);
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_view);
                iv.setColorFilter(Color.WHITE);
            }
            row.addView(iv);

            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(item[0]); tvLabel.setTextColor(Color.WHITE); tvLabel.setTextSize(15f);
            row.addView(tvLabel);
            final String ref = item[1];
            row.setOnClickListener(v -> { runItem(ref); closeMenu(); });
            list.addView(row);
        }
        scroll.addView(list);
        
        int maxHeight = prefs.getInt("bubble_bg_h", 800);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, maxHeight);
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
                for (ResolveInfo ri : ShortcutScanner.getProviders(ctx)) {
                    out.add(new String[]{ri.loadLabel(ctx.getPackageManager()).toString(), "act:CREATE_SHORTCUT_" + ri.activityInfo.packageName + "/" + ri.activityInfo.name});
                }
                for (String id : csv(prefs.getString("shortcut_ids", "")))
                    out.add(new String[]{"(Đã lưu) " + prefs.getString("shortcut_" + id + "_name", "Shortcut"), "act:RUN_SHORTCUT_" + id});
                break;
            }
            case "SYSTEM": {
                String[][] sys = { {"BACK","Quay lại"},{"HOME","Màn chính"},{"RECENTS","Đa nhiệm"},{"SCREEN_OFF","Tắt màn hình"},{"FLASH","Đèn pin"},{"SCREENSHOT","Chụp màn hình"},{"CAMERA","Camera"},{"VOLUME","Âm lượng"},{"POWER_DIALOG","Menu nguồn"},{"NOTIFICATIONS","Thông báo"},{"QUICK_SETTINGS","Cài đặt nhanh"},{"SPLIT_SCREEN","Chia đôi màn hình"},{"SCREEN_RECORD","Quay màn hình"},{"AUTO_ROTATE_TOGGLE","Tự động xoay"} };
                for (String[] s : sys) out.add(new String[]{s[1], "act:" + s[0]});
                break;
            }
            case "UTILITY": { 
                String[][] utl = { {"TOGGLE_OVERLAY","Bật/tắt Trợ năng"},{"TOGGLE_RECORD","Bật/tắt Ghi âm"},{"PAUSE_RECORD","Dừng/Tiếp Ghi âm"},{"YTDL_DOWNLOAD","Tải YTDLnis"},{"TOGGLE_WORK_PROFILE","Bật/tắt Hồ sơ CV"},{"OPEN_STORAGE_SCAN","Quét Dung Lượng"},{"SCAN_QR","Quét QR"},{"PLAY_MY_PLAYLIST","Phát My Playlist"} };
                for (String[] s : utl) out.add(new String[]{s[1], "act:" + s[0]});
                break;
            }
            case "TRIGGER": { 
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
        } else if (ref.startsWith("act:CREATE_SHORTCUT_")) {
            String[] split = ref.substring(20).split("/");
            if (split.length == 2) {
                Intent createIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                createIntent.setClassName(split[0], split[1]);
                createIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(createIntent); } catch (Exception ignored) {}
            }
        } else if (ref.startsWith("act:")) {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.putExtra("act", ref.substring(4));
            ctx.sendBroadcast(ipc);
        }
    }
}
