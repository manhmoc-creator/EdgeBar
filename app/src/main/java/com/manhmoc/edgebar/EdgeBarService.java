package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService;
import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.DashPathEffect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
// ĐẰNG TRƯỚC (Có thể là các dòng import cuối cùng)
import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {

    // === CHÈN CODE BIẾN TOÀN CỤC CỦA BẠN VÀO ĐÂY ===
    // Động cơ Twin-Engine Trợ năng
private android.view.View[] accHomeBars = new android.view.View[5];
private android.view.View[] accHomeCorners = new android.view.View[4];
private android.content.BroadcastReceiver accHomeReceiver;
private boolean isHomaccDrawn = false; // Guard chặn vẽ lại khi đã có view

    // ĐẰNG SAU (Các biến cũ của EdgeBarService)
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private FlashView fV;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private boolean isInRecents = false;
    private String lastForegroundPkg = ""; 
    private KeyguardManager km;
    private SharedPreferences prefs;
    private Vibrator vibrator;

    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private final Handler debounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable debounceRunnable = null;

private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
    if (k == null) return;
    // Kéo slider Homacc -> update trực tiếp qua updateViewLayout, KHÔNG removeView/addView
    if (k.startsWith("homacc_") && AccessibleHomeService.isRunning) {
        updateHomaccLive();
        return;
    }
    if (fV != null) fV.updateStyle();
    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    debounceRunnable = () -> updateVisibility();
    debounceHandler.postDelayed(debounceRunnable, 300);
};

   private BroadcastReceiver stateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context c, Intent i) {
        if ("com.manhmoc.edgebar.TEST_ANIM".equals(i.getAction())) {
            playAnim();
        } else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
            // [YC1] Tắt màn → xóa unlock session, MorseLock sẽ hỏi lại khi bật màn
            // KHÔNG xóa unlockedPackage ở đây nữa — HomescreenService giữ state
        } else {
            updateVisibility();
        }
    }
};

    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if ("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) {
                exec(i.getStringExtra("act"));
            }
        }
    };

    private class FlashView extends View {
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phaseFraction = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); }
        public void updateStyle() { p.setAlpha(prefs.getInt("anim_alpha", 255)); p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { /* giống các bản trước */ 
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

    private class CornerView extends View {
        private Paint pFill, pStroke; private int type; private String prefix = "lock_";
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
        private boolean isInv = false;

        public CornerView(Context c, int type) { this(c, type, "lock_"); }
        public CornerView(Context c, int type, String prefix) { super(c); this.type = type; this.prefix = prefix; pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); }


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
        // auto-hide: bắt đầu ẩn hoàn toàn, KHÔNG triggerFlash() khi khởi tạo
        // chỉ flash khi user thực sự chạm (gọi từ SidebarTouchListener)
        pFill.setColor(Color.argb(0, 96, 125, 139));
        pStroke.setAlpha(0);
    }
    invalidate();
}
        public void triggerFlash() { if(!isAutoHiding || isInv) return; autoHideHandler.removeCallbacksAndMessages(null); pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha+50), 96,125,139)); pStroke.setAlpha(Math.min(255, baseStrokeAlpha+50)); invalidate(); autoHideHandler.postDelayed(() -> { ValueAnimator a = ValueAnimator.ofFloat(1f,0f); a.setDuration(1500); a.addUpdateListener(anim -> { float val = (float)anim.getAnimatedValue(); pFill.setColor(Color.argb((int)(baseMoonAlpha*val), 96,125,139)); pStroke.setAlpha((int)(baseStrokeAlpha*val)); invalidate(); }); a.start(); }, hideDelay); }

        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas);
            float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(), pad = thick/2;
            String ck = prefix + "corner_" + CORNERS[type] + "_";
            int shapeMode = prefs.getInt(ck+"shape", 0);
            float sRad = prefs.getInt(ck+"rad", 80) / 1000f; float mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
            float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
            float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);

            Path moonPath = new Path(), strokePath = new Path();
            float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
            float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;

            if (type == 0) { // BR
                sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad;
                sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh;
                mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 1) { // BL
                sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad;
                sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh;
                mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
            } else if (type == 2) { // TR
                sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad;
                sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh;
                mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
            } else { // TL
                sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad;
                sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=0; mTipX=mw; mTipY=mh;
                mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
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

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
        filter.addAction("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
        registerReceiver(stateReceiver, filter);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));

        String cid = "eb_19_acc";
        NotificationChannel c = new NotificationChannel(cid, "Edge Bar đang chạy nền", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar").setSmallIcon(android.R.drawable.ic_lock_idle_lock).setOngoing(true).build();
        startForeground(1, n);
        accHomeReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                String act = intent.getAction();
                if ("com.manhmoc.edgebar.ACC_HOME_DRAW".equals(act)) {
                    drawAccessibleHome();
                } else if ("com.manhmoc.edgebar.ACC_HOME_REMOVE".equals(act)) {
                    removeAccessibleHome();
                } else if ("com.manhmoc.edgebar.ACC_HOME_SLEEP".equals(act)) {
                    // [MỤC 5] Deep sleep: chỉ ẩn view, GIỮ service sống — đỡ tốn pin re-init
                    for (int i=0;i<5;i++) if (accHomeBars[i]!=null) accHomeBars[i].setVisibility(View.GONE);
                    for (int i=0;i<4;i++) if (accHomeCorners[i]!=null) accHomeCorners[i].setVisibility(View.GONE);
                } else if ("com.manhmoc.edgebar.ACC_HOME_WAKE".equals(act)) {
                    // [MỤC 5] Thức dậy: vẽ lại nếu view chưa tồn tại, hoặc hiện lại view cũ
                    if (accHomeBars[0] == null && accHomeCorners[0] == null) drawAccessibleHome();
                    else {
                        SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                        for (int i=0;i<5;i++) if (accHomeBars[i]!=null && p.getBoolean("homacc_"+BARS[i]+"_en", false)) accHomeBars[i].setVisibility(View.VISIBLE);
                        for (int i=0;i<4;i++) if (accHomeCorners[i]!=null && p.getBoolean("homacc_corner_"+CORNERS[i]+"_en", false)) accHomeCorners[i].setVisibility(View.VISIBLE);
                        updateHomaccLive();
                    }
                }
            }
        };
        android.content.IntentFilter accFilter = new android.content.IntentFilter();
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_DRAW");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_REMOVE");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_SLEEP");
        accFilter.addAction("com.manhmoc.edgebar.ACC_HOME_WAKE");
        registerReceiver(accHomeReceiver, accFilter);
        if (AccessibleHomeService.isRunning) drawAccessibleHome();

        createFloatingBars();
    } // <-- ĐÂY MỚI LÀ DẤU ĐÓNG ĐÚNG CỦA onServiceConnected()
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        // [THAY] bằng — chỉ vẽ khi state THỰC SỰ thay đổi:
// Pixel 2XL opt: dùng flag cache, tránh removeView/addView mỗi event
boolean accShouldRun = AccessibleHomeService.isRunning;
if (accShouldRun && accHomeBars[0] == null) {
    // AccHome đang chạy nhưng chưa có view → vẽ lần đầu
    drawAccessibleHome();
} else if (!accShouldRun && accHomeBars[0] != null) {
    // AccHome đã tắt nhưng view vẫn còn → gỡ
    removeAccessibleHome();
}
// Nếu state không đổi → KHÔNG làm gì cả (zero CPU)
        String pName = event.getPackageName() != null ? event.getPackageName().toString() : "";
        String cName = event.getClassName() != null ? event.getClassName().toString() : "";

        isKbd = pName.contains("inputmethod") || cName.contains("InputWindow")
                || cName.contains("keyboard") || cName.contains("Keyboard");
        String bl = prefs.getString("blacklist", "");
        isBl = !pName.isEmpty() && bl.contains(pName);

        boolean isSystemUI = pName.contains("systemui")
                || pName.contains("launcher")
                || pName.contains("nexuslauncher")
                || pName.equals("com.android.settings")
                || pName.isEmpty()
                || isKbd;

