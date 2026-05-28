package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }

    private String[] ACT_KEYS = new String[36]; private String[] ACT_LABS = new String[36];
    private String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private String[] BAR_NAMES;
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; private String[] CORNER_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"};
    private String[] COLOR_NAMES;

    private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl"};
    private String[] ALL_COMP_NAMES;

    private String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private String[] M_BAR_NAMES;

    private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"};
    private String[] C_GESTURE_NAMES;

    private LinearLayout pageDesign, pageConditions, pageIntents, pageTiles, pageMacros, listRules, designSliderContainer, navMain;
    private Button btnLock, btnHome, btnMorse, btnEditLock, btnEditHome, btnEditMorse, btnEditAnim;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0;
    private final String CURRENT_VERSION = "V19.12.3.4.5.3";
    private RelativeLayout rootLayout;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }

    private void refreshPreview() {
        boolean pLock = (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE && designTabState == 0) || (currentMainTab == 1 && currentGesTab == 0);
        boolean pMorse = (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE && designTabState == 2);
        prefs.edit().putBoolean("preview_lock", pLock).putBoolean("preview_morse", pMorse).apply();
        Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i);
    }

    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).putBoolean("preview_morse", false).apply(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "TOGGLE_MORSE", "YTDL_DOWNLOAD", "VOICE_RECORD"};
        String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn chính"), T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume", "Âm Lượng"), T("Screenshot", "Chụp màn hình"), "Camera", T("Notifications", "Mở Thông Báo"), T("Toggle Acc", "Bật/Tắt Trợ Năng"), T("Toggle Overlay", "Bật/Tắt Lớp Phủ"), T("Lock App (Morse)", "Khóa App (Morse)"), "YTDLnis", T("Voice Record", "Ghi âm ẩn")};
        for (int i = 0; i < 16; i++) { ACT_KEYS[i] = bK[i]; ACT_LABS[i] = bL[i]; }
        for (int i = 1; i <= 15; i++) { ACT_KEYS[15 + i] = "INTENT_" + i; ACT_LABS[15 + i] = prefs.getString("intent_" + i + "_name", "Intent " + i); }
        for (int i = 1; i <= 5; i++) { ACT_KEYS[30 + i] = "MACRO_" + i; ACT_LABS[30 + i] = prefs.getString("macro_" + i + "_name", "Macro " + i); }

        ALL_COMP_NAMES = new String[]{T("Bottom Right", "Thanh Đáy Phải"), T("Bottom Left", "Thanh Đáy Trái"), T("Top Right", "Thanh Cạnh Phải"), T("Top Left", "Thanh Cạnh Trái"), T("Top Center", "Thanh Đỉnh Giữa"), T("Corner BR", "Góc Viền Đáy Phải"), T("Corner BL", "Góc Viền Đáy Trái"), T("Corner TR", "Góc Viền Đỉnh Phải"), T("Corner TL", "Góc Viền Đỉnh Trái")};
        M_BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half Center", "Trung Tâm Trên"), T("Bottom Half Center", "Trung Tâm Dưới")};
        C_GESTURE_NAMES = new String[]{T("Tap", "1 Chạm"), T("Double Tap", "2 Chạm"), T("Long Press", "Nhấn Giữ"), T("Swipe Up", "Vuốt Lên"), T("Swipe Down", "Vuốt Xuống"), T("Swipe Left", "Vuốt Trái"), T("Swipe Right", "Vuốt Phải"), T("Up + Hold", "Vuốt Lên + Giữ"), T("Down + Hold", "Vuốt Xuống + Giữ"), T("Left + Hold", "Vuốt Trái + Giữ"), T("Right + Hold", "Vuốt Phải + Giữ"), T("Diagonal", "Vuốt Chéo"), T("Diagonal + Hold", "Vuốt Chéo + Giữ")};
        BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa")};
        CORNER_NAMES = new String[]{T("Bottom Right Corner", "Góc đáy phải"), T("Bottom Left Corner", "Góc đáy trái"), T("Top Right Corner", "Góc đỉnh phải"), T("Top Left Corner", "Góc đỉnh trái")};
        COLOR_NAMES = new String[]{T("White", "Trắng"), "Neon", "Cyberpunk", "Lava", "Ocean", "Matrix", "Sunset", "Google", "Aurora", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};
    }

    @Override public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && data.getData() != null) {
            try {
                if (req == 101) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                    os.write(new JSONObject(prefs.getAll()).toString().getBytes()); os.close();
                    Toast.makeText(this, T("Backup Saved!", "Đã Lưu Cấu Hình Backup!"), Toast.LENGTH_SHORT).show();
                } else if (req == 102) {
                    java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder s = new StringBuilder(); String line; while ((line = r.readLine()) != null) s.append(line); r.close();
                    JSONObject j = new JSONObject(s.toString()); SharedPreferences.Editor ed = prefs.edit();
                    Iterator<String> k = j.keys();
                    while (k.hasNext()) {
                        String key = k.next(); Object v = j.get(key);
                        if (v instanceof Boolean) ed.putBoolean(key, (Boolean) v);
                        else if (v instanceof Integer) ed.putInt(key, (Integer) v);
                        else if (v instanceof Long) ed.putInt(key, ((Long) v).intValue());
                        else if (v instanceof String) ed.putString(key, (String) v);
                    }
                    ed.commit(); Toast.makeText(this, T("Restored Successfully!", "Đã Khôi Phục Cấu Hình!"), Toast.LENGTH_LONG).show(); recreate();
                }
            } catch (Exception e) { Toast.makeText(this, "IO Error!", Toast.LENGTH_LONG).show(); }
        }
    }

    @Override public void onBackPressed() {
        if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) {
            closeDesignSpace();
            Button btnD = rootLayout.findViewWithTag("btnDesign");
            if (btnD != null) { btnD.setText("⚙️"); btnD.setBackground(getRounded("#333333", 100f)); }
        } else super.onBackPressed();
    }

    private void closeDesignSpace() {
        pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(View.VISIBLE);
        View fab = rootLayout.findViewWithTag("fab"); if (fab != null) fab.setVisibility(View.VISIBLE);
        View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse"); if (fabNewMorse != null) fabNewMorse.setVisibility(View.GONE);
        refreshPreview();
    }

    private void openDesignSpace() {
        currentMainTab = 0; refreshPreview(); navMain.setVisibility(View.GONE);
        pageConditions.setVisibility(View.GONE); pageIntents.setVisibility(View.GONE); pageTiles.setVisibility(View.GONE); pageMacros.setVisibility(View.GONE);
        pageDesign.setVisibility(View.VISIBLE);
        View fab = rootLayout.findViewWithTag("fab"); if (fab != null) fab.setVisibility(View.GONE);
        View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse"); if (fabNewMorse != null) fabNewMorse.setVisibility(View.GONE);
    }

    private Button createCircleBtn(String icon, String color) {
        Button b = new Button(this); b.setText(icon); b.setTextColor(Color.WHITE); b.setTextSize(17);
        b.setGravity(Gravity.CENTER); b.setPadding(0, 0, 0, 0); b.setBackground(getRounded(color, 100f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0);
        b.setLayoutParams(lp); return b;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();

        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1, -1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40, 60, 40, 40);

        LinearLayout headerRow = new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setPadding(0, 0, 0, 50);
        TextView title = new TextView(this); title.setText("Edge Bar\n" + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnLang = new Button(this); btnLang.setText(isVi ? "🇻🇳 TIẾNG VIỆT" : "🇺🇸 ENGLISH"); btnLang.setTextColor(Color.WHITE); btnLang.setBackground(getRounded("#2E7D32", 20f)); btnLang.setPadding(30, 20, 30, 20);
        btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        headerRow.addView(title); headerRow.addView(btnLang); main.addView(headerRow);

        if (!Settings.canDrawOverlays(this)) {
            Button btnReq = new Button(this); btnReq.setText(T("⚠️ GRANT OVERLAY", "⚠️ CẤP QUYỀN LỚP PHỦ"));
            btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE);
            btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
            main.addView(btnReq);
        }

        navMain = new LinearLayout(this); navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        Button btnNavCond = createNavBtn(T("CONDITIONS", "ĐIỀU KIỆN"));
        Button btnNavInt = createNavBtn("INTENTS");
        Button btnNavTiles = createNavBtn("QS TILES");
        Button btnNavMacs = createNavBtn("MACROS");
        navMain.addView(btnNavCond); navMain.addView(btnNavInt); navMain.addView(btnNavTiles); navMain.addView(btnNavMacs);
        main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL); buildIntentsSpace();
        pageTiles = new LinearLayout(this); pageTiles.setOrientation(LinearLayout.VERTICAL); buildTilesSpace();
        pageMacros = new LinearLayout(this); pageMacros.setOrientation(LinearLayout.VERTICAL); buildMacrosSpace();

        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageIntents); main.addView(pageTiles); main.addView(pageMacros);
        scroll.addView(main); rootLayout.addView(scroll);

        // Bottom Bar
        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);

        Button btnUpdate = createCircleBtn("U", "#333333"); btnUpdate.setTextSize(20);
        btnUpdate.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions"))));

        Button btnPremium = new Button(this); btnPremium.setText("PREMIUM"); btnPremium.setTextColor(Color.BLACK);
        btnPremium.setBackground(getRounded("#00E5FF", 100f)); btnPremium.setOnClickListener(v -> showPremiumDialog());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1); pLp.setMargins(10, 0, 10, 0);
        btnPremium.setLayoutParams(pLp); btnPremium.setPadding(40, 0, 40, 0);

        View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

        Button fab = new Button(this); fab.setText("+ NEW EB"); fab.setTextColor(Color.BLACK); fab.setBackground(getRounded("#00E5FF", 100f)); fab.setTag("fab");
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -1); fLp.setMargins(10, 0, 10, 0); fab.setLayoutParams(fLp); fab.setPadding(40, 0, 40, 0);
        fab.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1));

        Button btnDesign = createCircleBtn("⚙️", "#333333"); btnDesign.setTag("btnDesign");
        btnDesign.setOnClickListener(v -> {
            if (pageDesign.getVisibility() == View.VISIBLE) {
                closeDesignSpace();
                btnDesign.setText("⚙️");
                btnDesign.setBackground(getRounded("#333333", 100f));
            } else {
                openDesignSpace();
                btnDesign.setText("⬅");
                btnDesign.setBackground(getRounded("#D32F2F", 100f));
            }
        });

        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); bottomBar.addView(fab); bottomBar.addView(btnDesign);
        rootLayout.addView(bottomBar);

        Button fabNewMorse = new Button(this); fabNewMorse.setText("+ NEW MORSE"); fabNewMorse.setTextColor(Color.BLACK);
        fabNewMorse.setBackground(getRounded("#00E5FF", 100f)); fabNewMorse.setTag("fabNewMorse"); fabNewMorse.setVisibility(View.GONE);
        RelativeLayout.LayoutParams newMorseLp = new RelativeLayout.LayoutParams(-2, -2);
        newMorseLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); newMorseLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        newMorseLp.setMargins(0, 0, 40, 200); fabNewMorse.setLayoutParams(newMorseLp);
        fabNewMorse.setPadding(40, 30, 40, 30);
        fabNewMorse.setOnClickListener(v -> openMorseMapperDialog());
        rootLayout.addView(fabNewMorse);

        btnNavCond.setOnClickListener(v -> switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs));
        btnNavInt.setOnClickListener(v -> switchMainTab(2, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs));
        btnNavTiles.setOnClickListener(v -> switchMainTab(3, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs));
        btnNavMacs.setOnClickListener(v -> switchMainTab(4, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs));
        switchMainTab(1, btnNavCond, btnNavInt, btnNavTiles, btnNavMacs);
        setContentView(rootLayout);
    }

    private void switchMainTab(int idx, Button b1, Button b2, Button b3, Button b4) {
        currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE);
        pageDesign.setVisibility(View.GONE);
        pageConditions.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageIntents.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        pageTiles.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        pageMacros.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        View fab = rootLayout.findViewWithTag("fab"); if (fab != null) fab.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse");
        if (fabNewMorse != null) fabNewMorse.setVisibility((idx == 1 && currentGesTab == 2) ? View.VISIBLE : View.GONE);
        b1.setBackground(getRounded(idx == 1 ? "#222222" : "#00000000", 20f));
        b1.setTextColor(idx == 1 ? Color.parseColor("#00E5FF") : Color.GRAY);
        b2.setBackground(getRounded(idx == 2 ? "#222222" : "#00000000", 20f));
        b2.setTextColor(idx == 2 ? Color.parseColor("#00E5FF") : Color.GRAY);
        b3.setBackground(getRounded(idx == 3 ? "#222222" : "#00000000", 20f));
        b3.setTextColor(idx == 3 ? Color.parseColor("#00E5FF") : Color.GRAY);
        b4.setBackground(getRounded(idx == 4 ? "#222222" : "#00000000", 20f));
        b4.setTextColor(idx == 4 ? Color.parseColor("#00E5FF") : Color.GRAY);
        if (idx == 1) renderRulesList();
    }

    private void buildConditionsSpace() { /* giữ nguyên như bản trước */ 
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(0, 0, 0, 20);
        btnLock = createTabBtn("LOCKSCREEN");
        btnHome = createTabBtn("HOMESCREEN");
        btnMorse = createTabBtn("MORSE");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(0, 0, 15, 0);
        btnLock.setLayoutParams(p); btnHome.setLayoutParams(p); btnMorse.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        tabContainer.addView(btnLock); tabContainer.addView(btnHome); tabContainer.addView(btnMorse);
        pageConditions.addView(tabContainer);
        listRules = new LinearLayout(this); listRules.setOrientation(LinearLayout.VERTICAL); pageConditions.addView(listRules);

        btnLock.setOnClickListener(v -> { currentGesTab = 0; refreshPreview(); updateTabStyle(0); renderRulesList(); });
        btnHome.setOnClickListener(v -> { currentGesTab = 1; refreshPreview(); updateTabStyle(1); renderRulesList(); });
        btnMorse.setOnClickListener(v -> { currentGesTab = 2; refreshPreview(); updateTabStyle(2); renderRulesList(); });
        btnHome.performClick();
    }
    private void updateTabStyle(int mode) {
        btnLock.setBackground(getRounded(mode==0?"#00E5FF":"#222222", 20f));
        btnLock.setTextColor(mode==0?Color.BLACK:Color.WHITE);
        btnHome.setBackground(getRounded(mode==1?"#00E5FF":"#222222", 20f));
        btnHome.setTextColor(mode==1?Color.BLACK:Color.WHITE);
        btnMorse.setBackground(getRounded(mode==2?"#00E5FF":"#222222", 20f));
        btnMorse.setTextColor(mode==2?Color.BLACK:Color.WHITE);
        View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse");
        if (fabNewMorse != null) fabNewMorse.setVisibility(mode==2?View.VISIBLE:View.GONE);
    }
    private void renderRulesList() { /* giữ nguyên từ bản trước */ }
    private void openRuleBuilderDialog(String editKey, int preComp, int preGes) { /* giữ nguyên */ }
    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes, String copyKey) { /* giữ nguyên */ return null; }
    private void openMorseMapperDialog() { /* giữ nguyên */ }

    private void buildIntentsSpace() { /* giữ nguyên */ }
    private void buildMacrosSpace() { /* giữ nguyên */ }
    private void buildTilesSpace() { /* giữ nguyên */ }
    private void buildDesignSpace() {
        pageDesign.addView(createSectionTitle(T("BACKUP / RESTORE", "KHU VỰC SAO LƯU")));
        LinearLayout backupRow = new LinearLayout(this); backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnBackup = new Button(this); btnBackup.setText(T("BACKUP", "💾 SAO LƯU")); btnBackup.setBackground(getRounded("#2E7D32", 20f)); btnBackup.setTextColor(Color.WHITE);
        Button btnRestore = new Button(this); btnRestore.setText(T("RESTORE", "📂 PHỤC HỒI")); btnRestore.setBackground(getRounded("#EF6C00", 20f)); btnRestore.setTextColor(Color.WHITE);
        btnBackup.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup.txt"); startActivityForResult(i, 101); });
        btnRestore.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, 102); });
        backupRow.addView(btnBackup); backupRow.addView(btnRestore); pageDesign.addView(wrapCard(backupRow));

        LinearLayout secSys = new LinearLayout(this); secSys.setOrientation(LinearLayout.VERTICAL);
        secSys.addView(createSectionTitle(T("SYSTEM BEHAVIOR", "HÀNH VI HỆ THỐNG")));
        CheckBox cbKbd = new CheckBox(this); cbKbd.setText(T("Auto-hide on Keyboard", "Tự ẩn khi hiện Bàn Phím")); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true));
        cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply());
        secSys.addView(cbKbd);
        secSys.addView(createSectionTitle("BLACKLIST (Hide Overlay)"));
        secSys.addView(createInput("Packages (com.ex.app)", "blacklist"));
        secSys.addView(createSectionTitle("LOCKLIST (Morse AppLock)"));
        // Locklist row với App Picker
        LinearLayout locklistRow = new LinearLayout(this); locklistRow.setOrientation(LinearLayout.HORIZONTAL);
        final EditText etLocklist = new EditText(this);
        etLocklist.setHint(T("Package names (com.example.app)", "Tên gói (com.example.app)"));
        etLocklist.setText(prefs.getString("locklist", ""));
        etLocklist.setTag("locklist_input");
        etLocklist.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        etLocklist.setBackground(getRounded("#2C2C2C", 20f));
        etLocklist.setPadding(30,30,30,30);
        etLocklist.setTextColor(Color.WHITE);
        etLocklist.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { prefs.edit().putString("locklist", s.toString()).apply(); }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        Button btnPick = new Button(this);
        btnPick.setText(T("📱 Pick App", "📱 Chọn App"));
        btnPick.setBackground(getRounded("#00E5FF", 20f));
        btnPick.setTextColor(Color.BLACK);
        btnPick.setOnClickListener(v -> showAppPickerDialog("locklist"));
        locklistRow.addView(etLocklist);
        locklistRow.addView(btnPick);
        secSys.addView(locklistRow);
        pageDesign.addView(wrapCard(secSys));

        pageDesign.addView(createSectionTitle(T("CORE DESIGN (COLOR/SIZE)", "THIẾT KẾ CỐT LÕI (MÀU/KÍCH THƯỚC)")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        btnEditLock = new Button(this); btnEditLock.setText("LOCK");
        btnEditHome = new Button(this); btnEditHome.setText("HOME");
        btnEditMorse = new Button(this); btnEditMorse.setText("MORSE");
        btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA");
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL);
        btnEditLock.setOnClickListener(v -> { designTabState=0; refreshPreview(); updateDesignTabs(); renderSliders(); });
        btnEditHome.setOnClickListener(v -> { designTabState=1; refreshPreview(); updateDesignTabs(); renderSliders(); });
        btnEditMorse.setOnClickListener(v -> { designTabState=2; refreshPreview(); updateDesignTabs(); renderSliders(); });
        btnEditAnim.setOnClickListener(v -> { designTabState=3; refreshPreview(); updateDesignTabs(); renderSliders(); });
        toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditMorse); toggleRow.addView(btnEditAnim);
        pageDesign.addView(toggleRow); pageDesign.addView(designSliderContainer);
        btnEditHome.performClick();

        // Thêm music downloader
        addMusicDownloaderUI();
    }

    private void updateDesignTabs() {
        btnEditLock.setBackground(getRounded(designTabState==0?"#00E5FF":"#222222",20f));
        btnEditLock.setTextColor(designTabState==0?Color.BLACK:Color.WHITE);
        btnEditHome.setBackground(getRounded(designTabState==1?"#00E5FF":"#222222",20f));
        btnEditHome.setTextColor(designTabState==1?Color.BLACK:Color.WHITE);
        btnEditMorse.setBackground(getRounded(designTabState==2?"#00E5FF":"#222222",20f));
        btnEditMorse.setTextColor(designTabState==2?Color.BLACK:Color.WHITE);
        btnEditAnim.setBackground(getRounded(designTabState==3?"#00E5FF":"#222222",20f));
        btnEditAnim.setTextColor(designTabState==3?Color.BLACK:Color.WHITE);
    }

    private void addMusicDownloaderUI() {
        LinearLayout musicArea = new LinearLayout(this);
        musicArea.setOrientation(LinearLayout.VERTICAL);
        musicArea.setPadding(30,20,30,20);
        musicArea.setBackground(getRounded("#1E1E1E", 20f));
        final EditText etMusic = new EditText(this);
        etMusic.setHint(T("YouTube URL or song name", "Link YouTube hoặc tên bài hát"));
        etMusic.setText(prefs.getString("ytdl_last_query", ""));
        etMusic.setBackground(getRounded("#2C2C2C", 20f));
        etMusic.setPadding(30,30,30,30);
        etMusic.setTextColor(Color.WHITE);
        Button btnMusic = new Button(this);
        btnMusic.setText(T("🎵 DOWNLOAD", "🎵 TẢI NHẠC"));
        btnMusic.setBackground(getRounded("#FF5722", 20f));
        btnMusic.setTextColor(Color.WHITE);
        btnMusic.setOnClickListener(v -> {
            String query = etMusic.getText().toString().trim();
            if (query.isEmpty()) {
                Toast.makeText(MainActivity.this, T("Enter URL or song name", "Nhập URL hoặc tên bài hát"), Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("ytdl_last_query", query).apply();
            Intent yt = new Intent(Intent.ACTION_SEND);
            yt.setType("text/plain");
            yt.putExtra(Intent.EXTRA_TEXT, query);
            yt.setPackage("com.deniscerri.ytdlnis");
            yt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(yt); } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Install YTDLnis first", Toast.LENGTH_LONG).show();
            }
        });
        musicArea.addView(etMusic);
        musicArea.addView(btnMusic);
        pageDesign.addView(createDrawer(T("🎵 MUSIC DOWNLOADER", "🎵 TẢI NHẠC"), musicArea));
    }

    private void showAppPickerDialog(String targetKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(T("Choose App", "Chọn ứng dụng"));
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        ArrayList<String> appNames = new ArrayList<>();
        ArrayList<String> pkgNames = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            appNames.add(ri.loadLabel(pm).toString());
            pkgNames.add(ri.activityInfo.packageName);
        }
        builder.setItems(appNames.toArray(new String[0]), (dialog, which) -> {
            String current = prefs.getString(targetKey, "");
            String newPkg = pkgNames.get(which);
            if (current.contains(newPkg)) {
                Toast.makeText(MainActivity.this, T("Already in list", "Đã có trong danh sách"), Toast.LENGTH_SHORT).show();
            } else {
                String newList = current.isEmpty() ? newPkg : current + "," + newPkg;
                prefs.edit().putString(targetKey, newList).apply();
                Toast.makeText(MainActivity.this, T("Added", "Đã thêm") + " " + newPkg, Toast.LENGTH_LONG).show();
                refreshLocklistInput();
            }
        });
        builder.setNegativeButton(T("Cancel", "Hủy"), null);
        builder.show();
    }

    private void refreshLocklistInput() {
        ViewGroup root = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        EditText et = root.findViewWithTag("locklist_input");
        if (et != null) et.setText(prefs.getString("locklist", ""));
    }

    private void showPremiumDialog() { /* giữ nguyên */ }
    private LinearLayout createDrawer(String title, View content) { /* giữ nguyên */ return null; }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { /* giữ nguyên */ return null; }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C",20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C",20f)); et.setPadding(30,30,30,30); et.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){ prefs.edit().putString(k,s.toString()).apply(); }
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){}
    }); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E",40f)); card.setPadding(40,40,40,40); card.addView(content); return card; }

    private void renderSliders() {
        designSliderContainer.removeAllViews();
        if (designTabState == 3) {
            // Animation sliders (giữ nguyên)
            Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG");
            btnTest.setBackground(getRounded("#FFC107",20f)); btnTest.setTextColor(Color.BLACK);
            btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); sendBroadcast(i); });
            designSliderContainer.addView(btnTest);
            // ... các slider khác giữ nguyên
        } else {
            String prefix = (designTabState==0?"lock_":(designTabState==1?"home_":"morse_"));
            String[] bKeys = (designTabState==2?M_BARS:BARS);
            String[] bNames = (designTabState==2?M_BAR_NAMES:BAR_NAMES);
            if (designTabState == 2) {
                // Nút bật/tắt Morse Overlay
                LinearLayout toggleRow = new LinearLayout(this);
                toggleRow.setOrientation(LinearLayout.HORIZONTAL);
                TextView tv = new TextView(this); tv.setText(T("Morse Overlay: ", "Lớp phủ Morse: ")); tv.setTextColor(Color.WHITE);
                final Button btnToggle = new Button(this);
                btnToggle.setText(prefs.getBoolean("morse_mode_en", false) ? "ON" : "OFF");
                btnToggle.setBackground(getRounded("#00E5FF",20f));
                btnToggle.setTextColor(Color.BLACK);
                btnToggle.setOnClickListener(v -> {
                    boolean newState = !prefs.getBoolean("morse_mode_en", false);
                    prefs.edit().putBoolean("morse_mode_en", newState).apply();
                    btnToggle.setText(newState ? "ON" : "OFF");
                    Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE");
                    sendBroadcast(i);
                    Toast.makeText(this, T("Morse overlay "+(newState?"enabled":"disabled"), "Lớp phủ Morse "+(newState?"bật":"tắt")), Toast.LENGTH_SHORT).show();
                });
                toggleRow.addView(tv); toggleRow.addView(btnToggle);
                designSliderContainer.addView(toggleRow);
            }
            // Thêm các slider còn lại...
        }
    }
    private LinearLayout createSlider(String t, String k, int max, int def) { /* giữ nguyên */ return null; }
}
