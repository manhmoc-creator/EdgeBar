package com.manhmoc.edgebar;
import android.animation.Animator; import android.animation.AnimatorListenerAdapter; import android.animation.ValueAnimator;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager;
import android.app.KeyguardManager; import android.app.Service;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent;
import android.content.IntentFilter; import android.content.SharedPreferences;
import android.graphics.Canvas; import android.graphics.Color; import android.graphics.DashPathEffect;
import android.graphics.LinearGradient; import android.graphics.Paint; import android.graphics.Path;
import android.graphics.PixelFormat; import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build; import android.os.Handler; import android.os.IBinder;
import android.os.VibrationEffect; import android.os.Vibrator;
import android.provider.MediaStore; import android.provider.Settings;
import android.view.GestureDetector; import android.view.Gravity; import android.view.MotionEvent; import android.view.View;
import android.view.WindowManager; import android.widget.RelativeLayout; import android.widget.TextView;

public class HomescreenService extends Service {
    public static boolean isRunning=false;
    private WindowManager wm;
    private View[] bars=new View[5]; private View[] corners=new View[4];
    private View[] mBars=new View[8]; private View[] mCorners=new View[4];
    private RelativeLayout morseContainer; private TextView tvMorse;
    private FlashView fV;
    private CameraManager cm; private String cId; private boolean fOn=false;
    private boolean isKbd=false,isBl=false;
    private SharedPreferences prefs; private KeyguardManager km; private Vibrator vibrator;
    private boolean isMorseLock=false,isRecording=false;
    private String morseAttempt=""; private int failCount=0; private String lockedPkg="";
    private Handler dotHandler=new Handler();

    private static final String[] BARS={"r","l","t_r","t_l","t_c"};
    private static final int[] GRAV={Gravity.BOTTOM|Gravity.RIGHT,Gravity.BOTTOM|Gravity.LEFT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT,Gravity.TOP|Gravity.CENTER_HORIZONTAL};
    private static final String[] M_BARS={"r","l","t_r","t_l","t_c","m_b_c","m_mid_t","m_mid_b"};
    private static final int[] M_GRAV={Gravity.BOTTOM|Gravity.RIGHT,Gravity.BOTTOM|Gravity.LEFT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT,Gravity.TOP|Gravity.CENTER_HORIZONTAL,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,Gravity.CENTER,Gravity.CENTER};
    private static final String[] CORNERS={"br","bl","tr","tl"};
    private static final int[] C_GRAV={Gravity.BOTTOM|Gravity.RIGHT,Gravity.BOTTOM|Gravity.LEFT,Gravity.TOP|Gravity.RIGHT,Gravity.TOP|Gravity.LEFT};

    private SharedPreferences.OnSharedPreferenceChangeListener prefListener=(p,k)->{if(k!=null){updateVisibility();if(fV!=null)fV.updateStyle();}};

