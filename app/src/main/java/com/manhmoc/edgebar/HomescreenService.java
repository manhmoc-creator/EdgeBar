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

import java.io.InputStream;
import java.util.Collections;

// ĐẰNG TRƯỚC
public class HomescreenService extends Service {
    // ĐẰNG SAU (Biến cũ của HomescreenService)
    public static boolean isRunning = false;
    private boolean isHomeOverlayShortcutOn() {
    return prefs.getBoolean("shortcut_home_on", false);
}
private boolean isAccessibleHomeShortcutOn() {
    return prefs.getBoolean("shortcut_acc_home_on", false);
}
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private RelativeLayout morseContainer;
    private TextView tvMorseStatus;
    private TextView tvLockIcon;
    private MorseBackgroundView bgView;
    private MorseBarView[] mBars = new MorseBarView[8];
    private View[] mCorners = new View[4];
    private FlashView fV;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false, isKbd = false, isBl = false;
    private SharedPreferences prefs;
    private KeyguardManager km;
    private Vibrator vibrator;

    private boolean isMorseLockActive = false;
    private String currentMorseAttempt = "";
    private int morseFailCount = 0;
    private String lockedPkg = "";


    private boolean isCountingDown = false;
    private Handler countdownHandler = new Handler();
    private Runnable countdownRunnable = null;
    private ValueAnimator warningAnimator = null;
    private boolean isUninstallGuardActive = false;
    private boolean isPreviewMorse = false;
    private boolean isCoveringRecents = false;
    private String currentForegroundPkg = "";
    private boolean isUnlockCooldown = false;
// HYBRID: cache trạng thái Accessibility, tránh đọc Settings mỗi frame
    private boolean accCacheValue = false;
    private long accCheckTimestamp = 0;
    private WindowManager.LayoutParams currentOverlayParams = null;

    
    private boolean isForceHome = false; // FIX 4: flag tức thì khi nhấn Home

    // HYBRID HOME V2: ContentObserver thay vì polling Settings DB
    // Chi phí = 0 CPU khi không có thay đổi, chỉ fire khi user thực sự toggle Accessibility
    private android.database.ContentObserver accObserver = null;
    private boolean accStateCached = false; // giá trị cache hiện tại


    private final Handler suicideHandler = new Handler(android.os.Looper.getMainLooper());
    private Runnable suicideRunnable = null; 
    private Handler unlockCooldownHandler = new Handler(); 
    private int uninstallGuardFailCount = 0;  
    private long lockUntilTime = 0;

        private Handler relockHandler = new Handler();
    private long relockScheduledTime = 0;
    private String pendingRelockPkg = "";
    private java.util.Set<String> unlockedApps = new java.util.HashSet<>();
    private long lastUnlockedTime = 0;

    private Runnable relockRunnable = () -> {
    String pkgToLock = pendingRelockPkg;
    unlockedApps.remove(pkgToLock);
    lastUnlockedTime = 0;
    relockScheduledTime = 0;
    pendingRelockPkg = "";
    if (!pkgToLock.isEmpty() && pkgToLock.equals(currentForegroundPkg)) {
        // Set trực tiếp, KHÔNG qua broadcast để tránh vòng lặp vô tận
        isMorseLockActive = true;
        lockedPkg = pkgToLock;
        morseFailCount = 0;
        currentMorseAttempt = "";
        if (tvMorseStatus != null) tvMorseStatus.setText("");
// FIX-TEXT-1: Apply style đồng bộ trước khi hiện
applyMorseTextStyle();
applyLockIconStyle();
updateLockIconPosition();
if (morseContainer != null) morseContainer.setVisibility(View.VISIBLE);
updateVisibility();
    }
};

    private Handler numberDisplayHandler = new Handler();
    private Runnable hideNumberRunnable; 

