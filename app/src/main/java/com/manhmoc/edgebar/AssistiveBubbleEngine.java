package com.manhmoc.edgebar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.res.Configuration;
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
import java.util.function.Supplier; 
private DisplayMetrics getRealMetrics() {
    DisplayMetrics dm = new DisplayMetrics();
    try { wm.getDefaultDisplay().getRealMetrics(dm); }
    catch (Exception e) { dm = ctx.getResources().getDisplayMetrics(); }
    return dm;
}
public class AssistiveBubbleEngine {
    private Context ctx; private WindowManager wm; private SharedPreferences prefs; private boolean isAnyMode;
    private View bubbleView; private WindowManager.LayoutParams bubbleLp;
    private FrameLayout menuOverlay; private WindowManager.LayoutParams menuLp;
    private LinearLayout panelCard;
    
    private final FrameLayout[] nodeButtons = new FrameLayout[9];
    private Integer selectedMainIdx = null;
    private Integer selectedSubIdx = null;
    private String currentSubmenu = null; 
private static final java.util.concurrent.ExecutorService bubbleIconExecutor =
    java.util.concurrent.Executors.newFixedThreadPool(4);
    private static final int BUBBLE_ICON_CACHE_LIMIT = 60;
private static final LinkedHashMap<String, Drawable> bubbleIconCache =
    new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) { return size() > BUBBLE_ICON_CACHE_LIMIT; }
    };
private final Handler bubbleIconHandler = new Handler(Looper.getMainLooper());

private void loadIconAsync(String cacheKey, java.util.function.Supplier<Drawable> loader, ImageView iv, int iconSize, boolean isAppHint) {
    synchronized (bubbleIconCache) {
        Drawable cached = bubbleIconCache.get(cacheKey);
        if (cached != null) { applyIconToImageView(iv, cached, iconSize, isAppHint); return; }
    }
    iv.setTag(cacheKey);
    bubbleIconExecutor.execute(() -> {
        Drawable d = loader.get();
        if (d != null) synchronized (bubbleIconCache) { bubbleIconCache.put(cacheKey, d); }
        bubbleIconHandler.post(() -> {
            if (d != null && cacheKey.equals(iv.getTag())) applyIconToImageView(iv, d, iconSize, isAppHint);
        });
    }).start();
}

private Drawable resolveSubNodeIcon(String customOverride, String ref) {
    Drawable d = getCustomIcon(customOverride);
    if (d != null) return d;
    PackageManager pm = ctx.getPackageManager();
    try {
        if (ref.startsWith("app:")) return pm.getApplicationIcon(ref.substring(4));
        if (ref.startsWith("act:CREATE_SHORTCUT_")) {
            String[] split = ref.substring(20).split("/");
            return pm.getActivityIcon(new ComponentName(split[0], split[1]));
        }
        if (ref.startsWith("act:RUN_SHORTCUT_")) {
            String scId = ref.substring(17);
            String path = prefs.getString("shortcut_" + scId + "_icon_path", "");
            if (!path.isEmpty()) {
                Bitmap bmp = BitmapFactory.decodeFile(path);
                if (bmp != null) return new BitmapDrawable(ctx.getResources(), bmp);
            } else {
                Intent scIntent = Intent.parseUri(prefs.getString("shortcut_" + scId + "_intent_uri", ""), 0);
                ComponentName cn = scIntent.getComponent();
                if (cn != null) return pm.getActivityIcon(cn);
            }
        }
    } catch (Exception ignored) {}
    return null;
}
    private static final String[] DEFAULT_ORDER = {"APP","SHORTCUT","SYSTEM","INTENT","MACRO","PANEL","UTILITY","TRIGGER","SEARCH"};
    
    private int restoreBubbleX = -1, restoreBubbleY = -1;
    private ValueAnimator jumpAnim;
    private ComponentCallbacks configCallbacks;
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
        if (key.equals("bubble_en") || key.equals("bubble_size") || key.equals("bubble_icon_size") || key.equals("bubble_main_icon") || key.equals("bubble_node_bg_alpha")) { 
            destroyAll(); rebuild(); 
        }
    }

public void destroy() { destroyAll(); }

