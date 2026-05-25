package com.manhmoc.edgebar;
import android.animation.*; import android.app.*; import android.content.*; import android.graphics.*; import android.graphics.drawable.GradientDrawable; import android.os.*; import android.view.*;

public class HomescreenService extends Service {
    public static boolean isRunning = false; private WindowManager wm; private FlashView fV; 
    private boolean isKbd = false, isBl = false; private SharedPreferences prefs; private KeyguardManager km; 
    private View[] hBars = new View[5]; private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; 
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null && k.startsWith("anim_")) fV.updateStyle(); } };
    private BroadcastReceiver syncReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if(i.getAction().equals("com.manhmoc.edgebar.SYNC_STATE")) { isKbd = i.getBooleanExtra("isKbd", false); isBl = i.getBooleanExtra("isBl", false); } updateVisibility(); } };
    private BroadcastReceiver animReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if(fV != null && prefs.getBoolean("preview_spy", false) == false) fV.playAnim(prefs.getInt("anim_style",0), prefs.getInt("anim_dur",1500)); } };

    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phase = 0f; private ValueAnimator anim;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { 
            p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); 
            if (getWidth() > 0 && getHeight() > 0) applyGradient(getWidth(), getHeight()); invalidate(); 
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { /* Giữ nguyên hàm applyGradient() */ }
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

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { isRunning = true; return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate(); isRunning = true; wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        int regFlags = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0;
        registerReceiver(syncReceiver, new IntentFilter("com.manhmoc.edgebar.SYNC_STATE"), regFlags);
        registerReceiver(animReceiver, new IntentFilter("com.manhmoc.edgebar.TEST_ANIM"), regFlags);

        fV = new FlashView(this); fV.setAlpha(0f); 
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); 
        wm.addView(fV, fp);
        
        for(int i=0; i<5; i++) { 
            hBars[i] = new View(this); 
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT); 
            wm.addView(hBars[i], lp); 
        }
        updateVisibility();
    }

    private void updateVisibility() { 
        boolean pLock = prefs.getBoolean("preview_lock", false); boolean pMorse = prefs.getBoolean("preview_morse", false); boolean pHome = prefs.getBoolean("preview_home", false); boolean pSpy = prefs.getBoolean("preview_spy", false);
        boolean isUnlocked = !km.isKeyguardLocked(); boolean hide = (prefs.getBoolean("avoid_kbd", true) && isKbd) || isBl; 
        
        if (pLock || pMorse || pSpy) { hideAll(); return; } // Cô lập hoàn toàn khi đang Preview Lock/Morse/Spy
        
        boolean showHome = pHome || (isUnlocked && !hide);
        for(int i=0; i<5; i++) { 
            boolean en = prefs.getBoolean("home_"+BARS[i]+"_en", false); hBars[i].setVisibility((en && showHome) ? View.VISIBLE : View.GONE);
            if(en && showHome) {
                GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(prefs.getInt("home_"+BARS[i]+"_alpha", 50), 96, 125, 139)); gd.setCornerRadius(24f); hBars[i].setBackground(gd);
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) hBars[i].getLayoutParams(); p.width = prefs.getInt("home_"+BARS[i]+"_w", 300); p.height = prefs.getInt("home_"+BARS[i]+"_h", 60); p.x = prefs.getInt("home_"+BARS[i]+"_x", 0); p.y = prefs.getInt("home_"+BARS[i]+"_y", 0); p.gravity = GRAV[i];
                if(prefs.getInt("home_"+BARS[i]+"_pri_mode", 0) == 1) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                wm.updateViewLayout(hBars[i], p);
            }
        } 
    }

    private void hideAll() { for(View v : hBars) v.setVisibility(View.GONE); }
    @Override public void onDestroy() { super.onDestroy(); isRunning = false; try{unregisterReceiver(syncReceiver); unregisterReceiver(animReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(View v : hBars) wm.removeView(v); if(fV!=null) wm.removeView(fV); }
}
