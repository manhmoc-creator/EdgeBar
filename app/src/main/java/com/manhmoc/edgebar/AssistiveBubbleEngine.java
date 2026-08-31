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
    
    // Lưu trữ vị trí Bong Bóng
    private int restoreBubbleX = -1, restoreBubbleY = -1;
    private ValueAnimator jumpAnim;

    private GestureDetector gestureDetector;
    private float sx, sy, lastX, lastY;
    private long lastTapUpTime = 0;
    private boolean isHolding = false;
    private final Handler tapHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingTapRunnable = null;
    
    // Gia tốc kế đo lực vuốt (ném bong bóng)
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
        if (key.equals("bubble_en") || key.equals("bubble_size") || key.equals("bubble_icon_size")) { destroyAll(); rebuild(); }
    }

    public void destroy() { destroyAll(); }

    private void destroyAll() {
        closeMenu();
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }

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
        final int MARGIN = 30; 
        
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
                        
                        // YÊU CẦU: Thuật toán ném (Fling) hoặc Snap
                        int targetX;
                        if (vX > 1500) targetX = dm.widthPixels - bSize - MARGIN; // Ném mạnh sang phải
                        else if (vX < -1500) targetX = MARGIN; // Ném mạnh sang trái
                        else targetX = (bubbleLp.x + bSize / 2 < dm.widthPixels / 2) ? MARGIN : dm.widthPixels - bSize - MARGIN;
                        
                        // Trôi thêm 1 xíu theo lực ném dọc cho mượt
                        int targetY = bubbleLp.y + (int) (vY * 0.12f);
                        targetY = Math.max(MARGIN, Math.min(targetY, dm.heightPixels - bSize - MARGIN));
                        
                        ValueAnimator snapAnim = ValueAnimator.ofFloat(0f, 1f);
                        snapAnim.setDuration(400); 
                        snapAnim.setInterpolator(new OvershootInterpolator(0.9f)); 
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
        int targetX = dm.widthPixels / 2 - bSize / 2; 
        
        // CỐ ĐỊNH vị trí gốc TẠI ĐÂY trước khi nó bị anim trôi đi
        restoreBubbleX = bubbleLp.x; 
        restoreBubbleY = bubbleLp.y;

        int startX = bubbleLp.x;
        
        ValueAnimator centerAnim = ValueAnimator.ofFloat(0f, 1f);
        centerAnim.setDuration(250); 
        centerAnim.setInterpolator(new DecelerateInterpolator(1.5f)); 
        centerAnim.addUpdateListener(a -> {
            float val = (float) a.getAnimatedValue();
            bubbleLp.x = (int) (startX + (targetX - startX) * val);
            try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
        });
        centerAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                openMenu(); 
            }
        });
        centerAnim.start();
    }

    private void toggleMenu() { if (menuOverlay != null) closeMenu(); else openMenu(); }

    private void openMenu() {
        if (menuOverlay != null) return;
        selectedIdx = null;

        FrameLayout overlay = new FrameLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    closeMenu(); return true;
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
        
        recalculateMenuPosition(); // Đo đạc chính xác chiều cao thực để gán vị trí Y
        
        panelCard.setAlpha(0f);
        panelCard.setScaleX(0.85f);
        panelCard.setScaleY(0.85f);
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

        // Đo chiều cao thực tế của giao diện Panel hiện tại
        panelCard.measure(
            View.MeasureSpec.makeMeasureSpec(menuLp.width, View.MeasureSpec.EXACTLY), 
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST)
        );
        int actualPh = panelCard.getMeasuredHeight();

        // Mặc định ưu tiên mở bên dưới bong bóng
        int targetY = bubbleLp.y + bSize + gap;
        // Nếu cắn đáy, đẩy ngược LÊN TRÊN bong bóng đúng bằng chiều cao thực tế vừa đo
        if (targetY + actualPh > dm.heightPixels - margin) { 
            targetY = bubbleLp.y - actualPh - gap;
        }
        menuLp.y = Math.max(margin, targetY);
        
        if (menuOverlay != null && menuOverlay.isAttachedToWindow()) {
            try { wm.updateViewLayout(menuOverlay, menuLp); } catch (Exception ignored) {}
        }
    }
    private void closeMenu() {
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        selectedIdx = null;
        
        if (jumpAnim != null) jumpAnim.cancel();
        if (restoreBubbleX != -1 && restoreBubbleY != -1) {
            jumpAnim = ValueAnimator.ofFloat(0f, 1f);
            jumpAnim.setDuration(350);
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
        
        // Hàng 3 cấu trúc rời rạc
        LinearLayout row3 = new LinearLayout(ctx);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setWeightSum(4f); 
        
        LinearLayout.LayoutParams r3Lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        r3Lp.setMargins(0, 15, 0, 0);
        row3.setLayoutParams(r3Lp);
        
        row3.addView(buildNodeButton(6, true, 1f));
        row3.addView(buildSearchBar(2f));
        row3.addView(buildNodeButton(7, true, 1f));
        card.addView(row3);
        
        return card;
    }

    private LinearLayout buildNodeRow(int... idxs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(idxs.length);
        for (int idx : idxs) row.addView(buildNodeButton(idx, true, 1f));
        return row;
    }

    private FrameLayout buildNodeButton(int idx, boolean forceBg, float weight) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            weight == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : 0, 
            LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(10, 10, 10, 10);
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

        if (forceBg || (selectedIdx != null && selectedIdx == idx)) {
            GradientDrawable boxBg = new GradientDrawable();
            // Nút 6, 7 tròn vo (100f), nút 0-5 hình vuông bo cạnh (36f)
            boxBg.setCornerRadius(idx >= 6 ? 100f : 36f);
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
            panelCard.addView(buildNodeRow(3, 4, 5));
            LinearLayout row3 = new LinearLayout(ctx);
            row3.setOrientation(LinearLayout.HORIZONTAL);
            row3.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable fabBg = new GradientDrawable();
            fabBg.setColor(Color.parseColor("#33000000"));
            fabBg.setCornerRadius(100f);
            row3.setBackground(fabBg);
            LinearLayout.LayoutParams r3Lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
            r3Lp.setMargins(15, 15, 15, 15);
            row3.setLayoutParams(r3Lp);
            
            row3.addView(buildNodeButton(6, false, 0));
            row3.addView(buildSearchBar(1f));
            row3.addView(buildNodeButton(7, false, 0));
            panelCard.addView(row3);

            recalculateMenuPosition(); // Tính toán và gán lại vị trí Y
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
        et.setHint("🔍 Tìm ứng dụng…");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#33000000"));
        bg.setCornerRadius(100f);
        et.setBackground(bg);
        et.setPadding(28, 14, 28, 14);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(10, 10, 10, 10);
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
        
        PackageManager pm = ctx.getPackageManager();
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
                try { iv.setImageDrawable(pm.getApplicationIcon(item[1].substring(4))); }
                catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
            } else if (item[1].startsWith("act:CREATE_SHORTCUT_")) {
                try {
                    String[] split = item[1].substring(20).split("/");
                    Drawable d = pm.getActivityIcon(new ComponentName(split[0], split[1]));
                    iv.setImageDrawable(d);
                } catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
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
        
        // Đo chiều cao thực của List để không giãn quá chiều cao Max
        int maxHeight = prefs.getInt("bubble_bg_h", 800);
        list.measure(
            View.MeasureSpec.makeMeasureSpec(menuLp.width, View.MeasureSpec.EXACTLY), 
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int listHeight = list.getMeasuredHeight();
        int finalHeight = Math.min(listHeight + 30, maxHeight); // Cộng thêm padding bù trừ
        
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, finalHeight);
        panelCard.addView(scroll, slp);

        recalculateMenuPosition(); // Tính toán và gán lại vị trí Y
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
                // Quét Shortcut trong hệ thống thay vì nội bộ
                for (ResolveInfo ri : ShortcutScanner.getProviders(ctx)) {
                    out.add(new String[]{ri.loadLabel(ctx.getPackageManager()).toString(), "act:CREATE_SHORTCUT_" + ri.activityInfo.packageName + "/" + ri.activityInfo.name});
                }
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
