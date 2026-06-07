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
import android.os.Handler;
import android.provider.Settings;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[36]; private String[] ACT_LABS = new String[36];
    private String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private String[] BAR_NAMES; 
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; private String[] CORNER_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; private String[] COLOR_NAMES;

    private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl"}; 
    private String[] ALL_COMP_NAMES;
    private String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
    private String[] M_BAR_NAMES;
    private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"}; 
    private String[] C_GESTURE_NAMES;

    private LinearLayout pageDesign, pageConditions, pageEcosystem, listRules, designSliderContainer, navMain; 
    private Button btnLock, btnHome, btnEditLock, btnEditHome, btnEditMorse, btnEditAnim;
    private int designTabState = 0; private int currentMainTab = 1; private int currentGesTab = 0; 
   private final String CURRENT_VERSION = "V19.12.3.4.6.9"; 
    private RelativeLayout rootLayout;

    private int ecoType = 0;
    private LinearLayout ecoContainer;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    
    private void refreshPreview() { 
        boolean pLock = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); 
        boolean pMorse = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==2);
        prefs.edit().putBoolean("preview_lock", pLock).putBoolean("preview_morse", pMorse).apply(); 
        Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); 
    }
    
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).putBoolean("preview_morse", false).apply(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "TOGGLE_MORSE", "YTDL_DOWNLOAD", "VOICE_RECORD"}; 
        String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn chính"), T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume", "Âm Lượng"), T("Screenshot", "Chụp màn hình"), "Camera", T("Notifications", "Mở Thông Báo"), T("Toggle Acc", "Bật/Tắt Trợ Năng"), T("Toggle Overlay", "Bật/Tắt Lớp Phủ"), T("Lock App (Morse)", "Khóa App (Morse)"), "YTDLnis", T("Voice Record", "Ghi âm ẩn")};
        for(int i=0; i<16; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
        for(int i=1; i<=15; i++) { ACT_KEYS[15+i]="INTENT_"+i; ACT_LABS[15+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
        for(int i=1; i<=5; i++) { ACT_KEYS[30+i]="MACRO_"+i; ACT_LABS[30+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
        
        ALL_COMP_NAMES = new String[]{T("Bottom Right", "Thanh Đáy Phải"), T("Bottom Left", "Thanh Đáy Trái"), T("Top Right", "Thanh Cạnh Phải"), T("Top Left", "Thanh Cạnh Trái"), T("Top Center", "Thanh Đỉnh Giữa"), T("Corner BR", "Góc Viền Đáy Phải"), T("Corner BL", "Góc Viền Đáy Trái"), T("Corner TR", "Góc Viền Đỉnh Phải"), T("Corner TL", "Góc Viền Đỉnh Trái")};
        M_BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half Center", "Trung Tâm Trên"), T("Bottom Half Center", "Trung Tâm Dưới")};
        C_GESTURE_NAMES = new String[]{T("Tap", "1 Chạm"), T("Double Tap", "2 Chạm"), T("Long Press", "Nhấn Giữ"), T("Swipe Up", "Vuốt Lên"), T("Swipe Down", "Vuốt Xuống"), T("Swipe Left", "Vuốt Trái"), T("Swipe Right", "Vuốt Phải"), T("Up + Hold", "Vuốt Lên + Giữ"), T("Down + Hold", "Vuốt Xuống + Giữ"), T("Left + Hold", "Vuốt Trái + Giữ"), T("Right + Hold", "Vuốt Phải + Giữ"), T("Diagonal", "Vuốt Chéo"), T("Diagonal + Hold", "Vuốt Chéo + Giữ")};
        BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa")}; 
        CORNER_NAMES = new String[]{T("Bottom Right Corner", "Góc đáy phải"), T("Bottom Left Corner", "Góc đáy trái"), T("Top Right Corner", "Góc đỉnh phải"), T("Top Left Corner", "Góc đỉnh trái")}; 
        COLOR_NAMES = new String[]{T("White", "Trắng"), "Neon", "Cyberpunk", "Lava", "Ocean", "Matrix", "Sunset", "Google", "Aurora", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};
    }

    @Override public void onActivityResult(int req, int res, Intent data) { 
        super.onActivityResult(req, res, data); 
        if(res == RESULT_OK && data != null && data.getData() != null) { 
            try { 
                if(req == 101) { 
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData()); 
                    os.write(new JSONObject(prefs.getAll()).toString().getBytes()); os.close(); 
                    Toast.makeText(this, T("Backup Saved!", "Đã Lưu Cấu Hình Backup!"), Toast.LENGTH_SHORT).show(); 
                } else if(req == 102) { 
                    java.io.InputStream is = getContentResolver().openInputStream(data.getData()); 
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is)); 
                    StringBuilder s = new StringBuilder(); String line; while((line=r.readLine())!=null) s.append(line); r.close(); 
                    JSONObject j = new JSONObject(s.toString()); SharedPreferences.Editor ed = prefs.edit(); 
                    Iterator<String> k = j.keys(); 
                    while(k.hasNext()) { 
                        String key = k.next(); Object v = j.get(key); 
                        if(v instanceof Boolean) ed.putBoolean(key, (Boolean)v); 
                        else if (v instanceof Integer) ed.putInt(key, (Integer)v); 
                        else if (v instanceof Long) ed.putInt(key, ((Long)v).intValue()); 
                        else if (v instanceof String) ed.putString(key, (String)v); 
                    } 
                    ed.commit(); Toast.makeText(this, T("Restored Successfully!", "Đã Khôi Phục Cấu Hình!"), Toast.LENGTH_LONG).show(); recreate();
                } else if (req == 103) {
                    String imagePath = data.getData().toString();
                    prefs.edit().putString("morse_bg_image", imagePath).apply();
                    prefs.edit().putInt("morse_bg_type", 1).apply();
                    Toast.makeText(this, "Đã chọn ảnh nền cho lớp phủ Morse!", Toast.LENGTH_SHORT).show();
                }
            } catch(Exception e) { Toast.makeText(this, "IO Error!", Toast.LENGTH_LONG).show(); } 
        } 
    }

    @Override public void onBackPressed() { if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); Button btnD = rootLayout.findViewWithTag("btnDesign"); if(btnD!=null){btnD.setText("⚙️"); btnD.setBackground(getRounded("#333333", 100f));} } else super.onBackPressed(); }
    private void closeDesignSpace() { pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(View.VISIBLE); View fab = rootLayout.findViewWithTag("fab"); if(fab != null) fab.setVisibility(View.VISIBLE); refreshPreview(); }
    private void openDesignSpace() { currentMainTab = 0; refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); View fab = rootLayout.findViewWithTag("fab"); if(fab != null) fab.setVisibility(View.GONE); }

    private Button createCircleBtn(String icon, String color) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(color, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();
        
        rootLayout = new RelativeLayout(this); rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        ScrollView scroll = new ScrollView(this); RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); rLp.bottomMargin = 240; scroll.setLayoutParams(rLp);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,60,40,40); 
        
        LinearLayout headerRow = new LinearLayout(this); headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL); headerRow.setPadding(0, 0, 0, 50);
        TextView title = new TextView(this); title.setText("Edge Bar\n" + CURRENT_VERSION); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnLang = new Button(this); btnLang.setText(isVi ? "🇻🇳 TIẾNG VIỆT" : "🇺🇸 ENGLISH"); btnLang.setTextColor(Color.WHITE); btnLang.setBackground(getRounded("#2E7D32", 20f)); btnLang.setPadding(30, 20, 30, 20); 
        btnLang.setOnClickListener(v -> { prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate(); });
        headerRow.addView(title); headerRow.addView(btnLang); main.addView(headerRow);
        
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText(T("⚠️ GRANT OVERLAY", "⚠️ CẤP QUYỀN LỚP PHỦ")); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

