package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService; import android.animation.ObjectAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View[] bars = new View[5]; private View lLockCorner, rLockCorner; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false; private KeyguardManager km; private SharedPreferences prefs; 
    private final String[] BARS = {"b_l", "b_r", "t_l", "t_r", "t_c"};
    private final int[] GRAV = {Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null && (k.contains("_w") || k.contains("_h") || k.contains("_x") || k.contains("_y") || k.contains("_alpha") || k.contains("_en"))) updateBarsLayout(); };

    private class FlashView extends View {
        private Paint p = new Paint();
        public FlashView(Context c) { super(c); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setAntiAlias(true); p.setShadowLayer(4f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float off = p.getStrokeWidth()/2; canvas.drawRect(off, off, getWidth()-off, getHeight()-off, p); }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        String cid = "eb_v10"; NotificationChannel c = new NotificationChannel(cid, "EdgeBar v10", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("EdgeBar v10.7 Đang Chạy").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n);
        createFloatingBars();
    }

    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try { switch(a) { case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break; case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break; case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break; case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break; case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); if (lens != null) { lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); } else { Intent fb = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); fb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(fb); } break; case "INTENT_1": fireIntent("1"); break; case "INTENT_2": fireIntent("2"); break; case "INTENT_3": fireIntent("3"); break; case "INTENT_4": fireIntent("4"); break; case "INTENT_5": fireIntent("5"); break; } } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {}
    }

    private void handleAction(String suffix) { // suffix is like "b_l_tap" or "l_corner"
        String state = km.isKeyguardLocked() ? "lock_" : "home_";
        String action = prefs.getString(state + suffix, "NONE");
        if (action.equals("NONE")) action = prefs.getString("both_" + suffix, "NONE");
        exec(action);
    }

    private void createFloatingBars() {
        fV = new FlashView(this); fV.setAlpha(0f);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}

        // 5 THANH (Hoạt động ở cả Home và Lock)
        for(int i=0; i<5; i++) {
            bars[i] = new View(this);
            WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT);
            try { wm.addView(bars[i], initP); } catch(Exception e){}
            bars[i].setOnTouchListener(new SidebarTouchListener(i));
        }
        updateBarsLayout();

        // 2 GÓC: HÌNH VIÊN THUỐC GANIMA MỀM MẠI (Chỉ bắt chạm khi ở Lockscreen)
        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(80, 8, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT);
        lLockCorner = new View(this); lLockCorner.setBackgroundResource(getResources().getIdentifier("white_handle", "drawable", getPackageName()));
        rLockCorner = new View(this); rLockCorner.setBackgroundResource(getResources().getIdentifier("white_handle", "drawable", getPackageName()));
        
        WindowManager.LayoutParams lpC = new WindowManager.LayoutParams(); lpC.copyFrom(hp); lpC.gravity = Gravity.BOTTOM | Gravity.LEFT; lpC.x = 40; lpC.y = 40;
        WindowManager.LayoutParams rpC = new WindowManager.LayoutParams(); rpC.copyFrom(hp); rpC.gravity = Gravity.BOTTOM | Gravity.RIGHT; rpC.x = 40; rpC.y = 40;
        
        View.OnTouchListener lockCornerTouch = new View.OnTouchListener() {
            private float sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if(!km.isKeyguardLocked()) return false; // Nhường sân cho ADB nếu không ở Lockscreen
                if(e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); return true; }
                if(e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX()-sx, dy = e.getRawY()-sy; if(dy < -40 && Math.abs(dx) > 40) { ObjectAnimator.ofFloat(fV, "alpha", 0f, 0.5f, 0f).setDuration(1000).start(); handleAction(v == lLockCorner ? "l_corner" : "r_corner"); } return true; } return false;
            }
        };
        lLockCorner.setOnTouchListener(lockCornerTouch); rLockCorner.setOnTouchListener(lockCornerTouch); 
        try { wm.addView(lLockCorner, lpC); wm.addView(rLockCorner, rpC); } catch(Exception e){}
    }

    private void updateBarsLayout() { for(int i=0; i<5; i++) updateBarLayout(i); }
    private void updateBarLayout(int i) {
        if(bars[i] == null) return;
        boolean en = prefs.getBoolean(BARS[i]+"_en", i < 2); bars[i].setVisibility(en ? View.VISIBLE : View.GONE);
        if(!en) return;
        int alpha = prefs.getInt(BARS[i]+"_alpha", 50); int w = prefs.getInt(BARS[i]+"_w", 300); int h = prefs.getInt(BARS[i]+"_h", 60); int x = prefs.getInt(BARS[i]+"_x", 0); int y = prefs.getInt(BARS[i]+"_y", 0);
        bars[i].setBackgroundColor(Color.argb(alpha, 96, 125, 139));
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; wm.updateViewLayout(bars[i], p);
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        private int idx; private GestureDetector gd;
        public SidebarTouchListener(int i) { this.idx = i; this.gd = new GestureDetector(EdgeBarService.this, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction(BARS[idx] + "_tap"); return true; }
                @Override public boolean onDoubleTap(MotionEvent e) { handleAction(BARS[idx] + "_dtap"); return true; }
                @Override public void onLongPress(MotionEvent e) { handleAction(BARS[idx] + "_long"); }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { if(e1==null||e2==null) return false; float dX = e2.getX()-e1.getX(), dY = e2.getY()-e1.getY(); if (Math.abs(dX) > Math.abs(dY)) handleAction(BARS[idx] + (dX > 0 ? "_right" : "_left")); else handleAction(BARS[idx] + (dY > 0 ? "_down" : "_up")); return true; }
            });
        }
        @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); return true; }
    }
    
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {} @Override public void onInterrupt() {} 
    @Override public void onDestroy() { super.onDestroy(); prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); if (lLockCorner != null) wm.removeView(lLockCorner); if (rLockCorner != null) wm.removeView(rLockCorner); if (fV != null) wm.removeView(fV); }
}
