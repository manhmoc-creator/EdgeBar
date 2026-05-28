package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private LinearLayout root;

    // Tên hiển thị cho các hành động
    private static final String[] ACTIONS = {
        "NONE","BACK","HOME","RECENTS","SCREEN_OFF","SCREENSHOT","NOTIFICATIONS",
        "POWER_DIALOG","FLASH","CAMERA","VOLUME","TOGGLE_MORSE",
        "MACRO_1","MACRO_2","MACRO_3","MACRO_4","MACRO_5",
        "INTENT_1","INTENT_2","INTENT_3","INTENT_4","INTENT_5",
        "INTENT_6","INTENT_7","INTENT_8","INTENT_9","INTENT_10",
        "INTENT_11","INTENT_12","INTENT_13","INTENT_14","INTENT_15",
        "YTDL_DOWNLOAD"
    };

    private static final String[] COLORS = {
        "WHITE","NEON","CYBERPUNK","LAVA","OCEAN","MATRIX","SUNSET","GOOGLE","AURORA","ABYSS"
    };

    private static final String[] LOCK_BARS  = {"r","l","t_r","t_l","t_c"};
    private static final String[] LOCK_NAMES = {"Phải (Lock)","Trái (Lock)","Top-Phải (Lock)","Top-Trái (Lock)","Top-Giữa (Lock)"};
    private static final String[] HOME_BARS  = {"r","l","t_r","t_l","t_c"};
    private static final String[] HOME_NAMES = {"Phải (Home)","Trái (Home)","Top-Phải (Home)","Top-Trái (Home)","Top-Giữa (Home)"};
    private static final String[] CORNERS    = {"br","bl","tr","tl"};
    private static final String[] C_NAMES    = {"BR","BL","TR","TL"};
    private static final String[] MORSE_BARS = {"r","l","t_r","t_l","t_c","m_b_c","m_mid_t","m_mid_b"};
    private static final String[] M_NAMES    = {"Phải","Trái","Top-Phải","Top-Trái","Top-Giữa","Bot-Giữa","Mid-Top","Mid-Bot"};
    private static final String[] GESTURES   = {"tap","dtap","long","up","down","left","right","diag","up_hold","down_hold","left_hold","right_hold","diag_hold"};
    private static final String[] G_NAMES    = {"Nhấn","2 Nhấn","Giữ","Vuốt Lên","Vuốt Xuống","Vuốt Trái","Vuốt Phải","Chéo","Giữ+Lên","Giữ+Xuống","Giữ+Trái","Giữ+Phải","Giữ+Chéo"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16,16,16,32);
        sv.addView(root);
        setContentView(sv);

        addHeader("⚡ EDGE BAR V19.12.3.4");
        addServiceControls();
        addSection("── LOCKSCREEN BARS ──");
        for(int i=0;i<LOCK_BARS.length;i++) addBarSection("lock_"+LOCK_BARS[i], LOCK_NAMES[i], "lock_");
        addSection("── HOME BARS ──");
        for(int i=0;i<HOME_BARS.length;i++) addBarSection("home_"+HOME_BARS[i], HOME_NAMES[i], "home_");
        addSection("── CORNERS (Lock) ──");
        for(int i=0;i<CORNERS.length;i++) addCornerSection("lock_", CORNERS[i], "Lock "+C_NAMES[i]);
        addSection("── CORNERS (Home) ──");
        for(int i=0;i<CORNERS.length;i++) addCornerSection("home_", CORNERS[i], "Home "+C_NAMES[i]);
        addSection("── MORSE APPLOCK ──");
        addMorseSection();
        addSection("── MORSE BARS ──");
        for(int i=0;i<MORSE_BARS.length;i++) addBarSection("morse_"+MORSE_BARS[i], "Morse: "+M_NAMES[i], "morse_");
        addSection("── MORSE CORNERS ──");
        for(int i=0;i<CORNERS.length;i++) addCornerSection("morse_", CORNERS[i], "Morse "+C_NAMES[i]);
        addSection("── QS TILES (1-15) ──");
        addTilesSection();
        addSection("── MACROS (1-5) ──");
        addMacrosSection();
        addSection("── INTENTS (1-15) ──");
        addIntentsSection();
        addSection("── ANIMATION ──");
        addAnimSection();
        addSection("── GLOBAL ──");
        addGlobalSection();
        addSection("── PREVIEW ──");
        addPreviewSection();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density); }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20); tv.setTextColor(0xFF00E5FF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0,dp(12),0,dp(12));
        root.addView(tv);
    }

    private void addSection(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14); tv.setTextColor(0xFF607D8B);
        tv.setPadding(0,dp(16),0,dp(4));
        root.addView(tv);
        View div = new View(this);
        div.setBackgroundColor(0xFF37474F);
        div.setLayoutParams(new LinearLayout.LayoutParams(-1,1));
        root.addView(div);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,dp(4),0,dp(4));
        root.addView(r);
        return r;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(12); tv.setTextColor(0xFFB0BEC5);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return tv;
    }

    private Switch sw(String key, boolean def) {
        Switch s = new Switch(this);
        s.setChecked(prefs.getBoolean(key, def));
        s.setOnCheckedChangeListener((b,v)->prefs.edit().putBoolean(key,v).apply());
        return s;
    }

    private SeekBar seek(String key, int def, int max) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max); sb.setProgress(prefs.getInt(key,def));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean u){prefs.edit().putInt(key,p).apply();}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        return sb;
    }

    private Spinner actionSpinner(String key) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,ACTIONS);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        String cur = prefs.getString(key,"NONE");
        for(int i=0;i<ACTIONS.length;i++) if(ACTIONS[i].equals(cur)){sp.setSelection(i);break;}
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,View v,int p,long id){prefs.edit().putString(key,ACTIONS[p]).apply();}
            public void onNothingSelected(AdapterView<?> a){}
        });
        return sp;
    }

    private Button btn(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setOnClickListener(v->action.run());
        return b;
    }

    // ── Service Controls ───────────────────────────────────────────────────
    private void addServiceControls() {
        LinearLayout r1 = row();
        r1.addView(btn("🔒 Bật/Tắt Trợ Năng", ()->{
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        }));
        r1.addView(btn("🏠 Bật/Tắt Home", ()->{
            Intent i = new Intent(this, HomescreenService.class);
            if(HomescreenService.isRunning) stopService(i);
            else { if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); }
        }));
        LinearLayout r2 = row();
        r2.addView(btn("⚙️ Overlay Perm", ()->{
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        }));
        r2.addView(btn("📳 WRITE_SECURE", ()->{
            Toast.makeText(this,"adb shell: pm grant "+getPackageName()+" android.permission.WRITE_SECURE_SETTINGS",Toast.LENGTH_LONG).show();
        }));
    }

    // ── Bar Section ────────────────────────────────────────────────────────
    private void addBarSection(String key, String name, String prefix) {
        LinearLayout hdr = row();
        hdr.addView(label("▶ "+name));
        hdr.addView(sw(key+"_en", false));

        // W, H seekbars
        LinearLayout rw = row(); rw.addView(label("W")); rw.addView(seek(key+"_w",300,1200));
        LinearLayout rh = row(); rh.addView(label("H")); rh.addView(seek(key+"_h",60,400));
        LinearLayout rx = row(); rx.addView(label("X")); rx.addView(seek(key+"_x",0,1200));
        LinearLayout ry = row(); ry.addView(label("Y")); ry.addView(seek(key+"_y",0,2400));
        LinearLayout ra = row(); ra.addView(label("Alpha")); ra.addView(seek(key+"_alpha",50,255));

        // Gesture actions
        for(int g=0;g<GESTURES.length;g++){
            LinearLayout rg = row();
            rg.addView(label("  "+G_NAMES[g]));
            rg.addView(actionSpinner(key+"_"+GESTURES[g]));
        }
    }

    // ── Corner Section ─────────────────────────────────────────────────────
    private void addCornerSection(String prefix, String corner, String name) {
        String key = prefix+"corner_"+corner;
        LinearLayout hdr = row();
        hdr.addView(label("▶ "+name));
        hdr.addView(sw(key+"_en",false));
        LinearLayout rw = row(); rw.addView(label("W")); rw.addView(seek(key+"_w",100,600));
        LinearLayout rh = row(); rh.addView(label("H")); rh.addView(seek(key+"_h",100,600));
        for(int g=0;g<GESTURES.length;g++){
            LinearLayout rg = row(); rg.addView(label("  "+G_NAMES[g])); rg.addView(actionSpinner(key+"_"+GESTURES[g]));
        }
    }

    // ── Morse Section ──────────────────────────────────────────────────────
    private void addMorseSection() {
        LinearLayout r1 = row(); r1.addView(label("Bật Morse Mode")); r1.addView(sw("morse_mode_en",false));
        LinearLayout r2 = row(); r2.addView(label("Dot Delay (ms)")); r2.addView(seek("morse_dot_delay",500,3000));
        LinearLayout r3 = row(); r3.addView(label("Fail Vib (ms)")); r3.addView(seek("morse_fail_vib",500,2000));
        LinearLayout r4 = row(); r4.addView(label("BG Alpha")); r4.addView(seek("morse_bg_alpha",200,255));

        String[] insults = {"morse_insult_1","morse_insult_2","morse_insult_3"};
        String[] defIns = {"Who are u?","What are u doing?","Get out!"};
        for(int i=0;i<3;i++){
            LinearLayout ri = row();
            ri.addView(label("Insult "+(i+1)));
            EditText et = new EditText(this);
            et.setText(prefs.getString(insults[i],defIns[i]));
            et.setTextSize(12);
            String key=insults[i];
            et.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString(key,et.getText().toString()).apply();});
            ri.addView(et);
        }
        LinearLayout rb = row();
        rb.addView(btn("🎵 Ghi mật khẩu Morse",()->sendBroadcast(new Intent("com.manhmoc.edgebar.START_MORSE_RECORD"))));
        rb.addView(btn("🗑️ Xoá mật khẩu",()->prefs.edit().remove("morse_password").apply()));
    }

    // ── Tiles Section ──────────────────────────────────────────────────────
    private void addTilesSection() {
        for(int i=1;i<=15;i++){
            LinearLayout r = row();
            r.addView(label("Tile "+i));
            r.addView(actionSpinner("tile_"+i+"_act"));
        }
    }

    // ── Macros Section ─────────────────────────────────────────────────────
    private void addMacrosSection() {
        for(int i=1;i<=5;i++){
            LinearLayout r = row(); r.addView(label("Macro "+i+" (svcs)"));
            EditText et = new EditText(this); et.setText(prefs.getString("macro_"+i+"_svcs",""));
            et.setHint("pkg/ServiceClass,..."); et.setTextSize(11);
            String key="macro_"+i+"_svcs";
            et.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString(key,et.getText().toString()).apply();});
            r.addView(et);
        }
    }

    // ── Intents Section ────────────────────────────────────────────────────
    private void addIntentsSection() {
        for(int i=1;i<=15;i++){
            addSection("Intent "+i);
            LinearLayout rp = row(); rp.addView(label("Package"));
            EditText ep=new EditText(this);ep.setText(prefs.getString("intent_"+i+"_pkg",""));ep.setTextSize(11);ep.setHint("com.example.app");
            String kp="intent_"+i+"_pkg";ep.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString(kp,ep.getText().toString()).apply();});
            rp.addView(ep);
            LinearLayout rc = row(); rc.addView(label("Class"));
            EditText ec=new EditText(this);ec.setText(prefs.getString("intent_"+i+"_cls",""));ec.setTextSize(11);ec.setHint(".MainActivity");
            String kc="intent_"+i+"_cls";ec.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString(kc,ec.getText().toString()).apply();});
            rc.addView(ec);
            LinearLayout ra2 = row(); ra2.addView(label("Action (optional)"));
            EditText ea=new EditText(this);ea.setText(prefs.getString("intent_"+i+"_act",""));ea.setTextSize(11);ea.setHint("android.intent.action.VIEW");
            String ka="intent_"+i+"_act";ea.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString(ka,ea.getText().toString()).apply();});
            ra2.addView(ea);
        }
    }

    // ── Anim Section ──────────────────────────────────────────────────────
    private void addAnimSection() {
        LinearLayout rs = row(); rs.addView(label("Style (0=fade,1-3=dash)"));
        Spinner spStyle = new Spinner(this);
        String[] styles={"0 - Fade","1 - Dash 1/4","2 - Dash 1/8","3 - Dash 1/12"};
        ArrayAdapter<String> as=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,styles);
        as.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStyle.setAdapter(as); spStyle.setSelection(prefs.getInt("anim_style",0));
        spStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,View v,int p,long id){prefs.edit().putInt("anim_style",p).apply();}
            public void onNothingSelected(AdapterView<?> a){}
        });
        rs.addView(spStyle);

        LinearLayout rc = row(); rc.addView(label("Màu"));
        Spinner spColor=new Spinner(this);
        ArrayAdapter<String> ac=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,COLORS);
        ac.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spColor.setAdapter(ac);
        String curC=prefs.getString("anim_color","WHITE");
        for(int i=0;i<COLORS.length;i++) if(COLORS[i].equals(curC)){spColor.setSelection(i);break;}
        spColor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,View v,int p,long id){prefs.edit().putString("anim_color",COLORS[p]).apply();}
            public void onNothingSelected(AdapterView<?> a){}
        });
        rc.addView(spColor);

        LinearLayout rd = row(); rd.addView(label("Thời gian (ms)")); rd.addView(seek("anim_dur",1500,5000));
        LinearLayout rth = row(); rth.addView(label("Độ dày")); rth.addView(seek("anim_thick",12,60));
        LinearLayout rr = row(); rr.addView(label("Bo góc")); rr.addView(seek("anim_rad",40,200));
        LinearLayout ral = row(); ral.addView(label("Alpha")); ral.addView(seek("anim_alpha",255,255));

        LinearLayout rb = row();
        rb.addView(btn("▶ Test Anim", ()->sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM"))));
    }

    // ── Global Section ─────────────────────────────────────────────────────
    private void addGlobalSection() {
        LinearLayout r1 = row(); r1.addView(label("Tránh bàn phím")); r1.addView(sw("avoid_kbd",true));
        LinearLayout r2 = row(); r2.addView(label("Vib (ms)")); r2.addView(seek("vib_dur",30,500));
        LinearLayout r3 = row(); r3.addView(label("Hold (ms)")); r3.addView(seek("hold_dur",600,2000));

        // Blacklist / Locklist
        LinearLayout rb = row(); rb.addView(label("Blacklist (pkg,pkg)"));
        EditText eb=new EditText(this);eb.setText(prefs.getString("blacklist",""));eb.setTextSize(11);
        eb.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString("blacklist",eb.getText().toString()).apply();});
        rb.addView(eb);

        LinearLayout rl = row(); rl.addView(label("AppLock (pkg,pkg)"));
        EditText el=new EditText(this);el.setText(prefs.getString("locklist",""));el.setTextSize(11);
        el.setOnFocusChangeListener((v,f)->{if(!f)prefs.edit().putString("locklist",el.getText().toString()).apply();});
        rl.addView(el);
    }

    // ── Preview Section ────────────────────────────────────────────────────
    private void addPreviewSection() {
        LinearLayout r1 = row(); r1.addView(label("Preview Lock")); r1.addView(sw("preview_lock",false));
        LinearLayout r2 = row(); r2.addView(label("Preview Morse")); r2.addView(sw("preview_morse",false));
    }
}
