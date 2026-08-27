package com.manhmoc.edgebar;

import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.DashPathEffect;
import android.graphics.BlurMaskFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast; // ← THÊM DÒNG NÀY
import java.io.InputStream;
import java.util.Collections;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.ImageReader;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.DisplayMetrics;
// ĐẰNG TRƯỚC
public class HomescreenService extends Service {
    // ĐẰNG SAU (Biến cũ của HomescreenService)
    public static boolean isRunning = false;
    public static volatile String liveForegroundPkg = "";
    private boolean isHomeOverlayShortcutOn() {
    return prefs.getBoolean("shortcut_home_on", false);
}
private boolean isAccessibleHomeShortcutOn() {
    return prefs.getBoolean("shortcut_acc_home_on", false);
}
    private WindowManager wm;
    private View[] bars = new View[12];
    private View[] corners = new View[4];
    private final java.util.Map<View, String> lastLayoutSig = new java.util.HashMap<>();
    private void updateLayoutIfChanged(View v, WindowManager.LayoutParams p) {
        String sig = p.flags + "|" + p.width + "|" + p.height + "|" + p.x + "|" + p.y + "|" + p.gravity;
        if (sig.equals(lastLayoutSig.get(v))) return; // không đổi -> zero IPC
        lastLayoutSig.put(v, sig);
        try { wm.updateViewLayout(v, p); } catch (Exception ignored) {}
    }
private final java.util.Map<View, String> lastGestureSig = new java.util.HashMap<>();
    private void applyAntiTapjacking(View v, int w, int h) {
    // [FIX VUỐT CẠNH KHÔNG BACK ĐƯỢC] Không loại trừ cử chỉ hệ thống nữa — việc
    // gọi setSystemGestureExclusionRects() ở đây vô tình chặn luôn cử chỉ vuốt
    // cạnh Back của Android tại vị trí Bar/Corner, càng vào sâu càng dính nhiều
    // Bar/Corner nên càng khó thoát ra. Trả rỗng để không loại trừ gì cả, nhường
    // đúng nghĩa cử chỉ Back cho hệ điều hành.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
    try {
        v.setSystemGestureExclusionRects(java.util.Collections.emptyList());
    } catch (Exception ignored) {}
}
    private FlashView fV;
    private GestureRippleView rippleView;
    // [MỚI] Chỉ báo ghi âm (chấm đỏ + mm:ss)
    private LinearLayout recIndicatorView;
    private TextView recIndicatorText;
    private View recIndicatorDot;
    private ValueAnimator recBlinkAnim;
    private boolean recIndicatorTestMode = false;
private boolean recIndicatorTestPaused = false;
        private void ensureRippleView() {
        if (rippleView != null) return;
        GestureRippleView newView = new GestureRippleView(this);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        try {
            wm.addView(newView, p);
            rippleView = newView; // chỉ gán khi addView() thành công
        } catch (Exception e) {
            rippleView = null;
        }
    }
    private void removeRippleViewIfIdle() {
        if (rippleView == null) return;
        try { wm.removeView(rippleView); } catch (Exception ignored) {}
        rippleView = null;
    }
        private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private SharedPreferences prefs;
    private KeyguardManager km;
    private Vibrator vibrator;
    private PanelEngine panelEngine;
    private int lastKbdHeight = 0;
    // [FIX] Bổ sung các biến này vì SidebarTouchListener bên dưới có gọi tới,
    // nhưng HomescreenService trước đây chưa khai báo (khác với EdgeBarService).
    private volatile boolean isDispatchingSyntheticGesture = false;
    private float globalTouchStartX = -1f, globalTouchStartY = -1f, globalTouchEndX = -1f, globalTouchEndY = -1f;
    private long lastSyncMs = 0;
    private final Handler appLockPollHandler = new Handler(android.os.Looper.getMainLooper());
    private String lastPolledFgPkg = "";
    private static final long APPLOCK_POLL_MS = 400; // đủ nhanh, không tốn pin đáng kể

    private void startAppLockPolling() {
        appLockPollHandler.postDelayed(appLockPollRunnable, APPLOCK_POLL_MS);
    }
    private final Runnable appLockPollRunnable = new Runnable() {
        @Override public void run() {
            try {
                String lockList = prefs.getString("applock_list", "");
                if (!lockList.isEmpty()) {
                    android.app.usage.UsageStatsManager usm =
                        (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                    long now = System.currentTimeMillis();
                    android.app.usage.UsageEvents events = usm.queryEvents(now - 3000, now);
                    android.app.usage.UsageEvents.Event ev = new android.app.usage.UsageEvents.Event();
                    String fg = lastPolledFgPkg;
                    while (events.hasNextEvent()) {
                        events.getNextEvent(ev);
                        if (ev.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                            fg = ev.getPackageName();
                        }
                    }
                    if (!fg.isEmpty() && !fg.equals(lastPolledFgPkg)) {
                        lastPolledFgPkg = fg;
                        AppLockHelper.check(HomescreenService.this, prefs, fg);
                    }
                }
            } catch (Exception ignored) {}
            appLockPollHandler.postDelayed(this, APPLOCK_POLL_MS);
        }
    };
    private static final long SYNC_THROTTLE_MS = 150;
    private long accCheckTimestamp = 0;
    private static final int KBD_HEIGHT_CHANGE_THRESHOLD = 20;
    private final String[] BARS = {"b_c", "r", "l", "r_u", "r_c", "r_d", "t_c", "t_r", "t_l", "l_u", "l_c", "l_d"};
    private final int[] GRAV = {
        Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
        Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
        Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
        Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
    };
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};
    private static final int BAR_ICON_CACHE_LIMIT = 40;
private static final java.util.LinkedHashMap<String, android.graphics.Bitmap> barIconCache =
    new java.util.LinkedHashMap<String, android.graphics.Bitmap>(16, 0.75f, true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String, android.graphics.Bitmap> e) {
            return size() > BAR_ICON_CACHE_LIMIT;
        }
    };
