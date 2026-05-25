package com.manhmoc.edgebar;
import android.animation.ValueAnimator; import android.animation.AnimatorListenerAdapter; import android.animation.Animator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.*; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.VibrationEffect; import android.os.Vibrator; import android.os.IBinder; import android.provider.MediaStore; import android.provider.Settings; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager;

public class HomescreenService extends Service {
    public static boolean isRunning = false; 
    private WindowManager wm; private View[] bars = new View[5]; private View[] corners = new View[4]; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private SharedPreferences prefs; private KeyguardManager km; private Vibrator vibrator;
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"}; private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null && k.startsWith("anim_")) fV.updateStyle(); } };
    
    private CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() { @Override public void onTorchModeChanged(String camId, boolean enabled) { if(camId.equals(cId)) fOn = enabled; } };
    private BroadcastReceiver syncReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.SYNC_STATE".equals(i.getAction())) { isKbd = i.getBooleanExtra("isKbd", false); isBl = i.getBooleanExtra("isBl", false); } updateVisibility(); }};
    private BroadcastReceiver animReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { 
        if(fV != null && fV.getVisibility() == View.VISIBLE && !prefs.getBoolean("preview_spy", false)) {
            fV.updateStyle(); int style = prefs.getInt("anim_style", 0); int dur = prefs.getInt("anim_dur", 1500);
            if(style == 0) { fV.setAlpha(0f); ValueAnimator anim = ValueAnimator.ofFloat(0f, 0.8f, 0f); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue())); anim.start(); } 
            else { fV.setAlpha(1f); float p = 2 * (fV.getWidth() + fV.getHeight()); ValueAnimator anim = ValueAnimator.ofFloat(0f, -p); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue())); anim.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { fV.setAlpha(0f); } }); anim.start(); }
        }
    }};

    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phase = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); p.setShadowLayer(15f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { 
            p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); 
            if(getWidth() > 0 && getHeight() > 0) applyGradient(getWidth(), getHeight()); invalidate(); 
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { 
            int[] cArr; switch(cTheme) { case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF")}; break; case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700")}; break; case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00")}; break; case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF")}; break; case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000")}; break; case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00")}; break; case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853")}; break; default: cArr=new int[]{Color.WHITE, Color.WHITE}; break; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.CLAMP)); p.setShadowLayer(15f, 0, 0, cArr[0]); 
        }
        public void setPhase(float ph) { this.phase = ph; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { 
            float off = p.getStrokeWidth()/2; RectF rect = new RectF(off, off, getWidth()-off, getHeight()-off); Path path = new Path(); path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            if(aStyle > 0) { float perim = 2 * (getWidth() + getHeight()); float len = (aStyle==1) ? perim/4f : (aStyle==2) ? perim/8f : perim/16f; float gap = (aStyle==1) ? perim*3f/4f : (aStyle==2) ? perim*3f/8f : perim*3f/16f; p.setPathEffect(new DashPathEffect(new float[]{len, gap}, phase)); } else { p.setPathEffect(null); }
            canvas.drawPath(path, p); 
        } 
    }
    
    // PHỤC DỰNG LÕI TRĂNG NON (CRESCENT MOON)
    private class CornerView extends View { 
        private Paint pShell = new Paint(), pMoon = new Paint(); private int type; private String pfx; 
        public CornerView(Context c, int type, String prefix) { super(c); this.type = type; this.pfx = prefix; 
            pShell.setStyle(Paint.Style.STROKE); pShell.setAntiAlias(true); pShell.setStrokeCap(Paint.Cap.ROUND); 
            pMoon.setStyle(Paint.Style.STROKE); pMoon.setAntiAlias(true); pMoon.setStrokeCap(Paint.Cap.ROUND); 
        } 
        public void updateProps(int thick, int alpha) { 
            pShell.setStrokeWidth(thick); pShell.setColor(Color.WHITE); pShell.setAlpha(alpha == 0 ? 0 : prefs.getInt(pfx+"corner_stroke_alpha", 200)); 
            pMoon.setStrokeWidth(thick * 1.5f); pMoon.setColor(Color.parseColor("#00E5FF")); pMoon.setAlpha(alpha == 0 ? 0 : prefs.getInt(pfx+"corner_moon_alpha", 100)); 
            invalidate(); 
        }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); 
            float w = getWidth(), h = getHeight(), pad = pShell.getStrokeWidth()/2; 
            String cPfx = pfx + "corner_" + CORNERS[type] + "_";
            float rad = prefs.getInt(cPfx+"rad", 80); int shape = prefs.getInt(cPfx+"shape", 0);
            
            // Vẽ Vỏ
            Path p = new Path(); 
            if(type==0) { p.moveTo(w-pad, 0); p.lineTo(w-pad, h-rad); p.quadTo(w-pad, h-pad, w-rad, h-pad); p.lineTo(0, h-pad); } 
            else if(type==1) { p.moveTo(pad, 0); p.lineTo(pad, h-rad); p.quadTo(pad, h-pad, rad, h-pad); p.lineTo(w, h-pad); } 
            else if(type==2) { p.moveTo(w-pad, h); p.lineTo(w-pad, rad); p.quadTo(w-pad, pad, w-rad, pad); p.lineTo(0, pad); } 
            else if(type==3) { p.moveTo(pad, h); p.lineTo(pad, rad); p.quadTo(pad, pad, rad, pad); p.lineTo(w, pad); } 
            canvas.drawPath(p, pShell); 

            // Vẽ Lõi Trăng Non
            float mRad = prefs.getInt(cPfx+"moon_rad", 80);
            float mx = (prefs.getInt(cPfx+"moon_x", 1250) - 1250) / 10f;
            float my = (prefs.getInt(cPfx+"moon_y", 1250) - 1250) / 10f;
            Path m = new Path();
            if(type==0) { m.moveTo(w-pad+mx, h-mRad+my); m.quadTo(w-pad+mx, h-pad+my, w-mRad+mx, h-pad+my); } 
            else if(type==1) { m.moveTo(pad+mx, h-mRad+my); m.quadTo(pad+mx, h-pad+my, mRad+mx, h-pad+my); } 
            else if(type==2) { m.moveTo(w-pad+mx, mRad+my); m.quadTo(w-pad+mx, pad+my, w-mRad+mx, pad+my); } 
            else if(type==3) { m.moveTo(pad+mx, mRad+my); m.quadTo(pad+mx, pad+my, mRad+mx, pad+my); } 
            canvas.drawPath(m, pMoon);
        } 
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { isRunning = true; return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate(); isRunning = true; wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); 
        try { cId = cm.getCameraIdList()[0]; cm.registerTorchCallback(torchCb, null); } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        
        IntentFilter filterState = new IntentFilter(); filterState.addAction(Intent.ACTION_SCREEN_OFF); filterState.addAction(Intent.ACTION_SCREEN_ON); filterState.addAction(Intent.ACTION_USER_PRESENT); filterState.addAction("com.manhmoc.edgebar.SYNC_STATE"); filterState.addAction("com.manhmoc.edgebar.UPDATE_UI");
        IntentFilter filterAnim = new IntentFilter("com.manhmoc.edgebar.TEST_ANIM");
        
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(syncReceiver, filterState, Context.RECEIVER_NOT_EXPORTED); registerReceiver(animReceiver, filterAnim, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(syncReceiver, filterState); registerReceiver(animReceiver, filterAnim);
        }

        String cid = "eb_19_home"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar Màn Chính", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); 
        Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar đang chạy nền (Home)").setSmallIcon(android.R.drawable.ic_menu_crop).build(); startForeground(2, n);
        
        fV = new FlashView(this); fV.setAlpha(0f); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}
        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], initP); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener(i)); }
        for(int i=0; i<4; i++) { corners[i] = new CornerView(this, i, "home_"); WindowManager.LayoutParams cp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(corners[i], cp); } catch(Exception e){} corners[i].setOnTouchListener(new CornerTouchListener(i)); } 
        updateVisibility();
    }

    private void updateVisibility() { 
        boolean pLock = prefs.getBoolean("preview_lock", false); boolean pMorse = prefs.getBoolean("preview_morse", false); boolean pHome = prefs.getBoolean("preview_home", false); boolean pSpy = prefs.getBoolean("preview_spy", false);
        boolean isUnlocked = !km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hide = (avoidKbd && isKbd) || isBl; 
        
        if (pLock || pMorse || pSpy) {
            for(View v : bars) if(v!=null) v.setVisibility(View.GONE);
            for(View v : corners) if(v!=null) v.setVisibility(View.GONE);
            if(fV != null) fV.setVisibility(View.GONE); return;
        }

        if (fV != null) fV.setVisibility(View.VISIBLE);
        boolean showHome = pHome || (isUnlocked && !hide);
        for(int i=0; i<5; i++) { 
            if(bars[i] == null) continue; boolean en = prefs.getBoolean("home_"+BARS[i]+"_en", i < 2); bars[i].setVisibility((en && showHome) ? View.VISIBLE : View.GONE);
            if(en && showHome) {
                int alpha = prefs.getInt("home_"+BARS[i]+"_alpha", 50); int w = prefs.getInt("home_"+BARS[i]+"_w", 300); int h = prefs.getInt("home_"+BARS[i]+"_h", 60); int x = prefs.getInt("home_"+BARS[i]+"_x", 0); int y = prefs.getInt("home_"+BARS[i]+"_y", 0); 
                GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); 
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; 
                if(prefs.getInt("home_"+BARS[i]+"_pri_mode", 0) == 1) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(bars[i], p); 
            } 
        } 
        String cPfx = "home_corner_";
        for(int i=0; i<4; i++) { 
            if(corners[i] == null) continue; boolean cornEn = prefs.getBoolean(cPfx+CORNERS[i]+"_en", true); boolean cornInv = prefs.getBoolean(cPfx+CORNERS[i]+"_invis", false); boolean cornBlk = prefs.getBoolean(cPfx+CORNERS[i]+"_block", true); corners[i].setVisibility((cornEn && showHome) ? View.VISIBLE : View.GONE);
            if(cornEn && showHome) { 
                ((CornerView)corners[i]).updateProps(prefs.getInt(cPfx+"thick", 8), cornInv ? 0 : 200); 
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams(); p.gravity = C_GRAV[i]; p.x = prefs.getInt(cPfx+CORNERS[i]+"_x", 0); p.y = prefs.getInt(cPfx+CORNERS[i]+"_y", 0); p.width = prefs.getInt(cPfx+CORNERS[i]+"_w", 100); p.height = prefs.getInt(cPfx+CORNERS[i]+"_h", 100);
                if(!cornBlk) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(corners[i], p); 
            } 
        }
    }
    
    private void doVibrate(int dur) { if(dur<=0) return; try { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    private void handleAction(String prefixKey, String actionKey) {
        String action = prefs.getString(actionKey, "NONE");
        if (!action.equals("NONE")) {
            if (prefs.getBoolean(prefixKey + "_vib", true)) { doVibrate(prefs.getInt("vib_dur", 30)); }
            if (prefs.getBoolean(prefixKey + "_anim", true)) { sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName())); }
            try { switch(action) { case "SCREEN_OFF": case "POWER_DIALOG": case "SCREENSHOT": case "NOTIFICATIONS": Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION"); ipc.putExtra("act", action); sendBroadcast(ipc); break; case "FLASH": cm.setTorchMode(cId, !fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); if (lens != null) { lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); } else { Intent fb = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); fb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(fb); } break; default: if(action.startsWith("INTENT_")) fireIntent(action.split("_")[1]); break; } } catch (Exception e) {}
        }
    }
    
    private void fireIntent(String idx) { try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private class CornerTouchListener implements View.OnTouchListener {
        private int idx; private float sx, sy; private long st; public CornerTouchListener(int i) { this.idx = i; }
        @Override public boolean onTouch(View v, MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 40 && Math.abs(dy) > 40) { boolean isHold = (System.currentTimeMillis() - st) > prefs.getInt("hold_dur", 600); handleAction("home_corner_" + CORNERS[idx], "home_corner_" + CORNERS[idx] + "_" + (isHold ? "hold" : "swipe")); return true; } } return true; }
    }

    private class SidebarTouchListener implements View.OnTouchListener { 
        private int idx; private GestureDetector gd; private float sx, sy; private long st;
        public SidebarTouchListener(int i) { this.idx = i; this.gd = new GestureDetector(HomescreenService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction("home_" + BARS[idx], "home_" + BARS[idx] + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction("home_" + BARS[idx], "home_" + BARS[idx] + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction("home_" + BARS[idx], "home_" + BARS[idx] + "_long"); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; } }); } 
        @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 50 || Math.abs(dy) > 50) { long duration = System.currentTimeMillis() - st; boolean isHold = duration > prefs.getInt("hold_dur", 600); String dir = ""; if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? "right" : "left"; else dir = dy > 0 ? "down" : "up"; String actionName = dir + (isHold ? "_hold" : ""); handleAction("home_" + BARS[idx], "home_" + BARS[idx] + "_" + actionName); return true; } } return true; } 
    }
    @Override public void onDestroy() { super.onDestroy(); isRunning = false; try{unregisterReceiver(syncReceiver); unregisterReceiver(animReceiver); cm.unregisterTorchCallback(torchCb);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); for(int i=0; i<4; i++) if(corners[i] != null) wm.removeView(corners[i]); if (fV != null) wm.removeView(fV); }
}