    private BroadcastReceiver syncReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String a=i.getAction();if(a==null)return;
            switch(a){
                case "com.manhmoc.edgebar.SYNC_STATE":
                    isKbd=i.getBooleanExtra("isKbd",false);isBl=i.getBooleanExtra("isBl",false);updateVisibility();break;
                case "com.manhmoc.edgebar.TEST_ANIM": playAnim();break;
                case "com.manhmoc.edgebar.MORSE_LOCK_ENGAGE":
                    isMorseLock=true;lockedPkg=i.getStringExtra("pkg");failCount=0;morseAttempt="";
                    tvMorse.setText("");updateVisibility();break;
                case "com.manhmoc.edgebar.START_MORSE_RECORD":
                    isRecording=true;morseAttempt="";tvMorse.setText("Recording...\nBL=Clear  BR=Save");updateVisibility();break;
                case "com.manhmoc.edgebar.TOGGLE_MORSE":
                    boolean m=prefs.getBoolean("morse_mode_en",false);prefs.edit().putBoolean("morse_mode_en",!m).apply();updateVisibility();break;
                case "com.manhmoc.edgebar.IPC_ACTION": exec(i.getStringExtra("act"));break;
            }
        }
    };

    private String mapToNum(String comp){
        String k=comp.replaceAll("morse_|home_|lock_|corner_","");
        switch(k){
            case "t_l":return "1";case "m_mid_t":return "2";case "t_r":return "3";
            case "l":return "4";case "t_c":return "5";case "r":return "6";
            case "m_mid_b":return "7";case "m_b_c":return "8";
            case "tl":return "9";case "tr":return "0";
            default:return "*";
        }
    }

    private class FlashView extends View {
        private Paint p=new Paint();float radius=40f;String cTheme="WHITE";int aStyle=0;float phase=0f;
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
        void setPhase(float f){phase=f;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float dw=getWidth(),dh=getHeight();if(dw<=0||dh<=0)return;
            float off=p.getStrokeWidth()/2;
            if(aStyle>0){float perim=2*(dw+dh);float seg=aStyle==1?perim/4f:aStyle==2?perim/8f:perim/12f;p.setPathEffect(new DashPathEffect(new float[]{seg,perim-seg},-perim*phase));}
            else p.setPathEffect(null);
            canvas.drawRoundRect(off,off,dw-off,dh-off,radius,radius,p);
        }
    }

    private class CornerView extends View {
        private Paint pFill=new Paint(),pStroke=new Paint();
        private int type;private String prefix;
        private Handler ah=new Handler();private boolean autoHiding=false;
        private int bMA,bSA,hd;private boolean isInv=false;
        CornerView(Context c,int t,String pfx){
            super(c);type=t;prefix=pfx;
            pFill.setStyle(Paint.Style.FILL);pFill.setAntiAlias(true);
            pStroke.setColor(Color.WHITE);pStroke.setStyle(Paint.Style.STROKE);pStroke.setAntiAlias(true);
            pStroke.setStrokeCap(Paint.Cap.ROUND);pStroke.setStrokeJoin(Paint.Join.ROUND);
        }
        void updateProps(int thick,int mA,int sA,boolean auto,int delay,boolean inv){
            pStroke.setStrokeWidth(thick);bMA=mA;bSA=sA;autoHiding=auto;hd=delay;isInv=inv;
            if(!auto){pFill.setColor(Color.argb(mA,96,125,139));pStroke.setAlpha(sA);}else triggerFlash();
            if(inv){pFill.setAlpha(0);pStroke.setAlpha(0);}invalidate();
        }
        void triggerFlash(){
            if(!autoHiding||isInv)return;
            ah.removeCallbacksAndMessages(null);
            pFill.setColor(Color.argb(Math.min(255,bMA+50),96,125,139));pStroke.setAlpha(Math.min(255,bSA+50));invalidate();
            ah.postDelayed(()->{ValueAnimator va=ValueAnimator.ofFloat(1f,0f);va.setDuration(1500);va.addUpdateListener(a->{float v=(float)a.getAnimatedValue();pFill.setColor(Color.argb((int)(bMA*v),96,125,139));pStroke.setAlpha((int)(bSA*v));invalidate();});va.start();},hd);
        }
        @Override protected void onDraw(Canvas canvas){
            float tw=getWidth(),th=getHeight(),thick=pStroke.getStrokeWidth(),pad=thick/2;
            String ck=prefix+"corner_"+CORNERS[type]+"_";
            int sm=prefs.getInt(ck+"shape",0);
            float sRad=prefs.getInt(ck+"rad",80)/1000f,mRad=prefs.getInt(ck+"moon_rad",80)/1000f;
            float sw=prefs.getInt(ck+"w",100),sh=prefs.getInt(ck+"h",100),mw=prefs.getInt(ck+"moon_w",100),mh=prefs.getInt(ck+"moon_h",100);
            Path moon=new Path(),stroke=new Path();
            float sRX,sRY,sTX,sTY,sCX,sCY,mRX,mRY,mTX,mTY,mCX,mCY;
            if(type==0){sRX=tw-pad;sRY=th-pad;sTX=tw-sw+pad;sTY=th-sh+pad;sCX=sRX-(1f-sRad)*(sw*.7f);sCY=sRY-(1f-sRad)*(sh*.7f);mRX=tw;mRY=th;mTX=tw-mw;mTY=th-mh;mCX=mRX-(1f-mRad)*(mw*.7f);mCY=mRY-(1f-mRad)*(mh*.7f);}
            else if(type==1){sRX=pad;sRY=th-pad;sTX=sw-pad;sTY=th-sh+pad;sCX=sRX+(1f-sRad)*(sw*.7f);sCY=sRY-(1f-sRad)*(sh*.7f);mRX=0;mRY=th;mTX=mw;mTY=th-mh;mCX=mRX+(1f-mRad)*(mw*.7f);mCY=mRY-(1f-mRad)*(mh*.7f);}
            else if(type==2){sRX=tw-pad;sRY=pad;sTX=tw-sw+pad;sTY=sh-pad;sCX=sRX-(1f-sRad)*(sw*.7f);sCY=sRY+(1f-sRad)*(sh*.7f);mRX=tw;mRY=0;mTX=tw-mw;mTY=mh;mCX=mRX-(1f-mRad)*(mw*.7f);mCY=mRY+(1f-mRad)*(mh*.7f);}
            else{sRX=pad;sRY=pad;sTX=sw-pad;sTY=sh-pad;sCX=sRX+(1f-sRad)*(sw*.7f);sCY=sRY+(1f-sRad)*(sh*.7f);mRX=0;mRY=0;mTX=mw;mTY=mh;mCX=mRX+(1f-mRad)*(mw*.7f);mCY=mRY+(1f-mRad)*(mh*.7f);}
            if(sm==1){stroke.moveTo(sRX,sRY);stroke.lineTo(sTX,sRY);}
            else if(sm==2){stroke.moveTo(sRX,sRY);stroke.lineTo(sRX,sTY);}
            else{stroke.moveTo(sRX,sTY);stroke.quadTo(sCX,sCY,sTX,sRY);}
            if(type==0||type==1){moon.moveTo(mRX,mTY);moon.lineTo(mRX,mRY);moon.lineTo(mTX,mRY);moon.quadTo(mCX,mCY,mRX,mTY);}
            else{moon.moveTo(mTX,mRY);moon.lineTo(mRX,mRY);moon.lineTo(mRX,mTY);moon.quadTo(mCX,mCY,mTX,mRY);}
            moon.close();
            canvas.drawPath(stroke,pStroke);
            float mx=prefs.getInt(ck+"moon_x",1250)-1250,my=prefs.getInt(ck+"moon_y",1250)-1250;
            canvas.save();canvas.translate(mx,my);canvas.drawPath(moon,pFill);canvas.restore();
        }
    }

    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int f,int s){isRunning=true;return START_STICKY;}

    @Override public void onCreate(){
        super.onCreate();isRunning=true;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        prefs=getSharedPreferences("EdgeBarPrefs",MODE_PRIVATE);
        cm=(CameraManager)getSystemService(Context.CAMERA_SERVICE);
        vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        try{cId=cm.getCameraIdList()[0];}catch(Exception e){}
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter f=new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);
        f.addAction("com.manhmoc.edgebar.SYNC_STATE");f.addAction("com.manhmoc.edgebar.TEST_ANIM");
        f.addAction("com.manhmoc.edgebar.IPC_ACTION");f.addAction("com.manhmoc.edgebar.MORSE_LOCK_ENGAGE");
        f.addAction("com.manhmoc.edgebar.START_MORSE_RECORD");f.addAction("com.manhmoc.edgebar.TOGGLE_MORSE");
        if(Build.VERSION.SDK_INT>=33) registerReceiver(syncReceiver,f,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(syncReceiver,f);
        String cid="eb_19_home";
        NotificationChannel nc=new NotificationChannel(cid,"Edge Bar Màn Chính",NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(nc);
        Notification n=new Notification.Builder(this,cid).setContentTitle("Edge Bar Màn Chính").setSmallIcon(R.drawable.ic_launcher_fg).setOngoing(true).build();
        startForeground(2,n);
        fV=new FlashView(this);fV.setAlpha(0f);fV.setVisibility(View.GONE);
        WindowManager.LayoutParams fp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        try{wm.addView(fV,fp);}catch(Exception e){}
        for(int i=0;i<5;i++){bars[i]=new View(this);WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,0,PixelFormat.TRANSLUCENT);try{wm.addView(bars[i],p);}catch(Exception e){}bars[i].setOnTouchListener(new STL("home_"+BARS[i],null));}
        for(int i=0;i<4;i++){corners[i]=new CornerView(this,i,"home_");WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,0,PixelFormat.TRANSLUCENT);try{wm.addView(corners[i],p);}catch(Exception e){}corners[i].setOnTouchListener(new STL("home_corner_"+CORNERS[i],corners[i]));}
        morseContainer=new RelativeLayout(this);morseContainer.setBackgroundColor(Color.argb(200,0,0,0));morseContainer.setVisibility(View.GONE);morseContainer.setOnTouchListener((v,e)->true);
        tvMorse=new TextView(this);tvMorse.setTextColor(Color.WHITE);tvMorse.setTextSize(36);tvMorse.setGravity(Gravity.CENTER);
        RelativeLayout.LayoutParams tvP=new RelativeLayout.LayoutParams(-1,-2);tvP.addRule(RelativeLayout.CENTER_IN_PARENT);
        morseContainer.addView(tvMorse,tvP);
        WindowManager.LayoutParams bgP=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        try{wm.addView(morseContainer,bgP);}catch(Exception e){}
        for(int i=0;i<8;i++){mBars[i]=new View(this);WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,0,PixelFormat.TRANSLUCENT);try{wm.addView(mBars[i],p);}catch(Exception e){}mBars[i].setOnTouchListener(new STL("morse_"+M_BARS[i],null));}
        for(int i=0;i<4;i++){mCorners[i]=new CornerView(this,i,"morse_");WindowManager.LayoutParams p=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,0,PixelFormat.TRANSLUCENT);try{wm.addView(mCorners[i],p);}catch(Exception e){}mCorners[i].setOnTouchListener(new STL("morse_corner_"+CORNERS[i],mCorners[i]));}
        updateVisibility();
    }

    private void updateVisibility(){
        boolean unlocked=!km.isKeyguardLocked();
        boolean hide=(prefs.getBoolean("avoid_kbd",true)&&isKbd)||isBl;
        boolean prevMorse=prefs.getBoolean("preview_morse",false);
        if(isMorseLock||isRecording||(prevMorse&&unlocked)){
            morseContainer.setVisibility(View.VISIBLE);
            morseContainer.setAlpha(prefs.getInt("morse_bg_alpha",200)/255f);
            for(View b:bars) if(b!=null) b.setVisibility(View.GONE);
            for(View c:corners) if(c!=null) c.setVisibility(View.GONE);
            for(int i=0;i<8;i++){if(mBars[i]==null)continue;boolean en=prefs.getBoolean("morse_"+M_BARS[i]+"_en",false);mBars[i].setVisibility(en?View.VISIBLE:View.GONE);if(en)updateMBar(i);}
            for(int i=0;i<4;i++){if(mCorners[i]==null)continue;boolean en=prefs.getBoolean("morse_corner_"+CORNERS[i]+"_en",false);mCorners[i].setVisibility(en?View.VISIBLE:View.GONE);if(en)updateMCorner(i);}
        } else {
            morseContainer.setVisibility(View.GONE);
            for(View b:mBars) if(b!=null) b.setVisibility(View.GONE);
            for(View c:mCorners) if(c!=null) c.setVisibility(View.GONE);
            for(int i=0;i<5;i++){if(bars[i]==null)continue;boolean en=prefs.getBoolean("home_"+BARS[i]+"_en",false);bars[i].setVisibility(en&&unlocked&&!hide?View.VISIBLE:View.GONE);if(en&&unlocked)updateBar(i);}
            for(int i=0;i<4;i++){if(corners[i]==null)continue;boolean en=prefs.getBoolean("home_corner_"+CORNERS[i]+"_en",false);corners[i].setVisibility(en&&unlocked&&!hide?View.VISIBLE:View.GONE);if(en&&unlocked)updateCorner(i);}
        }
    }
    private void updateBar(int i){
        String pre="home_"+BARS[i]+"_";
        int al=prefs.getInt(pre+"alpha",50),w=prefs.getInt(pre+"w",300),h=prefs.getInt(pre+"h",60),x=prefs.getInt(pre+"x",0),y=prefs.getInt(pre+"y",0);
        GradientDrawable gd=new GradientDrawable();gd.setColor(Color.argb(al,96,125,139));gd.setCornerRadius(24f);bars[i].setBackground(gd);
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)bars[i].getLayoutParams();
        p.flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.width=w;p.height=h;p.x=x;p.y=y;p.gravity=GRAV[i];wm.updateViewLayout(bars[i],p);
    }
    private void updateCorner(int i){
        String ck="home_corner_"+CORNERS[i]+"_";
        int vm=prefs.getInt(ck+"vis_mode",0);
        ((CornerView)corners[i]).updateProps(prefs.getInt("home_corner_thick",8),prefs.getInt("home_corner_moon_alpha",100),prefs.getInt("home_corner_stroke_alpha",200),vm==1,prefs.getInt("home_corner_hide_dur",2500),vm==2);
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)corners[i].getLayoutParams();
        p.flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.gravity=C_GRAV[i];p.width=Math.max(10,prefs.getInt(ck+"w",100)+Math.abs(prefs.getInt(ck+"moon_x",1250)-1250));p.height=Math.max(10,prefs.getInt(ck+"h",100)+Math.abs(prefs.getInt(ck+"moon_y",1250)-1250));
        p.x=prefs.getInt(ck+"x",0);p.y=prefs.getInt(ck+"y",0);wm.updateViewLayout(corners[i],p);
    }
    private void updateMBar(int i){
        String pre="morse_"+M_BARS[i]+"_";
        int al=prefs.getInt(pre+"alpha",50),w=prefs.getInt(pre+"w",300),h=prefs.getInt(pre+"h",60),x=prefs.getInt(pre+"x",0),y=prefs.getInt(pre+"y",0);
        GradientDrawable gd=new GradientDrawable();gd.setColor(Color.argb(al,96,125,139));gd.setCornerRadius(24f);mBars[i].setBackground(gd);
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)mBars[i].getLayoutParams();
        p.flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.width=w;p.height=h;p.x=x;p.y=y;p.gravity=M_GRAV[i];wm.updateViewLayout(mBars[i],p);
    }
    private void updateMCorner(int i){
        String ck="morse_corner_"+CORNERS[i]+"_";
        int vm=prefs.getInt(ck+"vis_mode",0);
        ((CornerView)mCorners[i]).updateProps(prefs.getInt("morse_corner_thick",8),prefs.getInt("morse_corner_moon_alpha",100),prefs.getInt("morse_corner_stroke_alpha",200),vm==1,prefs.getInt("morse_corner_hide_dur",2500),vm==2);
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)mCorners[i].getLayoutParams();
        p.flags=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.gravity=C_GRAV[i];p.width=Math.max(10,prefs.getInt(ck+"w",100)+Math.abs(prefs.getInt(ck+"moon_x",1250)-1250));p.height=Math.max(10,prefs.getInt(ck+"h",100)+Math.abs(prefs.getInt(ck+"moon_y",1250)-1250));
        p.x=prefs.getInt(ck+"x",0);p.y=prefs.getInt(ck+"y",0);wm.updateViewLayout(mCorners[i],p);
    }

    private void handleMorse(String comp,View v){
        doVibrate(30);
        if(v instanceof CornerView) ((CornerView)v).triggerFlash();
        if(comp.endsWith("corner_bl")){morseAttempt="";tvMorse.setText(isRecording?"Cleared":"");}
        else if(comp.endsWith("corner_br")){
            if(morseAttempt.isEmpty()) return;
            if(isRecording){
                prefs.edit().putString("morse_password",morseAttempt).apply();
                isRecording=false;tvMorse.setText("✓ Saved!");
                new Handler().postDelayed(()->{tvMorse.setText("");updateVisibility();},1500);
            } else {
                if(morseAttempt.equals(prefs.getString("morse_password",""))){
                    isMorseLock=false;failCount=0;
                    Intent s=new Intent("com.manhmoc.edgebar.MORSE_UNLOCK_SUCCESS");s.putExtra("pkg",lockedPkg);sendBroadcast(s);
                    updateVisibility();
                } else {
                    failCount++;doVibrate(prefs.getInt("morse_fail_vib",500));
                    if(failCount==1) tvMorse.setText(prefs.getString("morse_insult_1","Who are u?"));
                    else if(failCount==2) tvMorse.setText(prefs.getString("morse_insult_2","What are u doing?"));
                    else{
                        tvMorse.setText(prefs.getString("morse_insult_3","Get out!"));
                        exec("HOME");isMorseLock=false;
                        new Handler().postDelayed(this::updateVisibility,500);
                    }
                    morseAttempt="";
                }
            }
        } else {
            morseAttempt+=mapToNum(comp);
            if(isRecording) tvMorse.setText("Seq: "+morseAttempt);
            else{
                tvMorse.setText(morseAttempt);
                dotHandler.removeCallbacksAndMessages(null);
                dotHandler.postDelayed(()->{StringBuilder d=new StringBuilder();for(int i=0;i<morseAttempt.length();i++)d.append("● ");tvMorse.setText(d.toString());},prefs.getInt("morse_dot_delay",500));
            }
        }
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
    private void handleAction(String key){String a=prefs.getString(key,"NONE");if(a.equals("NONE")||!prefs.getBoolean(key+"_on",true))return;if(prefs.getBoolean(key+"_vib",true))doVibrate(prefs.getInt("vib_dur",30));if(prefs.getBoolean(key+"_anim",true))playAnim();for(String act:a.split(","))exec(act.trim());}
    private void doVibrate(int dur){if(dur<=0)return;try{if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(dur,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(dur);}catch(Exception e){}}
    private void exec(String a){
        if(a==null||a.equals("NONE"))return;
        try{
            switch(a){
                case "HOME":sendBroadcast(new Intent("com.manhmoc.edgebar.IPC_ACTION").putExtra("act","HOME"));break;
                case "TOGGLE_MORSE":prefs.edit().putBoolean("morse_mode_en",!prefs.getBoolean("morse_mode_en",false)).apply();updateVisibility();break;
                case "FLASH":fOn=!fOn;cm.setTorchMode(cId,fOn);break;
                case "CAMERA":try{Intent ic=new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);ic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(ic);}catch(Exception e){}break;
                case "VOLUME":((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_SAME,AudioManager.FLAG_SHOW_UI);break;
                default:
                    if(a.startsWith("INTENT_")){String idx=a.substring(7);try{String pkg=prefs.getString("intent_"+idx+"_pkg",""),cls=prefs.getString("intent_"+idx+"_cls",""),act=prefs.getString("intent_"+idx+"_act","");if(!pkg.isEmpty()&&!cls.isEmpty()){Intent i=new Intent();i.setClassName(pkg,cls);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}else if(!act.isEmpty()){Intent i=new Intent(act);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}}catch(Exception e){}}
                    else sendBroadcast(new Intent("com.manhmoc.edgebar.IPC_ACTION").putExtra("act",a));
            }
        }catch(Exception e){}
    }

    private class STL implements View.OnTouchListener {
        String key;View myView;GestureDetector gd;float sx,sy;long st;
        STL(String k,View v){key=k;myView=v;gd=new GestureDetector(HomescreenService.this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onSingleTapConfirmed(MotionEvent e){if(!isMorseLock&&!isRecording)handleAction(key+"_tap");return true;}
            @Override public boolean onDoubleTap(MotionEvent e){if(!isMorseLock&&!isRecording)handleAction(key+"_dtap");return true;}
            @Override public void onLongPress(MotionEvent e){if(!isMorseLock&&!isRecording)handleAction(key+"_long");}
        });}
        @Override public boolean onTouch(View v,MotionEvent e){
            if(isMorseLock||isRecording){if(e.getAction()==MotionEvent.ACTION_DOWN)handleMorse(key,myView);return true;}
            if(myView instanceof CornerView)((CornerView)myView).triggerFlash();
            gd.onTouchEvent(e);
            if(e.getAction()==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();st=System.currentTimeMillis();}
            else if(e.getAction()==MotionEvent.ACTION_UP){float dx=e.getRawX()-sx,dy=e.getRawY()-sy;if(Math.abs(dx)>50||Math.abs(dy)>50){boolean hold=System.currentTimeMillis()-st>prefs.getInt("hold_dur",600);String name;if(myView instanceof CornerView&&Math.abs(dx)>40&&Math.abs(dy)>40)name="diag"+(hold?"_hold":"");else{if(Math.abs(dx)>Math.abs(dy))name=dx>0?"right":"left";else name=dy>0?"down":"up";if(hold)name+="_hold";}handleAction(key+"_"+name);return true;}}
            return true;
        }
    }

    @Override public void onDestroy(){
        super.onDestroy();isRunning=false;
        try{unregisterReceiver(syncReceiver);}catch(Exception e){}
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        for(View b:bars)if(b!=null)try{wm.removeView(b);}catch(Exception e){}
        for(View b:mBars)if(b!=null)try{wm.removeView(b);}catch(Exception e){}
        for(View c:corners)if(c!=null)try{wm.removeView(c);}catch(Exception e){}
        for(View c:mCorners)if(c!=null)try{wm.removeView(c);}catch(Exception e){}
        if(morseContainer!=null)try{wm.removeView(morseContainer);}catch(Exception e){}
        if(fV!=null)try{wm.removeView(fV);}catch(Exception e){}
    }
}