// --- DEVICE ADMIN ---
        android.app.admin.DevicePolicyManager dpm =
            (android.app.admin.DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName adminComp = new ComponentName(this, EdgeAdminReceiver.class);
        if (!dpm.isAdminActive(adminComp)) {
            Button btnAdmin = new Button(this);
            btnAdmin.setText("⚠️ CẤP QUYỀN ADMIN DEVICE (Bảo vệ lớp phủ Morse)");
            btnAdmin.setBackground(getRounded("#D32F2F", 25f));
            btnAdmin.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams adminLp = new LinearLayout.LayoutParams(-1, -2);
            adminLp.setMargins(0, 10, 0, 0);
            btnAdmin.setLayoutParams(adminLp);
            btnAdmin.setOnClickListener(v -> {
                Intent adminIntent = new Intent(
                    android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                adminIntent.putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp);
                adminIntent.putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Bảo vệ lớp phủ Morse khỏi bị gỡ và chống gỡ cài đặt nhanh từ Home.");
                startActivity(adminIntent);
            });
            main.addView(btnAdmin);
        }

        // --- USAGE STATS ---
        try {
            android.app.AppOpsManager aom =
                (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = aom.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                Button btnUsage = new Button(this);
                btnUsage.setText("⚠️ CẤP QUYỀN TRUY CẬP DỮ LIỆU SỬ DỤNG");
                btnUsage.setBackground(getRounded("#D32F2F", 25f));
                btnUsage.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams usageLp = new LinearLayout.LayoutParams(-1, -2);
                usageLp.setMargins(0, 10, 0, 0);
                btnUsage.setLayoutParams(usageLp);
                btnUsage.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
                main.addView(btnUsage);
            }
        } catch (Exception e) { /* bỏ qua nếu thiết bị không hỗ trợ */ }


        navMain = new LinearLayout(this); navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        Button btnNavCond = createNavBtn(T("CONDITIONS", "ĐIỀU KIỆN"));
        Button btnNavEco = createNavBtn("ECOSYSTEM");
        navMain.addView(btnNavCond); navMain.addView(btnNavEco); main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); buildEcosystemSpace();

        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageEcosystem);
        scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        Button btnUpdate = createCircleBtn("U", "#333333"); btnUpdate.setTextSize(20); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = new Button(this); btnPremium.setText("PREMIUM"); btnPremium.setTextColor(Color.BLACK); btnPremium.setBackground(getRounded("#00E5FF", 100f)); btnPremium.setOnClickListener(v -> showPremiumDialog());
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1); pLp.setMargins(10,0,10,0); btnPremium.setLayoutParams(pLp); btnPremium.setPadding(40,0,40,0);
        View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        Button fab = new Button(this); fab.setText("+ NEW EB"); fab.setTextColor(Color.BLACK); fab.setBackground(getRounded("#00E5FF", 100f)); fab.setTag("fab");
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, -1); fLp.setMargins(10,0,10,0); fab.setLayoutParams(fLp); fab.setPadding(40,0,40,0); 
        fab.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1, ""));
        Button btnDesign = createCircleBtn("⚙️", "#333333"); btnDesign.setTag("btnDesign");
        btnDesign.setOnClickListener(v -> { if(pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); btnDesign.setText("⚙️"); btnDesign.setBackground(getRounded("#333333", 100f)); } else { openDesignSpace(); btnDesign.setText("⬅"); btnDesign.setBackground(getRounded("#D32F2F", 100f)); } });
        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); bottomBar.addView(fab); bottomBar.addView(btnDesign);
        rootLayout.addView(bottomBar);

        btnNavCond.setOnClickListener(v -> switchMainTab(1, btnNavCond, btnNavEco, fab));
        btnNavEco.setOnClickListener(v -> switchMainTab(2, btnNavCond, btnNavEco, fab));
        switchMainTab(1, btnNavCond, btnNavEco, fab);
        setContentView(rootLayout);
    }

    private void switchMainTab(int idx, Button b1, Button b2, Button fab) { 
        currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE);
        pageDesign.setVisibility(View.GONE); 
        pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE);
        pageEcosystem.setVisibility(idx==2?View.VISIBLE:View.GONE);
        fab.setVisibility(idx==1?View.VISIBLE:View.GONE);
        b1.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); b1.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY);
        b2.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); b2.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY);
        if(idx==1) renderRulesList();
        if(idx==2) renderEcosystem();
    }

    // ==================== KHÔNG GIAN ĐIỀU KIỆN ====================
    private void buildConditionsSpace() {
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 0, 0, 20); 
        btnLock = createTabBtn("LOCKSCREEN"); btnHome = createTabBtn("HOMESCREEN"); 
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f); p.setMargins(0,0,15,0); 
        btnLock.setLayoutParams(p); btnHome.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageConditions.addView(tabContainer);
        listRules = new LinearLayout(this); listRules.setOrientation(LinearLayout.VERTICAL); pageConditions.addView(listRules);
        
        btnLock.setOnClickListener(v -> { currentGesTab=0; refreshPreview(); btnLock.setBackground(getRounded("#00E5FF", 20f)); btnLock.setTextColor(Color.BLACK); btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE); renderRulesList(); }); 
        btnHome.setOnClickListener(v -> { currentGesTab=1; refreshPreview(); btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE); btnHome.setBackground(getRounded("#00E5FF", 20f)); btnHome.setTextColor(Color.BLACK); renderRulesList(); }); 
        btnHome.performClick();
    }

    private void renderRulesList() {
        listRules.removeAllViews(); 
        String prefix = currentGesTab == 0 ? "lock_" : "home_";
        LinearLayout currentRow = null; int count = 0;
        for (int c = 0; c < ALL_COMP_KEYS.length; c++) {
            for (int g = 0; g < C_GESTURES.length; g++) {
                String key = prefix + ALL_COMP_KEYS[c] + "_" + C_GESTURES[g];
                String action = prefs.getString(key, "NONE");
                if (!action.equals("NONE")) {
                    if(count % 2 == 0) { currentRow = new LinearLayout(this); currentRow.setOrientation(LinearLayout.HORIZONTAL); currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); listRules.addView(currentRow); }
                    
                    LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35); 
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2, 1f); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);

                    LinearLayout rowTop = new LinearLayout(this); rowTop.setOrientation(LinearLayout.HORIZONTAL); rowTop.setGravity(Gravity.CENTER_VERTICAL);
                    Switch swOn = new Switch(this); swOn.setChecked(prefs.getBoolean(key+"_on", true)); swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean(key+"_on", chk).apply());
                    TextView tIcons = new TextView(this); tIcons.setText((prefs.getBoolean(key+"_vib",true)?"📳 ":"") + (prefs.getBoolean(key+"_anim",true)?"✨":"")); tIcons.setGravity(Gravity.RIGHT); tIcons.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                    rowTop.addView(swOn); rowTop.addView(tIcons); card.addView(rowTop);

                    TextView tCond = new TextView(this); tCond.setText(ALL_COMP_NAMES[c] + "\n➔ " + C_GESTURE_NAMES[g]); tCond.setTextColor(Color.parseColor("#BBBBBB")); tCond.setTextSize(13); tCond.setPadding(0,15,0,15); card.addView(tCond);
                    
                    TextView tAct = new TextView(this); String[] acts = action.split(","); StringBuilder actName = new StringBuilder();
                    for(String a : acts) { for(int i=0;i<ACT_KEYS.length;i++) { if(ACT_KEYS[i].equals(a.trim())) { if(actName.length()>0) actName.append(" + "); actName.append(ACT_LABS[i]); } } }
                    tAct.setText(actName.toString().isEmpty() ? T("Error", "Lỗi") : actName.toString()); tAct.setTextColor(Color.parseColor("#00E5FF")); tAct.setTextSize(15); card.addView(tAct);

                    final int finalC = c; final int finalG = g; final String finalActs = action;
                    
                    LinearLayout rowBot = new LinearLayout(this); rowBot.setOrientation(LinearLayout.HORIZONTAL); rowBot.setPadding(0,20,0,0);
                    Button btnEdit = new Button(this); btnEdit.setText("SỬA"); btnEdit.setBackground(getRounded("#333333", 15f)); btnEdit.setTextColor(Color.WHITE); btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                    btnEdit.setOnClickListener(v -> openRuleBuilderDialog(key, finalC, finalG, ""));
                    
                    Button btnCopy = new Button(this); btnCopy.setText("COPY"); btnCopy.setBackground(getRounded("#2196F3", 15f)); btnCopy.setTextColor(Color.WHITE); LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(0,-2,1f); cpLp.setMargins(10,0,10,0); btnCopy.setLayoutParams(cpLp);
                    btnCopy.setOnClickListener(v -> openRuleBuilderDialog(null, finalC, finalG, finalActs)); 
                    
                    rowBot.addView(btnEdit); rowBot.addView(btnCopy); card.addView(rowBot);

                    card.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle(T("Delete Rule?", "Xóa Quy tắc?")).setPositiveButton(T("DELETE", "XÓA"), (d,w)->{prefs.edit().putString(key, "NONE").apply(); renderRulesList();}).setNegativeButton(T("CANCEL", "HỦY"), null).show(); return true; });
                    currentRow.addView(card); count++;
                }
            }
        }
        if(count % 2 != 0 && currentRow != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); currentRow.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText(T("No rules yet.\nPress + NEW EB to create.", "Chưa có quy tắc nào.\nBấm + NEW EB để tạo.")); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listRules.addView(empty); }
    }

    private void openRuleBuilderDialog(String editKey, int preComp, int preGes, String copyActs) { Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen); d.setContentView(buildRuleEditor(d, editKey, preComp, preGes, copyActs)); d.show(); }

    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes, String copyActs) {
        reloadActionLabels();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 120, 30, 30);
        
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button bTrig = createTabBtn("TRIGGER"); Button bAct = createTabBtn("ACTION"); Button bOpt = createTabBtn("OPTIONS");
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,-2,1f); tabLp.setMargins(10,0,10,0); 
        bTrig.setLayoutParams(tabLp); bAct.setLayoutParams(tabLp); bOpt.setLayoutParams(tabLp);
        tabs.addView(bTrig); tabs.addView(bAct); tabs.addView(bOpt); root.addView(tabs);

        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,40,0,0); scroll.addView(content); root.addView(scroll);

        final int[] selectedComp = {preComp != -1 ? preComp : 0}; 
        ArrayList<CheckBox> gestureBoxes = new ArrayList<>(); ArrayList<CheckBox> actionBoxes = new ArrayList<>();
        CheckBox cbVib = new CheckBox(this); CheckBox cbAnim = new CheckBox(this);

        LinearLayout vTrig = new LinearLayout(this); vTrig.setOrientation(LinearLayout.VERTICAL);
        TextView tvC = new TextView(this); tvC.setText(T("1. CHOOSE COMPONENT", "1. CHỌN VÙNG (COMPONENT)")); tvC.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvC);
        Spinner spComp = createSpinner(); spComp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ALL_COMP_NAMES)); spComp.setSelection(selectedComp[0]); spComp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){selectedComp[0] = pos;}public void onNothingSelected(AdapterView<?> p){}}); vTrig.addView(spComp);
        TextView tvG = new TextView(this); tvG.setText(T("\n2. CHOOSE GESTURES (OR logic)", "\n2. CHỌN CỬ CHỈ (Được chọn nhiều - Lệnh OR)")); tvG.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvG);
        for (int i=0; i<C_GESTURES.length; i++) { CheckBox cb = new CheckBox(this); cb.setText(C_GESTURE_NAMES[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0,20,0,20); if(preGes != -1 && i == preGes) cb.setChecked(true); gestureBoxes.add(cb); vTrig.addView(cb); }
        
        LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE);
        TextView tvA = new TextView(this); tvA.setText(T("CHOOSE ACTIONS (Multi-select)", "CHỌN HÀNH ĐỘNG THỰC THI (Được chọn nhiều)")); tvA.setTextColor(Color.parseColor("#00E5FF")); tvA.setPadding(0,0,0,20); vAct.addView(tvA);
        
        String savedActs = editKey != null ? prefs.getString(editKey, "") : copyActs;
        String[] savedArray = savedActs.split(",");
        for (int i=1; i<ACT_LABS.length; i++) { 
            CheckBox cbAct = new CheckBox(this); cbAct.setText(ACT_LABS[i]); cbAct.setTextColor(Color.WHITE); cbAct.setPadding(0,20,0,20); 
            boolean isChecked = false;
            for(String sa : savedArray) { if(sa.trim().equals(ACT_KEYS[i])) { isChecked = true; break; } }
            cbAct.setChecked(isChecked); actionBoxes.add(cbAct); vAct.addView(cbAct); 
        }

        LinearLayout vOpt = new LinearLayout(this); vOpt.setOrientation(LinearLayout.VERTICAL); vOpt.setVisibility(View.GONE);
        cbVib.setText(T("Haptic Feedback", "Bật Rung (Haptic Feedback)")); cbVib.setTextColor(Color.WHITE); cbVib.setChecked(editKey == null || prefs.getBoolean(editKey+"_vib", true)); vOpt.addView(cbVib);
        cbAnim.setText(T("Show Animation", "Bật Hiệu ứng Ánh sáng (Animation)")); cbAnim.setTextColor(Color.WHITE); cbAnim.setChecked(editKey == null || prefs.getBoolean(editKey+"_anim", true)); vOpt.addView(cbAnim);

        content.addView(vTrig); content.addView(vAct); content.addView(vOpt);

        View.OnClickListener tabClick = v -> { bTrig.setBackground(getRounded(v==bTrig?"#00E5FF":"#222222", 15f)); bTrig.setTextColor(v==bTrig?Color.BLACK:Color.WHITE); bAct.setBackground(getRounded(v==bAct?"#00E5FF":"#222222", 15f)); bAct.setTextColor(v==bAct?Color.BLACK:Color.WHITE); bOpt.setBackground(getRounded(v==bOpt?"#00E5FF":"#222222", 15f)); bOpt.setTextColor(v==bOpt?Color.BLACK:Color.WHITE); vTrig.setVisibility(v==bTrig?View.VISIBLE:View.GONE); vAct.setVisibility(v==bAct?View.VISIBLE:View.GONE); vOpt.setVisibility(v==bOpt?View.VISIBLE:View.GONE); }; bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick); bOpt.setOnClickListener(tabClick); bTrig.performClick();

        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL);
        Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY")); bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText(T("SAVE RULE", "LƯU QUY TẮC")); bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp);
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

        bCancel.setOnClickListener(v -> dialog.dismiss());
        bSave.setOnClickListener(v -> {
            ArrayList<String> acts = new ArrayList<>(); for(int i=0; i<actionBoxes.size(); i++) { if(actionBoxes.get(i).isChecked()) acts.add(ACT_KEYS[i+1]); }
            if(acts.isEmpty()) { Toast.makeText(this, T("Select at least 1 Action!", "Hãy chọn ít nhất 1 Hành động!"), Toast.LENGTH_SHORT).show(); return; }
            String joinedActions = TextUtils.join(",", acts); 
            String prefix = currentGesTab == 0 ? "lock_" : "home_"; 
            String compKey = ALL_COMP_KEYS[selectedComp[0]]; boolean hasChecked = false;
            if(editKey != null && preGes != -1) prefs.edit().putString(editKey, "NONE").apply(); 
            for(int i=0; i<gestureBoxes.size(); i++) {
                if(gestureBoxes.get(i).isChecked()) {
                    hasChecked = true; String finalKey = prefix + compKey + "_" + C_GESTURES[i];
                    prefs.edit().putString(finalKey, joinedActions).putBoolean(finalKey+"_vib", cbVib.isChecked()).putBoolean(finalKey+"_anim", cbAnim.isChecked()).apply();
                }
            }
            if(!hasChecked) { Toast.makeText(this, T("Select at least 1 Trigger!", "Hãy chọn ít nhất 1 Cử chỉ!"), Toast.LENGTH_SHORT).show(); return; }
            renderRulesList(); dialog.dismiss();
        });
        return root;
    }

    // ==================== KHÔNG GIAN HỆ SINH THÁI (ECOSYSTEM) ====================
    private void buildEcosystemSpace() {
        LinearLayout ecoNav = new LinearLayout(this); ecoNav.setOrientation(LinearLayout.HORIZONTAL); ecoNav.setPadding(0, 0, 0, 40);
        Button btnIntents = new Button(this); btnIntents.setText("INTENTS"); btnIntents.setBackground(getRounded("#D32F2F", 40f)); btnIntents.setTextColor(Color.WHITE);
        Button btnTiles = new Button(this); btnTiles.setText("QS TILES"); btnTiles.setBackground(getRounded("#4CAF50", 40f)); btnTiles.setTextColor(Color.WHITE);
        Button btnMacros = new Button(this); btnMacros.setText("MACROS"); btnMacros.setBackground(getRounded("#2196F3", 40f)); btnMacros.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, -2, 1f); btnLp.setMargins(10,0,10,0);
        btnIntents.setLayoutParams(btnLp); btnTiles.setLayoutParams(btnLp); btnMacros.setLayoutParams(btnLp);
        ecoNav.addView(btnIntents); ecoNav.addView(btnTiles); ecoNav.addView(btnMacros);
        pageEcosystem.addView(ecoNav);
        ecoContainer = new LinearLayout(this); ecoContainer.setOrientation(LinearLayout.VERTICAL);
        pageEcosystem.addView(ecoContainer);

        btnIntents.setOnClickListener(v -> { ecoType=0; renderEcosystem(); });
        btnTiles.setOnClickListener(v -> { ecoType=1; renderEcosystem(); });
        btnMacros.setOnClickListener(v -> { ecoType=2; renderEcosystem(); });
        renderEcosystem();
    }

    private void renderEcosystem() {
        ecoContainer.removeAllViews();
        if(ecoType == 0) {
            for (int i = 1; i <= 15; i++) {
                String name = prefs.getString("intent_"+i+"_name", "Intent "+i);
                String action = prefs.getString("i"+i+"_act", "");
                final int idx = i;
                LinearLayout card = createEcoCard(name, action, () -> openIntentEditor(idx));
                ecoContainer.addView(card);
            }
            Button btnAdd = new Button(this); btnAdd.setText("+ THÊM INTENT"); btnAdd.setBackground(getRounded("#333333", 20f)); btnAdd.setTextColor(Color.WHITE); btnAdd.setOnClickListener(v -> openIntentEditor(0));
            ecoContainer.addView(btnAdd);
        } else if(ecoType == 1) {
            for (int i = 1; i <= 15; i++) {
                String action = prefs.getString("tile_"+i+"_act", "NONE");
                String name = getActionLabel(action);
                final int idx = i;
                LinearLayout card = createEcoCard("Tile "+i, name, () -> openTileEditor(idx));
                ecoContainer.addView(card);
            }
            Button btnAdd = new Button(this); btnAdd.setText("+ THÊM QS TILE"); btnAdd.setBackground(getRounded("#333333", 20f)); btnAdd.setTextColor(Color.WHITE); btnAdd.setOnClickListener(v -> openTileEditor(0));
            ecoContainer.addView(btnAdd);
        } else {
            for (int i = 1; i <= 5; i++) {
                String name = prefs.getString("macro_"+i+"_name", "Macro "+i);
                String svcs = prefs.getString("macro_"+i+"_svcs", "");
                final int idx = i;
                LinearLayout card = createEcoCard(name, svcs, () -> openMacroEditor(idx));
                ecoContainer.addView(card);
            }
            Button btnAdd = new Button(this); btnAdd.setText("+ THÊM MACRO"); btnAdd.setBackground(getRounded("#333333", 20f)); btnAdd.setTextColor(Color.WHITE); btnAdd.setOnClickListener(v -> openMacroEditor(0));
            ecoContainer.addView(btnAdd);
        }
    }

    private String getActionLabel(String actionKey) {
        for (int i=0; i<ACT_KEYS.length; i++) {
            if (ACT_KEYS[i].equals(actionKey)) return ACT_LABS[i];
        }
        return actionKey;
    }

    private LinearLayout createEcoCard(String title, String subtitle, Runnable onEdit) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);
        TextView tvTitle = new TextView(this); tvTitle.setText(title); tvTitle.setTextColor(Color.WHITE); tvTitle.setTextSize(16);
        TextView tvSub = new TextView(this); tvSub.setText(subtitle); tvSub.setTextColor(Color.parseColor("#BBBBBB")); tvSub.setTextSize(12);
        card.addView(tvTitle); card.addView(tvSub);
        card.setOnClickListener(v -> onEdit.run());
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Xóa?").setPositiveButton("XÓA", (d,w) -> {
                if(ecoType==0) {
                    int num = Integer.parseInt(title.split(" ")[1]);
                    prefs.edit().putString("i"+num+"_act","").putString("intent_"+num+"_name","").apply();
                } else if(ecoType==1) {
                    int num = Integer.parseInt(title.split(" ")[1]);
                    prefs.edit().putString("tile_"+num+"_act","NONE").apply();
                } else {
                    int num = Integer.parseInt(title.split(" ")[1]);
                    prefs.edit().putString("macro_"+num+"_name","").putString("macro_"+num+"_svcs","").apply();
                }
                renderEcosystem();
            }).setNegativeButton("HỦY",null).show();
            return true;
        });
        return card;
    }

    private void openIntentEditor(int idx) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
        final int finalIdx = idx;
        EditText etName = createEcoInput("Tên gợi nhớ", idx>0 ? prefs.getString("intent_"+idx+"_name","") : "");
        EditText etAct = createEcoInput("Action", idx>0 ? prefs.getString("i"+idx+"_act","") : "");
        EditText etPkg = createEcoInput("Package", idx>0 ? prefs.getString("i"+idx+"_pkg","") : "");
        EditText etCls = createEcoInput("Class Name", idx>0 ? prefs.getString("i"+idx+"_cls","") : "");
        EditText etData = createEcoInput("Data URI", idx>0 ? prefs.getString("i"+idx+"_data","") : "");
        EditText etCat = createEcoInput("Categories", idx>0 ? prefs.getString("i"+idx+"_cat","") : "");
        EditText etFlags = createEcoInput("Flags", idx>0 ? prefs.getString("i"+idx+"_flags","") : "");
        CheckBox cbBr = new CheckBox(this); cbBr.setText("Send as Broadcast"); cbBr.setTextColor(Color.WHITE); cbBr.setChecked(idx<=0 || prefs.getBoolean("i"+idx+"_br",true));
        content.addView(etName); content.addView(etAct); content.addView(etPkg); content.addView(etCls); content.addView(etData); content.addView(etCat); content.addView(etFlags); content.addView(cbBr);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
        Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            if(finalIdx==0) {
                int newIdx = -1;
                for(int i=1;i<=15;i++) if(prefs.getString("i"+i+"_act","").isEmpty()) { newIdx=i; break; }
                if(newIdx==-1) { Toast.makeText(this,"Đã đủ 15 Intent!",Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("intent_"+newIdx+"_name", etName.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_act", etAct.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_pkg", etPkg.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_cls", etCls.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_data", etData.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_cat", etCat.getText().toString()).apply();
                prefs.edit().putString("i"+newIdx+"_flags", etFlags.getText().toString()).apply();
                prefs.edit().putBoolean("i"+newIdx+"_br", cbBr.isChecked()).apply();
            } else {
                prefs.edit().putString("intent_"+finalIdx+"_name", etName.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_act", etAct.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_pkg", etPkg.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_cls", etCls.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_data", etData.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_cat", etCat.getText().toString()).apply();
                prefs.edit().putString("i"+finalIdx+"_flags", etFlags.getText().toString()).apply();
                prefs.edit().putBoolean("i"+finalIdx+"_br", cbBr.isChecked()).apply();
            }
            renderEcosystem(); d.dismiss();
        });
        d.setContentView(root); d.show();
    }

    private void openTileEditor(int idx) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
        final int finalIdx = idx;
        Spinner sp = createSpinner();
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS));
        if(idx>0) {
            String cur = prefs.getString("tile_"+idx+"_act","NONE");
            for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(cur)) sp.setSelection(i);
        }
        content.addView(sp);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
        Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            if(finalIdx==0) {
                int newIdx = -1;
                for(int i=1;i<=15;i++) if(prefs.getString("tile_"+i+"_act","NONE").equals("NONE")) { newIdx=i; break; }
                if(newIdx==-1) { Toast.makeText(this,"Đã đủ 15 QS Tile!",Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("tile_"+newIdx+"_act", ACT_KEYS[sp.getSelectedItemPosition()]).apply();
            } else {
                prefs.edit().putString("tile_"+finalIdx+"_act", ACT_KEYS[sp.getSelectedItemPosition()]).apply();
            }
            renderEcosystem(); d.dismiss();
        });
        d.setContentView(root); d.show();
    }

    private void openMacroEditor(int idx) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
        final int finalIdx = idx;
        EditText etName = createEcoInput("Tên gợi nhớ", idx>0 ? prefs.getString("macro_"+idx+"_name","") : "");
        EditText etSvcs = createEcoInput("Services (com.pkg/.Class)", idx>0 ? prefs.getString("macro_"+idx+"_svcs","") : "");
        content.addView(etName); content.addView(etSvcs);
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
        Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            if(finalIdx==0) {
                int newIdx = -1;
                for(int i=1;i<=5;i++) if(prefs.getString("macro_"+i+"_name","").isEmpty()) { newIdx=i; break; }
                if(newIdx==-1) { Toast.makeText(this,"Đã đủ 5 Macro!",Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString("macro_"+newIdx+"_name", etName.getText().toString()).apply();
                prefs.edit().putString("macro_"+newIdx+"_svcs", etSvcs.getText().toString()).apply();
            } else {
                prefs.edit().putString("macro_"+finalIdx+"_name", etName.getText().toString()).apply();
                prefs.edit().putString("macro_"+finalIdx+"_svcs", etSvcs.getText().toString()).apply();
            }
            renderEcosystem(); d.dismiss();
        });
        d.setContentView(root); d.show();
    }

    private EditText createEcoInput(String hint, String value) {
        EditText et = new EditText(this); et.setHint(hint); et.setText(value); et.setTextColor(Color.WHITE); et.setHintTextColor(Color.GRAY);
        et.setBackground(getRounded("#2C2C2C",20f)); et.setPadding(30,30,30,30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp);
        return et;
    }

    // ==================== KHÔNG GIAN THIẾT KẾ ====================
    private void buildDesignSpace() {
        pageDesign.addView(createSectionTitle(T("BACKUP / RESTORE", "KHU VỰC SAO LƯU")));
        LinearLayout backupRow = new LinearLayout(this); backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnBackup = new Button(this); btnBackup.setText(T("BACKUP", "💾 SAO LƯU")); btnBackup.setBackground(getRounded("#2E7D32", 20f)); btnBackup.setTextColor(Color.WHITE); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); bp.setMargins(0,0,15,0); btnBackup.setLayoutParams(bp); Button btnRestore = new Button(this); btnRestore.setText(T("RESTORE", "📂 PHỤC HỒI")); btnRestore.setBackground(getRounded("#EF6C00", 20f)); btnRestore.setTextColor(Color.WHITE); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); btnRestore.setLayoutParams(rp);
        btnBackup.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup.txt"); startActivityForResult(i, 101); }); btnRestore.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, 102); }); backupRow.addView(btnBackup); backupRow.addView(btnRestore); pageDesign.addView(wrapCard(backupRow));
        
        LinearLayout secSys = new LinearLayout(this); secSys.setOrientation(LinearLayout.VERTICAL); secSys.addView(createSectionTitle(T("SYSTEM BEHAVIOR", "HÀNH VI HỆ THỐNG"))); CheckBox cbKbd = new CheckBox(this); cbKbd.setText(T("Auto-hide on Keyboard", "Tự ẩn khi hiện Bàn Phím")); cbKbd.setTextColor(Color.WHITE); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true)); cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply()); secSys.addView(cbKbd); 
        secSys.addView(createSectionTitle("BLACKLIST (Hide Overlay)")); secSys.addView(createInput("Packages (com.ex.app)", "blacklist")); 
        secSys.addView(createSectionTitle("LOCKLIST (Morse AppLock)")); 
        LinearLayout lockRow = new LinearLayout(this); lockRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText etLock = createInput("Packages (com.zing.zalo,...)", "locklist"); etLock.setTag("locklist_input"); etLock.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnPickApps = new Button(this); btnPickApps.setText("📱 PICK APP"); btnPickApps.setTextColor(Color.BLACK); btnPickApps.setBackground(getRounded("#00E5FF", 20f));
        btnPickApps.setOnClickListener(v -> showAppPickerDialog());
        lockRow.addView(etLock); lockRow.addView(btnPickApps);
        secSys.addView(lockRow);
        pageDesign.addView(wrapCard(secSys));
        addYTDLDesign(pageDesign);
        
        pageDesign.addView(createSectionTitle(T("CORE DESIGN (COLOR/SIZE)", "THIẾT KẾ CỐT LÕI (MÀU/KÍCH THƯỚC)")));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
        btnEditHome = new Button(this); btnEditHome.setText("HOME"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP); 
        btnEditMorse = new Button(this); btnEditMorse.setText("MORSE OS"); btnEditMorse.setLayoutParams(mP); 
        btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0);
        
        btnEditLock.setOnClickListener(v -> { designTabState=0; refreshPreview(); updateVisTabs(); renderSliders(); }); 
        btnEditHome.setOnClickListener(v -> { designTabState=1; refreshPreview(); updateVisTabs(); renderSliders(); }); 
        btnEditMorse.setOnClickListener(v -> { designTabState=2; refreshPreview(); updateVisTabs(); renderSliders(); }); 
        btnEditAnim.setOnClickListener(v -> { designTabState=3; refreshPreview(); updateVisTabs(); renderSliders(); });
        toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditMorse); toggleRow.addView(btnEditAnim);
        pageDesign.addView(toggleRow); pageDesign.addView(designSliderContainer);
        btnEditHome.performClick();
    }

    private void updateVisTabs() {
        btnEditLock.setBackground(getRounded(designTabState==0 ? "#00E5FF" : "#222222", 20f));
        btnEditLock.setTextColor(designTabState==0 ? Color.BLACK : Color.WHITE);
        btnEditHome.setBackground(getRounded(designTabState==1 ? "#00E5FF" : "#222222", 20f));
        btnEditHome.setTextColor(designTabState==1 ? Color.BLACK : Color.WHITE);
        btnEditMorse.setBackground(getRounded(designTabState==2 ? "#00E5FF" : "#222222", 20f));
        btnEditMorse.setTextColor(designTabState==2 ? Color.BLACK : Color.WHITE);
        btnEditAnim.setBackground(getRounded(designTabState==3 ? "#00E5FF" : "#222222", 20f));
        btnEditAnim.setTextColor(designTabState==3 ? Color.BLACK : Color.WHITE);
    }

    private void renderSliders() { 
        designSliderContainer.removeAllViews(); 
        if(designTabState == 3) { 
            Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG"); btnTest.setBackground(getRounded("#FFC107", 20f)); btnTest.setTextColor(Color.BLACK); btnTest.setPadding(0,30,0,30); LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2); testLp.setMargins(0,0,0,20); btnTest.setLayoutParams(testLp); btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); i.setPackage(getPackageName()); sendBroadcast(i); Toast.makeText(this, "Playing Animation...", Toast.LENGTH_SHORT).show(); }); designSliderContainer.addView(btnTest);
            LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText("Chủ đề:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); designSliderContainer.addView(lC); 
            LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText("Kiểu chạy:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Nhấp Nháy", "1 Tia sáng nối đuôi", "2 Tia sáng đối xứng", "3 Tia sáng đều nhau"})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); designSliderContainer.addView(lS); 
            designSliderContainer.addView(createSlider("Chiều ngang Hiệu ứng (0=Full)", "anim_w", 2000, 0)); designSliderContainer.addView(createSlider("Chiều dọc Hiệu ứng (0=Full)", "anim_h", 3500, 0)); designSliderContainer.addView(createSlider("Độ đậm mờ hiệu ứng (Alpha)", "anim_alpha", 255, 255)); designSliderContainer.addView(createSlider("Độ dày viền", "anim_thick", 50, 12)); designSliderContainer.addView(createSlider("Thời gian Animation (ms)", "anim_dur", 5000, 1500)); designSliderContainer.addView(createSlider("Thời gian Vuốt+Giữ (All)", "hold_dur", 2000, 600)); designSliderContainer.addView(createSlider("Độ rung (ms) (All)", "vib_dur", 100, 30)); 
        } else { 
            String prefix = designTabState == 0 ? "lock_" : (designTabState == 1 ? "home_" : "morse_"); 
            String[] bKeys = designTabState == 2 ? M_BARS : BARS;
            String[] bNames = designTabState == 2 ? M_BAR_NAMES : BAR_NAMES;
            if(designTabState == 2) {
                LinearLayout mRow = new LinearLayout(this); mRow.setOrientation(LinearLayout.HORIZONTAL);
                Button btnTestM = new Button(this); btnTestM.setText("👁️ THỬ MORSE OS"); btnTestM.setBackground(getRounded("#FFC107", 20f)); btnTestM.setTextColor(Color.BLACK); LinearLayout.LayoutParams tm = new LinearLayout.LayoutParams(0,-2,1f); tm.setMargins(0,0,10,20); btnTestM.setLayoutParams(tm);
                btnTestM.setOnClickListener(v->{ 
                    boolean cur = prefs.getBoolean("morse_mode_en", false);
                    prefs.edit().putBoolean("morse_mode_en", !cur).apply();
                    Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE");
                    sendBroadcast(i);
                    Toast.makeText(this, !cur ? "Bật lớp phủ Morse" : "Tắt lớp phủ Morse", Toast.LENGTH_SHORT).show();
                });
                Button btnMap = new Button(this); btnMap.setText("🔢 MAP KEYS"); btnMap.setBackground(getRounded("#E91E63", 20f)); btnMap.setTextColor(Color.WHITE); LinearLayout.LayoutParams mk = new LinearLayout.LayoutParams(0,-2,1f); mk.setMargins(10,0,0,20); btnMap.setLayoutParams(mk);
                btnMap.setOnClickListener(v -> openMorseMapDialog());
                mRow.addView(btnTestM); mRow.addView(btnMap); designSliderContainer.addView(mRow);
                LinearLayout passRow = new LinearLayout(this); passRow.setOrientation(LinearLayout.HORIZONTAL); passRow.setPadding(0,20,0,10);
                EditText etMasterPass = new EditText(this); etMasterPass.setHint("Mật khẩu Master"); etMasterPass.setText(prefs.getString("morse_master_pass", ""));
                etMasterPass.setBackground(getRounded("#2C2C2C", 20f)); etMasterPass.setPadding(30,30,30,30); etMasterPass.setTextColor(Color.WHITE);
                etMasterPass.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                Button btnFloatingSave = new Button(this); btnFloatingSave.setText("🔒"); btnFloatingSave.setBackground(getRounded("#00E5FF", 100f)); btnFloatingSave.setTextColor(Color.BLACK);
                btnFloatingSave.setLayoutParams(new LinearLayout.LayoutParams(100, -2));
                final Handler longPressHandler = new Handler();
                final Runnable longPressRunnable = new Runnable() {
                    @Override
                    public void run() {
                        btnFloatingSave.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        showChangePasswordDialog();
                    }
                };
                btnFloatingSave.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        longPressHandler.postDelayed(longPressRunnable, 30000);
                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    return false;
                });
                btnFloatingSave.setOnClickListener(v -> {
                    String newPass = etMasterPass.getText().toString();
                    if (!newPass.isEmpty()) {
                        prefs.edit().putString("morse_master_pass", newPass).apply();
                        Toast.makeText(this, "Đã lưu mật khẩu vào kho bảo mật!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Mật khẩu không được để trống!", Toast.LENGTH_SHORT).show();
                    }
                });
                passRow.addView(etMasterPass); passRow.addView(btnFloatingSave);
                designSliderContainer.addView(passRow);

                LinearLayout sliderDrawerContent = new LinearLayout(this);
                sliderDrawerContent.setOrientation(LinearLayout.VERTICAL);
                CheckBox cbVibEn = new CheckBox(this);
                cbVibEn.setText("Bật rung bàn phím Morse");
                cbVibEn.setTextColor(Color.WHITE);
                cbVibEn.setChecked(prefs.getBoolean("morse_vib_en", true));
                cbVibEn.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("morse_vib_en", c).apply());
                sliderDrawerContent.addView(cbVibEn);
                sliderDrawerContent.addView(createSlider("Độ rung khi gõ (ms)", "morse_vib_dur", 200, 30));
                sliderDrawerContent.addView(createComboDropdown("Nền lớp phủ", "morse_bg_type", new String[]{"Hiệu ứng Glitch", "Ảnh tùy chọn"}, prefs.getInt("morse_bg_type", 0)));
                Button btnPickBg = new Button(this);
                btnPickBg.setText("📁 CHỌN ẢNH NỀN");
                btnPickBg.setBackground(getRounded("#2196F3", 20f));
                btnPickBg.setTextColor(Color.WHITE);
                btnPickBg.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(intent, 103);
                });
                sliderDrawerContent.addView(btnPickBg);
                sliderDrawerContent.addView(createInput("Text nhập sai lần 1", "morse_insult_1"));
                sliderDrawerContent.addView(createInput("Text nhập sai lần 2", "morse_insult_2"));
                sliderDrawerContent.addView(createInput("Text nhập sai lần 3", "morse_insult_3"));
                sliderDrawerContent.addView(createInput("Text nhập sai lần 4", "morse_insult_4"));
                sliderDrawerContent.addView(createInput("Text nhập sai lần 5", "morse_insult_5"));
                sliderDrawerContent.addView(createSlider("Độ dài tối đa mật khẩu", "morse_max_len", 20, 10));
                sliderDrawerContent.addView(createSlider("Thời gian khóa sau 5 lần sai (phút)", "morse_lock_minutes", 60, 30));
                sliderDrawerContent.addView(createSlider("Thời gian khoá sau 3 lần sai (giây)", "morse_lock3_seconds", 1800, 10));
                sliderDrawerContent.addView(createSlider("Thời gian khoá sau 4 lần sai (giây)", "morse_lock4_seconds", 1800, 30));


