package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService; import android.animation.ValueAnimator; import android.animation.AnimatorListenerAdapter; import android.animation.Animator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.KeyguardManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.Path; import android.graphics.PixelFormat; import android.graphics.LinearGradient; import android.graphics.Shader; import android.graphics.DashPathEffect; import android.graphics.drawable.GradientDrawable; import android.hardware.camera2.CameraManager; import android.media.AudioManager; import android.os.Build; import android.os.Handler; import android.os.VibrationEffect; import android.os.Vibrator; import android.provider.MediaStore; import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm; private View[] bars = new View[5]; private View[] corners = new View[4]; private FlashView fV; private CameraManager cm; private String cId; private boolean fOn = false, isKbd = false, isBl = false; private KeyguardManager km; private SharedPreferences prefs; private Vibrator vibrator;
    private final String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private final int[] GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT, Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private final String[] CORNERS = {"br", "bl", "tr", "tl"}; private final int[] C_GRAV = {Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT};
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, k) -> { if(k != null) { updateVisibility(); if(fV != null) fV.updateStyle(); } };
    private BroadcastReceiver stateReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.TEST_ANIM".equals(i.getAction())) { playAnim(); } else updateVisibility(); } };
    private BroadcastReceiver ipcReceiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) { exec(i.getStringExtra("act")); } } };

    private class FlashView extends View { 
        private Paint p = new Paint(); float radius = 40f; String cTheme = "WHITE"; int aStyle = 0; private float phaseFraction = 0f;
        public FlashView(Context c) { super(c); p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setAntiAlias(true); setLayerType(LAYER_TYPE_SOFTWARE, p); updateStyle(); } 
        public void updateStyle() { p.setAlpha(prefs.getInt("anim_alpha", 255)); p.setStrokeWidth(prefs.getInt("anim_thick", 12)); radius = prefs.getInt("anim_rad", 40); cTheme = prefs.getString("anim_color", "WHITE"); aStyle = prefs.getInt("anim_style", 0); if(getWidth() > 0) applyGradient(getWidth(), getHeight()); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); applyGradient(w, h); }
        private void applyGradient(int w, int h) { 
            int[] cArr; switch(cTheme) { 
                case "NEON": cArr=new int[]{Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#FF00FF")}; break; 
                case "CYBERPUNK": cArr=new int[]{Color.parseColor("#8A2BE2"), Color.parseColor("#FFD700"), Color.parseColor("#8A2BE2")}; break; 
                case "LAVA": cArr=new int[]{Color.parseColor("#FF4500"), Color.parseColor("#FF8C00"), Color.parseColor("#FF4500")}; break; 
                case "OCEAN": cArr=new int[]{Color.parseColor("#00BFFF"), Color.parseColor("#1E90FF"), Color.parseColor("#00BFFF")}; break; 
                case "MATRIX": cArr=new int[]{Color.parseColor("#00FF00"), Color.parseColor("#008000"), Color.parseColor("#00FF00")}; break; 
                case "SUNSET": cArr=new int[]{Color.parseColor("#FF1493"), Color.parseColor("#FF8C00"), Color.parseColor("#FF1493")}; break; 
                case "GOOGLE": cArr=new int[]{Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"), Color.parseColor("#4285F4"), Color.parseColor("#EA4335")}; break; 
                case "AURORA": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#B388FF"), Color.parseColor("#FF4081")}; break;
                case "ABYSS": cArr=new int[]{Color.parseColor("#00E5FF"), Color.parseColor("#1DE9B6"), Color.parseColor("#2979FF")}; break;
                case "COSMIC": cArr=new int[]{Color.parseColor("#4A148C"), Color.parseColor("#E91E63"), Color.parseColor("#FFD700")}; break;
                case "FOREST": cArr=new int[]{Color.parseColor("#1B5E20"), Color.parseColor("#4CAF50"), Color.parseColor("#FFEB3B")}; break;
                case "FLAME": cArr=new int[]{Color.parseColor("#B71C1C"), Color.parseColor("#FF9800"), Color.parseColor("#FFEB3B")}; break;
                case "MIDNIGHT": cArr=new int[]{Color.parseColor("#1A237E"), Color.parseColor("#7B1FA2"), Color.parseColor("#03A9F4")}; break;
                case "TROPICAL": cArr=new int[]{Color.parseColor("#00695C"), Color.parseColor("#8BC34A"), Color.parseColor("#FF9800")}; break;
                case "CANDY": cArr=new int[]{Color.parseColor("#F06292"), Color.parseColor("#4DD0E1"), Color.parseColor("#FFF176")}; break;
                default: cArr=new int[]{Color.WHITE, Color.WHITE}; break; } 
            p.setShader(new LinearGradient(0, 0, w, h, cArr, null, Shader.TileMode.MIRROR)); p.setShadowLayer(15f, 0, 0, cArr[0]); 
        }
        public void setPhase(float fraction) { this.phaseFraction = fraction; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { 
            float drawW = getWidth(); float drawH = getHeight();
            if(drawW <= 0 || drawH <= 0) return;
            float off = p.getStrokeWidth()/2; 
            float left = off; float top = off;
            float right = drawW - off; float bottom = drawH - off;
            p.setStrokeCap(Paint.Cap.ROUND);
            if(aStyle > 0) { 
                float perim = 2 * (drawW + drawH); 
                float currentPhase = -perim * phaseFraction; 
                if (aStyle == 1) p.setPathEffect(new DashPathEffect(new float[]{perim/4f, 3*perim/4f}, currentPhase)); 
                else if (aStyle == 2) p.setPathEffect(new DashPathEffect(new float[]{perim/8f, 3*perim/8f}, currentPhase)); 
                else if (aStyle == 3) p.setPathEffect(new DashPathEffect(new float[]{perim/12f, 3*perim/12f}, currentPhase)); 
            } else { p.setPathEffect(null); }
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, p); 
        } 
    }
    
    private class CornerView extends View { 
        private Paint pFill, pStroke; private int type; 
        private Handler autoHideHandler = new Handler(); private boolean isAutoHiding = false; private int baseMoonAlpha, baseStrokeAlpha, hideDelay;
        private boolean isInv = false;
        
        public CornerView(Context c, int type) { super(c); this.type = type; 
            pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true); 
            pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE); pStroke.setAntiAlias(true); 
            pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        } 
        
        public void updateProps(int thick, int moonAlpha, int strokeAlpha, boolean autoHide, int delay, boolean inv) { 
            pStroke.setStrokeWidth(thick); 
            this.baseMoonAlpha = moonAlpha; this.baseStrokeAlpha = strokeAlpha; this.isAutoHiding = autoHide; this.hideDelay = delay; this.isInv = inv;
            if(!autoHide) { pFill.setColor(Color.argb(moonAlpha, 96, 125, 139)); pStroke.setAlpha(strokeAlpha); }
            else triggerFlash(); 
            if(inv) { pFill.setAlpha(0); pStroke.setAlpha(0); }
            invalidate(); 
        }
        
        public void triggerFlash() {
            if(!isAutoHiding || isInv) return; 
            autoHideHandler.removeCallbacksAndMessages(null);
            pFill.setColor(Color.argb(Math.min(255, baseMoonAlpha + 50), 96, 125, 139)); pStroke.setAlpha(Math.min(255, baseStrokeAlpha + 50)); invalidate();
            autoHideHandler.postDelayed(() -> {
                ValueAnimator a = ValueAnimator.ofFloat(1f, 0f); a.setDuration(1500);
                a.addUpdateListener(anim -> { float val = (float)anim.getAnimatedValue(); pFill.setColor(Color.argb((int)(baseMoonAlpha * val), 96, 125, 139)); pStroke.setAlpha((int)(baseStrokeAlpha * val)); invalidate(); });
                a.start();
            }, hideDelay); 
        }

        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); 
            float w = getWidth(), h = getHeight(), thick = pStroke.getStrokeWidth(); float pad = thick/2;
            int shapeMode = prefs.getInt("lock_corner_"+CORNERS[type]+"_shape", 0); 
            float sRad = prefs.getInt("lock_corner_"+CORNERS[type]+"_rad", 80) / 1000f; 
            float mw = prefs.getInt("lock_corner_"+CORNERS[type]+"_moon_w", 100); 
            float mh = prefs.getInt("lock_corner_"+CORNERS[type]+"_moon_h", 100);
            
            Path moonPath = new Path(); Path strokePath = new Path();
            float sStartX=0, sStartY=0, sEndX=0, sEndY=0, sCtrlX=0, sCtrlY=0;
            if(type==0) { sStartX = w-pad; sStartY = pad; sEndX = pad; sEndY = h-pad; sCtrlX = w-pad - (1f - sRad)*(w*0.7f); sCtrlY = h-pad - (1f - sRad)*(h*0.7f); } 
            else if(type==1) { sStartX = pad; sStartY = pad; sEndX = w-pad; sEndY = h-pad; sCtrlX = pad + (1f - sRad)*(w*0.7f); sCtrlY = h-pad - (1f - sRad)*(h*0.7f); } 
            else if(type==2) { sStartX = w-pad; sStartY = h-pad; sEndX = pad; sEndY = pad; sCtrlX = w-pad - (1f - sRad)*(w*0.7f); sCtrlY = pad + (1f - sRad)*(h*0.7f); } 
            else if(type==3) { sStartX = pad; sStartY = h-pad; sEndX = w-pad; sEndY = pad; sCtrlX = pad + (1f - sRad)*(w*0.7f); sCtrlY = pad + (1f - sRad)*(h*0.7f); } 
            
            if(shapeMode == 1) { sStartY = (type==0||type==1) ? h-pad : pad; sEndY = sStartY; sStartX = pad; sEndX = w-pad; strokePath.moveTo(sStartX, sStartY); strokePath.lineTo(sEndX, sEndY); }
            else if(shapeMode == 2) { sStartX = (type==0||type==2) ? w-pad : pad; sEndX = sStartX; sStartY = pad; sEndY = h-pad; strokePath.moveTo(sStartX, sStartY); strokePath.lineTo(sEndX, sEndY); }
            else { strokePath.moveTo(sStartX, sStartY); strokePath.quadTo(sCtrlX, sCtrlY, sEndX, sEndY); }

            if(type==0) { moonPath.moveTo(w-pad, pad); moonPath.lineTo(w-mw, pad); moonPath.lineTo(w-pad, mh); moonPath.close(); }
            else if(type==1) { moonPath.moveTo(pad, pad); moonPath.lineTo(mw, pad); moonPath.lineTo(pad, mh); moonPath.close(); }
            else if(type==2) { moonPath.moveTo(w-pad, h-pad); moonPath.lineTo(w-mw, h-pad); moonPath.lineTo(w-pad, h-mh); moonPath.close(); }
            else if(type==3) { moonPath.moveTo(pad, h-pad); moonPath.lineTo(mw, h-pad); moonPath.lineTo(pad, h-mh); moonPath.close(); }

            canvas.drawPath(moonPath, pFill); canvas.drawPath(strokePath, pStroke); 
        } 
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); wm = (WindowManager) getSystemService(WINDOW_SERVICE); km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); try { cId = cm.getCameraIdList()[0]; } catch (Exception e) {}
        prefs.registerOnSharedPreferenceChangeListener(prefListener); IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_SCREEN_ON); filter.addAction(Intent.ACTION_USER_PRESENT); filter.addAction("com.manhmoc.edgebar.TEST_ANIM"); registerReceiver(stateReceiver, filter);
        if(Build.VERSION.SDK_INT >= 33) registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(ipcReceiver, new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));
        String cid = "eb_19_acc"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar đang chạy nền", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); Notification n = new Notification.Builder(this, cid).setContentTitle("Edge Bar").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build(); startForeground(1, n); createFloatingBars();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { String pName = event.getPackageName() != null ? event.getPackageName().toString() : ""; String cName = event.getClassName() != null ? event.getClassName().toString() : ""; isKbd = pName.contains("inputmethod") || cName.contains("InputWindow") || cName.contains("keyboard") || cName.contains("Keyboard"); String bl = prefs.getString("blacklist", ""); isBl = !pName.isEmpty() && bl.contains(pName); updateVisibility(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); i.putExtra("isKbd", isKbd); i.putExtra("isBl", isBl); sendBroadcast(i); } }

    private void exec(String a) { if (a == null || a.equals("NONE")) return; try { switch(a) { 
        case "BACK": performGlobalAction(GLOBAL_ACTION_BACK); break; case "HOME": performGlobalAction(GLOBAL_ACTION_HOME); break; case "RECENTS": performGlobalAction(GLOBAL_ACTION_RECENTS); break; case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); break; case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG); break; case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT); break; case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break; case "FLASH": fOn = !fOn; cm.setTorchMode(cId, fOn); break; case "CAMERA": Intent c = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE); c.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(c); break; case "VOLUME": ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI); break; 
        case "YTDL_DOWNLOAD": try { android.content.ClipboardManager cb = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) { CharSequence data = cb.getPrimaryClip().getItemAt(0).getText(); if (data != null && (data.toString().startsWith("http://") || data.toString().startsWith("https://"))) { Intent ytdl = new Intent(Intent.ACTION_SEND); ytdl.setType("text/plain"); ytdl.putExtra(Intent.EXTRA_TEXT, data.toString()); ytdl.setPackage("com.deniscerri.ytdl"); ytdl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(ytdl); } } } catch (Exception e) {} break;
        default: if(a.startsWith("INTENT_")) fireIntent(a.split("_")[1]); break; } } catch (Exception e) {} }
    private void fireIntent(String idx) { try { String act = prefs.getString("intent_"+idx+"_act", ""); String pkg = prefs.getString("intent_"+idx+"_pkg", ""); Intent i; if (act.isEmpty() && !pkg.isEmpty()) { i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) return; } else { i = new Intent(act); if(!pkg.isEmpty()) i.setPackage(pkg); String cls = prefs.getString("intent_"+idx+"_cls", ""); if(!pkg.isEmpty() && !cls.isEmpty()) i.setComponent(new android.content.ComponentName(pkg, cls)); String data = prefs.getString("intent_"+idx+"_data", ""); if(!data.isEmpty()) i.setData(android.net.Uri.parse(data)); String cat = prefs.getString("intent_"+idx+"_cat", ""); if(!cat.isEmpty()) i.addCategory(cat); String flg = prefs.getString("intent_"+idx+"_flags", ""); if(!flg.isEmpty()) i.addFlags(Integer.parseInt(flg)); } if(prefs.getBoolean("intent_"+idx+"_br", true) && !act.isEmpty()) { sendBroadcast(i); } else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } } catch (Exception e) {} }
    
    private void playAnim() {
        WindowManager.LayoutParams fp = (WindowManager.LayoutParams) fV.getLayoutParams(); fp.width = WindowManager.LayoutParams.MATCH_PARENT; fp.height = WindowManager.LayoutParams.MATCH_PARENT; wm.updateViewLayout(fV, fp);
        fV.setVisibility(View.VISIBLE);
        fV.post(() -> {
            int style = prefs.getInt("anim_style", 0); int dur = prefs.getInt("anim_dur", 1500); 
            ValueAnimator anim;
            if(style == 0) { anim = ValueAnimator.ofFloat(0f, 1f, 0f); anim.addUpdateListener(a -> fV.setAlpha((float)a.getAnimatedValue())); } 
            else { fV.setAlpha(1f); anim = ValueAnimator.ofFloat(0f, 1f); anim.addUpdateListener(a -> fV.setPhase((float)a.getAnimatedValue())); } 
            anim.setDuration(dur);
            anim.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { fV.setAlpha(0f); fV.setVisibility(View.GONE); fp.width = 0; fp.height = 0; wm.updateViewLayout(fV, fp); } }); 
            anim.start();
        });
    }

    private void handleAction(String key) { String action = prefs.getString(key, "NONE"); if (!action.equals("NONE")) { if (prefs.getBoolean(key + "_vib", true)) { doVibrate(prefs.getInt("vib_dur", 30)); } if (prefs.getBoolean(key + "_anim", true)) { playAnim(); } exec(action); } }
    private void doVibrate(int dur) { if(dur<=0) return; try { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(dur); } catch(Exception e){} }

    private void createFloatingBars() {
        fV = new FlashView(this); fV.setAlpha(0f); fV.setVisibility(View.GONE); WindowManager.LayoutParams fp = new WindowManager.LayoutParams(0, 0, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT); try { wm.addView(fV, fp); } catch(Exception e){}
        for(int i=0; i<5; i++) { bars[i] = new View(this); WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 0, PixelFormat.TRANSLUCENT); try { wm.addView(bars[i], p); } catch(Exception e){} bars[i].setOnTouchListener(new SidebarTouchListener("lock_" + BARS[i], null)); } 
        for(int i=0; i<4; i++) { corners[i] = new CornerView(this, i); WindowManager.LayoutParams p = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, 0, PixelFormat.TRANSLUCENT); try { wm.addView(corners[i], p); } catch(Exception e){} corners[i].setOnTouchListener(new SidebarTouchListener("lock_corner_" + CORNERS[i], corners[i])); } updateVisibility();
    }

    private void updateVisibility() { 
        boolean isLocked = km.isKeyguardLocked(); boolean avoidKbd = prefs.getBoolean("avoid_kbd", true); boolean hide = (avoidKbd && isKbd) || isBl; 
        if(hide && fV != null) fV.setVisibility(View.GONE);
        for(int i=0; i<5; i++) { if(bars[i] == null) continue; boolean en = prefs.getBoolean("lock_"+BARS[i]+"_en", i < 2); bars[i].setVisibility((en && isLocked && !hide) ? View.VISIBLE : View.GONE); if(en && isLocked) { 
            int alpha = prefs.getInt("lock_"+BARS[i]+"_alpha", 50); int w = prefs.getInt("lock_"+BARS[i]+"_w", 300); int h = prefs.getInt("lock_"+BARS[i]+"_h", 60); int x = prefs.getInt("lock_"+BARS[i]+"_x", 0); int y = prefs.getInt("lock_"+BARS[i]+"_y", 0); 
            GradientDrawable gd = new GradientDrawable(); gd.setColor(Color.argb(alpha, 96, 125, 139)); gd.setCornerRadius(24f); bars[i].setBackground(gd); 
            int priMode = prefs.getInt("lock_"+BARS[i]+"_pri_mode", 0);
            int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; 
            if(priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH); 
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) bars[i].getLayoutParams(); p.flags = baseFlags; p.width = w; p.height = h; p.x = x; p.y = y; p.gravity = GRAV[i]; wm.updateViewLayout(bars[i], p); } 
        } 
        
        for(int i=0; i<4; i++) { 
            if(corners[i] == null) continue; boolean cornEn = prefs.getBoolean("lock_corner_"+CORNERS[i]+"_en", true); corners[i].setVisibility((cornEn && isLocked && !hide) ? View.VISIBLE : View.GONE); if(cornEn && isLocked) { 
                int moonAlpha = prefs.getInt("lock_corner_moon_alpha", 100); int strokeAlpha = prefs.getInt("lock_corner_stroke_alpha", 200); int hideDelay = prefs.getInt("lock_corner_hide_dur", 2500); 
                int visMode = prefs.getInt("lock_corner_"+CORNERS[i]+"_vis_mode", 0);
                boolean isAuto = (visMode == 1); boolean isInv = (visMode == 2);
                ((CornerView)corners[i]).updateProps(prefs.getInt("lock_corner_thick", 8), moonAlpha, strokeAlpha, isAuto, hideDelay, isInv); 
                
                int priMode = prefs.getInt("lock_corner_"+CORNERS[i]+"_pri_mode", 0);
                int baseFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; 
                if(priMode == 1) baseFlags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; else baseFlags |= (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH); 
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) corners[i].getLayoutParams(); p.flags = baseFlags; p.gravity = C_GRAV[i]; 
                
                int widthPref = prefs.getInt("lock_corner_"+CORNERS[i]+"_w", 100); int heightPref = prefs.getInt("lock_corner_"+CORNERS[i]+"_h", 100);
                p.width = Math.max(10, widthPref); p.height = Math.max(10, heightPref);
                p.x = prefs.getInt("lock_corner_"+CORNERS[i]+"_x", 0); p.y = prefs.getInt("lock_corner_"+CORNERS[i]+"_y", 0); 
                wm.updateViewLayout(corners[i], p); } 
        }
    }

    private class SidebarTouchListener implements View.OnTouchListener { 
        private String prefKeyBase; private View myView; private GestureDetector gd; private float sx, sy; private long st;
        public SidebarTouchListener(String keyBase, View v) { this.prefKeyBase = keyBase; this.myView = v; this.gd = new GestureDetector(EdgeBarService.this, new GestureDetector.SimpleOnGestureListener() { @Override public boolean onSingleTapConfirmed(MotionEvent e) { handleAction(prefKeyBase + "_tap"); return true; } @Override public boolean onDoubleTap(MotionEvent e) { handleAction(prefKeyBase + "_dtap"); return true; } @Override public void onLongPress(MotionEvent e) { handleAction(prefKeyBase + "_long"); } @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) { return false; } }); } 
        @Override public boolean onTouch(View v, MotionEvent e) { 
            if(myView != null && myView instanceof CornerView) ((CornerView)myView).triggerFlash();
            gd.onTouchEvent(e); if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); st = System.currentTimeMillis(); } else if (e.getAction() == MotionEvent.ACTION_UP) { 
                float dx = e.getRawX() - sx, dy = e.getRawY() - sy; 
                if (Math.abs(dx) > 50 || Math.abs(dy) > 50) { 
                    long duration = System.currentTimeMillis() - st; boolean isHold = duration > prefs.getInt("hold_dur", 600); String actionName = ""; 
                    if (myView instanceof CornerView && Math.abs(dx) > 40 && Math.abs(dy) > 40) { actionName = "diag" + (isHold ? "_hold" : ""); } 
                    else { if (Math.abs(dx) > Math.abs(dy)) actionName = dx > 0 ? "right" : "left"; else actionName = dy > 0 ? "down" : "up"; if (isHold) actionName += "_hold"; }
                    handleAction(prefKeyBase + "_" + actionName); return true; 
                } 
            } return true; } 
    }
    @Override public void onInterrupt() {} @Override public void onDestroy() { super.onDestroy(); try{unregisterReceiver(stateReceiver); unregisterReceiver(ipcReceiver);}catch(Exception e){} prefs.unregisterOnSharedPreferenceChangeListener(prefListener); for(int i=0; i<5; i++) if(bars[i] != null) wm.removeView(bars[i]); for(int i=0; i<4; i++) if(corners[i] != null) wm.removeView(corners[i]); if (fV != null) wm.removeView(fV); }
}
