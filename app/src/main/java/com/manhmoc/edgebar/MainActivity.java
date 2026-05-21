package com.manhmoc.edgebar;
import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.View; import android.widget.*; import java.util.ArrayList;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    private String[] ACT_KEYS = new String[34]; private String[] ACT_LABS = new String[34];
    private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl"}; 
    private String[] ALL_COMP_NAMES = {"Thanh Đáy Phải", "Thanh Đáy Trái", "Thanh Cạnh Phải", "Thanh Cạnh Trái", "Thanh Đỉnh Giữa", "Góc Viền Đáy Phải", "Góc Viền Đáy Trái", "Góc Viền Đỉnh Phải", "Góc Viền Đỉnh Trái"};
    private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"}; 
    private String[] C_GESTURE_NAMES = {"1 Chạm", "2 Chạm", "Nhấn Giữ", "Vuốt Lên", "Vuốt Xuống", "Vuốt Trái", "Vuốt Phải", "Vuốt Lên + Giữ", "Vuốt Xuống + Giữ", "Vuốt Trái + Giữ", "Vuốt Phải + Giữ", "Vuốt Chéo", "Vuốt Chéo + Giữ"};
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; 
    private String[] COLOR_NAMES = {"Trắng", "Neon", "Cyberpunk", "Lava", "Ocean", "Matrix", "Sunset", "Google", "Aurora", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};

    private LinearLayout pageDesign, pageConditions, pageIntents, pageTiles, pageMacros, listRules, designSliderContainer, navMain; 
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; 
    private final String CURRENT_VERSION = "V19.12.1.10"; 
    private RelativeLayout rootLayout;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    private void refreshPreview() { boolean p = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); prefs.edit().putBoolean("preview_lock", p).apply(); }
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).apply(); }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD"}; 
        String[] bL = {"Không có", "Quay lại", "Màn hình chính", "Đa nhiệm", "Tắt màn hình", "Đèn pin", "Menu Nguồn", "Menu Âm Lượng", "Chụp ảnh màn hình", "Camera", "Mở Thông Báo", "Bật/Tắt Trợ Năng", "Bật/Tắt Lớp Phủ", "Tải YTDLnis"};
        for(int i=0; i<14; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
        for(int i=1; i<=15; i++) { ACT_KEYS[13+i]="INTENT_"+i; ACT_LABS[13+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
        for(int i=1; i<=5; i++) { ACT_KEYS[28+i]="MACRO_"+i; ACT_LABS[28+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
    }

    @Override public void onBackPressed() { if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) closeDesignSpace(); else super.onBackPressed(); }
    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(View.VISIBLE); View fab = rootLayout.findViewWithTag("fab"); if(fab != null) fab.setVisibility(View.VISIBLE); refreshPreview(); }
    private void openDesignSpace() { currentMainTab = 0; refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageIntents.setVisibility(View.GONE); pageTiles.setVisibility(View.GONE); pageMacros.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); View fab = rootLayout.findViewWithTag("fab"); if(fab != null) fab.setVisibility(View.GONE); }

    private Button createRectBtn(String text, String color, boolean isFab) { Button b = new Button(this); b.setText(text); b.setTextColor(isFab ? Color.BLACK : Color.WHITE); b.setGravity(Gravity.CENTER); b.setBackground(getRounded(color, 25f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isFab ? -2 : 130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();

        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,60,40,40); 
        
        // V19.12.1.10: Tên app lên cao, Nút chức năng xuống dưới
        Button btnLang = createRectBtn(isVi ? "🇻🇳" : "🇺🇸", "#333333", false); btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        Button btnUpdate = createRectBtn("🔄", "#333333", false); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = createRectBtn("PREMIUM", "#00E5FF", false); btnPremium.setOnClickListener(v -> showPremiumDialog());
        
        TextView title = new TextView(this); title.setText("Edge Bar " + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(30); title.setPadding(0, 30, 0, 30);
        main.addView(btnLang); main.addView(btnUpdate); main.addView(btnPremium); main.addView(title);
        
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText("⚠️ CẤP QUYỀN LỚP PHỦ"); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

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

        // Nút hành động đáy
        LinearLayout fabBar = new LinearLayout(this); fabBar.setOrientation(LinearLayout.HORIZONTAL); fabBar.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); fabBar.setLayoutParams(bLp);
        Button btnDesign = createRectBtn("⚙️", "#333333", false); btnDesign.setOnClickListener(v -> openDesignSpace());
        Button fab = createRectBtn("+ NEW EB", "#00E5FF", true); fab.setTag("fab"); fab.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1));
        fabBar.addView(btnDesign); fabBar.addView(fab); rootLayout.addView(fabBar);

        btnNavCond.setOnClickListener(v->switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavInt.setOnClickListener(v->switchMainTab(2, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavTiles.setOnClickListener(v->switchMainTab(3, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); btnNavMacs.setOnClickListener(v->switchMainTab(4, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab)); 
        switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs, fab);
        setContentView(rootLayout);
    }
    // ... (Giữ nguyên các hàm buildConditionsSpace, renderRulesList, openRuleBuilderDialog v.v từ V19.12.1.6)