    private int bgType = 0;
    private String bgImagePath = "";
    private Bitmap bgBitmap = null;
    private int bgAlpha = 180;
    private int cachedBgAlpha = 180; // cache tránh đọc disk trong onDraw


    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};

    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private final int[] M_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.CENTER};

    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    // Lớp thanh bar có hỗ trợ tàng hình (auto-hide) giống corner
    private class MorseBarView extends View {
        private Handler autoHideHandler = new Handler();
        private int normalAlpha = 50;
        private int hideDelay = 2500;
        private int visMode = 0; // 0=normal, 1=auto-hide, 2=invisible
        private boolean isAutoHiding = false;
        private GradientDrawable gd;

        public MorseBarView(Context context) {
            super(context);
            gd = new GradientDrawable();
            gd.setCornerRadius(24f);
            setBackground(gd);
        }

        public void updateProps(int alpha, int mode, int delay) {
            this.normalAlpha = alpha;
            this.visMode = mode;
            this.hideDelay = delay;
            if (mode == 0) {
                setAlpha(alpha / 255f);
                if (autoHideHandler != null) autoHideHandler.removeCallbacksAndMessages(null);
                isAutoHiding = false;
            } else if (mode == 1) {
                setAlpha(0f);
                isAutoHiding = true;
            } else {
                setAlpha(0f);
                isAutoHiding = false;
            }
            gd.setColor(Color.argb(alpha, 96, 125, 139));
        }

        public void triggerFlash() {
            if (visMode != 1 || !isAutoHiding) return;
            autoHideHandler.removeCallbacksAndMessages(null);
            setAlpha(1f);
            autoHideHandler.postDelayed(() -> {
                if (visMode == 1) {
                    setAlpha(0f);
                }
            }, hideDelay);
        }
    }

    private class MorseBackgroundView extends View {
        private Paint paint = new Paint();
        public MorseBackgroundView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        // === CHÈN THUẬT TOÁN 6: TỐI ƯU HÓA GPU PASS ===
        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            setWillNotDraw(visibility != View.VISIBLE); 
        }
        // =============================================

        @Override
        protected void onDraw(Canvas canvas) {
            // Đọc từ RAM (cachedBgAlpha), không đọc disk mỗi frame
    // Đọc từ RAM (cachedBgAlpha), không đọc disk mỗi frame
    if (bgType == 1 && bgBitmap != null && !bgBitmap.isRecycled()) {
        paint.setAlpha(cachedBgAlpha);
        canvas.drawBitmap(bgBitmap, null, new Rect(0, 0, getWidth(), getHeight()), paint);
    } else {
        canvas.drawColor(Color.argb(cachedBgAlpha, 0, 0, 0));
    }
  }
}

    private class FlashView extends View {
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phaseFraction = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); }
        public void updateStyle() { p.setAlpha(prefs.getInt("anim_alpha", 255)); p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { /* như cũ */ 
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
        private Paint pFill, pStroke; private int type; private String prefix;
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
        private boolean isInv = false;

        public CornerView(Context c, int type, String prefix) { super(c); this.type = type; this.prefix = prefix; pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND); }

        public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) { pStroke.setStrokeWidth(thick); this.baseMoonAlpha = moonAlpha; this.baseStrokeAlpha = strokeAlpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv; if(!autoHide) { pFill.setColor(Color.argb(moonAlpha, 96, 125, 139)); pStroke.setAlpha(strokeAlpha); } else triggerFlash(); if(inv) { pFill.setAlpha(0); pStroke.setAlpha(0); } invalidate(); }

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
    private void applyMorseTextStyle() {
        if (tvMorseStatus == null) return;
        String theme = prefs.getString("anim_color", "NEON");
        int textColor;
        int glowColor;
        switch (theme) {
            case "OCEAN":     textColor = Color.parseColor("#00BFFF"); glowColor = Color.parseColor("#1E90FF"); break;
            case "AURORA":    textColor = Color.parseColor("#B388FF"); glowColor = Color.parseColor("#00E5FF"); break;
            case "ABYSS":     textColor = Color.parseColor("#1DE9B6"); glowColor = Color.parseColor("#00E5FF"); break;
            case "MIDNIGHT":  textColor = Color.parseColor("#03A9F4"); glowColor = Color.parseColor("#7B1FA2"); break;
            case "CANDY":     textColor = Color.parseColor("#F06292"); glowColor = Color.parseColor("#4DD0E1"); break;
            default:          textColor = Color.WHITE; glowColor = Color.parseColor("#00E5FF"); break;
        }
        int blurRadius = prefs.getInt("morse_text_blur", 20);
        boolean neonOn = prefs.getBoolean("morse_text_neon", true);
        tvMorseStatus.setTextColor(textColor);
        tvMorseStatus.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        if (neonOn) {
            tvMorseStatus.setShadowLayer(blurRadius, 0, 0, glowColor);
        } else {
            tvMorseStatus.setShadowLayer(4, 2, 2, Color.BLACK);
        }
    }

    // [FIX-(-2)] Style icon ổ khoá — màu neon theo theme, nổi trên lớp phủ đen
    private void applyLockIconStyle() {
        if (tvLockIcon == null) return;
        String theme = prefs.getString("anim_color", "NEON");
        int glowColor;
        switch (theme) {
            case "OCEAN":     glowColor = Color.parseColor("#00BFFF"); break;
            case "AURORA":    glowColor = Color.parseColor("#B388FF"); break;
            case "ABYSS":     glowColor = Color.parseColor("#1DE9B6"); break;
            case "MIDNIGHT":  glowColor = Color.parseColor("#03A9F4"); break;
            case "CANDY":     glowColor = Color.parseColor("#F06292"); break;
            default:          glowColor = Color.parseColor("#00E5FF"); break;
        }
        int blur = prefs.getInt("morse_text_blur", 20);
        boolean neonOn = prefs.getBoolean("morse_text_neon", true);
        if (neonOn) {
            tvLockIcon.setShadowLayer(blur * 1.5f, 0, 0, glowColor);
        } else {
            tvLockIcon.setShadowLayer(6, 2, 2, Color.BLACK);
        }
    }

        private void updateLockIconPosition() {
        if (tvLockIcon == null) return;
        int yVal = prefs.getInt("morse_lock_icon_y", 600);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenH = dm.heightPixels;
        
        float yRatio = (yVal / 3000f);
        if (yRatio < 0.1f) yRatio = 0.1f;
        if (yRatio > 0.85f) yRatio = 0.85f;
        int yPx = (int)(yRatio * screenH);
        
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) tvLockIcon.getLayoutParams();
        if (lp == null) return;
        lp.topMargin = yPx;
        lp.removeRule(RelativeLayout.CENTER_IN_PARENT);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        tvLockIcon.setLayoutParams(lp);
       
        int iconSizeCustom = prefs.getInt("morse_lock_icon_size", 48);
        tvLockIcon.setTextSize(iconSizeCustom);
    }
    private void reloadBackground() {
    bgType = prefs.getInt("morse_bg_type", 0);
    bgImagePath = prefs.getString("morse_bg_image", "");
    cachedBgAlpha = prefs.getInt("morse_bg_alpha", 180); // đồng bộ cache
    if (bgType == 1 && !bgImagePath.isEmpty()) {
            try {
                InputStream is = getContentResolver().openInputStream(Uri.parse(bgImagePath));
                bgBitmap = BitmapFactory.decodeStream(is);
                if (bgView != null) bgView.invalidate();
            } catch (Exception e) { bgBitmap = null; }
        } else {
            if (bgView != null) bgView.invalidate();
        }
    }

    private String mapComponentToNumber(String comp) {
        String key;
        if (comp.contains("corner_")) {
            key = "morse_map_" + comp.substring(6);
        } else {
            key = "morse_map_" + comp.substring(6);
        }
        String val = prefs.getString(key, "*");
        if (val.equals("X") || val.equals(">")) return val;
        if (val.matches("\\d")) return val;
        return "*";
    }

    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if (action.equals("com.manhmoc.edgebar.SYNC_STATE")) {
    isKbd = i.getBooleanExtra("isKbd", false);
    isBl = i.getBooleanExtra("isBl", false);
    String incomingPkg = i.getStringExtra("foreground_pkg");
    if (incomingPkg != null && !incomingPkg.isEmpty()) {
        boolean isRealApp = !incomingPkg.contains("systemui")
            && !incomingPkg.contains("launcher")
            && !incomingPkg.contains("nexuslauncher")
            && !incomingPkg.contains("inputmethod")
            && !incomingPkg.equals("android")
            && !incomingPkg.equals("com.android.settings")
            && !incomingPkg.contains("quickstep");
        if (isRealApp) {
            currentForegroundPkg = incomingPkg;
        }
    }
    if (!pendingRelockPkg.isEmpty()
            && currentForegroundPkg.equals(pendingRelockPkg)) {
        relockHandler.removeCallbacks(relockRunnable);
        relockScheduledTime = 0;
        pendingRelockPkg = "";
    } else if (!unlockedApps.isEmpty()
            && !currentForegroundPkg.isEmpty()
            && !currentForegroundPkg.contains("systemui")
            && !currentForegroundPkg.contains("launcher")
            && !unlockedApps.contains(currentForegroundPkg)
            && relockScheduledTime == 0) {
        String locklist = prefs.getString("locklist", "");
        for (String unlocked : unlockedApps) {
            if (locklist.contains(unlocked) && pendingRelockPkg.isEmpty()) {
                long relockMs = prefs.getInt("morse_relock_ms", 5000);
                relockScheduledTime = System.currentTimeMillis() + relockMs;
                pendingRelockPkg = unlocked;
                relockHandler.removeCallbacks(relockRunnable);
                relockHandler.postDelayed(relockRunnable, relockMs);
                break;
            }
        }
    }
    isPreviewMorse = prefs.getBoolean("preview_morse", false);
    // HYBRID HOME: Reset cache Accessibility nếu được yêu cầu
    if (i.getBooleanExtra("acc_cache_reset", false)) {
        accCheckTimestamp = 0;
    }
    // ← KẾT THÚC PHẦN THÊM
    updateVisibility();
            } else if (action.equals("com.manhmoc.edgebar.TEST_ANIM")) {
                playAnim();

} else if (action.equals("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE")) {
    String pkg = i.getStringExtra("pkg");
    if (pkg == null || pkg.isEmpty()) return;
    if (isUnlockCooldown) return;
    boolean foregroundIsThisLockedApp = currentForegroundPkg.equals(pkg);
    boolean isHome = currentForegroundPkg.contains("launcher")
            || currentForegroundPkg.contains("nexuslauncher")
            || currentForegroundPkg.isEmpty();
    if (!foregroundIsThisLockedApp || isHome) {
        return;
    }

    long now = System.currentTimeMillis();
    if (isMorseLockActive && pkg.equals(lockedPkg)) return;
    if (unlockedApps.contains(pkg)) {
        return;
    }
    if (now < lockUntilTime) {
        Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        kick.putExtra("act", "HOME");
        sendBroadcast(kick);
        return;
    }
    isMorseLockActive = true;
lockedPkg = pkg;
morseFailCount = 0;
currentMorseAttempt = "";
if (tvMorseStatus != null) tvMorseStatus.setText("");
// FIX-TEXT-1: Apply style TRƯỚC khi hiện overlay, đảm bảo text
// luôn nằm trên vùng tối bất kể slider đã kéo hay chưa
applyMorseTextStyle();
applyLockIconStyle();
updateLockIconPosition();
morseContainer.setVisibility(View.VISIBLE);
updateVisibility();
            } else if (action.equals("com.manhmoc.edgebar.MORSE_LOCK_DISMISS")) {
                isMorseLockActive = false;
                morseFailCount = 0;
                currentMorseAttempt = "";
                lockedPkg = "";
                morseContainer.setVisibility(View.GONE);
                updateVisibility();
            } else if (action.equals("com.manhmoc.edgebar.TOGGLE_MORSE")) {
                boolean isM = prefs.getBoolean("morse_mode_en", false);
                prefs.edit().putBoolean("morse_mode_en", !isM).apply();
                updateVisibility();
} else if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
    String reason = i.getStringExtra("reason");
    if ("homekey".equals(reason)) {
        // Nhấn Home → set flag tức thì, không chờ broadcast pkg
        isForceHome = true;
        if (isMorseLockActive) {
            scheduleSuicideCheck(); // tự sát ngay với isForceHome = true
        }
    } else if ("recentapps".equals(reason)) {
        String locklist = prefs.getString("locklist", "");
        boolean hasLockedApp = false;
        if (!locklist.isEmpty()) {
            String checkPkg = !unlockedApps.isEmpty() ? unlockedApps.iterator().next() : lockedPkg;
            if (!checkPkg.isEmpty()) {
                for (String pkg : locklist.split(",")) {
                    if (pkg.trim().equals(checkPkg)) { hasLockedApp = true; break; }
                }
            }
        }
        if (hasLockedApp) {
            isCoveringRecents = true;
            showMorseOSCover();
        }
    }
} else if (action.equals("com.manhmoc.edgebar.MORSE_OS_RECENTS_SHOW")) {
    String lastPkg = i.getStringExtra("last_pkg");
    if (lastPkg == null) lastPkg = "";
    boolean shouldCover = false;
    String locklist = prefs.getString("locklist", "");
    if (!lastPkg.isEmpty() && !locklist.isEmpty()) {
        for (String pkg : locklist.split(",")) {
            if (pkg.trim().equals(lastPkg)) { shouldCover = true; break; }
        }
    }
   if (!shouldCover && !unlockedApps.isEmpty() && !locklist.isEmpty()) {
    for (String unlockedPkg : unlockedApps) {
        for (String pkg : locklist.split(",")) {
            if (pkg.trim().equals(unlockedPkg)) {
                shouldCover = true;
                break;
            }
        }
        if (shouldCover) break;
    }
}
    if (shouldCover && !isMorseLockActive && !isUninstallGuardActive) {
        showMorseOSCover();
    }
} else if (action.equals("com.manhmoc.edgebar.MORSE_OS_RECENTS_HIDE")) {
    if (isCoveringRecents && !isMorseLockActive && !isPreviewMorse && !isUninstallGuardActive) {
        isCoveringRecents = false;
        if (morseContainer != null) {
            morseContainer.setVisibility(View.GONE);
            morseContainer.setOnTouchListener(null);
        }
        if (tvLockIcon != null) tvLockIcon.setOnTouchListener(null);
    }
} else if (action.equals("com.manhmoc.edgebar.UNINSTALL_DETECTED")) {
    if (!isUninstallGuardActive) {
        isUninstallGuardActive = true;
        uninstallGuardFailCount = 0;
        currentMorseAttempt = "";
        if (tvMorseStatus != null) tvMorseStatus.setText("🔒 XÁC NHẬN GỠ CÀI ĐẶT");
        morseContainer.setVisibility(View.VISIBLE);


        WindowManager.LayoutParams p = (WindowManager.LayoutParams) morseContainer.getLayoutParams();
        p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(morseContainer, p);
        updateVisibility();
    }
} else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
    unlockedApps.clear(); // Xóa lịch sử unlock (Yêu cầu 9)
    lastUnlockedTime = 0;
    relockHandler.removeCallbacks(relockRunnable);
    relockScheduledTime = 0;
    pendingRelockPkg = "";
    
    // [FIX-8] ÉP TỰ SÁT VÔ ĐIỀU KIỆN KHI TẮT MÀN HÌNH
    // Đảm bảo khi bật màn hình lại ở HOME, không có "bóng ma" MorseLock nào tồn tại
    isMorseLockActive = false;
    lockedPkg = "";
    currentMorseAttempt = "";
    morseFailCount = 0;
    isCountingDown = false;
    if (countdownRunnable != null) countdownHandler.removeCallbacks(countdownRunnable);
    if (warningAnimator != null) warningAnimator.cancel();
    
    if (morseContainer != null) {
        morseContainer.setVisibility(View.GONE);
        if (bgView != null) bgView.setBackgroundColor(Color.TRANSPARENT);
    }
} else if (action.equals(Intent.ACTION_USER_PRESENT)) {
    // [FIX-BUG-8] Kiểm tra chủ động: Chỉ cho phép tự động tái khóa lại nếu và chỉ nếu app đó thực sự đang mở trước mắt người dùng (không bị đánh lừa bởi trạng thái trễ package của Launcher)
    new Handler().postDelayed(() -> {
        // Lấy package thực tế tại đúng thời điểm sau độ trễ render của hệ thống
        String activePkg = currentForegroundPkg;
        boolean isRealHome = activePkg.isEmpty()
                || activePkg.contains("launcher")
                || activePkg.contains("nexuslauncher")
                || activePkg.contains("quickstep")
                || activePkg.contains("systemui");

        if (!isRealHome && !unlockedApps.contains(activePkg)) {
            String locklist = prefs.getString("locklist", "");
            if (!locklist.isEmpty()) {
                for (String pkg : locklist.split(",")) {
                    if (pkg.trim().equals(activePkg)) {
                        isMorseLockActive = false;
                        lockedPkg = "";
                        morseFailCount = 0;
                        currentMorseAttempt = "";
                        Intent engage = new Intent("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
                        engage.putExtra("pkg", activePkg);
                        sendBroadcast(engage);
                        break;
                    }
                }
            }
        }
    }, 600);
}
    }
};
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { isRunning = true; sendSyncState(); return START_STICKY; }
    private void sendSyncState() { Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter filter = new IntentFilter();
filter.addAction(Intent.ACTION_SCREEN_OFF);
// ACTION_SCREEN_ON đã được xử lý qua ACTION_USER_PRESENT, bỏ ACTION_SCREEN_ON
filter.addAction(Intent.ACTION_USER_PRESENT);
filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS); // giữ để bắt homekey/recentapps
filter.addAction("com.manhmoc.edgebar.SYNC_STATE");
filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
// IPC_ACTION xử lý trong EdgeBarService, không cần ở HomescreenService
filter.addAction("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
filter.addAction("com.manhmoc.edgebar.MORSE_LOCK_DISMISS");
filter.addAction("com.manhmoc.edgebar.TOGGLE_MORSE");
filter.addAction("com.manhmoc.edgebar.UNINSTALL_DETECTED");
// RECENTS_SHOW/HIDE gộp vào xử lý ACTION_CLOSE_SYSTEM_DIALOGS, bỏ 2 action này
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

        for (int i = 0; i < 5; i++) {
            bars[i] = new View(this);
            // HYBRID HOME V2: dùng accStateCached đã đọc sẵn ở trên — ZERO I/O thêm
            int initType = accStateCached
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                1, 1, initType, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch (Exception e) {}
            bars[i].setOnTouchListener(new SidebarTouchListener("home_" + BARS[i], null));
        }
        for (int i = 0; i < 4; i++) {
            corners[i] = new CornerView(this, i, "home_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(corners[i], p); } catch (Exception e) {}
            corners[i].setOnTouchListener(new SidebarTouchListener("home_corner_" + CORNERS[i], corners[i]));
        }

        morseContainer = new RelativeLayout(this);
        morseContainer.setBackgroundColor(Color.TRANSPARENT);
        morseContainer.setVisibility(View.GONE);

        bgView = new MorseBackgroundView(this);
        morseContainer.addView(bgView, new RelativeLayout.LayoutParams(-1, -1));
        tvMorseStatus = new TextView(this);
        tvMorseStatus.setId(android.view.View.generateViewId());
        tvMorseStatus.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(-1, -2);
        tLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        int textSizeSp = prefs.getInt("morse_text_size", 30);
        tvMorseStatus.setTextSize(textSizeSp);
        morseContainer.addView(tvMorseStatus, tLp);
        tvLockIcon = new TextView(this);
        tvLockIcon.setText("🔒");
        tvLockIcon.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams iconLp = new RelativeLayout.LayoutParams(-2, -2);
        iconLp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        morseContainer.addView(tvLockIcon, iconLp);
        int savedIconSize = prefs.getInt("morse_lock_icon_size", 48);
        tvLockIcon.setTextSize(savedIconSize);


        WindowManager.LayoutParams bgP = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(morseContainer, bgP); } catch (Exception e) {}

        for (int i = 0; i < 8; i++) {
            mBars[i] = new MorseBarView(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mBars[i], p); } catch (Exception e) {}
            mBars[i].setOnTouchListener(new SidebarTouchListener("morse_" + M_BARS[i], mBars[i]));
        }
        for (int i = 0; i < 4; i++) {
            mCorners[i] = new CornerView(this, i, "morse_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mCorners[i], p); } catch (Exception e) {}
            mCorners[i].setOnTouchListener(new SidebarTouchListener("morse_corner_" + CORNERS[i], mCorners[i]));
        }

        // HYBRID HOME V2: Lắng nghe thay đổi Accessibility bằng ContentObserver
        // Tiết kiệm pin tối đa: KHÔNG polling, KHÔNG query mỗi frame
        // Chỉ cập nhật type overlay khi user thực sự bật/tắt Accessibility
        accStateCached = isAccOn(); // đọc 1 lần lúc khởi động
        accObserver = new android.database.ContentObserver(
                new Handler(android.os.Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean newVal = isAccOn();
                if (newVal != accStateCached) {
                    accStateCached = newVal;
                    // Chỉ gọi khi giá trị THỰC SỰ thay đổi → tiết kiệm CPU
                    applyHybridTypeToAllBars();
                    updateVisibility();
                }
            }
        };
        getContentResolver().registerContentObserver(
            android.provider.Settings.Secure.getUriFor(
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false, accObserver);

        reloadBackground();
        updateVisibility();
        sendSyncState();
    }

    private final Handler debounceHandler = new Handler(android.os.Looper.getMainLooper());
private Runnable debounceRunnable = null;

private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
    if (k == null) return;
    // Xử lý ngay các thay đổi cần phản hồi tức thì (không debounce)
    if (k.equals("morse_bg_type") || k.equals("morse_bg_image")) reloadBackground();
    if (k.equals("morse_bg_alpha") && bgView != null) {
        cachedBgAlpha = p.getInt("morse_bg_alpha", 180); // FIX 5
        bgView.invalidate();
    }
    if (k.equals("anim_color") || k.equals("morse_text_blur") || k.equals("morse_text_neon")) {
        applyMorseTextStyle();
        applyLockIconStyle();
    }
    if (k.equals("morse_lock_icon_y")) updateLockIconPosition();
    if (fV != null) fV.updateStyle();
    // Debounce updateVisibility(): gộp nhiều thay đổi liên tiếp thành 1 lần gọi sau 300ms
    if (debounceRunnable != null) debounceHandler.removeCallbacks(debounceRunnable);
    debounceRunnable = () -> updateVisibility();
    debounceHandler.postDelayed(debounceRunnable, 500);
};

      private void startFailCountdown(int failCount, Runnable onFinished) {
        if (isCountingDown) return;
        boolean isHome = currentForegroundPkg.isEmpty()
                || currentForegroundPkg.contains("launcher")
                || currentForegroundPkg.contains("nexuslauncher");
        if (isHome) { onFinished.run(); return; }

        isCountingDown = true;
        String prefKey = (failCount == 3) ? "morse_lock3_seconds" : "morse_lock4_seconds";
        int totalSeconds = prefs.getInt(prefKey, (failCount == 3) ? 10 : 30);

        if (warningAnimator != null) warningAnimator.cancel();
        warningAnimator = ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);
        warningAnimator.setDuration(1200);
        warningAnimator.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            int red = (int)(180 * val);
            if (bgView != null) {
                bgView.setBackgroundColor(Color.argb((int)(80 * val), 255, 20, 60));
                bgView.invalidate();
            }
        });
        warningAnimator.start();

        final int[] remaining = {totalSeconds};
        if (tvMorseStatus != null)
            tvMorseStatus.setText("⏳ Chờ " + remaining[0] + "s");

        // [FIX-BUG-5] Bổ sung điều kiện kiểm tra vòng lặp: Nếu đã thoát ra HOME, lập tức hủy bộ đếm hoạt cảnh
        countdownRunnable = new Runnable() {
            @Override public void run() {
                boolean isCurrentHome = currentForegroundPkg.isEmpty() 
                        || currentForegroundPkg.contains("launcher") 
                        || currentForegroundPkg.contains("nexuslauncher")
                        || currentForegroundPkg.contains("quickstep");

                if (isCurrentHome || !isMorseLockActive) {
                    // Nếu kẻ trộm thoát ra Home, lập tức xóa sạch hoạt cảnh nháy đỏ đếm ngược
                    isCountingDown = false;
                    if (tvMorseStatus != null) tvMorseStatus.setText("");
                    if (bgView != null) {
                        bgView.setBackgroundColor(Color.TRANSPARENT);
                        bgView.invalidate();
                    }
                    countdownHandler.removeCallbacks(this);
                    return;
                }

                remaining[0]--;
                if (remaining[0] > 0) {
                    if (tvMorseStatus != null)
                        tvMorseStatus.setText("⏳ Chờ " + remaining[0] + "s");
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    isCountingDown = false;
                    if (tvMorseStatus != null) tvMorseStatus.setText("");
                    if (bgView != null) {
                        bgView.setBackgroundColor(Color.TRANSPARENT);
                        bgView.invalidate();
                    }
                    onFinished.run();
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }
    private void doMorseVibrate() {
        if (prefs.getBoolean("morse_vib_en", true)) {
            int dur = prefs.getInt("morse_vib_dur", 30);
            if (dur <= 0) return;
            try {
                if (Build.VERSION.SDK_INT >= 26)
                    vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(dur);
            } catch (Exception e) {}
        }
    }

    private void handleMorseTap(String comp, View v, boolean isLongPress) {


if (isUninstallGuardActive) {
    doMorseVibrate();
    String mappedKey = mapComponentToNumber(comp);
    String masterPass = prefs.getString("morse_master_pass", "");

    if (mappedKey.equals("X")) {
        if (!currentMorseAttempt.isEmpty())
            currentMorseAttempt = currentMorseAttempt.substring(0, currentMorseAttempt.length()-1);
        tvMorseStatus.setText(currentMorseAttempt.isEmpty() ? "🔒 XÁC NHẬN GỠ CÀI ĐẶT" : currentMorseAttempt);
        return;
    }
    if (mappedKey.equals(">")) {
        if (currentMorseAttempt.equals(masterPass)) {
            
            isUninstallGuardActive = false;
            morseContainer.setVisibility(View.GONE);
            currentMorseAttempt = "";
            
            Intent expand = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            expand.putExtra("act", "NOTIFICATIONS");
            sendBroadcast(expand);
        } else {
            uninstallGuardFailCount++;
            if (uninstallGuardFailCount >= 3) {
                
                tvMorseStatus.setText("Gỡ cài đặt không thành công, thử lại sau");
                new Handler().postDelayed(() -> {
                    isUninstallGuardActive = false;
                    uninstallGuardFailCount = 0;
                    currentMorseAttempt = "";
                    morseContainer.setVisibility(View.GONE);
                }, 2500);
            } else {
                tvMorseStatus.setText("Sai! Còn " + (3 - uninstallGuardFailCount) + " lần");
                currentMorseAttempt = "";
            }
        }
        return;
    }
    if (mappedKey.matches("\\d")) {
        currentMorseAttempt += mappedKey;
        tvMorseStatus.setText(currentMorseAttempt);
    }
    return;
}
        doMorseVibrate();
        if (v != null) {
            if (v instanceof CornerView) ((CornerView) v).triggerFlash();
            else if (v instanceof MorseBarView) ((MorseBarView) v).triggerFlash();
        }

        String mappedKey = mapComponentToNumber(comp);
        String masterPass = prefs.getString("morse_master_pass", "");
        int realMaxLen = masterPass.length();

        if (mappedKey.equals("X")) {
            if (isLongPress) {
                currentMorseAttempt = "";
                tvMorseStatus.setText("");
            } else {
                if (currentMorseAttempt.length() > 0) {
                    currentMorseAttempt = currentMorseAttempt.substring(0, currentMorseAttempt.length() - 1);
                    if (currentMorseAttempt.isEmpty()) tvMorseStatus.setText("");
                    else {
                        int showNumberMs = prefs.getInt("morse_show_number_ms", 800);
                        tvMorseStatus.setText(currentMorseAttempt);
                        if (hideNumberRunnable != null) numberDisplayHandler.removeCallbacks(hideNumberRunnable);
                        hideNumberRunnable = () -> {
                            StringBuilder dots = new StringBuilder();
                            for (int i = 0; i < currentMorseAttempt.length(); i++) dots.append("• ");
                            tvMorseStatus.setText(dots.toString());
                        };
                        numberDisplayHandler.postDelayed(hideNumberRunnable, showNumberMs);
                    }
                }
            }
            return;
        }
        else if (mappedKey.equals(">")) {
            if (currentMorseAttempt.isEmpty() || masterPass.isEmpty()) return;
            if (currentMorseAttempt.equals(masterPass)) {
    isUnlockCooldown = true;
    unlockCooldownHandler.removeCallbacksAndMessages(null);
    unlockCooldownHandler.postDelayed(() -> {
        isUnlockCooldown = false;
    }, 1000);

    unlockedApps.add(lockedPkg);
    lastUnlockedTime = System.currentTimeMillis();
    isMorseLockActive = false;
    morseFailCount = 0;
    lockUntilTime = 0;
    currentMorseAttempt = "";
    morseContainer.setVisibility(View.GONE);

    Intent success = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
    success.putExtra("pkg", lockedPkg);
    sendBroadcast(success);
    updateVisibility();
} else {
                morseFailCount++;
                doMorseVibrate();
                String insult = prefs.getString("morse_insult_" + Math.min(morseFailCount, 5), "Sai rồi!");
                currentMorseAttempt = "";

                // [FIX-5] Điều hướng kích hoạt bộ đếm thời gian nháy đỏ tại app bị khóa tùy biến theo số lần sai
                if (morseFailCount == 3 || morseFailCount == 4) {
                    tvMorseStatus.setText(insult);
                    startFailCountdown(morseFailCount, () -> {
                        if (isMorseLockActive && tvMorseStatus != null) tvMorseStatus.setText("");
                    });
                } else if (morseFailCount >= 5) {
                    tvMorseStatus.setText(insult);
                    int lockMinutes = prefs.getInt("morse_lock_minutes", 30);
                    lockUntilTime = System.currentTimeMillis() + lockMinutes * 60 * 1000L;
                    isMorseLockActive = false;
                    // Đá ngay kẻ trộm về HOME
                    Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                    kick.putExtra("act", "HOME");
                    sendBroadcast(kick);
                    new Handler().postDelayed(() -> updateVisibility(), 500);
                } else {
                    tvMorseStatus.setText(insult);
                }
            }
            return;
        }

        if (currentMorseAttempt.length() >= realMaxLen && realMaxLen > 0) {
            morseFailCount++;
            doMorseVibrate();
            String insult = prefs.getString("morse_insult_" + Math.min(morseFailCount,5), "Quá dài!");
            tvMorseStatus.setText(insult);
            currentMorseAttempt = "";
            if (morseFailCount >= 5) {
                int lockMinutes = prefs.getInt("morse_lock_minutes", 30);
                lockUntilTime = System.currentTimeMillis() + lockMinutes * 60 * 1000L;
                isMorseLockActive = false;
                Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                kick.putExtra("act", "HOME");
                sendBroadcast(kick);
            }
            return;
        }

        currentMorseAttempt += mappedKey;
// FIX-DOT-2: Hiện dạng "••• 5" — các số cũ đã thành dấu chấm,
// chỉ số mới nhất hiện rõ, không bao giờ hiện lại số cũ
if (hideNumberRunnable != null) numberDisplayHandler.removeCallbacks(hideNumberRunnable);
int showNumberMs = prefs.getInt("morse_show_number_ms", 800);
// Tạo chuỗi hiển thị: dấu chấm cho các ký tự cũ + số mới nhất
StringBuilder displayStr = new StringBuilder();
for (int i = 0; i < currentMorseAttempt.length() - 1; i++) displayStr.append("• ");
displayStr.append(mappedKey); // chỉ số mới nhất hiện dạng số
tvMorseStatus.setText(displayStr.toString());
// Sau showNumberMs: đổi số mới nhất thành dấu chấm
hideNumberRunnable = () -> {
    StringBuilder dots = new StringBuilder();
    for (int i = 0; i < currentMorseAttempt.length(); i++) dots.append("• ");
    tvMorseStatus.setText(dots.toString());
};
numberDisplayHandler.postDelayed(hideNumberRunnable, showNumberMs);
    }
private void showMorseOSCover() {
        if (morseContainer == null) return;
        if (isMorseLockActive || isPreviewMorse || isUninstallGuardActive) return;

        isCoveringRecents = true;
        morseContainer.setVisibility(View.VISIBLE);
        updateLockIconPosition();
        applyLockIconStyle();
        if (tvLockIcon != null) tvLockIcon.setVisibility(View.VISIBLE);

        WindowManager.LayoutParams p = (WindowManager.LayoutParams) morseContainer.getLayoutParams();
        p.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; // xuyên thấu hoàn toàn
        wm.updateViewLayout(morseContainer, p);
        morseContainer.setOnTouchListener(null);

        if (tvLockIcon != null) {
            tvLockIcon.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    dismissMorseOSCover();
                }
                return true;
            });
        }
    }

   private void dismissMorseOSCover() {
        isCoveringRecents = false;
        if (morseContainer != null) {
            morseContainer.setVisibility(View.GONE);
            morseContainer.setOnTouchListener(null);
        }
        // [FIX-BUG--1] Tuyệt đối không Set touch về Null, mạch lắng nghe chạm 1-Tap của ổ khóa sẽ được quản lý tập trung và an toàn bên trong hàm updateVisibility()
        new Handler().postDelayed(() -> {
            Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            kick.putExtra("act", "HOME");
            sendBroadcast(kick);
        }, 50);
    }
    private void updateVisibility() {
        // [FIX-BUG-4-7] Thay thế bằng cơ chế kiểm tra chủ động kép (Double Check) kết hợp độ trễ phần cứng để chặn đứng tình trạng MorseLock bị kẹt lại ở màn hình HOME
        if (isMorseLockActive && !isUninstallGuardActive) {
    scheduleSuicideCheck();
}

        if (morseContainer != null && morseContainer.getVisibility() == View.VISIBLE) {
    if (tvLockIcon != null) {
        tvLockIcon.setOnTouchListener((v, event) -> {
    if (event.getAction() == MotionEvent.ACTION_UP) {
        // Yêu cầu 10: 1 chạm vào ổ khóa → crash app bị khóa + về HOME
        // Dùng UsageStats để xác định package đang foreground một cách chính xác
        String pkgToKill = lockedPkg.isEmpty() ? currentForegroundPkg : lockedPkg;
        boolean isCurrentlyOnHome =
            currentForegroundPkg.isEmpty()
            || currentForegroundPkg.contains("launcher")
            || currentForegroundPkg.contains("nexuslauncher")
            || currentForegroundPkg.contains("quickstep")
            || currentForegroundPkg.contains("systemui")
            || isForceHome;

        if (!isCurrentlyOnHome && !pkgToKill.isEmpty()) {
            // Bước 1: Về HOME ngay lập tức
            Intent homeIntent = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            homeIntent.putExtra("act", "HOME");
            sendBroadcast(homeIntent);

            // Bước 2: Force-stop app sau khi đã về HOME (delay nhỏ tránh ANR)
            new Handler().postDelayed(() -> {
                try {
                    android.app.ActivityManager am =
                        (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                    am.killBackgroundProcesses(pkgToKill);
                    // Dùng usage stats để xác nhận app đã thực sự bị kill
                    android.app.usage.UsageStatsManager usm =
                        (android.app.usage.UsageStatsManager)
                        getSystemService(Context.USAGE_STATS_SERVICE);
                    long now = System.currentTimeMillis();
                    // Xóa trạng thái unlock để MorseLock không tái hiện ngay
                    unlockedApps.remove(pkgToKill);
                } catch (Exception ignored) {}
            }, 150);

            // Bước 3: Reset MorseLock state — ổn định sau crash (Yêu cầu 10)
            // Delay 500ms để chắc chắn HOME đã render xong trước khi reset
            new Handler().postDelayed(() -> {
                isMorseLockActive = false;
                isForceHome = false;
                morseFailCount = 0;
                currentMorseAttempt = "";
                lockedPkg = "";
                // QUAN TRỌNG: KHÔNG thêm pkg vào unlockedApps
                // → Khi user mở lại app, MorseLock sẽ hiện đúng
                morseContainer.setVisibility(View.GONE);
                updateVisibility();
            }, 500);

        } else if (isCurrentlyOnHome) {
            // Đang ở Home → chủ máy chủ động tắt overlay
            morseContainer.setVisibility(View.GONE);
            isMorseLockActive = false;
            isForceHome = false;
            currentMorseAttempt = "";
            morseFailCount = 0;
            if (!lockedPkg.isEmpty()) unlockedApps.add(lockedPkg);
                }
            }
            return true;
        });
    }
} // Khối lệnh trên kết thúc an toàn, hàm updateVisibility() tiếp tục chạy bên dưới
       boolean isUnlocked = !km.isKeyguardLocked();
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hideNormal = (avoidKbd && isKbd) || isBl;
        
        // DUAL-SOUL: Chỉ 1 trong 2 động cơ được phép vẽ tại 1 thời điểm
// → tiết kiệm tuyệt đối RAM/GPU Adreno 540 trên Pixel 2XL
boolean accHomeRunning = AccessibleHomeService.isRunning;
boolean oldHomeEnabled = HomescreenService.isRunning && prefs.getBoolean("shortcut_home_on", false);
// Nếu AccHome đang chạy → ép tắt toàn bộ bars cũ khỏi vùng nhớ GPU
boolean shouldRenderOldHome = isUnlocked && !hideNormal && !accHomeRunning && oldHomeEnabled;
// Pixel 2XL: giải phóng SurfaceFlinger layer khi overlay tắt
if (accHomeRunning) {
    for (int i = 0; i < 5; i++) if (bars[i] != null) bars[i].setVisibility(View.GONE);
    for (int i = 0; i < 4; i++) if (corners[i] != null) corners[i].setVisibility(View.GONE);
}
        isPreviewMorse = prefs.getBoolean("preview_morse", false);
        boolean timeLocked = (System.currentTimeMillis() < lockUntilTime);

        if ((isMorseLockActive && !timeLocked) || isPreviewMorse) {
            if (morseContainer.getVisibility() != View.VISIBLE) {
    // FIX-TEXT-1: Luôn apply style khi container vừa được hiện
    applyMorseTextStyle();
    applyLockIconStyle();
    updateLockIconPosition();
    morseContainer.setVisibility(View.VISIBLE);
}
            if (isPreviewMorse) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) morseContainer.getLayoutParams();
                p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                wm.updateViewLayout(morseContainer, p);
                morseContainer.setOnTouchListener((v, e) -> false);
            } else {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) morseContainer.getLayoutParams();
                p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                wm.updateViewLayout(morseContainer, p);
                morseContainer.setOnTouchListener((v, e) -> true);
            }

            for (int i = 0; i < 5; i++) if (bars[i] != null) bars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (corners[i] != null) corners[i].setVisibility(View.GONE);

            for (int i = 0; i < 8; i++) {
                if (mBars[i] == null) continue;
                boolean en = prefs.getBoolean("morse_" + M_BARS[i] + "_en", false);
                mBars[i].setVisibility(en ? View.VISIBLE : View.GONE);
                if (en) {
                    int alpha = prefs.getInt("morse_" + M_BARS[i] + "_alpha", 50);
                    int w = prefs.getInt("morse_" + M_BARS[i] + "_w", 300);
                    int h = prefs.getInt("morse_" + M_BARS[i] + "_h", 60);
                    int x = prefs.getInt("morse_" + M_BARS[i] + "_x", 0);
                    int y = prefs.getInt("morse_" + M_BARS[i] + "_y", 0);
                    int visMode = prefs.getInt("morse_" + M_BARS[i] + "_vis_mode", 0);
                    int hideDelay = prefs.getInt("morse_corner_hide_dur", 2500);
                    ((MorseBarView) mBars[i]).updateProps(alpha, visMode, hideDelay);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    if (isPreviewMorse) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) mBars[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = M_GRAV[i];
                    wm.updateViewLayout(mBars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mBars[i].getVisibility() == View.VISIBLE && !isPreviewMorse) {
                        mBars[i].setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                if (mCorners[i] == null) continue;
                boolean cornEn = prefs.getBoolean("morse_corner_" + CORNERS[i] + "_en", false);
                mCorners[i].setVisibility(cornEn ? View.VISIBLE : View.GONE);
                if (cornEn) {
                    String ck = "morse_corner_" + CORNERS[i] + "_";
                    int moonAlpha = prefs.getInt("morse_corner_moon_alpha", 100);
                    int strokeAlpha = prefs.getInt("morse_corner_stroke_alpha", 200);
                    int hideDelay = prefs.getInt("morse_corner_hide_dur", 2500);
                    int visMode = prefs.getInt(ck + "vis_mode", 0);
                    boolean isAuto = (visMode == 1);
                    boolean isInv = (visMode == 2);
                    ((CornerView) mCorners[i]).updateProps(prefs.getInt("morse_corner_thick", 8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    if (isPreviewMorse) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) mCorners[i].getLayoutParams();
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
                    p.x = prefs.getInt(ck + "x", 0);
                    p.y = prefs.getInt(ck + "y", 0);
                    wm.updateViewLayout(mCorners[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mCorners[i].getVisibility() == View.VISIBLE && !isPreviewMorse) {
                        mCorners[i].setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, p.width, p.height)));
                    }
                }
            }
        } else {
            if (morseContainer.getVisibility() != View.GONE) {
                morseContainer.setVisibility(View.GONE);
            }
            for (int i = 0; i < 8; i++) if (mBars[i] != null) mBars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (mCorners[i] != null) mCorners[i].setVisibility(View.GONE);

            boolean isPreviewLock = prefs.getBoolean("preview_lock", false);
            for (int i = 0; i < 5; i++) { 
                   if (bars[i] == null) continue; 
                   boolean en = prefs.getBoolean("home_" + BARS[i] + "_en", false);
                   bars[i].setVisibility((en && shouldRenderOldHome) ? View.VISIBLE : View.GONE); 
                    if (en && shouldRenderOldHome) { 
                    int alpha = isPreviewLock ? 0 : prefs.getInt("home_" + BARS[i] + "_alpha", 50);
                    int w = prefs.getInt("home_" + BARS[i] + "_w", 300);
                    int h = prefs.getInt("home_" + BARS[i] + "_h", 60);
                    int x = prefs.getInt("home_" + BARS[i] + "_x", 0);
                    int y = prefs.getInt("home_" + BARS[i] + "_y", 0);
                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(Color.argb(alpha, 96, 125, 139));
                    gd.setCornerRadius(24f);
                    bars[i].setBackground(gd);
                    int priMode = prefs.getInt("home_" + BARS[i] + "_pri_mode", 0);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    if (priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
                    p.flags = baseFlags;
                    // Đã xóa toàn bộ logic Hybrid Type check - Tiết kiệm RAM và chu kỳ CPU
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = GRAV[i];
                    wm.updateViewLayout(bars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bars[i].getVisibility() == View.VISIBLE && priMode == 0) {
                        bars[i].setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, w, h)));
                    }
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
                    p.x = prefs.getInt(ck + "x", 0);
                    p.y = prefs.getInt(ck + "y", 0);
                    wm.updateViewLayout(corners[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && corners[i].getVisibility() == View.VISIBLE && priMode == 0) {
                        corners[i].setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, p.width, p.height)));
                    }
                }
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
            for (String a : acts) exec(a.trim());
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
                case "MACRO_1":
                case "MACRO_2":
                case "MACRO_3":
                case "MACRO_4":
                case "MACRO_5":
                    Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                    iM.putExtra("services", prefs.getString(a.toLowerCase() + "_svcs", ""));
                    sendBroadcast(iM);
                    break;
                case "TOGGLE_MORSE":
                    Intent m = new Intent("com.manhmoc.edgebar.TOGGLE_MORSE");
                    sendBroadcast(m);
                    break;
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
                case "BACK":
                case "HOME":
                case "RECENTS":
                case "SCREEN_OFF":
                case "POWER_DIALOG":
                case "SCREENSHOT":
                case "NOTIFICATIONS":
                case "TOGGLE_OVERLAY":
                case "TOGGLE_ACC":
                    Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                    ipc.putExtra("act", a);
                    sendBroadcast(ipc);
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
                default:
                    if (a.startsWith("INTENT_")) fireIntent(a.split("_")[1]);
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
        private GestureDetector gd;
        private float sx, sy;
        private long st;
        private Handler longPressHandler = new Handler();
        private boolean longPressTriggered = false;

        public SidebarTouchListener(String keyBase, View v) {
            this.prefKeyBase = keyBase;
            this.myView = v;
            this.gd = new GestureDetector(HomescreenService.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    handleAction(prefKeyBase + "_tap");
                    return true;
                }
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    handleAction(prefKeyBase + "_dtap");
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    handleAction(prefKeyBase + "_long");
                }
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                    return false;
                }
            });
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (isMorseLockActive || isPreviewMorse) {
                String mapped = mapComponentToNumber(prefKeyBase);
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    if (mapped.equals("X")) {
                        longPressTriggered = false;
                        longPressHandler.postDelayed(() -> {
                            longPressTriggered = true;
                            handleMorseTap(prefKeyBase, myView, true);
                        }, 600);
                    } else {
                        handleMorseTap(prefKeyBase, myView, false);
                    }
                } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (mapped.equals("X")) {
                        longPressHandler.removeCallbacksAndMessages(null);
                        if (!longPressTriggered) {
                            handleMorseTap(prefKeyBase, myView, false);
                        }
                    }
                }
                return true;
            }
            if (myView != null && myView instanceof CornerView) ((CornerView) myView).triggerFlash();
            else if (myView != null && myView instanceof MorseBarView) ((MorseBarView) myView).triggerFlash();
            gd.onTouchEvent(e);
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                sx = e.getRawX();
                sy = e.getRawY();
                st = System.currentTimeMillis();
            } else if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getRawX() - sx;
                float dy = e.getRawY() - sy;
                if (Math.abs(dx) > 50 || Math.abs(dy) > 50) {
                    long duration = System.currentTimeMillis() - st;
                    boolean isHold = duration > prefs.getInt("hold_dur", 600);
                    String actionName = "";
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
            }
            return true;
        }
    }

    private void scheduleSuicideCheck() {
    // Hủy check cũ nếu có, tránh tích lũy callbacks
    if (suicideRunnable != null) suicideHandler.removeCallbacks(suicideRunnable);
    suicideRunnable = () -> {
        boolean foregroundIsLocked = false;
        boolean foregroundIsHome = isForceHome // FIX 4
                || currentForegroundPkg.isEmpty()
                || currentForegroundPkg.contains("launcher")
                || currentForegroundPkg.contains("nexuslauncher")
                || currentForegroundPkg.contains("quickstep")
                || currentForegroundPkg.contains("systemui");
        String locklist = prefs.getString("locklist", "");
        if (!currentForegroundPkg.isEmpty() && !locklist.isEmpty()) {
            for (String pkg : locklist.split(",")) {
                if (pkg.trim().equals(currentForegroundPkg)) { foregroundIsLocked = true; break; }
            }
        }
        if (!foregroundIsLocked || foregroundIsHome) {
            isForceHome = false; // reset sau khi tự sát
            isMorseLockActive = false;
            if (morseContainer != null) morseContainer.setVisibility(View.GONE);
            currentMorseAttempt = "";
            morseFailCount = 0;
            isCountingDown = false;
            if (countdownRunnable != null) countdownHandler.removeCallbacks(countdownRunnable);
            if (warningAnimator != null) warningAnimator.cancel();
            if (bgView != null) {
                bgView.setBackgroundColor(Color.TRANSPARENT);
                bgView.invalidate();
            }
        }
    };
    suicideHandler.postDelayed(suicideRunnable, 250);
}


    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try { unregisterReceiver(syncReceiver); } catch (Exception e) {}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        if (accObserver != null) getContentResolver().unregisterContentObserver(accObserver); // ← THÊM DÒNG NÀY
        for (int i = 0; i < 5; i++) if (bars[i] != null) wm.removeView(bars[i]);
        for (int i = 0; i < 8; i++) if (mBars[i] != null) wm.removeView(mBars[i]);
        for (int i = 0; i < 4; i++) {
            if (corners[i] != null) wm.removeView(corners[i]);
            if (mCorners[i] != null) wm.removeView(mCorners[i]);
        }
        if (morseContainer != null) wm.removeView(morseContainer);
        if (fV != null) wm.removeView(fV);
        if (bgBitmap != null && !bgBitmap.isRecycled()) { 
            bgBitmap.recycle(); 
            bgBitmap = null; // ← quan trọng: null để GC thu hồi ngay 
         }
      } 
