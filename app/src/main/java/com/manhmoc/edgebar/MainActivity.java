package com.manhmoc.edgebar;
import android.app.Activity; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.provider.Settings; import android.net.Uri; import android.view.View; import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private String[] ACT_KEYS = new String[24]; private String[] ACT_LABS = new String[24];
    private final String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private final String[] BAR_NAMES = {"ĐÁY TRÁI", "ĐÁY PHẢI", "ĐỈNH TRÁI", "ĐỈNH PHẢI", "ĐỈNH GIỮA"};
    private final String[] GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right"}; private final String[] GESTURE_NAMES = {"1 Chạm", "2 Chạm", "Nhấn giữ", "Vuốt Lên", "Vuốt Xuống", "Vuốt Trái", "Vuốt Phải"};
    private LinearLayout pageDesign, pageGestures, pageIntents; private Button btnNavDes, btnNavGes, btnNavInt;
    private LinearLayout tabBoth, tabLock, tabHome; private Button btnBoth, btnLock, btnHome;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        
        String[] bK = {"NONE", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "QR", "NOTIFICATIONS"};
        String[] bL = {"Không có", "Tắt màn hình", "Đèn pin", "Menu nguồn", "Âm lượng", "Chụp màn hình", "Camera an toàn", "Google Lens (QR)", "Thông báo"};
        for(int i=0; i<9; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; }
        for(int i=1; i<=15; i++) { ACT_KEYS[8+i]="INTENT_"+i; ACT_LABS[8+i]="Gửi Intent "+i; }

        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.parseColor("#121212")); LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,40,40,100);
        TextView title = new TextView(this); title.setText("⚙️ EdgeBar V14 - Magnum Opus"); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setPadding(0,0,0,20); main.addView(title);
        
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText("⚠️ CẤP QUYỀN VẼ ĐÈ GÓC MÀN CHÍNH"); btnReq.setBackgroundColor(Color.RED); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); } else { try { startService(new Intent(this, HomescreenService.class)); } catch (Exception e) {} }

        // NAVIGATION TOP BAR
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0, 0, 0, 40);
        btnNavDes = createNavBtn("THIẾT KẾ"); btnNavGes = createNavBtn("CỬ CHỈ"); btnNavInt = createNavBtn("INTENTS");
        nav.addView(btnNavDes); nav.addView(btnNavGes); nav.addView(btnNavInt); main.addView(nav);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL);
        pageGestures = new LinearLayout(this); pageGestures.setOrientation(LinearLayout.VERTICAL);
        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL);

        // --- KHÔNG GIAN THIẾT KẾ ---
        pageDesign.addView(createSection("🎯 THUẬT TOÁN THÔNG MINH"));
        CheckBox cbKbd = new CheckBox(this); cbKbd.setText("Ẩn 5 Thanh Bar khi Gboard (Bàn phím) hiện"); cbKbd.setTextColor(Color.parseColor("#FFC107")); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true)); cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply()); pageDesign.addView(cbKbd);
        
        pageDesign.addView(createSection("✨ ĐIỀU KIỆN 2 GÓC VÁT (ASSISTANT)"));
        CheckBox cbLockC = new CheckBox(this); cbLockC.setText("Bật Góc ở Màn Hình KHOÁ (Trợ năng)"); cbLockC.setTextColor(Color.WHITE); cbLockC.setChecked(prefs.getBoolean("lock_corner_en", true)); cbLockC.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("lock_corner_en", c).apply()); pageDesign.addView(cbLockC);
        CheckBox cbHomeC = new CheckBox(this); cbHomeC.setText("Bật Góc ở Màn Hình CHÍNH (ADB)"); cbHomeC.setTextColor(Color.WHITE); cbHomeC.setChecked(prefs.getBoolean("home_corner_en", true)); cbHomeC.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("home_corner_en", c).apply()); pageDesign.addView(cbHomeC);

        pageDesign.addView(createSection("🎨 KÍCH THƯỚC 5 THANH"));
        for(int i=0; i<5; i++) { CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(BARS[idx]+"_en", c).apply()); pageDesign.addView(cb); pageDesign.addView(createSlider("Độ trong", BARS[i]+"_alpha", 255, 50)); pageDesign.addView(createSlider("Ngang", BARS[i]+"_w", 1400, 300)); pageDesign.addView(createSlider("Dọc", BARS[i]+"_h", 1400, 60)); pageDesign.addView(createSlider("Toạ độ X", BARS[i]+"_x", 1000, 0)); pageDesign.addView(createSlider("Toạ độ Y", BARS[i]+"_y", 1000, 0)); }

        // --- KHÔNG GIAN CỬ CHỈ ---
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 20, 0, 20);
        btnBoth = createTabBtn("CẢ HAI"); btnLock = createTabBtn("LOCKSCREEN"); btnHome = createTabBtn("HOMESCREEN");
        tabContainer.addView(btnBoth); tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageGestures.addView(tabContainer);
        tabBoth = createConfigPage("both"); tabLock = createConfigPage("lock"); tabHome = createConfigPage("home");
        pageGestures.addView(tabBoth); pageGestures.addView(tabLock); pageGestures.addView(tabHome);
        btnBoth.setOnClickListener(v -> switchGesTab(0)); btnLock.setOnClickListener(v -> switchGesTab(1)); btnHome.setOnClickListener(v -> switchGesTab(2)); switchGesTab(0);

        // --- KHÔNG GIAN INTENTS (15 SLOTS) ---
        for (int i = 1; i <= 15; i++) { pageIntents.addView(createSection("Slot Intent " + i)); pageIntents.addView(createInput("Action", "i"+i+"_act")); pageIntents.addView(createInput("Package", "i"+i+"_pkg")); pageIntents.addView(createInput("Class Name", "i"+i+"_cls")); pageIntents.addView(createInput("Data URI", "i"+i+"_data")); pageIntents.addView(createInput("Categories", "i"+i+"_cat")); pageIntents.addView(createInput("Flags", "i"+i+"_flags")); CheckBox cb = new CheckBox(this); cb.setText("Gửi Broadcast"); cb.setTextColor(Color.WHITE); cb.setChecked(prefs.getBoolean("i"+i+"_br", true)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("i"+idx+"_br", c).apply()); pageIntents.addView(cb); }

        main.addView(pageDesign); main.addView(pageGestures); main.addView(pageIntents);
        btnNavDes.setOnClickListener(v->switchMainTab(0)); btnNavGes.setOnClickListener(v->switchMainTab(1)); btnNavInt.setOnClickListener(v->switchMainTab(2)); switchMainTab(1);

        scroll.addView(main); setContentView(scroll);
    }
    
    private void switchMainTab(int idx) { pageDesign.setVisibility(idx==0?View.VISIBLE:View.GONE); pageGestures.setVisibility(idx==1?View.VISIBLE:View.GONE); pageIntents.setVisibility(idx==2?View.VISIBLE:View.GONE); btnNavDes.setTextColor(idx==0?Color.CYAN:Color.GRAY); btnNavGes.setTextColor(idx==1?Color.CYAN:Color.GRAY); btnNavInt.setTextColor(idx==2?Color.CYAN:Color.GRAY); }
    private void switchGesTab(int idx) { tabBoth.setVisibility(idx==0?View.VISIBLE:View.GONE); tabLock.setVisibility(idx==1?View.VISIBLE:View.GONE); tabHome.setVisibility(idx==2?View.VISIBLE:View.GONE); btnBoth.setTextColor(idx==0?Color.GREEN:Color.WHITE); btnLock.setTextColor(idx==1?Color.GREEN:Color.WHITE); btnHome.setTextColor(idx==2?Color.GREEN:Color.WHITE); }
    
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setTextColor(Color.GRAY); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); b.setBackgroundColor(Color.TRANSPARENT); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); b.setTextColor(Color.WHITE); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); b.setBackgroundColor(Color.parseColor("#333333")); return b; }
    private LinearLayout createSlider(String t, String k, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); tv.setText(t + ": " + sb.getProgress()); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); l.addView(tv); l.addView(sb); return l; }
    private LinearLayout createConfigPage(String prefix) { LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); for(int i=0; i<5; i++) { page.addView(createSection(BAR_NAMES[i])); for(int j=0; j<7; j++) page.addView(createRow(GESTURE_NAMES[j], prefix + "_" + BARS[i] + "_" + GESTURES[j])); } page.addView(createSection("2 GÓC ĐÁY (ASSISTANT CURVE)")); page.addView(createRow("Góc Trái - Vuốt Chéo", prefix+"_l_corner")); page.addView(createRow("Góc Phải - Vuốt Chéo", prefix+"_r_corner")); return page; }
    private TextView createSection(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.GREEN); tv.setPadding(0,40,0,10); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); GradientDrawable g = new GradientDrawable(); g.setColor(Color.DKGRAY); g.setCornerRadius(10); sp.setBackground(g); sp.setPadding(10,10,10,10); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout createRow(String t, String k) { 
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,10,0,10); 
        TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
        Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); String v = prefs.getString(k,"NONE"); for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(v)) s.setSelection(i); s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString(k,ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); 
        CheckBox animCb = new CheckBox(this); animCb.setText("✨"); animCb.setTextColor(Color.YELLOW); animCb.setChecked(prefs.getBoolean(k+"_anim", true)); animCb.setOnCheckedChangeListener((v_,c)->prefs.edit().putBoolean(k+"_anim", c).apply());
        l.addView(tv); l.addView(s); l.addView(animCb); return l; 
    }
}
