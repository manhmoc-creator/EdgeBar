package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService; import android.animation.ObjectAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path; import android.graphics.PixelFormat; import android.graphics.LinearGradient; import android.graphics.Shader; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.VibrationEffect; import android.os.Vibrator; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View[] bars = new View[5]; private View lLockCorner, rLockCorner; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private KeyguardManager km; private SharedPreferences prefs; private Vibrator vibrator;
    private final String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null) fV.updateStyle(); } };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateVisibility(); } };
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) { exec(i.getStringExtra("act")); } } };

    // V19 GRADIENT ANIMATION RENDERER
    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE";
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); p.setShadowLayer(8f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { 
            p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); invalidate(); 
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) {
            int[] colors;
            switch(cTheme) {
                case "NEON": colors = new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF")}; break;
                case "CYBERPUNK": colors = new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700")}; break;
                case "LAVA": colors = new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00")}; break;
                case "OCEAN": colors = new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF")}; break;
                case "MATRIX": colors = new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000")}; break;
                case "SUNSET": colors = new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00")}; break;
                default: p.setShader(null); p.setColor(Color.WHITE); p.setShadowLayer(8f, 0, 0, Color.WHITE); return;
            }
            p.setShader(new LinearGradient(0, 0, w, h, colors, null, Shader.TileMode.CLAMP)); p.setShadowLayer(8f, 0, 0, colors[0]);
        }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float off = p.getStrokeWidth()/2; canvas.drawRoundRect(off, off, getWidth()-off, getHeight()-off, radius, radius, p); } 
    }
    private class AssistantCurveView extends View { private Paint p; private boolean isLeft; public AssistantCurveView(Context c, boolean left) { super(c); isLeft = left; p = new Paint(); p.setColor(Color.WHITE); p.setAlpha(200); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8f); p.setAntiAlias(true); p.setStrokeCap(Paint.Cap.ROUND); } @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); Path path = new Path(); float w = getWidth(), h = getHeight(), pad = 8f; if(isLeft) { path.moveTo(pad, 0); path.quadTo(pad, h-pad, w, h-pad); } else { path.moveTo(w-pad, 0); path.quadTo(w-pad, h-pad, 0, h-pad); } canvas.drawPath(path, p); } }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener); IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); registerReceiver(stateReceiver, filter);
        if(Build.VERSION.SDK_INT >= 33) registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));
        String cid = "eb_v19_acc"; NotificationChannel c = new NotificationChannel(cid, "EdgeBar V19 Lock", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("V19 Trợ Năng Màn Khoá").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n); createFloatingBars();
    }

    // V19 RADAR: Sửa lỗi Gboard bằng cách quét sâu Package Name
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { 
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { 
            String pName = event.getPackageName() != null ? event.getPackageName().toString() : "";
            String cName = event.getClassName() != null ? event.getClassName().toString() : ""; 
            isKbd = pName.contains("inputmethod") || cName.contains("InputWindow") || cName.contains("keyboard") || cName.contains("Keyboard"); 
            String bl = prefs.getString("blacklist", ""); isBl = !pName.isEmpty() && bl.contains(pName); 
            updateVisibility(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); i.putExtra("isKbd", isKbd); i.putExtra("isBl", isBl); sendBroadcast(i); 
        } 
    }

    private void exec(String a) { if (a == null || a.equals("NONE")) return; try { switch(a) { case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break; case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break; case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break; case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break; case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); if (lens != null) { lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); } else { Intent fb = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); fb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(fb); } break; default: if(a.startsWith("INTENT_")) fireIntent(a.split("_")[1]); break; } } catch (Exception e) {} }
    private void fireIntent(String idx) { try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private void handleAction(String suffix) { String key = "lock_" + suffix; String action = prefs.getString(key, "NONE"); if (action.equals("NONE")) { key = "both_" + suffix; action = prefs.getString(key, "NONE"); } if (!action.equals("NONE")) { if (prefs.getBoolean(key + "_anim", true)) { ObjectAnimator.ofFloat(fV, "alpha", 0f, 0.5f, 0f).setDuration(prefs.getInt("anim_dur", 1500)).start(); } exec(action); } }
    private void doVibrate() { try { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(30); } catch(Exception e){} }

    private void createFloatingBars() {
        fV = new FlashView(this); fV.setAlpha(0f); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}
        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], initP); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener(i)); } 
        WindowManager.LayoutParams hp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); lLockCorner = new AssistantCurveView(this, true); rLockCorner = new AssistantCurveView(this, false); WindowManager.LayoutParams lpC = new WindowManager.LayoutParams(); lpC.copyFrom(hp); lpC.gravity = Gravity.BOTTOM | Gravity.LEFT; WindowManager.LayoutParams rpC = new WindowManager.LayoutParams(); rpC.copyFrom(hp); rpC.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        View.OnTouchListener lockCornerTouch = new View.OnTouchListener() { private float sx, sy; @Override public boolean onTouch(View v, MotionEvent e) { if(e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); return true; } if(e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX()-sx, dy = e.getRawY()-sy; if(dy < -40 && Math.abs(dx) > 40) { handleAction(v == lLockCorner ? "l_corner" : "r_corner"); } return true; } return false; } };
        lLockCorner.setOnTouchListener(lockCornerTouch); rLockCorner.setOnTouchListener(lockCornerTouch); try { wm.addView(lLockCorner, lpC); wm.addView(rLockCorner, rpC); } catch(Exception e){} updateVisibility();
    }

    private void updateVisibility() {
        boolean isLocked = km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hide = (avoidKbd && isKbd) || isBl;
        for(int i=0; i<5; i++) { if(bars[i] == null) continue; boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", i < 2); bars[i].setVisibility((en && isLocked && !hide) ? View.VISIBLE : View.GONE); if(en && isLocked) { int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha", 50); int w = prefs.getInt("lock_"+BARS[i]+"_w", 300); int h = prefs.getInt("lock_"+BARS[i]+"_h", 60); int x = prefs.getInt("lock_"+BARS[i]+"_x", 0); int y = prefs.getInt("lock_"+BARS[i]+"_y", 0); GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; wm.updateViewLayout(bars[i], p); } }
        boolean cornEn = prefs.getBoolean("lock_corner_en", true); int cornVis = (cornEn && isLocked && !hide) ? View.VISIBLE : View.GONE; if (lLockCorner != null) lLockCorner.setVisibility(cornVis); if (rLockCorner != null) rLockCorner.setVisibility(cornVis);
    }

    private class SidebarTouchListener implements View.OnTouchListener { 
        private int idx; private GestureDetector gd; private float sx, sy; private long st;
        public SidebarTouchListener(int i) { this.idx = i; this.gd = new GestureDetector(EdgeBarService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction(BARS[idx] + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction(BARS[idx] + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction(BARS[idx] + "_long"); doVibrate(); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; } }); } 
        @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 50 || Math.abs(dy) > 50) { long duration = System.currentTimeMillis() - st; boolean isHold = duration > 600; String dir = ""; if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? "right" : "left"; else dir = dy > 0 ? "down" : "up"; String actionName = dir + (isHold ? "_hold" : ""); if(isHold) doVibrate(); handleAction(BARS[idx] + "_" + actionName); return true; } } return true; } 
    }
    @Override public void onInterrupt() {} @Override public void onDestroy() { super.onDestroy(); try{unregisterReceiver(stateReceiver); unregisterReceiver(ipcReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); if (lLockCorner != null) wm.removeView(lLockCorner); if (rLockCorner != null) wm.removeView(rLockCorner); if (fV != null) wm.removeView(fV); }
}
