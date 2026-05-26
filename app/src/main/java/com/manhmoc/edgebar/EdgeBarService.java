package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService; import android.animation.ValueAnimator; import android.animation.AnimatorListenerAdapter; import android.animation.Animator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.*; import android.graphics.DashPathEffect; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.VibrationEffect; import android.os.Vibrator; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View[] bars = new View[5]; private View[] corners = new View[4]; 
    private View[] mBars = new View[7]; private View[] mCorners = new View[4]; // Mảng riêng cho Morse
    private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private KeyguardManager km; private SharedPreferences prefs; private Vibrator vibrator;
    
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "c", "b_c"}; private final int[] M_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.CENTER, Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"}; private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null && k.startsWith("anim_")) fV.updateStyle(); } };
    private CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() { @Override public void onTorchModeChanged(String camId, boolean enabled) { if(camId.equals(cId)) fOn = enabled; } };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { updateVisibility(); } };
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) { exec(i.getStringExtra("act")); } } };
    private BroadcastReceiver animReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { 
        if(fV != null && fV.getVisibility() == View.VISIBLE && !prefs.getBoolean("preview_spy", false)) {
            fV.updateStyle(); int style = prefs.getInt("anim_style", 0); int dur = prefs.getInt("anim_dur", 1500);
            if(style == 0) { fV.setAlpha(0f); ValueAnimator anim = ValueAnimator.ofFloat(0f, 0.8f, 0f); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue())); anim.start(); } 
            else { fV.setAlpha(1f); float p = 2 * (fV.getWidth() + fV.getHeight()); ValueAnimator anim = ValueAnimator.ofFloat(0f, -p); anim.setDuration(dur); anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue())); anim.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { fV.setAlpha(0f); } }); anim.start(); }
        }
    }};

    private class FlashView extends View { 
        private Paint p = new Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.DITHER_FLAG); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phase = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); p.setShadowLayer(15f, 0, 0, Color.WHITE); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0 && getHeight() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { 
            int[] cArr; switch(cTheme) { case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF")}; break; case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700")}; break; case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00")}; break; case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF")}; break; case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000")}; break; case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00")}; break; case "GOOGLE": cArr=new int[]{Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853")}; break; 
        case "AURORA": return new int[]{android.graphics.Color.parseColor("#00E5FF"), android.graphics.Color.parseColor("#E040FB")};
        case "ABYSS": return new int[]{android.graphics.Color.parseColor("#1A237E"), android.graphics.Color.parseColor("#000000")};
        case "FOREST": return new int[]{android.graphics.Color.parseColor("#1B5E20"), android.graphics.Color.parseColor("#00E676")};
        case "FLAME": return new int[]{android.graphics.Color.parseColor("#D50000"), android.graphics.Color.parseColor("#FFD600")};
        case "MIDNIGHT": return new int[]{android.graphics.Color.parseColor("#000000"), android.graphics.Color.parseColor("#311B92")};
        case "TROPICAL": return new int[]{android.graphics.Color.parseColor("#FF4081"), android.graphics.Color.parseColor("#FFEB3B")};
        case "CANDY": return new int[]{android.graphics.Color.parseColor("#F50057"), android.graphics.Color.parseColor("#00E5FF")};
        default: cArr=new int[]{Color.WHITE, Color.WHITE}; break; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.CLAMP)); p.setShadowLayer(15f, 0, 0, cArr[0]); 
        }
        public void setPhase(float ph) { this.phase = ph; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { 
            float off = p.getStrokeWidth()/2; RectF rect = new RectF(off, off, getWidth()-off, getHeight()-off); Path path = new Path(); path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            if(aStyle > 0) { float perim = 2 * (getWidth() + getHeight()); float len = (aStyle==1) ? perim/4f : (aStyle==2) ? perim/8f : perim/16f; float gap = (aStyle==1) ? perim*3f/4f : (aStyle==2) ? perim*3f/8f : perim*3f/16f; p.setPathEffect(new DashPathEffect(new float[]{len, gap}, phase)); } else { p.setPathEffect(null); }
            canvas.drawPath(path, p); 
        } 
    }
    
    private class CornerView extends View { 
        private Paint pShell = new Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.DITHER_FLAG), pMoon = new Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.DITHER_FLAG); private int type; private String pfx; 
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
            
            Path p = new Path(); 
            if(type==0) { p.moveTo(w-pad, 0); p.lineTo(w-pad, h-rad); p.quadTo(w-pad, h-pad, w-rad, h-pad); p.lineTo(0, h-pad); } 
            else if(type==1) { p.moveTo(pad, 0); p.lineTo(pad, h-rad); p.quadTo(pad, h-pad, rad, h-pad); p.lineTo(w, h-pad); } 
            else if(type==2) { p.moveTo(w-pad, h); p.lineTo(w-pad, rad); p.quadTo(w-pad, pad, w-rad, pad); p.lineTo(0, pad); } 
            else if(type==3) { p.moveTo(pad, h); p.lineTo(pad, rad); p.quadTo(pad, pad, rad, pad); p.lineTo(w, pad); } 
            canvas.drawPath(p, pShell); 

            float mRad = prefs.getInt(cPfx+"moon_rad", 80); float mx = (prefs.getInt(cPfx+"moon_x", 1250) - 1250) / 10f; float my = (prefs.getInt(cPfx+"moon_y", 1250) - 1250) / 10f;
            Path m = new Path();
            if(type==0) { m.moveTo(w-pad+mx, h-mRad+my); m.quadTo(w-pad+mx, h-pad+my, w-mRad+mx, h-pad+my); } 
            else if(type==1) { m.moveTo(pad+mx, h-mRad+my); m.quadTo(pad+mx, h-pad+my, mRad+mx, h-pad+my); } 
            else if(type==2) { m.moveTo(w-pad+mx, mRad+my); m.quadTo(w-pad+mx, pad+my, w-mRad+mx, pad+my); } 
            else if(type==3) { m.moveTo(pad+mx, mRad+my); m.quadTo(pad+mx, pad+my, mRad+mx, pad+my); } 
            canvas.drawPath(m, pMoon);
        } 
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); 
        try { cId = cm.getCameraIdList()[0]; cm.registerTorchCallback(torchCb, null); } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener); 
        
        IntentFilter filterState = new IntentFilter(); filterState.addAction(Intent.ACTION_SCREEN_OFF); filterState.addAction(Intent.ACTION_SCREEN_ON); filterState.addAction(Intent.ACTION_USER_PRESENT); filterState.addAction("com.manhmoc.edgebar.UPDATE_UI");
        IntentFilter filterIpc = new IntentFilter("com.manhmoc.edgebar.IPC_ACTION");
        IntentFilter filterAnim = new IntentFilter("com.manhmoc.edgebar.TEST_ANIM");

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filterState, Context.RECEIVER_NOT_EXPORTED); registerReceiver(ipcReceiver, filterIpc, Context.RECEIVER_NOT_EXPORTED); registerReceiver(animReceiver, filterAnim, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, filterState); registerReceiver(ipcReceiver, filterIpc); registerReceiver(animReceiver, filterAnim);
        }

        String cid = "eb_19_acc"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar Trợ Năng", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar đang chạy nền").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n); createFloatingBars();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { String pName = event.getPackageName() != null ? event.getPackageName().toString() : ""; String cName = event.getClassName() != null ? event.getClassName().toString() : ""; isKbd = pName.contains("inputmethod") || cName.contains("InputWindow") || cName.contains("keyboard") || cName.contains("Keyboard"); String bl = prefs.getString("blacklist", ""); isBl = !pName.isEmpty() && bl.contains(pName); updateVisibility(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); i.putExtra("isKbd", isKbd); i.putExtra("isBl", isBl); sendBroadcast(i); } }

    private void exec(String a) { if (a == null || a.equals("NONE")) return; try { switch(a) { case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break; case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break; case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break; case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break; case "FLASH": cm.setTorchMode(cId, !fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; case "QR": Intent lens = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens"); if (lens != null) { lens.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(lens); } else { Intent fb = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://lens.google.com/")); fb.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(fb); } break; 
        case "AURORA": return new int[]{android.graphics.Color.parseColor("#00E5FF"), android.graphics.Color.parseColor("#E040FB")};
        case "ABYSS": return new int[]{android.graphics.Color.parseColor("#1A237E"), android.graphics.Color.parseColor("#000000")};
        case "FOREST": return new int[]{android.graphics.Color.parseColor("#1B5E20"), android.graphics.Color.parseColor("#00E676")};
        case "FLAME": return new int[]{android.graphics.Color.parseColor("#D50000"), android.graphics.Color.parseColor("#FFD600")};
        case "MIDNIGHT": return new int[]{android.graphics.Color.parseColor("#000000"), android.graphics.Color.parseColor("#311B92")};
        case "TROPICAL": return new int[]{android.graphics.Color.parseColor("#FF4081"), android.graphics.Color.parseColor("#FFEB3B")};
        case "CANDY": return new int[]{android.graphics.Color.parseColor("#F50057"), android.graphics.Color.parseColor("#00E5FF")};
        default: if(a.startsWith("INTENT_")) fireIntent(a.split("_")[1]); break; } } catch (Exception e) {} }
    
    private void fireIntent(String idx) { try { String act = prefs.getString("i"+idx+"_act", ""); String pkg = prefs.getString("i"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("i"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("i"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("i"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("i"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("i"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private void handleAction(String prefixKey, String actionKey) { 
        String action = prefs.getString(actionKey, "NONE"); 
        if (!action.equals("NONE")) { 
            if (prefs.getBoolean(prefixKey + "_vib", true)) { doVibrate(prefs.getInt("vib_dur", 30)); }
            if (prefs.getBoolean(prefixKey + "_anim", true)) { sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName())); } 
            exec(action); 
        } 
    }
    
    private void doVibrate(int dur) { if(dur<=0) return; try { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    private void createFloatingBars() {
        fV = new FlashView(this); fV.setAlpha(0f); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}
        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams initP = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], initP); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener(i, false)); } 
        for(int i=0; i<4; i++) { corners[i] = new CornerView(this, i, "lock_"); WindowManager.LayoutParams cp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); try { wm.addView(corners[i], cp); } catch(Exception e){} corners[i].setOnTouchListener(new CornerTouchListener(i, false)); } 
        // Tạo mảng Morse
        for(int i=0; i<7; i++) { mBars[i] = new View(this); WindowManager.LayoutParams mp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); try { wm.addView(mBars[i], mp); } catch(Exception e){} mBars[i].setOnTouchListener(new SidebarTouchListener(i, true)); }
        for(int i=0; i<4; i++) { mCorners[i] = new CornerView(this, i, "morse_"); WindowManager.LayoutParams mcp = new WindowManager.LayoutParams(70, 70, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, PixelFormat.TRANSLUCENT); try { wm.addView(mCorners[i], mcp); } catch(Exception e){} mCorners[i].setOnTouchListener(new CornerTouchListener(i, true)); }
        updateVisibility();
    }

    private void updateVisibility() { 
        boolean pLock = prefs.getBoolean("preview_lock", false); boolean pMorse = prefs.getBoolean("preview_morse", false); boolean pMorseDim = prefs.getBoolean("preview_morse_dim", false); boolean pHome = prefs.getBoolean("preview_home", false); boolean pSpy = prefs.getBoolean("preview_spy", false);
        boolean isLocked = km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hide = (avoidKbd && isKbd) || isBl; 
        
        if (pHome || pSpy) {
            for(View v : bars) if(v!=null) v.setVisibility(View.GONE); for(View v : corners) if(v!=null) v.setVisibility(View.GONE);
            for(View v : mBars) if(v!=null) v.setVisibility(View.GONE); for(View v : mCorners) if(v!=null) v.setVisibility(View.GONE);
            if(fV != null) fV.setVisibility(View.GONE); return;
        }
        
        if (fV != null) fV.setVisibility(View.VISIBLE);
        
        // Ẩn/Hiện Lock Bars
        boolean showLock = pLock || (isLocked && !hide && (!pMorse || pMorseDim));
        for(int i=0; i<5; i++) { 
            if(bars[i] == null) continue; boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", i < 2); bars[i].setVisibility((en && showLock && !pMorse) ? View.VISIBLE : View.GONE); 
            if(en && showLock && !pMorse) { int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha", 50); int w = prefs.getInt("lock_"+BARS[i]+"_w", 300); int h = prefs.getInt("lock_"+BARS[i]+"_h", 60); int x = prefs.getInt("lock_"+BARS[i]+"_x", 0); int y = prefs.getInt("lock_"+BARS[i]+"_y", 0); GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; if(!prefs.getBoolean("lock_"+BARS[i]+"_block", true)) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(bars[i], p); } 
        } 
        String cPfxLock = "lock_corner_";
        for(int i=0; i<4; i++) { 
            if(corners[i] == null) continue; boolean cornEn = prefs.getBoolean(cPfxLock+CORNERS[i]+"_en", true); boolean cornInv = prefs.getBoolean(cPfxLock+CORNERS[i]+"_invis", false); boolean cornBlk = prefs.getBoolean(cPfxLock+CORNERS[i]+"_block", true); corners[i].setVisibility((cornEn && showLock && !pMorse) ? View.VISIBLE : View.GONE); 
            if(cornEn && showLock && !pMorse) { ((CornerView)corners[i]).updateProps(prefs.getInt(cPfxLock+"thick", 8), cornInv ? 0 : 200); WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams(); p.gravity = C_GRAV[i]; p.x = prefs.getInt(cPfxLock+CORNERS[i]+"_x", 0); p.y = prefs.getInt(cPfxLock+CORNERS[i]+"_y", 0); p.width = prefs.getInt(cPfxLock+CORNERS[i]+"_w", 100); p.height = prefs.getInt(cPfxLock+CORNERS[i]+"_h", 100); if(!cornBlk) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(corners[i], p); } 
        }

        // TÍNH NĂNG CHỈ HIỆN MORSE KHI BẬT NỀN ĐEN
        boolean showMorse = (pMorse && pMorseDim) || (isLocked && pMorseDim && !hide);
        for(int i=0; i<7; i++) { 
            if(mBars[i] == null) continue; boolean en = prefs.getBoolean("morse_"+M_BARS[i]+"_en", false); mBars[i].setVisibility((en && showMorse) ? View.VISIBLE : View.GONE); 
            if(en && showMorse) { int alpha = prefs.getInt("morse_"+M_BARS[i]+"_alpha", 50); int w = prefs.getInt("morse_"+M_BARS[i]+"_w", 300); int h = prefs.getInt("morse_"+M_BARS[i]+"_h", 60); int x = prefs.getInt("morse_"+M_BARS[i]+"_x", 0); int y = prefs.getInt("morse_"+M_BARS[i]+"_y", 0); GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 255, 0, 80)); gd.setCornerRadius(24f); mBars[i].setBackground(gd); WindowManager.LayoutParams p = (WindowManager.LayoutParams) mBars[i].getLayoutParams(); p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = M_GRAV[i]; if(!prefs.getBoolean("morse_"+M_BARS[i]+"_block", true)) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(mBars[i], p); } 
        }
        String cPfxMorse = "morse_corner_";
        for(int i=0; i<4; i++) { 
            if(mCorners[i] == null) continue; boolean cornEn = prefs.getBoolean(cPfxMorse+CORNERS[i]+"_en", false); boolean cornInv = prefs.getBoolean(cPfxMorse+CORNERS[i]+"_invis", false); boolean cornBlk = prefs.getBoolean(cPfxMorse+CORNERS[i]+"_block", true); mCorners[i].setVisibility((cornEn && showMorse) ? View.VISIBLE : View.GONE); 
            if(cornEn && showMorse) { ((CornerView)mCorners[i]).updateProps(prefs.getInt(cPfxMorse+"thick", 8), cornInv ? 0 : 200); WindowManager.LayoutParams p = (WindowManager.LayoutParams) mCorners[i].getLayoutParams(); p.gravity = C_GRAV[i]; p.x = prefs.getInt(cPfxMorse+CORNERS[i]+"_x", 0); p.y = prefs.getInt(cPfxMorse+CORNERS[i]+"_y", 0); p.width = prefs.getInt(cPfxMorse+CORNERS[i]+"_w", 100); p.height = prefs.getInt(cPfxMorse+CORNERS[i]+"_h", 100); if(!cornBlk) p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; wm.updateViewLayout(mCorners[i], p); } 
        }
    }

    private class CornerTouchListener implements View.OnTouchListener {
        private int idx; private float sx, sy; private long st; private boolean isMorse; 
        public CornerTouchListener(int i, boolean morse) { this.idx = i; this.isMorse = morse; }
        @Override public boolean onTouch(View v, MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 40 && Math.abs(dy) > 40) { boolean isHold = (System.currentTimeMillis() - st) > prefs.getInt("hold_dur", 600); handleAction((isMorse?"morse_corner_":"lock_corner_") + CORNERS[idx], (isMorse?"morse_corner_":"lock_corner_") + CORNERS[idx] + "_" + (isHold ? "hold" : "swipe")); return true; } } return true; }
    }

    private class SidebarTouchListener implements View.OnTouchListener { 
        private int idx; private GestureDetector gd; private float sx, sy; private long st; private boolean isMorse;
        public SidebarTouchListener(int i, boolean morse) { this.idx = i; this.isMorse = morse; this.gd = new GestureDetector(EdgeBarService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction((isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]), (isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]) + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction((isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]), (isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]) + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction((isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]), (isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]) + "_long"); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; } }); } 
        @Override public boolean onTouch(View v, MotionEvent e) { gd.onTouchEvent(e); if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { float dx = e.getRawX() - sx, dy = e.getRawY() - sy; if (Math.abs(dx) > 50 || Math.abs(dy) > 50) { long duration = System.currentTimeMillis() - st; boolean isHold = duration > prefs.getInt("hold_dur", 600); String dir = ""; if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? "right" : "left"; else dir = dy > 0 ? "down" : "up"; String actionName = dir + (isHold ? "_hold" : ""); handleAction((isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]), (isMorse?"morse_":"lock_") + (isMorse?M_BARS[idx]:BARS[idx]) + "_" + actionName); return true; } } return true; } 
    }
    @Override public void onInterrupt() {} @Override public void onDestroy() { super.onDestroy(); try{unregisterReceiver(stateReceiver); unregisterReceiver(ipcReceiver); unregisterReceiver(animReceiver); cm.unregisterTorchCallback(torchCb);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); for(int i=0; i<4; i++) if(corners[i] != null) wm.removeView(corners[i]); for(int i=0; i<7; i++) if(mBars[i] != null) wm.removeView(mBars[i]); for(int i=0; i<4; i++) if(mCorners[i] != null) wm.removeView(mCorners[i]); if (fV != null) wm.removeView(fV); }
}
