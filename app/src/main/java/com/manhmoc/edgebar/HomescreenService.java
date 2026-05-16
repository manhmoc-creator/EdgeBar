package com.manhmoc.edgebar;
import android.animation.ValueAnimator; import android.animation.AnimatorListenerAdapter; import android.animation.Animator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.*; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.VibrationEffect; import android.os.Vibrator; import android.os.IBinder; import android.provider.MediaStore; import android.provider.Settings; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager;

public class HomescreenService extends Service {
    public static boolean isRunning = false; 
    private WindowManager wm; private View[] bars = new View[5]; private View[] corners = new View[4]; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private SharedPreferences prefs; private KeyguardManager km; private Vibrator vibrator;
    private final String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"tl", "tr", "bl", "br"}; private final int[] C_GRAV = {Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT};
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null) fV.updateStyle(); } };
    private BroadcastReceiver syncReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if(i.getAction().equals("com.manhmoc.edgebar.SYNC_STATE")) { isKbd = i.getBooleanExtra("isKbd", false); isBl = i.getBooleanExtra("isBl", false); } updateVisibility(); } };

    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phase = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); p.setShadowLayer(15f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { 
            int[] cArr; switch(cTheme) { case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF")}; break; case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700")}; break; case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00")}; break; case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF")}; break; case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000")}; break; case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00")}; break; case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853")}; break; default: cArr=new int[]{Color.WHITE, Color.WHITE}; break; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.CLAMP)); p.setShadowLayer(15f, 0, 0, cArr[0]); 
        }
        public void setPhase(float ph) { this.phase = ph; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { 
            float w = getWidth(), h = getHeight();
            if(aStyle > 0 && w > 0 && h > 0) { 
                float perim = 2 * (w + h); float len = (aStyle==1) ? perim/4f : (aStyle==2) ? perim/8f : perim/16f; float gap = (aStyle==1) ? perim*3f/4f : (aStyle==2) ? perim*3f/8f : perim*3f/16f; 
                if (len > 0 && gap > 0) p.setPathEffect(new DashPathEffect(new float[]{len, gap}, phase)); else p.setPathEffect(null);
            } else { p.setPathEffect(null); }
            float off = p.getStrokeWidth()/2; canvas.drawRoundRect(off, off, w-off, h-off, radius, radius, p); 
        } 
    }
    
    private class CornerView extends View { 
        private Paint p; private int type; 
        public CornerView(Context c, int type) { super(c); this.type = type; p = new Paint(); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setAntiAlias(true); p.setStrokeCap(Paint.Cap.ROUND); } 
        public void updateProps(int thick, int alpha) { p.setStrokeWidth(thick); p.setAlpha(alpha); invalidate(); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); Path path = new Path(); float w = getWidth(), h = getHeight(), pad = p.getStrokeWidth()/2; float rad = prefs.getInt("home_corner_rad", 40);
            if(type==0) { path.moveTo(pad, h); path.lineTo(pad, rad); path.quadTo(pad, pad, rad, pad); path.lineTo(w, pad); } else if(type==1) { path.moveTo(0, pad); path.lineTo(w-rad, pad); path.quadTo(w-pad, pad, w-pad, rad); path.lineTo(w-pad, h); } else if(type==2) { path.moveTo(pad, 0); path.lineTo(pad, h-rad); path.quadTo(pad, h-pad, rad, h-pad); path.lineTo(w, h-pad); } else if(type==3) { path.moveTo(0, h-pad); path.lineTo(w-rad, h-pad); path.quadTo(w-pad, h-pad, w-pad, h-rad); path.lineTo(w-pad, 0); } canvas.drawPath(path, p); 
        } 
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { isRunning = true; return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate(); isRunning = true; wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction("com.manhmoc.edgebar.SYNC_STATE");
        registerReceiver(syncReceiver, filter);

        String cid = "eb_20_home"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar Màn Chính", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar Lớp phủ ADB").setSmallIcon(android.R.drawable.ic_dialog_info).build(); startForeground(2, n);
        
        fV = new FlashView(this); fV.setAlpha(0f); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}
        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], initP); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener(i)); }
        for(int i=0; i<4; i++) { corners[i] = new CornerView(this, i); WindowManager.LayoutParams cp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT); try { wm.addView(corners[i], cp); } catch(Exception e){} corners[i].setOnTouchListener(new CornerTouchListener(i)); } updateVisibility();
    }

    private void updateVisibility() { 
        boolean isUnlocked = !km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hide = (avoidKbd && isKbd) || isBl; 
        for(int i=0; i<5; i++) { if(bars[i] == null) continue; boolean en = prefs.getBoolean("home_"+BARS[i]+"_en", i < 2); bars[i].setVisibility((en && isUnlocked && !hide) ? View.VISIBLE : View.GONE); if(en && isUnlocked) { int alpha = prefs.getInt("home_"+BARS[i]+"_alpha", 50); boolean block = prefs.getBoolean("home_"+BARS[i]+"_block", true); if(block && alpha == 0) alpha = 1; int w = prefs.getInt("home_"+BARS[i]+"_w", 300); int h = prefs.getInt("home_"+BARS[i]+"_h", 60); int x = prefs.getInt("home_"+BARS[i]+"_x", 0); int y = prefs.getInt("home_"+BARS[i]+"_y", 0); GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); if(p==null) continue; p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; if(!block) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; try { wm.updateViewLayout(bars[i], p); } catch(Exception e){} } } 
        for(int i=0; i<4; i++) { if(corners[i] == null) continue; boolean cornEn = prefs.getBoolean("home_corner_"+CORNERS[i]+"_en", true); boolean cornInv = prefs.getBoolean("home_corner_"+CORNERS[i]+"_invis", false); boolean cornBlk = prefs.getBoolean("home_corner_"+CORNERS[i]+"_block", true); corners[i].setVisibility((cornEn && isUnlocked && !hide) ? View.VISIBLE : View.GONE); if(cornEn && isUnlocked) { int alpha = cornInv ? (cornBlk ? 1 : 0) : 200; ((CornerView)corners[i]).updateProps(prefs.getInt("home_corner_thick", 8), alpha); WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams(); if(p==null) continue; p.gravity = C_GRAV[i]; p.x = prefs.getInt("home_corner_w", 0); p.y = prefs.getInt("home_corner_h", 0); if(!cornBlk) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; try { wm.updateViewLayout(corners[i], p); } catch(Exception e){} } }
    }
    
    private void doVibrate(int dur) { if(dur<=0) return; try { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    private void handleAction(String prefixKey, String actionKey) {
        String action = prefs.getString(actionKey, "NONE");
        if (!action.equals("NONE")) {
            if (prefs.getBoolean(actionKey + "_vib", true)) { doVibrate(prefs.getInt("vib_dur", 30)); }
            if (prefs.getBoolean(actionKey + "_anim", true) && fV != null) { 
                fV.updateStyle(); int style = prefs.getInt("anim_style", 0); int dur = prefs.getInt("anim_dur", 1500); 
                if(style == 0) { fV.setAlpha(0f); ValueAnimator anim = ValueAnimator.ofFloat(0f, 0.8f, 0f); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue())); anim.start(); } 
                else { fV.setAlpha(1f); float p = 2 * (fV.getWidth() + fV.getHeight()); ValueAnimator anim = ValueAnimator.ofFloat(0f, -p); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue())); anim.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { fV.setAlpha(0f); } }); anim.start(); } 
            }
            try { switch(action) { case "SCREEN_OFF": case "POWER_DIALOG": case "SCREENSHOT": case "NOTIFICATIONS": Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION"); ipc.putExtra("act", action); sendBroadcast(ipc); break; case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); break; default: if(action.startsWith("INTENT_")) fireIntent(action.split("_")[1]); break; } } catch (Exception e) {}
        }
    }
    
    private void fireIntent(String idx) { try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private class CornerTouchListener implements View.OnTouchListener {
        private int idx; private float sx, sy; private long st; public CornerTouchListener(int i) { this.idx = i; }
        @Override public boolean onTouch(View v, MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 40 && Math.abs(dy) > 40) { boolean isHold = (System.currentTimeMillis() - st) > prefs.getInt("hold_dur", 600); handleAction("home_corner_" + CORNERS[idx] + "_" + (isHold ? "hold" : "swipe")); return true; } } return true; }
    }

    private class SidebarTouchListener implements View.OnTouchListener { 
        private int idx; private GestureDetector gd; private float sx, sy; private long st;
        public SidebarTouchListener(int i) { this.idx = i; this.gd = new GestureDetector(HomescreenService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction("home_" + BARS[idx] + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction("home_" + BARS[idx] + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction("home_" + BARS[idx] + "_long"); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; } }); } 
        @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 50 || Math.abs(dy) > 50) { long duration = System.currentTimeMillis() - st; boolean isHold = duration > prefs.getInt("hold_dur", 600); String dir = ""; if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? "right" : "left"; else dir = dy > 0 ? "down" : "up"; String actionName = dir + (isHold ? "_hold" : ""); handleAction("home_" + BARS[idx] + "_" + actionName); return true; } } return true; } 
    }
    @Override public void onDestroy() { super.onDestroy(); isRunning = false; try{unregisterReceiver(syncReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) { try { if(bars[i] != null) wm.removeView(bars[i]); } catch(Exception e){} } for(int i=0; i<4; i++) { try { if(corners[i] != null) wm.removeView(corners[i]); } catch(Exception e){} } try { if(fV != null) wm.removeView(fV); } catch(Exception e){} }
}
