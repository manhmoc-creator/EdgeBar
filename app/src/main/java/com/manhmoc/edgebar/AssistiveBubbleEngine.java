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
    
    private final FrameLayout[] mainNodes = new FrameLayout[9];
    private Integer selectedMainIdx = null;
    private Integer selectedSubIdx = null;
    private String currentSubmenu = null; // Null = Màn 9 nút chính, khác Null = Màn lưới con
    
    private static final String[] DEFAULT_ORDER = {"APP","SHORTCUT","SYSTEM","INTENT","MACRO","PANEL","UTILITY","TRIGGER","SEARCH"};
    
    private int restoreBubbleX = -1, restoreBubbleY = -1;
    private ValueAnimator jumpAnim;

    private GestureDetector gestureDetector;
    private float sx, sy, lastX, lastY;
    private VelocityTracker velocityTracker;

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
        if (key.equals("bubble_en") || key.equals("bubble_size") || key.equals("bubble_icon_size") || key.equals("bubble_main_icon")) { 
            destroyAll(); rebuild(); 
        }
    }

    public void destroy() { destroyAll(); }

    private void destroyAll() {
        closeMenu();
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }

    private Drawable getCustomIcon(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        try {
            if (ref.startsWith("app:")) return ctx.getPackageManager().getApplicationIcon(ref.substring(4));
            if (ref.startsWith("poolc:")) {
                int[] pool = PanelEngine.getCustomIconPool(ctx);
                int idx = Integer.parseInt(ref.substring(6));
                if (idx >= 0 && idx < pool.length) return ctx.getDrawable(pool[idx]);
            }
            if (ref.startsWith("pool:")) {
                int idx = Integer.parseInt(ref.substring(5));
                if (idx >= 0 && idx < PanelEngine.SYSTEM_ICON_POOL.length) return ctx.getDrawable(PanelEngine.SYSTEM_ICON_POOL[idx]);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void buildBubble() {
        if (bubbleView != null) return;
        ImageView iv = new ImageView(ctx);
        int size = prefs.getInt("bubble_size", 120);
        
        Drawable customIcon = getCustomIcon(prefs.getString("bubble_main_icon", ""));
        if (customIcon != null) {
            Bitmap norm = PanelEngine.normalizeIconBitmap(customIcon.mutate(), size, 0.7f);
            if (norm != null) iv.setImageBitmap(norm);
            else iv.setImageDrawable(customIcon);
        } else {
            try { iv.setImageDrawable(ctx.getPackageManager().getApplicationIcon(ctx.getPackageName())); }
            catch (Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
        }
        
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
        final int MARGIN = 30; 
        
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (menuOverlay != null) {
                    if (currentSubmenu != null) {
                        currentSubmenu = null; // Đang ở Menu Con -> Tap để Back ra 9 nút chính
                        refreshPanelCard();
                    } else {
                        closeMenu(); // Đang ở 9 nút chính -> Tap để Đóng Panel
                    }
                } else {
                    moveToCenterAndOpenMenu(); 
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                fireGestureAction("dtap");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                fireGestureAction("long");
            }
        });

        bubbleView.setOnTouchListener((v, e) -> {
            if (gestureDetector.onTouchEvent(e)) return true;
            
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                    else velocityTracker.clear();
                    velocityTracker.addMovement(e);

                    downRaw[0] = e.getRawX() - bubbleLp.x; 
                    downRaw[1] = e.getRawY() - bubbleLp.y;
                    isDragging[0] = false;
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (velocityTracker != null) velocityTracker.addMovement(e);
                    
                    float newX = e.getRawX() - downRaw[0];
                    float newY = e.getRawY() - downRaw[1];
                    if (!isDragging[0] && (Math.abs(newX - bubbleLp.x) > 8 || Math.abs(newY - bubbleLp.y) > 8)) {
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
                    if (isDragging[0]) {
                        float vX = 0, vY = 0;
                        if (velocityTracker != null) {
                            velocityTracker.addMovement(e);
                            velocityTracker.computeCurrentVelocity(1000);
                            vX = velocityTracker.getXVelocity();
                            vY = velocityTracker.getYVelocity();
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }

                        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                        int bSize = prefs.getInt("bubble_size", 120);
                        
                        int targetX;
                        if (vX > 1500) targetX = dm.widthPixels - bSize - MARGIN; 
                        else if (vX < -1500) targetX = MARGIN; 
                        else targetX = (bubbleLp.x + bSize / 2 < dm.widthPixels / 2) ? MARGIN : dm.widthPixels - bSize - MARGIN;
                        
                        int calculatedTargetY = bubbleLp.y + (int) (vY * 0.12f);
                        final int finalTargetYDrag = Math.max(MARGIN, Math.min(calculatedTargetY, dm.heightPixels - bSize - MARGIN));
                        final int finalTargetXDrag = targetX;
                        
                        ValueAnimator snapAnim = ValueAnimator.ofFloat(0f, 1f);
                        snapAnim.setDuration(400); 
                        snapAnim.setInterpolator(new OvershootInterpolator(0.9f)); 
                        int startX = bubbleLp.x; int startY = bubbleLp.y;
                        snapAnim.addUpdateListener(a -> {
                            float val = (float) a.getAnimatedValue();
                            bubbleLp.x = (int) (startX + (finalTargetXDrag - startX) * val);
                            bubbleLp.y = (int) (startY + (finalTargetYDrag - startY) * val);
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

    private void fireGestureAction(String gesture) {
        String rulesCsv = prefs.getString("bubble_pack_rules", "");
        if (rulesCsv.isEmpty()) return;
        for (String rId : csvToList(rulesCsv)) {
            if (!prefs.getBoolean("prule_" + rId + "_en", true)) continue;
            String g = prefs.getString("prule_" + rId + "_gestures", "");
            if (g.contains(gesture)) {
                if (prefs.getBoolean("prule_" + rId + "_vib", true)) {
                    try {
                        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                        else v.vibrate(30);
                    } catch (Exception ignored) {}
                }
                if (prefs.getBoolean("prule_" + rId + "_anim", true)) {
                    Intent anim = new Intent("com.manhmoc.edgebar.TEST_ANIM");
                    anim.setPackage(ctx.getPackageName()); ctx.sendBroadcast(anim);
                }
                if (prefs.getBoolean("prule_" + rId + "_jump_on", true)) {
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

                String acts = prefs.getString("prule_" + rId + "_acts", "");
                if (!acts.isEmpty() && !acts.equals("NONE")) {
                    Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                    for (String act : acts.split(",")) {
                        String at = act.trim();
                        if (at.isEmpty()) continue;
                        if (at.equals("LAUNCH_APP")) {
                            ipc.putExtra("act", "LAUNCH_APP");
                            ipc.putExtra("launch_pkg", prefs.getString("prule_" + rId + "_launch_pkg", ""));
                        } else if (at.equals("RUN_SHORTCUT")) {
                            ipc.putExtra("act", "RUN_SHORTCUT_" + prefs.getString("prule_" + rId + "_shortcut_id", ""));
                        } else {
                            ipc.putExtra("act", at);
                        }
                        ctx.sendBroadcast(ipc);
                    }
                }
            }
        }
    }

    private void moveToCenterAndOpenMenu() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        int targetX = dm.widthPixels / 2 - bSize / 2; 
        
        restoreBubbleX = bubbleLp.x; 
        restoreBubbleY = bubbleLp.y;

        int startX = bubbleLp.x;
        
        openMenu();
        
        ValueAnimator centerAnim = ValueAnimator.ofFloat(0f, 1f);
        centerAnim.setDuration(220); 
        centerAnim.setInterpolator(new DecelerateInterpolator(1.5f)); 
        centerAnim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            bubbleLp.x = (int) (startX + (targetX - startX) * val);
            try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
        });
        centerAnim.start();
    }

    private void openMenu() {
        if (menuOverlay != null) return;
        selectedMainIdx = null; selectedSubIdx = null; currentSubmenu = null;

        FrameLayout overlay = new FrameLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    if (currentSubmenu != null) { currentSubmenu = null; refreshPanelCard(); } 
                    else closeMenu(); 
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        int wmType = isAnyMode ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        
        menuLp = new WindowManager.LayoutParams(
            prefs.getInt("bubble_bg_w", 800), WindowManager.LayoutParams.WRAP_CONTENT, wmType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
            PixelFormat.TRANSLUCENT);
        
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        menuLp.x = dm.widthPixels / 2 - menuLp.width / 2; 
        menuLp.gravity = Gravity.TOP | Gravity.LEFT;

        overlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) { closeMenu(); return true; }
            return false;
        });

        panelCard = buildPanelCard();
        panelCard.setOnClickListener(v -> {}); 
        
        recalculateMenuPosition(); 
        
        panelCard.setAlpha(0f); panelCard.setScaleX(0.85f); panelCard.setScaleY(0.85f);
        panelCard.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(1.1f)).start();

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        overlay.addView(panelCard, clp);
        try { wm.addView(overlay, menuLp); menuOverlay = overlay; } catch (Exception e) { return; }
    }

    private void recalculateMenuPosition() {
        if (panelCard == null || menuLp == null) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        int margin = 40; 
        int gap = 45; 

        panelCard.measure(
            View.MeasureSpec.makeMeasureSpec(menuLp.width, View.MeasureSpec.EXACTLY), 
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST)
        );
        int actualPh = panelCard.getMeasuredHeight();

        int calculatedTargetY = bubbleLp.y + bSize + gap;
        if (calculatedTargetY + actualPh > dm.heightPixels - margin) { 
            calculatedTargetY = bubbleLp.y - actualPh - gap;
        }
        
        final int finalTargetY = Math.max(margin, calculatedTargetY);
        menuLp.y = finalTargetY;
        
        if (menuOverlay != null && menuOverlay.isAttachedToWindow()) {
            try { wm.updateViewLayout(menuOverlay, menuLp); } catch (Exception ignored) {}
        }
    }

    private void closeMenu() {
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        selectedMainIdx = null; selectedSubIdx = null; currentSubmenu = null;
        
        if (jumpAnim != null) jumpAnim.cancel();
        if (restoreBubbleX != -1 && restoreBubbleY != -1) {
            jumpAnim = ValueAnimator.ofFloat(0f, 1f);
            jumpAnim.setDuration(250);
            jumpAnim.setInterpolator(new OvershootInterpolator(0.85f));
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

    private List<String> getMainOrder() {
        String csv = prefs.getString("bubble_node_order", "");
        List<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        if (out.size() != 9) { out.clear(); Collections.addAll(out, DEFAULT_ORDER); }
        return out;
    }

    private List<String> getSubItems(String type) {
        String csv = prefs.getString("bubble_node_items_" + type, "");
        List<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        while (out.size() < 9) out.add(""); 
        return out;
    }

    private String getLabelForType(String type) {
        switch (type) {
            case "APP": return "Apps"; case "SHORTCUT": return "Shortcut"; case "SYSTEM": return "System";
            case "UTILITY": return "Utility"; case "TRIGGER": return "Trigger"; case "INTENT": return "Intent";
            case "MACRO": return "Macro"; case "PANEL": return "Panel"; case "SEARCH": return "Search";
            default: return "Node";
        }
    }

    private String getActionLabelForSubNode(String ref) {
        if (ref.startsWith("app:")) {
            try { return ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(ref.substring(4),0)).toString(); }
            catch (Exception e) { return "App"; }
        } else if (ref.startsWith("act:RUN_SHORTCUT_")) {
            return prefs.getString("shortcut_" + ref.substring(17) + "_name", "Shortcut");
        } else if (ref.startsWith("act:INTENT_")) {
            return prefs.getString("intent_" + ref.substring(11) + "_name", "Intent");
        } else if (ref.startsWith("act:MACRO_")) {
            return prefs.getString("macro_" + ref.substring(10) + "_name", "Macro");
        } else if (ref.startsWith("act:PANEL_")) {
            return prefs.getString("pack_panel_" + ref.substring(10) + "_name", "Panel");
        } else if (ref.startsWith("act:")) {
            String actKey = ref.substring(4);
            if (actKey.equals("BACK")) return "Quay lại"; if (actKey.equals("HOME")) return "Màn chính";
            if (actKey.equals("RECENTS")) return "Đa nhiệm"; if (actKey.equals("SCREEN_OFF")) return "Tắt màn hình";
            if (actKey.equals("FLASH")) return "Đèn pin"; if (actKey.equals("SCREENSHOT")) return "Chụp màn hình";
            if (actKey.equals("CAMERA")) return "Camera"; if (actKey.equals("VOLUME")) return "Âm lượng";
            if (actKey.equals("POWER_DIALOG")) return "Menu nguồn"; if (actKey.equals("NOTIFICATIONS")) return "Thông báo";
            if (actKey.equals("QUICK_SETTINGS")) return "Cài đặt nhanh"; if (actKey.equals("SPLIT_SCREEN")) return "Chia đôi màn hình";
            if (actKey.equals("SCREEN_RECORD")) return "Quay màn hình"; if (actKey.equals("AUTO_ROTATE_TOGGLE")) return "Tự động xoay";
            if (actKey.equals("TOGGLE_OVERLAY")) return "Bật/tắt Trợ năng"; if (actKey.equals("TOGGLE_RECORD")) return "Bật/tắt Ghi âm";
            if (actKey.equals("PAUSE_RECORD")) return "Dừng/Tiếp Ghi âm"; if (actKey.equals("YTDL_DOWNLOAD")) return "Tải YTDLnis";
            if (actKey.equals("TOGGLE_WORK_PROFILE")) return "Bật/tắt Hồ sơ CV"; if (actKey.equals("OPEN_STORAGE_SCAN")) return "Quét Dung Lượng";
            if (actKey.equals("SCAN_QR")) return "Quét QR"; if (actKey.equals("PLAY_MY_PLAYLIST")) return "Phát My Playlist";
            if (actKey.equals("TRIGGER_TAP")) return "Tap"; if (actKey.equals("TRIGGER_DTAP")) return "Double Tap";
            if (actKey.equals("TRIGGER_LONG")) return "Long Press"; if (actKey.equals("TRIGGER_UP")) return "Vuốt Lên";
            if (actKey.equals("TRIGGER_DOWN")) return "Vuốt Xuống"; if (actKey.equals("TRIGGER_LEFT")) return "Vuốt Trái";
            if (actKey.equals("TRIGGER_RIGHT")) return "Vuốt Phải"; if (actKey.equals("TRIGGER_ACC_MENU_2F")) return "Giả lập 2 ngón";
            return actKey;
        }
        return "Action";
    }

    private LinearLayout buildPanelCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(prefs.getInt("bubble_bg_alpha", 160), 0, 0, 0));
        bg.setCornerRadius(prefs.getInt("bubble_bg_radius", 40));
        card.setBackground(bg);
        card.setPadding(20, 30, 20, 30);

        if (currentSubmenu != null) {
            if (currentSubmenu.equals("SEARCH")) buildSearchMenu(card);
            else buildSubmenuGrid(card, currentSubmenu);
        } else {
            // LƯỚI 9 NÚT MẶC ĐỊNH LÀ 3 HÀNG - MỖI HÀNG 3 NÚT
            for (int i = 0; i < 3; i++) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(3);
                for (int j = 0; j < 3; j++) {
                    int idx = i * 3 + j;
                    row.addView(createNodeButton(getMainOrder().get(idx), null, null, selectedMainIdx != null && selectedMainIdx == idx, 
                        v -> {
                            if (selectedMainIdx != null) {
                                if (selectedMainIdx != idx) {
                                    List<String> order = new ArrayList<>(getMainOrder());
                                    Collections.swap(order, selectedMainIdx, idx);
                                    prefs.edit().putString("bubble_node_order", TextUtils.join(",", order)).apply();
                                }
                                selectedMainIdx = null;
                                refreshPanelCard();
                            } else {
                                currentSubmenu = getMainOrder().get(idx);
                                refreshPanelCard();
                            }
                        }, 
                        v -> { selectedMainIdx = idx; refreshPanelCard(); return true; }
                    ));
                }
                card.addView(row);
            }
        }
        return card;
    }

    private void buildSubmenuGrid(LinearLayout card, String type) {
        TextView tvHeader = new TextView(ctx);
        tvHeader.setText(getLabelForType(type));
        tvHeader.setTextColor(Color.parseColor("#00E5FF"));
        tvHeader.setTextSize(16f);
        tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(0, 0, 0, 20);
        card.addView(tvHeader);

        List<String> items = getSubItems(type);
        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(3);
            for (int j = 0; j < 3; j++) {
                int idx = i * 3 + j;
                String ref = items.get(idx);
                row.addView(createNodeButton(getActionLabelForSubNode(ref), ref, type, selectedSubIdx != null && selectedSubIdx == idx, 
                    v -> {
                        if (selectedSubIdx != null) {
                            if (selectedSubIdx != idx) {
                                List<String> list = getSubItems(type);
                                Collections.swap(list, selectedSubIdx, idx);
                                prefs.edit().putString("bubble_node_items_" + type, TextUtils.join(",", list)).apply();
                            }
                            selectedSubIdx = null;
                            refreshPanelCard();
                        } else {
                            if (!ref.isEmpty()) { runItem(ref); closeMenu(); }
                        }
                    }, 
                    v -> { selectedSubIdx = idx; refreshPanelCard(); return true; }
                ));
            }
            card.addView(row);
        }
    }

    // --- HÀM BUILD ICON LƯỚI ĐỒNG NHẤT (CHO CẢ MÀN CHÍNH & MÀN CON) ---
    private FrameLayout createNodeButton(String label, String ref, String parentType, boolean isSelected, View.OnClickListener onClick, View.OnLongClickListener onLongClick) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(10, 15, 10, 15);
        box.setLayoutParams(lp);
        
        if (ref != null && ref.isEmpty()) return box; // Node rỗng ở Submenu thì trả về FrameLayout trống để giữ lưới 3x3

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        
        FrameLayout iconBox = new FrameLayout(ctx);
        int iconSize = prefs.getInt("bubble_icon_size", 100);
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(iconSize + 40, iconSize + 40);
        iconBox.setLayoutParams(ibLp);
        
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setCornerRadius(100f); 
        boxBg.setColor(isSelected ? Color.parseColor("#8AB4F8") : Color.parseColor("#333333")); // MÀU XÁM ĐẬM CỐ ĐỊNH
        iconBox.setBackground(boxBg);

        ImageView iv = new ImageView(ctx);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        
        Drawable d = null; Bitmap b = null;
        boolean shouldTint = false;
        PackageManager pm = ctx.getPackageManager();

        // 1. Phân biệt Icon của Main Node hay Sub Node
        String customOverride = "";
        if (parentType == null) {
            // Main Node (Vd: SYSTEM, APP...) -> Lấy Icon lưu theo TYPE
            String typeKey = label; // Ở màn chính, Label chính là Type (cần convert lại)
            if (label.equals("Apps")) typeKey = "APP"; else if (label.equals("Shortcut")) typeKey = "SHORTCUT";
            else if (label.equals("System")) typeKey = "SYSTEM"; else if (label.equals("Utility")) typeKey = "UTILITY";
            else if (label.equals("Trigger")) typeKey = "TRIGGER"; else if (label.equals("Intent")) typeKey = "INTENT";
            else if (label.equals("Macro")) typeKey = "MACRO"; else if (label.equals("Panel")) typeKey = "PANEL";
            else if (label.equals("Search")) typeKey = "SEARCH";
            
            customOverride = prefs.getString("bubble_node_icon_" + typeKey, "");
            d = getCustomIcon(customOverride);
            shouldTint = true; 
            
            if (d == null) {
                try {
                    if (typeKey.equals("SYSTEM")) d = ctx.getDrawable(android.R.drawable.ic_menu_preferences);
                    else if (typeKey.equals("UTILITY")) d = ctx.getDrawable(android.R.drawable.ic_menu_manage);
                    else if (typeKey.equals("APP")) d = ctx.getDrawable(android.R.drawable.sym_def_app_icon);
                    else if (typeKey.equals("SHORTCUT")) d = ctx.getDrawable(android.R.drawable.ic_menu_send);
                    else if (typeKey.equals("TRIGGER")) d = ctx.getDrawable(android.R.drawable.ic_menu_directions);
                    else if (typeKey.equals("INTENT")) d = ctx.getDrawable(android.R.drawable.ic_menu_compass);
                    else if (typeKey.equals("SEARCH")) d = ctx.getDrawable(android.R.drawable.ic_menu_search);
                    else d = ctx.getDrawable(android.R.drawable.ic_menu_view); 
                } catch (Exception ignored) {}
            }
        } else {
            // Sub Node (Vd: 1 Action cụ thể)
            customOverride = prefs.getString("bubble_node_icon_override_" + parentType + "_" + ref, "");
            d = getCustomIcon(customOverride);
            
            if (d == null) {
                if (ref.startsWith("app:")) {
                    try { d = pm.getApplicationIcon(ref.substring(4)); } catch(Exception ignored) {}
                } else if (ref.startsWith("act:RUN_SHORTCUT_")) {
                    try {
                        String scId = ref.substring(17);
                        String path = prefs.getString("shortcut_" + scId + "_icon_path", "");
                        if (!path.isEmpty()) b = BitmapFactory.decodeFile(path);
                        else {
                            Intent scIntent = Intent.parseUri(prefs.getString("shortcut_" + scId + "_intent_uri", ""), 0);
                            ComponentName cn = scIntent.getComponent();
                            if (cn != null) d = pm.getActivityIcon(cn);
                        }
                    } catch (Exception ignored) {}
                    if (d == null && b == null) { d = ctx.getDrawable(android.R.drawable.ic_menu_send); shouldTint = true; }
                } else {
                    shouldTint = true;
                    int defRes = android.R.drawable.ic_menu_view;
                    if (ref.equals("act:BACK")) defRes = android.R.drawable.ic_media_rew;
                    else if (ref.equals("act:HOME")) defRes = android.R.drawable.ic_menu_compass;
                    else if (ref.equals("act:RECENTS")) defRes = android.R.drawable.ic_menu_recent_history;
                    else if (ref.equals("act:SCREEN_OFF")) defRes = android.R.drawable.ic_lock_lock;
                    else if (ref.equals("act:POWER_DIALOG")) defRes = android.R.drawable.ic_lock_power_off;
                    else if (ref.equals("act:SCREENSHOT") || ref.equals("act:CAMERA")) defRes = android.R.drawable.ic_menu_camera;
                    else if (ref.equals("act:NOTIFICATIONS")) defRes = android.R.drawable.ic_dialog_email;
                    else if (ref.equals("act:VOICE_RECORD") || ref.equals("act:TOGGLE_RECORD")) defRes = android.R.drawable.ic_btn_speak_now;
                    else if (ref.equals("act:QUICK_SETTINGS")) defRes = android.R.drawable.ic_menu_preferences;
                    else if (ref.equals("act:AUTO_ROTATE_TOGGLE")) defRes = android.R.drawable.ic_menu_always_landscape_portrait;
                    else if (ref.equals("act:YTDL_DOWNLOAD")) defRes = android.R.drawable.ic_menu_upload;
                    else if (ref.equals("act:SCREEN_RECORD")) defRes = android.R.drawable.presence_video_online;
                    d = ctx.getDrawable(defRes);
                }
            } else {
                if (customOverride.startsWith("pool")) shouldTint = true;
            }
        }

        if (b != null) {
            iv.setImageBitmap(b);
        } else if (d != null) {
            if (shouldTint) { d = d.mutate(); d.setTint(Color.WHITE); }
            Bitmap norm = PanelEngine.normalizeIconBitmap(d, iconSize, 0.77f);
            if (norm != null) iv.setImageBitmap(norm);
            else iv.setImageDrawable(d);
        }
        iconBox.addView(iv);
        
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 10, 0, 0);

        content.addView(iconBox); content.addView(tv);
        box.addView(content);
        
        box.setOnClickListener(onClick);
        if (onLongClick != null) box.setOnLongClickListener(onLongClick);
        return box;
    }

    private void buildSearchMenu(LinearLayout card) {
        EditText et = new EditText(ctx);
        et.setHint("🔍 Tìm kiếm hệ thống...");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#333333"));
        bg.setCornerRadius(100f);
        et.setBackground(bg);
        et.setPadding(28, 20, 28, 20);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 15);
        et.setLayoutParams(lp);
        card.addView(et);

        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout listContainer = new LinearLayout(ctx);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setTag("LIST_CONTAINER"); 
        scroll.addView(listContainer);
        
        int maxHeight = prefs.getInt("bubble_bg_h", 800);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, maxHeight);
        card.addView(scroll, slp);

        et.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { showGridListOnly("SEARCH", s.toString().trim()); }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });

        showGridListOnly("SEARCH", ""); 
        
        et.postDelayed(() -> {
            et.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private void showGridListOnly(String type, String query) {
        if (panelCard == null) return;
        LinearLayout listContainer = panelCard.findViewWithTag("LIST_CONTAINER");
        if (listContainer == null) return;
        listContainer.removeAllViews();

        List<String[]> items;
        if (type.equals("SEARCH")) {
            items = buildItems("ALL"); 
        } else {
            items = new ArrayList<>();
            List<String> selectedRefs = getSubItems(type);
            List<String[]> allOfType = buildItems(type); 
            for (String ref : selectedRefs) {
                if (ref.isEmpty()) continue;
                for (String[] it : allOfType) {
                    if (it[1].equals(ref)) { items.add(it); break; }
                }
            }
        }

        String q = query.toLowerCase(Locale.ROOT);
        List<String[]> shown = new ArrayList<>();
        for (String[] it : items) if (q.isEmpty() || it[0].toLowerCase(Locale.ROOT).contains(q)) shown.add(it);

        if (shown.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Không tìm thấy kết quả"); empty.setTextColor(Color.GRAY);
            empty.setPadding(20, 20, 20, 20);
            listContainer.addView(empty);
        }
        
        PackageManager pm = ctx.getPackageManager();
        for (String[] item : shown) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(10, 16, 10, 16);
            
            ImageView iv = new ImageView(ctx);
            int isize = 75;
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(isize, isize);
            ilp.setMargins(0, 0, 26, 0);
            iv.setLayoutParams(ilp);
            
            if (item[1].startsWith("app:")) {
                try { iv.setImageDrawable(pm.getApplicationIcon(item[1].substring(4))); }
                catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
            } else if (item[1].startsWith("act:RUN_SHORTCUT_")) {
                try {
                    String scId = item[1].substring(17);
                    String uriStr = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                    Intent scIntent = Intent.parseUri(uriStr, 0);
                    ComponentName cn = scIntent.getComponent();
                    if (cn != null) iv.setImageDrawable(pm.getActivityIcon(cn));
                    else iv.setImageResource(android.R.drawable.ic_menu_send);
                } catch (Exception e) {
                    iv.setImageResource(android.R.drawable.ic_menu_send);
                }
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_view);
                iv.setColorFilter(Color.WHITE);
            }
            row.addView(iv);

            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(item[0]); tvLabel.setTextColor(Color.WHITE); tvLabel.setTextSize(14f);
            row.addView(tvLabel);
            final String ref = item[1];
            row.setOnClickListener(v -> { runItem(ref); closeMenu(); });
            listContainer.addView(row);
        }
        
        listContainer.measure(
            View.MeasureSpec.makeMeasureSpec(menuLp.width, View.MeasureSpec.EXACTLY), 
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int listHeight = listContainer.getMeasuredHeight();
        int maxHeight = prefs.getInt("bubble_bg_h", 800);
        int finalHeight = Math.min(listHeight + 30, maxHeight); 
        
        View scroll = (View) listContainer.getParent();
        if (scroll != null) {
            LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) scroll.getLayoutParams();
            slp.height = finalHeight;
            scroll.setLayoutParams(slp);
        }
        recalculateMenuPosition();
    }

    private List<String[]> buildItems(String type) {
        List<String[]> out = new ArrayList<>();
        if (type.equals("SEARCH") || type.equals("ALL")) {
            out.addAll(buildItems("APP")); out.addAll(buildItems("SHORTCUT")); out.addAll(buildItems("SYSTEM"));
            out.addAll(buildItems("UTILITY")); out.addAll(buildItems("TRIGGER")); out.addAll(buildItems("INTENT"));
            out.addAll(buildItems("MACRO")); out.addAll(buildItems("PANEL"));
            return out;
        }
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
                // Đọc toàn bộ shortcut hệ thống
                for (ResolveInfo ri : ShortcutScanner.getProviders(ctx)) {
                    out.add(new String[]{ri.loadLabel(ctx.getPackageManager()).toString(), "act:CREATE_SHORTCUT_" + ri.activityInfo.packageName + "/" + ri.activityInfo.name});
                }
                break;
            }
            case "SYSTEM": {
                String[][] sys = { {"BACK","Quay lại"},{"HOME","Màn chính"},{"RECENTS","Đa nhiệm"},{"SCREEN_OFF","Tắt màn hình"},{"FLASH","Đèn pin"},{"SCREENSHOT","Chụp màn hình"},{"CAMERA","Camera"},{"VOLUME","Âm lượng"},{"POWER_DIALOG","Menu nguồn"},{"NOTIFICATIONS","Thông báo"},{"QUICK_SETTINGS","Cài đặt nhanh"},{"SPLIT_SCREEN","Chia đôi màn hình"},{"SCREEN_RECORD","Quay màn hình"},{"AUTO_ROTATE_TOGGLE","Tự động xoay"}, {"TRIGGER_ACC_MENU_2F", "Giả lập 2 ngón"} };
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
                for (String id : csvToList(prefs.getString("intent_ids", "")))
                    out.add(new String[]{prefs.getString("intent_" + id + "_name", "Intent"), "act:INTENT_" + id});
                break;
            }
            case "MACRO": {
                for (String id : csvToList(prefs.getString("macro_ids", "")))
                    out.add(new String[]{prefs.getString("macro_" + id + "_name", "Macro"), "act:MACRO_" + id});
                break;
            }
            case "PANEL": {
                for (String id : csvToList(prefs.getString("pack_panel_ids", "")))
                    out.add(new String[]{prefs.getString("pack_panel_" + id + "_name", "Panel"), "act:PANEL_" + id});
                break;
            }
        }
        return out;
    }

    private List<String> csvToList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
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
