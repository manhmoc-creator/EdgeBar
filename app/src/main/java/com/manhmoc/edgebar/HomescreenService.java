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
    private View[] bars = new View[5];
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        String sig = w + "x" + h;
        if (sig.equals(lastGestureSig.get(v))) return;
        lastGestureSig.put(v, sig);
        try {
            v.setSystemGestureExclusionRects(
                java.util.Collections.singletonList(new Rect(0, 0, w, h)));
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
        rippleView = new GestureRippleView(this);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        try { wm.addView(rippleView, p); } catch (Exception ignored) {}
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
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};

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
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bmp);
    d = d.mutate();
    d.setTint(Color.WHITE);
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
    private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE";
    int aStyle = 0; private float phaseFraction = 0f;
    private int effW = 0, effH = 0; // [FIX] cache 2 slider anim_w/anim_h trước đây bị bỏ quên
    public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p);
        updateStyle(); }

    public void updateStyle() {
        p.setAlpha(prefs.getInt("anim_alpha", 255));
        p.setStrokeWidth(prefs.getInt("anim_thick", 12));
        radius = prefs.getInt("anim_rad", 40);
        cTheme = prefs.getString("anim_color", "WHITE");
        aStyle = prefs.getInt("anim_style", 0);
        // [FIX] Trước đây 2 dòng này không tồn tại -> slider "Chiều ngang/dọc
        // Hiệu ứng" ghi vào prefs nhưng chưa bao giờ được đọc lại ở đâu cả.
        effW = prefs.getInt("anim_w", 0);
        effH = prefs.getInt("anim_h", 0);
        if(getWidth() > 0) applyGradient(getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h);
    }
    
    private void applyGradient(int w, int h) {  /* như cũ */ 
            int[] cArr; switch(cTheme) {
                case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FF00FF")}; break;
                case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700"), Color.parseColor("#8A2BE2")}; break;
                case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00"), Color.parseColor("#FF4500")}; break;
                case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF"), Color.parseColor("#00BFFF")}; break;
                case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000"), Color.parseColor("#00FF00")}; break;
                case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00"), Color.parseColor("#FF1493")}; break;
                case "GOOGLE": cArr=new int[]{Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"), Color.parseColor("#4285F4"), Color.parseColor("#EA4335")}; break;
                case "AURORA": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"), Color.parseColor("#FF4081")}; break;
                case "ABYSS": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#1DE9B6"), Color.parseColor("#2979FF")}; break;
                case "COSMIC": cArr=new int[]{Color.parseColor("#4A148C"), Color.parseColor("#E91E63"), Color.parseColor("#FFD700")}; break;
                case "FOREST": cArr=new int[]{Color.parseColor("#1B5E20"), Color.parseColor("#4CAF50"), Color.parseColor("#FFEB3B")}; break;
                case "FLAME": cArr=new int[]{Color.parseColor("#B71C1C"), Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B")}; break;
                case "MIDNIGHT": cArr=new int[]{Color.parseColor("#1A237E"), Color.parseColor("#7B1FA2"), Color.parseColor("#03A9F4")}; break;
                case "TROPICAL": cArr=new int[]{Color.parseColor("#00695C"), Color.parseColor("#8BC34A"), Color.parseColor("#FF9800")}; break;
                case "CANDY": cArr=new int[]{Color.parseColor("#F06292"), Color.parseColor("#4DD0E1"), Color.parseColor("#FFF176")}; break;
                default: cArr=new int[]{Color.WHITE, Color.WHITE}; break;
            }
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR)); p.setShadowLayer(15f, 0, 0, cArr[0]);
        }
        public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        float drawW = getWidth(); float drawH = getHeight();
        if(drawW <= 0 || drawH <= 0) return;
        float off = p.getStrokeWidth()/2;
        float left = off; float top = off;
        float right = drawW - off; float bottom = drawH - off;
        p.setStrokeCap(Paint.Cap.ROUND);
        if(aStyle > 0) {
            float perim = 2 * (drawW + drawH);
            float currentPhase = -perim * phaseFraction;
            if (aStyle == 1) p.setPathEffect(new DashPathEffect(new float[]{perim/4f, 3*perim/4f}, currentPhase));
            else if (aStyle == 2) p.setPathEffect(new DashPathEffect(new float[]{perim/8f, 3*perim/8f}, currentPhase));
            else if (aStyle == 3) p.setPathEffect(new DashPathEffect(new float[]{perim/12f, 3*perim/12f}, currentPhase));
        } else { p.setPathEffect(null); }
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, p);
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
// [MỚI] Hộp thoại chọn Ghi âm + Hiện thao tác chạm trước khi xin quyền quay màn hình
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

        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            .show();
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
    removeYtdlOverlay(); // [FIX TAPJACKING] dọn overlay YTDL nếu đang mở dở khi tắt màn hình
    removeRippleViewIfIdle(); // [FIX TAPJACKING] dọn overlay ripple nếu không còn dùng
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
                for (int j = 0; j < 5; j++) if (bars[j] != null) bars[j].setVisibility(View.GONE);
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
                .setSmallIcon(android.R.drawable.ic_menu_crop)
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
        for (int i = 0; i < 5; i++) {
            bars[i] = new BarView(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
    1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch (Exception e) {}
            bars[i].setOnTouchListener(new SidebarTouchListener("home_" + BARS[i], bars[i]));
        }
        for (int i = 0; i < 4; i++) {
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
            for (int i = 0; i < 5; i++) if (bars[i] != null) bars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (corners[i] != null) corners[i].setVisibility(View.GONE);
        }

        boolean isPreviewLock = prefs.getBoolean("preview_lock", false);
        for (int i = 0; i < 5; i++) {
            if (bars[i] == null) continue;
            boolean en = prefs.getBoolean("home_" + BARS[i] + "_en", false);
            bars[i].setVisibility((en && shouldRenderOldHome) ? View.VISIBLE : View.GONE);
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
        for (int i = 0; i < 4; i++) {
            if (corners[i] == null) continue;
            boolean cornEn = prefs.getBoolean("home_corner_" + CORNERS[i] + "_en", false);
            corners[i].setVisibility((cornEn && shouldRenderOldHome) ? View.VISIBLE : View.GONE);
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
    private void playAnim() {
        WindowManager.LayoutParams fp = (WindowManager.LayoutParams) fV.getLayoutParams();
        fp.width = WindowManager.LayoutParams.MATCH_PARENT;
        fp.height = WindowManager.LayoutParams.MATCH_PARENT;
        wm.updateViewLayout(fV, fp);
        fV.setVisibility(View.VISIBLE);
        fV.post(() -> {
            int style = prefs.getInt("anim_style", 0);
            int dur = prefs.getInt("anim_dur", 1500);
            ValueAnimator anim;
            if (style == 0) {
                anim = ValueAnimator.ofFloat(0f, 1f, 0f);
                anim.addUpdateListener(a -> fV.setAlpha((float) a.getAnimatedValue()));
            } else {
                fV.setAlpha(1f);
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.addUpdateListener(a -> fV.setPhase((float) a.getAnimatedValue()));
            }
            anim.setDuration(dur);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator a) {
                    fV.setAlpha(0f);
                    fV.setVisibility(View.GONE);
                    fp.width = 0;
                    fp.height = 0;
                    wm.updateViewLayout(fV, fp);
                }
            });
            anim.start();
        });
    }

    private void handleAction(String key) {
        String action = prefs.getString(key, "NONE");
        boolean isOn = prefs.getBoolean(key + "_on", true);
        if (!action.equals("NONE") && isOn) {
            if (prefs.getBoolean(key + "_vib", true)) doVibrate(prefs.getInt("vib_dur", 30));
            if (prefs.getBoolean(key + "_anim", true)) playAnim();
String[] acts = action.split(",");
for (String a : acts) {
    if (a.trim().equals("RUN_SHORTCUT")) {
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
} else if (a.trim().equals("LAUNCH_APP")) {
        String pkg = prefs.getString(key + "_launch_pkg", "");
        if (!pkg.isEmpty()) {
            try {
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(li); }
            } catch (Exception ignored) {}
        }
    } else exec(a.trim());
}
        }
    }

    private void doVibrate(int dur) {
        if (dur <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(dur);
        } catch (Exception e) {}
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
                    sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC"));
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
private float sx, sy;
private long st;
private Handler longPressHandler = new Handler(); // dùng chung cho cả nhánh Morse lẫn nhánh thường
private boolean longPressTriggered = false;
private long lastTapUpTime = 0;
private static final long DTAP_WINDOW_MS = 300;
private static final float SWIPE_CANCEL_SLOP_PX = 60f;
public SidebarTouchListener(String keyBase, View v) {
    this.prefKeyBase = keyBase;
    this.myView = v;
}
 private float[] computeJumpDir() {
        float dxDir = 0f, dyDir = 0f;
        if (myView instanceof CornerView) {
            int idx = ((CornerView) myView).getCornerType(); // 0=BR,1=BL,2=TR,3=TL
            dxDir = (idx == 0 || idx == 2) ? -1f : 1f;
            dyDir = (idx == 0 || idx == 1) ? -1f : 1f;
        } else {
            if (prefKeyBase.contains("_r_") || prefKeyBase.endsWith("_r")) dxDir = -1f;
            else if (prefKeyBase.contains("_l_") || prefKeyBase.endsWith("_l")) dxDir = 1f;
        }
        return new float[]{dxDir, dyDir};
    }
    private float[] computeJumpDirForTap() {
        float[] auto = computeJumpDir();
        int mode = prefs.getInt(prefKeyBase + "_jumpdir", 0); // 0=Auto,1=Chéo lên,2=Chéo xuống,3=Thẳng lên,4=Thẳng xuống,5=Thẳng trái,6=Thẳng phải
        switch (mode) {
            case 1: return new float[]{auto[0], -1f};
            case 2: return new float[]{auto[0], 1f};
            case 3: return new float[]{0f, -1f};
            case 4: return new float[]{0f, 1f};
            case 5: return new float[]{-1f, 0f};
            case 6: return new float[]{1f, 0f};
            default: return auto;
        }
    }
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (myView instanceof CornerView) ((CornerView) myView).triggerFlash();
            else if (myView instanceof BarView) ((BarView) myView).triggerFlash();
switch (e.getAction()) {
    case MotionEvent.ACTION_MOVE: {
    if (rippleView != null) rippleView.moveTo(e.getRawX(), e.getRawY());
    float mdx = e.getRawX() - sx, mdy = e.getRawY() - sy;
    if (!longPressTriggered && (Math.abs(mdx) > SWIPE_CANCEL_SLOP_PX || Math.abs(mdy) > SWIPE_CANCEL_SLOP_PX)) {
        longPressHandler.removeCallbacksAndMessages(null);
    }
    return true;
}
    case MotionEvent.ACTION_DOWN:
        sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis();
        longPressTriggered = false;
        longPressHandler.removeCallbacksAndMessages(null);
        ensureRippleView();
        rippleView.showAt(sx, sy);
        longPressHandler.postDelayed(() -> {
            longPressTriggered = true;
            handleAction(prefKeyBase + "_long");
            if (rippleView != null) { float[] dirL = computeJumpDirForTap(); rippleView.jumpIcon(sx, sy, "long", Color.argb(180, 96, 125, 139), dirL[0], dirL[1]); }
        }, prefs.getInt("hold_dur", 600));
        return true;
    case MotionEvent.ACTION_UP: {
        longPressHandler.removeCallbacksAndMessages(null);
        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
        long duration = System.currentTimeMillis() - st;
        if (Math.abs(dx) > SWIPE_CANCEL_SLOP_PX || Math.abs(dy) > SWIPE_CANCEL_SLOP_PX) {
            if (longPressTriggered) return true;
            boolean isHold = duration > prefs.getInt("hold_dur", 600);
            String actionName;
            if (myView instanceof CornerView && Math.abs(dx) > 40 && Math.abs(dy) > 40) {
                actionName = "diag" + (isHold ? "_hold" : "");
            } else {
                if (Math.abs(dx) > Math.abs(dy)) actionName = dx > 0 ? "right" : "left";
                else actionName = dy > 0 ? "down" : "up";
                if (isHold) actionName += "_hold";
            }
            handleAction(prefKeyBase + "_" + actionName);
            if (rippleView != null) {
                rippleView.popRipple();
                float swipeMag = (float) Math.sqrt(dx * dx + dy * dy);
                float dirX = swipeMag > 0.001f ? dx / swipeMag : 0f;
                float dirY = swipeMag > 0.001f ? dy / swipeMag : 0f;
                rippleView.jumpIcon(e.getRawX(), e.getRawY(), actionName, Color.argb(200, 255, 255, 255), dirX, dirY);
            }
            return true;
        }
        if (longPressTriggered) return true;
        // [FIX LONG-PRESS] Dự phòng giống EdgeBarService — tránh phụ thuộc hoàn toàn vào
        // Handler.postDelayed() có kịp tự bắn trước ACTION_UP hay không.
        if (duration >= prefs.getInt("hold_dur", 600)) {
            longPressTriggered = true;
            handleAction(prefKeyBase + "_long");
            return true;
        }
        long now = System.currentTimeMillis();
        final float upX = e.getRawX(), upY = e.getRawY();
        boolean hasDtap = !prefs.getString(prefKeyBase + "_dtap", "NONE").equals("NONE");
        if (!hasDtap) {
            lastTapUpTime = 0;
            float[] dirT = computeJumpDirForTap();
            handleAction(prefKeyBase + "_tap");
            if (rippleView != null) rippleView.jumpIcon(upX, upY, "tap", Color.argb(180, 96, 125, 139), dirT[0], dirT[1]);
        } else if (now - lastTapUpTime <= DTAP_WINDOW_MS) {
            float[] dirDT = computeJumpDirForTap();
            lastTapUpTime = 0;
            handleAction(prefKeyBase + "_dtap");
            if (rippleView != null) rippleView.jumpIcon(upX, upY, "dtap", Color.argb(180, 96, 125, 139), dirDT[0], dirDT[1]);
        } else {
            lastTapUpTime = now;
            final long myUpTs = now;
            longPressHandler.postDelayed(() -> {
                if (lastTapUpTime == myUpTs) {
                    lastTapUpTime = 0;
                    handleAction(prefKeyBase + "_tap");
                    if (rippleView != null) { float[] dirDelay = computeJumpDirForTap(); rippleView.jumpIcon(upX, upY, "tap", Color.argb(180, 96, 125, 139), dirDelay[0], dirDelay[1]); }
                }
            }, DTAP_WINDOW_MS + 20);
        }
        if (rippleView != null) rippleView.popRipple();
        return true;
    }
    case MotionEvent.ACTION_CANCEL: {
        longPressHandler.removeCallbacksAndMessages(null);
        if (!longPressTriggered) {
            long duration = System.currentTimeMillis() - st;
            float cdx = e.getRawX() - sx, cdy = e.getRawY() - sy;
            if (duration >= prefs.getInt("hold_dur", 600)
                    && Math.abs(cdx) < SWIPE_CANCEL_SLOP_PX && Math.abs(cdy) < SWIPE_CANCEL_SLOP_PX) {
                longPressTriggered = true;
                handleAction(prefKeyBase + "_long");
                if (rippleView != null) { float[] dirC = computeJumpDirForTap(); rippleView.jumpIcon(sx, sy, "long", Color.argb(180, 96, 125, 139), dirC[0], dirC[1]); }
            }
        }
        return true;
    }
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

        // [MỚI] Chạm để Tạm dừng/Tiếp tục — ghi âm thật thì điều khiển service thật,
        // đang ở chế độ THỬ thì chỉ đổi trạng thái hiển thị, không đụng MediaRecorder.
        recIndicatorView.setOnClickListener(v -> {
            if (VoiceRecorderService.isRunning) {
                Intent p2 = new Intent(this, VoiceRecorderService.class);
                p2.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(p2); else startService(p2);
            } else if (recIndicatorTestMode) {
                recIndicatorTestPaused = !recIndicatorTestPaused;
                updateRecIndicator(recIndicatorTestPaused ? "PAUSED" : "RECORDING", 0);
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.x = prefs.getInt("anim_rec_x", 1000) - 1000;
        lp.y = prefs.getInt("anim_rec_y", 1000) - 1000;
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
        lp.y = prefs.getInt("anim_rec_y", 1000) - 1000;
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
    for (int i = 0; i < 5; i++) if (bars[i] != null) wm.removeView(bars[i]);
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
