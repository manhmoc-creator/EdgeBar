package com.manhmoc.edgebar;
import android.Manifest; import android.animation.ObjectAnimator; import android.animation.ValueAnimator; import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.PackageManager; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.graphics.RectF; import android.graphics.drawable.GradientDrawable; import android.os.Build; import android.os.Bundle; import android.os.Handler; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.widget.*; import org.json.JSONObject; import java.util.ArrayList; import java.util.Iterator;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[35]; private String[] ACT_LABS = new String[35];
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

    private LinearLayout pageDesign, pageConditions, pageIntents, pageTiles, pageMacros, listRules, designSliderContainer, navMain, fabEcoContainer; 
    private Button btnLock, btnHome, btnNavCond, btnNavAdv, btnEditLock, btnEditHome, btnEditAnim, fabRule, fabI, fabQ, fabM;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; private int currentEcoTab = 0;
    private final String CURRENT_VERSION = "V19.12.2.12"; private RelativeLayout rootLayout;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    private void refreshPreview() { boolean p = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); prefs.edit().putBoolean("preview_lock", p).apply(); }
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).apply(); }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD", "VOICE_RECORD"}; 
        String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn hình chính"), T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume", "Menu Âm Lượng"), T("Screenshot", "Chụp ảnh màn"), T("Camera", "Camera"), T("Notifications", "Mở Thông Báo"), T("Toggle Acc", "Bật/Tắt Trợ Năng"), T("Toggle Overlay", "Bật/Tắt Lớp Phủ"), T("YTDL Download", "Tải bằng YTDL"), T("Stealth Record", "Ghi Âm Ẩn")};
        for(int i=0; i<15; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
        for(int i=1; i<=15; i++) { ACT_KEYS[14+i]="INTENT_"+i; ACT_LABS[14+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
        for(int i=1; i<=5; i++) { ACT_KEYS[29+i]="MACRO_"+i; ACT_LABS[29+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
    }

    @Override public void onBackPressed() { 
        if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); Button btnD = rootLayout.findViewWithTag("btnDesign"); if(btnD!=null){btnD.setText("⚙️"); btnD.setTextColor(Color.WHITE);}
        } else super.onBackPressed(); 
    }
    
    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(currentMainTab == 1 ? View.VISIBLE : View.GONE); if(currentMainTab == 2) switchEcoTab(currentEcoTab); fabRule.setVisibility(currentMainTab == 1 ? View.VISIBLE : View.GONE); fabEcoContainer.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); refreshPreview(); }
    private void openDesignSpace() { refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageIntents.setVisibility(View.GONE); pageTiles.setVisibility(View.GONE); pageMacros.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); fabRule.setVisibility(View.GONE); fabEcoContainer.setVisibility(View.GONE); }
    private Button createCircleBtn(String icon, String bg, String txtColor) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.parseColor(txtColor)); b.setTextSize(15); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(bg, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(120, 120); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();

        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,60,40,40); 
        
        LinearLayout headerRow = new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setPadding(0, 0, 0, 50);
        TextView title = new TextView(this); title.setText("Edge Bar\n" + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnLang = new Button(this); btnLang.setText(isVi ? "🇻🇳 Tiếng Việt" : "🇺🇸 US-English"); btnLang.setTextColor(Color.WHITE); btnLang.setBackground(getRounded("#2E7D32", 20f)); btnLang.setPadding(30, 20, 30, 20); btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        headerRow.addView(title); headerRow.addView(btnLang); main.addView(headerRow);
        
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1); }
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText(T("⚠️ GRANT OVERLAY PERMISSION", "⚠️ CẤP QUYỀN LỚP PHỦ")); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

        navMain = new LinearLayout(this); navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        btnNavCond = createNavBtn(T("CONDITIONS", "ĐIỀU KIỆN")); btnNavAdv = createNavBtn(T("ECOSYSTEM", "HỆ SINH THÁI")); 
        navMain.addView(btnNavCond); navMain.addView(btnNavAdv); main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        
        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL); pageIntents.setVisibility(View.GONE);
        pageTiles = new LinearLayout(this); pageTiles.setOrientation(LinearLayout.VERTICAL); pageTiles.setVisibility(View.GONE);
        pageMacros = new LinearLayout(this); pageMacros.setOrientation(LinearLayout.VERTICAL); pageMacros.setVisibility(View.GONE);

        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageIntents); main.addView(pageTiles); main.addView(pageMacros);
        scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        
        Button btnUpdate = createCircleBtn("U", "#333333", "#BBBBBB"); btnUpdate.setTextSize(20); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = new Button(this); btnPremium.setText("PREMIUM"); btnPremium.setTextColor(Color.BLACK); btnPremium.setBackground(getRounded("#00E5FF", 100f)); btnPremium.setOnClickListener(v -> showPremiumDialog()); LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1); pLp.setMargins(10,0,10,0); btnPremium.setLayoutParams(pLp); btnPremium.setPadding(40,0,40,0);
        View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        fabRule = new Button(this); fabRule.setText("+ NEW EB"); fabRule.setTextColor(Color.BLACK); fabRule.setBackground(getRounded("#00E5FF", 100f)); LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -1); fLp.setMargins(10,0,10,0); fabRule.setLayoutParams(fLp); fabRule.setPadding(40,0,40,0); 
        
        fabEcoContainer = new LinearLayout(this); fabEcoContainer.setOrientation(LinearLayout.HORIZONTAL); 
        fabI = createCircleBtn("I", "#333333", "#E53935"); 
        fabQ = createCircleBtn("QS", "#333333", "#43A047"); 
        fabM = createCircleBtn("M", "#333333", "#1E88E5");
        fabEcoContainer.addView(fabI); fabEcoContainer.addView(fabQ); fabEcoContainer.addView(fabM);

        Button btnDesign = createCircleBtn("⚙️", "#333333", "#FFFFFF"); btnDesign.setTag("btnDesign"); btnDesign.setOnClickListener(v -> { if(pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); btnDesign.setText("⚙️"); btnDesign.setTextColor(Color.WHITE); } else { openDesignSpace(); btnDesign.setText("<"); btnDesign.setTextColor(Color.parseColor("#BBBBBB")); } });

        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); bottomBar.addView(fabRule); bottomBar.addView(fabEcoContainer); bottomBar.addView(btnDesign); rootLayout.addView(bottomBar);

        btnNavCond.setOnClickListener(v->switchMainTab(1)); btnNavAdv.setOnClickListener(v->switchMainTab(2)); 
        fabRule.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1));
        
        fabI.setOnClickListener(v -> { switchEcoTab(0); handleEcoFabClick(0); }); 
        fabQ.setOnClickListener(v -> { switchEcoTab(1); handleEcoFabClick(1); }); 
        fabM.setOnClickListener(v -> { switchEcoTab(2); handleEcoFabClick(2); });

        switchMainTab(1); setContentView(rootLayout);
    }

    private void handleEcoFabClick(int type) {
        int emptySlot = findEmptySlot(type);
        if (emptySlot == -1) { Toast.makeText(this, "Đã đạt giới hạn tối đa!", Toast.LENGTH_SHORT).show(); return; }
        openEcoItemDialog(type, emptySlot);
    }

    private int findEmptySlot(int type) {
        int max = (type == 2) ? 5 : 15;
        String prefix = (type == 0) ? "intent_" : (type == 1 ? "tile_" : "macro_");
        for(int i=1; i<=max; i++) {
            String val = (type == 1) ? prefs.getString(prefix+i+"_act", "NONE") : prefs.getString(prefix+i+"_name", "");
            if (val.isEmpty() || val.equals("NONE")) return i;
        }
        return -1;
    }

    private void switchMainTab(int idx) { 
        currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE);
        pageDesign.setVisibility(View.GONE); pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE);
        if (idx == 2) { switchEcoTab(currentEcoTab); } else { pageIntents.setVisibility(View.GONE); pageTiles.setVisibility(View.GONE); pageMacros.setVisibility(View.GONE); }
        btnNavCond.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); btnNavCond.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY); 
        btnNavAdv.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); btnNavAdv.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY); 
        fabRule.setVisibility(idx == 1 ? View.VISIBLE : View.GONE); fabEcoContainer.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if(idx==1) renderRulesList();
    }

    private void switchEcoTab(int tab) {
        currentEcoTab = tab;
        pageIntents.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        pageTiles.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        pageMacros.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        fabI.setBackground(getRounded(tab == 0 ? "#44E53935" : "#333333", 100f));
        fabQ.setBackground(getRounded(tab == 1 ? "#4443A047" : "#333333", 100f));
        fabM.setBackground(getRounded(tab == 2 ? "#441E88E5" : "#333333", 100f));
        renderEcosystemList();
    }

    private void buildConditionsSpace() {
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 0, 0, 20); 
        btnLock = createTabBtn("LOCKSCREEN"); btnHome = createTabBtn("HOMESCREEN"); 
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(0,0,15,0); btnLock.setLayoutParams(p); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); btnHome.setLayoutParams(rp);
        tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageConditions.addView(tabContainer);
        listRules = new LinearLayout(this); listRules.setOrientation(LinearLayout.VERTICAL); pageConditions.addView(listRules);
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
                    final int finalC = c; final int finalG = g; card.setOnClickListener(v -> openRuleBuilderDialog(key, finalC, finalG)); card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle(T("Delete Rule?", "Xóa Quy tắc?")).setPositiveButton(T("DELETE", "XÓA"), (d,w)->{prefs.edit().putString(key, "NONE").apply(); renderRulesList();}).setNegativeButton(T("CANCEL", "HỦY"), null).show(); return true; }); currentRow.addView(card); count++;
                } } }
        if(count % 2 != 0 && currentRow != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); currentRow.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText(T("No rules yet.\nPress + NEW EB to create.", "Chưa có quy tắc nào.\nBấm + NEW EB để tạo.")); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listRules.addView(empty); }
    }

    private void renderEcosystemList() {
        pageIntents.removeAllViews(); pageTiles.removeAllViews(); pageMacros.removeAllViews(); reloadActionLabels(); 
        int countI = 0, countQ = 0, countM = 0; LinearLayout rowI = null, rowQ = null, rowM = null;
        
        for (int i = 1; i <= 15; i++) { 
            String name = prefs.getString("intent_"+i+"_name", ""); 
            if(!name.isEmpty()) { 
                String act = prefs.getString("intent_"+i+"_act", ""); String detail = "Act: " + (act.isEmpty()?"Trống":act);
                if(countI % 2 == 0) { rowI = new LinearLayout(this); rowI.setOrientation(LinearLayout.HORIZONTAL); rowI.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); pageIntents.addView(rowI); }
                rowI.addView(createEcoCard(0, i, "🔴 INTENT " + i, name, detail, prefs.getBoolean("intent_"+i+"_on", true), "#E53935")); countI++; 
            } 
        }
        for (int i = 1; i <= 15; i++) { 
            String act = prefs.getString("tile_"+i+"_act", "NONE"); 
            if(!act.equals("NONE")) { 
                String actName = "Lỗi"; for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(act)) actName = ACT_LABS[j]; 
                if(countQ % 2 == 0) { rowQ = new LinearLayout(this); rowQ.setOrientation(LinearLayout.HORIZONTAL); rowQ.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); pageTiles.addView(rowQ); }
                boolean isOn = getPackageManager().getComponentEnabledSetting(new ComponentName(this, "com.manhmoc.edgebar.Tile"+i)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                rowQ.addView(createEcoCard(1, i, "🟢 QS TILE " + i, actName, isOn ? "Đã bật System" : "Đã ẩn System", isOn, "#43A047")); countQ++; 
            } 
        }
        for (int i = 1; i <= 5; i++) { 
            String name = prefs.getString("macro_"+i+"_name", ""); 
            if(!name.isEmpty()) { 
                if(countM % 2 == 0) { rowM = new LinearLayout(this); rowM.setOrientation(LinearLayout.HORIZONTAL); rowM.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); pageMacros.addView(rowM); }
                rowM.addView(createEcoCard(2, i, "🔵 MACRO " + i, name, "Svc: " + prefs.getString("macro_"+i+"_svcs", "Trống"), prefs.getBoolean("macro_"+i+"_on", true), "#1E88E5")); countM++; 
            } 
        }
        
        if(countI % 2 != 0 && rowI != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); rowI.addView(dummy); }
        if(countQ % 2 != 0 && rowQ != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); rowQ.addView(dummy); }
        if(countM % 2 != 0 && rowM != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); rowM.addView(dummy); }
        
        if(countI == 0) { TextView empty = new TextView(this); empty.setText("Chưa có Intent nào."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); pageIntents.addView(empty); }
        if(countQ == 0) { TextView empty = new TextView(this); empty.setText("Chưa có QS Tile nào."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); pageTiles.addView(empty); }
        if(countM == 0) { TextView empty = new TextView(this); empty.setText("Chưa có Macro nào."); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); pageMacros.addView(empty); }
    }

    private View createEcoCard(int type, int slotIndex, String title, String desc, String detail, boolean isEnabled, String colorHex) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2, 1f); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);
        LinearLayout topRow = new LinearLayout(this); topRow.setOrientation(LinearLayout.HORIZONTAL); topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView tTitle = new TextView(this); tTitle.setText(title); tTitle.setTextColor(Color.parseColor("#BBBBBB")); tTitle.setTextSize(11); tTitle.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Switch sw = new Switch(this); sw.setChecked(isEnabled); 
        sw.setOnCheckedChangeListener((v, c) -> {
            if(type == 0) prefs.edit().putBoolean("intent_"+slotIndex+"_on", c).apply();
            else if(type == 2) prefs.edit().putBoolean("macro_"+slotIndex+"_on", c).apply();
            else if(type == 1) {
                ComponentName comp = new ComponentName(this, "com.manhmoc.edgebar.Tile" + slotIndex);
                getPackageManager().setComponentEnabledSetting(comp, c ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Toast.makeText(this, c ? "Đã MỞ KHÓA Tile!" : "Đã ẨN Tile", Toast.LENGTH_SHORT).show();
            }
        });
        topRow.addView(tTitle); topRow.addView(sw); card.addView(topRow);
        
        TextView tDesc = new TextView(this); tDesc.setText(desc); tDesc.setTextColor(Color.parseColor(colorHex)); tDesc.setTextSize(15); tDesc.setPadding(0,10,0,0); card.addView(tDesc);
        TextView tDet = new TextView(this); tDet.setText(detail); tDet.setTextColor(Color.GRAY); tDet.setTextSize(10); tDet.setPadding(0,5,0,0); tDet.setMaxLines(2); tDet.setEllipsize(TextUtils.TruncateAt.END); card.addView(tDet);
        
        card.setOnClickListener(v -> openEcoItemDialog(type, slotIndex));
        card.setOnLongClickListener(v -> { 
            new AlertDialog.Builder(this).setTitle("Xóa mục này?").setPositiveButton("XÓA", (d,w)->{
                if(type == 0) prefs.edit().putString("intent_"+slotIndex+"_name", "").apply();
                else if(type == 1) prefs.edit().putString("tile_"+slotIndex+"_act", "NONE").apply();
                else if(type == 2) prefs.edit().putString("macro_"+slotIndex+"_name", "").apply();
                renderEcosystemList();
            }).setNegativeButton("HỦY", null).show(); 
            return true; 
        });
        return card;
    }

    private void openEcoItemDialog(int type, int slotIndex) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 80, 30, 30);
        
        TextView header = new TextView(this); header.setTextSize(20); header.setPadding(0,0,0,30);
        if(type == 0) { header.setText("🔴 CẤU HÌNH INTENT " + slotIndex); header.setTextColor(Color.parseColor("#E53935")); }
        else if(type == 1) { header.setText("🟢 CẤU HÌNH QS TILE " + slotIndex); header.setTextColor(Color.parseColor("#43A047")); }
        else { header.setText("🔵 CẤU HÌNH MACRO " + slotIndex); header.setTextColor(Color.parseColor("#1E88E5")); }
        root.addView(header);

        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);

        if(type == 0) {
            content.addView(createInput("Tên gợi nhớ (VD: Mở FB)", "intent_"+slotIndex+"_name"));
            content.addView(createInput("Action", "intent_"+slotIndex+"_act"));
            content.addView(createInput("Package", "intent_"+slotIndex+"_pkg"));
            content.addView(createInput("Class Name", "intent_"+slotIndex+"_cls"));
            content.addView(createInput("Data URI (acc://...)", "intent_"+slotIndex+"_data"));
            content.addView(createInput("Categories", "intent_"+slotIndex+"_cat"));
            content.addView(createInput("Flags", "intent_"+slotIndex+"_flags"));
            CheckBox cb = new CheckBox(this); cb.setText("Gửi dạng Broadcast"); cb.setTextColor(Color.WHITE); cb.setChecked(prefs.getBoolean("intent_"+slotIndex+"_br", true)); 
            cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("intent_"+slotIndex+"_br", c).apply()); content.addView(cb);
        } else if(type == 1) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(this); tv.setText("Hành động thực thi: "); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
            Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); 
            String v = prefs.getString("tile_"+slotIndex+"_act","NONE"); for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(v)) s.setSelection(j); 
            s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("tile_"+slotIndex+"_act",ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); 
            s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); 
            row.addView(tv); row.addView(s); content.addView(row);
        } else if(type == 2) {
            content.addView(createInput("Tên gợi nhớ Macro", "macro_"+slotIndex+"_name")); 
            content.addView(createInput("Danh sách Services (com.a/.b, com.c/.d)", "macro_"+slotIndex+"_svcs"));
        }

        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,30,0,0);
        Button bCancel = new Button(this); bCancel.setText("HỦY BỎ"); bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText("LƯU LẠI"); bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp);
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> { d.dismiss(); reloadActionLabels(); renderEcosystemList(); });
        d.setContentView(root); d.show();
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
        
        LinearLayout ytdlSys = new LinearLayout(this); ytdlSys.setOrientation(LinearLayout.VERTICAL); ytdlSys.addView(createSectionTitle(T("YTDLnis INTEGRATION", "KIỂM TRA LIÊN KẾT YTDLnis")));
        EditText ytdlInput = createInput("Nhập tên bài hát / Link YouTube...", "ytdl_test_input");
        Button btnYtdl = new Button(this); btnYtdl.setText("🚀 CHUYỂN HƯỚNG TỚI YTDLnis"); btnYtdl.setBackground(getRounded("#FF9800", 20f)); btnYtdl.setTextColor(Color.BLACK); LinearLayout.LayoutParams yLp = new LinearLayout.LayoutParams(-1,-2); yLp.setMargins(0,10,0,0); btnYtdl.setLayoutParams(yLp);
        btnYtdl.setOnClickListener(v -> {
            String q = ytdlInput.getText().toString(); if(q.isEmpty()) { Toast.makeText(this, "Chưa nhập thông tin!", Toast.LENGTH_SHORT).show(); return; }
            try { Intent intent = new Intent(Intent.ACTION_SEND); intent.setType("text/plain"); intent.putExtra(Intent.EXTRA_TEXT, q); intent.setPackage("com.deniscerri.ytdl"); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION); startActivity(intent); } catch(Exception e) { Toast.makeText(this, "Lỗi: YTDLnis chưa cài đặt", Toast.LENGTH_SHORT).show(); }
        }); 
        ytdlSys.addView(ytdlInput); ytdlSys.addView(btnYtdl); pageDesign.addView(wrapCard(ytdlSys));

        pageDesign.addView(createSectionTitle(T("CORE DESIGN", "THIẾT KẾ CỐT LÕI")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL); btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); btnEditHome = new Button(this); btnEditHome.setText("HOME"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP); btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0); btnEditLock.setOnClickListener(v -> { designTabState=0; refreshPreview(); btnEditLock.setBackground(getRounded("#00E5FF", 20f)); btnEditLock.setTextColor(Color.BLACK); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); renderSliders(); }); btnEditHome.setOnClickListener(v -> { designTabState=1; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#00E5FF", 20f)); btnEditHome.setTextColor(Color.BLACK); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); renderSliders(); }); btnEditAnim.setOnClickListener(v -> { designTabState=2; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#00E5FF", 20f)); btnEditAnim.setTextColor(Color.BLACK); renderSliders(); }); toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditAnim); pageDesign.addView(toggleRow); pageDesign.addView(designSliderContainer); btnEditLock.performClick();
    }
    
    private class BreathView extends View {
        private Paint pDraw, pText; public float animAlpha = 0f; private int shape; 
        private float w, h, thick, dotSize; private String timeStr = "00:00"; private int color;
        public BreathView(Context c) { super(c); pDraw = new Paint(); pDraw.setAntiAlias(true); pDraw.setStyle(Paint.Style.STROKE); pDraw.setStrokeCap(Paint.Cap.ROUND); pText = new Paint(); pText.setAntiAlias(true); pText.setColor(Color.WHITE); pText.setTextSize(35f); pText.setShadowLayer(5, 0, 0, Color.BLACK); shape = prefs.getInt("breath_shape", 0); w = prefs.getInt("breath_w", 0); h = prefs.getInt("breath_h", 0); thick = prefs.getInt("breath_thick", 12); dotSize = prefs.getInt("breath_dot_size", 40); pDraw.setStrokeWidth(thick); String cTheme = prefs.getString("anim_color", "WHITE"); color = Color.WHITE; for(int i=0; i<COLOR_KEYS.length; i++) if(COLOR_KEYS[i].equals(cTheme)) color = Color.parseColor(COLOR_VALS[i]); if(shape == 1) { pDraw.setStyle(Paint.Style.FILL); pDraw.setColor(color); } else { pDraw.setColor(color); } }
        public void setAnimAlpha(float a) { this.animAlpha = a; invalidate(); } public void setTime(String t) { this.timeStr = t; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); pDraw.setAlpha((int)(animAlpha * prefs.getInt("breath_alpha", 255))); float cx = getWidth() / 2f; float cy = getHeight() / 2f; 
        if (shape == 1) { cx = dotSize + 10; cy = dotSize + 10; canvas.drawCircle(cx, cy, dotSize, pDraw); canvas.drawText(timeStr, cx + dotSize + 25, cy + 12, pText); 
        } else { float drawW = (w > 0) ? w : getWidth(); float drawH = (h > 0) ? h : getHeight(); float off = thick / 2f; float left = (getWidth() - drawW) / 2f + off; float top = (getHeight() - drawH) / 2f + off; canvas.drawRoundRect(new RectF(left, top, left + drawW - 2*off, top + drawH - 2*off), 40, 40, pDraw); canvas.drawText(timeStr, cx - 40, top + drawH + 40, pText); } }
    }
    
    private void testBreathAnimation() {
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Cấp quyền Lớp phủ trước!", Toast.LENGTH_SHORT).show(); return; }
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE); BreathView bv = new BreathView(this);
        int shape = prefs.getInt("breath_shape", 0); int w = prefs.getInt("breath_w", 0); int h = prefs.getInt("breath_h", 0); int dotSize = prefs.getInt("breath_dot_size", 40); int bX = prefs.getInt("breath_dot_x", 0); int bY = prefs.getInt("breath_dot_y", 0);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(shape == 1 ? (int)(dotSize*2 + 300) : (w > 0 ? w : -1), shape == 1 ? (int)(dotSize*2 + 50) : (h > 0 ? h : 100), Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        if (shape == 1) { params.gravity = Gravity.TOP | Gravity.LEFT; params.x = bX; params.y = bY; } else { params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; }
        ObjectAnimator anim = ObjectAnimator.ofFloat(bv, "animAlpha", 0.1f, 1f, 0.1f); anim.setDuration(prefs.getInt("breath_delay", 1500)); anim.setRepeatCount(ValueAnimator.INFINITE); anim.start();
        wm.addView(bv, params); Toast.makeText(this, "Đang chạy thử hơi thở 4s...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> { anim.cancel(); try { wm.removeView(bv); } catch(Exception e){} }, 4000);
    }

    private void renderSliders() { designSliderContainer.removeAllViews(); if(designTabState == 2) { 
        designSliderContainer.addView(createSectionTitle("✨ HIỆU ỨNG ÁNH SÁNG (VIỀN)"));
        Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG VIỀN"); btnTest.setBackground(getRounded("#FFC107", 20f)); btnTest.setTextColor(Color.BLACK); btnTest.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2); testLp.setMargins(0,0,0,20); btnTest.setLayoutParams(testLp); btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); i.setPackage(getPackageName()); sendBroadcast(i); Toast.makeText(this, "Playing Animation...", Toast.LENGTH_SHORT).show(); }); designSliderContainer.addView(btnTest);
        LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText("Chủ đề:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); designSliderContainer.addView(lC); 
        LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText("Kiểu chạy:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Nhấp Nháy", "1 Tia sáng nối đuôi", "2 Tia sáng đối xứng", "3 Tia sáng đều nhau"})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); designSliderContainer.addView(lS); 
        designSliderContainer.addView(createSlider("Chiều ngang Hiệu ứng (0=Full)", "anim_w", 2000, 0)); designSliderContainer.addView(createSlider("Chiều dọc Hiệu ứng (0=Full)", "anim_h", 3500, 0)); designSliderContainer.addView(createSlider("Độ đậm mờ hiệu ứng (Alpha)", "anim_alpha", 255, 255)); designSliderContainer.addView(createSlider("Độ dày viền", "anim_thick", 50, 12)); designSliderContainer.addView(createSlider("Thời gian Animation (ms)", "anim_dur", 5000, 1500)); designSliderContainer.addView(createSlider("Thời gian Vuốt+Giữ (All)", "hold_dur", 2000, 600)); designSliderContainer.addView(createSlider("Độ rung (ms) (All)", "vib_dur", 100, 30)); 
        
        LinearLayout recSys = new LinearLayout(this); recSys.setOrientation(LinearLayout.VERTICAL); recSys.addView(createSectionTitle("🎤 THIẾT KẾ HƠI THỞ GHI ÂM"));
        Button btnTestBreath = new Button(this); btnTestBreath.setText("▶ THỬ ANIMATION HƠI THỞ"); btnTestBreath.setBackground(getRounded("#E91E63", 20f)); btnTestBreath.setTextColor(Color.WHITE); btnTestBreath.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp2 = new LinearLayout.LayoutParams(-1,-2); testLp2.setMargins(0,0,0,20); btnTestBreath.setLayoutParams(testLp2); btnTestBreath.setOnClickListener(v -> testBreathAnimation()); recSys.addView(btnTestBreath);
        recSys.addView(createComboDropdown("Chất lượng Ghi âm (kbps)", "rec_kbps", new String[]{"64 kbps", "128 kbps", "256 kbps", "320 kbps (High)"}, 1));
        recSys.addView(createComboDropdown("Hình dáng Hơi Thở", "breath_shape", new String[]{"Dải Viền (Edge)", "Chấm Tròn (Dot)"}, 0));
        recSys.addView(createSlider("Khoảng nghỉ nhịp thở (ms)", "breath_delay", 5000, 1500));
        recSys.addView(createSlider("Độ mờ tối đa (Alpha)", "breath_alpha", 255, 255));
        TextView tEdge = new TextView(this); tEdge.setText("Cấu hình Dải Viền:"); tEdge.setTextColor(Color.GRAY); recSys.addView(tEdge);
        recSys.addView(createSlider("Chiều ngang (0=Full)", "breath_w", 2000, 0)); recSys.addView(createSlider("Chiều dọc (0=Full)", "breath_h", 3500, 0)); recSys.addView(createSlider("Độ dày viền", "breath_thick", 50, 12));
        TextView tDot = new TextView(this); tDot.setText("Cấu hình Chấm Tròn:"); tDot.setTextColor(Color.GRAY); recSys.addView(tDot);
        recSys.addView(createSlider("Độ to chấm tròn", "breath_dot_size", 200, 40)); recSys.addView(createSlider("Toạ độ X", "breath_dot_x", 1500, 0)); recSys.addView(createSlider("Toạ độ Y", "breath_dot_y", 3000, 0));
        LinearLayout recBtns = new LinearLayout(this); recBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button btnTestRec = new Button(this); btnTestRec.setText("▶ BẮT ĐẦU GHI ÂM"); btnTestRec.setBackground(getRounded("#E91E63", 20f)); btnTestRec.setTextColor(Color.WHITE); btnTestRec.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button btnStopRec = new Button(this); btnStopRec.setText("⏹ DỪNG LẠI"); btnStopRec.setBackground(getRounded("#333333", 20f)); btnStopRec.setTextColor(Color.WHITE); LinearLayout.LayoutParams tbLp2 = new LinearLayout.LayoutParams(0,-2,1f); tbLp2.setMargins(15,0,0,0); btnStopRec.setLayoutParams(tbLp2);
        btnTestRec.setOnClickListener(v -> { try { Intent i = new Intent(this, RecorderService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch(Exception e) { Toast.makeText(this, "Lỗi gọi Service", Toast.LENGTH_SHORT).show(); } });
        btnStopRec.setOnClickListener(v -> { try { Intent i = new Intent(this, RecorderService.class); stopService(i); } catch(Exception e) {} });
        recBtns.addView(btnTestRec); recBtns.addView(btnStopRec); recSys.addView(recBtns);
        designSliderContainer.addView(wrapCard(recSys));
        
        } else { String prefix = designTabState == 1 ? "home_" : "lock_"; 
        designSliderContainer.addView(createSectionTitle("5 EDGE BARS"));
        for(int i=0; i< 5; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+BARS[idx]+"_en", c).apply()); drawerContent.addView(cb); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+BARS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createSlider("Độ trong suốt", prefix+BARS[i]+"_alpha", 255, 50)); drawerContent.addView(createSlider("Chiều ngang", prefix+BARS[i]+"_w", 3000, 300)); drawerContent.addView(createSlider("Chiều dọc", prefix+BARS[i]+"_h", 3000, 60)); drawerContent.addView(createSlider("Toạ độ X", prefix+BARS[i]+"_x", 1000, 0)); drawerContent.addView(createSlider("Toạ độ Y", prefix+BARS[i]+"_y", 1000, 0)); designSliderContainer.addView(createDrawer(BAR_NAMES[i], drawerContent)); } 
        designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS"));
        for(int i=0; i<4; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); drawerContent.addView(createComboDropdown("Hiển thị", prefix+"corner_"+CORNERS[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0)); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+"corner_"+CORNERS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix+"corner_"+CORNERS[i]+"_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix+"corner_"+CORNERS[i]+"_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix+"corner_"+CORNERS[i]+"_h", 2500, 100)); drawerContent.addView(createSlider("Di chuyển Ngang (X)", prefix+"corner_"+CORNERS[i]+"_x", 2500, 0)); drawerContent.addView(createSlider("Di chuyển Dọc (Y)", prefix+"corner_"+CORNERS[i]+"_y", 2500, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix+"corner_"+CORNERS[i]+"_moon_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix+"corner_"+CORNERS[i]+"_moon_h", 2500, 100)); drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_x", 2500, 1250)); drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_y", 2500, 1250)); drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_rad", 1000, 80)); drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_moon_rad", 1000, 80)); designSliderContainer.addView(createDrawer(CORNER_NAMES[i], drawerContent)); }
        LinearLayout globalDrawer = new LinearLayout(this); globalDrawer.setOrientation(LinearLayout.VERTICAL); globalDrawer.setPadding(30,10,30,30); globalDrawer.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500)); globalDrawer.addView(createSlider("Độ mờ vùng TRĂNG NON (Đậm/Nhạt)", prefix+"corner_moon_alpha", 255, 100)); globalDrawer.addView(createSlider("Độ mờ VIỀN GÓC (Đậm/Nhạt)", prefix+"corner_stroke_alpha", 255, 200)); globalDrawer.addView(createSlider("Độ đậm viền (Dày/Mỏng)", prefix+"corner_thick", 50, 8)); designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawer));
        } }

    private void showPremiumDialog() { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("👑 PREMIUM ARCHITECT").setMessage("ĐẶC QUYỀN VƯỢT RÀO OS:\n- Tắt Trợ Năng, lớp phủ vẫn chạy 100% mượt mà ở Màn hình khoá.\n- Tích hợp Trình Ghi Âm tàng hình độc lập không bị OS chặn.\n- Bypass Tapjacking hoàn hảo.\n\nLỆNH ADB KÍCH HOẠT (Chỉ 1 lần duy nhất):\n1. Cho phép tự động Bật/Tắt Trợ Năng & Lớp phủ:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\n\n2. Cấp quyền vẽ xuyên không gian vô hạn:\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow").setPositiveButton("OK", null).show(); }
    private LinearLayout createDrawer(String title, View content) { LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL); container.setBackground(getRounded("#222222", 20f)); LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,20); container.setLayoutParams(clp); TextView header = new TextView(this); header.setText(title); header.setTextColor(Color.parseColor("#00E5FF")); header.setPadding(30,30,30,30); header.setTextSize(16); content.setVisibility(View.GONE); header.setOnClickListener(v -> { boolean isClosed = content.getVisibility() == View.GONE; content.setVisibility(isClosed ? View.VISIBLE : View.GONE); header.setBackground(getRounded(isClosed ? "#333333" : "#222222", 20f)); }); container.addView(header); container.addView(content); return container; }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,20); TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sp = createSpinner(); sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); sp.setSelection(prefs.getInt(key, def)); sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt(key,pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f)); l.addView(tv); l.addView(sp); return l; }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    private LinearLayout createSlider(String t, String k, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); }); row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; }
}
