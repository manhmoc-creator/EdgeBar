package com.manhmoc.edgebar;
import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.View; import android.widget.*; import java.util.ArrayList;

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
    private LinearLayout pageDesign, pageConditions, pageIntents, pageTiles, pageMacros, listRules, designSliderContainer, navMain; 
    private Button btnLock, btnHome, btnEditLock, btnEditHome, btnEditAnim;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; 
    private final String CURRENT_VERSION = "V19.12.1.9"; private RelativeLayout rootLayout;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    private void refreshPreview() { boolean p = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); prefs.edit().putBoolean("preview_lock", p).apply(); }
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).apply(); }
    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD", "VOICE_RECORD"}; 
        String[] bL = {"Không có", "Quay lại", "Màn hình chính", "Đa nhiệm", "Tắt màn hình", "Đèn pin", "Menu Nguồn", "Menu Âm Lượng", "Chụp ảnh màn hình", "Camera", "Mở Thông Báo", "Bật/Tắt Trợ Năng", "Bật/Tắt Lớp Phủ", "Tải bằng YTDLnis", "Ghi âm ẩn"};
        for(int i=0; i<15; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
        for(int i=1; i<=15; i++) { ACT_KEYS[14+i]="INTENT_"+i; ACT_LABS[14+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
        for(int i=1; i<=5; i++) { ACT_KEYS[29+i]="MACRO_"+i; ACT_LABS[29+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
    }
    @Override public void onBackPressed() { if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); } else super.onBackPressed(); }
    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(View.VISIBLE); rootLayout.findViewWithTag("fab").setVisibility(View.VISIBLE); refreshPreview(); }
    private void openDesignSpace() { refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); rootLayout.findViewWithTag("fab").setVisibility(View.GONE); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); reloadActionLabels();
        BAR_NAMES = new String[]{"Đáy phải", "Đáy trái", "Cạnh Phải", "Cạnh Trái", "Đỉnh giữa"}; CORNER_NAMES = new String[]{"Góc đáy phải", "Góc đáy trái", "Góc đỉnh phải", "Góc đỉnh trái"}; COLOR_NAMES = new String[]{"Trắng", "Neon", "Cyberpunk", "Lava", "Ocean", "Matrix", "Sunset", "Google", "Aurora", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};

        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,60,40,40); 
        TextView title = new TextView(this); title.setText("Edge Bar\n" + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(30); title.setPadding(0, 0, 0, 50); main.addView(title);
        navMain = new LinearLayout(this); navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        Button btnNavCond = createNavBtn("ĐIỀU KIỆN"); Button btnNavInt = createNavBtn("INTENTS"); Button btnNavTiles = createNavBtn("QS TILES"); Button btnNavMacs = createNavBtn("MACROS"); 
        navMain.addView(btnNavCond); navMain.addView(btnNavInt); navMain.addView(btnNavTiles); navMain.addView(btnNavMacs); main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL); buildIntentsSpace();
        pageTiles = new LinearLayout(this); pageTiles.setOrientation(LinearLayout.VERTICAL); buildTilesSpace();
        pageMacros = new LinearLayout(this); pageMacros.setOrientation(LinearLayout.VERTICAL); buildMacrosSpace();
        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageIntents); main.addView(pageTiles); main.addView(pageMacros);
        scroll.addView(main); rootLayout.addView(scroll);

        // UI ERGONOMIC - TẤT CẢ NÚT ĐIỀU KHIỂN DỒN HẾT XUỐNG ĐÁY
        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        Button btnU = createCircleBtn("🔄", "#333333", "#FFFFFF"); btnU.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions"))));
        Button btnP = createCircleBtn("👑", "#333333", "#FFD700"); btnP.setOnClickListener(v->showPremiumDialog());
        Button btnL = createCircleBtn(isVi ? "🇻🇳" : "🇺🇸", "#2E7D32", "#FFFFFF"); btnL.setOnClickListener(v->{isVi=!isVi; prefs.edit().putBoolean("lang_vi", isVi).apply(); recreate();});
        Button btnD = createCircleBtn("⚙️", "#333333", "#FFFFFF"); btnD.setTag("btnDesign"); btnD.setOnClickListener(v -> { if(pageDesign.getVisibility()==View.VISIBLE) {closeDesignSpace(); btnD.setText("⚙️");} else {openDesignSpace(); btnD.setText("⬅");} });
        Button fab = new Button(this); fab.setText("+ NEW EB"); fab.setTextColor(Color.BLACK); fab.setBackground(getRounded("#00E5FF", 100f)); fab.setTag("fab"); LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(0, -1, 1f); fLp.setMargins(10,0,10,0); fab.setLayoutParams(fLp); fab.setOnClickListener(v -> { if(currentMainTab==1) openRuleBuilderDialog(null, -1, -1); else openNewbieDialog(); });
        bottomBar.addView(btnU); bottomBar.addView(btnP); bottomBar.addView(btnL); bottomBar.addView(fab); bottomBar.addView(btnD); rootLayout.addView(bottomBar);

        btnNavCond.setOnClickListener(v->switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavInt.setOnClickListener(v->switchMainTab(2, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavTiles.setOnClickListener(v->switchMainTab(3, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavMacs.setOnClickListener(v->switchMainTab(4, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); 
        switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab); setContentView(rootLayout);
    }
    // ... [Các hàm buildSpace giữ nguyên logic như bản 19.12.1.8] ...
    private void switchMainTab(int idx, Button b1, Button b2, Button b3, Button b4, Button fab) { currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE); pageDesign.setVisibility(View.GONE); pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE); pageIntents.setVisibility(idx==2?View.VISIBLE:View.GONE); pageTiles.setVisibility(idx==3?View.VISIBLE:View.GONE); pageMacros.setVisibility(idx==4?View.VISIBLE:View.GONE); fab.setVisibility(idx==1?View.VISIBLE:View.GONE); b1.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); b2.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); b3.setBackground(getRounded(idx==3?"#222222":"#00000000", 20f)); b4.setBackground(getRounded(idx==4?"#222222":"#00000000", 20f)); if(idx==1) renderRulesList(); }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private void showPremiumDialog() { new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("👑 PREMIUM ARCHITECT").setMessage("Lệnh ADB:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow").setPositiveButton("OK", null).show(); }
    private void openEcoEditor(String type, int id) { /* ... Giữ nguyên logic cũ ... */ }
    private void openNewbieDialog() { /* ... Giữ nguyên logic cũ ... */ }
    private void buildConditionsSpace() { /* ... Giữ nguyên logic cũ ... */ }
    private void buildIntentsSpace() { /* ... Giữ nguyên logic cũ ... */ }
    private void buildMacrosSpace() { /* ... Giữ nguyên logic cũ ... */ }
    private void buildTilesSpace() { /* ... Giữ nguyên logic cũ ... */ }
    private void renderRulesList() { /* ... Giữ nguyên logic cũ ... */ }
    private void renderEcosystemList() { /* ... Giữ nguyên logic cũ ... */ }
    private void openRuleBuilderDialog(String editKey, int preComp, int preGes) { /* ... Giữ nguyên logic cũ ... */ }
    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes) { /* ... Giữ nguyên logic cũ ... */ }
    private LinearLayout createDrawer(String title, View content) { /* ... Giữ nguyên logic cũ ... */ }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { /* ... Giữ nguyên logic cũ ... */ }
    private void buildDesignSpace() { /* ... Giữ nguyên logic cũ ... */ }
    private LinearLayout createSlider(String t, String k, int max, int def) { /* ... Giữ nguyên logic cũ ... */ }
}
