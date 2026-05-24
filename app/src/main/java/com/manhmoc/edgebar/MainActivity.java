package com.manhmoc.edgebar;
import android.Manifest; import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.LauncherActivityInfo; import android.content.pm.LauncherApps; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Build; import android.os.Bundle; import android.os.UserHandle; import android.os.UserManager; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.View; import android.widget.*; import org.json.JSONObject; import java.util.ArrayList; import java.util.Iterator; import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[36]; private String[] ACT_LABS = new String[36];
    private String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; 
    private String[] BAR_NAMES = {"Đáy phải", "Đáy trái", "Cạnh Phải", "Cạnh Trái", "Đỉnh giữa"}; 
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; 
    private String[] CORNER_NAMES = {"Góc đáy phải", "Góc đáy trái", "Góc đỉnh phải", "Góc đỉnh trái"};
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; 
    private String[] COLOR_VALS = {"#FFFFFF", "#00FFFF", "#FFD700", "#FF4500", "#00BFFF", "#00FF00", "#FF8C00", "#EA4335", "#00E5FF", "#1DE9B6", "#4CAF50", "#FF9800", "#03A9F4", "#8BC34A", "#F06292"};
    private String[] COLOR_NAMES = {"Trắng Tinh Khiết", "Neon (Pink-Cyan)", "Cyberpunk (Purple-Gold)", "Lava (Red-Orange)", "Ocean (Blue-Cyan)", "Matrix (Green)", "Sunset (Purple-Orange)", "Google (4 Colors)", "Aurora (Cyan-Purple)", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};
    private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl"}; 
    private String[] ALL_COMP_NAMES = {"Thanh Đáy Phải", "Thanh Đáy Trái", "Thanh Cạnh Phải", "Thanh Cạnh Trái", "Thanh Đỉnh Giữa", "Góc Viền Đáy Phải", "Góc Viền Đáy Trái", "Góc Viền Đỉnh Phải", "Góc Viền Đỉnh Trái"};
    private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"}; 
    private String[] C_GESTURE_NAMES = {"1 Chạm", "2 Chạm", "Nhấn Giữ", "Vuốt Lên", "Vuốt Xuống", "Vuốt Trái", "Vuốt Phải", "Vuốt Lên + Giữ", "Vuốt Xuống + Giữ", "Vuốt Trái + Giữ", "Vuốt Phải + Giữ", "Vuốt Chéo", "Vuốt Chéo + Giữ"};

    private LinearLayout pageDesign, pageConditions, pageEcosystem, listRules, listEcosystem, designSliderContainer, navMain, morseTriggerContainer, listMorseTriggers; 
    private Button btnLock, btnHome, btnNavCond, btnNavAdv, btnEditLock, btnEditHome, btnEditAnim, btnEditMorse, fabRule, fabI, fabQ, fabM, btnUpdate, fabMorse;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; private boolean isMorseTriggerSpaceOpen = false;
    private final String CURRENT_VERSION = "V19.12.3.3.2"; private RelativeLayout rootLayout;
    private View morseOverlayView;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    
    private void refreshPreview() { 
        boolean pLock = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); 
        boolean pMorse = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==3);
        prefs.edit().putBoolean("preview_lock", pLock).putBoolean("preview_morse", pMorse).apply(); 
        
        // Quản lý hiển thị nút FAB
        if (fabRule != null) fabRule.setVisibility(currentMainTab == 1 && (pageDesign == null || pageDesign.getVisibility() == View.GONE) ? View.VISIBLE : View.GONE);
        if (fabMorse != null) fabMorse.setVisibility((designTabState == 3 && isMorseTriggerSpaceOpen) ? View.VISIBLE : View.GONE);
    }
    
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).putBoolean("preview_morse", false).apply(); if (morseOverlayView != null) toggleMorseOverlayPreview(); }
    @Override protected void onDestroy() { super.onDestroy(); if (morseOverlayView != null) toggleMorseOverlayPreview(); }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD", "VOICE_RECORD", "STOP_RECORD"}; 
        String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn hình chính"), T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume", "Menu Âm Lượng"), T("Screenshot", "Chụp ảnh màn"), T("Camera", "Camera"), T("Notifications", "Mở Thông Báo"), T("Toggle Acc", "Bật/Tắt Trợ Năng"), T("Toggle Overlay", "Bật/Tắt Lớp Phủ"), T("YTDL Download", "Tải bằng YTDL"), T("Stealth Record", "Bật Ghi Âm"), T("Stop Record", "Tắt Ghi Âm")};
        for(int i=0; i<16; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
        for(int i=1; i<=15; i++) { ACT_KEYS[15+i]="INTENT_"+i; ACT_LABS[15+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
        for(int i=1; i<=5; i++) { ACT_KEYS[30+i]="MACRO_"+i; ACT_LABS[30+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
    }

    @Override public void onBackPressed() { 
        if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { 
            if(isMorseTriggerSpaceOpen) { closeMorseTriggerSpace(); return; }
            closeDesignSpace(); Button btnD = rootLayout.findViewWithTag("btnDesign"); if(btnD!=null){btnD.setText("⚙️"); btnD.setTextColor(Color.WHITE);}
        } else super.onBackPressed(); 
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                if (requestCode == 101) { 
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    JSONObject json = new JSONObject(); java.util.Map<String, ?> allEntries = prefs.getAll();
                    for (java.util.Map.Entry<String, ?> entry : allEntries.entrySet()) json.put(entry.getKey(), entry.getValue());
                    os.write(json.toString().getBytes()); os.close(); Toast.makeText(this, "✅ Sao lưu thành công!", Toast.LENGTH_SHORT).show();
                } else if (requestCode == 102) { 
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder(); String line; while ((line = reader.readLine()) != null) sb.append(line);
                    is.close(); JSONObject json = new JSONObject(sb.toString()); SharedPreferences.Editor editor = prefs.edit();
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next(); Object value = json.get(key);
                        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                        else if (value instanceof Float) editor.putFloat(key, (Float) value);
                        else if (value instanceof Long) editor.putLong(key, (Long) value);
                        else if (value instanceof String) editor.putString(key, (String) value);
                    }
                    editor.apply(); Toast.makeText(this, "✅ Phục hồi thành công! Đang tải lại...", Toast.LENGTH_LONG).show(); recreate();
                }
            } catch (Exception e) { Toast.makeText(this, "❌ Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
        }
    }

    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(currentMainTab == 1 ? View.VISIBLE : View.GONE); pageEcosystem.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); fabI.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); fabQ.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); fabM.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); refreshPreview(); }
    private void openDesignSpace() { refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); fabI.setVisibility(View.GONE); fabQ.setVisibility(View.GONE); fabM.setVisibility(View.GONE); }
    private void openMorseTriggerSpace() { isMorseTriggerSpaceOpen = true; designSliderContainer.setVisibility(View.GONE); morseTriggerContainer.setVisibility(View.VISIBLE); refreshPreview(); renderMorseTriggersList(); }
    private void closeMorseTriggerSpace() { isMorseTriggerSpaceOpen = false; designSliderContainer.setVisibility(View.VISIBLE); morseTriggerContainer.setVisibility(View.GONE); refreshPreview(); }

    private Button createCircleBtn(String icon, String color, String txtColor) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.parseColor(txtColor)); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(color, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();

        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,60,40,40); 
        
        LinearLayout headerRow = new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setPadding(0, 0, 0, 50);
        TextView title = new TextView(this); title.setText("Edge Bar\n" + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnLang = new Button(this); btnLang.setText(isVi ? "🇻🇳 Tiếng Việt" : "🇺🇸 US-English"); btnLang.setTextColor(Color.WHITE); btnLang.setBackground(getRounded("#2E7D32", 20f)); btnLang.setPadding(30, 20, 30, 20); btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        headerRow.addView(title); headerRow.addView(btnLang); main.addView(headerRow);
        
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1); }
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText(T("⚠️ CẤP QUYỀN LỚP PHỦ", "⚠️ CẤP QUYỀN LỚP PHỦ")); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

        navMain = new LinearLayout(this); navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        btnNavCond = createNavBtn(T("CONDITIONS", "ĐIỀU KIỆN")); btnNavAdv = createNavBtn(T("ECOSYSTEM", "HỆ SINH THÁI")); 
        navMain.addView(btnNavCond); navMain.addView(btnNavAdv); main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); pageEcosystem.setVisibility(View.GONE); buildEcosystemSpace();
        
        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageEcosystem);
        scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        
        btnUpdate = createCircleBtn("U", "#333333", "#BBBBBB"); btnUpdate.setTextSize(20); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = new Button(this); btnPremium.setText("PRO"); btnPremium.setTextColor(Color.BLACK); btnPremium.setBackground(getRounded("#FFD700", 100f)); btnPremium.setOnClickListener(v -> showPremiumDialog()); LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1); pLp.setMargins(10,0,10,0); btnPremium.setLayoutParams(pLp); btnPremium.setPadding(30,0,30,0);
        View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        // FAB Gốc
        fabRule = new Button(this); fabRule.setText("+ NEW EB"); fabRule.setTextColor(Color.BLACK); fabRule.setBackground(getRounded("#00E5FF", 100f)); LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -1); fLp.setMargins(10,0,10,0); fabRule.setLayoutParams(fLp); fabRule.setPadding(40,0,40,0); 
        // FAB Morse Mới (ẩn mặc định, kích thước giống hệt fabRule)
        fabMorse = new Button(this); fabMorse.setText("+ NEW MORSE"); fabMorse.setTextColor(Color.BLACK); fabMorse.setBackground(getRounded("#E91E63", 100f)); fabMorse.setLayoutParams(fLp); fabMorse.setPadding(40,0,40,0); fabMorse.setVisibility(View.GONE);
        
        fabI = createCircleBtn("I", "#E53935", "#FFFFFF"); fabI.setVisibility(View.GONE);
        fabQ = createCircleBtn("QS", "#43A047", "#FFFFFF"); fabQ.setVisibility(View.GONE);
        fabM = createCircleBtn("M", "#1E88E5", "#FFFFFF"); fabM.setVisibility(View.GONE);
        Button btnDesign = createCircleBtn("⚙️", "#333333", "#FFFFFF"); btnDesign.setTag("btnDesign"); btnDesign.setOnClickListener(v -> { if(pageDesign.getVisibility() == View.VISIBLE) { if(isMorseTriggerSpaceOpen) closeMorseTriggerSpace(); else { closeDesignSpace(); btnDesign.setText("⚙️"); btnDesign.setTextColor(Color.WHITE); } } else { openDesignSpace(); btnDesign.setText("<"); btnDesign.setTextColor(Color.parseColor("#BBBBBB")); } });
        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); 
        bottomBar.addView(fabRule); bottomBar.addView(fabMorse); // Cùng vị trí, sẽ toggle visibility
        bottomBar.addView(fabI); bottomBar.addView(fabQ); bottomBar.addView(fabM); bottomBar.addView(btnDesign); rootLayout.addView(bottomBar);

        btnNavCond.setOnClickListener(v->switchMainTab(1)); btnNavAdv.setOnClickListener(v->switchMainTab(2)); 
        fabRule.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1));
        
        fabMorse.setOnClickListener(v -> { int slot = getEmptySlot("morse_pack_", 10); if(slot>0) showMorseTriggerEditor(slot); else Toast.makeText(this,"Đã đầy 10 Gói Morse!",Toast.LENGTH_SHORT).show(); });
        
        fabI.setOnClickListener(v -> { int slot = getEmptySlot("intent_", 15); if(slot>0) showIntentEditor(slot); else Toast.makeText(this,"Đã đầy 15 Intents!",Toast.LENGTH_SHORT).show(); }); 
        fabQ.setOnClickListener(v -> { int slot = getEmptySlot("tile_", 15); if(slot>0) showQsEditor(slot); else Toast.makeText(this,"Đã đầy 15 Tiles!",Toast.LENGTH_SHORT).show(); }); 
        fabM.setOnClickListener(v -> { int slot = getEmptySlot("macro_", 5); if(slot>0) showMacroEditor(slot); else Toast.makeText(this,"Đã đầy 5 Macros!",Toast.LENGTH_SHORT).show(); });
        switchMainTab(1); setContentView(rootLayout);
    }
    
    private int getEmptySlot(String prefix, int max) {
        for(int i=1; i<=max; i++) { if(prefs.getString(prefix+i+(prefix.startsWith("tile")?"_act":"_name"), "").isEmpty() || prefs.getString(prefix+i+(prefix.startsWith("tile")?"_act":"_name"), "").equals("NONE")) return i; } return -1;
    }

    private void switchMainTab(int idx) { 
        currentMainTab = idx; isMorseTriggerSpaceOpen = false; refreshPreview(); navMain.setVisibility(View.VISIBLE);
        pageDesign.setVisibility(View.GONE); pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE);
        pageEcosystem.setVisibility(idx==2?View.VISIBLE:View.GONE);
        btnNavCond.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); btnNavCond.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY); 
        btnNavAdv.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); btnNavAdv.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY); 
        fabI.setVisibility(idx == 2 ? View.VISIBLE : View.GONE); fabQ.setVisibility(idx == 2 ? View.VISIBLE : View.GONE); fabM.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if(idx==1) renderRulesList();
        if(idx==2) renderEcosystemList();
    }

    private void buildEcosystemSpace() {
        LinearLayout ytdlBox = new LinearLayout(this); ytdlBox.setOrientation(LinearLayout.HORIZONTAL); ytdlBox.setPadding(0,0,0,30); ytdlBox.setGravity(Gravity.CENTER_VERTICAL);
        EditText edtYtdl = createInput("Nhập tên bài hát / Link nhạc...", "temp_ytdl"); 
        edtYtdl.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnYtdl = new Button(this); btnYtdl.setText("🔍 YTDL"); btnYtdl.setBackground(getRounded("#E91E63", 20f)); btnYtdl.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -1); btnLp.setMargins(15,10,0,10); btnYtdl.setLayoutParams(btnLp);
        
        btnYtdl.setOnClickListener(v -> {
            String query = edtYtdl.getText().toString().trim();
            if(!query.isEmpty()) {
                Intent ytdl = new Intent(Intent.ACTION_SEND); ytdl.setType("text/plain"); ytdl.putExtra(Intent.EXTRA_TEXT, query); ytdl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ytdl.setPackage("com.deniscerri.ytdl"); startActivity(ytdl); } 
                catch(Exception e1) { try { ytdl.setPackage("com.deniscerri.ytdlnis"); startActivity(ytdl); } catch(Exception e2) { Toast.makeText(this, "Bạn chưa cài đặt YTDLnis!", Toast.LENGTH_SHORT).show(); } }
            }
        });
        ytdlBox.addView(edtYtdl); ytdlBox.addView(btnYtdl);
        pageEcosystem.addView(createSectionTitle("🎵 TÌM KIẾM NHANH YTDLNIS")); pageEcosystem.addView(ytdlBox); pageEcosystem.addView(createSectionTitle("📦 DANH SÁCH MODULE"));
        listEcosystem = new LinearLayout(this); listEcosystem.setOrientation(LinearLayout.VERTICAL); pageEcosystem.addView(listEcosystem);
    }

    private void buildConditionsSpace() {
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 0, 0, 20); 
        btnLock = createTabBtn("LOCKSCREEN"); btnHome = createTabBtn("HOMESCREEN"); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(0,0,15,0); btnLock.setLayoutParams(p); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); btnHome.setLayoutParams(rp);
        tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageConditions.addView(tabContainer); listRules = new LinearLayout(this); listRules.setOrientation(LinearLayout.VERTICAL); pageConditions.addView(listRules);
        btnLock.setOnClickListener(v -> { currentGesTab=0; refreshPreview(); btnLock.setBackground(getRounded("#00E5FF", 20f)); btnLock.setTextColor(Color.BLACK); btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE); renderRulesList(); }); 
        btnHome.setOnClickListener(v -> { currentGesTab=1; refreshPreview(); btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE); btnHome.setBackground(getRounded("#00E5FF", 20f)); btnHome.setTextColor(Color.BLACK); renderRulesList(); }); 
        btnLock.performClick();
    }

    private void renderRulesList() {
        listRules.removeAllViews(); String prefix = currentGesTab == 0 ? "lock_" : "home_"; LinearLayout currentRow = null; int count = 0;
        for (int c = 0; c < ALL_COMP_KEYS.length; c++) { for (int g = 0; g < C_GESTURES.length; g++) { String key = prefix + ALL_COMP_KEYS[c] + "_" + C_GESTURES[g]; String action = prefs.getString(key, "NONE");
                if (!action.equals("NONE")) {
                    if(count % 2 == 0) { currentRow = new LinearLayout(this); currentRow.setOrientation(LinearLayout.HORIZONTAL); currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listRules.addView(currentRow); }
                    LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2, 1f); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);
                    LinearLayout rowTop = new LinearLayout(this); rowTop.setOrientation(LinearLayout.HORIZONTAL); rowTop.setGravity(Gravity.CENTER_VERTICAL); Switch swOn = new Switch(this); swOn.setChecked(prefs.getBoolean(key+"_on", true)); swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean(key+"_on", chk).apply()); TextView tIcons = new TextView(this); tIcons.setText((prefs.getBoolean(key+"_vib",true)?"📳 ":"") + (prefs.getBoolean(key+"_anim",true)?"✨":"")); tIcons.setGravity(Gravity.RIGHT); tIcons.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); rowTop.addView(swOn); rowTop.addView(tIcons); card.addView(rowTop);
                    TextView tCond = new TextView(this); tCond.setText(ALL_COMP_NAMES[c] + "\n➔ " + C_GESTURE_NAMES[g]); tCond.setTextColor(Color.parseColor("#BBBBBB")); tCond.setTextSize(13); tCond.setPadding(0,15,0,15); card.addView(tCond);
                    TextView tAct = new TextView(this); String[] acts = action.split(","); StringBuilder actName = new StringBuilder(); for(String a : acts) { for(int i=0;i<ACT_KEYS.length;i++) { if(ACT_KEYS[i].equals(a.trim())) { if(actName.length()>0) actName.append(" + "); actName.append(ACT_LABS[i]); } } } tAct.setText(actName.toString().isEmpty() ? "Lỗi" : actName.toString()); tAct.setTextColor(Color.parseColor("#00E5FF")); tAct.setTextSize(15); card.addView(tAct);
                    final int finalC = c; final int finalG = g; card.setOnClickListener(v -> openRuleBuilderDialog(key, finalC, finalG)); card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Xóa Quy tắc?").setPositiveButton("XÓA", (d,w)->{prefs.edit().putString(key, "NONE").apply(); renderRulesList();}).setNegativeButton("HỦY", null).show(); return true; }); currentRow.addView(card); count++;
                } } }
        if(count % 2 != 0 && currentRow != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); currentRow.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText("Chưa có quy tắc nào.\nBấm + NEW EB để tạo."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listRules.addView(empty); }
    }

    private void renderEcosystemList() {
        listEcosystem.removeAllViews(); reloadActionLabels(); 
        int count = 0; LinearLayout row = null;
        for (int i = 1; i <= 15; i++) { String name = prefs.getString("intent_"+i+"_name", ""); if(!name.isEmpty()) { 
            String act = prefs.getString("intent_"+i+"_act", "Trống"); boolean isBr = prefs.getBoolean("intent_"+i+"_br", true);
            String detail = "Loại: " + (isBr ? "Broadcast" : "Activity") + "\nAction: " + act;
            if(count % 2 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listEcosystem.addView(row); }
            View card = createDetailedEcoCard("🔴 INTENT " + i, name, detail, "intent_"+i+"_on", true); final int idx = i; card.setOnClickListener(v -> showIntentEditor(idx)); 
            card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Xóa thẻ này?").setPositiveButton("XÓA", (d,w)->{prefs.edit().putString("intent_"+idx+"_name", "").putString("intent_"+idx+"_act", "NONE").apply(); renderEcosystemList(); reloadActionLabels();}).setNegativeButton("HỦY", null).show(); return true; });
            row.addView(card); count++; 
        } }
        for (int i = 1; i <= 15; i++) { String act = prefs.getString("tile_"+i+"_act", "NONE"); if(!act.equals("NONE")) { 
            String actName = "Lỗi"; for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(act)) actName = ACT_LABS[j]; 
            ComponentName comp = new ComponentName(this, "com.manhmoc.edgebar.Tile" + i); boolean isEnabled = getPackageManager().getComponentEnabledSetting(comp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            String status = isEnabled ? "🟢 Đã lên Quick Settings" : "⚫ Đang ẩn";
            if(count % 2 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listEcosystem.addView(row); }
            View card = createDetailedEcoCard("🟢 QS TILE " + i, actName, status, "tile_"+i+"_on", isEnabled); final int idx = i; card.setOnClickListener(v -> showQsEditor(idx)); 
            card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Xóa thẻ này?").setPositiveButton("XÓA", (d,w)->{prefs.edit().putString("tile_"+idx+"_act", "NONE").apply(); getPackageManager().setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); renderEcosystemList(); reloadActionLabels();}).setNegativeButton("HỦY", null).show(); return true; });
            row.addView(card); count++; 
        } }
        for (int i = 1; i <= 5; i++) { String name = prefs.getString("macro_"+i+"_name", ""); if(!name.isEmpty()) { 
            if(count % 2 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listEcosystem.addView(row); }
            View card = createDetailedEcoCard("🔵 MACRO " + i, name, "Svc: " + prefs.getString("macro_"+i+"_svcs", "Trống"), "macro_"+i+"_on", true); final int idx = i; card.setOnClickListener(v -> showMacroEditor(idx)); 
            card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Xóa thẻ này?").setPositiveButton("XÓA", (d,w)->{prefs.edit().putString("macro_"+idx+"_name", "").putString("macro_"+idx+"_svcs", "").apply(); renderEcosystemList(); reloadActionLabels();}).setNegativeButton("HỦY", null).show(); return true; });
            row.addView(card); count++; 
        } }
        if(count % 2 != 0 && row != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); row.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText("Hệ sinh thái đang trống.\nSử dụng các nút I, QS, M bên dưới để thêm module."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listEcosystem.addView(empty); }
    }

    private View createDetailedEcoCard(String title, String desc, String detail, String prefKey, boolean defState) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2, 1f); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tTitle = new TextView(this); tTitle.setText(title); tTitle.setTextColor(Color.parseColor("#BBBBBB")); tTitle.setTextSize(12); tTitle.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Switch sw = new Switch(this); sw.setChecked(prefs.getBoolean(prefKey, defState)); 
        sw.setOnCheckedChangeListener((v,c) -> { prefs.edit().putBoolean(prefKey, c).apply(); if(prefKey.startsWith("tile_")) { int tileIdx = Integer.parseInt(prefKey.replace("tile_","").replace("_on","")); ComponentName comp = new ComponentName(this, "com.manhmoc.edgebar.Tile" + tileIdx); getPackageManager().setComponentEnabledSetting(comp, c ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); renderEcosystemList(); } else if(prefKey.startsWith("morse_pack_")) { renderMorseTriggersList(); } });
        row.addView(tTitle); row.addView(sw); card.addView(row);
        TextView tDesc = new TextView(this); tDesc.setText(desc); tDesc.setTextColor(Color.parseColor("#00E5FF")); tDesc.setTextSize(15); tDesc.setPadding(0,10,0,5); card.addView(tDesc);
        TextView tDet = new TextView(this); tDet.setText(detail); tDet.setTextColor(Color.GRAY); tDet.setTextSize(10); tDet.setMaxLines(2); tDet.setEllipsize(TextUtils.TruncateAt.END); card.addView(tDet);
        return card;
    }

    private void showIntentEditor(int defaultSlot) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40); root.addView(createSectionTitle("🔴 CẤU HÌNH INTENT " + defaultSlot));
        EditText eName = createInput("Tên gợi nhớ (VD: Mở Facebook)", ""); EditText eAct = createInput("Action", ""); EditText ePkg = createInput("Package", ""); EditText eCls = createInput("Class Name", ""); EditText eData = createInput("Data URI (acc://...)", ""); EditText eCat = createInput("Categories", ""); EditText eFlags = createInput("Flags", ""); CheckBox cbBr = new CheckBox(this); cbBr.setText("Gửi dạng Broadcast"); cbBr.setTextColor(Color.WHITE);
        int i = defaultSlot; eName.setText(prefs.getString("intent_"+i+"_name", "")); eAct.setText(prefs.getString("intent_"+i+"_act", "")); ePkg.setText(prefs.getString("intent_"+i+"_pkg", "")); eCls.setText(prefs.getString("intent_"+i+"_cls", "")); eData.setText(prefs.getString("intent_"+i+"_data", "")); eCat.setText(prefs.getString("intent_"+i+"_cat", "")); eFlags.setText(prefs.getString("intent_"+i+"_flags", "")); cbBr.setChecked(prefs.getBoolean("intent_"+i+"_br", true));
        ScrollView scroll = new ScrollView(this); LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(eName); form.addView(eAct); form.addView(ePkg); form.addView(eCls); form.addView(eData); form.addView(eCat); form.addView(eFlags); form.addView(cbBr); scroll.addView(form); LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1,0,1f); scroll.setLayoutParams(sclp); root.addView(scroll);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0); Button btnC = new Button(this); btnC.setText("HỦY"); btnC.setBackground(getRounded("#333333", 20f)); btnC.setTextColor(Color.WHITE); btnC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Button btnS = new Button(this); btnS.setText("LƯU LẠI"); btnS.setBackground(getRounded("#4CAF50", 20f)); btnS.setTextColor(Color.WHITE); LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,-2,1f); btnLp.setMargins(20,0,0,0); btnS.setLayoutParams(btnLp);
        btnC.setOnClickListener(v -> d.dismiss()); btnS.setOnClickListener(v -> { prefs.edit().putString("intent_"+i+"_name", eName.getText().toString()).putString("intent_"+i+"_act", eAct.getText().toString()).putString("intent_"+i+"_pkg", ePkg.getText().toString()).putString("intent_"+i+"_cls", eCls.getText().toString()).putString("intent_"+i+"_data", eData.getText().toString()).putString("intent_"+i+"_cat", eCat.getText().toString()).putString("intent_"+i+"_flags", eFlags.getText().toString()).putBoolean("intent_"+i+"_br", cbBr.isChecked()).apply(); d.dismiss(); renderEcosystemList(); reloadActionLabels(); });
        footer.addView(btnC); footer.addView(btnS); root.addView(footer); d.setContentView(root); d.show();
    }

    private void showQsEditor(int defaultSlot) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40); root.addView(createSectionTitle("🟢 CẤU HÌNH QUICK SETTINGS TILE " + defaultSlot));
        Spinner spAct = createSpinner(); spAct.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); CheckBox cbShow = new CheckBox(this); cbShow.setText("Hiển thị Tile này lên thanh trạng thái"); cbShow.setTextColor(Color.parseColor("#4CAF50"));
        int i = defaultSlot; String v = prefs.getString("tile_"+i+"_act","NONE"); for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(v)) spAct.setSelection(j); ComponentName comp = new ComponentName(this, "com.manhmoc.edgebar.Tile" + i); cbShow.setChecked(getPackageManager().getComponentEnabledSetting(comp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); TextView tvA = new TextView(this); tvA.setText("Hành động thực thi khi bấm Tile:"); tvA.setTextColor(Color.GRAY); tvA.setPadding(0,20,0,10); form.addView(tvA); form.addView(spAct); form.addView(cbShow); LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1,0,1f); form.setLayoutParams(sclp); root.addView(form);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0); Button btnC = new Button(this); btnC.setText("HỦY"); btnC.setBackground(getRounded("#333333", 20f)); btnC.setTextColor(Color.WHITE); btnC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Button btnS = new Button(this); btnS.setText("LƯU LẠI"); btnS.setBackground(getRounded("#4CAF50", 20f)); btnS.setTextColor(Color.WHITE); LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,-2,1f); btnLp.setMargins(20,0,0,0); btnS.setLayoutParams(btnLp);
        btnC.setOnClickListener(v_ -> d.dismiss()); btnS.setOnClickListener(v_ -> { prefs.edit().putString("tile_"+i+"_act", ACT_KEYS[spAct.getSelectedItemPosition()]).apply(); getPackageManager().setComponentEnabledSetting(comp, cbShow.isChecked() ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); d.dismiss(); renderEcosystemList(); reloadActionLabels(); });
        footer.addView(btnC); footer.addView(btnS); root.addView(footer); d.setContentView(root); d.show();
    }

    private void showMacroEditor(int defaultSlot) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40); root.addView(createSectionTitle("🔵 CẤU HÌNH MACRO " + defaultSlot));
        EditText eName = createInput("Tên gợi nhớ Macro", ""); EditText eSvcs = createInput("Danh sách Services (com.a/.b, ...)", "");
        int i = defaultSlot; eName.setText(prefs.getString("macro_"+i+"_name", "")); eSvcs.setText(prefs.getString("macro_"+i+"_svcs", ""));
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(eName); form.addView(eSvcs); LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1,0,1f); form.setLayoutParams(sclp); root.addView(form);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0); Button btnC = new Button(this); btnC.setText("HỦY"); btnC.setBackground(getRounded("#333333", 20f)); btnC.setTextColor(Color.WHITE); btnC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Button btnS = new Button(this); btnS.setText("LƯU LẠI"); btnS.setBackground(getRounded("#4CAF50", 20f)); btnS.setTextColor(Color.WHITE); LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,-2,1f); btnLp.setMargins(20,0,0,0); btnS.setLayoutParams(btnLp);
        btnC.setOnClickListener(v_ -> d.dismiss()); btnS.setOnClickListener(v_ -> { prefs.edit().putString("macro_"+i+"_name", eName.getText().toString()).putString("macro_"+i+"_svcs", eSvcs.getText().toString()).apply(); d.dismiss(); renderEcosystemList(); reloadActionLabels(); });
        footer.addView(btnC); footer.addView(btnS); root.addView(footer); d.setContentView(root); d.show();
    }

    // --- CẤU HÌNH GÓI TRIGGER MORSE ---
    private void showMorseTriggerEditor(int defaultSlot) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40); root.addView(createSectionTitle("📦 CẤU HÌNH MORSE TRIGGER PACK " + defaultSlot));
        EditText eName = createInput("Tên gói (VD: Khóa Zalo)", ""); EditText eTriggers = createInput("Chuỗi hành động (VD: r_tap, l_up)", "");
        int i = defaultSlot; eName.setText(prefs.getString("morse_pack_"+i+"_name", "")); eTriggers.setText(prefs.getString("morse_pack_"+i+"_triggers", ""));
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.addView(eName); form.addView(eTriggers); LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1,0,1f); form.setLayoutParams(sclp); root.addView(form);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0); Button btnC = new Button(this); btnC.setText("HỦY"); btnC.setBackground(getRounded("#333333", 20f)); btnC.setTextColor(Color.WHITE); btnC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Button btnS = new Button(this); btnS.setText("LƯU LẠI"); btnS.setBackground(getRounded("#4CAF50", 20f)); btnS.setTextColor(Color.WHITE); LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,-2,1f); btnLp.setMargins(20,0,0,0); btnS.setLayoutParams(btnLp);
        btnC.setOnClickListener(v_ -> d.dismiss()); btnS.setOnClickListener(v_ -> { prefs.edit().putString("morse_pack_"+i+"_name", eName.getText().toString()).putString("morse_pack_"+i+"_triggers", eTriggers.getText().toString()).apply(); d.dismiss(); renderMorseTriggersList(); });
        footer.addView(btnC); footer.addView(btnS); root.addView(footer); d.setContentView(root); d.show();
    }

    private void renderMorseTriggersList() {
        if(listMorseTriggers == null) return;
        listMorseTriggers.removeAllViews();
        int count = 0; LinearLayout row = null;
        for (int i = 1; i <= 10; i++) { String name = prefs.getString("morse_pack_"+i+"_name", ""); if(!name.isEmpty()) { 
            String triggers = prefs.getString("morse_pack_"+i+"_triggers", "Trống");
            if(count % 2 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listMorseTriggers.addView(row); }
            View card = createDetailedEcoCard("📦 MORSE PACK " + i, name, "Chuỗi: " + triggers, "morse_pack_"+i+"_on", true); final int idx = i; card.setOnClickListener(v -> showMorseTriggerEditor(idx)); 
            card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle("Xóa gói này?").setPositiveButton("XÓA", (d,w)->{prefs.edit().putString("morse_pack_"+idx+"_name", "").putString("morse_pack_"+idx+"_triggers", "").apply(); renderMorseTriggersList();}).setNegativeButton("HỦY", null).show(); return true; });
            row.addView(card); count++; 
        } }
        if(count % 2 != 0 && row != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); row.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText("Chưa có gói Trigger nào.\nBấm + NEW MORSE bên dưới để tạo."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listMorseTriggers.addView(empty); }
    }

    private void openRuleBuilderDialog(String editKey, int preComp, int preGes) { Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar); d.setContentView(buildRuleEditor(d, editKey, preComp, preGes)); d.show(); }

    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes) {
        reloadActionLabels(); LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 120, 30, 30);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); Button bTrig = createTabBtn("TRIGGER"); Button bAct = createTabBtn("ACTION"); Button bOpt = createTabBtn("OPTIONS"); LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,-2,1f); tabLp.setMargins(10,0,10,0); bTrig.setLayoutParams(tabLp); bAct.setLayoutParams(tabLp); bOpt.setLayoutParams(tabLp); tabs.addView(bTrig); tabs.addView(bAct); tabs.addView(bOpt); root.addView(tabs);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f)); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,40,0,0); scroll.addView(content); root.addView(scroll);
        final int[] selectedComp = {preComp != -1 ? preComp : 0}; ArrayList<CheckBox> gestureBoxes = new ArrayList<>(); ArrayList<CheckBox> actionBoxes = new ArrayList<>(); CheckBox cbVib = new CheckBox(this); CheckBox cbAnim = new CheckBox(this);
        LinearLayout vTrig = new LinearLayout(this); vTrig.setOrientation(LinearLayout.VERTICAL); TextView tvC = new TextView(this); tvC.setText(T("1. COMPONENT", "1. CHỌN VÙNG")); tvC.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvC); Spinner spComp = createSpinner(); spComp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ALL_COMP_NAMES)); spComp.setSelection(selectedComp[0]); spComp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){selectedComp[0] = pos;}public void onNothingSelected(AdapterView<?> p){}}); vTrig.addView(spComp); TextView tvG = new TextView(this); tvG.setText(T("\n2. GESTURES (OR Logic)", "\n2. CHỌN CỬ CHỈ (Lệnh OR)")); tvG.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvG); for (int i=0; i<C_GESTURES.length; i++) { CheckBox cb = new CheckBox(this); cb.setText(C_GESTURE_NAMES[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0,20,0,20); if(editKey != null && i == preGes) cb.setChecked(true); gestureBoxes.add(cb); vTrig.addView(cb); }
        LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE); TextView tvA = new TextView(this); tvA.setText(T("EXECUTE ACTIONS", "CHỌN HÀNH ĐỘNG THỰC THI")); tvA.setTextColor(Color.parseColor("#00E5FF")); tvA.setPadding(0,0,0,20); vAct.addView(tvA); String savedActs = editKey != null ? prefs.getString(editKey, "") : ""; for (int i=1; i<ACT_LABS.length; i++) { CheckBox cbAct = new CheckBox(this); cbAct.setText(ACT_LABS[i]); cbAct.setTextColor(Color.WHITE); cbAct.setPadding(0,20,0,20); if(savedActs.contains(ACT_KEYS[i])) cbAct.setChecked(true); actionBoxes.add(cbAct); vAct.addView(cbAct); }
        LinearLayout vOpt = new LinearLayout(this); vOpt.setOrientation(LinearLayout.VERTICAL); vOpt.setVisibility(View.GONE); cbVib.setText(T("Haptic Feedback", "Bật Rung")); cbVib.setTextColor(Color.WHITE); cbVib.setChecked(editKey == null || prefs.getBoolean(editKey+"_vib", true)); vOpt.addView(cbVib); cbAnim.setText(T("Light Animation", "Bật Hiệu ứng Ánh sáng")); cbAnim.setTextColor(Color.WHITE); cbAnim.setChecked(editKey == null || prefs.getBoolean(editKey+"_anim", true)); vOpt.addView(cbAnim); content.addView(vTrig); content.addView(vAct); content.addView(vOpt);
        View.OnClickListener tabClick = v -> { bTrig.setBackground(getRounded(v==bTrig?"#00E5FF":"#222222", 15f)); bTrig.setTextColor(v==bTrig?Color.BLACK:Color.WHITE); bAct.setBackground(getRounded(v==bAct?"#00E5FF":"#222222", 15f)); bAct.setTextColor(v==bAct?Color.BLACK:Color.WHITE); bOpt.setBackground(getRounded(v==bOpt?"#00E5FF":"#222222", 15f)); bOpt.setTextColor(v==bOpt?Color.BLACK:Color.WHITE); vTrig.setVisibility(v==bTrig?View.VISIBLE:View.GONE); vAct.setVisibility(v==bAct?View.VISIBLE:View.GONE); vOpt.setVisibility(v==bOpt?View.VISIBLE:View.GONE); }; bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick); bOpt.setOnClickListener(tabClick); bTrig.performClick();
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY")); bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Button bSave = new Button(this); bSave.setText(T("SAVE RULE", "LƯU QUY TẮC")); bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp); footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        bCancel.setOnClickListener(v -> dialog.dismiss());
        bSave.setOnClickListener(v -> { ArrayList<String> acts = new ArrayList<>(); for(int i=0; i<actionBoxes.size(); i++) { if(actionBoxes.get(i).isChecked()) acts.add(ACT_KEYS[i+1]); } if(acts.isEmpty()) { Toast.makeText(this, "Empty Action!", Toast.LENGTH_SHORT).show(); return; } String joinedActions = TextUtils.join(",", acts); String prefix = currentGesTab == 0 ? "lock_" : "home_"; String compKey = ALL_COMP_KEYS[selectedComp[0]]; boolean hasChecked = false; if(editKey != null) prefs.edit().putString(editKey, "NONE").apply(); for(int i=0; i<gestureBoxes.size(); i++) { if(gestureBoxes.get(i).isChecked()) { hasChecked = true; String finalKey = prefix + compKey + "_" + C_GESTURES[i]; prefs.edit().putString(finalKey, joinedActions).putBoolean(finalKey+"_vib", cbVib.isChecked()).putBoolean(finalKey+"_anim", cbAnim.isChecked()).apply(); } } if(!hasChecked) { Toast.makeText(this, "Empty Trigger!", Toast.LENGTH_SHORT).show(); return; } renderRulesList(); dialog.dismiss(); }); return root;
    }

    private void buildDesignSpace() {
        pageDesign.addView(createSectionTitle(T("BACKUP & RESTORE", "KHU VỰC SAO LƯU")));
        LinearLayout backupRow = new LinearLayout(this); backupRow.setOrientation(LinearLayout.HORIZONTAL); Button btnBackup = new Button(this); btnBackup.setText("💾 SAO LƯU"); btnBackup.setBackground(getRounded("#2E7D32", 20f)); btnBackup.setTextColor(Color.WHITE); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); bp.setMargins(0,0,15,0); btnBackup.setLayoutParams(bp); Button btnRestore = new Button(this); btnRestore.setText("📂 PHỤC HỒI"); btnRestore.setBackground(getRounded("#EF6C00", 20f)); btnRestore.setTextColor(Color.WHITE); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); btnRestore.setLayoutParams(rp); 
        btnBackup.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup.txt"); startActivityForResult(i, 101); }); 
        btnRestore.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, 102); }); 
        backupRow.addView(btnBackup); backupRow.addView(btnRestore); pageDesign.addView(wrapCard(backupRow));

        LinearLayout secSys = new LinearLayout(this); secSys.setOrientation(LinearLayout.VERTICAL); secSys.addView(createSectionTitle(T("SYSTEM BEHAVIOR", "HÀNH VI HỆ THỐNG"))); CheckBox cbKbd = new CheckBox(this); cbKbd.setText(T("Hide on Keyboard", "Tự ẩn khi hiện Bàn Phím")); cbKbd.setTextColor(Color.WHITE); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true)); cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply()); secSys.addView(cbKbd); secSys.addView(createSectionTitle(T("BLACKLIST", "DANH SÁCH ĐEN"))); secSys.addView(createInput("Packages (com.ex.app)", "blacklist")); pageDesign.addView(wrapCard(secSys));
        
        pageDesign.addView(createSectionTitle(T("CORE DESIGN", "THIẾT KẾ CỐT LÕI")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL); 
        btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
        btnEditHome = new Button(this); btnEditHome.setText("HOME"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP); 
        btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
        btnEditMorse = new Button(this); btnEditMorse.setText("MORSE"); LinearLayout.LayoutParams mP2 = new LinearLayout.LayoutParams(0, -2, 1f); mP2.setMargins(10,0,0,0); btnEditMorse.setLayoutParams(mP2); 
        
        // VÙNG HIỂN THỊ MORSE TRIGGER PACK (MẶC ĐỊNH ẨN)
        morseTriggerContainer = new LinearLayout(this); morseTriggerContainer.setOrientation(LinearLayout.VERTICAL); morseTriggerContainer.setVisibility(View.GONE);
        morseTriggerContainer.addView(createSectionTitle("🚀 DANH SÁCH MORSE TRIGGER PACK"));
        listMorseTriggers = new LinearLayout(this); listMorseTriggers.setOrientation(LinearLayout.VERTICAL); morseTriggerContainer.addView(listMorseTriggers);
        
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0); 
        
        btnEditLock.setOnClickListener(v -> { isMorseTriggerSpaceOpen = false; designTabState=0; refreshPreview(); btnEditLock.setBackground(getRounded("#00E5FF", 20f)); btnEditLock.setTextColor(Color.BLACK); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); btnEditMorse.setBackground(getRounded("#222222", 20f)); btnEditMorse.setTextColor(Color.WHITE); morseTriggerContainer.setVisibility(View.GONE); designSliderContainer.setVisibility(View.VISIBLE); renderSliders(); }); 
        btnEditHome.setOnClickListener(v -> { isMorseTriggerSpaceOpen = false; designTabState=1; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#00E5FF", 20f)); btnEditHome.setTextColor(Color.BLACK); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); btnEditMorse.setBackground(getRounded("#222222", 20f)); btnEditMorse.setTextColor(Color.WHITE); morseTriggerContainer.setVisibility(View.GONE); designSliderContainer.setVisibility(View.VISIBLE); renderSliders(); }); 
        btnEditAnim.setOnClickListener(v -> { isMorseTriggerSpaceOpen = false; designTabState=2; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#00E5FF", 20f)); btnEditAnim.setTextColor(Color.BLACK); btnEditMorse.setBackground(getRounded("#222222", 20f)); btnEditMorse.setTextColor(Color.WHITE); morseTriggerContainer.setVisibility(View.GONE); designSliderContainer.setVisibility(View.VISIBLE); renderSliders(); }); 
        btnEditMorse.setOnClickListener(v -> { designTabState=3; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); btnEditMorse.setBackground(getRounded("#00E5FF", 20f)); btnEditMorse.setTextColor(Color.BLACK); 
            if(isMorseTriggerSpaceOpen) { designSliderContainer.setVisibility(View.GONE); morseTriggerContainer.setVisibility(View.VISIBLE); } else { morseTriggerContainer.setVisibility(View.GONE); designSliderContainer.setVisibility(View.VISIBLE); renderSliders(); }
        });

        toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditAnim); toggleRow.addView(btnEditMorse); 
        pageDesign.addView(toggleRow); pageDesign.addView(morseTriggerContainer); pageDesign.addView(designSliderContainer); btnEditLock.performClick();
    }

    private void renderSliders() { designSliderContainer.removeAllViews(); 
        if(designTabState == 2) { 
            // --- DRAWERS ANIMATION VIỀN CƠ BẢN ---
            LinearLayout edgeAnimContent = new LinearLayout(this); edgeAnimContent.setOrientation(LinearLayout.VERTICAL); edgeAnimContent.setPadding(10,10,10,10);
            Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG VIỀN"); btnTest.setBackground(getRounded("#FFC107", 20f)); btnTest.setTextColor(Color.BLACK); btnTest.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2); testLp.setMargins(0,0,0,20); btnTest.setLayoutParams(testLp); btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); i.setPackage(getPackageName()); sendBroadcast(i); }); edgeAnimContent.addView(btnTest);
            LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText("Chủ đề:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); 
            
            final boolean[] firstRunC = {true};
            sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){ if(firstRunC[0]) { firstRunC[0] = false; return; } prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply(); sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName()));}public void onNothingSelected(AdapterView<?> p){}}); 
            
            sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); edgeAnimContent.addView(lC); 
            LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText("Kiểu chạy:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Nhấp Nháy", "1 Tia sáng nối đuôi", "2 Tia sáng đối xứng", "3 Tia sáng đều nhau"})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); edgeAnimContent.addView(lS); 
            edgeAnimContent.addView(createSignedSlider("Chiều ngang Hiệu ứng", "anim_w", -2000, 2000, 0)); edgeAnimContent.addView(createSignedSlider("Chiều dọc Hiệu ứng", "anim_h", -3500, 3500, 0)); edgeAnimContent.addView(createSlider("Độ đậm mờ hiệu ứng (Alpha)", "anim_alpha", 255, 255)); edgeAnimContent.addView(createSlider("Độ dày viền", "anim_thick", 50, 12)); edgeAnimContent.addView(createSlider("Thời gian Animation (ms)", "anim_dur", 5000, 1500)); edgeAnimContent.addView(createSlider("Thời gian Vuốt+Giữ (All)", "hold_dur", 2000, 600)); edgeAnimContent.addView(createSlider("Độ rung (ms) (All)", "vib_dur", 100, 30)); 
            designSliderContainer.addView(createDrawer("✨ HIỆU ỨNG ÁNH SÁNG (VIỀN)", edgeAnimContent));
            
            // --- DRAWERS BREATH RECORDER ---
            LinearLayout recSys = new LinearLayout(this); recSys.setOrientation(LinearLayout.VERTICAL); recSys.setPadding(10,10,10,10);
            Button btnTestBreath = new Button(this); btnTestBreath.setText("▶ THỬ ANIMATION HƠI THỞ"); btnTestBreath.setBackground(getRounded("#E91E63", 20f)); btnTestBreath.setTextColor(Color.WHITE); btnTestBreath.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp2 = new LinearLayout.LayoutParams(-1,-2); testLp2.setMargins(0,0,0,20); btnTestBreath.setLayoutParams(testLp2); btnTestBreath.setOnClickListener(v -> sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH"))); recSys.addView(btnTestBreath);
            Button btnStopBreath = new Button(this); btnStopBreath.setText("⏹ DỪNG THỬ"); btnStopBreath.setBackground(getRounded("#333333", 20f)); btnStopBreath.setTextColor(Color.WHITE); btnStopBreath.setPadding(0,30,0,30); LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1,-2); stopLp.setMargins(0,0,0,20); btnStopBreath.setLayoutParams(stopLp); btnStopBreath.setOnClickListener(v -> sendBroadcast(new Intent("com.manhmoc.edgebar.STOP_BREATH"))); recSys.addView(btnStopBreath);
            
            recSys.addView(createComboDropdown("Cơ chế Ghi âm", "record_engine", new String[]{"Tự động (Lock: Trợ năng / Home: Lớp phủ)", "Luôn dùng Lớp Phủ (Cần ADB)", "Luôn dùng Trợ Năng"}, 0));
            CheckBox cbCover = new CheckBox(this); cbCover.setText("Phủ lên cả Thanh trạng thái (Status bar)"); cbCover.setTextColor(Color.WHITE); cbCover.setPadding(0,10,0,20); cbCover.setChecked(prefs.getBoolean("breath_cover_status", true)); cbCover.setOnCheckedChangeListener((v,c)->prefs.edit().putBoolean("breath_cover_status", c).apply()); recSys.addView(cbCover);
            
            recSys.addView(createComboDropdown("Hình dáng Hơi Thở", "breath_shape", new String[]{"Dải Viền (Edge)", "Chấm Tròn (Dot)"}, 0));
            recSys.addView(createSlider("Khoảng nghỉ nhịp thở (ms)", "breath_delay", 5000, 1500));
            recSys.addView(createSlider("Độ mờ tối đa (Alpha)", "breath_alpha", 255, 255));
            
            TextView tEdge = new TextView(this); tEdge.setText("Cấu hình Dải Viền:"); tEdge.setTextColor(Color.GRAY); recSys.addView(tEdge);
            recSys.addView(createSignedSlider("Chiều ngang", "breath_w", -2000, 2000, 0)); recSys.addView(createSignedSlider("Chiều dọc", "breath_h", -3500, 3500, 0)); recSys.addView(createSlider("Độ dày viền", "breath_thick", 50, 12));
            
            TextView tDot = new TextView(this); tDot.setText("Cấu hình Chấm Tròn:"); tDot.setTextColor(Color.GRAY); recSys.addView(tDot);
            recSys.addView(createSlider("Độ to chấm tròn", "breath_dot_size", 400, 40)); recSys.addView(createSignedSlider("Toạ độ X", "breath_dot_x", -1500, 1500, 0)); recSys.addView(createSignedSlider("Toạ độ Y", "breath_dot_y", -3000, 3000, 0));
            designSliderContainer.addView(createDrawer("🎤 BREATH RECORDER & GHI ÂM TÀNG HÌNH", recSys));
            
        } else if (designTabState == 0 || designTabState == 1 || designTabState == 3) { 
            String prefix = designTabState == 1 ? "home_" : (designTabState == 3 ? "morse_" : "lock_"); 
            
            // UI MORSE: Thêm nút truy cập Trigger Pack
            if (designTabState == 3) {
                LinearLayout overlayBox = new LinearLayout(this); overlayBox.setOrientation(LinearLayout.VERTICAL); overlayBox.setPadding(30,10,30,30);
                overlayBox.addView(createSlider("Độ mờ vùng Lớp Phủ Khoá (Alpha)", "morse_overlay_alpha", 255, 180));
                
                LinearLayout appSelectRow = new LinearLayout(this); appSelectRow.setOrientation(LinearLayout.HORIZONTAL); appSelectRow.setGravity(Gravity.CENTER_VERTICAL); appSelectRow.setPadding(35, 35, 35, 35); appSelectRow.setBackground(getRounded("#2C2C2C", 20f));
                LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(-1, -2); lpRow.setMargins(0, 15, 0, 15); appSelectRow.setLayoutParams(lpRow);
                LinearLayout textCol = new LinearLayout(this); textCol.setOrientation(LinearLayout.VERTICAL); textCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                TextView tvAppTitle = new TextView(this); tvAppTitle.setText("Choose Apps"); tvAppTitle.setTextColor(Color.WHITE); tvAppTitle.setTextSize(16);
                TextView tvAppDesc = new TextView(this); tvAppDesc.setText("Select apps for Morse lock"); tvAppDesc.setTextColor(Color.GRAY); tvAppDesc.setTextSize(12);
                textCol.addView(tvAppTitle); textCol.addView(tvAppDesc);

                TextView tvAppCount = new TextView(this); tvAppCount.setTextColor(Color.parseColor("#BBBBBB")); tvAppCount.setTextSize(16); tvAppCount.setGravity(Gravity.RIGHT);
                String savedApps = prefs.getString("morse_locked_apps", ""); int count = 0; if (!savedApps.isEmpty()) { String[] split = savedApps.split(","); for(String s : split) if(!s.trim().isEmpty()) count++; }
                tvAppCount.setText(count + " Apps");

                appSelectRow.addView(textCol); appSelectRow.addView(tvAppCount);
                appSelectRow.setOnClickListener(v -> showAppSelectorDialog(tvAppCount));
                overlayBox.addView(appSelectRow);
                
                Button btnTestOverlay = new Button(this); btnTestOverlay.setText("👁️ BẬT/TẮT THỬ LỚP PHỦ"); btnTestOverlay.setBackground(getRounded("#FFC107", 20f)); btnTestOverlay.setTextColor(Color.BLACK);
                LinearLayout.LayoutParams bp1 = new LinearLayout.LayoutParams(-1,-2); bp1.setMargins(0,15,0,15); btnTestOverlay.setLayoutParams(bp1);
                btnTestOverlay.setOnClickListener(v -> toggleMorseOverlayPreview());
                overlayBox.addView(btnTestOverlay);
                
                // NÚT MỞ KHÔNG GIAN TRIGGER PACK
                Button btnTriggerPack = new Button(this); btnTriggerPack.setText("📦 QUẢN LÝ TRIGGER PACK"); btnTriggerPack.setBackground(getRounded("#E91E63", 20f)); btnTriggerPack.setTextColor(Color.WHITE);
                btnTriggerPack.setLayoutParams(bp1); btnTriggerPack.setOnClickListener(v -> openMorseTriggerSpace());
                overlayBox.addView(btnTriggerPack);
                
                designSliderContainer.addView(createDrawer("🛡️ CẤU HÌNH LỚP PHỦ KHÓA & PACKS", overlayBox));
            }

            designSliderContainer.addView(createSectionTitle("5 EDGE BARS"));
            for(int i=0; i< 5; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+BARS[idx]+"_en", c).apply()); drawerContent.addView(cb); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+BARS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createSlider("Độ trong suốt", prefix+BARS[i]+"_alpha", 255, 50)); drawerContent.addView(createSlider("Chiều ngang", prefix+BARS[i]+"_w", 3000, 300)); drawerContent.addView(createSlider("Chiều dọc", prefix+BARS[i]+"_h", 3000, 60)); drawerContent.addView(createSignedSlider("Toạ độ X", prefix+BARS[i]+"_x", -1500, 1500, 0)); drawerContent.addView(createSignedSlider("Toạ độ Y", prefix+BARS[i]+"_y", -1500, 1500, 0)); designSliderContainer.addView(createDrawer(BAR_NAMES[i], drawerContent)); } 
            designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS"));
            for(int i=0; i<4; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); drawerContent.addView(createComboDropdown("Hiển thị", prefix+"corner_"+CORNERS[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0)); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+"corner_"+CORNERS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix+"corner_"+CORNERS[i]+"_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix+"corner_"+CORNERS[i]+"_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix+"corner_"+CORNERS[i]+"_h", 2500, 100)); drawerContent.addView(createSignedSlider("Di chuyển Ngang (X)", prefix+"corner_"+CORNERS[i]+"_x", -1500, 1500, 0)); drawerContent.addView(createSignedSlider("Di chuyển Dọc (Y)", prefix+"corner_"+CORNERS[i]+"_y", -1500, 1500, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix+"corner_"+CORNERS[i]+"_moon_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix+"corner_"+CORNERS[i]+"_moon_h", 2500, 100)); drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_x", 2500, 1250)); drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_y", 2500, 1250)); drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_rad", 1000, 80)); drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_moon_rad", 1000, 80)); designSliderContainer.addView(createDrawer(CORNER_NAMES[i], drawerContent)); }
            LinearLayout globalDrawer = new LinearLayout(this); globalDrawer.setOrientation(LinearLayout.VERTICAL); globalDrawer.setPadding(30,10,30,30); globalDrawer.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500)); globalDrawer.addView(createSlider("Độ mờ vùng TRĂNG NON (Đậm/Nhạt)", prefix+"corner_moon_alpha", 255, 100)); globalDrawer.addView(createSlider("Độ mờ VIỀN GÓC (Đậm/Nhạt)", prefix+"corner_stroke_alpha", 255, 200)); globalDrawer.addView(createSlider("Độ đậm viền (Dày/Mỏng)", prefix+"corner_thick", 50, 8)); designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawer));
        } 
    }

    // --- CẬP NHẬT: THUẬT TOÁN QUÉT APP XUYÊN THẤU VÀO ISLAND/WORK PROFILE BẰNG LAUNCHER APPS ---
    private void showAppSelectorDialog(TextView tvCountToUpdate) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40);
        root.addView(createSectionTitle("CHỌN APP SẼ BỊ KHÓA BỞI MORSE"));
        
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(-1,0,1f); scroll.setLayoutParams(sclp);

        LauncherApps launcherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        List<UserHandle> profiles = userManager.getUserProfiles();

        String savedApps = prefs.getString("morse_locked_apps", "");
        ArrayList<CheckBox> checkBoxes = new ArrayList<>();
        ArrayList<String> packages = new ArrayList<>();
        
        // Quét xuyên tất cả profile
        class AppItem implements Comparable<AppItem> { String pkg; String name; AppItem(String p, String n){ pkg=p; name=n; } public int compareTo(AppItem o) { return name.compareToIgnoreCase(o.name); } }
        ArrayList<AppItem> appList = new ArrayList<>();
        
        for (UserHandle profile : profiles) {
            List<LauncherActivityInfo> apps = launcherApps.getActivityList(null, profile);
            for (LauncherActivityInfo info : apps) {
                String pkg = info.getComponentName().getPackageName();
                String name = info.getLabel().toString() + (profile.equals(android.os.Process.myUserHandle()) ? "" : " 💼 (Đảo)");
                boolean alreadyAdded = false;
                for(AppItem item : appList) { if(item.pkg.equals(pkg)) { alreadyAdded = true; break; } }
                if(!alreadyAdded) appList.add(new AppItem(pkg, name));
            }
        }
        
        java.util.Collections.sort(appList);

        for(AppItem item : appList) {
            CheckBox cb = new CheckBox(this); cb.setText(item.name + "\n(" + item.pkg + ")"); cb.setTextColor(Color.WHITE); cb.setPadding(0,25,0,25);
            if(savedApps.contains(item.pkg)) cb.setChecked(true);
            list.addView(cb); checkBoxes.add(cb); packages.add(item.pkg);
        }
        root.addView(scroll);

        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
        Button btnC = new Button(this); btnC.setText("HỦY"); btnC.setBackground(getRounded("#333333", 20f)); btnC.setTextColor(Color.WHITE); btnC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button btnS = new Button(this); btnS.setText("LƯU LẠI"); btnS.setBackground(getRounded("#4CAF50", 20f)); btnS.setTextColor(Color.WHITE); LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,-2,1f); btnLp.setMargins(20,0,0,0); btnS.setLayoutParams(btnLp);
        btnC.setOnClickListener(v -> d.dismiss());
        btnS.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder(); int finalCount = 0;
            for(int i=0; i<checkBoxes.size(); i++) { if(checkBoxes.get(i).isChecked()) { sb.append(packages.get(i)).append(","); finalCount++; } }
            prefs.edit().putString("morse_locked_apps", sb.toString()).apply();
            if (tvCountToUpdate != null) tvCountToUpdate.setText(finalCount + " Apps");
            d.dismiss(); Toast.makeText(this, "Đã lưu danh sách app bị khóa!", Toast.LENGTH_SHORT).show();
        });
        footer.addView(btnC); footer.addView(btnS); root.addView(footer); d.setContentView(root); d.show();
    }

    private void toggleMorseOverlayPreview() {
        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        if (morseOverlayView != null) { 
            wm.removeView(morseOverlayView); morseOverlayView = null; 
            Toast.makeText(this, "Đã tắt Lớp phủ Morse", Toast.LENGTH_SHORT).show(); return; 
        }
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this,"Cần cấp quyền Lớp phủ trước!", Toast.LENGTH_SHORT).show(); return; }
        
        morseOverlayView = new View(this);
        int alpha = prefs.getInt("morse_overlay_alpha", 180);
        morseOverlayView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        
        int flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.os.Build.VERSION.SDK_INT >= 26 ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : android.view.WindowManager.LayoutParams.TYPE_PHONE,
            flags, android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;
        try { wm.addView(morseOverlayView, params); Toast.makeText(this, "Đã BẬT. Bấm lại nút Thử Lớp Phủ để tắt.", Toast.LENGTH_LONG).show(); } catch (Exception e) {}
    }

    private void showPremiumDialog() { 
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,40,40,40);
        TextView tvTitle = createSectionTitle("👑 PREMIUM ARCHITECT");
        TextView tvDesc = new TextView(this); tvDesc.setText("ĐẶC QUYỀN VƯỢT RÀO OS:\n- Tắt Trợ Năng, lớp phủ vẫn chạy 100% mượt mà ở Màn hình khoá.\n- Tích hợp Trình Ghi Âm tàng hình độc lập không bị OS chặn.\n- Bypass Tapjacking hoàn hảo.\n\nLỆNH ADB KÍCH HOẠT (Chỉ 1 lần duy nhất):"); tvDesc.setTextColor(Color.WHITE); tvDesc.setPadding(0,20,0,20);
        
        String cmd1 = "adb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS";
        Button btnC1 = new Button(this); btnC1.setText("📋 COPY LỆNH 1 (WRITE_SECURE_SETTINGS)"); btnC1.setBackground(getRounded("#2E7D32", 20f)); btnC1.setTextColor(Color.WHITE);
        btnC1.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("ADB 1", cmd1); clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã copy Lệnh 1!", Toast.LENGTH_SHORT).show();
        });
        
        String cmd2 = "adb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow";
        Button btnC2 = new Button(this); btnC2.setText("📋 COPY LỆNH 2 (SYSTEM_ALERT_WINDOW)"); btnC2.setBackground(getRounded("#2E7D32", 20f)); btnC2.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,20,0,20); btnC2.setLayoutParams(lp);
        btnC2.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("ADB 2", cmd2); clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Đã copy Lệnh 2!", Toast.LENGTH_SHORT).show();
        });
        
        Button btnClose = new Button(this); btnClose.setText("ĐÓNG"); btnClose.setBackground(getRounded("#333333", 20f)); btnClose.setTextColor(Color.WHITE); btnClose.setOnClickListener(v -> d.dismiss());
        root.addView(tvTitle); root.addView(tvDesc); root.addView(btnC1); root.addView(btnC2); root.addView(btnClose);
        d.setContentView(root); d.show();
    }

    private LinearLayout createDrawer(String title, View content) { LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL); container.setBackground(getRounded("#222222", 20f)); LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,20); container.setLayoutParams(clp); TextView header = new TextView(this); header.setText(title); header.setTextColor(Color.parseColor("#00E5FF")); header.setPadding(30,30,30,30); header.setTextSize(16); content.setVisibility(View.GONE); header.setOnClickListener(v -> { boolean isClosed = content.getVisibility() == View.GONE; content.setVisibility(isClosed ? View.VISIBLE : View.GONE); header.setBackground(getRounded(isClosed ? "#333333" : "#222222", 20f)); }); container.addView(header); container.addView(content); return container; }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,20); TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sp = createSpinner(); sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); sp.setSelection(prefs.getInt(key, def)); sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt(key,pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f)); l.addView(tv); l.addView(sp); return l; }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    
    // CẬP NHẬT: TÁCH LUỒNG LỆNH BROADCAST CHO BREATH VÀ ANIMA
    private LinearLayout createSlider(String t, String k, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ if (designTabState == 2) { if(k.startsWith("breath")) sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH").setPackage(getPackageName())); else sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName())); } } }); btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); }); row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; }
    
    // CẬP NHẬT: TÁCH LUỒNG LỆNH BROADCAST CHO BREATH VÀ ANIMA
    private LinearLayout createSignedSlider(String t, String k, int min, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); int curVal = prefs.getInt(k, def); tv.setText(t + ": " + curVal); l.addView(tv); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); SeekBar sb = new SeekBar(this); sb.setMax(max - min); sb.setProgress(curVal - min); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ int v = p + min; tv.setText(t + ": " + v); prefs.edit().putInt(k, v).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ if (designTabState == 2) { if(k.startsWith("breath")) sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH").setPackage(getPackageName())); else sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName())); } } }); btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<(max-min)) sb.setProgress(p+1); }); row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; }
}