// [MỚI] Cho phép EdgeBarService tạm "xuyên thấu" bong bóng trong lúc bắn
// cử chỉ giả lập (TRIGGER_*) — nếu không, chính cửa sổ overlay của bong bóng
// sẽ chặn cú chạm giả lập ngay tại toạ độ xuất phát, không bao giờ lọt
// xuống app phía dưới được.
public void setBubbleTouchable(boolean touchable) {
    if (bubbleView == null || bubbleLp == null) return;
    try {
        if (touchable) bubbleLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else bubbleLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(bubbleView, bubbleLp);
    } catch (Exception ignored) {}
}

        private void destroyAll() {
        closeMenu();
        unregisterRotationWatcher(); // [FIX] hủy đăng ký để tránh leak khi service dừng
        if (bubbleView != null) { try { wm.removeView(bubbleView); } catch (Exception ignored) {} bubbleView = null; }
    }
    private void registerRotationWatcher() {
    if (configCallbacks != null) return;
    configCallbacks = new ComponentCallbacks() {
        @Override public void onConfigurationChanged(Configuration newConfig) {
            handleOrientationChange(newConfig.orientation);
        }
        @Override public void onLowMemory() {}
    };
    try { ctx.registerComponentCallbacks(configCallbacks); } catch (Exception ignored) {}
}

private void unregisterRotationWatcher() {
    if (configCallbacks != null) {
        try { ctx.unregisterComponentCallbacks(configCallbacks); } catch (Exception ignored) {}
        configCallbacks = null;
    }
}

