package com.manhmoc.edgebar;

import android.accessibilityservice.AccessibilityService; import android.animation.ObjectAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View lBar, rBar, lCorner, rCorner; private FlashView fV; 
    private CameraManager cm; private String cId; private boolean fOn = false; 
    private KeyguardManager km; private SharedPreferences prefs; private GestureDetector gdL, gdR;
    
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences p, String k) { if(k != null && k.startsWith("edge_")) updateBarsLayout(); }
    };

    private class FlashView extends View {
        private Paint p = new Paint();
        public FlashView(Context c) { super(c); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(12f); p.setAntiAlias(true); p.setShadowLayer(10f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float off = p.getStrokeWidth()/2; canvas.drawRect(off, off, getWidth()-off, getHeight()-off, p); }
    }

    private class CornerCurveView extends View {
        private Paint p; private boolean isLeft;
        public CornerCurveView(Context c, boolean left) {
            super(c); isLeft = left;
            p = new Paint(); p.setColor(Color.argb(160, 255, 255, 255));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6f); p.setAntiAlias(true); p.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); android.graphics.Path path = new android.graphics.Path();
            float w = getWidth(), h = getHeight(), pad = 6f;
            if(isLeft) { path.moveTo(pad, h); path.quadTo(pad, pad, w, pad); } // Trái: đường cong ôm từ dưới lên cạnh trái
            else { path.moveTo(w-pad, h); path.quadTo(w-pad, pad, 0, pad); }  // Phải: đường cong ôm từ dưới lên cạnh phải
            canvas.drawPath(path, p);
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        startStatusNotification(); createFloatingBars();
    }

    private void startStatusNotification() {
        String cid = "eb_v10"; NotificationChannel c = new NotificationChannel(cid, "EdgeBar v10", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("EdgeBar v10.1 Đang Chạy").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n);
    }

    private void exec(String a) {
        if (a == null || a.equals("NONE")) return;
        try {
            switch(a) {
                case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break;
                case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break;
                case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break;
                case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
                case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break;
                case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break;
                case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break;
                case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); if (lens != null) { lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); } else { Intent fb = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); fb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(fb); } break;
                case "INTENT_1": fireIntent("1"); break; case "INTENT_2": fireIntent("2"); break; case "INTENT_3": fireIntent("3"); break; case "INTENT_4": fireIntent("4"); break; case "INTENT_5": fireIntent("5"); break;
            }
        } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try {
            String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i;
            if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } 
            else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); }
            if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        } catch (Exception e) {}
    }

    private void handleAction(String side, String gesture) {
        String specificPrefix = km.isKeyguardLocked() ? "lock" : "home";
        String action = prefs.getString(specificPrefix + "_" + side + "_" + gesture, "NONE");
        if (action.equals("NONE")) action = prefs.getString("both_" + side + "_" + gesture, "NONE");
        exec(action);
    }

    private void triggerFlash() { ObjectAnimator.ofFloat(fV, "alpha", 0f, 1f, 0f).setDuration(500).start(); }

    private void updateBarsLayout() {
        if(lBar == null || rBar == null) return;
        int alpha = prefs.getInt("edge_alpha", 50); int thick = prefs.getInt("edge_thick", 80); int size = prefs.getInt("edge_size", 400); int colorBlueGrey = Color.argb(alpha, 96, 125, 139);
        lBar.setBackgroundColor(colorBlueGrey); rBar.setBackgroundColor(colorBlueGrey);
        WindowManager.LayoutParams pL = (WindowManager.LayoutParams) lBar.getLayoutParams(); pL.width = thick; pL.height = size; wm.updateViewLayout(lBar, pL);
        WindowManager.LayoutParams pR = (WindowManager.LayoutParams) rBar.getLayoutParams(); pR.width = thick; pR.height = size; wm.updateViewLayout(rBar, pR);
    }

    private void createFloatingBars() {
        fV = new FlashView(this); fV.setAlpha(0f);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); wm.addView(fV, fp);

        int alpha = prefs.getInt("edge_alpha", 50); int thick = prefs.getInt("edge_thick", 80); int size = prefs.getInt("edge_size", 400);
        int colorBlueGrey = Color.argb(alpha, 96, 125, 139);
        lBar = new View(this); rBar = new View(this); lBar.setBackgroundColor(colorBlueGrey); rBar.setBackgroundColor(colorBlueGrey);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(thick, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT);
        WindowManager.LayoutParams pL = new WindowManager.LayoutParams(); pL.copyFrom(p); pL.gravity = Gravity.BOTTOM | Gravity.LEFT; pL.y = 150; 
        WindowManager.LayoutParams pR = new WindowManager.LayoutParams(); pR.copyFrom(p); pR.gravity = Gravity.BOTTOM | Gravity.RIGHT; pR.y = 150;

        gdL = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction("l", "tap"); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { handleAction("l", "dtap"); return true; }
            @Override public void onLongPress(MotionEvent e) { handleAction("l", "long"); }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                float dX = e2.getX()-e1.getX(), dY = e2.getY()-e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) { handleAction("l", dX > 0 ? "right" : "left"); } else { handleAction("l", dY > 0 ? "down" : "up"); } return true;
            }
        });
        gdR = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction("r", "tap"); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { handleAction("r", "dtap"); return true; }
            @Override public void onLongPress(MotionEvent e) { handleAction("r", "long"); }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                float dX = e2.getX()-e1.getX(), dY = e2.getY()-e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) { handleAction("r", dX > 0 ? "right" : "left"); } else { handleAction("r", dY > 0 ? "down" : "up"); } return true;
            }
        });

        View.OnTouchListener mainTouch = (v, e) -> v == lBar ? gdL.onTouchEvent(e) : gdR.onTouchEvent(e);
        lBar.setOnTouchListener(mainTouch); rBar.setOnTouchListener(mainTouch); wm.addView(lBar, pL); wm.addView(rBar, pR);

        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(100, 100, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT);
        lCorner = new CornerCurveView(this, true); rCorner = new CornerCurveView(this, false);
        WindowManager.LayoutParams lpC = new WindowManager.LayoutParams(); lpC.copyFrom(hp); lpC.gravity = Gravity.BOTTOM | Gravity.LEFT; lpC.x = 0; lpC.y = 0;
        WindowManager.LayoutParams rpC = new WindowManager.LayoutParams(); rpC.copyFrom(hp); rpC.gravity = Gravity.BOTTOM | Gravity.RIGHT; rpC.x = 0; rpC.y = 0;

        View.OnTouchListener cornerTouch = new View.OnTouchListener() {
            private float sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if(e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); return true; }
                if(e.getAction() == MotionEvent.ACTION_UP) { 
                    float dx = e.getRawX()-sx, dy = e.getRawY()-sy; 
                    if(Math.abs(dx) > 30 || Math.abs(dy) > 30) { triggerFlash(); handleAction(v == lCorner ? "l" : "r", "corner"); }
                    return true; 
                } return false;
            }
        };
        lCorner.setOnTouchListener(cornerTouch); rCorner.setOnTouchListener(cornerTouch); wm.addView(lCorner, lpC); wm.addView(rCorner, rpC);
    }
    
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {} @Override public void onInterrupt() {} 
    @Override public void onDestroy() { super.onDestroy(); prefs.unregisterOnSharedPreferenceChangeListener(prefListener); if (lBar != null) wm.removeView(lBar); if (rBar != null) wm.removeView(rBar); if (lCorner != null) wm.removeView(lCorner); if (rCorner != null) wm.removeView(rCorner); if (fV != null) wm.removeView(fV); }
}
