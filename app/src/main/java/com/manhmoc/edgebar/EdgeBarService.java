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
    private GestureDetector gdL, gdR;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        startStatusNotification(); 
        createFloatingBars();
    }

    private void startStatusNotification() {
        String cid = "eb_v9";
        NotificationChannel c = new NotificationChannel(cid, "EdgeBar Status", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("EdgeBar v9").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
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
                case "QR": 
                    Intent qr = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("googleapp://lens"));
                    qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(qr); break;
                case "INTENT_1": fireIntent("1"); break;
                case "INTENT_2": fireIntent("2"); break;
                case "INTENT_3": fireIntent("3"); break;
            }
        } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try {
            String act = prefs.getString("i"+idx+"_act", "");
            String pkg = prefs.getString("i"+idx+"_pkg", "");
            Intent i;

            // NẾU ĐỂ TRỐNG ACTION MÀ NHẬP PACKAGE -> TỰ ĐỘNG MỞ APP
            if (act.isEmpty() && !pkg.isEmpty()) {
                i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) return;
            } else {
                i = new Intent(act);
                if(!pkg.isEmpty()) i.setPackage(pkg);
                String cls = prefs.getString("i"+idx+"_cls", ""); 
                if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls));
                String data = prefs.getString("i"+idx+"_data", ""); 
                if(!data.isEmpty()) i.setData(android.net.Uri.parse(data));
                String cat = prefs.getString("i"+idx+"_cat", ""); 
                if(!cat.isEmpty()) i.addCategory(cat);
                
                String flg = prefs.getString("i"+idx+"_flags", ""); 
                if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg));
            }

            if(prefs.getBoolean("i"+idx+"_br", true) && act.isEmpty() == false) {
                sendBroadcast(i);
            } else { 
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
                startActivity(i); 
            }
        } catch (Exception e) {}
    }

    private String getPrefix() {
        int m = prefs.getInt("master_mode", 0);
        if (m == 0) return "both";
        return km.isKeyguardLocked() ? "lock" : "home";
    }

    private void handleGesture(String side, String gesture) {
        String pfx = getPrefix();
        exec(prefs.getString(pfx + "_" + side + "_" + gesture, "NONE"));
    }

    private void createFloatingBars() {
        DisplayMetrics m = getResources().getDisplayMetrics(); int w = (m.widthPixels - 250) / 2;
        lBar = new View(this); rBar = new View(this); lBar.setBackgroundColor(Color.TRANSPARENT); rBar.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(w, 80, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        WindowManager.LayoutParams pL = new WindowManager.LayoutParams(); pL.copyFrom(p); pL.gravity = Gravity.BOTTOM | Gravity.LEFT;
        WindowManager.LayoutParams pR = new WindowManager.LayoutParams(); pR.copyFrom(p); pR.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        
        // KHỞI TẠO CẢM BIẾN 1 LẦN DUY NHẤT
        gdL = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleGesture("l", "tap"); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { handleGesture("l", "dtap"); return true; }
            @Override public void onLongPress(MotionEvent e) { handleGesture("l", "long"); }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if(e1==null||e2==null)return false; float dX = e2.getX()-e1.getX(); float dY = e2.getY()-e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) { if (Math.abs(dX) > 40) handleGesture("l", dX > 0 ? "right" : "left"); } 
                else { if (Math.abs(dY) > 40) handleGesture("l", dY > 0 ? "down" : "up"); } return true;
            }
        });

        gdR = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleGesture("r", "tap"); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { handleGesture("r", "dtap"); return true; }
            @Override public void onLongPress(MotionEvent e) { handleGesture("r", "long"); }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if(e1==null||e2==null)return false; float dX = e2.getX()-e1.getX(); float dY = e2.getY()-e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) { if (Math.abs(dX) > 40) handleGesture("r", dX > 0 ? "right" : "left"); } 
                else { if (Math.abs(dY) > 40) handleGesture("r", dY > 0 ? "down" : "up"); } return true;
            }
        });

        View.OnTouchListener touchLogic = (v, e) -> {
            int md = prefs.getInt("master_mode", 0); boolean isLocked = km.isKeyguardLocked();
            if ((md == 1 && !isLocked) || (md == 2 && isLocked)) return false;
            return v == lBar ? gdL.onTouchEvent(e) : gdR.onTouchEvent(e);
        };

        lBar.setOnTouchListener(touchLogic); rBar.setOnTouchListener(touchLogic);
        wm.addView(lBar, pL); wm.addView(rBar, pR);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { super.onDestroy(); if (lBar != null) wm.removeView(lBar); if (rBar != null) wm.removeView(rBar); }
}