private int clampPx(int v, int min, int max) { return Math.max(min, Math.min(v, max)); }

    private static final int BUBBLE_EDGE_MARGIN = 30;

    private void handleOrientationChange(int orientation) {
    if (bubbleView == null || bubbleLp == null) return;
    if (menuOverlay != null) closeMenu();

    // [FIX] Đợi 180ms để Resources/WindowManager ổn định hẳn sau khi xoay,
    // tránh đọc DisplayMetrics còn dở dang (nguyên nhân bong bóng "rơi vào giữa").
    bubbleIconHandler.postDelayed(() -> {
        if (bubbleView == null || bubbleLp == null) return;
        DisplayMetrics dm = getRealMetrics();
        int bSize = prefs.getInt("bubble_size", 120);
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            prefs.edit().putInt("bubble_x_portrait", bubbleLp.x).putInt("bubble_y_portrait", bubbleLp.y).apply();
            int savedX = prefs.getInt("bubble_x_landscape", Integer.MIN_VALUE);
            int savedY = prefs.getInt("bubble_y_landscape", Integer.MIN_VALUE);
            int targetX, targetY;
            if (savedX != Integer.MIN_VALUE) {
                targetX = savedX; targetY = savedY;
            } else {
                boolean wasNearLeft = bubbleLp.x < (dm.heightPixels / 2);
                targetX = wasNearLeft ? BUBBLE_EDGE_MARGIN : dm.widthPixels - bSize - BUBBLE_EDGE_MARGIN;
                targetY = bubbleLp.y;
            }
            bubbleLp.x = clampPx(targetX, 0, dm.widthPixels - bSize);
            bubbleLp.y = clampPx(targetY, BUBBLE_EDGE_MARGIN, dm.heightPixels - bSize - BUBBLE_EDGE_MARGIN);
        } else {
            prefs.edit().putInt("bubble_x_landscape", bubbleLp.x).putInt("bubble_y_landscape", bubbleLp.y).apply();
            int rawX = prefs.getInt("bubble_x_portrait", prefs.getInt("bubble_x", 40));
            int targetY = prefs.getInt("bubble_y_portrait", prefs.getInt("bubble_y", 600));
            boolean nearLeft = rawX + bSize / 2 < dm.widthPixels / 2;
            int targetX = nearLeft ? BUBBLE_EDGE_MARGIN : dm.widthPixels - bSize - BUBBLE_EDGE_MARGIN;
            bubbleLp.x = clampPx(targetX, 0, dm.widthPixels - bSize);
            bubbleLp.y = clampPx(targetY, BUBBLE_EDGE_MARGIN, dm.heightPixels - bSize - BUBBLE_EDGE_MARGIN);
        }
        try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
        prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
    }, 180);
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

    private void applyIconToImageView(ImageView iv, Drawable d, int iconSize, boolean isApp) {
        if (d == null) return;
        if (isApp) {
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(0, 0, 0, 0); 
        } else {
            d = d.mutate(); 
            d.setTint(Color.WHITE);
            
            // [FIX LAG KHỦNG KHIẾP BUBBLE] 
            // KHÔNG GỌI PanelEngine.normalizeIconBitmap() ở đây! Nó làm Main Thread bị treo 1-2s.
            // Dùng scale có sẵn của ImageView cực nhanh và không tốn CPU.
            iv.setImageDrawable(d);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            
            // Tính toán bù đệm để System Icon có kích thước tương đương với lúc normalize
            int pad = (int) (iconSize * 0.15f); 
            iv.setPadding(pad, pad, pad, pad);
        }
    }

    private void buildBubble() {
        if (bubbleView != null) return;
        ImageView iv = new ImageView(ctx);
        int size = prefs.getInt("bubble_size", 120);
        
        Drawable customIcon = getCustomIcon(prefs.getString("bubble_main_icon", ""));
        applyIconToImageView(iv, customIcon, size, customIcon != null && prefs.getString("bubble_main_icon", "").startsWith("app:"));
        if (customIcon == null) {
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
        DisplayMetrics dmInit = ctx.getResources().getDisplayMetrics();
        bubbleLp.x = clampPx(prefs.getInt("bubble_x", 40), 0, dmInit.widthPixels - size);
        bubbleLp.y = clampPx(prefs.getInt("bubble_y", 600), 0, dmInit.heightPixels - size);
        try { wm.addView(iv, bubbleLp); bubbleView = iv; } catch (Exception e) { return; }
        attachDragTouch();
        registerRotationWatcher(); // [FIX] tự canh lại vị trí mỗi khi xoay màn, không còn "biến mất"
    }

    private void attachDragTouch() {
        final float[] downRaw = new float[2];
        final boolean[] isDragging = {false};
        final int MARGIN = 30; 
        
                final long[] lastTapUpMs = {0};
        final boolean[] longFiredFlag = {false};
        final Runnable[] pendingSingleTap = {null};
        final Handler tapHandler = new Handler(Looper.getMainLooper());
        final Runnable longPressCheck = () -> {
            longFiredFlag[0] = true;
            fireGestureAction("long");
        };
        final int DTAP_WINDOW_MS = 220; // ngắn hơn hẳn timeout mặc định của hệ thống (~300-400ms)

        bubbleView.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longFiredFlag[0] = false;
                    tapHandler.postDelayed(longPressCheck, 500);
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
                        if (menuOverlay != null) followMenuDuringDrag(); // [MỚI] Panel đi theo bong bóng
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tapHandler.removeCallbacks(longPressCheck);
                    if (!isDragging[0] && !longFiredFlag[0] && e.getActionMasked() == MotionEvent.ACTION_UP) {
                        long now = System.currentTimeMillis();
                        if (pendingSingleTap[0] != null) {
                            tapHandler.removeCallbacks(pendingSingleTap[0]);
                            pendingSingleTap[0] = null;
                            lastTapUpMs[0] = 0;
                            fireGestureAction("dtap"); // chạm lần 2 trong cửa sổ ngắn -> double tap
                        } else {
                            lastTapUpMs[0] = now;
                            pendingSingleTap[0] = () -> {
                                pendingSingleTap[0] = null;
                                if (menuOverlay != null) {
                                    if (currentSubmenu != null) {
                                        currentSubmenu = null; selectedSubIdx = null; selectedMainIdx = null;
                                        refreshPanelCard();
                                    } else closeMenu();
                                } else moveToCenterAndOpenMenu();
                            };
                            tapHandler.postDelayed(pendingSingleTap[0], DTAP_WINDOW_MS);
                        }
                    }
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

                        DisplayMetrics dm = getRealMetrics();
                        int bSize = prefs.getInt("bubble_size", 120);
                        
                        int targetX;
                        if (vX > 1500) targetX = dm.widthPixels - bSize - MARGIN; 
                        else if (vX < -1500) targetX = MARGIN; 
                        else targetX = (bubbleLp.x + bSize / 2 < dm.widthPixels / 2) ? MARGIN : dm.widthPixels - bSize - MARGIN;
                        
                        int calculatedTargetY = bubbleLp.y + (int) (vY * 0.12f);
                        final int finalTargetYDrag = Math.max(MARGIN, Math.min(calculatedTargetY, dm.heightPixels - bSize - MARGIN));
                        final int finalTargetXDrag = targetX;
                        
ValueAnimator snapAnim = ValueAnimator.ofFloat(0f, 1f);
snapAnim.setDuration(320); 
// [FIX] OvershootInterpolator khiến giá trị vọt vượt quá 1.0 rồi tụt lại
// -> đây chính là cảm giác "bật lại như quả bóng" khi gần đường tâm.
// Đổi sang DecelerateInterpolator: mượt, giảm tốc dần về đích, không overshoot,
// đường đi luôn là 1 đường thẳng liên tục từ vị trí thả tay tới điểm neo cạnh.
snapAnim.setInterpolator(new DecelerateInterpolator(1.8f)); 
int startX = bubbleLp.x; int startY = bubbleLp.y;
                        snapAnim.addUpdateListener(a -> {
                            float val = (float) a.getAnimatedValue();
                            bubbleLp.x = (int) (startX + (finalTargetXDrag - startX) * val);
                            bubbleLp.y = (int) (startY + (finalTargetYDrag - startY) * val);
                            try { wm.updateViewLayout(bubbleView, bubbleLp); } catch (Exception ignored) {}
                            if (menuOverlay != null) followMenuDuringDrag(); // [MỚI] Panel đi theo lúc tự trượt về mép
                        });
                        snapAnim.addListener(new AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(Animator animation) {
                                prefs.edit().putInt("bubble_x", bubbleLp.x).putInt("bubble_y", bubbleLp.y).apply();
                                // [MỚI] Sau khi kéo xong trong lúc Panel đang mở, chốt lại vị trí này làm
                                // "điểm phục hồi" — tránh việc tap để đóng Panel làm bong bóng nhảy ngược
                                // về đúng vị trí TRƯỚC KHI mở Panel (đè mất thao tác kéo vừa làm).
                                if (menuOverlay != null) { restoreBubbleX = bubbleLp.x; restoreBubbleY = bubbleLp.y; }
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

    int startX = bubbleLp.x + prefs.getInt("bubble_size", 120) / 2;
    int startY = bubbleLp.y + prefs.getInt("bubble_size", 120) / 2;
    if (acts.contains("TRIGGER_ACC_MENU_2F")) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        startX = dm.widthPixels / 2;
        startY = dm.heightPixels - 10;
    } else if (acts.contains("TRIGGER_")) {
        // [MỚI] Kẹp biên: bong bóng bắn cử chỉ trực tiếp (dtap/long) khi CHƯA
        // nhảy ra giữa màn hình -> đảm bảo còn đủ khoảng trống cho quãng vuốt
        // giả lập (sim_swipe_dist_pct), tránh dính sát mép gây "gãy"/hụt cử chỉ.
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int margin = (int) (Math.min(dm.widthPixels, dm.heightPixels)
            * (prefs.getInt("sim_swipe_dist_pct", 80) / 100f)) + 60;
        startX = Math.max(margin, Math.min(dm.widthPixels - margin, startX));
        startY = Math.max(margin, Math.min(dm.heightPixels - margin, startY));
    }
    ipc.putExtra("startX", startX);
    ipc.putExtra("startY", startY);

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
        DisplayMetrics dm = getRealMetrics();
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
        
        DisplayMetrics dm = getRealMetrics(); 
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
        menuLp.x = dm.widthPixels / 2 - menuLp.width / 2; // [FIX] luôn ép lại giữa màn, không kế thừa X cũ

        if (menuOverlay != null && menuOverlay.isAttachedToWindow()) {
            try { wm.updateViewLayout(menuOverlay, menuLp); } catch (Exception ignored) {}
        }
    }
    /** [MỚI] Bám theo bong bóng khi user đang KÉO trong lúc Panel đang mở — đổi cả
     *  X lẫn Y theo đúng vị trí tâm bong bóng hiện tại, kẹp trong màn hình. Tách riêng
     *  khỏi recalculateMenuPosition() vì hàm đó cố định X = giữa màn hình (phục vụ
     *  hiệu ứng bong bóng tự nhảy vào giữa lúc mới mở Panel) — không được đụng vào. */
    private void followMenuDuringDrag() {
        if (panelCard == null || menuLp == null || menuOverlay == null) return;
        DisplayMetrics dm = getRealMetrics();
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
        menuLp.y = Math.max(margin, calculatedTargetY);

        int targetX = bubbleLp.x + bSize / 2 - menuLp.width / 2;
        menuLp.x = Math.max(margin, Math.min(targetX, dm.widthPixels - menuLp.width - margin));

        try { wm.updateViewLayout(menuOverlay, menuLp); } catch (Exception ignored) {}
    }

    private void closeMenu() {
        if (menuOverlay != null) { try { wm.removeView(menuOverlay); } catch (Exception ignored) {} menuOverlay = null; }
        selectedMainIdx = null; selectedSubIdx = null; currentSubmenu = null;
        
        if (jumpAnim != null) jumpAnim.cancel();
        if (restoreBubbleX != -1 && restoreBubbleY != -1) {
            jumpAnim = ValueAnimator.ofFloat(0f, 1f);
jumpAnim.setDuration(250);
jumpAnim.setInterpolator(new DecelerateInterpolator(1.6f)); // đồng bộ, không overshoot
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
    if (!csv.isEmpty()) {
        // split(",", -1) GIỮ NGUYÊN vị trí các ô rỗng đã được sắp xếp sẵn theo
        // PREF_ORDER (góc phải-dưới -> ... -> tâm) khi lưu ở showBubbleNodePicker().
        // Không được lọc bỏ chuỗi rỗng ở đây, nếu không thứ tự ưu tiên sẽ bị phá.
        for (String s : csv.split(",", -1)) out.add(s.trim());
    }
    while (out.size() < 9) out.add("");
    if (out.size() > 9) out = new ArrayList<>(out.subList(0, 9));
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
        if (ref == null || ref.isEmpty()) return "Trống";
        if (ref.startsWith("app:")) {
            try { return ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(ref.substring(4),0)).toString(); }
            catch (Exception e) { return "App"; }
        } else if (ref.startsWith("act:CREATE_SHORTCUT_")) {
            try {
                String[] split = ref.substring(20).split("/");
                return ctx.getPackageManager().getActivityInfo(new ComponentName(split[0], split[1]), 0).loadLabel(ctx.getPackageManager()).toString();
            } catch (Exception e) { return "Shortcut"; }
        } else if (ref.startsWith("act:RUN_SHORTCUT_")) {
            return prefs.getString("shortcut_" + ref.substring(17) + "_name", "Shortcut");
        } else if (ref.startsWith("act:INTENT_")) {
            return prefs.getString("intent_" + ref.substring(11) + "_name", "Intent");
        } else if (ref.startsWith("act:MACRO_")) {
            return prefs.getString("macro_" + ref.substring(10) + "_name", "Macro");
        } else if (ref.startsWith("act:PANEL_")) {
            return prefs.getString("pack_panel_" + ref.substring(10) + "_name", "Panel");
        } else if (ref.startsWith("act:")) {
            List<String[]> allItems = buildItems("ALL");
            for (String[] it : allItems) {
                if (it[1].equals(ref)) return it[0];
            }
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
            for (int i = 0; i < 3; i++) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(3);
                for (int j = 0; j < 3; j++) {
                    int idx = i * 3 + j;
                    row.addView(buildMainButton(idx));
                }
                card.addView(row);
            }
        }
        return card;
    }

    private FrameLayout buildMainButton(int idx) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(10, 15, 10, 15);
        box.setLayoutParams(lp);

        String type = getMainOrder().get(idx);
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        
        FrameLayout iconBox = new FrameLayout(ctx);
        int iconSize = prefs.getInt("bubble_icon_size", 100);
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(iconSize + 40, iconSize + 40);
        iconBox.setLayoutParams(ibLp);
        
        int nodeAlpha = prefs.getInt("bubble_node_bg_alpha", 255);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setCornerRadius(100f); 
        boxBg.setColor(selectedMainIdx != null && selectedMainIdx == idx ? Color.parseColor("#8AB4F8") : Color.argb(nodeAlpha, 51, 51, 51));
        iconBox.setBackground(boxBg);

        ImageView iv = new ImageView(ctx);
        FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        iv.setLayoutParams(ivLp);
        
                String customOverride = prefs.getString("bubble_node_icon_" + type, "");
        boolean isAppIcon = customOverride.startsWith("app:");

        int fallbackRes;
        switch (type) {
            case "SYSTEM": fallbackRes = android.R.drawable.ic_menu_preferences; break;
            case "UTILITY": fallbackRes = android.R.drawable.ic_menu_manage; break;
            case "APP": fallbackRes = android.R.drawable.sym_def_app_icon; break;
            case "SHORTCUT": fallbackRes = android.R.drawable.ic_menu_send; break;
            case "TRIGGER": fallbackRes = android.R.drawable.ic_menu_directions; break;
            case "INTENT": fallbackRes = android.R.drawable.ic_menu_compass; break;
            case "SEARCH": fallbackRes = android.R.drawable.ic_menu_search; break;
            default: fallbackRes = android.R.drawable.ic_menu_view;
        }
        try { applyIconToImageView(iv, ctx.getDrawable(fallbackRes), iconSize, false); } catch (Exception ignored) {}

        if (!customOverride.isEmpty()) {
            loadIconAsync("main_" + type + "_" + customOverride, () -> getCustomIcon(customOverride), iv, iconSize, isAppIcon);
        }
        iconBox.addView(iv);
        
        TextView tv = new TextView(ctx);
        tv.setText(getLabelForType(type));
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 10, 0, 0);

        content.addView(iconBox); content.addView(tv);
        box.addView(content);
        nodeButtons[idx] = box;
        
        box.setOnLongClickListener(v -> { selectedMainIdx = idx; refreshPanelCard(); return true; });
        box.setOnClickListener(v -> {
            if (selectedMainIdx != null) {
                if (selectedMainIdx != idx) {
                    List<String> order = new ArrayList<>(getMainOrder());
                    Collections.swap(order, selectedMainIdx, idx);
                    prefs.edit().putString("bubble_node_order", TextUtils.join(",", order)).apply();
                }
                selectedMainIdx = null;
                refreshPanelCard();
            } else {
                selectedSubIdx = null; // [FIX] đảm bảo submenu mới mở luôn sạch, không dính lựa chọn cũ 
                currentSubmenu = type;
                refreshPanelCard();
            }
        });
        return box;
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
                row.addView(buildSubNodeButton(type, idx, items.get(idx)));
            }
            card.addView(row);
        }
    }

    private FrameLayout buildSubNodeButton(String type, int idx, String ref) {
        FrameLayout box = new FrameLayout(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(10, 15, 10, 15);
        box.setLayoutParams(lp);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        
        FrameLayout iconBox = new FrameLayout(ctx);
        int iconSize = prefs.getInt("bubble_icon_size", 100);
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(iconSize + 40, iconSize + 40);
        iconBox.setLayoutParams(ibLp);
        
        int nodeAlpha = prefs.getInt("bubble_node_bg_alpha", 255);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setCornerRadius(100f); 
        boxBg.setColor(selectedSubIdx != null && selectedSubIdx == idx ? Color.parseColor("#8AB4F8") : Color.argb(nodeAlpha, 51, 51, 51));
        iconBox.setBackground(boxBg);

                if (!ref.isEmpty()) {
            ImageView iv = new ImageView(ctx);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iv.setLayoutParams(ivLp);

            String customOverride = prefs.getString("bubble_node_icon_override_" + type + "_" + ref, "");

            int defaultIconRes = android.R.drawable.ic_menu_view;
            if (ref.equals("act:BACK")) defaultIconRes = android.R.drawable.ic_media_rew;
            else if (ref.equals("act:HOME")) defaultIconRes = android.R.drawable.ic_menu_compass;
            else if (ref.equals("act:RECENTS")) defaultIconRes = android.R.drawable.ic_menu_recent_history;
            else if (ref.equals("act:SCREEN_OFF")) defaultIconRes = android.R.drawable.ic_lock_lock;
            else if (ref.equals("act:POWER_DIALOG")) defaultIconRes = android.R.drawable.ic_lock_power_off;
            else if (ref.equals("act:SCREENSHOT") || ref.equals("act:CAMERA")) defaultIconRes = android.R.drawable.ic_menu_camera;
            else if (ref.equals("act:NOTIFICATIONS")) defaultIconRes = android.R.drawable.ic_dialog_email;
            else if (ref.equals("act:VOICE_RECORD") || ref.equals("act:TOGGLE_RECORD")) defaultIconRes = android.R.drawable.ic_btn_speak_now;
            try { applyIconToImageView(iv, ctx.getDrawable(defaultIconRes), iconSize, false); } catch (Exception ignored) {}

            boolean isAppIconFinal = customOverride.startsWith("app:") || ref.startsWith("app:")
                || ref.startsWith("act:CREATE_SHORTCUT_") || ref.startsWith("act:RUN_SHORTCUT_");
            loadIconAsync("sub_" + type + "_" + ref + "_" + customOverride,
                () -> resolveSubNodeIcon(customOverride, ref), iv, iconSize, isAppIconFinal);

            iconBox.addView(iv);
        }

        
        TextView tv = new TextView(ctx);
        tv.setText(getActionLabelForSubNode(ref));
        tv.setTextColor(ref.isEmpty() ? Color.GRAY : Color.WHITE);
        tv.setTextSize(11f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 10, 0, 0);

        content.addView(iconBox); content.addView(tv);
        box.addView(content);
        
        box.setOnLongClickListener(v -> { selectedSubIdx = idx; refreshPanelCard(); return true; });
        box.setOnClickListener(v -> {
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
        });
        return box;
    }

    private void buildSearchMenu(LinearLayout card) {
        EditText et = new EditText(ctx);
        et.setHint("🔍 Tìm kiếm hệ thống...");
        et.setHintTextColor(Color.parseColor("#9AA0A6"));
        et.setTextColor(Color.WHITE);
        et.setSingleLine(true);
        int nodeAlpha = prefs.getInt("bubble_node_bg_alpha", 255);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(nodeAlpha, 51, 51, 51));
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
        for (int p = 0; p < shown.size(); p++) {
            String[] item = shown.get(p);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(10, 16, 10, 16);
            
            ImageView iv = new ImageView(ctx);
            int isize = 75;
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(isize, isize);
            ilp.setMargins(0, 0, 26, 0);
            iv.setLayoutParams(ilp);
            
            Drawable customOvr = getCustomIcon(prefs.getString("bubble_node_icon_override_" + type + "_" + item[1], ""));
            boolean isCustomApp = prefs.getString("bubble_node_icon_override_" + type + "_" + item[1], "").startsWith("app:");
            
            if (customOvr != null) {
                if (!isCustomApp) { customOvr = customOvr.mutate(); customOvr.setTint(Color.WHITE); }
                iv.setImageDrawable(customOvr);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                if (item[1].startsWith("app:")) {
                    try { iv.setImageDrawable(pm.getApplicationIcon(item[1].substring(4))); }
                    catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
                } else if (item[1].startsWith("act:CREATE_SHORTCUT_")) {
                    try {
                        String[] split = item[1].substring(20).split("/");
                        Drawable d = pm.getActivityIcon(new ComponentName(split[0], split[1]));
                        iv.setImageDrawable(d);
                    } catch(Exception e) { iv.setImageResource(android.R.drawable.sym_def_app_icon); }
                } else if (item[1].startsWith("act:RUN_SHORTCUT_")) {
                    try {
                        String scId = item[1].substring(17);
                        String path = prefs.getString("shortcut_" + scId + "_icon_path", "");
                        if (!path.isEmpty()) {
                            iv.setImageBitmap(BitmapFactory.decodeFile(path));
                        } else {
                            Intent scIntent = Intent.parseUri(prefs.getString("shortcut_" + scId + "_intent_uri", ""), 0);
                            ComponentName cn = scIntent.getComponent();
                            if (cn != null) iv.setImageDrawable(pm.getActivityIcon(cn));
                            else iv.setImageResource(android.R.drawable.ic_menu_send);
                        }
                    } catch (Exception e) { iv.setImageResource(android.R.drawable.ic_menu_send); }
                } else {
                    int defaultIconRes = android.R.drawable.ic_menu_view;
                    if (item[1].equals("act:BACK")) defaultIconRes = android.R.drawable.ic_media_rew;
                    else if (item[1].equals("act:HOME")) defaultIconRes = android.R.drawable.ic_menu_compass;
                    else if (item[1].equals("act:RECENTS")) defaultIconRes = android.R.drawable.ic_menu_recent_history;
                    else if (item[1].equals("act:SCREEN_OFF")) defaultIconRes = android.R.drawable.ic_lock_lock;
                    else if (item[1].equals("act:POWER_DIALOG")) defaultIconRes = android.R.drawable.ic_lock_power_off;
                    else if (item[1].equals("act:SCREENSHOT") || item[1].equals("act:CAMERA")) defaultIconRes = android.R.drawable.ic_menu_camera;
                    else if (item[1].equals("act:NOTIFICATIONS")) defaultIconRes = android.R.drawable.ic_dialog_email;
                    else if (item[1].equals("act:VOICE_RECORD") || item[1].equals("act:TOGGLE_RECORD")) defaultIconRes = android.R.drawable.ic_btn_speak_now;
                    
                    iv.setImageResource(defaultIconRes);
                    iv.setColorFilter(Color.WHITE);
                }
            }
            row.addView(iv);

            TextView tvLabel = new TextView(ctx);
            tvLabel.setText(item[0]); tvLabel.setTextColor(Color.WHITE); tvLabel.setTextSize(14f);
            row.addView(tvLabel);

            final String ref = item[1];
            
            row.setOnClickListener(v -> {
                runItem(ref); 
                closeMenu();
            });

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

    private void refreshPanelCard() {
        if (panelCard != null) {
            panelCard.removeAllViews();
            if (currentSubmenu != null) {
                if (currentSubmenu.equals("SEARCH")) buildSearchMenu(panelCard);
                else buildSubmenuGrid(panelCard, currentSubmenu);
            } else {
                for (int i = 0; i < 3; i++) {
                    LinearLayout row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setWeightSum(3);
                    for (int j = 0; j < 3; j++) {
                        int idx = i * 3 + j;
                        row.addView(buildMainButton(idx));
                    }
                    panelCard.addView(row);
                }
            }
            recalculateMenuPosition();
        }
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
                // YÊU CẦU 3: Quét cả ứng dụng trong không gian riêng (Island) bằng LauncherApps
                android.os.UserManager um = (android.os.UserManager) ctx.getSystemService(Context.USER_SERVICE);
                android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                try {
                    for (android.os.UserHandle profile : um.getUserProfiles()) {
                        boolean island = !profile.equals(android.os.Process.myUserHandle());
                        for (android.content.pm.LauncherActivityInfo info : la.getActivityList(null, profile)) {
                            out.add(new String[]{info.getLabel().toString() + (island ? " [Island]" : ""), "app:" + info.getApplicationInfo().packageName});
                        }
                    }
                } catch (Exception ignored) {}
                out.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
                break;
            }
            case "SHORTCUT": {
                for (ResolveInfo ri : ShortcutScanner.getProviders(ctx)) {
                    if (ri.activityInfo.packageName.equals(ctx.getPackageName())) continue; 
                    out.add(new String[]{ri.loadLabel(ctx.getPackageManager()).toString(), "act:CREATE_SHORTCUT_" + ri.activityInfo.packageName + "/" + ri.activityInfo.name});
                }
                // YÊU CẦU 3: Đã gỡ bỏ vòng lặp quét shortcut do EdgeBar tự tạo (RUN_SHORTCUT)
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
            
            // YÊU CẦU 6: Định tuyến vị trí điểm neo khi Cử chỉ Giả lập gọi từ Bảng điều khiển
            int startX = bubbleLp.x + prefs.getInt("bubble_size", 120) / 2;
            int startY = bubbleLp.y + prefs.getInt("bubble_size", 120) / 2;
            if (ref.contains("TRIGGER_ACC_MENU_2F")) {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                startX = dm.widthPixels / 2;
                startY = dm.heightPixels - 10;
            }
            ipc.putExtra("startX", startX);
            ipc.putExtra("startY", startY);
            
            ctx.sendBroadcast(ipc);
        }
    }
}