if (!isSystemUI) {
    String locklist = prefs.getString("locklist", "");
    boolean isAppLocked = false;
    if (!locklist.isEmpty()) {
        for (String pkg : locklist.split(",")) {
            if (pkg.trim().equals(pName)) { isAppLocked = true; break; }
        }
    }
           if (isAppLocked) {
                boolean isMainWindow = !cName.contains("Dialog")
                    && !cName.contains("Popup")
                    && !cName.contains("Toast")
                    && !cName.contains("Panel")
                    && !cName.contains("Permission");
                if (isMainWindow) {
                    // [FIX-BUG-0] Đón đầu luồng xử lý: Ép dịch vụ HomescreenService dựng màn chắn MorseOS ngay lập tức tại luồng chính để triệt tiêu thời gian trễ chớp màn hình
                    Intent i = new Intent("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
                    i.putExtra("pkg", pName);
                    sendBroadcast(i);
                    
                    // Đồng thời gửi một tín hiệu tối khẩn để HomescreenService hiện diện container không trễ
                    if (HomescreenService.isRunning) {
                        try {
                            // Đồng bộ trực tiếp biến cờ trạng thái nếu chạy chung tiến trình monolithic
                            Intent fastShow = new Intent("com.manhmoc.edgebar.SYNC_STATE");
                            fastShow.putExtra("foreground_pkg", pName);
                            sendBroadcast(fastShow);
                        } catch (Exception ignored) {}
                    }
                }
            }
}
        updateVisibility();
if (pName.contains("packageinstaller") || pName.contains("installer")
        || pName.contains("vending")) {
    if (cName.contains("Uninstall") || cName.contains("uninstall")
            || cName.contains("Delete") || cName.contains("UninstallActivity")
            || cName.contains("DeleteActivity")) {
        Intent uninstallGuard = new Intent("com.manhmoc.edgebar.UNINSTALL_DETECTED");
        sendBroadcast(uninstallGuard);
    }
}
boolean nowInRecents = pName.contains("launcher")
        || pName.contains("nexuslauncher")
        || pName.contains("quickstep")
        || pName.contains("systemui")
        || cName.contains("RecentsActivity")
        || cName.contains("RecentTasksActivity")
        || cName.contains("recents");
if (nowInRecents && !isInRecents) {
    isInRecents = true;
    Intent coverIntent = new Intent("com.manhmoc.edgebar.MORSE_OS_RECENTS_SHOW");
    coverIntent.putExtra("last_pkg", lastForegroundPkg);
    sendBroadcast(coverIntent);
} else if (!nowInRecents && isInRecents) {
    isInRecents = false;
    Intent hideIntent = new Intent("com.manhmoc.edgebar.MORSE_OS_RECENTS_HIDE");
    sendBroadcast(hideIntent);
}
if (!nowInRecents && !pName.isEmpty() && !isKbd) {
    lastForegroundPkg = pName;
}
Intent syncIntent = new Intent("com.manhmoc.edgebar.SYNC_STATE");
syncIntent.putExtra("isKbd", isKbd);
syncIntent.putExtra("isBl", isBl);
syncIntent.putExtra("foreground_pkg", pName);
sendBroadcast(syncIntent);
    }
}

    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch (a) {
                case "MACRO_1": case "MACRO_2": case "MACRO_3": case "MACRO_4": case "MACRO_5":
                    Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                    iM.putExtra("services", prefs.getString(a.toLowerCase()+"_svcs", ""));
                    sendBroadcast(iM); break;
                case "TOGGLE_MORSE":
                    Intent m = new Intent("com.manhmoc.edgebar.TOGGLE_MORSE");
                    sendBroadcast(m); break;
                case "YTDL_DOWNLOAD":
                    try{
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
                    } catch (Exception e) {} break;
                case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break;
                case "RECENTS": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case "SCREEN_ON":
                    // Thuật toán WakeLock giải phóng RAM nhanh
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (pm != null && !pm.isInteractive()) {
                        android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, 
                            "EdgeBar:ScreenOn");
                        wl.acquire(2000); // Chỉ giữ CPU trong 3 giây để bật màn, sau đó tự nhả RAM
                    }
                    break;
                case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break;
                case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
                case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break;
                case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break;
                case "VOLUME": ((AudioManager) getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break;
                default: if (a.startsWith("INTENT_")) fireIntent(a.split("_")[1]); break;
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

    private void playAnim() {
        WindowManager.LayoutParams fp = (WindowManager.LayoutParams) fV.getLayoutParams();
        fp.width = WindowManager.LayoutParams.MATCH_PARENT; fp.height = WindowManager.LayoutParams.MATCH_PARENT;
        wm.updateViewLayout(fV, fp);
        fV.setVisibility(View.VISIBLE);
        fV.post(() -> {
            int style = prefs.getInt("anim_style", 0);
            int dur = prefs.getInt("anim_dur", 1500);
            ValueAnimator anim;
            if (style == 0) {
                anim = ValueAnimator.ofFloat(0f,1f,0f);
                anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue()));
            } else {
                fV.setAlpha(1f);
                anim = ValueAnimator.ofFloat(0f,1f);
                anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue()));
            }
            anim.setDuration(dur);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    fV.setAlpha(0f);
                    fV.setVisibility(View.GONE);
                    fp.width = 0; fp.height = 0;
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
            if (prefs.getBoolean(key+"_vib", true)) doVibrate(prefs.getInt("vib_dur",30));
            if (prefs.getBoolean(key+"_anim", true)) playAnim();
            String[] acts = action.split(",");
            for (String a : acts) exec(a.trim());
        }
    }
    private void doVibrate(int dur) { if (dur<=0) return; try { if (Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    private void createFloatingBars() {
        fV = new FlashView(this);
        fV.setAlpha(0f); fV.setVisibility(View.GONE);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(0,0, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(fV, fp); } catch(Exception e){}
        for (int i=0;i<5;i++) {
            bars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch(Exception e){}
            bars[i].setOnTouchListener(new SidebarTouchListener("lock_"+BARS[i], null));
        }
        for (int i=0;i<4;i++) {
            corners[i] = new CornerView(this,i);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1,1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
            try { wm.addView(corners[i], p); } catch(Exception e){}
            corners[i].setOnTouchListener(new SidebarTouchListener("lock_corner_"+CORNERS[i], corners[i]));
        }
        updateVisibility();
    }

    private void updateVisibility() {
        boolean isPreview = prefs.getBoolean("preview_lock", false);
        boolean isLocked = km.isKeyguardLocked() || isPreview;
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hide = (avoidKbd && isKbd) || isBl;
        if (hide && fV != null) fV.setVisibility(View.GONE);
        for (int i=0;i<5;i++) {
            if (bars[i]==null) continue;
            boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", false);
            bars[i].setVisibility((en && isLocked && !hide) ? View.VISIBLE : View.GONE);
            if (en && isLocked) {
                int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha",50);
                int w = prefs.getInt("lock_"+BARS[i]+"_w",300);
                int h = prefs.getInt("lock_"+BARS[i]+"_h",60);
                int x = prefs.getInt("lock_"+BARS[i]+"_x",0);
                int y = prefs.getInt("lock_"+BARS[i]+"_y",0);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color.argb(alpha,96,125,139));
                gd.setCornerRadius(24f);
                bars[i].setBackground(gd);
                int priMode = prefs.getInt("lock_"+BARS[i]+"_pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
                p.flags = baseFlags; p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i];
                wm.updateViewLayout(bars[i], p);
            }
        }
        for (int i=0;i<4;i++) {
            if (corners[i]==null) continue;
            boolean cornEn = prefs.getBoolean("lock_corner_"+CORNERS[i]+"_en", false);
            corners[i].setVisibility((cornEn && isLocked && !hide) ? View.VISIBLE : View.GONE);
            if (cornEn && isLocked) {
                String ck = "lock_corner_"+CORNERS[i]+"_";
                int moonAlpha = prefs.getInt("lock_corner_moon_alpha",100);
                int strokeAlpha = prefs.getInt("lock_corner_stroke_alpha",200);
                int hideDelay = prefs.getInt("lock_corner_hide_dur",2500);
                int visMode = prefs.getInt(ck+"vis_mode",0);
                boolean isAuto = (visMode==1), isInv = (visMode==2);
                ((CornerView)corners[i]).updateProps(prefs.getInt("lock_corner_thick",8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                int priMode = prefs.getInt(ck+"pri_mode",0);
                int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if (priMode==1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
                p.flags = baseFlags; p.gravity = C_GRAV[i];
                int wPref = prefs.getInt(ck+"w",100), hPref = prefs.getInt(ck+"h",100);
                int mwPref = prefs.getInt(ck+"moon_w",100), mhPref = prefs.getInt(ck+"moon_h",100);
                int mxOffset = Math.abs(prefs.getInt(ck+"moon_x",1250)-1250);
                int myOffset = Math.abs(prefs.getInt(ck+"moon_y",1250)-1250);
                p.width = Math.max(10, Math.max(wPref, mwPref)+mxOffset);
                p.height = Math.max(10, Math.max(hPref, mhPref)+myOffset);
                p.x = prefs.getInt(ck+"x",0); p.y = prefs.getInt(ck+"y",0);
                wm.updateViewLayout(corners[i], p);
            }
        }
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        private String prefKeyBase;
        private View myView;
        private GestureDetector gd;
        private float sx, sy;
        private long st;
        public SidebarTouchListener(String keyBase, View v) {
            this.prefKeyBase = keyBase;
            this.myView = v;
            this.gd = new GestureDetector(EdgeBarService.this, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction(prefKeyBase+"_tap"); return true; }
                @Override public boolean onDoubleTap(MotionEvent e) { handleAction(prefKeyBase+"_dtap"); return true; }
                @Override public void onLongPress(MotionEvent e) { handleAction(prefKeyBase+"_long"); }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; }
            });
        }
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (myView != null && myView instanceof CornerView) ((CornerView)myView).triggerFlash();
            gd.onTouchEvent(e);
            if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); }
            else if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                if (Math.abs(dx) > 50 || Math.abs(dy) > 50) {
                    long duration = System.currentTimeMillis() - st;
                    boolean isHold = duration > prefs.getInt("hold_dur", 600);
                    String actionName = "";
                    if (myView instanceof CornerView && Math.abs(dx) > 40 && Math.abs(dy) > 40) { actionName = "diag" + (isHold ? "_hold" : ""); }
                    else { if (Math.abs(dx) > Math.abs(dy)) actionName = dx > 0 ? "right" : "left"; else actionName = dy > 0 ? "down" : "up"; if (isHold) actionName += "_hold"; }
                    handleAction(prefKeyBase + "_" + actionName);
                    return true;
                }
            }
            return true;
        }
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        super.onDestroy();
        try{ unregisterReceiver(stateReceiver); }catch(Exception e){}
        try{ unregisterReceiver(ipcReceiver); }catch(Exception e){}
        if (accHomeReceiver != null) try{ unregisterReceiver(accHomeReceiver); }catch(Exception e){}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        for (int i=0;i<5;i++) if (bars[i]!=null) wm.removeView(bars[i]);
        for (int i=0;i<4;i++) if (corners[i]!=null) wm.removeView(corners[i]);
        if (fV != null) wm.removeView(fV);
        removeAccessibleHome(); 
    }
    // [XÓA] toàn bộ method drawAccessibleHome() cũ (từ "private void drawAccessibleHome() {"