private android.graphics.Bitmap resolveBarIconBitmap(String ref, int size) {
    String key = ref + "_" + size;
    synchronized (barIconCache) {
        android.graphics.Bitmap cached = barIconCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
    }
    android.graphics.drawable.Drawable d = null;
    try {
        if (ref.startsWith("app:")) {
            d = getPackageManager().getApplicationIcon(ref.substring(4));
        } else if (ref.startsWith("poolc:")) {
            int idx = Integer.parseInt(ref.substring(6));
            int[] pool = PanelEngine.getCustomIconPool(this);
            if (idx >= 0 && idx < pool.length) d = getDrawable(pool[idx]);
        } else if (ref.startsWith("pool:")) {
            int idx = Integer.parseInt(ref.substring(5));
            if (idx >= 0 && idx < PanelEngine.SYSTEM_ICON_POOL.length) d = getDrawable(PanelEngine.SYSTEM_ICON_POOL[idx]);
        }
    } catch (Exception ignored) {}
    if (d == null) return null;
    d = d.mutate();
    d.setTint(Color.WHITE);
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    d.setBounds(0, 0, size, size);
    d.draw(c);
    synchronized (barIconCache) { barIconCache.put(key, bmp); }
    return bmp;
}
private java.util.List<android.graphics.Bitmap> resolveBarIcons(String csv, int size) {
    java.util.List<android.graphics.Bitmap> list = new java.util.ArrayList<>();
    if (csv == null || csv.isEmpty()) return list;
    for (String ref : csv.split(",")) {
        if (ref.trim().isEmpty()) continue;
        android.graphics.Bitmap b = resolveBarIconBitmap(ref.trim(), size);
        if (b != null) list.add(b);
    }
    return list;
}
        private class FlashView extends View {
    private Paint pCore = new Paint(), pGlowMid = new Paint(), pGlowOuter = new Paint();
    float radius = 40f; String cTheme = "WHITE";
    int aStyle = 0; private float phaseFraction = 0f;
    private int effW = 0, effH = 0;

    public FlashView(Context c) {
        super(c);
        for (Paint p : new Paint[]{pCore, pGlowMid, pGlowOuter}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setAntiAlias(true);
        }
        setLayerType(LAYER_TYPE_SOFTWARE, null); // bắt buộc để BlurMaskFilter hoạt động
        updateStyle();
    }

    public void updateStyle() {
        int baseAlpha = prefs.getInt("anim_alpha", 255);
        int thick = Math.max(4, prefs.getInt("anim_thick", 12));
        radius = prefs.getInt("anim_rad", 40);
        cTheme = prefs.getString("anim_color", "WHITE");
        aStyle = prefs.getInt("anim_style", 0);
        effW = prefs.getInt("anim_w", 0);
        effH = prefs.getInt("anim_h", 0);

        // Lõi: mảnh, sáng vừa phải, blur nhẹ để không "sắc lẻm"
        pCore.setStrokeWidth(thick * 0.35f);
        pCore.setAlpha((int)(baseAlpha * 0.9f));
        pCore.setMaskFilter(new BlurMaskFilter(thick * 0.5f, BlurMaskFilter.Blur.NORMAL));

        // Glow giữa: rộng hơn, mờ vừa
        pGlowMid.setStrokeWidth(thick * 1.4f);
        pGlowMid.setAlpha((int)(baseAlpha * 0.4f));
        pGlowMid.setMaskFilter(new BlurMaskFilter(thick * 1.3f, BlurMaskFilter.Blur.NORMAL));

        // Glow ngoài: rất rộng, rất mờ -> tạo halo lan tỏa như video
        pGlowOuter.setStrokeWidth(thick * 3f);
        pGlowOuter.setAlpha((int)(baseAlpha * 0.2f));
        pGlowOuter.setMaskFilter(new BlurMaskFilter(thick * 2.8f, BlurMaskFilter.Blur.NORMAL));

        if (getWidth() > 0) applyGradient(getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h);
    }

    private void applyGradient(int w, int h) {
        int[] cArr; switch(cTheme) {
            case "NEON": cArr=new int[]{Color.parseColor("#FF0055"), Color.parseColor("#7000FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FF0055")}; break;
            case "CYBERPUNK": cArr=new int[]{Color.parseColor("#F500FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FFDF00"), Color.parseColor("#F500FF")}; break;
            case "LAVA": cArr=new int[]{Color.parseColor("#FF0000"), Color.parseColor("#FF5A00"), Color.parseColor("#FF9A00"), Color.parseColor("#FF0000")}; break;
            case "OCEAN": cArr=new int[]{Color.parseColor("#005BEA"), Color.parseColor("#00C6FB"), Color.parseColor("#005BEA")}; break;
            case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000"), Color.parseColor("#00FF00")}; break;
            case "SUNSET": cArr=new int[]{Color.parseColor("#FF512F"), Color.parseColor("#DD2476"), Color.parseColor("#FF512F")}; break;
            case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"), Color.parseColor("#4285F4")}; break;
            case "AURORA": cArr=new int[]{Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"), Color.parseColor("#00E5FF"), Color.parseColor("#8E2DE2")}; break;
            case "ABYSS": cArr=new int[]{Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364"), Color.parseColor("#0F2027")}; break;
            case "COSMIC": cArr=new int[]{Color.parseColor("#FF00CC"), Color.parseColor("#333399"), Color.parseColor("#FF00CC")}; break;
            case "FOREST": cArr=new int[]{Color.parseColor("#11998E"), Color.parseColor("#38EF7D"), Color.parseColor("#11998E")}; break;
            case "FLAME": cArr=new int[]{Color.parseColor("#F12711"), Color.parseColor("#F5AF19"), Color.parseColor("#F12711")}; break;
            case "MIDNIGHT": cArr=new int[]{Color.parseColor("#1A2980"), Color.parseColor("#26D0CE"), Color.parseColor("#1A2980")}; break;
            case "TROPICAL": cArr=new int[]{Color.parseColor("#43C6AC"), Color.parseColor("#F8FFAE"), Color.parseColor("#43C6AC")}; break;
            case "CANDY": cArr=new int[]{Color.parseColor("#FF9A9E"), Color.parseColor("#FECFEF"), Color.parseColor("#FF9A9E")}; break;
            default: cArr=new int[]{Color.WHITE, Color.parseColor("#E0E0E0"), Color.WHITE}; break;
        }
        Shader shader = new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR);
        pCore.setShader(shader);
        pGlowMid.setShader(shader);
        pGlowOuter.setShader(shader);
    }

    public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
        float drawW = getWidth(); float drawH = getHeight();
        if (drawW <= 0 || drawH <= 0) return;
        float off = pGlowOuter.getStrokeWidth() / 2;
        float left = off; float top = off;
        float right = drawW - off; float bottom = drawH - off;

        if (aStyle > 0) {
            float perim = 2 * (drawW + drawH);
            float currentPhase = -perim * phaseFraction;
            float[] intervals = aStyle == 1 ? new float[]{perim/4f, 3*perim/4f}
                : aStyle == 2 ? new float[]{perim/8f, 3*perim/8f}
                : new float[]{perim/12f, 3*perim/12f};
            DashPathEffect dash = new DashPathEffect(intervals, currentPhase);
            pCore.setPathEffect(dash); pGlowMid.setPathEffect(dash); pGlowOuter.setPathEffect(dash);
        } else {
            pCore.setPathEffect(null); pGlowMid.setPathEffect(null); pGlowOuter.setPathEffect(null);
        }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pGlowOuter);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pGlowMid);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, pCore);
    }
}
    // ===== GESTURE RIPPLE VIEW (icon + sóng theo điểm chạm) =====
    // ===== GESTURE RIPPLE VIEW (chấm sóng chạm + icon NHẢY LÊN xoay 1 vòng rồi RƠI XUỐNG) =====
    private class GestureRippleView extends View {
        private float touchX = -1, touchY = -1;
        private float rippleRadius = 0f, rippleAlpha = 0f;
        private Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator popAnim;

        private Bitmap jumpIconBmp = null;
        private float jumpX = -1, jumpY = -1;
        private float jumpOffsetX = 0f;
        private float jumpOffsetY = 0f;
        private float jumpRotation = 0f;
        private float jumpAlpha = 0f;
        private ValueAnimator jumpUpAnim, fallAnim;
        private final Handler jumpHandler = new Handler(android.os.Looper.getMainLooper());

        public GestureRippleView(Context c) { super(c); setLayerType(LAYER_TYPE_HARDWARE, null); }

        public void showAt(float x, float y) {
            touchX = x; touchY = y;
            rippleRadius = 10f; rippleAlpha = 1f;
            setVisibility(View.VISIBLE); invalidate();
        }
        public void moveTo(float x, float y) { touchX = x; touchY = y; invalidate(); }

        public void popRipple() {
            if (popAnim != null) popAnim.cancel();
            popAnim = ValueAnimator.ofFloat(1f, 0f);
            popAnim.setDuration(320);
            popAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                rippleRadius = 60f + (1f - v) * 70f;
                rippleAlpha = v;
                invalidate();
            });
            popAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (jumpIconBmp == null) setVisibility(View.GONE);
                }
            });
            popAnim.start();
        }

        /** Icon của đúng cử chỉ vừa thực hiện: NHẢY LÊN + xoay 1 vòng (450ms) -> giữ
         *  trên đỉnh (2000ms) -> RƠI XUỐNG mờ dần biến mất (550ms). Tổng ~3 giây.
         *  Zero-alloc lặp lại: chỉ 2 ValueAnimator ngắn + 1 Handler, không giữ
         *  Thread/Service nào -> tối ưu pin/RAM cho Pixel 2XL. */
        public void jumpIcon(float x, float y, String gestureKey, int color, float dxDir, float dyDir) {
            jumpHandler.removeCallbacksAndMessages(null);
            if (jumpUpAnim != null) jumpUpAnim.cancel();
            if (fallAnim != null) fallAnim.cancel();
            int jSize = prefs.getInt("homacc_jump_icon_size", 90);
jumpIconBmp = resolveGestureIconBitmap(gestureKey, jSize);
            if (jumpIconBmp == null) return;
            jumpX = x; jumpY = y;
            jumpOffsetX = 0f; jumpOffsetY = 0f; jumpRotation = 0f; jumpAlpha = 1f;
            setVisibility(View.VISIBLE);
            invalidate();
            int jumpTotalDur = Math.max(60, prefs.getInt("homacc_jump_anim_dur", 1000));
            jumpUpAnim = ValueAnimator.ofFloat(0f, 1f);
            jumpUpAnim.setDuration((long) (jumpTotalDur * 0.45f));
            jumpUpAnim.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
            jumpUpAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                int dist = prefs.getInt("homacc_jump_dist", 160); // [MỚI] khoảng cách nhảy toàn cục
                jumpOffsetY = dyDir * dist * v;
                jumpOffsetX = dxDir * dist * v;
                jumpRotation = 360f * v;
                invalidate();
            });
            jumpUpAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    jumpHandler.postDelayed(() -> startFall(), prefs.getInt("homacc_jump_hold_ms", 2000));
                }
            });
            jumpUpAnim.start();
        }
        private void startFall() {
            int fallDur = Math.max(60, prefs.getInt("homacc_jump_anim_dur", 1000));
            fallAnim = ValueAnimator.ofFloat(0f, 1f);
            fallAnim.setDuration((long) (fallDur * 0.55f));
            fallAnim.setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f));
            float startOffset = jumpOffsetY;
            float startOffsetX = jumpOffsetX;
            fallAnim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                jumpOffsetY = startOffset + (140f - startOffset) * v;
                jumpOffsetX = startOffsetX * (1f - v);
                jumpAlpha = 1f - v;
                invalidate();
            });
            fallAnim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    jumpIconBmp = null;
                    if (rippleAlpha <= 0f) setVisibility(View.GONE);
                    invalidate();
                }
            });
            fallAnim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (touchX >= 0 && rippleAlpha > 0f) {
                ripplePaint.setColor(Color.argb((int) (rippleAlpha * 160), 255, 255, 255));
                canvas.drawCircle(touchX, touchY, rippleRadius, ripplePaint);
            }
            if (jumpIconBmp != null) {
                canvas.save();
                float cx = jumpX + jumpOffsetX, cy = jumpY + jumpOffsetY;
                canvas.rotate(jumpRotation, cx, cy);
                int jAlpha = prefs.getInt("homacc_jump_icon_alpha", 255);
iconPaint.setAlpha((int) (jumpAlpha * jAlpha));
                canvas.drawBitmap(jumpIconBmp, cx - jumpIconBmp.getWidth()/2f, cy - jumpIconBmp.getHeight()/2f, iconPaint);
                canvas.restore();
            }
        }
    }
   private final java.util.Map<String, Bitmap> gestureIconCache = new java.util.HashMap<>();
    private Bitmap resolveGestureIconBitmap(String gestureKey, int size) {
        String ref = prefs.getString("homacc_gesture_icon_" + gestureKey, "");
        String cacheKey = gestureKey + "_" + ref + "_" + size; // [FIX] gồm cả size lẫn ref -> đổi slider/icon là cache tự invalidate
        if (gestureIconCache.containsKey(cacheKey)) return gestureIconCache.get(cacheKey);
        Drawable d = null;
        try {
            if (ref.startsWith("app:")) d = getPackageManager().getApplicationIcon(ref.substring(4));
            else if (ref.startsWith("poolc:")) { int[] p2 = PanelEngine.getCustomIconPool(this); int idx = Integer.parseInt(ref.substring(6)); if (idx>=0 && idx<p2.length) d = getDrawable(p2[idx]); }
            else if (ref.startsWith("pool:")) { int idx = Integer.parseInt(ref.substring(5)); if (idx>=0 && idx<PanelEngine.SYSTEM_ICON_POOL.length) d = getDrawable(PanelEngine.SYSTEM_ICON_POOL[idx]); }
        } catch (Exception ignored) {}
        if (d == null) { gestureIconCache.put(cacheKey, null); return null; }
    // [FIX CRASH] size lấy từ slider "Jump Icon Size" có thể bị kéo về 0/âm —
    // Bitmap.createBitmap(0,0,...) ném IllegalArgumentException không bắt được,
    // đây chính là nguyên nhân crash tap/dtap/long khi có gán icon (swipe không
    // crash vì thường chưa được gán icon nên hàm return null ở dòng trên, không
    // bao giờ chạy tới đây). Ép tối thiểu 8px + bọc try/catch để không bao giờ
    // crash dù prefs có giá trị bất thường.
    int safeSize = Math.max(8, size);
    try {
        Bitmap bmp = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d = d.mutate(); d.setTint(Color.WHITE); d.setBounds(0,0,safeSize,safeSize); d.draw(c);
        gestureIconCache.put(cacheKey, bmp);
        return bmp;
    } catch (Exception e) {
        gestureIconCache.put(cacheKey, null);
        return null;
    }
    }
 private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    private void initDeviceAdmin() {
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, HomebDeviceAdminReceiver.class);
    }

    /** Trả về true nếu đã có quyền Device Admin, false nếu chưa (và sẽ tự bật dialog xin quyền) */
    private boolean ensureDeviceAdmin() {
        if (dpm == null) initDeviceAdmin();
        if (dpm.isAdminActive(adminComponent)) return true;
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Cấp quyền này để Homeb tắt màn hình bằng nút System.");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return false;
    }

    /** Action SYSTEM: Screen Off — gọi hàm này khi người dùng bấm nút Screen Off trong Homeb */
    private void doScreenOff() {
        if (!ensureDeviceAdmin()) return; // đang chờ user cấp quyền lần đầu
        try {
            dpm.lockNow();
        } catch (SecurityException e) {
            android.widget.Toast.makeText(this, "Homeb chưa được cấp quyền Device Admin", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
 private MediaProjection mediaProjection;
private ImageReader imageReader;
private VirtualDisplay virtualDisplay;
private BroadcastReceiver screenshotReceiver;
private long captureStartMs;
private final java.util.concurrent.atomic.AtomicBoolean captureDone = new java.util.concurrent.atomic.AtomicBoolean(false);
private static final long CAPTURE_WARMUP_MS = 350; // chờ dialog hệ thống mờ hẳn rồi mới lấy khung hình
    private void registerScreenshotReceiver() {
        if (screenshotReceiver != null) return;
        screenshotReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");
                if (resultCode == Activity.RESULT_OK && data != null) {
                    captureScreen(resultCode, data);
                }
            }
        };
        IntentFilter f = new IntentFilter("com.manhmoc.edgebar.ACTION_SCREENSHOT_GRANTED");
        registerReceiver(screenshotReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    }
// [FIX BADTOKEN + TỐI ƯU PIXEL 2XL] Homeb chạy dưới Service thường (không phải
// AccessibilityService) — dùng TYPE_APPLICATION_OVERLAY (đã có quyền SYSTEM_ALERT_WINDOW
// sẵn trong Manifest) thay vì TYPE_ACCESSIBILITY_OVERLAY. Không set type -> .show() ném
// BadTokenException bị nuốt câm trong try-catch của exec(), khiến dialog "biến mất" vô hình.
    private void showScreenRecordOptionsThenCapture() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 10);

        CheckBox cbAudio = new CheckBox(this);
        cbAudio.setText("Ghi âm (Micro)");
        cbAudio.setChecked(prefs.getBoolean("screenrec_audio_en", false));
        box.addView(cbAudio);

        CheckBox cbTouches = new CheckBox(this);
        cbTouches.setText("Hiển thị vị trí thao tác chạm trên màn hình");
        cbTouches.setChecked(prefs.getBoolean("screenrec_showtouches_en", true));
        box.addView(cbTouches);

        android.app.AlertDialog dlgSR = new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Bắt đầu ghi?")
            .setView(box)
            .setPositiveButton("Bắt đầu", (d, w) -> {
                prefs.edit()
                    .putBoolean("screenrec_audio_en", cbAudio.isChecked())
                    .putBoolean("screenrec_showtouches_en", cbTouches.isChecked())
                    .apply();
                Intent permIntent = new Intent(this, ScreenRecordPermissionActivity.class);
                permIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(permIntent);
            })
            .setNegativeButton("Hủy", null)
            .create();
        if (dlgSR.getWindow() != null) {
            dlgSR.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dlgSR.show();
    }
    /** Action SYSTEM: Screenshot — gọi hàm này khi người dùng bấm nút Screenshot trong Homeb */
    private void doScreenshot() {
        registerScreenshotReceiver();
        Intent i = new Intent(this, ScreenshotPermissionActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(i);
    }
    private void captureScreen(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) return;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels, height = metrics.heightPixels, density = metrics.densityDpi;

        captureDone.set(false);
        captureStartMs = System.currentTimeMillis();

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "HomebScreenshot", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        imageReader.setOnImageAvailableListener(reader -> {
            android.media.Image image = reader.acquireLatestImage();
            if (image == null) return;

            // Bỏ qua khung hình xuất hiện lúc dialog hệ thống còn đang mờ dần -> hết mờ ảnh
            if (System.currentTimeMillis() - captureStartMs < CAPTURE_WARMUP_MS) {
                image.close();
                return;
            }
            // Chỉ xử lý ĐÚNG 1 lần -> chặn callback thứ 2 chụp trúng lúc Toast đang hiện
            if (!captureDone.compareAndSet(false, true)) {
                image.close();
                return;
            }

            Bitmap bmp = imageToBitmap(image, width, height);
            image.close();
            saveBitmapToGallery(bmp);
            releaseScreenCapture();
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
    }
    private Bitmap imageToBitmap(android.media.Image image, int width, int height) {
        android.media.Image.Plane plane = image.getPlanes()[0];
        java.nio.ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return Bitmap.createBitmap(bmp, 0, 0, width, height);
    }

    private void saveBitmapToGallery(Bitmap bmp) {
        String name = "Homeb_" + System.currentTimeMillis() + ".png";
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Homeb");
        android.net.Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) return;
        try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            android.widget.Toast.makeText(this, "Đã lưu ảnh chụp màn hình", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    private void releaseScreenCapture() {
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
    }
    private class BarView extends View {
    private int baseAlpha, hideDelay;
    private boolean isAutoHiding = false, isInv = false;
    private Handler autoHideHandler = new Handler();
    private GradientDrawable gd = new GradientDrawable();
    private java.util.List<android.graphics.Bitmap> icons = new java.util.ArrayList<>();
    private Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public BarView(Context c) { super(c); gd.setCornerRadius(24f); setBackground(gd); }
    private int userIconAlpha = 255;
    public void setIcons(java.util.List<android.graphics.Bitmap> newIcons, int alpha) {
        this.icons = newIcons != null ? newIcons : new java.util.ArrayList<>();
        userIconAlpha = alpha;
        invalidate();
    }
    // [MỚI] Tô lại icon (vốn là bitmap trắng đơn sắc) sang đen/trắng theo nền —
    // null = giữ nguyên màu gốc (trắng). Zero-alloc: chỉ đổi ColorFilter, không tạo Bitmap mới.
    private Integer iconTintColor = null;
    public void setIconTintColor(Integer color) {
        if (iconTintColor == null ? color == null : iconTintColor.equals(color)) return;
        iconTintColor = color;
        iconPaint.setColorFilter(color == null ? null :
            new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        invalidate();
    }
    private int iconAlphaFactor = 255; // 0 = ẩn hoàn toàn icon, 255 = hiện đầy đủ

    public void updateProps(int alpha, boolean autoHide, int delay, boolean inv, float radius) {
        this.baseAlpha = alpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv;
        autoHideHandler.removeCallbacksAndMessages(null);
        gd.setCornerRadius(radius); // [MỚI] Độ bo tròn tuỳ chỉnh từ slider "bar_radius"
        if (inv) { gd.setColor(Color.argb(0, 96, 125, 139)); iconAlphaFactor = 0; }
        else if (!autoHide) { gd.setColor(Color.argb(alpha, 96, 125, 139)); iconAlphaFactor = 255; }
        else { gd.setColor(Color.argb(0, 96, 125, 139)); iconAlphaFactor = 255; }
        invalidate();
    }
    public void triggerFlash() {
        if (!isAutoHiding || isInv) return;
        autoHideHandler.removeCallbacksAndMessages(null);
        gd.setColor(Color.argb(Math.min(255, baseAlpha + 50), 96, 125, 139));
        invalidate();
        autoHideHandler.postDelayed(() -> {
            ValueAnimator a = ValueAnimator.ofFloat(1f, 0f);
            a.setDuration(1500);
            a.addUpdateListener(anim -> {
                float val = (float) anim.getAnimatedValue();
                gd.setColor(Color.argb((int) (baseAlpha * val), 96, 125, 139));
                // icon KHÔNG mờ theo nền nữa — luôn giữ độ hiện rõ trong suốt lúc tàng hình
                invalidate();
            });
            a.start();
        }, hideDelay);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        iconPaint.setAlpha((int) (userIconAlpha * (iconAlphaFactor / 255f)));
        if (icons.isEmpty()) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        int n = icons.size();
        int gap = 8;
        boolean horizontal = w >= h;
        int mainDim = horizontal ? w : h;
        int crossDim = horizontal ? h : w;
        int userIconSize = icons.get(0).getWidth(); // kích thước user đã chọn ở slider

        // [MỚI] Tự động lấp đầy thanh: nếu tổng chiều dài các icon (theo size user
        // chọn) vượt quá chiều dài Bar, tự co đều lại để vừa khít — không tràn ra
        // ngoài. Nếu đủ chỗ thì giữ nguyên kích thước user đã chọn (không phóng to
        // thêm, tránh vỡ nét). Dùng drawBitmap(bitmap, null, destRect, paint) để co
        // giãn ngay trên GPU khi vẽ — KHÔNG tạo Bitmap mới, Zero cấp phát thêm,
        // tối ưu pin/RAM cho Pixel 2XL vì đây là hàm onDraw() gọi liên tục.
        int maxFit = Math.max(8, (mainDim - (n - 1) * gap) / n);
        int drawSize = Math.min(userIconSize, maxFit);
        drawSize = Math.min(drawSize, crossDim); // không vượt bề dày thanh

        int totalMain = n * drawSize + (n - 1) * gap;
        int startMain = (mainDim - totalMain) / 2; // luôn căn giữa khối icon trong thanh
        int crossOffset = (crossDim - drawSize) / 2;

        for (int i = 0; i < n; i++) {
            int pos = startMain + i * (drawSize + gap);
            android.graphics.Rect dst = horizontal
                ? new android.graphics.Rect(pos, crossOffset, pos + drawSize, crossOffset + drawSize)
                : new android.graphics.Rect(crossOffset, pos, crossOffset + drawSize, pos + drawSize);
            canvas.drawBitmap(icons.get(i), null, dst, iconPaint);
        }
    }
}
    private class CornerView extends View {
            private Paint pFill, pStroke; private int type; private String prefix;
            private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
            private boolean isInv = false;

            public CornerView(Context c, int type, String prefix) {  
                super(c); this.type = type; this.prefix = prefix; 
                setLayerType(LAYER_TYPE_HARDWARE, null);
                pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); 
                pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); 
            }

            public int getCornerType() { return type; }

            public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) {
                pStroke.setStrokeWidth(thick);
                this.baseMoonAlpha = moonAlpha;
                this.baseStrokeAlpha = strokeAlpha;
                this.isAutoHiding = autoHide;
                this.hideDelay = delay;
                this.isInv = inv;
                if (inv) {
                    pFill.setAlpha(0);
                    pStroke.setAlpha(0);
                } else if (!autoHide) {
                    pFill.setColor(Color.argb(moonAlpha, 96, 125, 139));
                    pStroke.setColor(Color.argb(strokeAlpha, 255, 255, 255));
                } else {
                    pFill.setColor(Color.argb(0, 96, 125, 139));
                    pStroke.setColor(Color.argb(0, 255, 255, 255));
                }
                invalidate();
            }

            public void triggerFlash() { 
                if(!isAutoHiding || isInv) return; 
                autoHideHandler.removeCallbacksAndMessages(null); 
                pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha + 50), 96, 125, 139)); 
                pStroke.setColor(Color.argb(Math.min(255, baseStrokeAlpha + 50), 255, 255, 255)); 
                invalidate(); 
                autoHideHandler.postDelayed(() -> { 
                    ValueAnimator a = ValueAnimator.ofFloat(1f, 0f); 
                    a.setDuration(1500); 
                    a.addUpdateListener(anim -> { 
                        float val = (float)anim.getAnimatedValue(); 
                        pFill.setColor(Color.argb((int)(baseMoonAlpha * val), 96, 125, 139)); 
                        pStroke.setColor(Color.argb((int)(baseStrokeAlpha * val), 255, 255, 255)); 
                        invalidate(); 
                    }); 
                    a.start(); 
                }, hideDelay); 
            }

            @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas);
                float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(); float pad = thick/2;
                String ck = prefix + "corner_" + CORNERS[type] + "_";
                int shapeMode = prefs.getInt(ck+"shape", 0);
                float sRad = prefs.getInt(ck+"rad", 80) / 1000f; float mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
                float sw = prefs.getInt(ck+"w", 100); float sh = prefs.getInt(ck+"h", 100);
                float mw = prefs.getInt(ck+"moon_w", 100); float mh = prefs.getInt(ck+"moon_h", 100);

                Path moonPath = new Path(); Path strokePath = new Path();
                float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
                float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;

                if(type==0) { // BR
                    sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if(type==1) { // BL
                    sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if(type==2) { // TR
                    sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                } else { // TL
                    sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=0; mTipX=mw; mTipY=mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                }

                if(shapeMode == 1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
                else if(shapeMode == 2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
                else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }

                if(type==0||type==1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
                else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
                moonPath.close();

                canvas.drawPath(strokePath, pStroke);
                float mx = prefs.getInt(ck+"moon_x", 1250) - 1250;
                float my = prefs.getInt(ck+"moon_y", 1250) - 1250;
                canvas.save(); canvas.translate(mx, my); canvas.drawPath(moonPath, pFill); canvas.restore();
            }
        }
    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
    removeYtdlOverlay(); 
    removeRippleViewIfIdle(); 
    
    // [YÊU CẦU MỚI] Tự động hồi sinh Trợ năng (cho màn Lock) khi tắt màn hình từ Homeb
    try {
        String mySvc = getPackageName() + "/" + EdgeBarService.class.getName();
        String cur = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null) cur = "";
        if (!cur.contains(mySvc)) {
            // 1. Tắt cờ Homeb
            prefs.edit().putBoolean("shortcut_home_on", false).apply();
            
            // 2. Bật Trợ năng bằng lệnh Secure
            String newVal = cur.isEmpty() ? mySvc : cur + ":" + mySvc;
            android.provider.Settings.Secure.putString(c.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newVal);
            android.provider.Settings.Secure.putString(c.getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            
            // 3. Show full overlay cho không gian Lock (Hủy mọi thao tác Ẩn thủ công trước đó)
            SharedPreferences.Editor ed = prefs.edit();
            for (String b : BARS) ed.putBoolean("lock_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("lock_corner_" + cn + "_manual_hide", false);
            ed.apply();
            
            // 4. Tự sát Homeb Service để nhường quyền điều khiển ngay lập tức
            stopSelf();
        }
    } catch (Exception ignored) {}
    
} else if (Intent.ACTION_USER_PRESENT.equals(action)) {
    // [THAY BẰNG CODE HỒI SINH HOMEB Ở ĐÂY]
    SharedPreferences.Editor ed = prefs.edit();
    for (String b : BARS) ed.putBoolean("home_" + b + "_manual_hide", false);
    for (String cn : CORNERS) ed.putBoolean("home_corner_" + cn + "_manual_hide", false);
    ed.apply();
    updateVisibility();
} else if (action.equals("com.manhmoc.edgebar.SYNC_STATE")) {
                // V19.12.3.6.6: Throttle — tối đa 1 lần xử lý SYNC_STATE mỗi 150ms
                long nowSync = System.currentTimeMillis();
                if (nowSync - lastSyncMs < SYNC_THROTTLE_MS) return;
                lastSyncMs = nowSync;

                isKbd = i.getBooleanExtra("isKbd", false);
                isBl = i.getBooleanExtra("isBl", false);
                lastKbdHeight = i.getIntExtra("kbd_height", 0);
                String incomingPkg = i.getStringExtra("foreground_pkg");
                if (incomingPkg != null && !incomingPkg.isEmpty()) {
                    boolean isRealApp = !incomingPkg.contains("systemui")
                        && !incomingPkg.contains("launcher")
                        && !incomingPkg.contains("nexuslauncher")
                        && !incomingPkg.contains("inputmethod")
                        && !incomingPkg.equals("android")
                        && !incomingPkg.equals("com.android.settings")
                        && !incomingPkg.contains("quickstep");
                    if (isRealApp) liveForegroundPkg = incomingPkg;
                }
                if (i.getBooleanExtra("acc_cache_reset", false)) {
                    accCheckTimestamp = 0;
                }
                updateVisibility();
            } else if (action.equals("com.manhmoc.edgebar.TEST_ANIM")) {
                playAnim();
            } else if ("com.manhmoc.edgebar.PAUSE_WM_OPS".equals(action)) {
                // Fix Bug 6: Ẩn tất cả bars — KHÔNG removeView, giữ token WM hợp lệ
                // Pixel 2XL opt: setVisibility GONE = zero GPU cost trên Adreno 540
                for (int j = 0; j < 12; j++) if (bars[j] != null) bars[j].setVisibility(View.GONE);
                for (int j = 0; j < 4; j++) if (corners[j] != null) corners[j].setVisibility(View.GONE);
            } else if ("com.manhmoc.edgebar.RESUME_WM_OPS".equals(action)) {
                if (i.getBooleanExtra("acc_cache_reset", false)) {
                    accCheckTimestamp = 0;
                }
                updateVisibility();
            } else if ("com.manhmoc.edgebar.OPEN_PANEL_REQUEST".equals(action)) {
                String panelId = i.getStringExtra("panel_id");
                if (panelEngine != null && panelId != null) panelEngine.togglePanel(panelId);
            } else if ("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED".equals(action)) {
                if (panelEngine != null) panelEngine.rebuildAll();
            } else if ("com.manhmoc.edgebar.PANEL_TEST_TOGGLE".equals(action)) {
                String panelId = i.getStringExtra("panel_id");
                if (panelEngine != null && panelId != null) panelEngine.setForceTest(panelId, i.getBooleanExtra("on", false));
            } else if (VoiceRecorderService.TICK_ACTION.equals(action)) {
                String state = i.getStringExtra("state");
                long sec = i.getLongExtra("elapsed_sec", 0);
                updateRecIndicator(state, sec);
            } else if ("com.manhmoc.edgebar.IPC_ACTION".equals(action)) {
                // [MỚI] VolKey (và mọi nguồn khác) bắn IPC_ACTION broadcast — trước đây
                // chỉ EdgeBarService nghe được, Homeb (Trợ năng tắt) bị bỏ sót hoàn toàn.
                String act = i.getStringExtra("act");
                if (act == null) return;
                if ("LAUNCH_APP".equals(act)) {
                    String pkg = i.getStringExtra("launch_pkg");
                    if (pkg != null && !pkg.isEmpty()) {
                        try {
                            Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                        } catch (Exception ignored) {}
                    }
                    return;
                }
                if ("RUN_SHORTCUT".equals(act)) {
                    String scId = i.getStringExtra("shortcut_id");
                    if (scId != null && !scId.isEmpty()) {
                        try {
                            String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                            if (!uri.isEmpty()) {
                                Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                                scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(scIntent);
                            }
                        } catch (Exception ignored) {}
                    }
                    return;
                }
                if (act.startsWith("RUN_SHORTCUT_")) {
                    String scId = act.substring("RUN_SHORTCUT_".length());
                    try {
                        String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                        if (!uri.isEmpty()) {
                            Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(scIntent);
                        }
                    } catch (Exception ignored) {}
                    return;
                }
                exec(act);
} else if ("com.manhmoc.edgebar.TEST_REC_INDICATOR".equals(action)) {
                boolean on = i.getBooleanExtra("on", false);
                if (!VoiceRecorderService.isRunning) {
                    recIndicatorTestMode = on;
                    recIndicatorTestPaused = false;
                    updateRecIndicator(on ? "RECORDING" : "STOPPED", 0);
                }
            }
        }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        sendSyncState();
        return START_STICKY;
    }

    /**
     * [MỤC 1/3/7] Kiểm tra điều kiện sống còn của service.
     * Service chỉ cần chạy khi: old Home overlay ON, HOẶC MorseLock ON.
     * Nếu cả hai đều OFF → tự dừng để giải phóng RAM (Pixel 2XL opt).
     */
    private boolean isAccEnabled() {
    String s = android.provider.Settings.Secure.getString(getContentResolver(),
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return s != null && s.contains(getPackageName() + "/" + EdgeBarService.class.getName());
}
    private void sendSyncState() { Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        // [FIX OVERLAY "MA"] Mỗi lần Service THẬT SỰ khởi động lại (không phải do lỗi
// UI runtime), coi như không có phiên xem trước hợp lệ nào — dọn sạch cờ preview_*
// phòng trường hợp process trước bị OOM-kill giữa lúc đang xem trước, để lại
// giá trị true vĩnh viễn gây overlay không tương tác được. Zero cost: 1 lần ghi.
prefs.edit()
    .putBoolean("preview_lock", false)
    .putBoolean("preview_homacc", false)
    .putBoolean("preview_home", false)
    .apply();
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

// [MỚI] Idempotent — gọi lại nhiều lần cũng chỉ ghi đè cùng 1 PendingIntent (FLAG_UPDATE_CURRENT)
HomebWatchdogReceiver.scheduleRepeating(this);

prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter();
filter.addAction(Intent.ACTION_SCREEN_OFF);
// ACTION_SCREEN_ON đã được xử lý qua ACTION_USER_PRESENT, bỏ ACTION_SCREEN_ON
filter.addAction(Intent.ACTION_USER_PRESENT);
filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS); // giữ để bắt homekey/recentapps
// [TÌM] đoạn setup IntentFilter trong onCreate():
filter.addAction("com.manhmoc.edgebar.SYNC_STATE");
filter.addAction("com.manhmoc.edgebar.TEST_ANIM");

// [THÊM] 2 dòng ngay sau:
filter.addAction("com.manhmoc.edgebar.PAUSE_WM_OPS");
filter.addAction("com.manhmoc.edgebar.RESUME_WM_OPS");
// CODE MỚI — thêm 2 dòng:
filter.addAction("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
filter.addAction("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED");
filter.addAction(VoiceRecorderService.TICK_ACTION);
filter.addAction("com.manhmoc.edgebar.IPC_ACTION");
filter.addAction("com.manhmoc.edgebar.TEST_REC_INDICATOR");
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(syncReceiver, filter);

        String cid = "eb_19_home";
        NotificationChannel c = new NotificationChannel(cid, "Homeb", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Homeb")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setOngoing(true).build();
        startForeground(2, n);

        fV = new FlashView(this);
        fV.setAlpha(0f);
        fV.setVisibility(View.GONE);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(fV, fp); } catch (Exception e) {}

        // [FIX CRASH] kbdSensorView trước đây CHƯA BAO GIỜ được khởi tạo (new View + addView)
        // ở file này — chỉ có khai báo field rồi gọi thẳng setOnApplyWindowInsetsListener()
        // trên biến null bên dưới, gây NullPointerException crash onCreate() ngay trên mọi
        // máy Android 11+ (API >= 30). Bổ sung đúng phần dựng cửa sổ, dùng TYPE_APPLICATION_OVERLAY
        // (không NO_LIMITS) để nhận đúng WindowInsets.Type.ime() — cùng cấu hình đã sửa bên
        // EdgeBarService. Vẫn chỉ 1 View trong suốt, không thêm chi phí pin/RAM nào.
        int barCount = Math.min(bars.length, Math.min(BARS.length, GRAV.length));
for (int i = 0; i < barCount; i++) {
    bars[i] = new BarView(this);
    WindowManager.LayoutParams p = new WindowManager.LayoutParams(
1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
    try { wm.addView(bars[i], p); } catch (Exception e) {}
    bars[i].setOnTouchListener(new SidebarTouchListener("home_" + BARS[i], bars[i]));
}
        int cornerCount = Math.min(corners.length, Math.min(CORNERS.length, C_GRAV.length));
for (int i = 0; i < cornerCount; i++) {
    corners[i] = new CornerView(this, i, "home_");
    WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
    try { wm.addView(corners[i], p); } catch (Exception e) {}
    corners[i].setOnTouchListener(new SidebarTouchListener("home_corner_" + CORNERS[i], corners[i]));
}

        // HYBRID HOME V2: Lắng nghe thay đổi Accessibility bằng ContentObserver
        // Tiết kiệm pin tối đa: KHÔNG polling, KHÔNG query mỗi frame
        // Chỉ cập nhật type overlay khi user thực sự bật/tắt Accessibility
        // CODE MỚI — thêm dòng này:
panelEngine = new PanelEngine(this, wm, prefs, /* isAnyMode = */ false); // HomescreenService = IAO
        panelEngine.rebuildAll();
        updateVisibility();
        sendSyncState();
        startAppLockPolling(); // [MỚI] Homeb không có Accessibility -> phải poll UsageStats
    }
    private final Handler debounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable debounceRunnable = null;
private final Handler panelDebounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable panelDebounceRunnable = null;
// [TỐI ƯU PIN/RAM] Throttle ghi prefs khi kéo Slider — leading-edge throttle +
// write bắt buộc lúc nhả tay. Giảm số lần apply() từ "mỗi pixel kéo" (40-100+ lần
// mỗi lần vuốt) xuống tối đa ~16 lần/giây, vẫn giữ cảm giác preview real-time.
private final Handler sliderPrefHandler = new Handler(android.os.Looper.getMainLooper());
private final java.util.Map<String, Long> sliderLastWriteMs = new java.util.HashMap<>();
private final java.util.Map<String, Runnable> sliderPendingRunnable = new java.util.HashMap<>();
private static final long SLIDER_WRITE_THROTTLE_MS = 60;
private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
    if (k == null) return;

    boolean isOurKey = false;
    String[] ourPrefixes =
    {"lock_","home_","homacc_","anim_","vib_","hold_",
     "pack_panel_","lenap_",
     "blacklist","locklist","avoid_kbd","shortcut_","preview_","lang_","ytdl_",
     "intent_","tile_","macro_","i1_","i2_","i3_","i4_","i5_","i6_","i7_",
     "i8_","i9_","i10_","i11_","i12_","i13_","i14_","i15_"};
    for (String prefix : ourPrefixes)
        if (k.startsWith(prefix) || k.equals(prefix)) { isOurKey = true; break; }
    if (!isOurKey) return;
    if (k.startsWith("anim_")) {
        if (fV != null) fV.updateStyle();
        if (k.startsWith("anim_rec_")) liveUpdateRecIndicatorPosition();
        return;
    }
    if (k.startsWith("pack_panel_")) {
        if (panelEngine == null) return;
        if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
        panelDebounceRunnable = () -> panelEngine.onPrefChanged(k);
        panelDebounceHandler.postDelayed(panelDebounceRunnable, 120);
        return;
    }
    if (k.startsWith("shortcut_") && k.endsWith("_icon_override")) {
        if (panelEngine == null) return;
        final String key = k;
        if (panelDebounceRunnable != null) panelDebounceHandler.removeCallbacks(panelDebounceRunnable);
        panelDebounceRunnable = () -> panelEngine.onPrefChanged(key);
        panelDebounceHandler.postDelayed(panelDebounceRunnable, 120);
        return;
    }

    // Xem giải thích tại EdgeBarService.java — cờ này đã được set trực tiếp lên
    // View rồi, khỏi rebuild toàn bộ overlay qua updateVisibility() nữa.
    if (k.endsWith("_manual_hide")) return;

    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    debounceRunnable = () -> {
        debounceRunnable = null;
        updateVisibility();
    };
    debounceHandler.postDelayed(debounceRunnable, 500);
};

    /** [FIX #2] Ẩn ĐỒNG THỜI nền đen + icon khóa + toàn bộ bar/corner Morse trong
     *  CÙNG một lệnh gọi — không phụ thuộc broadcast SYNC_STATE bất đồng bộ (đây
     *  chính là nguyên nhân nền đen biến mất trước, bar/corner biến mất trễ hoặc
     *  thấp thoáng hiện lại trên Home). Zero RAM thêm: chỉ lặp lại View đã có sẵn.
     */
    private void updateVisibility() {
        if (panelEngine != null) panelEngine.rebuildAll();

        boolean isUnlocked = !km.isKeyguardLocked();
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hideNormal = isBl;
        boolean pushForKbd = avoidKbd && lastKbdHeight > 0;

        // DUAL-SOUL: Chỉ 1 trong 2 động cơ được phép vẽ tại 1 thời điểm
        // → tiết kiệm tuyệt đối RAM/GPU Adreno 540 trên Pixel 2XL
        boolean accHomeRunning = AccessibleHomeService.isRunning && isAccEnabled();
        boolean oldHomeEnabled = HomescreenService.isRunning && prefs.getBoolean("shortcut_home_on", false);
        boolean previewHomeOn = prefs.getBoolean("preview_home", false);
        boolean shouldRenderOldHome = isUnlocked && !hideNormal && !accHomeRunning && (oldHomeEnabled || previewHomeOn);

        if (accHomeRunning) {
            for (int i = 0; i < 12; i++) if (bars[i] != null) bars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (corners[i] != null) corners[i].setVisibility(View.GONE);
        }

                boolean isPreviewLock = prefs.getBoolean("preview_lock", false);
        int barLoopCount = Math.min(bars.length, BARS.length);
        for (int i = 0; i < barLoopCount; i++) {
            if (bars[i] == null) continue;
            boolean en = prefs.getBoolean("home_" + BARS[i] + "_en", false);
            bars[i].setVisibility((en && shouldRenderOldHome && !prefs.getBoolean("home_"+BARS[i]+"_manual_hide", false)) ? View.VISIBLE : View.GONE);
            if (en && shouldRenderOldHome) {
                int alpha = (isPreviewLock && !previewHomeOn) ? 0 : prefs.getInt("home_" + BARS[i] + "_alpha", 50);
                int w = prefs.getInt("home_" + BARS[i] + "_w", 300);
                int h = prefs.getInt("home_" + BARS[i] + "_h", 60);
                int x = prefs.getInt("home_" + BARS[i] + "_x", 0);
                int y = prefs.getInt("home_" + BARS[i] + "_y", 0);
                int visMode = prefs.getInt("home_" + BARS[i] + "_vis_mode", 0);
                int barHideDur = prefs.getInt("home_bar_hide_dur", 2500);
                ((BarView)bars[i]).updateProps(alpha, visMode==1, barHideDur, visMode==2, prefs.getInt("home_bar_radius", 24));
               int iconSize = prefs.getInt("home_" + BARS[i] + "_icon_size", prefs.getInt("home_bar_icon_size", 40)); 
               int iconAlpha = prefs.getInt("home_" + BARS[i] + "_icon_alpha", prefs.getInt("home_bar_icon_alpha", 255)); 
                ((BarView)bars[i]).setIcons(resolveBarIcons(prefs.getString("home_" + BARS[i] + "_icons",""), iconSize), iconAlpha);
                int priMode = prefs.getInt("home_" + BARS[i] + "_pri_mode", 0);
                int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
                p.flags = baseFlags;
                p.width = w;
                p.height = h;
                p.x = x;
                int pushY = (pushForKbd && (i==0 || i==1)) ? lastKbdHeight : 0;
                p.y = y + pushY;
                p.gravity = GRAV[i];
                updateLayoutIfChanged(bars[i], p);
                if (priMode == 0) applyAntiTapjacking(bars[i], w, h);
            }
        }
                int cornerLoopCount = Math.min(corners.length, CORNERS.length);
        for (int i = 0; i < cornerLoopCount; i++) {
            if (corners[i] == null) continue;
            boolean cornEn = prefs.getBoolean("home_corner_" + CORNERS[i] + "_en", false);
            corners[i].setVisibility((cornEn && shouldRenderOldHome && !prefs.getBoolean("home_corner_"+CORNERS[i]+"_manual_hide", false)) ? View.VISIBLE : View.GONE);
            if (cornEn && shouldRenderOldHome) {
                String ck = "home_corner_" + CORNERS[i] + "_";
                int moonAlpha = isPreviewLock ? 0 : prefs.getInt("home_corner_moon_alpha", 100);
                int strokeAlpha = isPreviewLock ? 0 : prefs.getInt("home_corner_stroke_alpha", 200);
                int hideDelay = prefs.getInt("home_corner_hide_dur", 2500);
                int visMode = prefs.getInt(ck + "vis_mode", 0);
                boolean isAuto = (visMode == 1);
                boolean isInv = (visMode == 2);
                ((CornerView) corners[i]).updateProps(prefs.getInt("home_corner_thick", 8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                int priMode = prefs.getInt(ck + "pri_mode", 0);
                int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
                p.flags = baseFlags;
                p.gravity = C_GRAV[i];
                int wPref = prefs.getInt(ck + "w", 100);
                int hPref = prefs.getInt(ck + "h", 100);
                int mwPref = prefs.getInt(ck + "moon_w", 100);
                int mhPref = prefs.getInt(ck + "moon_h", 100);
                int mxOffset = Math.abs(prefs.getInt(ck + "moon_x", 1250) - 1250);
                int myOffset = Math.abs(prefs.getInt(ck + "moon_y", 1250) - 1250);
                p.width = Math.max(10, Math.max(wPref, mwPref) + mxOffset);
                p.height = Math.max(10, Math.max(hPref, mhPref) + myOffset);
                int pushYc = (pushForKbd && (i==0 || i==1)) ? lastKbdHeight : 0;
                p.x = prefs.getInt(ck + "x", 0);
                p.y = prefs.getInt(ck + "y", 0) + pushYc;
                updateLayoutIfChanged(corners[i], p);
                if (priMode == 0) applyAntiTapjacking(corners[i], p.width, p.height);
            }
        }
    }
private void setViewVisibilityAnimated(View v, boolean show) {
    if (v == null) return;
    v.animate().cancel();
    if (show) {
        if (v.getVisibility() != View.VISIBLE) { v.setAlpha(0f); v.setVisibility(View.VISIBLE); }
        v.animate().alpha(1f).setDuration(120).start();
    } else {
        v.animate().alpha(0f).setDuration(120)
            .withEndAction(() -> { if (v.getAlpha() == 0f) v.setVisibility(View.GONE); }).start();
    }
}
    private Animator activeAnimAnimator;
    private void playAnim() {
        if (fV == null) return;
        // [TỐI ƯU] Không còn wm.updateViewLayout() ở đây nữa (view đã MATCH_PARENT cố định) —
        // Rung/Trigger/Animation giờ bắn ra gần như đồng thời, không còn trễ 1 nhịp IPC.
        if (activeAnimAnimator != null) { activeAnimAnimator.cancel(); activeAnimAnimator = null; }

        int style = prefs.getInt("anim_style", 0);
        int dur = Math.max(60, prefs.getInt("anim_dur", 1500));
        int holdDur = Math.max(0, prefs.getInt("anim_hold_dur", 400)); // [MỚI] thời gian giữ đỉnh sáng

        fV.setVisibility(View.VISIBLE);
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        if (style == 0) {
            int halfDur = Math.max(1, dur / 2);
            ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);
            fadeIn.setDuration(halfDur);
            fadeIn.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            ValueAnimator fadeOut = ValueAnimator.ofFloat(1f, 0f);
            fadeOut.setDuration(halfDur);
            fadeOut.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            set.play(fadeOut).after(holdDur).after(fadeIn); // fadeIn -> giữ holdDur -> fadeOut
        } else {
            fV.setAlpha(1f);
            ValueAnimator dash = ValueAnimator.ofFloat(0f, 1f);
            dash.setDuration(dur);
            dash.addUpdateListener(a -> fV.setPhase((float) a.getAnimatedValue()));
            ValueAnimator fadeOut = ValueAnimator.ofFloat(1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            set.play(fadeOut).after(holdDur).after(dash);
        }
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                fV.setAlpha(0f); fV.setVisibility(View.GONE);
                if (activeAnimAnimator == set) activeAnimAnimator = null;
            }
            @Override public void onAnimationCancel(Animator a) {
                fV.setAlpha(0f); fV.setVisibility(View.GONE);
            }
        });
        activeAnimAnimator = set;
        set.start();
    }
        private static final int MAX_TRIGGER_DEPTH = 3;
    private static final String[] GESTURE_SUFFIXES = {
        "_up_hold","_down_hold","_left_hold","_right_hold","_diag_hold",
        "_dtap","_long","_diag","_up","_down","_left","_right","_tap"
    };
    private String stripGestureSuffix(String key) {
        for (String suf : GESTURE_SUFFIXES) if (key.endsWith(suf)) return key.substring(0, key.length() - suf.length());
        return key;
    }
    private void handleAction(String key) { 
        handleAction(key, 0, true); 
    }

    private void handleAction(String key, int depth, boolean applyVibAnim) {
        String action = prefs.getString(key, "NONE");
        boolean isOn = prefs.getBoolean(key + "_on", true);
        if (action.equals("NONE") || !isOn) return;

        // [FIX KHỰNG HÌNH] Anima dùng LAYER_TYPE_SOFTWARE (bắt buộc cho BlurMaskFilter)
        // -> vẽ CPU toàn màn hình, có thể mất vài chục ms. Nếu gọi playAnim() TRƯỚC
        // khi xử lý TRIGGER_*, lượt vẽ software này chiếm main thread đúng lúc
        // Runnable bắn touch giả lập (đã lên lịch qua postDelayed trong
        // dispatchRealScreenGesture) tới giờ chạy -> touch bị delay theo, gây khựng
        // hẳn 1-2 khung hình. Giải pháp: nếu action có TRIGGER_*, hoãn playAnim()
        // ra SAU khi việc bắn touch đã chắc chắn được lên lịch xong (không phải chạy
        // xong, chỉ cần lên lịch xong là đủ tách rời 2 luồng công việc), để traversal
        // vẽ nặng của Anima không còn chen ngang & chặn Runnable touch nữa.
        boolean hasTriggerAction = action.contains("TRIGGER_");
        if (applyVibAnim) {
            if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
            if (prefs.getBoolean(key + "_anim", true)) {
                if (hasTriggerAction) {
                    int animDelay = prefs.getInt("sim_gesture_delay", 10) + 25;
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(this::playAnim, animDelay);
                } else {
                    playAnim();
                }
            }
        }

        String[] acts = action.split(",");
        for (String a : acts) {
            String at = a.trim();
            if (at.startsWith("TRIGGER_")) {
                if (depth >= MAX_TRIGGER_DEPTH) continue;
                String targetGesture = at.substring(8).toLowerCase(java.util.Locale.ROOT);
                String base = stripGestureSuffix(key);
                handleAction(base + "_" + targetGesture, depth + 1, false);
                        } else if (at.equals("HIDE_SOME_OVERLAY")) {
                hideSomeOverlay(key);
            } else if (at.equals("SHOW_ALL_OVERLAY")) {
                showAllOverlay();
            } else if (at.equals("RUN_SHORTCUT")) {
                String scId = prefs.getString(key + "_shortcut_id", "");
                if (!scId.isEmpty()) {
                    try {
                        String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                        if (!uri.isEmpty()) {
                            Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(scIntent);
                        }
                    } catch (Exception ignored) {}
                }
            } else if (at.equals("LAUNCH_APP")) {
                String pkg = prefs.getString(key + "_launch_pkg", "");
                if (!pkg.isEmpty()) {
                    try {
                        Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
                    } catch (Exception ignored) {}
                }
            } else exec(at);
        }
    }
    // [MỚI] Homeb chỉ có đúng 1 không gian "home_" nên không cần suy luận prefix.
            private void hideSomeOverlay(String key) {
        String targetsBar = prefs.getString("home_bar_hide_targets", "");
        String targetsCorner = prefs.getString("home_corner_hide_targets", "");
        String targets = targetsBar + (targetsBar.isEmpty() || targetsCorner.isEmpty() ? "" : ",") + targetsCorner;
        
        if (targets.isEmpty()) return;
        // [TỐI ƯU] Ẩn TRỰC TIẾP đúng view, không gọi lại updateVisibility() toàn bộ.
        boolean changed = false;
        SharedPreferences.Editor ed = prefs.edit();
        for (String t : targets.split(",")) {
            String tt = t.trim();
            if (tt.isEmpty()) continue;
            boolean isCorner = tt.equals("br") || tt.equals("bl") || tt.equals("tr") || tt.equals("tl");
            String mid = isCorner ? "corner_" + tt : tt;
            String k = "home_" + mid + "_manual_hide";
            if (!prefs.getBoolean(k, false)) { ed.putBoolean(k, true); changed = true; }
            if (isCorner) {
                int idx = java.util.Arrays.asList(CORNERS).indexOf(tt);
                if (idx >= 0 && corners[idx] != null) corners[idx].setVisibility(View.GONE);
            } else {
                int idx = java.util.Arrays.asList(BARS).indexOf(tt);
                if (idx >= 0 && bars[idx] != null) bars[idx].setVisibility(View.GONE);
            }
        }
        if (changed) ed.apply();
    }
    // [MỚI] Homeb chỉ có đúng 1 không gian ("home_") nên không cần suy luận prefix.
    private void showAllOverlay() {
        boolean changed = false;
        SharedPreferences.Editor ed = prefs.edit();
        for (String barKey : BARS) {
            String k = "home_" + barKey + "_manual_hide";
            if (prefs.getBoolean(k, false)) { ed.putBoolean(k, false); changed = true; }
        }
        for (String cornerKey : CORNERS) {
            String k = "home_corner_" + cornerKey + "_manual_hide";
            if (prefs.getBoolean(k, false)) { ed.putBoolean(k, false); changed = true; }
        }
        if (changed) { ed.apply(); updateVisibility(); }
    }
    private void doVibrate(int dur) {
        if (dur <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(dur);
        } catch (Exception e) {}
    }

    // [MỚI] Phát nhạc từ Download/My Playlist — xem MyPlaylistService.java
    private void startMyPlaylist() {
        Intent i = new Intent(this, MyPlaylistService.class);
        i.setAction(MyPlaylistService.ACTION_TOGGLE);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }
 // ===== YTDL QUICK INPUT OVERLAY — chỉ tồn tại đúng lúc dùng, Zero-RAM lúc đóng =====
    private View ytdlOverlay;

    private void showYtdlQuickInput() {
        if (ytdlOverlay != null) return; // đang mở sẵn -> không chồng lần 2
        if (!Settings.canDrawOverlays(this)) return;

        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setPadding(40, 40, 40, 30);
        card.setOnClickListener(v -> {}); // chặn chạm xuyên qua card làm đóng nhầm
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F2121212"));
        bg.setCornerRadius(28f);
        card.setBackground(bg);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("🎵 YTDLnis — Tải nhạc/video");
        title.setTextColor(Color.parseColor("#FFD700"));
        title.setTextSize(15f);
        title.setPadding(0, 0, 0, 20);
        card.addView(title);

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Dán link hoặc nhập tên bài hát");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#2C2C2C"));
        inputBg.setCornerRadius(20f);
        input.setBackground(inputBg);
        input.setPadding(24, 20, 24, 20);
        input.setText(prefs.getString("ytdl_last_link", ""));
        card.addView(input);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = 24;
        btnRow.setLayoutParams(rowLp);

        android.widget.Button bCancel = ytdlBtn("HỦY", "#333333", Color.WHITE);
        android.widget.Button bSave = ytdlBtn("LƯU LINK", "#4CAF50", Color.WHITE);
        android.widget.Button bDownload = ytdlBtn("TẢI", "#00E5FF", Color.BLACK);
        btnRow.addView(bCancel); btnRow.addView(bSave); btnRow.addView(bDownload);
        card.addView(btnRow);

        bCancel.setOnClickListener(v -> removeYtdlOverlay());
        bSave.setOnClickListener(v -> {
            prefs.edit().putString("ytdl_last_link", input.getText().toString().trim()).apply();
            removeYtdlOverlay();
        });
        bDownload.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                prefs.edit().putString("ytdl_last_link", q).apply();
                try {
                    Intent y = new Intent(Intent.ACTION_SEND);
                    y.setType("text/plain");
                    y.putExtra(Intent.EXTRA_TEXT, q);
                    y.setPackage("com.deniscerri.ytdl");
                    y.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(y);
                } catch (Exception ignored) {}
            }
            removeYtdlOverlay();
        });

        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams cardLp = new android.widget.FrameLayout.LayoutParams(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        cardLp.gravity = Gravity.CENTER;
        wrap.addView(card, cardLp);
        wrap.setOnClickListener(v -> removeYtdlOverlay()); // chạm ra ngoài thẻ -> hủy

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(wrap, lp);
            ytdlOverlay = wrap;
            input.requestFocus();
        } catch (Exception ignored) {}
    }

    private android.widget.Button ytdlBtn(String text, String bg, int textColor) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(text); b.setTextColor(textColor); b.setTextSize(12.5f);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bg)); g.setCornerRadius(16f);
        b.setBackground(g);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(6, 0, 6, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void removeYtdlOverlay() {
    if (ytdlOverlay == null) return;
    // [FIX GBOARD BỊ CHẶN] Bắt buộc giải phóng IME TRƯỚC khi gỡ cửa sổ, nếu không
    // InputMethodManagerService sẽ "kẹt" nghĩ cửa sổ này vẫn giữ focus bàn phím,
    // khiến Gboard không hiện được ở bất kỳ app nào khác trên toàn máy.
    try {
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(ytdlOverlay.getWindowToken(), 0);
        ytdlOverlay.clearFocus();
    } catch (Exception ignored) {}
    try {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) ytdlOverlay.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(ytdlOverlay, lp);
    } catch (Exception ignored) {}
    try { wm.removeView(ytdlOverlay); } catch (Exception ignored) {}
    ytdlOverlay = null;
}
    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch (a) {
                case "YTDL_DOWNLOAD": showYtdlQuickInput(); break;
                case "HOME":
    // [MỚI] Không cần Accessibility — vẫn chạy được sau khi Blacklist Auto-Homeb tắt Trợ năng
    try {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    } catch (Exception ignored) {}
    break;
                case "FLASH":
                    fOn = !fOn;
                    cm.setTorchMode(cId, fOn);
                    break;
                case "CAMERA":
                    Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                    c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(c);
                    break;
                case "VOLUME":
                    ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                    break;
                case "SCREEN_ON": {
                    android.os.PowerManager pmOn = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pmOn != null && !pmOn.isInteractive()) {
                        android.os.PowerManager.WakeLock wlOn = pmOn.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "EdgeBar:ScreenOnHomeb");
                        wlOn.acquire(2000);
                    }
                    break;
                }
               case "TOGGLE_RECORD": {
                    Intent recIntent = new Intent(this, VoiceRecorderService.class);
                    recIntent.setAction(VoiceRecorderService.ACTION_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(recIntent);
                    else startService(recIntent);
                    break;
                }
                case "PAUSE_RECORD": {
                    if (!VoiceRecorderService.isRunning) break;
                    Intent pauseIntent = new Intent(this, VoiceRecorderService.class);
                    pauseIntent.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(pauseIntent);
                    else startService(pauseIntent);
                    break;
                }
                case "OPEN_STORAGE_SCAN": {
                    // [FIX] Dùng cờ bền trong SharedPreferences thay vì Intent extra —
                    // onResume() của MainActivity LUÔN chạy khi Activity hiện ra, đảm bảo
                    // tuyệt đối nhảy đúng tab Storage. Zero RAM/pin thêm.
                    prefs.edit().putBoolean("pending_storage_scan", true).apply();
                    Intent openStorage = new Intent(this, MainActivity.class);
                    openStorage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(openStorage);
                    break;
                }
                case "SCAN_QR": {
                    Intent qr = new Intent(this, QrScanActivity.class);
                    qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    startActivity(qr);
                    break;
                }
                case "TOGGLE_OVERLAY": {
                    Intent toggleIntent = new Intent(this, ToggleReceiver.class);
                    toggleIntent.setAction("com.manhmoc.edgebar.TOGGLE_ACC");
                    sendBroadcast(toggleIntent);
                    break;
                }
                case "TOGGLE_WORK_PROFILE": {
                    try {
                        android.os.UserManager um =
                            (android.os.UserManager) getSystemService(Context.USER_SERVICE);
                        android.os.UserHandle work = null;
                        for (android.os.UserHandle uh : um.getUserProfiles()) {
                            if (!uh.equals(android.os.Process.myUserHandle())) { work = uh; break; }
                        }
                        if (work != null) {
                            boolean currentlyQuiet = um.isQuietModeEnabled(work);
                            um.requestQuietModeEnabled(!currentlyQuiet, work);
                        }
                    } catch (SecurityException ignored) {}
                    break;
                }
                case "SCREEN_OFF": doScreenOff(); break;
                case "SCREENSHOT": doScreenshot(); break;
                                case "PLAY_MY_PLAYLIST": startMyPlaylist(); break;
                case "HIDE_SOME_OVERLAY": hideSomeOverlay("home_"); break;
                case "SHOW_ALL_OVERLAY": showAllOverlay(); break;
                case "SCREEN_RECORD": {
                    if (ScreenRecorderService.isRunning) {
                        Intent stopIntent = new Intent(this, ScreenRecorderService.class);
                        stopIntent.setAction("STOP");
                        startService(stopIntent);
                    } else {
                        showScreenRecordOptionsThenCapture();
                    }
                    break;
                }
                case "AUTO_ROTATE_TOGGLE": {
                    try {
                        if (!android.provider.Settings.System.canWrite(this)) {
                            Toast.makeText(this, "Chưa có quyền 'Sửa đổi cài đặt hệ thống'!", Toast.LENGTH_LONG).show();
                            break;
                        }
                        int cur = android.provider.Settings.System.getInt(getContentResolver(),
                            android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
                        android.provider.Settings.System.putInt(getContentResolver(),
                            android.provider.Settings.System.ACCELEROMETER_ROTATION, cur == 1 ? 0 : 1);
                    } catch (Exception e) {}
                    break;
                }
                // THÊM case mới trong switch(a) của cả 2 file:
default:
                        if (a.startsWith("PANEL_")) {
                            // [THAY] Panel định danh bằng UUID Data Pack, không còn 1/2/3 cố định
                            Intent op = new Intent("com.manhmoc.edgebar.OPEN_PANEL_REQUEST");
                            op.putExtra("panel_id", a.substring(6));
                            sendBroadcast(op);
                        } else if (a.startsWith("INTENT_")) {
                            fireIntentById(a.substring(7)); // UUID thật, không phải số thứ tự
                        } else if (a.startsWith("MACRO_")) {
                            String macroId = a.substring(6);
                            Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                            iM.putExtra("services", prefs.getString("macro_" + macroId + "_svcs", ""));
                            sendBroadcast(iM);
                        } else if (a.startsWith("RUN_SHORTCUT_")) {
                            // [TỐI ƯU PIXEL 2XL] Xử lý trực tiếp Shortcut qua UUID tại chỗ, Zero-IPC overhead
                            // Giải quyết triệt để lỗi Lenap/Frontier/Ecosystem không gọi được shortcut
                            String scId = a.substring(13); // Độ dài của "RUN_SHORTCUT_"
                            try {
                                String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                                if (!uri.isEmpty()) {
                                    Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                                    scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(scIntent);
                                }
                            } catch (Exception ignored) {}
                        }
                        break;
            }
        } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try {
            String act = prefs.getString("i" + idx + "_act", "");
            String pkg = prefs.getString("i" + idx + "_pkg", "");
            Intent i;
            if (act.isEmpty() && !pkg.isEmpty()) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) return;
            } else {
                i = new Intent(act);
                if (!pkg.isEmpty()) i.setPackage(pkg);
                String cls = prefs.getString("i" + idx + "_cls", "");
                if (!pkg.isEmpty() && !cls.isEmpty())
                    i.setComponent(new android.content.ComponentName(pkg, cls));
                String data = prefs.getString("i" + idx + "_data", "");
                if (!data.isEmpty()) i.setData(android.net.Uri.parse(data));
                String cat = prefs.getString("i" + idx + "_cat", "");
                if (!cat.isEmpty()) i.addCategory(cat);
                String flg = prefs.getString("i" + idx + "_flags", "");
                if (!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
            }
            if (prefs.getBoolean("i" + idx + "_br", true) && !act.isEmpty()) {
                sendBroadcast(i);
            } else {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception e) {}
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        private String prefKeyBase;
        private View myView;
        private float sx, sy, lastX, lastY;
        private long st;
        private boolean longFired = false;
        private final Handler lpHandler = new Handler(android.os.Looper.getMainLooper());
private void checkAndYieldOS(String actionKey) {
            if (prefs.getBoolean(actionKey + "_os", false)) {
                try {
                    // [FIX] KHÔNG ẩn View đồng bộ ngay trong lúc đang xử lý ACTION_UP
                    // của CHÍNH View này — làm vậy buộc hệ thống đồng bộ lại
                    // WindowManager/InputDispatcher ngay giữa chừng (cùng gốc bug với
                    // updateViewLayout() vô điều kiện từng làm hỏng bộ đếm long-press,
                    // xem updateLayoutIfChanged()), gây khựng hẳn 1 nhịp TRƯỚC khi
                    // TRIGGER_* kịp bắn cử chỉ thật xuống OS. Dời việc ẩn sang đúng
                    // mốc VSYNC kế tiếp bằng postOnAnimation() thay vì Handler.post()
                    // thường (tránh xếp hàng chung Looper với holdCheckRunnable/anim
                    // callback — đây là lý do bản post() cũ từng bị giật) — vẫn xảy ra
                    // gần như tức thời (≤1 frame, ~16ms), không còn chặn dispatch chạm
                    // hiện tại nên không còn giật hình dù trùng lúc TRIGGER_* đang chạy.
                    String hideKey = prefKeyBase + "_manual_hide";
                    myView.postOnAnimation(() -> {
                        myView.setVisibility(View.GONE);
                        prefs.edit().putBoolean(hideKey, true).apply();
                    });

                    lpHandler.postDelayed(() -> {
                        myView.setVisibility(View.VISIBLE);
                        prefs.edit().putBoolean(hideKey, false).apply();
                    }, prefs.getInt("os_yield_dur", 3000));
                } catch (Exception ignored) {}
            }
        }
        private long lastTapUpTime = 0;
        private float lastTapUpX = -1f, lastTapUpY = -1f; // [MỚI] Tọa độ của cú chạm trước
        private static final long DTAP_WINDOW_MS = 280;
        private static final float DTAP_MAX_DIST_PX = 40f; // [SIẾT CHẶT] Giảm xuống 40px
        private static final float SWIPE_CANCEL_SLOP_PX = 60f;
        private static final float COMBO_THRESHOLD_PX = 130f;
        private static final float COMBO_RETURN_SLOP_PX = 35f; // [FIX] Ngưỡng "quay đầu" riêng cho combo, không dùng chung SWIPE_CANCEL_SLOP_PX
        private Runnable pendingTapRunnable = null;
        private boolean multiTouchCanceled = false; // [MỚI] Cờ kiểm soát cảm ứng đa điểm
        public SidebarTouchListener(String keyBase, View v) {
            this.prefKeyBase = keyBase;
            this.myView = v;
        }
        // [SỬA LỖI TOẠ ĐỘ] Lấy tọa độ tuyệt đối trực tiếp từ màn hình để đo khoảng cách chuẩn xác 100%
        private float getFixedX(MotionEvent e) { return e.getRawX(); }
        private float getFixedY(MotionEvent e) { return e.getRawY(); }

        private float[] computeJumpDirForTap() {
            float dxDir = 0f, dyDir = 0f;
            if (myView instanceof CornerView) {
                int idx = ((CornerView) myView).getCornerType();
                dxDir = (idx == 0 || idx == 2) ? -1f : 1f;
                dyDir = (idx == 0 || idx == 1) ? -1f : 1f;
            } else {
                if (prefKeyBase.contains("_r_") || prefKeyBase.endsWith("_r")) dxDir = -1f;
                else if (prefKeyBase.contains("_l_") || prefKeyBase.endsWith("_l")) dxDir = 1f;
            }
            int mode = prefs.getInt(prefKeyBase + "_jumpdir", 0);
            switch (mode) {
                case 1: return new float[]{dxDir, -1f}; case 2: return new float[]{dxDir, 1f};
                case 3: return new float[]{0f, -1f}; case 4: return new float[]{0f, 1f};
                case 5: return new float[]{-1f, 0f}; case 6: return new float[]{1f, 0f};
                default: return new float[]{dxDir, dyDir};
            }
        }

        private final Runnable holdCheckRunnable = () -> {
            longFired = true;
            float cdx = lastX - sx, cdy = lastY - sy;
            if (Math.abs(cdx) > SWIPE_CANCEL_SLOP_PX || Math.abs(cdy) > SWIPE_CANCEL_SLOP_PX) {
                String actionName;
                if (myView instanceof CornerView && Math.abs(cdx) > 40 && Math.abs(cdy) > 40) actionName = "diag_hold";
                else {
                    if (Math.abs(cdx) > Math.abs(cdy)) actionName = cdx > 0 ? "right_hold" : "left_hold";
                    else actionName = cdy > 0 ? "down_hold" : "up_hold";
                }
                handleAction(prefKeyBase + "_" + actionName);
                checkAndYieldOS(prefKeyBase + "_" + actionName);
                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_" + actionName + "_jump_on", true)) {
                    float swipeMag = (float) Math.sqrt(cdx * cdx + cdy * cdy);
                    float dirX = swipeMag > 0.001f ? cdx / swipeMag : 0f;
                    float dirY = swipeMag > 0.001f ? cdy / swipeMag : 0f;
                    rippleView.jumpIcon(sx, sy, actionName, Color.argb(180, 96, 125, 139), dirX, dirY);
                }
            } else {
                handleAction(prefKeyBase + "_long");
                checkAndYieldOS(prefKeyBase + "_long"); // THÊM DÒNG NÀY
                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_long" + "_jump_on", true)) {
                    float[] dir = computeJumpDirForTap();
                    rippleView.jumpIcon(sx, sy, "long", Color.argb(180, 96, 125, 139), dir[0], dir[1]);
                }
            }
        };

