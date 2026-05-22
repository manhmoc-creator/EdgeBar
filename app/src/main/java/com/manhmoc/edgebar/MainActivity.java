package com.manhmoc.edgebar;
import android.Manifest; import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Build; import android.os.Bundle; import android.os.Environment; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.View; import android.view.ViewGroup; import android.widget.*; import org.json.JSONObject; import java.util.ArrayList; import java.util.Iterator;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[35]; private String[] ACT_LABS = new String[35];
    private String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private String[] BAR_NAMES; 
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; private String[] CORNER_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; private String[] COLOR_NAMES;
    private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl"}; 
    private String[] ALL_COMP_NAMES = {"Thanh Đáy Phải", "Thanh Đáy Trái", "Thanh Cạnh Phải", "Thanh Cạnh Trái", "Thanh Đỉnh Giữa", "Góc Viền Đáy Phải", "Góc Viền Đáy Trái", "Góc Viền Đỉnh Phải", "Góc Viền Đỉnh Trái"};
    private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"}; 
    private String[] C_GESTURE_NAMES = {"1 Chạm", "2 Chạm", "Nhấn Giữ", "Vuốt Lên", "Vuốt Xuống", "Vuốt Trái", "Vuốt Phải", "Vuốt Lên + Giữ", "Vuốt Xuống + Giữ", "Vuốt Trái + Giữ", "Vuốt Phải + Giữ", "Vuốt Chéo", "Vuốt Chéo + Giữ"};

    private LinearLayout pageDesign, pageConditions, pageEcosystem, listRules, listEco, designSliderContainer, navMain, fabEcoContainer; 
    private Button btnLock, btnHome, btnNavCond, btnNavAdv, btnEditLock, btnEditHome, btnEditAnim, fabRule;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; 
    private final String CURRENT_VERSION = "V19.12.2.5"; private RelativeLayout rootLayout;

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
    
    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(currentMainTab == 1 ? View.VISIBLE : View.GONE); pageEcosystem.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); fabRule.setVisibility(currentMainTab == 1 ? View.VISIBLE : View.GONE); fabEcoContainer.setVisibility(currentMainTab == 2 ? View.VISIBLE : View.GONE); refreshPreview(); }
    private void openDesignSpace() { refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); fabRule.setVisibility(View.GONE); fabEcoContainer.setVisibility(View.GONE); }
    private Button createCircleBtn(String icon, String color, String txtColor) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.parseColor(txtColor)); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(color, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();
        BAR_NAMES = new String[]{"Đáy phải", "Đáy trái", "Cạnh Phải", "Cạnh Trái", "Đỉnh giữa"}; CORNER_NAMES = new String[]{"Góc đáy phải", "Góc đáy trái", "Góc đỉnh phải", "Góc đỉnh trái"}; COLOR_NAMES = new String[]{"Trắng Tinh Khiết", "Neon (Pink-Cyan)", "Cyberpunk (Purple-Gold)", "Lava (Red-Orange)", "Ocean (Blue-Cyan)", "Matrix (Green)", "Sunset (Purple-Orange)", "Google (4 Colors)", "Aurora (Cyan-Purple)", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};

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
        pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); buildEcosystemSpace();

        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageEcosystem); 
        scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);

        // V19.12.2.5: Đổi nút Update thành chữ U, đồng bộ màu sắc với Nút Gear
        Button btnUpdate = createCircleBtn("U", "#333333", "#FFFFFF"); btnUpdate.setTextSize(20); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = new Button(this); btnPremium.setText("PREMIUM"); btnPremium.setTextColor(Color.BLACK); btnPremium.setBackground(getRounded("#00E5FF", 100f)); btnPremium.setOnClickListener(v -> showPremiumDialog());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1); pLp.setMargins(10,0,10,0); btnPremium.setLayoutParams(pLp); btnPremium.setPadding(40,0,40,0);
        View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        // Nút Rule
        fabRule = new Button(this); fabRule.setText("+ NEW EB"); fabRule.setTextColor(Color.BLACK); fabRule.setBackground(getRounded("#00E5FF", 100f));
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -1); fLp.setMargins(10,0,10,0); fabRule.setLayoutParams(fLp); fabRule.setPadding(40,0,40,0); 
        
        // V19.12.2.5: Cụm Nút 3 Màu (Đỏ - Lục - Lam)
        fabEcoContainer = new LinearLayout(this); fabEcoContainer.setOrientation(LinearLayout.HORIZONTAL); fabEcoContainer.setLayoutParams(fLp); fabEcoContainer.setGravity(Gravity.CENTER);
        Button fabI = new Button(this); fabI.setText("IN"); fabI.setTextColor(Color.WHITE); fabI.setBackground(getRounded("#E53935", 100f)); fabI.setLayoutParams(new LinearLayout.LayoutParams(110, -1));
        Button fabQ = new Button(this); fabQ.setText("QS"); fabQ.setTextColor(Color.WHITE); fabQ.setBackground(getRounded("#43A047", 100f)); LinearLayout.LayoutParams flLp = new LinearLayout.LayoutParams(110, -1); flLp.setMargins(10,0,10,0); fabQ.setLayoutParams(flLp);
        Button fabM = new Button(this); fabM.setText("MA"); fabM.setTextColor(Color.WHITE); fabM.setBackground(getRounded("#1E88E5", 100f)); fabM.setLayoutParams(new LinearLayout.LayoutParams(110, -1));
        fabEcoContainer.addView(fabI); fabEcoContainer.addView(fabQ); fabEcoContainer.addView(fabM);

        Button btnDesign = createCircleBtn("⚙️", "#333333", "#FFFFFF"); btnDesign.setTag("btnDesign");
        btnDesign.setOnClickListener(v -> { if(pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); btnDesign.setText("⚙️"); btnDesign.setTextColor(Color.WHITE); } else { openDesignSpace(); btnDesign.setText("<"); btnDesign.setTextColor(Color.parseColor("#BBBBBB")); } });

        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); bottomBar.addView(fabRule); bottomBar.addView(fabEcoContainer); bottomBar.addView(btnDesign);
        rootLayout.addView(bottomBar);

        btnNavCond.setOnClickListener(v->switchMainTab(1)); btnNavAdv.setOnClickListener(v->switchMainTab(2)); 
        fabRule.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1));
        fabI.setOnClickListener(v -> openNewbieDialog(0)); fabQ.setOnClickListener(v -> openNewbieDialog(1)); fabM.setOnClickListener(v -> openNewbieDialog(2));

        switchMainTab(1); setContentView(rootLayout);
    }

    private void switchMainTab(int idx) { 
        currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE);
        pageDesign.setVisibility(View.GONE); pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE); pageEcosystem.setVisibility(idx==2?View.VISIBLE:View.GONE);
        btnNavCond.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); btnNavCond.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY); 
        btnNavAdv.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); btnNavAdv.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY); 
        fabRule.setVisibility(idx == 1 ? View.VISIBLE : View.GONE); fabEcoContainer.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        if(idx==1) renderRulesList(); else renderEcosystemList();
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
    
    private void buildEcosystemSpace() { 
        listEco = new LinearLayout(this); listEco.setOrientation(LinearLayout.VERTICAL); pageEcosystem.addView(listEco); 
        // V19.12.2.5: Thêm nút test YTDLnis ở đáy Ecosystem
        Button btnYtdl = new Button(this); btnYtdl.setText("🔍 THỬ TÌM KIẾM YTDLnis"); btnYtdl.setBackground(getRounded("#FF9800", 20f)); btnYtdl.setTextColor(Color.BLACK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,30,0,0); btnYtdl.setLayoutParams(lp);
        btnYtdl.setOnClickListener(v -> {
            try { Intent i = new Intent(Intent.ACTION_SEARCH); i.setPackage("com.deniscerri.ytdl"); i.putExtra("query", "nhạc lofi"); startActivity(i); Toast.makeText(this, "Đã gọi YTDLnis", Toast.LENGTH_SHORT).show(); } catch(Exception e) { Toast.makeText(this, "Chưa cài YTDLnis hoặc sai Intent", Toast.LENGTH_SHORT).show(); }
        });
        pageEcosystem.addView(btnYtdl);
    }

    private void renderRulesList() {
        listRules.removeAllViews(); String prefix = currentGesTab == 0 ? "lock_" : "home_";
        LinearLayout currentRow = null; int count = 0;
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
        listEco.removeAllViews(); reloadActionLabels(); int count = 0;
        // V19.12.2.5: Bổ sung logic hiển thị thông tin Detail
        for (int i = 1; i <= 15; i++) { String name = prefs.getString("intent_"+i+"_name", ""); if(!name.isEmpty()) { 
            String detail = "Dạng: " + (prefs.getBoolean("intent_"+i+"_br", true) ? "Broadcast" : "Action") + " | Pkg: " + prefs.getString("intent_"+i+"_pkg", "Trống");
            listEco.addView(createEcoCard("🔴 INTENT " + i, name, detail, "INTENT", i)); count++; 
        } }
        for (int i = 1; i <= 15; i++) { String act = prefs.getString("tile_"+i+"_act", "NONE"); if(!act.equals("NONE")) { 
            String actName = "Lỗi"; for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(act)) actName = ACT_LABS[j]; 
            String detail = "Thực thi: " + actName;
            listEco.addView(createEcoCard("🟢 QS TILE " + i, actName, detail, "TILE", i)); count++; 
        } }
        for (int i = 1; i <= 5; i++) { String name = prefs.getString("macro_"+i+"_name", ""); if(!name.isEmpty()) { 
            String detail = "Services: " + prefs.getString("macro_"+i+"_svcs", "Trống");
            listEco.addView(createEcoCard("🔵 MACRO " + i, name, detail, "MACRO", i)); count++; 
        } }
        if(count == 0) { TextView empty = new TextView(this); empty.setText(T("Ecosystem is empty.\nChoose IN / QS / MA to create.", "Hệ sinh thái trống.\nChọn Đỏ / Lục / Lam để tạo mới.")); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listEco.addView(empty); }
    }

    private View createEcoCard(String title, String desc, String detail, String type, int id) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,30); card.setLayoutParams(lp);
        TextView tTitle = new TextView(this); tTitle.setText(title); tTitle.setTextColor(Color.parseColor("#BBBBBB")); tTitle.setTextSize(13); tTitle.setPadding(0,0,0,10); card.addView(tTitle);
        TextView tDesc = new TextView(this); tDesc.setText(desc); tDesc.setTextColor(Color.parseColor("#00E5FF")); tDesc.setTextSize(16); card.addView(tDesc);
        // Detail TextView
        TextView tDet = new TextView(this); tDet.setText(detail); tDet.setTextColor(Color.GRAY); tDet.setTextSize(12); tDet.setPadding(0,10,0,0); card.addView(tDet);
        
        card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle(T("Delete?", "Xóa Mục này?")).setPositiveButton(T("DELETE", "XÓA"), (d,w)->{ if(type.equals("INTENT")) prefs.edit().putString("intent_"+id+"_name", "").apply(); else if(type.equals("MACRO")) prefs.edit().putString("macro_"+id+"_name", "").apply(); else prefs.edit().putString("tile_"+id+"_act", "NONE").apply(); renderEcosystemList(); reloadActionLabels(); }).setNegativeButton(T("CANCEL", "HỦY"), null).show(); return true; });
        return card;
    }

    private void openNewbieDialog(int tabIndex) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 80, 30, 30);
        
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button bInt = createTabBtn("🔴 INTENTS"); Button bQS = createTabBtn("🟢 QS TILES"); Button bMac = createTabBtn("🔵 MACROS");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,-2,1f); tabLp.setMargins(10,0,10,0); bInt.setLayoutParams(tabLp); bQS.setLayoutParams(tabLp); bMac.setLayoutParams(tabLp);
        tabs.addView(bInt); tabs.addView(bQS); tabs.addView(bMac); root.addView(tabs);

        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,40,0,0); scroll.addView(content); root.addView(scroll);

        LinearLayout vInt = new LinearLayout(this); vInt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout vQS = new LinearLayout(this); vQS.setOrientation(LinearLayout.VERTICAL); vQS.setVisibility(View.GONE);
        LinearLayout vMac = new LinearLayout(this); vMac.setOrientation(LinearLayout.VERTICAL); vMac.setVisibility(View.GONE);

        for (int i = 1; i <= 15; i++) { 
            LinearLayout sInt = new LinearLayout(this); sInt.setOrientation(LinearLayout.VERTICAL); sInt.setPadding(30,10,30,30);
            sInt.addView(createInput("Tên gợi nhớ (VD: Mở FB)", "intent_"+i+"_name")); sInt.addView(createInput("Action", "intent_"+i+"_act")); sInt.addView(createInput("Package", "intent_"+i+"_pkg")); sInt.addView(createInput("Class Name", "intent_"+i+"_cls")); sInt.addView(createInput("Data URI (acc://...)", "intent_"+i+"_data")); sInt.addView(createInput("Categories", "intent_"+i+"_cat")); sInt.addView(createInput("Flags", "intent_"+i+"_flags")); CheckBox cb = new CheckBox(this); cb.setText("Gửi dạng Broadcast"); cb.setTextColor(Color.WHITE); cb.setChecked(prefs.getBoolean("intent_"+i+"_br", true)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("intent_"+idx+"_br", c).apply()); sInt.addView(cb); 
            vInt.addView(createDrawer("🔴 Intent Slot " + i, sInt)); 
        }
        for (int i = 1; i <= 15; i++) { 
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(30,10,30,30);
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(this); tv.setText("Hành động: "); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
            Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); String v = prefs.getString("tile_"+i+"_act","NONE"); for(int j=0;j<ACT_KEYS.length;j++) if(ACT_KEYS[j].equals(v)) s.setSelection(j); final int idx=i; s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("tile_"+idx+"_act",ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); 
            row.addView(tv); row.addView(s); box.addView(row);
            CheckBox cbShow = new CheckBox(this); cbShow.setText("Bật Tile này lên Hệ thống Quick Settings"); cbShow.setTextColor(Color.parseColor("#4CAF50")); ComponentName comp = new ComponentName(this, "com.manhmoc.edgebar.Tile" + i); cbShow.setChecked(getPackageManager().getComponentEnabledSetting(comp) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED); cbShow.setOnCheckedChangeListener((vBtn, isChecked) -> { getPackageManager().setComponentEnabledSetting(comp, isChecked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); Toast.makeText(this, isChecked ? "Đã MỞ KHÓA Tile!" : "Đã ẨN Tile", Toast.LENGTH_SHORT).show(); }); box.addView(cbShow);
            vQS.addView(createDrawer("🟢 QS Tile " + i, box));
        }
        for(int i=1; i<=5; i++) { 
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(30,10,30,30);
            box.addView(createInput("Tên gợi nhớ Macro", "macro_"+i+"_name")); box.addView(createInput("Danh sách Services (com.a/.b, com.c/.d)", "macro_"+i+"_svcs"));
            vMac.addView(createDrawer("🔵 MACRO " + i, box)); 
        }

        content.addView(vInt); content.addView(vQS); content.addView(vMac);

        View.OnClickListener tabClick = v -> { 
            bInt.setBackground(getRounded(v==bInt?"#E53935":"#222222", 15f)); bInt.setTextColor(Color.WHITE); 
            bQS.setBackground(getRounded(v==bQS?"#43A047":"#222222", 15f)); bQS.setTextColor(Color.WHITE); 
            bMac.setBackground(getRounded(v==bMac?"#1E88E5":"#222222", 15f)); bMac.setTextColor(Color.WHITE); 
            vInt.setVisibility(v==bInt?View.VISIBLE:View.GONE); vQS.setVisibility(v==bQS?View.VISIBLE:View.GONE); vMac.setVisibility(v==bMac?View.VISIBLE:View.GONE); 
        }; bInt.setOnClickListener(tabClick); bQS.setOnClickListener(tabClick); bMac.setOnClickListener(tabClick); 
        if(tabIndex == 0) bInt.performClick(); else if(tabIndex == 1) bQS.performClick(); else bMac.performClick();

        Button btnClose = new Button(this); btnClose.setText("ĐÓNG & LƯU LẠI"); btnClose.setBackground(getRounded("#4CAF50", 20f)); btnClose.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1,-2); btnLp.setMargins(0,20,0,0); btnClose.setLayoutParams(btnLp);
        btnClose.setOnClickListener(v -> { d.dismiss(); reloadActionLabels(); renderEcosystemList(); });
        root.addView(btnClose); d.setContentView(root); d.show();
    }

    // V19.12.2.5: Đổi Theme thành NoActionBar để không chặn Status Bar (kéo QS)
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
        
        LinearLayout recSys = new LinearLayout(this); recSys.setOrientation(LinearLayout.VERTICAL); recSys.addView(createSectionTitle(T("🎤 BREATH RECORDER", "🎤 THIẾT KẾ HƠI THỞ GHI ÂM")));
        recSys.addView(createComboDropdown("Chất lượng Ghi âm (kbps)", "rec_kbps", new String[]{"64 kbps", "128 kbps", "256 kbps", "320 kbps (High)"}, 1));
        recSys.addView(createComboDropdown("Màu Hơi Thở Viền", "breath_color", new String[]{"Đỏ (Red)", "Lục (Green)", "Lam (Cyan)"}, 0));
        recSys.addView(createSlider("Độ trễ nhấp nháy Pixel 2XL (ms)", "breath_delay", 5000, 2500));
        
        LinearLayout recBtns = new LinearLayout(this); recBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button btnTestRec = new Button(this); btnTestRec.setText("▶ BẮT ĐẦU GHI ÂM"); btnTestRec.setBackground(getRounded("#E91E63", 20f)); btnTestRec.setTextColor(Color.WHITE); btnTestRec.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button btnStopRec = new Button(this); btnStopRec.setText("⏹ DỪNG LẠI"); btnStopRec.setBackground(getRounded("#333333", 20f)); btnStopRec.setTextColor(Color.WHITE); LinearLayout.LayoutParams tbLp2 = new LinearLayout.LayoutParams(0,-2,1f); tbLp2.setMargins(15,0,0,0); btnStopRec.setLayoutParams(tbLp2);
        
        // V19.12.2.5: Bọc try-catch để cứu UI khi Service văng
        btnTestRec.setOnClickListener(v -> { 
            try { Intent i = new Intent(this, RecorderService.class); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); } catch(Exception e) { Toast.makeText(this, "Lỗi gọi Service: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        btnStopRec.setOnClickListener(v -> { 
            try { Intent i = new Intent(this, RecorderService.class); stopService(i); } catch(Exception e) {}
        });
        recBtns.addView(btnTestRec); recBtns.addView(btnStopRec); recSys.addView(recBtns);
        pageDesign.addView(wrapCard(recSys));

        pageDesign.addView(createSectionTitle(T("CORE DESIGN", "THIẾT KẾ CỐT LÕI")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL); btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); btnEditHome = new Button(this); btnEditHome.setText("HOME"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP); btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0); btnEditLock.setOnClickListener(v -> { designTabState=0; refreshPreview(); btnEditLock.setBackground(getRounded("#00E5FF", 20f)); btnEditLock.setTextColor(Color.BLACK); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); renderSliders(); }); btnEditHome.setOnClickListener(v -> { designTabState=1; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#00E5FF", 20f)); btnEditHome.setTextColor(Color.BLACK); btnEditAnim.setBackground(getRounded("#222222", 20f)); btnEditAnim.setTextColor(Color.WHITE); renderSliders(); }); btnEditAnim.setOnClickListener(v -> { designTabState=2; refreshPreview(); btnEditLock.setBackground(getRounded("#222222", 20f)); btnEditLock.setTextColor(Color.WHITE); btnEditHome.setBackground(getRounded("#222222", 20f)); btnEditHome.setTextColor(Color.WHITE); btnEditAnim.setBackground(getRounded("#00E5FF", 20f)); btnEditAnim.setTextColor(Color.BLACK); renderSliders(); }); toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditAnim); pageDesign.addView(toggleRow); pageDesign.addView(designSliderContainer); btnEditLock.performClick();
    }
    
    @Override public void onActivityResult(int req, int res, Intent data) { 
        super.onActivityResult(req, res, data); 
        if(res == RESULT_OK && data != null && data.getData() != null) { 
            try { 
                if(req == 101) { 
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData()); os.write(new JSONObject(prefs.getAll()).toString().getBytes()); os.close(); Toast.makeText(this, "Đã Lưu Cấu Hình!", Toast.LENGTH_SHORT).show(); 
                } else if(req == 102) { 
                    java.io.InputStream is = getContentResolver().openInputStream(data.getData()); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is)); StringBuilder s = new StringBuilder(); String line; while((line=r.readLine())!=null) s.append(line); r.close(); JSONObject j = new JSONObject(s.toString()); SharedPreferences.Editor ed = prefs.edit(); Iterator<String> k = j.keys(); 
                    while(k.hasNext()) { String key = k.next(); Object v = j.get(key); if(v instanceof Boolean) ed.putBoolean(key, (Boolean)v); else if (v instanceof Integer) ed.putInt(key, (Integer)v); else if (v instanceof String) ed.putString(key, (String)v); else if (v instanceof Double) ed.putFloat(key, ((Double)v).floatValue()); else if (v instanceof Long) ed.putLong(key, (Long)v); } ed.apply(); Toast.makeText(this, "Đã Khôi Phục! Đang khởi động lại UI...", Toast.LENGTH_LONG).show(); recreate();
                } 
            } catch(Exception e) { Toast.makeText(this, "Lỗi Khôi phục: " + e.getMessage(), Toast.LENGTH_LONG).show(); } 
        } 
    }

    private void showPremiumDialog() { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("👑 PREMIUM ARCHITECT").setMessage("Lệnh ADB:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow").setPositiveButton("OK", null).show(); }
    private LinearLayout createDrawer(String title, View content) { LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL); container.setBackground(getRounded("#222222", 20f)); LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,20); container.setLayoutParams(clp); TextView header = new TextView(this); header.setText(title); header.setTextColor(Color.parseColor("#00E5FF")); header.setPadding(30,30,30,30); header.setTextSize(16); content.setVisibility(View.GONE); header.setOnClickListener(v -> { boolean isClosed = content.getVisibility() == View.GONE; content.setVisibility(isClosed ? View.VISIBLE : View.GONE); header.setBackground(getRounded(isClosed ? "#333333" : "#222222", 20f)); }); container.addView(header); container.addView(content); return container; }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,20); TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sp = createSpinner(); sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); sp.setSelection(prefs.getInt(key, def)); sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt(key,pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f)); l.addView(tv); l.addView(sp); return l; }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    
    private void renderSliders() { designSliderContainer.removeAllViews(); if(designTabState == 2) { 
        Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG"); btnTest.setBackground(getRounded("#FFC107", 20f)); btnTest.setTextColor(Color.BLACK); btnTest.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2); testLp.setMargins(0,0,0,20); btnTest.setLayoutParams(testLp); btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); i.setPackage(getPackageName()); sendBroadcast(i); Toast.makeText(this, "Playing Animation...", Toast.LENGTH_SHORT).show(); }); designSliderContainer.addView(btnTest);
        LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText("Chủ đề:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); designSliderContainer.addView(lC); 
        LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText("Kiểu chạy:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Nhấp Nháy", "1 Tia sáng nối đuôi", "2 Tia sáng đối xứng", "3 Tia sáng đều nhau"})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); designSliderContainer.addView(lS); 
        designSliderContainer.addView(createSlider("Chiều ngang Hiệu ứng (0=Full)", "anim_w", 2000, 0)); designSliderContainer.addView(createSlider("Chiều dọc Hiệu ứng (0=Full)", "anim_h", 3500, 0)); designSliderContainer.addView(createSlider("Độ đậm mờ hiệu ứng (Alpha)", "anim_alpha", 255, 255)); designSliderContainer.addView(createSlider("Độ dày viền", "anim_thick", 50, 12)); designSliderContainer.addView(createSlider("Thời gian Animation (ms)", "anim_dur", 5000, 1500)); designSliderContainer.addView(createSlider("Thời gian Vuốt+Giữ (All)", "hold_dur", 2000, 600)); designSliderContainer.addView(createSlider("Độ rung (ms) (All)", "vib_dur", 100, 30)); } else { String prefix = designTabState == 1 ? "home_" : "lock_"; 
        designSliderContainer.addView(createSectionTitle("5 EDGE BARS"));
        for(int i=0; i< 5; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+BARS[idx]+"_en", c).apply()); drawerContent.addView(cb); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+BARS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createSlider("Độ trong suốt", prefix+BARS[i]+"_alpha", 255, 50)); drawerContent.addView(createSlider("Chiều ngang", prefix+BARS[i]+"_w", 3000, 300)); drawerContent.addView(createSlider("Chiều dọc", prefix+BARS[i]+"_h", 3000, 60)); drawerContent.addView(createSlider("Toạ độ X", prefix+BARS[i]+"_x", 1000, 0)); drawerContent.addView(createSlider("Toạ độ Y", prefix+BARS[i]+"_y", 1000, 0)); designSliderContainer.addView(createDrawer(BAR_NAMES[i], drawerContent)); } 
        designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS"));
        for(int i=0; i<4; i++) { LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); drawerContent.addView(createComboDropdown("Hiển thị", prefix+"corner_"+CORNERS[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0)); drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+"corner_"+CORNERS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0)); drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix+"corner_"+CORNERS[i]+"_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix+"corner_"+CORNERS[i]+"_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix+"corner_"+CORNERS[i]+"_h", 2500, 100)); drawerContent.addView(createSlider("Di chuyển Ngang (X)", prefix+"corner_"+CORNERS[i]+"_x", 2500, 0)); drawerContent.addView(createSlider("Di chuyển Dọc (Y)", prefix+"corner_"+CORNERS[i]+"_y", 2500, 0)); drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix+"corner_"+CORNERS[i]+"_moon_w", 2500, 100)); drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix+"corner_"+CORNERS[i]+"_moon_h", 2500, 100)); drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_x", 2500, 1250)); drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_y", 2500, 1250)); drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_rad", 1000, 80)); drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_moon_rad", 1000, 80)); designSliderContainer.addView(createDrawer(CORNER_NAMES[i], drawerContent)); }
        LinearLayout globalDrawer = new LinearLayout(this); globalDrawer.setOrientation(LinearLayout.VERTICAL); globalDrawer.setPadding(30,10,30,30); globalDrawer.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500)); globalDrawer.addView(createSlider("Độ mờ vùng TRĂNG NON (Đậm/Nhạt)", prefix+"corner_moon_alpha", 255, 100)); globalDrawer.addView(createSlider("Độ mờ VIỀN GÓC (Đậm/Nhạt)", prefix+"corner_stroke_alpha", 255, 200)); globalDrawer.addView(createSlider("Độ đậm viền (Dày/Mỏng)", prefix+"corner_thick", 50, 8)); designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawer));
        } }

    private LinearLayout createSlider(String t, String k, int max, int def) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv); LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); }); row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; }
}