LinearLayout relockRow = new LinearLayout(this);
relockRow.setOrientation(LinearLayout.VERTICAL);
relockRow.setPadding(0, 10, 0, 10);

int curRelockMs = prefs.getInt("morse_relock_ms", 5000);
String relockLabel = formatRelockTime(curRelockMs);

TextView tvRelock = new TextView(this);
tvRelock.setTextColor(Color.WHITE);
tvRelock.setText("Relock sau khi thoát app: " + relockLabel);
relockRow.addView(tvRelock);

LinearLayout relockBtnRow = new LinearLayout(this);
relockBtnRow.setOrientation(LinearLayout.HORIZONTAL);
relockBtnRow.setGravity(Gravity.CENTER_VERTICAL);

Button btnRelockM = new Button(this); btnRelockM.setText("-");
btnRelockM.setTextColor(Color.parseColor("#BBBBBB"));
btnRelockM.setBackgroundColor(Color.TRANSPARENT); btnRelockM.setTextSize(20);

Button btnRelockP = new Button(this); btnRelockP.setText("+");
btnRelockP.setTextColor(Color.parseColor("#BBBBBB"));
btnRelockP.setBackgroundColor(Color.TRANSPARENT); btnRelockP.setTextSize(20);

SeekBar sbRelock = new SeekBar(this);
sbRelock.setMax(1800);
sbRelock.setProgress(curRelockMs / 1000);
sbRelock.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
sbRelock.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
    public void onProgressChanged(SeekBar s, int p, boolean b) {
        int ms = Math.max(1000, p * 1000); // tối thiểu 1 giây
        prefs.edit().putInt("morse_relock_ms", ms).apply();
        tvRelock.setText("Relock sau khi thoát app: " + formatRelockTime(ms));
    }
    public void onStartTrackingTouch(SeekBar s) {}
    public void onStopTrackingTouch(SeekBar s) {}
});
btnRelockM.setOnClickListener(v -> { if(sbRelock.getProgress()>1) sbRelock.setProgress(sbRelock.getProgress()-1); });
btnRelockP.setOnClickListener(v -> { if(sbRelock.getProgress()<1800) sbRelock.setProgress(sbRelock.getProgress()+1); });

