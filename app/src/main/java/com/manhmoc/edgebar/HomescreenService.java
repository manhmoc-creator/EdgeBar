package com.manhmoc.edgebar;
import android.animation.ObjectAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path; import android.graphics.PixelFormat; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.IBinder; import android.provider.MediaStore; import android.provider.Settings; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager;

public class HomescreenService extends Service {
    private WindowManager wm; private View[] bars = new View[5]; private View lHomeCorner, rHomeCorner; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false; private SharedPreferences prefs; private KeyguardManager km;
    private final String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) updateVisibility(); };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateVisibility(); } };
    
    // NHẬN SÓNG TỪ RADAR KBD CỦA TRỢ NĂNG
    private BroadcastReceiver kbdReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { isKbd = i.getBooleanExtra("isKbd", false); updateVisibility(); } };

    private class FlashView extends View { private Paint p = new Paint(); public FlashView(Context c) { super(c); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setAntiAlias(true); p.setShadowLayer(4f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); } @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float off = p.getStrokeWidth()/2; canvas.drawRect(off, off, getWidth()-off, getHeight()-off, p); } }
    private class AssistantCurveView extends View { private Paint p; private boolean isLeft; public AssistantCurveView(Context c, boolean left) { super(c); isLeft = left; p = new Paint(); p.setColor(Color.WHITE); p.setAlpha(200); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5f); p.setAntiAlias(true); p.setStrokeCap(Paint.Cap.ROUND); } @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); Path path = new Path(); float w = getWidth(), h = getHeight(), pad = 5f; if(isLeft) { path.moveTo(pad, 0); path.quadTo(pad, h-pad, w, h-pad); } else { path.moveTo(w-pad, 0); path.quadTo(w-pad, h-pad, 0, h-pad); } canvas.drawPath(path, p); } }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); registerReceiver(stateReceiver, filter);
        
        IntentFilter kbdF = new IntentFilter("com.manhmoc.edgebar.KBD_STATE");
        if(android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(kbdReceiver, kbdF, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(kbdReceiver, kbdF);

        String cid = "eb_v14_home"; NotificationChannel c = new NotificationChannel(cid, "EdgeBar V14 Homescreen", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("V14 ADB Màn Chính Đang Chạy").setSmallIcon(android.R.drawable.ic_lock_lock).build(); startForeground(2, n);
        
        fV = new FlashView(this); fV.setAlpha(0f); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}

        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], initP); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener(i)); }

        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lHomeCorner = new AssistantCurveView(this, true); rHomeCorner = new AssistantCurveView(this, false);
        WindowManager.LayoutParams lpC = new WindowManager.LayoutParams(); lpC.copyFrom(hp); lpC.gravity = Gravity.BOTTOM | Gravity.LEFT;
        WindowManager.LayoutParams rpC = new WindowManager.LayoutParams(); rpC.copyFrom(hp); rpC.gravity = Gravity.BOTTOM | Gravity.RIGHT;

        View.OnTouchListener homeCornerTouch = new View.OnTouchListener() { private float sx, sy; @Override public boolean onTouch(View v, MotionEvent e) { if(e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); return true; } if(e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX()-sx, dy = e.getRawY()-sy; if(dy < -40 && Math.abs(dx) > 40) { handleAction(v == lHomeCorner ? "l_corner" : "r_corner"); } return true; } return false; } };
        lHomeCorner.setOnTouchListener(homeCornerTouch); rHomeCorner.setOnTouchListener(homeCornerTouch); try { wm.addView(lHomeCorner, lpC); wm.addView(rHomeCorner, rpC); } catch(Exception e){}
        updateVisibility();
    }

    private void updateVisibility() {
        boolean isUnlocked = !km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hideForKbd = avoidKbd && isKbd;
        for(int i=0; i<5; i++) { if(bars[i] == null) continue; boolean en = prefs.getBoolean(BARS[i]+"_en", i < 2); bars[i].setVisibility((en && isUnlocked && !hideForKbd) ? View.VISIBLE : View.GONE); if(en && isUnlocked) { int alpha = prefs.getInt(BARS[i]+"_alpha", 50); int w = prefs.getInt(BARS[i]+"_w", 300); int h = prefs.getInt(BARS[i]+"_h", 60); int x = prefs.getInt(BARS[i]+"_x", 0); int y = prefs.getInt(BARS[i]+"_y", 0); GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; wm.updateViewLayout(bars[i], p); } }
        boolean cornEn = prefs.getBoolean("home_corner_en", true); int cornVis = (cornEn && isUnlocked && !hideForKbd) ? View.VISIBLE : View.GONE;
        if (lHomeCorner != null) lHomeCorner.setVisibility(cornVis); if (rHomeCorner != null) rHomeCorner.setVisibility(cornVis);
    }

    private void handleAction(String suffix) {
        String key = "home_" + suffix; String action = prefs.getString(key, "NONE");
        if (action.equals("NONE")) { key = "both_" + suffix; action = prefs.getString(key, "NONE"); }
        if (!action.equals("NONE")) {
            if (prefs.getBoolean(key + "_anim", true)) { ObjectAnimator.ofFloat(fV, "alpha", 0f, 0.4f, 0f).setDuration(1500).start(); } // ANIMATION CHẬM VÀ NHẸ
            try { switch(action) { case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); break; default: if(action.startsWith("INTENT_")) fireIntent(action.split("_")[1]); break; } } catch (Exception e) {}
        }
    }
    
    private void fireIntent(String idx) { try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private class SidebarTouchListener implements View.OnTouchListener { private int idx; private GestureDetector gd; public SidebarTouchListener(int i) { this.idx = i; this.gd = new GestureDetector(HomescreenService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction(BARS[idx] + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction(BARS[idx] + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction(BARS[idx] + "_long"); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { if(e1==null||e2==null) return false; float dX = e2.getX()-e1.getX(), dY = e2.getY()-e1.getY(); if (Math.abs(dX) > Math.abs(dY)) handleAction(BARS[idx] + (dX > 0 ? "_right" : "_left")); else handleAction(BARS[idx] + (dY > 0 ? "_down" : "_up")); return true; } }); } @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); return true; } }
    @Override public void onDestroy() { super.onDestroy(); try{unregisterReceiver(stateReceiver); unregisterReceiver(kbdReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); if (lHomeCorner != null) wm.removeView(lHomeCorner); if (rHomeCorner != null) wm.removeView(rHomeCorner); if (fV != null) wm.removeView(fV); }
}