// đến dấu "}" đóng cuối method đó)

// [THÊM] thay bằng method mới hoàn chỉnh:
/**
 * IRON VEIL PHANTOM v19.12.3.6.0
 * drawAccessibleHome() — Thiết kế lại hoàn toàn Homacc overlay.
 *
 * Fix Bug 0: Flags đúng TYPE_ACCESSIBILITY_OVERLAY + NOT_TOUCH_MODAL
 * Anti-tapjacking: FLAG_WATCH_OUTSIDE_TOUCH + systemGestureExclusionRects
 * Pixel 2XL opt: null-check trước mỗi wm.addView, tránh crash token invalid
 */
private void drawAccessibleHome() {
    removeAccessibleHome(); // Dọn sạch trước, tránh duplicate view leak
    SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String px = "homacc_";
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    if (wm == null) return;
    int type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    // FLAG_SHOW_WHEN_LOCKED: Homacc vượt qua màn khóa, QS panel, chat bubble
    int baseF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

    // --- 5 EDGE BARS ---
    for (int i = 0; i < 5; i++) {
        boolean en = p.getBoolean(px + BARS[i] + "_en", false);
        if (!en) { accHomeBars[i] = null; continue; }

        View bar = new View(this);
        int alpha   = p.getInt(px + BARS[i] + "_alpha", 50);
        int w       = p.getInt(px + BARS[i] + "_w", 300);
        int h       = p.getInt(px + BARS[i] + "_h", 60);
        int x       = p.getInt(px + BARS[i] + "_x", 0);
        int y       = p.getInt(px + BARS[i] + "_y", 0);
        int priMode = p.getInt(px + BARS[i] + "_pri_mode", 0);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(alpha, 96, 125, 139));
        gd.setCornerRadius(24f);
        bar.setBackground(gd);

        int f = baseF;
        // priMode==1: xuyên thấu hoàn toàn — KHÔNG nhận touch
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            w, h, type, f, PixelFormat.TRANSLUCENT);
        lp.x = x; lp.y = y;
        lp.gravity = GRAV[i];

        try {
            wm.addView(bar, lp);
            accHomeBars[i] = bar;
        } catch (Exception e) {
            accHomeBars[i] = null;
            continue;
        }

        // Anti-tapjacking: loại bỏ OS gesture tại đúng vùng bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && priMode == 0) {
            bar.setSystemGestureExclusionRects(
                java.util.Collections.singletonList(
                    new android.graphics.Rect(0, 0, w, h)));
        }

        final int barIdx = i;
        bar.setOnTouchListener(new SidebarTouchListener("homacc_" + BARS[barIdx], null));
    }

    // --- 4 FRAME CORNERS ---
    for (int i = 0; i < 4; i++) {
        boolean en = p.getBoolean(px + "corner_" + CORNERS[i] + "_en", false);
        if (!en) { accHomeCorners[i] = null; continue; }

        CornerView corner = new CornerView(this, i, "homacc_");

        int moonAlpha   = p.getInt(px + "corner_moon_alpha", 100);
        int strokeAlpha = p.getInt(px + "corner_stroke_alpha", 200);
        int hideDelay   = p.getInt(px + "corner_hide_dur", 2500);
        int visMode     = p.getInt(px + "corner_" + CORNERS[i] + "_vis_mode", 0);
        int priMode     = p.getInt(px + "corner_" + CORNERS[i] + "_pri_mode", 0);

        corner.updateProps(
            p.getInt(px + "corner_thick", 8),
            moonAlpha, strokeAlpha,
            visMode == 1,  // auto-hide
            hideDelay,
            visMode == 2   // invisible
        );

        int f = baseF;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        String ck = px + "corner_" + CORNERS[i] + "_";
        int wP    = p.getInt(ck + "w", 100),       hP    = p.getInt(ck + "h", 100);
        int mwP   = p.getInt(ck + "moon_w", 100),  mhP   = p.getInt(ck + "moon_h", 100);
        int mxOff = Math.abs(p.getInt(ck + "moon_x", 1250) - 1250);
        int myOff = Math.abs(p.getInt(ck + "moon_y", 1250) - 1250);
        int cw = Math.max(10, Math.max(wP, mwP) + mxOff);
        int ch = Math.max(10, Math.max(hP, mhP) + myOff);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            cw, ch, type, f, PixelFormat.TRANSLUCENT);
        lp.x = p.getInt(ck + "x", 0);
        lp.y = p.getInt(ck + "y", 0);
        lp.gravity = C_GRAV[i];

        try {
            wm.addView(corner, lp);
            accHomeCorners[i] = corner;
        } catch (Exception e) {
            accHomeCorners[i] = null;
            continue;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && priMode == 0) {
            corner.setSystemGestureExclusionRects(
                java.util.Collections.singletonList(
                    new android.graphics.Rect(0, 0, cw, ch)));
        }

        final int cornIdx = i;
        corner.setOnTouchListener(
            new SidebarTouchListener("homacc_corner_" + CORNERS[cornIdx], corner));
    }
    isHomaccDrawn = true; // Đánh dấu đã vẽ xong
}

    private void removeAccessibleHome() {
    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    for (int i = 0; i < 5; i++) {
        if (accHomeBars[i] != null) { wm.removeView(accHomeBars[i]); accHomeBars[i] = null; }
    }
    for (int i = 0; i < 4; i++) {
        if (accHomeCorners[i] != null) { wm.removeView(accHomeCorners[i]); accHomeCorners[i] = null; }
    }
    isHomaccDrawn = false; // Reset guard để lần sau có thể vẽ lại
  }
