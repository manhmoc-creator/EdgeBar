package com.manhmoc.edgebar;
import android.app.Activity; import android.app.AlertDialog; import android.app.DownloadManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.os.Environment; import android.provider.Settings; import android.net.Uri; import android.view.Gravity; import android.view.View; import android.widget.*; import org.json.JSONObject; import java.util.Iterator; import java.net.HttpURLConnection; import java.net.URL; import java.io.InputStreamReader; import java.io.BufferedReader;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi; 
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[24]; private String[] ACT_LABS = new String[24];
    private String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private String[] BAR_NAMES; 
    private String[] CORNERS = {"tl", "tr", "bl", "br"}; private String[] CORNER_NAMES;
    private String[] GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold"}; 
    private String[] GESTURE_NAMES; private String[] C_GESTURES = {"swipe", "hold"}; private String[] C_GESTURE_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE"};
    private String[] COLOR_NAMES;

    private LinearLayout pageDesign, pageGestures, pageIntents, designSliderContainer; private Button btnNavDes, btnNavGes, btnNavInt;
    private LinearLayout tabLock, tabHome; private Button btnLock, btnHome, btnEditLock, btnEditHome, btnEditAnim;
    private int designTabState = 0; private final String CURRENT_VERSION = "V19.7"; 

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        isVi = prefs.getBoolean("lang_vi", true); 
        
        BAR_NAMES = new String[]{T("LEFT BOTTOM", "ĐÁY TRÁI"), T("RIGHT BOTTOM", "ĐÁY PHẢI"), T("LEFT TOP", "ĐỈNH TRÁI"), T("RIGHT TOP", "ĐỈNH PHẢI"), T("CENTER TOP", "ĐỈNH GIỮA")};
        CORNER_NAMES = new String[]{T("Top Left Corner", "Góc Đỉnh Trái"), T("Top Right Corner", "Góc Đỉnh Phải"), T("Bottom Left Corner", "Góc Đáy Trái"), T("Bottom Right Corner", "Góc Đáy Phải")};
        GESTURE_NAMES = new String[]{T("Tap", "1 Chạm"), T("Double Tap", "2 Chạm"), T("Long Press", "Nhấn Giữ"), T("Swipe Up", "Vuốt Lên"), T("Swipe Down", "Vuốt Xuống"), T("Swipe Left", "Vuốt Trái"), T("Swipe Right", "Vuốt Phải"), T("Up + Hold", "Vuốt Lên + Giữ"), T("Down + Hold", "Vuốt Xuống + Giữ"), T("Left + Hold", "Vuốt Trái + Giữ"), T("Right + Hold", "Vuốt Phải + Giữ")};
        C_GESTURE_NAMES = new String[]{T("Swipe Diagonal", "Vuốt Chéo"), T("Swipe + Hold", "Vuốt Chéo + Giữ")};
        COLOR_NAMES = new String[]{T("Pure White", "Trắng Tinh Khiết"), "Neon (Pink-Cyan)", "Cyberpunk (Purple-Gold)", "Lava (Red-Orange)", "Ocean (Blue-Cyan)", "Matrix (Green)", "Sunset (Purple-Orange)", "Google (4 Colors)"};

        String[] bK = {"NONE", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "QR", "NOTIFICATIONS"}; 
        String[] bL = {T("None", "Không có"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume Panel", "Menu Âm Lượng"), T("Screenshot", "Chụp ảnh màn hình"), "Camera", "Google Lens", T("Notifications", "Mở Thông Báo")};
        for(int i=0; i<9; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } for(int i=1; i<=15; i++) { ACT_KEYS[8+i]="INTENT_"+i; ACT_LABS[8+i]=T("Fire Intent ", "Gửi Intent ")+i; }

        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.parseColor("#121212")); LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,40,40,100);
        
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0,0,0,30);
        TextView title = new TextView(this); title.setText("⚙️ Edge Bar " + CURRENT_VERSION + "\nThe Flawless"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        
        Button btnLang = new Button(this); btnLang.setText(isVi ? "🇻🇳 VI" : "🇺🇸 EN"); btnLang.setTextColor(Color.WHITE); btnLang.setBackground(getRounded("#2E7D32", 20f)); LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(-2, -2); lpL.setMargins(0,0,15,0); btnLang.setLayoutParams(lpL);
        btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        
        Button btnUpdate = new Button(this); btnUpdate.setText(T("CHECK\nUPDATE", "CẬP\nNHẬT")); btnUpdate.setTextColor(Color.parseColor("#00E5FF")); btnUpdate.setBackground(getRounded("#1E1E1E", 25f)); btnUpdate.setPadding(30,20,30,20);
        header.addView(title); header.addView(btnLang); header.addView(btnUpdate); main.addView(header);
        
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText(T("⚠️ GRANT OVERLAY PERMISSION", "⚠️ CẤP QUYỀN VẼ ĐÈ LỚP PHỦ")); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0, 0, 0, 40);
        btnNavDes = createNavBtn(T("DESIGN", "THIẾT KẾ")); btnNavGes = createNavBtn(T("GESTURES", "CỬ CHỈ")); btnNavInt = createNavBtn("INTENTS");
        nav.addView(btnNavDes); nav.addView(btnNavGes); nav.addView(btnNavInt); main.addView(nav);
        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageGestures = new LinearLayout(this); pageGestures.setOrientation(LinearLayout.VERTICAL); pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL);

        LinearLayout backupRow = new LinearLayout(this); backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnBackup = new Button(this); btnBackup.setText(T("💾 BACKUP", "💾 SAO LƯU")); btnBackup.setBackground(getRounded("#2E7D32", 20f)); btnBackup.setTextColor(Color.WHITE); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); bp.setMargins(0,0,15,0); btnBackup.setLayoutParams(bp);
        Button btnRestore = new Button(this); btnRestore.setText(T("📂 RESTORE", "📂 PHỤC HỒI")); btnRestore.setBackground(getRounded("#EF6C00", 20f)); btnRestore.setTextColor(Color.WHITE); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); rp.setMargins(15,0,0,0); btnRestore.setLayoutParams(rp);
        btnBackup.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup.txt"); startActivityForResult(i, 101); });
        backupRow.addView(btnBackup); backupRow.addView(btnRestore); pageDesign.addView(wrapCard(backupRow));

        LinearLayout secSys = new LinearLayout(this); secSys.setOrientation(LinearLayout.VERTICAL);
        secSys.addView(createSectionTitle(T("SYSTEM BEHAVIOR", "HÀNH VI HỆ THỐNG")));
        CheckBox cbKbd = new CheckBox(this); cbKbd.setText(T("Auto-hide when Gboard is active", "Tự ẩn khi hiện Bàn Phím (Gboard)")); cbKbd.setTextColor(Color.WHITE); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true)); cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply()); secSys.addView(cbKbd);
        secSys.addView(createSectionTitle(T("BLACKLIST (Auto-hide Overlay)", "DANH SÁCH ĐEN (Tự ẩn Lớp phủ)"))); secSys.addView(createInput("Package Names (com.example.app)", "blacklist"));
        pageDesign.addView(wrapCard(secSys));

        LinearLayout secVisual = new LinearLayout(this); secVisual.setOrientation(LinearLayout.VERTICAL);
        secVisual.addView(createSectionTitle(T("VISUAL TUNING", "TUỲ CHỈNH GIAO DIỆN")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(bp);
        btnEditHome = new Button(this); btnEditHome.setText("HOME"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP);
        btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(rp);
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0);
        btnEditLock.setOnClickListener(v -> { designTabState=0; updateVisTabs(); renderSliders(); }); btnEditHome.setOnClickListener(v -> { designTabState=1; updateVisTabs(); renderSliders(); }); btnEditAnim.setOnClickListener(v -> { designTabState=2; updateVisTabs(); renderSliders(); });
        toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditAnim); secVisual.addView(toggleRow); secVisual.addView(designSliderContainer);
        pageDesign.addView(wrapCard(secVisual)); btnEditLock.performClick();

        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 20, 0, 20); 
        btnLock = createTabBtn("LOCKSCREEN"); btnHome = createTabBtn("HOMESCREEN"); btnLock.setLayoutParams(bp); btnHome.setLayoutParams(rp); tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageGestures.addView(tabContainer);
        tabLock = createConfigPage("lock"); tabHome = createConfigPage("home"); pageGestures.addView(tabLock); pageGestures.addView(tabHome); 
        btnLock.setOnClickListener(v -> switchGesTab(0)); btnHome.setOnClickListener(v -> switchGesTab(1)); switchGesTab(0);

        main.addView(pageDesign); main.addView(pageGestures); main.addView(pageIntents); btnNavDes.setOnClickListener(v->switchMainTab(0)); btnNavGes.setOnClickListener(v->switchMainTab(1)); btnNavInt.setOnClickListener(v->switchMainTab(2)); switchMainTab(1);
        scroll.addView(main); setContentView(scroll);
    }
    
    private void updateVisTabs() { btnEditLock.setBackground(getRounded(designTabState==0 ? "#00E5FF" : "#222222", 20f)); btnEditLock.setTextColor(designTabState==0 ? Color.BLACK : Color.WHITE); btnEditHome.setBackground(getRounded(designTabState==1 ? "#00E5FF" : "#222222", 20f)); btnEditHome.setTextColor(designTabState==1 ? Color.BLACK : Color.WHITE); btnEditAnim.setBackground(getRounded(designTabState==2 ? "#00E5FF" : "#222222", 20f)); btnEditAnim.setTextColor(designTabState==2 ? Color.BLACK : Color.WHITE); }
    
    private void renderSliders() { designSliderContainer.removeAllViews(); if(designTabState == 2) { 
        LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText(T("Theme:","Chủ đề:")); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); designSliderContainer.addView(lC); 
        LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText(T("Run Style:","Kiểu chạy:")); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{T("Fade (Blink)","Nhấp Nháy"), T("1 Dash","1 Tia sáng nối đuôi"), T("2 Dashes","2 Tia sáng đối xứng"), T("3 Dashes","3 Tia sáng đều nhau")})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); designSliderContainer.addView(lS); 
        designSliderContainer.addView(createSlider(T("Stroke Thickness","Độ dày viền"), "anim_thick", 50, 12)); designSliderContainer.addView(createSlider(T("Corner Radius","Độ bo góc"), "anim_rad", 100, 40)); designSliderContainer.addView(createSlider(T("Anim Duration (ms)","Thời gian Animation (ms)"), "anim_dur", 5000, 1500)); designSliderContainer.addView(createSlider(T("Hold Gesture Time (ms)","Thời gian Vuốt+Giữ"), "hold_dur", 2000, 600)); designSliderContainer.addView(createSlider(T("Vibration (ms)","Độ rung (ms)"), "vib_dur", 100, 30)); } else { String prefix = designTabState == 1 ? "home_" : "lock_"; 
        designSliderContainer.addView(createSectionTitle(T("5 EDGE BARS","5 THANH CẠNH")));
        for(int i=0; i<5; i++) { CheckBox cb = new CheckBox(this); cb.setText(T("ENABLE: ","BẬT: ") + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+BARS[idx]+"_en", c).apply()); designSliderContainer.addView(cb); designSliderContainer.addView(createSlider(T("Opacity","Độ trong suốt"), prefix+BARS[i]+"_alpha", 255, 50)); designSliderContainer.addView(createSlider(T("Width","Chiều ngang"), prefix+BARS[i]+"_w", 1400, 300)); designSliderContainer.addView(createSlider(T("Height","Chiều dọc"), prefix+BARS[i]+"_h", 1400, 60)); designSliderContainer.addView(createSlider(T("Pos X","Toạ độ X"), prefix+BARS[i]+"_x", 1000, 0)); designSliderContainer.addView(createSlider(T("Pos Y","Toạ độ Y"), prefix+BARS[i]+"_y", 1000, 0)); } 
        designSliderContainer.addView(createSectionTitle(T("4 FRAME CORNERS","4 GÓC VIỀN THỰC THỂ (TRĂNG NON)")));
        for(int i=0; i<4; i++) { CheckBox cb = new CheckBox(this); cb.setText(T("Enable: ","Bật: ") + CORNER_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+"corner_"+CORNERS[i]+"_en", true)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+"corner_"+CORNERS[idx]+"_en", c).apply()); designSliderContainer.addView(cb); }
        CheckBox cbInv = new CheckBox(this); cbInv.setText(T("Invisible Mode (Hide UI but keep actions)","Chế độ Tàng Hình (Ẩn khung nhưng vẫn nhận vuốt)")); cbInv.setTextColor(Color.parseColor("#FFC107")); cbInv.setChecked(prefs.getBoolean(prefix+"corner_invis", false)); cbInv.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+"corner_invis", c).apply()); designSliderContainer.addView(cbInv);
        designSliderContainer.addView(createSlider(T("Moon Opacity","Độ mờ Trăng non"), prefix+"corner_alpha", 255, 180)); designSliderContainer.addView(createSlider(T("Frame Width (X Distance)","Kéo giãn chiều Ngang (X)"), prefix+"corner_w", 1000, 0)); designSliderContainer.addView(createSlider(T("Frame Height (Y Distance)","Kéo giãn chiều Dọc (Y)"), prefix+"corner_h", 1000, 0)); designSliderContainer.addView(createSlider(T("Stroke Thickness","Độ đậm viền"), prefix+"corner_thick", 50, 8)); designSliderContainer.addView(createSlider(T("Curve Radius","Độ cong góc"), prefix+"corner_rad", 100, 40));
        } }

    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    private void switchMainTab(int idx) { pageDesign.setVisibility(idx==0?View.VISIBLE:View.GONE); pageGestures.setVisibility(idx==1?View.VISIBLE:View.GONE); pageIntents.setVisibility(idx==2?View.VISIBLE:View.GONE); btnNavDes.setBackground(getRounded(idx==0?"#222222":"#00000000", 20f)); btnNavDes.setTextColor(idx==0?Color.parseColor("#00E5FF"):Color.GRAY); btnNavGes.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); btnNavGes.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY); btnNavInt.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); btnNavInt.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY); }
    private void switchGesTab(int idx) { tabLock.setVisibility(idx==0?View.VISIBLE:View.GONE); tabHome.setVisibility(idx==1?View.VISIBLE:View.GONE); btnLock.setBackground(getRounded(idx==0?"#00E5FF":"#222222", 20f)); btnLock.setTextColor(idx==0?Color.BLACK:Color.WHITE); btnHome.setBackground(getRounded(idx==1?"#00E5FF":"#222222", 20f)); btnHome.setTextColor(idx==1?Color.BLACK:Color.WHITE); }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    
    private LinearLayout createSlider(String t, String k, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); }); row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; }

    private LinearLayout createConfigPage(String prefix) { LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); for(int i=0; i<5; i++) { LinearLayout sec = new LinearLayout(this); sec.setOrientation(LinearLayout.VERTICAL); sec.addView(createGestureHeader(BAR_NAMES[i])); for(int j=0; j<GESTURES.length; j++) sec.addView(createRow(GESTURE_NAMES[j], prefix + "_" + BARS[i] + "_" + GESTURES[j])); page.addView(wrapCard(sec)); } LinearLayout secC = new LinearLayout(this); secC.setOrientation(LinearLayout.VERTICAL); secC.addView(createSectionTitle(T("4 FRAME CORNERS","4 GÓC VIỀN"))); for(int i=0; i<4; i++) { secC.addView(createGestureHeader(CORNER_NAMES[i])); for(int j=0; j<2; j++) secC.addView(createRow(C_GESTURE_NAMES[j], prefix + "_corner_" + CORNERS[i] + "_" + C_GESTURES[j])); } page.addView(wrapCard(secC)); return page; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    
    // Header gộp chung title và các icon ✨ 📳 lên đỉnh
    private LinearLayout createGestureHeader(String titleText) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,20); TextView tv = new TextView(this); tv.setText(titleText); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); TextView tAnim = new TextView(this); tAnim.setText("✨"); tAnim.setPadding(10,0,20,0); TextView tVib = new TextView(this); tVib.setText("📳"); tVib.setPadding(10,0,10,0); l.addView(tv); l.addView(tAnim); l.addView(tVib); return l; }
    
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    
    // Bỏ chữ ở ô checkbox
    private LinearLayout createRow(String t, String k) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); String v = prefs.getString(k,"NONE"); for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(v)) s.setSelection(i); s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString(k,ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f)); CheckBox animCb = new CheckBox(this); animCb.setText(""); animCb.setChecked(prefs.getBoolean(k+"_anim", true)); animCb.setOnCheckedChangeListener((v_,c)->prefs.edit().putBoolean(k+"_anim", c).apply()); CheckBox vibCb = new CheckBox(this); vibCb.setText(""); vibCb.setChecked(prefs.getBoolean(k+"_vib", true)); vibCb.setOnCheckedChangeListener((v_,c)->prefs.edit().putBoolean(k+"_vib", c).apply()); l.addView(tv); l.addView(s); l.addView(animCb); l.addView(vibCb); return l; }
}
