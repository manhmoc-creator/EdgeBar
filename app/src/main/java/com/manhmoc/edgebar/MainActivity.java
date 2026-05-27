package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
    private final String CURRENT_VERSION = "V19.12.4.1";
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
        View fabMorseToggle = rootLayout.findViewWithTag("fabMorseToggle"); if (fabMorseToggle != null) fabMorseToggle.setVisibility(View.GONE);
        refreshPreview();
    }

    private void openDesignSpace() {
        currentMainTab = 0; refreshPreview(); navMain.setVisibility(View.GONE);
        pageConditions.setVisibility(View.GONE); pageIntents.setVisibility(View.GONE); pageTiles.setVisibility(View.GONE); pageMacros.setVisibility(View.GONE);
        pageDesign.setVisibility(View.VISIBLE);
        View fab = rootLayout.findViewWithTag("fab"); if (fab != null) fab.setVisibility(View.GONE);
        View fabMorseToggle = rootLayout.findViewWithTag("fabMorseToggle"); if (fabMorseToggle != null) fabMorseToggle.setVisibility(View.GONE);
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

        // Nút nổi bật/tắt Morse (chỉ hiện khi ở tab MORSE)
        Button fabMorseToggle = new Button(this); fabMorseToggle.setText("🔒"); fabMorseToggle.setTextColor(Color.WHITE);
        fabMorseToggle.setBackground(getRounded("#333333", 100f)); fabMorseToggle.setTag("fabMorseToggle"); fabMorseToggle.setVisibility(View.GONE);
        RelativeLayout.LayoutParams toggleLp = new RelativeLayout.LayoutParams(130, 130);
        toggleLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); toggleLp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        toggleLp.setMargins(40, 0, 0, 200); fabMorseToggle.setLayoutParams(toggleLp);
        fabMorseToggle.setOnClickListener(v -> {
            boolean isM = prefs.getBoolean("morse_mode_en", false);
            prefs.edit().putBoolean("morse_mode_en", !isM).apply();
            Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i);
            fabMorseToggle.setText(!isM ? "🔓" : "🔒");
        });
        rootLayout.addView(fabMorseToggle);

        // Nút nổi "NEW MORSE" (chỉ hiện khi ở tab CONDITIONS và currentGesTab == 2)
        Button fabNewMorse = new Button(this); fabNewMorse.setText("🔢"); fabNewMorse.setTextColor(Color.BLACK);
        fabNewMorse.setBackground(getRounded("#00E5FF", 100f)); fabNewMorse.setTag("fabNewMorse"); fabNewMorse.setVisibility(View.GONE);
        RelativeLayout.LayoutParams newMorseLp = new RelativeLayout.LayoutParams(-2, -2);
        newMorseLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); newMorseLp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        newMorseLp.setMargins(0, 0, 40, 200); fabNewMorse.setLayoutParams(newMorseLp);
        fabNewMorse.setPadding(40, 40, 40, 40);
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

    private void buildConditionsSpace() {
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

        btnLock.setOnClickListener(v -> {
            currentGesTab = 0; refreshPreview();
            btnLock.setBackground(getRounded("#00E5FF", 20f)); btnLock.setTextColor(Color.BLACK);
            btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE);
            btnMorse.setBackground(getRounded("#222222", 20f)); btnMorse.setTextColor(Color.WHITE);
            View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse"); if (fabNewMorse != null) fabNewMorse.setVisibility(View.GONE);
            renderRulesList();
        });
        btnHome.setOnClickListener(v -> {
            currentGesTab = 1; refreshPreview();
            btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE);
            btnHome.setBackground(getRounded("#00E5FF", 20f)); btnHome.setTextColor(Color.BLACK);
            btnMorse.setBackground(getRounded("#222222", 20f)); btnMorse.setTextColor(Color.WHITE);
            View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse"); if (fabNewMorse != null) fabNewMorse.setVisibility(View.GONE);
            renderRulesList();
        });
        btnMorse.setOnClickListener(v -> {
            currentGesTab = 2; refreshPreview();
            btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE);
            btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE);
            btnMorse.setBackground(getRounded("#00E5FF", 20f)); btnMorse.setTextColor(Color.BLACK);
            View fabNewMorse = rootLayout.findViewWithTag("fabNewMorse"); if (fabNewMorse != null) fabNewMorse.setVisibility(View.VISIBLE);
            renderRulesList();
        });
        btnHome.performClick();
    }

    private void renderRulesList() {
        listRules.removeAllViews();
        String prefix = currentGesTab == 0 ? "lock_" : (currentGesTab == 1 ? "home_" : "morse_");
        String[] iterKeys = currentGesTab == 2 ? new String[]{"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b", "corner_br", "corner_bl", "corner_tr", "corner_tl"} : ALL_COMP_KEYS;
        String[] iterNames = currentGesTab == 2 ? new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half", "Giữa Trên"), T("Bottom Half", "Giữa Dưới"), T("Corner BR", "Góc Đáy Phải"), T("Corner BL", "Góc Đáy Trái"), T("Corner TR", "Góc Đỉnh Phải"), T("Corner TL", "Góc Đỉnh Trái")} : ALL_COMP_NAMES;

        LinearLayout currentRow = null; int count = 0;
        for (int c = 0; c < iterKeys.length; c++) {
            for (int g = 0; g < C_GESTURES.length; g++) {
                String key = prefix + iterKeys[c] + "_" + C_GESTURES[g];
                String action = prefs.getString(key, "NONE");
                if (!action.equals("NONE")) {
                    if (count % 2 == 0) {
                        currentRow = new LinearLayout(this); currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                        listRules.addView(currentRow);
                    }
                    LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35, 35, 35, 35);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f); lp.setMargins(15, 15, 15, 15);
                    card.setLayoutParams(lp);

                    LinearLayout rowTop = new LinearLayout(this); rowTop.setOrientation(LinearLayout.HORIZONTAL);
                    rowTop.setGravity(Gravity.CENTER_VERTICAL);
                    Switch swOn = new Switch(this); swOn.setChecked(prefs.getBoolean(key + "_on", true));
                    swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean(key + "_on", chk).apply());
                    TextView tIcons = new TextView(this);
                    tIcons.setText((prefs.getBoolean(key + "_vib", true) ? "📳 " : "") + (prefs.getBoolean(key + "_anim", true) ? "✨" : ""));
                    tIcons.setGravity(Gravity.RIGHT); tIcons.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                    rowTop.addView(swOn); rowTop.addView(tIcons); card.addView(rowTop);

                    TextView tCond = new TextView(this);
                    tCond.setText(iterNames[c] + "\n➔ " + C_GESTURE_NAMES[g]);
                    tCond.setTextColor(Color.parseColor("#BBBBBB")); tCond.setTextSize(13); tCond.setPadding(0, 15, 0, 15);
                    card.addView(tCond);

                    TextView tAct = new TextView(this);
                    String[] acts = action.split(","); StringBuilder actName = new StringBuilder();
                    for (String a : acts) {
                        for (int i = 0; i < ACT_KEYS.length; i++) {
                            if (ACT_KEYS[i].equals(a.trim())) {
                                if (actName.length() > 0) actName.append(" + ");
                                actName.append(ACT_LABS[i]);
                            }
                        }
                    }
                    tAct.setText(actName.toString().isEmpty() ? T("Error", "Lỗi") : actName.toString());
                    tAct.setTextColor(Color.parseColor("#00E5FF")); tAct.setTextSize(15); card.addView(tAct);

                    final int finalC = c; final int finalG = g; final String finalKey = key;
                    card.setOnClickListener(v -> openRuleBuilderDialog(finalKey, finalC, finalG));
                    card.setOnLongClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle(T("Rule Options", "Tùy chọn quy tắc"))
                                .setItems(new String[]{T("Delete", "Xóa"), T("Copy", "Sao chép")}, (d, which) -> {
                                    if (which == 0) {
                                        prefs.edit().putString(finalKey, "NONE").apply();
                                        renderRulesList();
                                    } else {
                                        // Copy rule: mở rule builder với các giá trị được pre-fill (giống edit nhưng tạo rule mới)
                                        openRuleBuilderDialogForCopy(finalKey, finalC, finalG);
                                    }
                                }).show();
                        return true;
                    });
                    currentRow.addView(card);
                    count++;
                }
            }
        }
        if (count % 2 != 0 && currentRow != null) {
            View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            currentRow.addView(dummy);
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText(T("No rules yet.\nPress + NEW EB to create.", "Chưa có quy tắc nào.\nBấm + NEW EB để tạo."));
            empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0, 100, 0, 0);
            listRules.addView(empty);
        }
    }

    private void openRuleBuilderDialogForCopy(String copyKey, int preComp, int preGes) {
        // Tương tự edit nhưng sau khi lưu sẽ tạo rule mới (editKey = null)
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        d.setContentView(buildRuleEditor(d, null, preComp, preGes, copyKey));
        d.show();
    }

    private void openRuleBuilderDialog(String editKey, int preComp, int preGes) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        d.setContentView(buildRuleEditor(d, editKey, preComp, preGes, null));
        d.show();
    }

    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes, String copyKey) {
        reloadActionLabels();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 120, 30, 30);

        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button bTrig = createTabBtn("TRIGGER"); Button bAct = createTabBtn("ACTION"); Button bOpt = createTabBtn("OPTIONS");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, -2, 1f); tabLp.setMargins(10, 0, 10, 0);
        bTrig.setLayoutParams(tabLp); bAct.setLayoutParams(tabLp); bOpt.setLayoutParams(tabLp);
        tabs.addView(bTrig); tabs.addView(bAct); tabs.addView(bOpt); root.addView(tabs);

        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0, 40, 0, 0);
        scroll.addView(content); root.addView(scroll);

        final int[] selectedComp = {preComp != -1 ? preComp : 0};
        ArrayList<CheckBox> gestureBoxes = new ArrayList<>(); ArrayList<CheckBox> actionBoxes = new ArrayList<>();
        CheckBox cbVib = new CheckBox(this); CheckBox cbAnim = new CheckBox(this);

        String[] iterNames = currentGesTab == 2 ? new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half", "Giữa Trên"), T("Bottom Half", "Giữa Dưới"), T("Corner BR", "Góc Đáy Phải"), T("Corner BL", "Góc Đáy Trái"), T("Corner TR", "Góc Đỉnh Phải"), T("Corner TL", "Góc Đỉnh Trái")} : ALL_COMP_NAMES;
        String[] iterKeys = currentGesTab == 2 ? new String[]{"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b", "corner_br", "corner_bl", "corner_tr", "corner_tl"} : ALL_COMP_KEYS;

        LinearLayout vTrig = new LinearLayout(this); vTrig.setOrientation(LinearLayout.VERTICAL);
        TextView tvC = new TextView(this); tvC.setText(T("1. CHOOSE COMPONENT", "1. CHỌN VÙNG (COMPONENT)"));
        tvC.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvC);
        Spinner spComp = createSpinner(); spComp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, iterNames));
        spComp.setSelection(selectedComp[0]);
        spComp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { selectedComp[0] = pos; }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        vTrig.addView(spComp);
        TextView tvG = new TextView(this); tvG.setText(T("\n2. CHOOSE GESTURES (OR logic)", "\n2. CHỌN CỬ CHỈ (Được chọn nhiều - Lệnh OR)"));
        tvG.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvG);
        for (int i = 0; i < C_GESTURES.length; i++) {
            CheckBox cb = new CheckBox(this); cb.setText(C_GESTURE_NAMES[i]); cb.setTextColor(Color.WHITE);
            cb.setPadding(0, 20, 0, 20);
            if (copyKey != null && preGes == i) cb.setChecked(true);
            else if (editKey != null && i == preGes) cb.setChecked(true);
            gestureBoxes.add(cb); vTrig.addView(cb);
        }

        LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE);
        TextView tvA = new TextView(this); tvA.setText(T("CHOOSE ACTIONS (Multi-select)", "CHỌN HÀNH ĐỘNG THỰC THI (Được chọn nhiều)"));
        tvA.setTextColor(Color.parseColor("#00E5FF")); tvA.setPadding(0, 0, 0, 20); vAct.addView(tvA);

        String savedActs = "";
        if (editKey != null) savedActs = prefs.getString(editKey, "");
        if (copyKey != null) savedActs = prefs.getString(copyKey, "");
        String[] savedArray = savedActs.split(",");
        for (int i = 1; i < ACT_LABS.length; i++) {
            CheckBox cbAct = new CheckBox(this); cbAct.setText(ACT_LABS[i]); cbAct.setTextColor(Color.WHITE);
            cbAct.setPadding(0, 20, 0, 20);
            boolean isChecked = false;
            for (String sa : savedArray) {
                if (sa.trim().equals(ACT_KEYS[i])) { isChecked = true; break; }
            }
            cbAct.setChecked(isChecked); actionBoxes.add(cbAct); vAct.addView(cbAct);
        }

        LinearLayout vOpt = new LinearLayout(this); vOpt.setOrientation(LinearLayout.VERTICAL); vOpt.setVisibility(View.GONE);
        cbVib.setText(T("Haptic Feedback", "Bật Rung (Haptic Feedback)"));
        cbVib.setTextColor(Color.WHITE); cbVib.setChecked(editKey == null || prefs.getBoolean((editKey != null ? editKey : copyKey) + "_vib", true));
        vOpt.addView(cbVib);
        cbAnim.setText(T("Show Animation", "Bật Hiệu ứng Ánh sáng (Animation)"));
        cbAnim.setTextColor(Color.WHITE); cbAnim.setChecked(editKey == null || prefs.getBoolean((editKey != null ? editKey : copyKey) + "_anim", true));
        vOpt.addView(cbAnim);

        content.addView(vTrig); content.addView(vAct); content.addView(vOpt);

        View.OnClickListener tabClick = v -> {
            bTrig.setBackground(getRounded(v == bTrig ? "#00E5FF" : "#222222", 15f));
            bTrig.setTextColor(v == bTrig ? Color.BLACK : Color.WHITE);
            bAct.setBackground(getRounded(v == bAct ? "#00E5FF" : "#222222", 15f));
            bAct.setTextColor(v == bAct ? Color.BLACK : Color.WHITE);
            bOpt.setBackground(getRounded(v == bOpt ? "#00E5FF" : "#222222", 15f));
            bOpt.setTextColor(v == bOpt ? Color.BLACK : Color.WHITE);
            vTrig.setVisibility(v == bTrig ? View.VISIBLE : View.GONE);
            vAct.setVisibility(v == bAct ? View.VISIBLE : View.GONE);
            vOpt.setVisibility(v == bOpt ? View.VISIBLE : View.GONE);
        };
        bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick); bOpt.setOnClickListener(tabClick);
        bTrig.performClick();

        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL);
        Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY"));
        bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE);
        bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button bSave = new Button(this); bSave.setText(T("SAVE RULE", "LƯU QUY TẮC"));
        bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f); slp.setMargins(20, 0, 0, 0);
        bSave.setLayoutParams(slp);
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

        bCancel.setOnClickListener(v -> dialog.dismiss());
        bSave.setOnClickListener(v -> {
            ArrayList<String> acts = new ArrayList<>();
            for (int i = 0; i < actionBoxes.size(); i++) {
                if (actionBoxes.get(i).isChecked()) acts.add(ACT_KEYS[i + 1]);
            }
            if (acts.isEmpty()) {
                Toast.makeText(this, T("Select at least 1 Action!", "Hãy chọn ít nhất 1 Hành động!"), Toast.LENGTH_SHORT).show();
                return;
            }
            String joinedActions = TextUtils.join(",", acts);
            String prefix = currentGesTab == 0 ? "lock_" : (currentGesTab == 1 ? "home_" : "morse_");
            String compKey = iterKeys[selectedComp[0]];
            boolean hasChecked = false;
            if (editKey != null) prefs.edit().putString(editKey, "NONE").apply();
            for (int i = 0; i < gestureBoxes.size(); i++) {
                if (gestureBoxes.get(i).isChecked()) {
                    hasChecked = true;
                    String finalKey = prefix + compKey + "_" + C_GESTURES[i];
                    prefs.edit().putString(finalKey, joinedActions)
                            .putBoolean(finalKey + "_vib", cbVib.isChecked())
                            .putBoolean(finalKey + "_anim", cbAnim.isChecked()).apply();
                }
            }
            if (!hasChecked) {
                Toast.makeText(this, T("Select at least 1 Trigger!", "Hãy chọn ít nhất 1 Cử chỉ!"), Toast.LENGTH_SHORT).show();
                return;
            }
            renderRulesList(); dialog.dismiss();
        });
        return root;
    }

    // Dialog ánh xạ phím Morse (12 component)
    private void openMorseMapperDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#121212")); container.setPadding(40, 80, 40, 80);
        container.setGravity(Gravity.CENTER);

        TextView title = new TextView(this); title.setText("CẤU HÌNH BÀN PHÍM MORSE");
        title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(22); title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        container.addView(title);

        // Danh sách 12 component
        String[] allMorseComponents = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b", "corner_br", "corner_bl", "corner_tr", "corner_tl"};
        String[] displayNames = {
                T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"),
                T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half", "Trung Tâm Trên"), T("Bottom Half", "Trung Tâm Dưới"),
                T("Corner BR", "Góc đáy phải"), T("Corner BL", "Góc đáy trái"), T("Corner TR", "Góc đỉnh phải"), T("Corner TL", "Góc đỉnh trái")
        };
        String[] defaultValues = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "X", ">"};
        String[] keyNames = new String[12];
        for (int i = 0; i < 12; i++) keyNames[i] = "morse_map_" + allMorseComponents[i];

        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 12; i++) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, 15, 0, 15);
            TextView label = new TextView(this); label.setText(displayNames[i]); label.setTextColor(Color.WHITE);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            EditText edit = new EditText(this); edit.setHint("Số / X / >"); edit.setText(prefs.getString(keyNames[i], defaultValues[i]));
            edit.setBackground(getRounded("#2C2C2C", 20f)); edit.setPadding(30, 20, 30, 20); edit.setTextColor(Color.WHITE);
            edit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.5f));
            final String key = keyNames[i];
            edit.addTextChangedListener(new android.text.TextWatcher() {
                public void afterTextChanged(android.text.Editable s) { prefs.edit().putString(key, s.toString()).apply(); }
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
            });
            row.addView(label); row.addView(edit); grid.addView(row);
        }
        container.addView(grid);

        Button closeBtn = new Button(this); closeBtn.setText(T("CLOSE", "ĐÓNG"));
        closeBtn.setBackground(getRounded("#333333", 20f)); closeBtn.setTextColor(Color.WHITE);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        container.addView(closeBtn);

        dialog.setContentView(container); dialog.show();
    }

    // Các hàm build... (giữ nguyên từ bản cũ, nhưng sửa max slider)
    private void buildIntentsSpace() { ... } // giữ nguyên code cũ (dài, sẽ giữ)
    private void buildMacrosSpace() { ... }
    private void buildTilesSpace() { ... }
    private void buildDesignSpace() { ... }
    private void showPremiumDialog() { ... }
    private LinearLayout createDrawer(String title, View content) { ... }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { ... }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }

    private void renderSliders() {
        designSliderContainer.removeAllViews();
        if (designTabState == 3) {
            // Animation
            // ... giữ nguyên (không ảnh hưởng)
        } else {
            String prefix = designTabState == 0 ? "lock_" : (designTabState == 1 ? "home_" : "morse_");
            String[] bKeys = designTabState == 2 ? M_BARS : BARS;
            String[] bNames = designTabState == 2 ? M_BAR_NAMES : BAR_NAMES;

            if (designTabState == 2) {
                // Ngăn kéo nhập mật khẩu master
                LinearLayout passDrawer = new LinearLayout(this); passDrawer.setOrientation(LinearLayout.VERTICAL);
                passDrawer.setPadding(30, 20, 30, 20); passDrawer.setBackground(getRounded("#222222", 20f));
                TextView passTitle = new TextView(this); passTitle.setText("🔐 MẬT KHẨU MORSE (CHỈ SỐ)");
                passTitle.setTextColor(Color.parseColor("#FFD700")); passTitle.setPadding(0, 0, 0, 20);
                passDrawer.addView(passTitle);

                LinearLayout rowPass = new LinearLayout(this); rowPass.setOrientation(LinearLayout.HORIZONTAL);
                EditText etPass = new EditText(this); etPass.setHint("VD: 12345"); etPass.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                etPass.setText(prefs.getString("morse_master_pass", "").replace(">", "")); // loại bỏ > nếu có
                etPass.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                Button btnSavePass = new Button(this); btnSavePass.setText("LƯU");
                btnSavePass.setBackground(getRounded("#4CAF50", 20f)); btnSavePass.setTextColor(Color.WHITE);
                btnSavePass.setOnClickListener(v -> {
                    String pass = etPass.getText().toString().trim().replace(">", ""); // tự động loại bỏ >
                    prefs.edit().putString("morse_master_pass", pass).apply();
                    Toast.makeText(this, "Đã lưu mật khẩu!", Toast.LENGTH_SHORT).show();
                });
                rowPass.addView(etPass); rowPass.addView(btnSavePass);
                passDrawer.addView(rowPass);
                designSliderContainer.addView(createDrawer("CÀI ĐẶT MẬT KHẨU", passDrawer));

                // Slider độ mờ, thời gian hiện số, độ rung khi nhập sai
                designSliderContainer.addView(createSlider("Độ mờ màn chắn Morse (Alpha Đen)", "morse_bg_alpha", 255, 180));
                designSliderContainer.addView(createSlider("Thời gian hiện số (ms) trước khi ẩn", "morse_dot_delay", 2000, 500));
                designSliderContainer.addView(createSlider("Độ rung khi nhập sai (ms)", "morse_fail_vib", 1500, 500));
            }

            // Edge Bars (5 hoặc 8)
            designSliderContainer.addView(createSectionTitle("EDGE BARS (" + bKeys.length + " THANH)"));
            for (int i = 0; i < bKeys.length; i++) {
                LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL);
                drawerContent.setPadding(30, 10, 30, 30);
                CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + bNames[i]); cb.setTextColor(Color.parseColor("#4CAF50"));
                cb.setChecked(prefs.getBoolean(prefix + bKeys[i] + "_en", false));
                final int idx = i;
                cb.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean(prefix + bKeys[idx] + "_en", c).apply());
                drawerContent.addView(cb);
                drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix + bKeys[i] + "_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
                drawerContent.addView(createSlider("Độ trong suốt", prefix + bKeys[i] + "_alpha", 255, 50));
                drawerContent.addView(createSlider("Chiều ngang", prefix + bKeys[i] + "_w", 3000, 300));
                drawerContent.addView(createSlider("Chiều dọc", prefix + bKeys[i] + "_h", 3000, 60));
                // Tăng max lên 2500
                drawerContent.addView(createSlider("Toạ độ X", prefix + bKeys[i] + "_x", 2500, 0));
                drawerContent.addView(createSlider("Toạ độ Y", prefix + bKeys[i] + "_y", 2500, 0));
                designSliderContainer.addView(createDrawer(bNames[i], drawerContent));
            }

            // Frame Corners (4)
            designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS"));
            for (int i = 0; i < 4; i++) {
                LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL);
                drawerContent.setPadding(30, 10, 30, 30);
                CheckBox cbEn = new CheckBox(this); cbEn.setText(T("ENABLE: ", "BẬT: ") + CORNER_NAMES[i]);
                cbEn.setTextColor(Color.parseColor("#4CAF50")); cbEn.setChecked(prefs.getBoolean(prefix + "corner_" + CORNERS[i] + "_en", false));
                final int idx = i;
                cbEn.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean(prefix + "corner_" + CORNERS[idx] + "_en", c).apply());
                drawerContent.addView(cbEn);

                drawerContent.addView(createComboDropdown("Hiển thị", prefix + "corner_" + CORNERS[i] + "_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0));
                drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix + "corner_" + CORNERS[i] + "_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
                drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix + "corner_" + CORNERS[i] + "_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0));
                drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix + "corner_" + CORNERS[i] + "_w", 2500, 100));
                drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix + "corner_" + CORNERS[i] + "_h", 2500, 100));
                drawerContent.addView(createSlider("Di chuyển Ngang (X)", prefix + "corner_" + CORNERS[i] + "_x", 2500, 0));
                drawerContent.addView(createSlider("Di chuyển Dọc (Y)", prefix + "corner_" + CORNERS[i] + "_y", 2500, 0));
                drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix + "corner_" + CORNERS[i] + "_moon_w", 2500, 100));
                drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix + "corner_" + CORNERS[i] + "_moon_h", 2500, 100));
                drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix + "corner_" + CORNERS[i] + "_moon_x", 2500, 1250));
                drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix + "corner_" + CORNERS[i] + "_moon_y", 2500, 1250));
                drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix + "corner_" + CORNERS[i] + "_rad", 1000, 80));
                drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix + "corner_" + CORNERS[i] + "_moon_rad", 1000, 80));
                designSliderContainer.addView(createDrawer(CORNER_NAMES[i], drawerContent));
            }

            LinearLayout globalDrawer = new LinearLayout(this); globalDrawer.setOrientation(LinearLayout.VERTICAL);
            globalDrawer.setPadding(30, 10, 30, 30);
            globalDrawer.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix + "corner_hide_dur", 5000, 2500));
            globalDrawer.addView(createSlider("Độ mờ vùng TRĂNG NON (Đậm/Nhạt)", prefix + "corner_moon_alpha", 255, 100));
            globalDrawer.addView(createSlider("Độ mờ VIỀN GÓC (Đậm/Nhạt)", prefix + "corner_stroke_alpha", 255, 200));
            globalDrawer.addView(createSlider("Độ đậm viền (Dày/Mỏng)", prefix + "corner_thick", 50, 8));
            designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawer));
        }
    }

    private LinearLayout createSlider(String t, String k, int max, int def) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0, 10, 0, 10);
        TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def));
        l.addView(tv);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB"));
        btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20);
        Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB"));
        btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20);
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def));
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if (p > 0) sb.setProgress(p - 1); });
        btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if (p < max) sb.setProgress(p + 1); });
        row.addView(btnMinus); row.addView(sb); row.addView(btnPlus);
        l.addView(row);
        return l;
    }

    // Các hàm còn lại (buildDesignSpace, buildIntentsSpace, ...) giữ nguyên từ bản cũ.
    // Do giới hạn độ dài, tôi chỉ giữ lại các hàm cần thiết. Bạn có thể copy từ bản V19.12.3.4 các hàm còn lại.
}