/**
 * V19.12.3.6.2 Obsidian Veil Phantom
 * updateHomaccLive(): Cập nhật Homacc overlay khi kéo slider trong Design.
 * KHÔNG removeView/addView — chỉ updateViewLayout + invalidate.
 * Zero object allocation → Adreno 540 không bị GC pause.
 */
private void updateHomaccLive() {
    if (!isHomaccDrawn) return;
    SharedPreferences p = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String px = "homacc_";
    for (int i = 0; i < 5; i++) {
        if (accHomeBars[i] == null) continue;
        boolean en = p.getBoolean(px + BARS[i] + "_en", false);
        accHomeBars[i].setVisibility(en ? View.VISIBLE : View.GONE);
        if (!en) continue;
        int alpha   = p.getInt(px + BARS[i] + "_alpha", 50);
        int w       = p.getInt(px + BARS[i] + "_w", 300);
        int h       = p.getInt(px + BARS[i] + "_h", 60);
        int x       = p.getInt(px + BARS[i] + "_x", 0);
        int y       = p.getInt(px + BARS[i] + "_y", 0);
        int priMode = p.getInt(px + BARS[i] + "_pri_mode", 0);
        int visMode = p.getInt(px + BARS[i] + "_vis_mode", 0);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(alpha, 96, 125, 139));
        gd.setCornerRadius(24f);
        accHomeBars[i].setBackground(gd);
        // vis_mode: 0=hiện, 1=auto-hide (ẩn đến khi chạm), 2=vô hình
        accHomeBars[i].setAlpha(visMode == 0 ? 1f : 0f);
        int f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) accHomeBars[i].getLayoutParams();
        lp.flags = f; lp.width = w; lp.height = h; lp.x = x; lp.y = y;
        lp.gravity = GRAV[i];
        try { wm.updateViewLayout(accHomeBars[i], lp); } catch (Exception ignored) {}
    }
    for (int i = 0; i < 4; i++) {
        if (accHomeCorners[i] == null) continue;
        boolean en = p.getBoolean(px + "corner_" + CORNERS[i] + "_en", false);
        accHomeCorners[i].setVisibility(en ? View.VISIBLE : View.GONE);
        if (!en) continue;
        String ck = px + "corner_" + CORNERS[i] + "_";
        int moonAlpha   = p.getInt(px + "corner_moon_alpha", 100);
        int strokeAlpha = p.getInt(px + "corner_stroke_alpha", 200);
        int hideDelay   = p.getInt(px + "corner_hide_dur", 2500);
        int visMode     = p.getInt(ck + "vis_mode", 0);
        int priMode     = p.getInt(ck + "pri_mode", 0);
        ((CornerView) accHomeCorners[i]).updateProps(
            p.getInt(px + "corner_thick", 8),
            moonAlpha, strokeAlpha,
            visMode == 1, hideDelay, visMode == 2);
        int f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
              | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
              | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (priMode == 1) f |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int wP  = p.getInt(ck + "w", 100),      hP  = p.getInt(ck + "h", 100);
        int mwP = p.getInt(ck + "moon_w", 100), mhP = p.getInt(ck + "moon_h", 100);
        int mxO = Math.abs(p.getInt(ck + "moon_x", 1250) - 1250);
        int myO = Math.abs(p.getInt(ck + "moon_y", 1250) - 1250);
        int cw = Math.max(10, Math.max(wP, mwP) + mxO);
        int ch = Math.max(10, Math.max(hP, mhP) + myO);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) accHomeCorners[i].getLayoutParams();
        lp.flags = f; lp.width = cw; lp.height = ch;
        lp.x = p.getInt(ck + "x", 0); lp.y = p.getInt(ck + "y", 0);
        lp.gravity = C_GRAV[i];
        try { wm.updateViewLayout(accHomeCorners[i], lp); } catch (Exception ignored) {}
        accHomeCorners[i].invalidate();
    }
  }
} // <-- Dấu ngoặc nhọn kết thúc toàn bộ class EdgeBarService
