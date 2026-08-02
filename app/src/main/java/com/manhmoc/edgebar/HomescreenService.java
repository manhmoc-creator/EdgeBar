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
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast; // ← THÊM DÒNG NÀY

import java.io.InputStream;
import java.util.Collections;

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
    private View kbdSensorView;
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
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private SharedPreferences prefs;
    private KeyguardManager km;
    private Vibrator vibrator;
    private PanelEngine panelEngine;
    private int lastKbdHeight = 0;
    private long lastSyncMs = 0;
    private static final long SYNC_THROTTLE_MS = 150;
    private long accCheckTimestamp = 0;
    private static final int KBD_HEIGHT_CHANGE_THRESHOLD = 20;
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};

    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};
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
   private class BarView extends View {
    private int baseAlpha, hideDelay;
    private boolean isAutoHiding = false, isInv = false;
    private Handler autoHideHandler = new Handler();
    private GradientDrawable gd = new GradientDrawable();
    public BarView(Context c) { super(c); gd.setCornerRadius(24f); setBackground(gd); }
    public void updateProps(int alpha, boolean autoHide, int delay, boolean inv) {
        this.baseAlpha = alpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv;
        autoHideHandler.removeCallbacksAndMessages(null);
        if (inv) gd.setColor(Color.argb(0, 96, 125, 139));
        else if (!autoHide) gd.setColor(Color.argb(alpha, 96, 125, 139));
        else gd.setColor(Color.argb(0, 96, 125, 139));
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
                invalidate();
            });
            a.start();
        }, hideDelay);
    }
}
    private class CornerView extends View {
        private Paint pFill, pStroke; private int type; private String prefix;
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
        private boolean isInv = false;
        public CornerView(Context c, int type, String prefix) {  super(c); this.type = type; this.prefix = prefix; pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); }

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
pStroke.setAlpha(strokeAlpha);
} else {
pFill.setColor(Color.argb(0, 96, 125, 139));
pStroke.setAlpha(0);
}
invalidate();
}

        public void triggerFlash() { if(!isAutoHiding || isInv) return; autoHideHandler.removeCallbacksAndMessages(null); pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha + 50), 96, 125, 139)); pStroke.setAlpha(Math.min(255, baseStrokeAlpha + 50)); invalidate(); autoHideHandler.postDelayed(() -> { ValueAnimator a = ValueAnimator.ofFloat(1f, 0f); a.setDuration(1500); a.addUpdateListener(anim -> { float val = (float)anim.getAnimatedValue(); pFill.setColor(Color.argb((int)(baseMoonAlpha * val), 96, 125, 139)); pStroke.setAlpha((int)(baseStrokeAlpha * val)); invalidate(); }); a.start(); }, hideDelay); }

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
            if (action.equals("com.manhmoc.edgebar.SYNC_STATE")) {
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
        kbdSensorView = new View(this);
        kbdSensorView.setAlpha(0f);
        // [GIẢM VÙNG PHỦ TAPJACKING] Chỉ neo ở nửa dưới màn hình (nơi IME thực sự
        // xuất hiện) thay vì MATCH_PARENT toàn màn — giảm diện tích "obscure" gây
        // cảnh báo tapjacking ở các app khác (Play Store, ngân hàng...) khi Homeb sống.
        int screenH = getResources().getDisplayMetrics().heightPixels;
        WindowManager.LayoutParams kp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (int)(screenH * 0.5f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        kp.gravity = Gravity.BOTTOM;
        try { wm.addView(kbdSensorView, kp); } catch (Exception e) {}
        // [FIX PUSH-AWAY BÀN PHÍM] Homeb chạy độc lập không cần Accessibility —
        // gắn thẳng WindowInsets.Type.ime() lên overlay của CHÍNH service này,
        // không còn phụ thuộc broadcast từ EdgeBarService (nguồn không tồn tại
        // khi Accessibility tắt). Chỉ update khi lệch ≥ ngưỡng, tránh gọi
        // updateVisibility() thừa mỗi khi Gboard đang animate.
        if (Build.VERSION.SDK_INT >= 30) {
            kbdSensorView.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeH = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
                if (Math.abs(imeH - lastKbdHeight) >= KBD_HEIGHT_CHANGE_THRESHOLD) {
                    lastKbdHeight = imeH;
                    isKbd = imeH > 0;
                    updateVisibility();
                }
                return insets;
            });
        }
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
     "pack_panel_",
     "blacklist","locklist","avoid_kbd","shortcut_","preview_","lang_","ytdl_",
     "intent_","tile_","macro_","i1_","i2_","i3_","i4_","i5_","i6_","i7_",
     "i8_","i9_","i10_","i11_","i12_","i13_","i14_","i15_"};
    for (String prefix : ourPrefixes)
        if (k.startsWith(prefix) || k.equals(prefix)) { isOurKey = true; break; }
    if (!isOurKey) return;

    if (k.startsWith("anim_") && fV != null) { fV.updateStyle(); return; }

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
                ((BarView)bars[i]).updateProps(alpha, visMode==1, 2500, visMode==2);
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

    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch (a) {
                case "YTDL_DOWNLOAD":
                    try {
                        android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
                            CharSequence txt = cb.getPrimaryClip().getItemAt(0).getText();
                            if (txt != null && txt.toString().startsWith("http")) {
                                Intent y = new Intent(Intent.ACTION_SEND);
                                y.setType("text/plain");
                                y.putExtra(Intent.EXTRA_TEXT, txt.toString());
                                y.setPackage("com.deniscerri.ytdl");
                                y.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(y);
                            }
                        }
                    } catch (Exception e) {}
                    break;
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
               case "VOICE_RECORD": {
                    Intent recIntent = new Intent(this, VoiceRecorderService.class);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(recIntent);
                    else startService(recIntent);
                    break;
                }
                case "TOGGLE_OVERLAY": {
                    sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC"));
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
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (myView != null && myView instanceof CornerView) ((CornerView) myView).triggerFlash();

switch (e.getAction()) {
    case MotionEvent.ACTION_MOVE: {
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
        longPressHandler.postDelayed(() -> {
            longPressTriggered = true;
            handleAction(prefKeyBase + "_long");
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
        if (now - lastTapUpTime <= DTAP_WINDOW_MS) {
            lastTapUpTime = 0;
            handleAction(prefKeyBase + "_dtap");
        } else {
            lastTapUpTime = now;
            final long myUpTs = now;
            longPressHandler.postDelayed(() -> {
                if (lastTapUpTime == myUpTs) {
                    lastTapUpTime = 0;
                    handleAction(prefKeyBase + "_tap");
                }
            }, DTAP_WINDOW_MS + 20);
        }
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
    @Override
   public void onDestroy() {
    super.onDestroy();
    isRunning = false;
    try { unregisterReceiver(syncReceiver); } catch (Exception e) {}
    prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    for (int i = 0; i < 5; i++) if (bars[i] != null) wm.removeView(bars[i]);
    for (int i = 0; i < 4; i++) if (corners[i] != null) wm.removeView(corners[i]);
    if (fV != null) wm.removeView(fV);
    // [FIX TAPJACKING GỐC] kbdSensorView trước đây KHÔNG bao giờ được gỡ khi
    // Service bị stopService() — trở thành cửa sổ full-màn-hình mồ côi tồn tại
    // vĩnh viễn trên WindowManager dù Service đã chết, khiến Android/Play Protect
    // luôn coi mọi UI hệ thống (kể cả lúc Trợ năng đang chạy sạch) là đang bị
    // 1 app khác che phủ → báo "ứng dụng khác đang chặn màn hình" liên tục.
    if (kbdSensorView != null) { try { wm.removeView(kbdSensorView); } catch (Exception ignored) {} kbdSensorView = null; }
  }
}  // ← đây là dấu } cuối cùng đóng class HomescreenService, KHÔNG XÓA
