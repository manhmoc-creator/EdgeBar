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

import java.util.Collections;
import java.util.Random;

public class HomescreenService extends Service {
    public static boolean isRunning = false;
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private RelativeLayout morseContainer;
    private TextView tvMorseStatus;
    private View scratchView;
    private View[] mBars = new View[8];
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
    private Handler morseDotHandler = new Handler();

    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL};

    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private final int[] M_GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.CENTER};

    private final String[] CORNERS = {"br", "bl", "tr", "tl"};
    private final int[] C_GRAV = {Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT, Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> {
        if (k != null) {
            updateVisibility();
            if (fV != null) fV.updateStyle();
        }
    };

    private BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getAction().equals("com.manhmoc.edgebar.SYNC_STATE")) {
                isKbd = i.getBooleanExtra("isKbd", false);
                isBl = i.getBooleanExtra("isBl", false);
                updateVisibility();
            } else if (i.getAction().equals("com.manhmoc.edgebar.TEST_ANIM")) {
                playAnim();
            } else if (i.getAction().equals("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE")) {
                isMorseLockActive = true;
                lockedPkg = i.getStringExtra("pkg");
                morseFailCount = 0;
                currentMorseAttempt = "";
                tvMorseStatus.setText("");
                updateVisibility();
            } else if (i.getAction().equals("com.manhmoc.edgebar.TOGGLE_MORSE")) {
                boolean isM = prefs.getBoolean("morse_mode_en", false);
                prefs.edit().putBoolean("morse_mode_en", !isM).apply();
                updateVisibility();
            }
        }
    };

    private String mapComponentToNumber(String comp) {
        String key = "morse_map_" + comp.replace("morse_", "").replace("home_", "").replace("lock_", "").replace("corner_", "");
        return prefs.getString(key, "*");
    }

    private class ScratchView extends View {
        private Paint paint = new Paint();
        private Random random = new Random();
        private int[] crackPoints;

        public ScratchView(Context context) {
            super(context);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(6);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            generateCracks();
        }

        private void generateCracks() {
            crackPoints = new int[20];
            for (int i = 0; i < 20; i += 2) {
                crackPoints[i] = random.nextInt(2000);
                crackPoints[i + 1] = random.nextInt(3000);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (crackPoints == null) generateCracks();
            paint.setAlpha(80);
            for (int i = 0; i < crackPoints.length - 2; i += 2) {
                canvas.drawLine(crackPoints[i], crackPoints[i + 1], crackPoints[i + 2], crackPoints[i + 3], paint);
            }
            paint.setStrokeWidth(3);
            paint.setAlpha(120);
            for (int i = 0; i < 15; i++) {
                int x1 = random.nextInt(getWidth());
                int y1 = random.nextInt(getHeight());
                int x2 = x1 + random.nextInt(200) - 100;
                int y2 = y1 + random.nextInt(200) - 100;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
            invalidate();
        }
    }

    private class FlashView extends View {
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phaseFraction = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); }
        public void updateStyle() { p.setAlpha(prefs.getInt("anim_alpha", 255)); p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) {
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
            } else if(type==3) { // TL
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
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction("com.manhmoc.edgebar.SYNC_STATE");
        filter.addAction("com.manhmoc.edgebar.TEST_ANIM");
        filter.addAction("com.manhmoc.edgebar.IPC_ACTION");
        filter.addAction("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
        filter.addAction("com.manhmoc.edgebar.TOGGLE_MORSE");
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(syncReceiver, filter);

        String cid = "eb_19_home";
        NotificationChannel c = new NotificationChannel(cid, "Edge Bar Màn Chính", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Edge Bar Màn Chính")
                .setSmallIcon(R.drawable.ic_launcher_fg)
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

        // Home layers
        for (int i = 0; i < 5; i++) {
            bars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], p); } catch (Exception e) {}
            bars[i].setOnTouchListener(new SidebarTouchListener("home_" + BARS[i], null));
        }
        for (int i = 0; i < 4; i++) {
            corners[i] = new CornerView(this, i, "home_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(corners[i], p); } catch (Exception e) {}
            corners[i].setOnTouchListener(new SidebarTouchListener("home_corner_" + CORNERS[i], corners[i]));
        }

        // Morse container
        morseContainer = new RelativeLayout(this);
        morseContainer.setBackgroundColor(Color.BLACK);
        morseContainer.setVisibility(View.GONE);
        morseContainer.setOnTouchListener((v, e) -> {
            boolean isPreview = prefs.getBoolean("preview_morse", false);
            return !isPreview;
        });

        tvMorseStatus = new TextView(this);
        tvMorseStatus.setTextColor(Color.WHITE);
        tvMorseStatus.setTextSize(30);
        tvMorseStatus.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(-1, -2);
        tLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        morseContainer.addView(tvMorseStatus, tLp);

        scratchView = new ScratchView(this);
        morseContainer.addView(scratchView, new RelativeLayout.LayoutParams(-1, -1));

        WindowManager.LayoutParams bgP = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { wm.addView(morseContainer, bgP); } catch (Exception e) {}

        // Morse bars & corners
        for (int i = 0; i < 8; i++) {
            mBars[i] = new View(this);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mBars[i], p); } catch (Exception e) {}
            mBars[i].setOnTouchListener(new SidebarTouchListener("morse_" + M_BARS[i], null));
        }
        for (int i = 0; i < 4; i++) {
            mCorners[i] = new CornerView(this, i, "morse_");
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT);
            try { wm.addView(mCorners[i], p); } catch (Exception e) {}
            mCorners[i].setOnTouchListener(new SidebarTouchListener("morse_corner_" + CORNERS[i], mCorners[i]));
        }

        updateVisibility();
        sendSyncState();
    }

    private void handleMorseTap(String comp, View v) {
        doVibrate(30);
        if (v != null && v instanceof CornerView) ((CornerView) v).triggerFlash();

        String mappedKey = mapComponentToNumber(comp);

        if (mappedKey.equals("X")) {
            currentMorseAttempt = "";
            tvMorseStatus.setText("");
        } else if (mappedKey.equals(">")) {
            String masterPass = prefs.getString("morse_master_pass", "");
            if (currentMorseAttempt.isEmpty() || masterPass.isEmpty()) return;
            String cleanMaster = masterPass.replace(">", "");
            if (currentMorseAttempt.equals(cleanMaster)) {
                isMorseLockActive = false;
                morseFailCount = 0;
                Intent i = new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
                i.putExtra("pkg", lockedPkg);
                sendBroadcast(i);
                updateVisibility();
            } else {
                morseFailCount++;
                int failVib = prefs.getInt("morse_fail_vib", 500);
                doVibrate(failVib);
                if (morseFailCount == 1)
                    tvMorseStatus.setText(prefs.getString("morse_insult_1", "Who are u?"));
                else if (morseFailCount == 2)
                    tvMorseStatus.setText(prefs.getString("morse_insult_2", "What are u doing?"));
                else {
                    tvMorseStatus.setText(prefs.getString("morse_insult_3", "Get out!"));
                    Intent kick = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                    kick.putExtra("act", "HOME");
                    sendBroadcast(kick);
                    isMorseLockActive = false;
                    new Handler().postDelayed(() -> updateVisibility(), 500);
                }
                currentMorseAttempt = "";
            }
        } else {
            currentMorseAttempt += mappedKey;
            tvMorseStatus.setText(currentMorseAttempt);
            morseDotHandler.removeCallbacksAndMessages(null);
            int dotDelay = prefs.getInt("morse_dot_delay", 500);
            morseDotHandler.postDelayed(() -> {
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < currentMorseAttempt.length(); i++) dots.append("• ");
                tvMorseStatus.setText(dots.toString());
            }, dotDelay);
        }
    }

    private void updateVisibility() {
        boolean isUnlocked = !km.isKeyguardLocked();
        boolean avoidKbd = prefs.getBoolean("avoid_kbd", true);
        boolean hideNormal = (avoidKbd && isKbd) || isBl;
        boolean isPreviewMorse = prefs.getBoolean("preview_morse", false);

        if (hideNormal && fV != null && !isMorseLockActive && !isPreviewMorse) fV.setVisibility(View.GONE);

        if (isMorseLockActive || isPreviewMorse) {
            morseContainer.setVisibility(View.VISIBLE);
            morseContainer.setAlpha(prefs.getInt("morse_bg_alpha", 180) / 255f);

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
                    GradientDrawable gd = new GradientDrawable();
                    gd.setColor(Color.argb(alpha, 96, 125, 139));
                    gd.setCornerRadius(24f);
                    mBars[i].setBackground(gd);
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                    WindowManager.LayoutParams p = (WindowManager.LayoutParams) mBars[i].getLayoutParams();
                    p.flags = baseFlags;
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = M_GRAV[i];
                    wm.updateViewLayout(mBars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mBars[i].getVisibility() == View.VISIBLE) {
                        Rect rect = new Rect(0, 0, w, h);
                        mBars[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
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
                    int baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mCorners[i].getVisibility() == View.VISIBLE) {
                        Rect rect = new Rect(0, 0, p.width, p.height);
                        mCorners[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
        } else {
            morseContainer.setVisibility(View.GONE);
            for (int i = 0; i < 8; i++) if (mBars[i] != null) mBars[i].setVisibility(View.GONE);
            for (int i = 0; i < 4; i++) if (mCorners[i] != null) mCorners[i].setVisibility(View.GONE);

            boolean isPreviewLock = prefs.getBoolean("preview_lock", false);
            for (int i = 0; i < 5; i++) {
                if (bars[i] == null) continue;
                boolean en = prefs.getBoolean("home_" + BARS[i] + "_en", false);
                bars[i].setVisibility((en && isUnlocked && !hideNormal) ? View.VISIBLE : View.GONE);
                if (en && isUnlocked) {
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
                    p.width = w;
                    p.height = h;
                    p.x = x;
                    p.y = y;
                    p.gravity = GRAV[i];
                    wm.updateViewLayout(bars[i], p);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && bars[i].getVisibility() == View.VISIBLE && priMode == 0) {
                        Rect rect = new Rect(0, 0, w, h);
                        bars[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                if (corners[i] == null) continue;
                boolean cornEn = prefs.getBoolean("home_corner_" + CORNERS[i] + "_en", false);
                corners[i].setVisibility((cornEn && isUnlocked && !hideNormal) ? View.VISIBLE : View.GONE);
                if (cornEn && isUnlocked) {
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
                        Rect rect = new Rect(0, 0, p.width, p.height);
                        corners[i].setSystemGestureExclusionRects(Collections.singletonList(rect));
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
                                y.setPackage("com.deniscerri.ytdlnis");
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
            if (isMorseLockActive || prefs.getBoolean("preview_morse", false)) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) handleMorseTap(prefKeyBase, myView);
                return true;
            }
            if (myView != null && myView instanceof CornerView) ((CornerView) myView).triggerFlash();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try { unregisterReceiver(syncReceiver); } catch (Exception e) {}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        for (int i = 0; i < 5; i++) if (bars[i] != null) wm.removeView(bars[i]);
        for (int i = 0; i < 8; i++) if (mBars[i] != null) wm.removeView(mBars[i]);
        for (int i = 0; i < 4; i++) {
            if (corners[i] != null) wm.removeView(corners[i]);
            if (mCorners[i] != null) wm.removeView(mCorners[i]);
        }
        if (morseContainer != null) wm.removeView(morseContainer);
        if (fV != null) wm.removeView(fV);
    }
}
