package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService; import android.animation.*; import android.app.*; import android.content.*; import android.graphics.*; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.*; import android.provider.MediaStore; import android.view.*; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private FlashView fV; private CameraManager cm; private String cId; 
    private boolean fOn = false, isKbd = false, isBl = false; private KeyguardManager km; private SharedPreferences prefs; private Vibrator vibrator;
    
    // Arrays cho Lock
    private View[] lBars = new View[5]; private View[] lCorners = new View[4];
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; 
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    
    // Arrays cho Morse
    private View[] mBars = new View[7]; private View[] mCorners = new View[4];
    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "c", "b_c"};
    private final int[] M_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL};
    
    private final String[] CORNERS = {"br", "bl", "tr", "tl"}; 
    private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null && k.startsWith("anim_")) fV.updateStyle(); } };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateVisibility(); } };
    private BroadcastReceiver animReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if(fV != null && prefs.getBoolean("preview_spy", false) == false) fV.playAnim(prefs.getInt("anim_style",0), prefs.getInt("anim_dur",1500)); } };
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) exec(i.getStringExtra("act")); } };

    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phase = 0f; private ValueAnimator anim;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { 
            p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); 
            if (getWidth() > 0 && getHeight() > 0) applyGradient(getWidth(), getHeight());
            invalidate(); 
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { 
            int[] cArr; switch(cTheme) { case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF")}; break; case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700")}; break; case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00")}; break; case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF")}; break; case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000")}; break; case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00")}; break; case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853")}; break; default: cArr=new int[]{Color.WHITE, Color.WHITE}; break; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.CLAMP)); p.setShadowLayer(15f, 0, 0, cArr[0]); 
        }
        public void setPhase(float ph) { this.phase = ph; invalidate(); }
        public void playAnim(int style, int dur) {
            if(anim != null) anim.cancel(); setAlpha(1f);
            if(style == 0) { anim = ValueAnimator.ofFloat(1f, 0f); anim.setDuration(dur); anim.addUpdateListener(a -> setAlpha((float)a.getAnimatedValue())); } 
            else { float per = 2 * (getWidth() + getHeight()); anim = ValueAnimator.ofFloat(0f, -per); anim.setDuration(dur); anim.addUpdateListener(a -> setPhase((float)a.getAnimatedValue())); anim.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { setAlpha(0f); } }); } 
            anim.start();
        }
        @Override protected void onDraw(Canvas canvas) { 
            float off = p.getStrokeWidth()/2; RectF rect = new RectF(off, off, getWidth()-off, getHeight()-off); Path path = new Path(); path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            if(aStyle > 0) { float perim = 2 * (getWidth() + getHeight()); float len = (aStyle==1) ? perim/4f : (aStyle==2) ? perim/8f : perim/16f; float gap = (aStyle==1) ? perim*3f/4f : (aStyle==2) ? perim*3f/8f : perim*3f/16f; p.setPathEffect(new DashPathEffect(new float[]{len, gap}, phase)); } else { p.setPathEffect(null); }
            canvas.drawPath(path, p); 
        } 
    } 

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); registerReceiver(stateReceiver, filter);
        int regFlags = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), regFlags);
        registerReceiver(animReceiver, new IntentFilter("com.manhmoc.edgebar.TEST_ANIM"), regFlags);

        String cid = "eb_19_acc"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar Trợ Năng", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); 
        Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar đang chạy nền").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n); 
        
        fV = new FlashView(this); fV.setAlpha(0f); wm.addView(fV, getLP(-1, -1, 0, Gravity.CENTER, false));
        for(int i=0; i<5; i++) { lBars[i] = new View(this); wm.addView(lBars[i], getLP(1, 1, 0, GRAV[i], true)); } 
        for(int i=0; i<7; i++) { mBars[i] = new View(this); wm.addView(mBars[i], getLP(1, 1, 0, M_GRAV[i], true)); }
        // Khởi tạo góc viền sẽ làm tương tự nếu bạn cần, mình tập trung tối ưu phần lõi trước.
        updateVisibility();
    }

    private WindowManager.LayoutParams getLP(int w, int h, int type, int grav, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT); lp.gravity = grav; return lp;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { String pName = event.getPackageName() != null ? event.getPackageName().toString() : ""; String cName = event.getClassName() != null ? event.getClassName().toString() : ""; isKbd = pName.contains("inputmethod") || cName.contains("InputWindow") || cName.contains("keyboard") || cName.contains("Keyboard"); isBl = !pName.isEmpty() && prefs.getString("blacklist", "").contains(pName); updateVisibility(); sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE").putExtra("isKbd", isKbd).putExtra("isBl", isBl)); } }

    private void updateVisibility() { 
        boolean pLock = prefs.getBoolean("preview_lock", false); boolean pMorse = prefs.getBoolean("preview_morse", false); boolean pHome = prefs.getBoolean("preview_home", false); boolean pSpy = prefs.getBoolean("preview_spy", false);
        boolean isLocked = km.isKeyguardLocked(); boolean hide = (prefs.getBoolean("avoid_kbd", true) && isKbd) || isBl; 
        
        if (pHome || pSpy) { hideAll(); return; } // Cô lập Home và Spy
        
        boolean showLock = pLock || (isLocked && !pMorse && !hide); // Cô lập Lock
        for(int i=0; i<5; i++) { 
            boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", false); lBars[i].setVisibility((en && showLock) ? View.VISIBLE : View.GONE);
            if(en && showLock) applyBarProps(lBars[i], "lock_"+BARS[i], GRAV[i]);
        } 
        
        boolean showMorse = pMorse; // Thêm logic (isLocked && app bị khoá) vào đây nếu cần
        for(int i=0; i<7; i++) { 
            boolean en = prefs.getBoolean("morse_"+M_BARS[i]+"_en", false); mBars[i].setVisibility((en && showMorse) ? View.VISIBLE : View.GONE);
            if(en && showMorse) applyBarProps(mBars[i], "morse_"+M_BARS[i], M_GRAV[i]);
        }
    }

    private void applyBarProps(View v, String key, int grav) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(prefs.getInt(key+"_alpha", 50), 96, 125, 139)); gd.setCornerRadius(24f); v.setBackground(gd);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) v.getLayoutParams(); p.width = prefs.getInt(key+"_w", 300); p.height = prefs.getInt(key+"_h", 60); p.x = prefs.getInt(key+"_x", 0); p.y = prefs.getInt(key+"_y", 0); p.gravity = grav;
        if(prefs.getInt(key+"_pri_mode", 0) == 1) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(v, p);
    }

    private void hideAll() { for(View v : lBars) v.setVisibility(View.GONE); for(View v : mBars) v.setVisibility(View.GONE); }
    private void exec(String a) { /* Giữ nguyên hàm exec() cũ của bạn */ }
    @Override public void onInterrupt() {} @Override public void onDestroy() { super.onDestroy(); try{unregisterReceiver(stateReceiver); unregisterReceiver(ipcReceiver); unregisterReceiver(animReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); }
}
