package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService; import android.animation.ValueAnimator; import android.animation.AnimatorListenerAdapter; import android.animation.Animator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path; import android.graphics.PixelFormat; import android.graphics.LinearGradient; import android.graphics.Shader; import android.graphics.DashPathEffect; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.Handler; import android.os.VibrationEffect; import android.os.Vibrator; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;
public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View[] bars = new View[5]; private View[] corners = new View[4]; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private KeyguardManager km; private SharedPreferences prefs; private Vibrator vibrator;
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"}; private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};
    private String currentPackage = ""; private String unlockedPackage = "";
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null) fV.updateStyle(); } };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { 
        @Override public void onReceive(Context c, Intent i) { 
            if("com.manhmoc.edgebar.TEST_ANIM".equals(i.getAction())) playAnim(); 
            else if ("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS".equals(i.getAction())) unlockedPackage = i.getStringExtra("pkg");
            else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) unlockedPackage = "";
            else updateVisibility(); 
        } 
    };
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) exec(i.getStringExtra("act")); } };
    private class FlashView extends View { private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phaseFraction = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { p.setAlpha(prefs.getInt("anim_alpha", 255)); p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        private void applyGradient(int w, int h) { int[] cArr; switch(cTheme) { 
            case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FF00FF")}; break; 
            case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700"), Color.parseColor("#8A2BE2")}; break; 
            default: cArr=new int[]{Color.WHITE, Color.WHITE}; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR)); 
        }
        public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { 
            float drawW = getWidth(), drawH = getHeight(); if(drawW <= 0 || drawH <= 0) return;
            float off = p.getStrokeWidth()/2; 
            float left = off, top = off, right = drawW - off, bottom = drawH - off;
            if(aStyle > 0) { float perim = 2 * (drawW + drawH); float currentPhase = -perim * phaseFraction; 
                if (aStyle == 1) p.setPathEffect(new DashPathEffect(new float[]{perim/4f, 3*perim/4f}, currentPhase)); 
                else if (aStyle == 2) p.setPathEffect(new DashPathEffect(new float[]{perim/8f, 3*perim/8f}, currentPhase)); 
                else if (aStyle == 3) p.setPathEffect(new DashPathEffect(new float[]{perim/12f, 3*perim/12f}, currentPhase)); 
            } else p.setPathEffect(null);
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, p); 
        } 
    }
    private class CornerView extends View { private Paint pFill, pStroke; private int type; private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay; private boolean isInv = false;
        public CornerView(Context c, int type) { super(c); this.type = type; pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); } 
        public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) { pStroke.setStrokeWidth(thick); this.baseMoonAlpha = moonAlpha; this.baseStrokeAlpha = strokeAlpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv; if(!autoHide) { pFill.setColor(Color.argb(moonAlpha, 96, 125, 139)); pStroke.setAlpha(strokeAlpha); } else triggerFlash(); if(inv) { pFill.setAlpha(0); pStroke.setAlpha(0); } invalidate(); }
        public void triggerFlash() { if(!isAutoHiding || isInv) return; autoHideHandler.removeCallbacksAndMessages(null); pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha + 50), 96, 125, 139)); pStroke.setAlpha(Math.min(255, baseStrokeAlpha + 50)); invalidate(); autoHideHandler.postDelayed(() -> { ValueAnimator a = ValueAnimator.ofFloat(1f, 0f); a.setDuration(1500); a.addUpdateListener(anim -> { float val = (float)anim.getAnimatedValue(); pFill.setColor(Color.argb((int)(baseMoonAlpha * val), 96, 125, 139)); pStroke.setAlpha((int)(baseStrokeAlpha * val)); invalidate(); }); a.start(); }, hideDelay); }
        @Override protected void onDraw(Canvas canvas) { 
            float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(); float pad = thick/2;
            String ck = "lock_corner_" + CORNERS[type] + "_";
            int shapeMode = prefs.getInt(ck+"shape", 0);
            float sRad = prefs.getInt(ck+"rad", 80) / 1000f, mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
            float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
            float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);
            Path moonPath = new Path(), strokePath = new Path();
            float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
            float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;
            if(type==0) { sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f); }
            else if(type==1) { sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f); }
            else if(type==2) { sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad; sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh; mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f); }
            else if(type==3) { sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad; sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                mRootX=0; mRootY=0; mTipX=mw; mTipY=mh; mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f); }
            if(shapeMode == 1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
            else if(shapeMode == 2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
            else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }
            if(type==0||type==1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
            else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
            moonPath.close();
            canvas.drawPath(strokePath, pStroke); 
            float mx = prefs.getInt(ck+"moon_x", 1250) - 1250, my = prefs.getInt(ck+"moon_y", 1250) - 1250;
            canvas.save(); canvas.translate(mx, my); canvas.drawPath(moonPath, pFill); canvas.restore();
        } 
    }
    @Override protected void onServiceConnected() { /* ... khởi tạo ... */ wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {} prefs.registerOnSharedPreferenceChangeListener(prefListener); IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction("com.manhmoc.edgebar.TEST_ANIM"); filter.addAction("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS"); registerReceiver(stateReceiver, filter); if(Build.VERSION.SDK_INT >= 33) registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION")); String cid = "eb_19_acc"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n); createFloatingBars(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* xử lý locklist */ }
    private void exec(String a) { /* gọi action */ }
    private void fireIntent(String idx) { /* intent */ }
    private void playAnim() { /* animation */ }
    private void handleAction(String key) { /* handle */ }
    private void doVibrate(int dur) { if(dur>0 && vibrator!=null) { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } }
    private void createFloatingBars() { fV = new FlashView(this); fV.setAlpha(0f); fV.setVisibility(View.GONE); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(0, 0, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT); wm.addView(fV, fp); for(int i=0;i<5;i++) { bars[i]=new View(this); wm.addView(bars[i], new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT)); bars[i].setOnTouchListener(new SidebarTouchListener("lock_"+BARS[i],null)); } for(int i=0;i<4;i++) { corners[i]=new CornerView(this,i); wm.addView(corners[i], new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT)); corners[i].setOnTouchListener(new SidebarTouchListener("lock_corner_"+CORNERS[i],corners[i])); } updateVisibility(); }
    private void updateVisibility() { /* hiển thị bar */ }
    private class SidebarTouchListener implements View.OnTouchListener { /* ... */ }
    @Override public void onInterrupt() {} @Override public void onDestroy() { /* dọn dẹp */ }
}