private boolean isHolding = false;
private float holdAnchorX = 0f, holdAnchorY = 0f; // [FIX] mốc đo swipe SAU khi giữ
private float minDx = 0f, maxDx = 0f, minDy = 0f, maxDy = 0f;
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (isDispatchingSyntheticGesture) return false;
            if (myView instanceof CornerView) ((CornerView)myView).triggerFlash();
            else if (myView instanceof BarView) ((BarView)myView).triggerFlash();
            
            // [FIX LỖI GÕ PHÍM 5-7] Sử dụng getActionMasked() để bắt chính xác đa điểm
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    multiTouchCanceled = false;
                    sx = getFixedX(e); sy = getFixedY(e);
                    lastX = sx; lastY = sy;
                    globalTouchStartX = sx; globalTouchStartY = sy; globalTouchEndX = sx; globalTouchEndY = sy;
                    maxDx = 0; minDx = 0; maxDy = 0; minDy = 0;
                    st = System.currentTimeMillis();
                    longFired = false;
                    isHolding = false;
                    
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (pendingTapRunnable != null) {
                        lpHandler.removeCallbacks(pendingTapRunnable);
                        pendingTapRunnable = null;
                    }
                                        ensureRippleView();
                    if (rippleView != null) rippleView.showAt(sx, sy);
                    lpHandler.postDelayed(holdCheckRunnable, prefs.getInt("hold_dur", 600));
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // [BẢO VỆ CHỐNG NHẦM LẪN] Ngón tay thứ 2 chạm vào (vd: gõ phím nhanh) -> hủy ngay mọi cử chỉ đang chờ
                    multiTouchCanceled = true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (rippleView != null) rippleView.popRipple();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (multiTouchCanceled) return true;
                    lastX = getFixedX(e); lastY = getFixedY(e);
                    globalTouchEndX = lastX; globalTouchEndY = lastY;
                    float cdx = lastX - sx; float cdy = lastY - sy;
                    if (cdx > maxDx) maxDx = cdx; if (cdx < minDx) minDx = cdx;
                    if (cdy > maxDy) maxDy = cdy; if (cdy < minDy) minDy = cdy;
                    if (rippleView != null) rippleView.moveTo(lastX, lastY);
                    return true;
                    
                case MotionEvent.ACTION_CANCEL:
                    if (multiTouchCanceled) return true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    isHolding = false;
                    if (rippleView != null) rippleView.popRipple();
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    if (multiTouchCanceled) return true;
                    lpHandler.removeCallbacks(holdCheckRunnable);
                    if (longFired) {
                        if (rippleView != null) rippleView.popRipple();
                        return true;
                    }
                    float finalDx = lastX - sx, finalDy = lastY - sy;
                    float absDx = Math.abs(finalDx), absDy = Math.abs(finalDy);
                    String actionName = "";
                    boolean isDiag = (myView instanceof CornerView && absDx > 40 && absDy > 40);

             if (isHolding) {
                 float holdDx = lastX - holdAnchorX, holdDy = lastY - holdAnchorY;
                 float holdAbsDx = Math.abs(holdDx), holdAbsDy = Math.abs(holdDy);
                 if (holdAbsDx < SWIPE_CANCEL_SLOP_PX && holdAbsDy < SWIPE_CANCEL_SLOP_PX) {
                     actionName = "long";
                 } else {
                     boolean isDiagHold = (myView instanceof CornerView && holdAbsDx > 40 && holdAbsDy > 40);
                     if (isDiagHold) actionName = "diag_hold";
                     else {
                         if (holdAbsDx > holdAbsDy) actionName = holdDx > 0 ? "hold_right" : "hold_left";
                         else actionName = holdDy > 0 ? "hold_down" : "hold_up";
                     }
                 }
             } else {

                        if (absDx < SWIPE_CANCEL_SLOP_PX && absDy < SWIPE_CANCEL_SLOP_PX) {
                            long now = System.currentTimeMillis();
                            boolean hasDtap = !prefs.getString(prefKeyBase + "_dtap", "NONE").equals("NONE");
                            float[] dirTap = computeJumpDirForTap();
                            
                            if (!hasDtap) {
                                lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                checkAndYieldOS(prefKeyBase + "_tap");
                                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                    rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                            } else {

                                long gap = now - lastTapUpTime;
                                float tapDist = 0f;
                                if (lastTapUpTime > 0) {
                                    float dX = sx - lastTapUpX;
                                    float dY = sy - lastTapUpY;
                                    tapDist = (float) Math.sqrt(dX * dX + dY * dY);
                                }

                                if (lastTapUpTime > 0 && gap <= DTAP_WINDOW_MS && tapDist <= DTAP_MAX_DIST_PX) {
                                    if (gap > 40) { 
                                        lastTapUpTime = 0; 
                                        handleAction(prefKeyBase + "_dtap");
                                        checkAndYieldOS(prefKeyBase + "_dtap");
                                        if (rippleView != null && prefs.getBoolean(prefKeyBase + "_dtap" + "_jump_on", true))
                                            rippleView.jumpIcon(lastX, lastY, "dtap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                    } else {

                                        lastTapUpTime = now; lastTapUpX = sx; lastTapUpY = sy;
                                        final long myUpTs = now;
                                        pendingTapRunnable = () -> {
                                            if (lastTapUpTime == myUpTs) {
                                                lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                                checkAndYieldOS(prefKeyBase + "_tap"); // THÊM DÒNG NÀY Ở VỊ TRÍ 2
                                                if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                                    rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                            }
                                        };

                                        lpHandler.postDelayed(pendingTapRunnable, DTAP_WINDOW_MS + 20);
                                    }
                                } else {
                                    if (pendingTapRunnable != null) {
                                        lpHandler.removeCallbacks(pendingTapRunnable);
                                        pendingTapRunnable.run(); 
                                        pendingTapRunnable = null;
                                    }
                                    
                                    lastTapUpTime = now; lastTapUpX = sx; lastTapUpY = sy;
                                    final long myUpTs = now;
                                    pendingTapRunnable = () -> {
                                        if (lastTapUpTime == myUpTs) {
                                            lastTapUpTime = 0; handleAction(prefKeyBase + "_tap");
                                            checkAndYieldOS(prefKeyBase + "_tap"); // THÊM DÒNG NÀY Ở VỊ TRÍ 3
                                            if (rippleView != null && prefs.getBoolean(prefKeyBase + "_tap" + "_jump_on", true))
                                                rippleView.jumpIcon(lastX, lastY, "tap", Color.argb(180, 96, 125, 139), dirTap[0], dirTap[1]);
                                        }
                                    };

                                    lpHandler.postDelayed(pendingTapRunnable, DTAP_WINDOW_MS + 20);
                                }
                            }
                            if (rippleView != null) rippleView.popRipple();
                            return true;
                        } else {
                            if (!isDiag) {
                                                                if (minDy < -COMBO_THRESHOLD_PX && finalDy > minDy + COMBO_RETURN_SLOP_PX) actionName = "up_down";
                                else if (maxDy > COMBO_THRESHOLD_PX && finalDy < maxDy - COMBO_RETURN_SLOP_PX) actionName = "down_up";
                                else if (minDx < -COMBO_THRESHOLD_PX && finalDx > minDx + COMBO_RETURN_SLOP_PX) actionName = "left_right";
                                else if (maxDx > COMBO_THRESHOLD_PX && finalDx < maxDx - COMBO_RETURN_SLOP_PX) actionName = "right_left";
                                else if (absDx > absDy) actionName = finalDx > 0 ? "right" : "left";
                                else actionName = finalDy > 0 ? "down" : "up";
                            } else {
                                actionName = "diag";
                            }
                        }
                    }

                    if (!actionName.isEmpty()) {
                        handleAction(prefKeyBase + "_" + actionName);
                        checkAndYieldOS(prefKeyBase + "_" + actionName);
                        if (rippleView != null) {
                            rippleView.popRipple();
                            if (prefs.getBoolean(prefKeyBase + "_" + actionName + "_jump_on", true)) {
                                float swipeMag = (float) Math.sqrt(finalDx * finalDx + finalDy * finalDy);
                                float dirX = swipeMag > 0.001f ? finalDx / swipeMag : 0f;
                                float dirY = swipeMag > 0.001f ? finalDy / swipeMag : 0f;
                                rippleView.jumpIcon(lastX, lastY, actionName, Color.argb(200, 255, 255, 255), dirX, dirY);
                            }
                        }
                    }
                    return true;
            }
            return true;
        }
    }
    /** Bản thay thế fireIntent() cho hệ Intent động (UUID) — đọc đúng field mà
 *  openIntentEditorV2() đã lưu ("intent_<uuid>_act/_pkg/_cls/_data/_cat/_flags/_br"). */