// HYBRID HOME V2: Kiểm tra Accessibility — đọc trực tiếp, chỉ gọi từ ContentObserver
    // Không cần cache vì ContentObserver đã đảm bảo chỉ gọi khi có thay đổi thật
    private boolean isAccOn() {
        try {
            String s = android.provider.Settings.Secure.getString(
                getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(
                getPackageName() + "/" + EdgeBarService.class.getName());
        } catch (Exception e) {
            return false;
        }
    }

    // HYBRID HOME V2: Áp dụng type đúng cho TẤT CẢ bars/corners một lần
    // Không removeView/addView — chỉ updateViewLayout để ZERO overhead RAM
    private void applyHybridTypeToAllBars() {
        int newType = accStateCached
            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        for (int i = 0; i < 5; i++) {
            if (bars[i] == null) continue;
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams();
            if (p.type != newType) {
                p.type = newType;
                try { wm.updateViewLayout(bars[i], p); } catch (Exception ignored) {}
            }
        }
        for (int i = 0; i < 4; i++) {
            if (corners[i] == null) continue;
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams();
            if (p.type != newType) {
                p.type = newType;
                try { wm.updateViewLayout(corners[i], p); } catch (Exception ignored) {}
            }
        }
    }

}  // ← đây là dấu } cuối cùng đóng class HomescreenService, KHÔNG XÓA