relockBtnRow.addView(btnRelockM); relockBtnRow.addView(sbRelock); relockBtnRow.addView(btnRelockP);
relockRow.addView(relockBtnRow);
designSliderContainer.addView(relockRow);

                sliderDrawerContent.addView(createSlider("Độ mờ màn chắn Morse (Alpha Đen)", "morse_bg_alpha", 255, 180));
                sliderDrawerContent.addView(createSlider("Vị trí dọc Icon Ổ Khoá (0=Trên, 3000=Dưới)", "morse_lock_icon_y", 3000, 600));
                sliderDrawerContent.addView(createSlider("Kích thước cấu hình Icon Ổ Khoá", "morse_lock_icon_size", 150, 48));
                sliderDrawerContent.addView(createSlider("Độ nét Neon (Blur) của text/mật khẩu", "morse_text_blur", 60, 20));
                sliderDrawerContent.addView(createSlider("Cỡ chữ mật khẩu (sp)", "morse_text_size", 60, 30));
                CheckBox cbNeon = new CheckBox(this);
                cbNeon.setText("Bật hiệu ứng Neon cho text nhập mật khẩu");
                cbNeon.setTextColor(Color.WHITE);
                cbNeon.setChecked(prefs.getBoolean("morse_text_neon", true));
                cbNeon.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("morse_text_neon", c).apply());
                sliderDrawerContent.addView(cbNeon);

                sliderDrawerContent.addView(createSlider("Thời gian hiện dấu chấm (ms)", "morse_dot_delay", 2000, 500));
                sliderDrawerContent.addView(createSlider("Thời gian hiện số (ms) trước khi thành dấu chấm", "morse_show_number_ms", 3000, 800));
                sliderDrawerContent.addView(createSlider("Độ rung khi nhập sai (ms)", "morse_fail_vib", 1500, 500));
                designSliderContainer.addView(createDrawer("CÀI ĐẶT MORSE NÂNG CAO", sliderDrawerContent));
            }
            designSliderContainer.addView(createSectionTitle("⚙️ MORSE OS - LỚP PHỦ TUỲ CHỈNH")); 
            designSliderContainer.addView(createSectionTitle("EDGE BARS (" + bKeys.length + " THANH)"));
            for(int i=0; i < bKeys.length; i++) { 
                LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); 
                CheckBox cb = new CheckBox(this); cb.setText("BẬT: " + bNames[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+bKeys[i]+"_en", false)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+bKeys[idx]+"_en", c).apply()); drawerContent.addView(cb); 
                if (designTabState == 2) {
                    drawerContent.addView(createComboDropdown("Hiển thị", prefix+bKeys[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0));
                }
                drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+bKeys[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
                drawerContent.addView(createSlider("Độ trong suốt", prefix+bKeys[i]+"_alpha", 255, 50));
                drawerContent.addView(createSlider("Chiều ngang", prefix+bKeys[i]+"_w", 3000, 300));
                drawerContent.addView(createSlider("Chiều dọc", prefix+bKeys[i]+"_h", 3000, 60));
                drawerContent.addView(createSlider("Toạ độ X", prefix+bKeys[i]+"_x", 1000, 0));
                drawerContent.addView(createSlider("Toạ độ Y", prefix+bKeys[i]+"_y", 2500, 0));
                designSliderContainer.addView(createDrawer(bNames[i], drawerContent));
            } 
            designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS"));
            for(int i=0; i<4; i++) { 
                LinearLayout drawerContent = new LinearLayout(this); drawerContent.setOrientation(LinearLayout.VERTICAL); drawerContent.setPadding(30,10,30,30); 
                CheckBox cbEn = new CheckBox(this); cbEn.setText(T("ENABLE: ", "BẬT: ") + CORNER_NAMES[i]); cbEn.setTextColor(Color.parseColor("#4CAF50")); cbEn.setChecked(prefs.getBoolean(prefix+"corner_"+CORNERS[i]+"_en", false)); final int idx = i; cbEn.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+"corner_"+CORNERS[idx]+"_en", c).apply()); drawerContent.addView(cbEn);
                drawerContent.addView(createComboDropdown("Hiển thị", prefix+"corner_"+CORNERS[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0));
                drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+"corner_"+CORNERS[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
                drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix+"corner_"+CORNERS[i]+"_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0));
                drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix+"corner_"+CORNERS[i]+"_w", 2500, 100));
                drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix+"corner_"+CORNERS[i]+"_h", 2500, 100));
                drawerContent.addView(createSlider("Di chuyển Ngang (X)", prefix+"corner_"+CORNERS[i]+"_x", 2500, 0));
                drawerContent.addView(createSlider("Di chuyển Dọc (Y)", prefix+"corner_"+CORNERS[i]+"_y", 2500, 0));
                drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix+"corner_"+CORNERS[i]+"_moon_w", 2500, 100));
                drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix+"corner_"+CORNERS[i]+"_moon_h", 2500, 100));
                drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_x", 2500, 1250));
                drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix+"corner_"+CORNERS[i]+"_moon_y", 2500, 1250));
                drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_rad", 1000, 80));
                drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix+"corner_"+CORNERS[i]+"_moon_rad", 1000, 80));
                designSliderContainer.addView(createDrawer(CORNER_NAMES[i], drawerContent));
            }
            LinearLayout globalDrawer = new LinearLayout(this); globalDrawer.setOrientation(LinearLayout.VERTICAL); globalDrawer.setPadding(30,10,30,30); globalDrawer.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500)); globalDrawer.addView(createSlider("Độ mờ vùng TRĂNG NON (Đậm/Nhạt)", prefix+"corner_moon_alpha", 255, 100)); globalDrawer.addView(createSlider("Độ mờ VIỀN GÓC (Đậm/Nhạt)", prefix+"corner_stroke_alpha", 255, 200)); globalDrawer.addView(createSlider("Độ đậm viền (Dày/Mỏng)", prefix+"corner_thick", 50, 8)); designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawer));
        } 
    }

    // ==================== CÁC HÀM PHỤ TRỢ CHUNG ====================
 private void showAppPickerDialog() {
        android.os.UserManager um =
            (android.os.UserManager) getSystemService(Context.USER_SERVICE);
        android.content.pm.LauncherApps la =
            (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);

        final List<String> allPkgs = new ArrayList<>();
        final List<String> allNames = new ArrayList<>();

        try {
            List<android.os.UserHandle> profiles = um.getUserProfiles();
            for (android.os.UserHandle profile : profiles) {
                List<android.content.pm.LauncherActivityInfo> activities =
                    la.getActivityList(null, profile);
                for (android.content.pm.LauncherActivityInfo info : activities) {
                    String pkg = info.getApplicationInfo().packageName;
                    String name = info.getLabel().toString();
                    // Gắn tag [Island] nếu không phải profile chính
                    if (!profile.equals(android.os.Process.myUserHandle())) {
                        name += " [Island]";
                    }
                    if (!allPkgs.contains(pkg)) {
                        allPkgs.add(pkg);
                        allNames.add(name);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: dùng queryIntentActivities nếu LauncherApps lỗi
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo app : apps) {
                String pkg = app.activityInfo.packageName;
                if (!allPkgs.contains(pkg)) {
                    allPkgs.add(pkg);
                    allNames.add(app.loadLabel(getPackageManager()).toString());
                }
            }
        }

        // Sort theo tên (đồng bộ cả 2 list)
        List<String[]> combined = new ArrayList<>();
        for (int i = 0; i < allPkgs.size(); i++)
            combined.add(new String[]{allNames.get(i), allPkgs.get(i)});
        combined.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        allNames.clear(); allPkgs.clear();
        for (String[] pair : combined) { allNames.add(pair[0]); allPkgs.add(pair[1]); }

        String currentLocklist = prefs.getString("locklist", "");
        final boolean[] checked = new boolean[allPkgs.size()];
        for (int i = 0; i < allPkgs.size(); i++)
            checked[i] = currentLocklist.contains(allPkgs.get(i));

        new AlertDialog.Builder(this)
            .setTitle(T("Choose App to Lock", "Chọn ứng dụng cần khóa"))
            .setMultiChoiceItems(
                allNames.toArray(new String[0]), checked,
                (dialog, which, isChecked) -> checked[which] = isChecked)
            .setPositiveButton("LƯU", (d, w) -> {
                List<String> selected = new ArrayList<>();
                for (int i = 0; i < allPkgs.size(); i++)
                    if (checked[i]) selected.add(allPkgs.get(i));
                String newLocklist = TextUtils.join(",", selected);
                prefs.edit().putString("locklist", newLocklist).apply();
                // Cập nhật EditText nếu đang hiển thị
                try {
                    ViewGroup rootView = (ViewGroup)
                        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
                    EditText et = rootView.findViewWithTag("locklist_input");
                    if (et != null) et.setText(newLocklist);
                } catch (Exception ignored) {}
            })
            .setNegativeButton("HỦY", null)
            .show();
    }


    private void openMorseMapDialog() {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40, 120, 40, 40);
        TextView title = new TextView(this); title.setText("🔢 MORSE NUMPAD MAPPING"); title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(20); title.setPadding(0,0,0,40); root.addView(title);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
        String[] mKeys = {"t_l", "m_mid_t", "t_r", "l", "t_c", "r", "corner_tl", "corner_tr", "corner_bl", "corner_br", "m_b_c", "m_mid_b"};
        String[] mNames = {"Cạnh Trái", "Trung Tâm Trên", "Cạnh Phải", "Đáy Trái", "Đỉnh Giữa", "Đáy Phải", "Góc Đỉnh Trái", "Góc Đỉnh Phải", "Góc Đáy Trái", "Góc Đáy Phải", "Đáy Giữa", "Trung Tâm Dưới"};
        String[] mapOptions = {"* (Bỏ qua)", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "X (Xóa)", "> (Mở khóa)"};
        ArrayList<Spinner> spinners = new ArrayList<>();
        for(int i=0; i<mKeys.length; i++) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,10,0,10);
            TextView tv = new TextView(this); tv.setText(mNames[i]); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            Spinner sp = createSpinner(); sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mapOptions));
            String cur = prefs.getString("morse_map_" + mKeys[i], "*");
            int sel = 0; for(int j=0; j<mapOptions.length; j++) if(mapOptions[j].startsWith(cur)) sel = j;
            sp.setSelection(sel);
            sp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f));
            row.addView(tv); row.addView(sp); content.addView(row); spinners.add(sp);
        }
        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
        Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY")); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText(T("SAVE", "LƯU")); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            for(int i=0; i<mKeys.length; i++) {
                String val = mapOptions[spinners.get(i).getSelectedItemPosition()].substring(0,1);
                prefs.edit().putString("morse_map_" + mKeys[i], val).apply();
            }
            Toast.makeText(this, "Đã lưu ma trận bàn phím Morse!", Toast.LENGTH_SHORT).show();
            d.dismiss();
        });
        d.setContentView(root); d.show();
    }

    private void addYTDLDesign(LinearLayout parent) {
        LinearLayout ytdlDrawer = new LinearLayout(this); ytdlDrawer.setOrientation(LinearLayout.VERTICAL);
        ytdlDrawer.setPadding(30, 20, 30, 20); ytdlDrawer.setBackground(getRounded("#222222", 20f));
        TextView title = new TextView(this); title.setText("🎵 YTDLnis - TẢI NHẠC/VIDEO");
        title.setTextColor(Color.parseColor("#FFD700")); title.setPadding(0, 0, 0, 20);
        ytdlDrawer.addView(title);
        EditText etLink = new EditText(this); etLink.setHint("Paste link / tên bài hát"); etLink.setText(prefs.getString("ytdl_last_link", ""));
        etLink.setBackground(getRounded("#2C2C2C", 20f)); etLink.setPadding(30, 30, 30, 30); etLink.setTextColor(Color.WHITE);
        ytdlDrawer.addView(etLink);
        LinearLayout btnRow = new LinearLayout(this); btnRow.setOrientation(LinearLayout.HORIZONTAL); btnRow.setPadding(0, 20, 0, 0);
        Button btnSave = new Button(this); btnSave.setText("💾 LƯU LINK"); btnSave.setBackground(getRounded("#4CAF50", 20f)); btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> { prefs.edit().putString("ytdl_last_link", etLink.getText().toString()).apply(); Toast.makeText(this, "Đã lưu link!", Toast.LENGTH_SHORT).show(); });
        Button btnDownload = new Button(this); btnDownload.setText("📥 TẢI NGAY"); btnDownload.setBackground(getRounded("#00E5FF", 20f)); btnDownload.setTextColor(Color.BLACK);
        btnDownload.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        btnDownload.setOnClickListener(v -> {
            String link = etLink.getText().toString().trim();
            if (!link.isEmpty()) {
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                sendIntent.putExtra(Intent.EXTRA_TEXT, link);
                sendIntent.setPackage("com.deniscerri.ytdl");
                sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(sendIntent);
            } else {
                Toast.makeText(this, "Hãy nhập link hoặc tên bài hát!", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(btnSave); btnRow.addView(btnDownload);
        ytdlDrawer.addView(btnRow);
        parent.addView(createDrawer("YTDL DOWNLOADER", ytdlDrawer));
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Đổi mật khẩu Morse");
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(40,40,40,40);
        EditText etNew = new EditText(this); etNew.setHint("Mật khẩu mới"); etNew.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etNew);
        builder.setView(layout);
        builder.setPositiveButton("Lưu", (d,w) -> {
            String newPass = etNew.getText().toString().trim();
            if (!newPass.isEmpty()) {
                prefs.edit().putString("morse_master_pass", newPass).apply();
                Toast.makeText(this, "Đã đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Mật khẩu không hợp lệ!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void showPremiumDialog() { 
        String t = T("ADB COMMANDS:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow\n\n🚀 TĂNG TỐC BẰNG ADB (chạy 1 lần):\nadb shell settings put global window_animation_scale 0\nadb shell settings put global transition_animation_scale 0\nadb shell settings put global animator_duration_scale 0", 
        "🔧 LỆNH ADB CỐT LÕI (Cấp 1 lần):\n\n1. Quyền ghi Cài đặt bảo mật:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\n\n2. Quyền vẽ Lớp phủ (Tàng hình AppOps):\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow\n\n🚀 TĂNG TỐC BẰNG ADB (chạy 1 lần):\nadb shell settings put global window_animation_scale 0\nadb shell settings put global transition_animation_scale 0\nadb shell settings put global animator_duration_scale 0"); 
        ScrollView sv = new ScrollView(this); sv.setPadding(50,50,50,50); TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setTextSize(15f); tv.setLineSpacing(0, 1.3f); sv.addView(tv); 
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("👑 PREMIUM ARCHITECT INFO").setView(sv).setPositiveButton("OK", null).show(); 
    }

    private LinearLayout createDrawer(String title, View content) { 
        LinearLayout container = new LinearLayout(this); container.setOrientation(LinearLayout.VERTICAL); container.setBackground(getRounded("#222222", 20f)); 
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,20); container.setLayoutParams(clp); 
        TextView header = new TextView(this); header.setText(title); header.setTextColor(Color.parseColor("#00E5FF")); header.setPadding(30,30,30,30); header.setTextSize(16); 
        content.setVisibility(View.GONE); 
        header.setOnClickListener(v -> { boolean isClosed = content.getVisibility() == View.GONE; content.setVisibility(isClosed ? View.VISIBLE : View.GONE); header.setBackground(getRounded(isClosed ? "#333333" : "#222222", 20f)); }); 
        container.addView(header); container.addView(content); 
        return container; 
    }
    private LinearLayout createComboDropdown(String title, String key, String[] items, int def) { 
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0,10,0,20); 
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(Color.parseColor("#E91E63")); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
        Spinner sp = createSpinner(); sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); sp.setSelection(prefs.getInt(key, def)); 
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt(key,pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); 
        sp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.2f)); l.addView(tv); l.addView(sp); 
        return l; 
    }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }


    private String formatRelockTime(int ms) {
    if (ms < 60000) return (ms / 1000) + " giây";
    else if (ms < 3600000) return (ms / 60000) + " phút " + ((ms % 60000) / 1000) + "s";
    else return "30 phút";
}
    private LinearLayout createSlider(String t, String k, int max, int def) { 
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); 
        TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv); 
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); 
        Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); 
        Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); 
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); 
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); 
        btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); }); 
        row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); 
        return l; 
    }
}