private void fireIntentById(String id) {
    try {
        String act = prefs.getString("intent_" + id + "_act", "");
        String pkg = prefs.getString("intent_" + id + "_pkg", "");
        Intent i;
        if (act.isEmpty() && !pkg.isEmpty()) {
            i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return;
        } else {
            i = new Intent(act);
            if (!pkg.isEmpty()) i.setPackage(pkg);
            String cls = prefs.getString("intent_" + id + "_cls", "");
            if (!pkg.isEmpty() && !cls.isEmpty())
                i.setComponent(new android.content.ComponentName(pkg, cls));
            String data = prefs.getString("intent_" + id + "_data", "");
            if (!data.isEmpty()) i.setData(android.net.Uri.parse(data));
            String cat = prefs.getString("intent_" + id + "_cat", "");
            if (!cat.isEmpty()) i.addCategory(cat);
            String flg = prefs.getString("intent_" + id + "_flags", "");
            if (!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
        }
        if (prefs.getBoolean("intent_" + id + "_br", false) && !act.isEmpty()) {
            sendBroadcast(i);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    } catch (Exception e) {}
}
private void ensureRecIndicator() {
        if (recIndicatorView != null) return;
        recIndicatorView = new LinearLayout(this);
        recIndicatorView.setOrientation(LinearLayout.HORIZONTAL);
        recIndicatorView.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(200, 20, 20, 20));
        bg.setCornerRadius(100f);
        recIndicatorView.setBackground(bg);
        int pad = 16;
        recIndicatorView.setPadding(pad*2, pad, pad*2, pad);
        recIndicatorView.setMinimumWidth(prefs.getInt("anim_rec_width", 260));
        recIndicatorView.setMinimumHeight(prefs.getInt("anim_rec_height", 90));

        recIndicatorDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Color.parseColor("#FF3B30"));
        recIndicatorDot.setBackground(dot);
        int dotSize = Math.round(24 * (prefs.getInt("anim_rec_size", 140) / 140f));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.setMargins(0, 0, 16, 0);
        recIndicatorDot.setLayoutParams(dotLp);

        recIndicatorText = new TextView(this);
        recIndicatorText.setTextColor(Color.WHITE);
        recIndicatorText.setTextSize(13 * (prefs.getInt("anim_rec_size", 140) / 140f));
        recIndicatorText.setText("00:00");

        recIndicatorView.addView(recIndicatorDot);
        recIndicatorView.addView(recIndicatorText);

        // Handler phân biệt chạm 1 lần (Pause/Play) và chạm 2 lần (Stop & Mở file)
    final android.os.Handler tapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable singleTapRunnable = () -> {
        // [1 CHẠM]: Tạm dừng ↔ Tiếp tục
        if (VoiceRecorderService.isRunning) {
            Intent p2 = new Intent(this, VoiceRecorderService.class);
            p2.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(p2); else startService(p2);
        } else if (ScreenRecorderService.isRunning) {
            Intent p3 = new Intent(this, ScreenRecorderService.class);
            p3.setAction(ScreenRecorderService.ACTION_PAUSE_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(p3); else startService(p3);
        } else if (recIndicatorTestMode) {
            recIndicatorTestPaused = !recIndicatorTestPaused;
            updateRecIndicator(recIndicatorTestPaused ? "PAUSED" : "RECORDING", 0);
        }
    };

    final long[] lastClickTime = {0};
    recIndicatorView.setOnClickListener(v -> {
        long now = System.currentTimeMillis();
        if (now - lastClickTime[0] < 300) {
            // [DOUBLE TAP]: Dừng hẳn + Rung + Xem/Phát File
            tapHandler.removeCallbacks(singleTapRunnable);
            lastClickTime[0] = 0;
            doVibrate(50); // Rung 50ms báo hiệu
            
            if (VoiceRecorderService.isRunning) {
                Intent s2 = new Intent(this, VoiceRecorderService.class);
                s2.setAction("com.manhmoc.edgebar.VOICEREC_STOP_PLAY");
                startService(s2);
            } else if (ScreenRecorderService.isRunning) {
                Intent s3 = new Intent(this, ScreenRecorderService.class);
                s3.setAction("com.manhmoc.edgebar.SCREENREC_STOP_PLAY");
                startService(s3);
            } else if (recIndicatorTestMode) {
                recIndicatorTestMode = false;
                recIndicatorTestPaused = false;
                updateRecIndicator("STOPPED", 0);
            }
        } else {
            // Đợi xem có cú chạm thứ 2 không, nếu sau 300ms không có thì gọi 1 Chạm
            lastClickTime[0] = now;
            tapHandler.postDelayed(singleTapRunnable, 300);
        }
    });

    // [NHẤN GIỮ]: Chỉ dừng hẳn (không phát file)
    recIndicatorView.setOnLongClickListener(v -> {
        tapHandler.removeCallbacks(singleTapRunnable); // Hủy nếu đang chờ 1 chạm
        lastClickTime[0] = 0;
        doVibrate(35);
        if (VoiceRecorderService.isRunning) {
            Intent s2 = new Intent(this, VoiceRecorderService.class);
            s2.setAction(VoiceRecorderService.ACTION_STOP);
            startService(s2);
        } else if (ScreenRecorderService.isRunning) {
            Intent s3 = new Intent(this, ScreenRecorderService.class);
            s3.setAction(ScreenRecorderService.ACTION_STOP);
            startService(s3);
        } else if (recIndicatorTestMode) {
            recIndicatorTestMode = false;
            recIndicatorTestPaused = false;
            updateRecIndicator("STOPPED", 0);
        }
        return true;
    });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.x = prefs.getInt("anim_rec_x", 1000) - 1000;
        // [FIX] Ép khoảng cách an toàn tối thiểu 100px với mép dưới màn hình —
        // tránh đè lên vùng cử chỉ vuốt-về (back gesture) của hệ thống.
        lp.y = Math.max(100, prefs.getInt("anim_rec_y", 1000) - 1000);
        try { wm.addView(recIndicatorView, lp); } catch (Exception ignored) {}
        recBlinkAnim = ValueAnimator.ofFloat(1f, 0.25f, 1f);
        recBlinkAnim.setDuration(1000);
        recBlinkAnim.setRepeatCount(ValueAnimator.INFINITE);
        recBlinkAnim.addUpdateListener(a -> { if (recIndicatorDot != null) recIndicatorDot.setAlpha((float) a.getAnimatedValue()); });
    }

    // [MỚI] Cập nhật vị trí/kích thước TẠI CHỖ khi kéo slider anim_rec_x/y/size —
    // chỉ updateViewLayout(), KHÔNG removeView/addView -> rẻ CPU/pin, mượt tức thì.
    private void liveUpdateRecIndicatorPosition() {
        if (recIndicatorView == null) return;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) recIndicatorView.getLayoutParams();
        lp.x = prefs.getInt("anim_rec_x", 1000) - 1000;
        lp.y = Math.max(100, prefs.getInt("anim_rec_y", 1000) - 1000); // [FIX] xem ensureRecIndicator()
        try { wm.updateViewLayout(recIndicatorView, lp); } catch (Exception ignored) {}
        recIndicatorView.setMinimumWidth(prefs.getInt("anim_rec_width", 260));
        recIndicatorView.setMinimumHeight(prefs.getInt("anim_rec_height", 90));
        float scale = prefs.getInt("anim_rec_size", 140) / 140f;
        int dotSize = Math.round(24 * scale);
        if (recIndicatorDot != null) {
            LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) recIndicatorDot.getLayoutParams();
            dlp.width = dotSize; dlp.height = dotSize;
            recIndicatorDot.setLayoutParams(dlp);
        }
        if (recIndicatorText != null) recIndicatorText.setTextSize(scale * 13);
    }
    private void updateRecIndicator(String state, long sec) {
        if (state == null || "STOPPED".equals(state)) {
            if (recBlinkAnim != null) recBlinkAnim.cancel();
            if (recIndicatorView != null) { try { wm.removeView(recIndicatorView); } catch (Exception ignored) {} recIndicatorView = null; }
            return;
        }
        ensureRecIndicator();
        recIndicatorText.setText(String.format("%02d:%02d", sec/60, sec%60));
        if ("PAUSED".equals(state)) {
            if (recBlinkAnim != null && recBlinkAnim.isRunning()) recBlinkAnim.cancel();
            if (recIndicatorDot != null) recIndicatorDot.setAlpha(1f);
        } else {
            if (recBlinkAnim != null && !recBlinkAnim.isRunning()) recBlinkAnim.start();
        }
    }
    @Override
   public void onDestroy() {
    super.onDestroy();
    isRunning = false;
    appLockPollHandler.removeCallbacksAndMessages(null); // [MỚI] dừng poll, tránh leak Handler
    try { unregisterReceiver(syncReceiver); } catch (Exception e) {}
    prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    removeYtdlOverlay(); // [FIX TAPJACKING] BẮT BUỘC gỡ trước tiên — đây là overlay
    // TYPE_APPLICATION_OVERLAY + FLAG_LAYOUT_NO_LIMITS phủ toàn màn hình. Trước đây bị
    // sót khỏi onDestroy(), nếu Service chết đúng lúc ô nhập YTDL đang mở, cửa sổ này
    // "mồ côi" vĩnh viễn trên WindowManager (không còn ai gọi removeView được nữa) ->
    // Android bật cơ chế lọc chạm chống tapjacking -> chặn thao tác toàn hệ thống.
    for (int i = 0; i < 12; i++) if (bars[i] != null) wm.removeView(bars[i]);
    for (int i = 0; i < 4; i++) if (corners[i] != null) wm.removeView(corners[i]);
    if (rippleView != null) wm.removeView(rippleView);
    if (recIndicatorView != null) { try { wm.removeView(recIndicatorView); } catch (Exception ignored) {} }
    if (recBlinkAnim != null) recBlinkAnim.cancel();
    if (fV != null) wm.removeView(fV);
    // [FIX TAPJACKING GỐC] kbdSensorView trước đây KHÔNG bao giờ được gỡ khi
    // Service bị stopService() — trở thành cửa sổ full-màn-hình mồ côi tồn tại
    // vĩnh viễn trên WindowManager dù Service đã chết, khiến Android/Play Protect
    // luôn coi mọi UI hệ thống (kể cả lúc Trợ năng đang chạy sạch) là đang bị
    // 1 app khác che phủ → báo "ứng dụng khác đang chặn màn hình" liên tục.
  }
}  // ← đây là dấu } cuối cùng đóng class HomescreenService, KHÔNG XÓA
