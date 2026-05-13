package com.manhmoc.edgebar;

import android.animation.ObjectAnimator; import android.app.Service; import android.app.KeyguardManager; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.IBinder; import android.provider.MediaStore; import android.provider.Settings; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager;

public class HomescreenCornerService extends Service {
    private WindowManager wm; private View lHomeCorner, rHomeCorner; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false; private SharedPreferences prefs; private KeyguardManager km;

    private class FlashView extends View {
        private Paint p = new Paint();
        public FlashView(Context c) { super(c); p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4f); p.setAntiAlias(true); p.setShadowLayer(4f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float off = p.getStrokeWidth()/2; canvas.drawRect(off, off, getWidth()-off, getHeight()-off, p); }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }

        fV = new FlashView(this); fV.setAlpha(0f);
        WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}

        // VIÊN THUỐC GANIMA MỀM MẠI
        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(80, 8, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lHomeCorner = new View(this); lHomeCorner.setBackgroundResource(getResources().getIdentifier("white_handle", "drawable", getPackageName()));
        rHomeCorner = new View(this); rHomeCorner.setBackgroundResource(getResources().getIdentifier("white_handle", "drawable", getPackageName()));
        
        WindowManager.LayoutParams lpC = new WindowManager.LayoutParams(); lpC.copyFrom(hp); lpC.gravity = Gravity.BOTTOM | Gravity.LEFT; lpC.x = 40; lpC.y = 40;
        WindowManager.LayoutParams rpC = new WindowManager.LayoutParams(); rpC.copyFrom(hp); rpC.gravity = Gravity.BOTTOM | Gravity.RIGHT; rpC.x = 40; rpC.y = 40;

        View.OnTouchListener homeCornerTouch = new View.OnTouchListener() {
            private float sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if(km.isKeyguardLocked()) return false; // Không đá sân Lockscreen của Trợ năng
                if(e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); return true; }
                if(e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX()-sx, dy = e.getRawY()-sy; if(dy < -40 && Math.abs(dx) > 40) { 
                    ObjectAnimator.ofFloat(fV, "alpha", 0f, 0.5f, 0f).setDuration(1000).start(); 
                    handleAction(v == lHomeCorner ? "l_corner" : "r_corner"); 
                } return true; } return false;
            }
        };
        lHomeCorner.setOnTouchListener(homeCornerTouch); rHomeCorner.setOnTouchListener(homeCornerTouch); 
        try { wm.addView(lHomeCorner, lpC); wm.addView(rHomeCorner, rpC); } catch(Exception e){}
    }

    private void handleAction(String suffix) { // suffix is "l_corner" or "r_corner"
        String action = prefs.getString("home_" + suffix, "NONE");
        if (action.equals("NONE")) action = prefs.getString("both_" + suffix, "NONE");
        if (action.equals("NONE")) return;
        try { switch(action) { case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); break; case "INTENT_1": fireIntent("1"); break; case "INTENT_2": fireIntent("2"); break; case "INTENT_3": fireIntent("3"); break; case "INTENT_4": fireIntent("4"); break; case "INTENT_5": fireIntent("5"); break; } } catch (Exception e) {}
    }

    private void fireIntent(String idx) {
        try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {}
    }
    @Override public void onDestroy() { super.onDestroy(); if (lHomeCorner != null) wm.removeView(lHomeCorner); if (rHomeCorner != null) wm.removeView(rHomeCorner); if (fV != null) wm.removeView(fV); }
}
