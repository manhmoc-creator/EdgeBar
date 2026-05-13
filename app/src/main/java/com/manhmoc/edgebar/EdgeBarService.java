package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
public class EdgeBarService extends AccessibilityService {
    private WindowManager wm;
    private View lBar, rBar;
    private CameraManager cm;
    private String cId;
    private boolean fOn = false;
    private KeyguardManager km;
    private SharedPreferences prefs;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        startStatusNotification(); createFloatingBars();
    }

    private void startStatusNotification() {
        String cid = "eb_v8";
        NotificationChannel c = new NotificationChannel(cid, "EdgeBar Status", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("EdgeBar v8").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
        startForeground(1, n);
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
                case "QR": Intent qr = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(qr); break;
                case "INTENT":
                    Intent i = new Intent(prefs.getString("i_act", ""));
                    String pkg = prefs.getString("i_pkg", ""); if(!pkg.isEmpty()) i.setPackage(pkg);
                    String data = prefs.getString("i_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data));
                    if(prefs.getBoolean("i_br", true)) sendBroadcast(i);
                    else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
                    break;
            }
        } catch (Exception e) {}
    }

    private void createFloatingBars() {
        DisplayMetrics m = getResources().getDisplayMetrics(); int w = (m.widthPixels - 250) / 2;
        lBar = new View(this); rBar = new View(this); lBar.setBackgroundColor(Color.TRANSPARENT); rBar.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        WindowManager.LayoutParams pL = new WindowManager.LayoutParams(); pL.copyFrom(p); pL.gravity = Gravity.BOTTOM | Gravity.LEFT;
        WindowManager.LayoutParams pR = new WindowManager.LayoutParams(); pR.copyFrom(p); pR.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        
        lBar.setOnTouchListener((v, e) -> check(e, "l")); rBar.setOnTouchListener((v, e) -> check(e, "r"));
        wm.addView(lBar, pL); wm.addView(rBar, pR);
    }

    private GestureDetector gdL, gdR;
    private boolean check(MotionEvent e, String side) {
        int m = prefs.getInt("mode", 0); boolean lock = km.isKeyguardLocked();
        if ((m == 1 && !lock) || (m == 2 && lock)) return false;
        if (gdL == null) gdL = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { exec(prefs.getString("l_tap", "NONE")); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { exec(prefs.getString("l_dtap", "NONE")); return true; }
        });
        if (gdR == null) gdR = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { exec(prefs.getString("r_tap", "NONE")); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { exec(prefs.getString("r_dtap", "NONE")); return true; }
        });
        return side.equals("l") ? gdL.onTouchEvent(e) : gdR.onTouchEvent(e);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
