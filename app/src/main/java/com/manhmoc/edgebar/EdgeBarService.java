package com.manhmoc.edgebar;
import android.accessibilityservice.AccessibilityService;
import android.animation.Animator; import android.animation.AnimatorListenerAdapter; import android.animation.ValueAnimator;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent;
import android.content.IntentFilter; import android.content.SharedPreferences;
import android.graphics.Canvas; import android.graphics.Color; import android.graphics.DashPathEffect;
import android.graphics.LinearGradient; import android.graphics.Paint; import android.graphics.Path;
import android.graphics.PixelFormat; import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build; import android.os.Handler; import android.os.VibrationEffect; import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View;
import android.view.WindowManager; import android.view.accessibility.AccessibilityEvent;

public class EdgeBarService extends AccessibilityService {
    private WindowManager wm;
    private View[] bars = new View[5];
    private View[] corners = new View[4];
    private FlashView fV;
    private CameraManager cm; private String cId; private boolean fOn=false;
    private boolean isKbd=false, isBl=false;
    private KeyguardManager km;
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private String unlockedPackage="";

    private static final String[] BARS={"r","l","t_r","t_l","t_c"};
    private static final int[] GRAV={Gravity.BOTTOM|Gravity.RIGHT,Gravity.BOTTOM|Gravity.LEFT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT,Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private static final String[] CORNERS={"br","bl","tr","tl"};
    private static final int[] C_GRAV={Gravity.BOTTOM|Gravity.RIGHT,Gravity.BOTTOM|Gravity.LEFT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener=(p,k)->{if(k!=null){updateVisibility();if(fV!=null)fV.updateStyle();}};

    private BroadcastReceiver stateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction(); if(a==null) return;
            if("com.manhmoc.edgebar.TEST_ANIM".equals(a)) playAnim();
            else if("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS".equals(a)) unlockedPackage=i.getStringExtra("pkg");
            else if(Intent.ACTION_SCREEN_OFF.equals(a)) unlockedPackage="";
            else updateVisibility();
        }
    };
    private BroadcastReceiver ipcReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){if("com.manhmoc.edgebar.IPC_ACTION".equals(i.getAction())) exec(i.getStringExtra("act"));}
    };

    // ── FlashView ──────────────────────────────────────────────────────────
    private class FlashView extends View {
        private Paint p=new Paint(); float radius=40f; String cTheme="WHITE"; int aStyle=0; float phaseFraction=0f;
        FlashView(Context c){super(c);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setAntiAlias(true);setLayerType(LAYER_TYPE_SOFTWARE,p);updateStyle();}
        void updateStyle(){p.setAlpha(prefs.getInt("anim_alpha",255));p.setStrokeWidth(prefs.getInt("anim_thick",12));radius=prefs.getInt("anim_rad",40);cTheme=prefs.getString("anim_color","WHITE");aStyle=prefs.getInt("anim_style",0);if(getWidth()>0)applyGradient(getWidth(),getHeight());invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);applyGradient(w,h);}
        void applyGradient(int w,int h){
            int[] ca;
            switch(cTheme){
                case "NEON":      ca=new int[]{0xFFFF00FF,0xFF00FFFF,0xFFFF00FF};break;
                case "CYBERPUNK": ca=new int[]{0xFF8A2BE2,0xFFFFD700,0xFF8A2BE2};break;
                case "LAVA":      ca=new int[]{0xFFFF4500,0xFFFF8C00,0xFFFF4500};break;
                case "OCEAN":     ca=new int[]{0xFF00BFFF,0xFF1E90FF,0xFF00BFFF};break;
                case "MATRIX":    ca=new int[]{0xFF00FF00,0xFF008000,0xFF00FF00};break;
                case "SUNSET":    ca=new int[]{0xFFFF1493,0xFFFF8C00,0xFFFF1493};break;
                case "GOOGLE":    ca=new int[]{0xFFEA4335,0xFFFBBC05,0xFF34A853,0xFF4285F4,0xFFEA4335};break;
                case "AURORA":    ca=new int[]{0xFF00E5FF,0xFFB388FF,0xFFFF4081};break;
                case "ABYSS":     ca=new int[]{0xFF00E5FF,0xFF1DE9B6,0xFF2979FF};break;
                default:          ca=new int[]{0xFFFFFFFF,0xFFFFFFFF};
            }
            p.setShader(new LinearGradient(0,0,w,h,ca,null,Shader.TileMode.MIRROR));
            p.setShadowLayer(15f,0,0,ca[0]);
        }
        void setPhase(float f){phaseFraction=f;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float dw=getWidth(),dh=getHeight(); if(dw<=0||dh<=0) return;
            float off=p.getStrokeWidth()/2;
            if(aStyle>0){
                float perim=2*(dw+dh); float phase=-perim*phaseFraction;
                float seg=aStyle==1?perim/4f:aStyle==2?perim/8f:perim/12f;
                p.setPathEffect(new DashPathEffect(new float[]{seg,perim-seg},phase));
            } else p.setPathEffect(null);
            canvas.drawRoundRect(off,off,dw-off,dh-off,radius,radius,p);
        }
    }

    // ── CornerView ─────────────────────────────────────────────────────────
    private class CornerView extends View {
        private Paint pFill=new Paint(),pStroke=new Paint();
        private int type; private String prefix;
        private Handler ah=new Handler(); private boolean autoHiding=false;
        private int baseMoonAlpha,baseStrokeAlpha,hideDelay; private boolean isInv=false;
        CornerView(Context c,int t,String pfx){
            super(c);type=t;prefix=pfx;
            pFill.setStyle(Paint.Style.FILL);pFill.setAntiAlias(true);
            pStroke.setColor(Color.WHITE);pStroke.setStyle(Paint.Style.STROKE);pStroke.setAntiAlias(true);
            pStroke.setStrokeCap(Paint.Cap.ROUND);pStroke.setStrokeJoin(Paint.Join.ROUND);
        }
        void updateProps(int thick,int mA,int sA,boolean auto,int delay,boolean inv){
            pStroke.setStrokeWidth(thick);baseMoonAlpha=mA;baseStrokeAlpha=sA;autoHiding=auto;hideDelay=delay;isInv=inv;
            if(!auto){pFill.setColor(Color.argb(mA,96,125,139));pStroke.setAlpha(sA);}else triggerFlash();
            if(inv){pFill.setAlpha(0);pStroke.setAlpha(0);}
            invalidate();
        }
        void triggerFlash(){
            if(!autoHiding||isInv) return;
            ah.removeCallbacksAndMessages(null);
            pFill.setColor(Color.argb(Math.min(255,baseMoonAlpha+50),96,125,139));
            pStroke.setAlpha(Math.min(255,baseStrokeAlpha+50));invalidate();
            ah.postDelayed(()->{
                ValueAnimator va=ValueAnimator.ofFloat(1f,0f);va.setDuration(1500);
                va.addUpdateListener(a->{float v=(float)a.getAnimatedValue();pFill.setColor(Color.argb((int)(baseMoonAlpha*v),96,125,139));pStroke.setAlpha((int)(baseStrokeAlpha*v));invalidate();});
                va.start();
            },hideDelay);
        }
        @Override protected void onDraw(Canvas canvas){
            float tw=getWidth(),th=getHeight(),thick=pStroke.getStrokeWidth(),pad=thick/2;
            String ck=prefix+"corner_"+CORNERS[type]+"_";
            int shapeMode=prefs.getInt(ck+"shape",0);
            float sRad=prefs.getInt(ck+"rad",80)/1000f,mRad=prefs.getInt(ck+"moon_rad",80)/1000f;
            float sw=prefs.getInt(ck+"w",100),sh=prefs.getInt(ck+"h",100);
            float mw=prefs.getInt(ck+"moon_w",100),mh=prefs.getInt(ck+"moon_h",100);
            Path moon=new Path(),stroke=new Path();
            float sRX,sRY,sTX,sTY,sCX,sCY,mRX,mRY,mTX,mTY,mCX,mCY;
            if(type==0){sRX=tw-pad;sRY=th-pad;sTX=tw-sw+pad;sTY=th-sh+pad;sCX=sRX-(1f-sRad)*(sw*.7f);sCY=sRY-(1f-sRad)*(sh*.7f);mRX=tw;mRY=th;mTX=tw-mw;mTY=th-mh;mCX=mRX-(1f-mRad)*(mw*.7f);mCY=mRY-(1f-mRad)*(mh*.7f);}
            else if(type==1){sRX=pad;sRY=th-pad;sTX=sw-pad;sTY=th-sh+pad;sCX=sRX+(1f-sRad)*(sw*.7f);sCY=sRY-(1f-sRad)*(sh*.7f);mRX=0;mRY=th;mTX=mw;mTY=th-mh;mCX=mRX+(1f-mRad)*(mw*.7f);mCY=mRY-(1f-mRad)*(mh*.7f);}
            else if(type==2){sRX=tw-pad;sRY=pad;sTX=tw-sw+pad;sTY=sh-pad;sCX=sRX-(1f-sRad)*(sw*.7f);sCY=sRY+(1f-sRad)*(sh*.7f);mRX=tw;mRY=0;mTX=tw-mw;mTY=mh;mCX=mRX-(1f-mRad)*(mw*.7f);mCY=mRY+(1f-mRad)*(mh*.7f);}
            else{sRX=pad;sRY=pad;sTX=sw-pad;sTY=sh-pad;sCX=sRX+(1f-sRad)*(sw*.7f);sCY=sRY+(1f-sRad)*(sh*.7f);mRX=0;mRY=0;mTX=mw;mTY=mh;mCX=mRX+(1f-mRad)*(mw*.7f);mCY=mRY+(1f-mRad)*(mh*.7f);}
            if(shapeMode==1){stroke.moveTo(sRX,sRY);stroke.lineTo(sTX,sRY);}
            else if(shapeMode==2){stroke.moveTo(sRX,sRY);stroke.lineTo(sRX,sTY);}
            else{stroke.moveTo(sRX,sTY);stroke.quadTo(sCX,sCY,sTX,sRY);}
            if(type==0||type==1){moon.moveTo(mRX,mTY);moon.lineTo(mRX,mRY);moon.lineTo(mTX,mRY);moon.quadTo(mCX,mCY,mRX,mTY);}
            else{moon.moveTo(mTX,mRY);moon.lineTo(mRX,mRY);moon.lineTo(mRX,mTY);moon.quadTo(mCX,mCY,mTX,mRY);}
            moon.close();
            canvas.drawPath(stroke,pStroke);
            float mx=prefs.getInt(ck+"moon_x",1250)-1250,my=prefs.getInt(ck+"moon_y",1250)-1250;
            canvas.save();canvas.translate(mx,my);canvas.drawPath(moon,pFill);canvas.restore();
        }
    }

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        prefs=getSharedPreferences("EdgeBarPrefs",MODE_PRIVATE);
        cm=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
        vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        try{cId=cm.getCameraIdList()[0];}catch(Exception e){}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter f=new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);f.addAction("com.manhmoc.edgebar.TEST_ANIM");
        f.addAction("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");
        registerReceiver(stateReceiver,f);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(ipcReceiver,new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(ipcReceiver,new IntentFilter("com.manhmoc.edgebar.IPC_ACTION"));
        String cid="eb_19_acc";
        NotificationChannel nc=new NotificationChannel(cid,"Edge Bar đang chạy nền",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        Notification n=new Notification.Builder(this,cid).setContentTitle("Edge Bar").setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build();
        startForeground(1,n);
        createFloatingBars();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent ev){
        if(ev.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pName=ev.getPackageName()!=null?ev.getPackageName().toString():"";
        String cName=ev.getClassName()!=null?ev.getClassName().toString():"";
        isKbd=pName.contains("inputmethod")||cName.toLowerCase().contains("keyboard")||cName.contains("InputWindow");
        String bl=prefs.getString("blacklist","");
        isBl=!pName.isEmpty()&&bl.contains(pName);
        String locklist=prefs.getString("locklist","");
        boolean isLocked=false;
        if(!pName.isEmpty()&&!locklist.isEmpty())
            for(String pkg:locklist.split(",")) if(pkg.trim().equals(pName)){isLocked=true;break;}
        if(isLocked&&!pName.equals(unlockedPackage)){
            Intent i=new Intent("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");i.putExtra("pkg",pName);sendBroadcast(i);
        } else if(!pName.isEmpty()&&!pName.contains("systemui")&&!isKbd) unlockedPackage="";
        updateVisibility();
        Intent i=new Intent("com.manhmoc.edgebar.SYNC_STATE");i.putExtra("isKbd",isKbd);i.putExtra("isBl",isBl);sendBroadcast(i);
    }

    private void exec(String a){
        if(a==null||a.equals("NONE")) return;
        try{
            switch(a){
                case "MACRO_1":case "MACRO_2":case "MACRO_3":case "MACRO_4":case "MACRO_5":
                    Intent iM=new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
                    iM.putExtra("services",prefs.getString(a.toLowerCase()+"_svcs",""));sendBroadcast(iM);break;
                case "TOGGLE_MORSE": sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_MORSE"));break;
                case "BACK": performGlobalAction(GLOBAL_ACTION_BACK);break;
                case "HOME": performGlobalAction(GLOBAL_ACTION_HOME);break;
                case "RECENTS": performGlobalAction(GLOBAL_ACTION_RECENTS);break;
                case "SCREEN_OFF": performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);break;
                case "POWER_DIALOG": performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);break;
                case "SCREENSHOT": performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);break;
                case "NOTIFICATIONS": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);break;
                case "FLASH": fOn=!fOn;cm.setTorchMode(cId,fOn);break;
                case "CAMERA":
                    Intent ic=new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
                    ic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(ic);break;
                case "VOLUME":
                    ((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);break;
                case "YTDL_DOWNLOAD":
                    try{
                        android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                        if(cb.hasPrimaryClip()&&cb.getPrimaryClip().getItemCount()>0){
                            CharSequence txt=cb.getPrimaryClip().getItemAt(0).getText();
                            if(txt!=null&&txt.toString().startsWith("http")){
                                Intent y=new Intent(Intent.ACTION_SEND);y.setType("text/plain");
                                y.putExtra(Intent.EXTRA_TEXT,txt.toString());y.setPackage("com.deniscerri.ytdlnis");
                                y.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(y);
                            }
                        }
                    }catch(Exception ex){}break;
                default:
                    if(a.startsWith("INTENT_")) fireIntent(a.substring(7));break;
            }
        }catch(Exception e){}
    }

    private void fireIntent(String idx){
        try{
            String pkg=prefs.getString("intent_"+idx+"_pkg","");
            String cls=prefs.getString("intent_"+idx+"_cls","");
            String act=prefs.getString("intent_"+idx+"_act","");
            if(!pkg.isEmpty()&&!cls.isEmpty()){
                Intent i=new Intent();i.setClassName(pkg,cls);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
            } else if(!act.isEmpty()){
                Intent i=new Intent(act);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);
            }
        }catch(Exception e){}
    }

    private void playAnim(){
        if(fV==null) return;
        WindowManager.LayoutParams fp=(WindowManager.LayoutParams)fV.getLayoutParams();
        fp.width=WindowManager.LayoutParams.MATCH_PARENT;fp.height=WindowManager.LayoutParams.MATCH_PARENT;
        wm.updateViewLayout(fV,fp);fV.setVisibility(View.VISIBLE);
        fV.post(()->{
            int style=prefs.getInt("anim_style",0),dur=prefs.getInt("anim_dur",1500);
            ValueAnimator anim;
            if(style==0){anim=ValueAnimator.ofFloat(0f,1f,0f);anim.addUpdateListener(a->fV.setAlpha((float)a.getAnimatedValue()));}
            else{fV.setAlpha(1f);anim=ValueAnimator.ofFloat(0f,1f);anim.addUpdateListener(a->fV.setPhase((float)a.getAnimatedValue()));}
            anim.setDuration(dur);
            anim.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){fV.setAlpha(0f);fV.setVisibility(View.GONE);fp.width=0;fp.height=0;wm.updateViewLayout(fV,fp);}});
            anim.start();
        });
    }

    private void handleAction(String key){
        String action=prefs.getString(key,"NONE");
        if(action.equals("NONE")||!prefs.getBoolean(key+"_on",true)) return;
        if(prefs.getBoolean(key+"_vib",true)) doVibrate(prefs.getInt("vib_dur",30));
        if(prefs.getBoolean(key+"_anim",true)) playAnim();
        for(String a:action.split(",")) exec(a.trim());
    }

    private void doVibrate(int dur){
        if(dur<=0) return;
        try{if(Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(dur,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(dur);}catch(Exception e){}
    }

    private void createFloatingBars(){
        fV=new FlashView(this);fV.setAlpha(0f);fV.setVisibility(View.GONE);
        WindowManager.LayoutParams fp=new WindowManager.LayoutParams(0,0,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        try{wm.addView(fV,fp);}catch(Exception e){}
        for(int i=0;i<5;i++){
            bars[i]=new View(this);
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
            try{wm.addView(bars[i],p);}catch(Exception e){}
            bars[i].setOnTouchListener(new SidebarTouchListener("lock_"+BARS[i],null));
        }
        for(int i=0;i<4;i++){
            corners[i]=new CornerView(this,i,"lock_");
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,0,PixelFormat.TRANSLUCENT);
            try{wm.addView(corners[i],p);}catch(Exception e){}
            corners[i].setOnTouchListener(new SidebarTouchListener("lock_corner_"+CORNERS[i],corners[i]));
        }
        updateVisibility();
    }

    private void updateVisibility(){
        boolean isPreview=prefs.getBoolean("preview_lock",false);
        boolean locked=km.isKeyguardLocked()||isPreview;
        boolean hide=(prefs.getBoolean("avoid_kbd",true)&&isKbd)||isBl;
        if(hide&&fV!=null) fV.setVisibility(View.GONE);
        for(int i=0;i<5;i++){if(bars[i]==null)continue;
            boolean en=prefs.getBoolean("lock_"+BARS[i]+"_en",false);
            bars[i].setVisibility(en&&locked&&!hide?View.VISIBLE:View.GONE);
            if(en&&locked){
                int al=prefs.getInt("lock_"+BARS[i]+"_alpha",50);
                int w=prefs.getInt("lock_"+BARS[i]+"_w",300),h=prefs.getInt("lock_"+BARS[i]+"_h",60);
                int x=prefs.getInt("lock_"+BARS[i]+"_x",0),y=prefs.getInt("lock_"+BARS[i]+"_y",0);
                GradientDrawable gd=new GradientDrawable();gd.setColor(Color.argb(al,96,125,139));gd.setCornerRadius(24f);
                bars[i].setBackground(gd);
                int pri=prefs.getInt("lock_"+BARS[i]+"_pri_mode",0);
                int bf=WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if(pri==1) bf|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else bf|=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                WindowManager.LayoutParams p=(WindowManager.LayoutParams)bars[i].getLayoutParams();
                p.flags=bf;p.width=w;p.height=h;p.x=x;p.y=y;p.gravity=GRAV[i];
                wm.updateViewLayout(bars[i],p);
            }
        }
        for(int i=0;i<4;i++){if(corners[i]==null)continue;
            boolean en=prefs.getBoolean("lock_corner_"+CORNERS[i]+"_en",false);
            corners[i].setVisibility(en&&locked&&!hide?View.VISIBLE:View.GONE);
            if(en&&locked){
                String ck="lock_corner_"+CORNERS[i]+"_";
                int mA=prefs.getInt("lock_corner_moon_alpha",100),sA=prefs.getInt("lock_corner_stroke_alpha",200);
                int hd=prefs.getInt("lock_corner_hide_dur",2500),vm=prefs.getInt(ck+"vis_mode",0);
                ((CornerView)corners[i]).updateProps(prefs.getInt("lock_corner_thick",8),mA,sA,vm==1,hd,vm==2);
                int pri=prefs.getInt(ck+"pri_mode",0);
                int bf=WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                if(pri==1) bf|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else bf|=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
                WindowManager.LayoutParams p=(WindowManager.LayoutParams)corners[i].getLayoutParams();
                p.flags=bf;p.gravity=C_GRAV[i];
                int wp=prefs.getInt(ck+"w",100),hp=prefs.getInt(ck+"h",100);
                int mxO=Math.abs(prefs.getInt(ck+"moon_x",1250)-1250),myO=Math.abs(prefs.getInt(ck+"moon_y",1250)-1250);
                p.width=Math.max(10,Math.max(wp,prefs.getInt(ck+"moon_w",100))+mxO);
                p.height=Math.max(10,Math.max(hp,prefs.getInt(ck+"moon_h",100))+myO);
                p.x=prefs.getInt(ck+"x",0);p.y=prefs.getInt(ck+"y",0);
                wm.updateViewLayout(corners[i],p);
            }
        }
    }

    private class SidebarTouchListener implements View.OnTouchListener {
        private String keyBase; private View myView; private GestureDetector gd; private float sx,sy; private long st;
        SidebarTouchListener(String k,View v){keyBase=k;myView=v;
            gd=new GestureDetector(EdgeBarService.this,new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onSingleTapConfirmed(MotionEvent e){handleAction(keyBase+"_tap");return true;}
                @Override public boolean onDoubleTap(MotionEvent e){handleAction(keyBase+"_dtap");return true;}
                @Override public void onLongPress(MotionEvent e){handleAction(keyBase+"_long");}
            });
        }
        @Override public boolean onTouch(View v,MotionEvent e){
            if(myView instanceof CornerView) ((CornerView)myView).triggerFlash();
            gd.onTouchEvent(e);
            if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();st=System.currentTimeMillis();}
            else if(e.getAction()==MotionEvent.ACTION_UP){
                float dx=e.getRawX()-sx,dy=e.getRawY()-sy;
                if(Math.abs(dx)>50||Math.abs(dy)>50){
                    boolean hold=System.currentTimeMillis()-st>prefs.getInt("hold_dur",600);
                    String name;
                    if(myView instanceof CornerView&&Math.abs(dx)>40&&Math.abs(dy)>40) name="diag"+(hold?"_hold":"");
                    else{if(Math.abs(dx)>Math.abs(dy)) name=dx>0?"right":"left";else name=dy>0?"down":"up";if(hold) name+="_hold";}
                    handleAction(keyBase+"_"+name);return true;
                }
            }
            return true;
        }
    }

    @Override public void onInterrupt(){}
    @Override public void onDestroy(){
        super.onDestroy();
        try{unregisterReceiver(stateReceiver);unregisterReceiver(ipcReceiver);}catch(Exception e){}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        for(int i=0;i<5;i++) if(bars[i]!=null)try{wm.removeView(bars[i]);}catch(Exception e){}
        for(int i=0;i<4;i++) if(corners[i]!=null)try{wm.removeView(corners[i]);}catch(Exception e){}
        if(fV!=null)try{wm.removeView(fV);}catch(Exception e){}
    }
}
