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
import android.os.Build;
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
import android.graphics.Bitmap;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    
    private String[] ACT_KEYS = new String[40]; private String[] ACT_LABS = new String[40];
    private String[] BARS = {"r", "l", "t_r", "t_l", "t_c"}; private String[] BAR_NAMES; 
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; private String[] CORNER_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; private String[] COLOR_NAMES;
private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "corner_br", "corner_bl", "corner_tr", "corner_tl", "fingerprint", "launch_app"};
private String[] ALL_COMP_NAMES;
private String[] VOLKEY_COMPS = {"up", "down"};
private String[] VOLKEY_COMP_NAMES;
private String[] VOLKEY_GESTURES = {"tap", "dtap", "long"};
private String[] VOLKEY_GESTURE_NAMES;
private String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
private String[] M_BAR_NAMES;
private String[] C_GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold", "diag", "diag_hold"};
private String[] C_GESTURE_NAMES;
private LinearLayout pageDesign, pageConditions, pageEcosystem, listRules, designSliderContainer, navMain;
private String[] PANEL_COLOR_KEYS = {"SLATE","STEEL","MIST","GRAPHITE","INDIGO_MIST","TEAL_GREY","COOL_ASH","DEEP_BLUE"};
private String[] PANEL_COLOR_HEX  = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
private String[] PANEL_COLOR_NAMES; // set trong reloadActionLabels()
private String[] PANEL_POS_NAMES;   // 9 vị trí, set trong reloadActionLabels()

private Button btnLock, btnHomacc, btnHome, btnVolKey, btnEditLock, btnEditHome, btnEditHomacc, btnEditMorse, btnEditAnim, btnEditPanel;
private int currentPanelIdx = 1; // 1-3, panel nào đang được chỉnh trong tab PANEL
private Button fab;
    private int designTabState = 0;
    private int currentMainTab = 1; private int currentGesTab = 0; 
    private final String CURRENT_VERSION = "V19.12.3.6.21"; 
    private RelativeLayout rootLayout;

    private int ecoType = 0;
    private LinearLayout ecoContainer;
    // THÊM 2 field static này ngay dưới khai báo ecoContainer:
private static List<String[]> cachedAppList = null; // mỗi phần tử: {name, pkg}
private static long cachedAppListTs = 0;
private static final long APP_LIST_CACHE_MS = 5 * 60 * 1000; // 5 phút
private static final java.util.Map<String,String> appLabelCache = new java.util.HashMap<>();

private String getAppLabelCached(String pkg) {
    if (pkg == null || pkg.isEmpty()) return T("(Not selected)", "(Chưa chọn)");
    String cached = appLabelCache.get(pkg);
    if (cached != null) return cached;
    try {
        String label = getPackageManager()
            .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
        appLabelCache.put(pkg, label);
        return label;
    } catch (Exception e) { return pkg; }
}

private List<String[]> getAppListCached() {
    long now = System.currentTimeMillis();
    if (cachedAppList != null && (now - cachedAppListTs) < APP_LIST_CACHE_MS) return cachedAppList;
    android.os.UserManager um = (android.os.UserManager) getSystemService(Context.USER_SERVICE);
    android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
    List<String[]> combined = new ArrayList<>();
    try {
        for (android.os.UserHandle profile : um.getUserProfiles()) {
            boolean island = !profile.equals(android.os.Process.myUserHandle());
            for (android.content.pm.LauncherActivityInfo info : la.getActivityList(null, profile)) {
                String pkg = info.getApplicationInfo().packageName;
                String name = info.getLabel().toString() + (island ? " [Island]" : "");
                combined.add(new String[]{name, pkg});
            }
        }
    } catch (Exception ignored) {}
    combined.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
    cachedAppList = combined; cachedAppListTs = now;
    return combined;
}
    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    
    private void refreshPreview() { 
    boolean pLock = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0) || (currentMainTab==1 && currentGesTab==0); 
    boolean pMorse = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==2);
    boolean pPanel = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==5);
    prefs.edit().putBoolean("preview_lock", pLock).putBoolean("preview_morse", pMorse).putBoolean("preview_panel", pPanel).apply(); 
    Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); 
}
    @Override protected void onResume() { super.onResume(); refreshPreview(); }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).putBoolean("preview_morse", false).putBoolean("preview_panel", false).apply(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }
    private void reloadActionLabels() {
String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "TOGGLE_MORSE", "YTDL_DOWNLOAD", "VOICE_RECORD", "LAUNCH_APP", "OPEN_PANEL_1", "OPEN_PANEL_2", "OPEN_PANEL_3"};
String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn chính"), T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"), T("Power Menu", "Menu Nguồn"), T("Volume", "Âm Lượng"), T("Screenshot", "Chụp màn hình"), "Camera", T("Notifications", "Mở Thông Báo"), T("Toggle Acc", "Bật/Tắt Trợ Năng"), T("Toggle Overlay", "Bật/Tắt Lớp Phủ"), T("Lock App (Morse)", "Khóa App (Morse)"), "YTDLnis", T("Voice Record", "Ghi âm"), T("Launch App", "Mở Ứng dụng"), T("Open Panel 1","Mở Panel 1"), T("Open Panel 2","Mở Panel 2"), T("Open Panel 3","Mở Panel 3")};
// V19.12.3.6.10: bỏ SCREEN_ON khỏi danh sách chung — chỉ còn dùng riêng cho VOLKEY
for(int i=0; i<20; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; }
for(int i=1; i<=15; i++) { ACT_KEYS[19+i]="INTENT_"+i; ACT_LABS[19+i] = prefs.getString("intent_"+i+"_name", "Intent " + i); }
for(int i=1; i<=5; i++) { ACT_KEYS[34+i]="MACRO_"+i; ACT_LABS[34+i] = prefs.getString("macro_"+i+"_name", "Macro " + i); }
// V19.12.3.6.10: bỏ vol_on/vol_off khỏi component chung (đã có không gian VOLKEY riêng)
ALL_COMP_NAMES = new String[]{T("Bottom Right", "Thanh Đáy Phải"), T("Bottom Left", "Thanh Đáy Trái"), T("Top Right", "Thanh Cạnh Phải"), T("Top Left", "Thanh Cạnh Trái"), T("Top Center", "Thanh Đỉnh Giữa"), T("Corner BR", "Góc Viền Đáy Phải"), T("Corner BL", "Góc Viền Đáy Trái"), T("Corner TR", "Góc Viền Đỉnh Phải"), T("Corner TL", "Góc Viền Đỉnh Trái"), T("Fingerprint", "Vân Tay"), T("Launch App", "Lắng nghe Mở Ứng dụng")};
VOLKEY_COMP_NAMES = new String[]{T("Volume Up Button", "Nút Tăng Âm Lượng"), T("Volume Down Button", "Nút Giảm Âm Lượng")};
VOLKEY_GESTURE_NAMES = new String[]{T("Press Once", "Nhấn 1 Lần"), T("Press Twice", "Nhấn 2 Lần"), T("Hold", "Giữ (Long Press)")};
M_BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half Center", "Trung Tâm Trên"), T("Bottom Half Center", "Trung Tâm Dưới")};
C_GESTURE_NAMES = new String[]{T("Tap", "1 Chạm"), T("Double Tap", "2 Chạm"), T("Long Press", "Nhấn Giữ"), T("Swipe Up", "Vuốt Lên"), T("Swipe Down", "Vuốt Xuống"), T("Swipe Left", "Vuốt Trái"), T("Swipe Right", "Vuốt Phải"), T("Up + Hold", "Vuốt Lên + Giữ"), T("Down + Hold", "Vuốt Xuống + Giữ"), T("Left + Hold", "Vuốt Trái + Giữ"), T("Right + Hold", "Vuốt Phải + Giữ"), T("Diagonal", "Vuốt Chéo"), T("Diagonal + Hold", "Vuốt Chéo + Giữ")};
BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa")};
CORNER_NAMES = new String[]{T("Bottom Right Corner", "Góc đáy phải"), T("Bottom Left Corner", "Góc đáy trái"), T("Top Right Corner", "Góc đỉnh phải"), T("Top Left Corner", "Góc đỉnh trái")};
COLOR_NAMES = new String[]{T("White", "Trắng"), "Neon", "Cyberpunk", "Lava", "Ocean", "Matrix", "Sunset", "Google", "Aurora", "Abyss", "Forest", "Flame", "Midnight", "Tropical", "Candy"};
PANEL_COLOR_NAMES = new String[]{"Slate","Steel","Mist","Graphite", T("Indigo Mist","Chàm Sương"), T("Teal Grey","Xanh Lục Xám"), T("Cool Ash","Tro Lạnh"), T("Deep Blue","Xanh Đậm")};
PANEL_POS_NAMES = new String[]{
    T("Bottom Center","Đáy Giữa"), T("Bottom Left","Đáy Trái"), T("Bottom Right","Đáy Phải"),
    T("Left Top","Trái Trên"), T("Left Center","Trái Giữa"), T("Left Bottom","Trái Dưới"),
    T("Right Top","Phải Trên"), T("Right Center","Phải Giữa"), T("Right Bottom","Phải Dưới")
};	
}
private String[] getVolKeyActKeys() {
    String[] arr = new String[ACT_KEYS.length + 1];
    System.arraycopy(ACT_KEYS, 0, arr, 0, ACT_KEYS.length);
    arr[ACT_KEYS.length] = "SCREEN_ON";
    return arr;
}
private String[] getVolKeyActLabs() {
    String[] arr = new String[ACT_LABS.length + 1];
    System.arraycopy(ACT_LABS, 0, arr, 0, ACT_LABS.length);
    arr[ACT_LABS.length] = T("Screen On", "Bật màn hình");
    return arr;
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
    } else if (req == 104) {      // ✅ ĐÚNG: nối liền, không có } thừa ở giữa
        try {
            Intent shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            String shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            if (shortcutIntent == null) { Toast.makeText(this, "Shortcut không hợp lệ!", Toast.LENGTH_SHORT).show(); }
            else {
                String id = java.util.UUID.randomUUID().toString().substring(0, 8);
                String iconPath = "";
                Bitmap bmp = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
                if (bmp != null) iconPath = ShortcutScanner.saveIconToFile(this, bmp, id);
                String uri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME);
                String curIds = prefs.getString("shortcut_ids", "");
                String newIds = curIds.isEmpty() ? id : curIds + "," + id;
                prefs.edit()
                    .putString("shortcut_" + id + "_name", shortcutName == null ? "Shortcut" : shortcutName)
                    .putString("shortcut_" + id + "_intent_uri", uri)
                    .putString("shortcut_" + id + "_icon_path", iconPath)
                    .putString("shortcut_ids", newIds)
                    .apply();
                if (pendingShortcutCallback != null) {
                    pendingShortcutCallback.accept(id, shortcutName == null ? "Shortcut" : shortcutName);
                    pendingShortcutCallback = null;
                }
                Toast.makeText(this, T("Shortcut saved!","Đã lưu Shortcut!"), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) { Toast.makeText(this, "Lỗi lưu Shortcut!", Toast.LENGTH_SHORT).show(); }
    }
} catch(Exception e) { Toast.makeText(this, "IO Error!", Toast.LENGTH_LONG).show(); }
        } 
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == 201 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
        Intent i = new Intent(this, VoiceRecorderService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        renderEcosystem();
    } else if (requestCode == 201) {
        Toast.makeText(this, "Cần quyền Micro để ghi âm!", Toast.LENGTH_SHORT).show();
    }
}
    @Override public void onBackPressed() { if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); Button btnD = rootLayout.findViewWithTag("btnDesign"); if(btnD!=null){btnD.setText("⚙️"); btnD.setBackground(getRounded("#333333", 100f));} } else super.onBackPressed(); }
    private void closeDesignSpace() { currentMainTab = 1; pageDesign.setVisibility(View.GONE); navMain.setVisibility(View.VISIBLE); pageConditions.setVisibility(View.VISIBLE); updateFabVisibility(); refreshPreview(); }
private void openDesignSpace() { currentMainTab = 0; refreshPreview(); navMain.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE); pageDesign.setVisibility(View.VISIBLE); if(fab != null) fab.setVisibility(View.GONE); }
    // V19.12.3.6.10: FAB "+NEW EB" hiện ở mọi tab Điều kiện (kể cả LOCK) —
// riêng option vân tay đã bị loại khỏi component list của LOCK ngay trong
// buildRuleEditor(), nên không cần ẩn cả nút.
private void updateFabVisibility() {
    if (fab == null) return;
    if (currentMainTab == 1) { // Condition Space
        fab.setVisibility(View.VISIBLE);
        fab.setText("+NEW EB");
        fab.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1, ""));
    } else if (currentMainTab == 2) { // Ecosystem Space
        fab.setVisibility(View.VISIBLE);
        if (ecoType == 0 || ecoType == 1 || ecoType == 2) {
            fab.setText(ecoType == 0 ? "+INTENT" : (ecoType == 1 ? "QS TILE" : "+ MACRO"));
            fab.setOnClickListener(v -> {
                String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
                String newId = addDynamicId(listKey);
                if (ecoType == 0) openIntentEditorV2(newId);
                else if (ecoType == 1) openTileEditorV2(newId);
                else openMacroEditorV2(newId);
            });
        } else if (ecoType == 3) {
            fab.setText("+SOURCE");
            fab.setOnClickListener(v -> runDeepStorageScan());
        } else if (ecoType == 4) {
            boolean recOn = VoiceRecorderService.isRunning;
            fab.setText(recOn ? " END 🎙 " : "+RECORD");
            fab.setOnClickListener(v -> {
                if (android.content.pm.PackageManager.PERMISSION_GRANTED != checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 201);
                    return;
                }
                Intent i = new Intent(this, VoiceRecorderService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                // Delay nhỏ để service kịp cập nhật trạng thái
                new Handler().postDelayed(this::updateFabVisibility, 300);
            });
        }
    } else {
        fab.setVisibility(View.GONE);
    }
}
    private Button createCircleBtn(String icon, String color) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(color, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);syncVolumeService();updateFabVisibility();isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();
        
        // Tối ưu OLED: Nền đen tuyệt đối #000000 tắt hoàn toàn bóng LED trên Pixel 2XL
    rootLayout = new RelativeLayout(this);
    rootLayout.setBackgroundColor(Color.parseColor("#000000"));

    ScrollView scroll = new ScrollView(this); 
    RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1,-1); 
    rLp.bottomMargin = 240;
    scroll.setLayoutParams(rLp);

    LinearLayout main = new LinearLayout(this);
    main.setOrientation(LinearLayout.VERTICAL); main.setPadding(30,50,30,40);

    // Xây dựng Header theo bản vẽ tay image_95ae3d.jpg
    // Xây dựng Header theo bản vẽ tay image_95ae3d.jpg
LinearLayout headerRow = new LinearLayout(this);
headerRow.setOrientation(LinearLayout.HORIZONTAL);
headerRow.setPadding(0, 0, 0, 45);
headerRow.setGravity(Gravity.CENTER_VERTICAL);

// Cột trái: Tên App và Version (Tăng lên 22sp cực to rõ, vượt trội so với Conditions/Ecosystem)
LinearLayout leftCol = new LinearLayout(this);
leftCol.setOrientation(LinearLayout.VERTICAL);
leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
TextView title = new TextView(this);
title.setText("Edge Launcher\n" + CURRENT_VERSION);
title.setTextColor(Color.parseColor("#E8EAED"));
title.setTextSize(22f); // Tăng từ 18f lên 22f cho chuẩn tỷ lệ thị giác
title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
leftCol.addView(title);

// Cột phải: Backup/Restore và English (Nới rộng tỷ lệ weight 1.35f để không bao giờ bị nhảy dòng)
LinearLayout rightCol = new LinearLayout(this);
rightCol.setOrientation(LinearLayout.VERTICAL);
rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.35f));

LinearLayout topBtns = new LinearLayout(this);
topBtns.setOrientation(LinearLayout.HORIZONTAL);
Button btnBackup = createSystemBtn("💾BACKUP", "#202124", "#8AB4F8");
Button btnRestore = createSystemBtn("📁RESTORE", "#202124", "#8AB4F8");
btnBackup.setOnClickListener(v -> {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/json");
    i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup_" + System.currentTimeMillis() + ".json");
    startActivityForResult(i, 101);
});
btnRestore.setOnClickListener(v -> {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("*/*");
    startActivityForResult(i, 102);
});
topBtns.addView(btnBackup); topBtns.addView(btnRestore);

Button btnLang = createSystemBtn(isVi ? " 🇻🇳 TIẾNG VIỆT" : "🇺🇸 ENGLISH", "#202124", "#E8EAED");
btnLang.setOnClickListener(v -> {
    prefs.edit().putBoolean("lang_vi", !isVi).apply();
    Toast.makeText(this, isVi ? "Switching to English..." : "Đang chuyển Tiếng Việt...", Toast.LENGTH_SHORT).show();
    recreate(); // Tái tạo toàn bộ Activity để giải phóng RAM các chuỗi ngôn ngữ cũ
});
LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(-1, -2);
langLp.setMargins(4, 10, 4, 0);
btnLang.setLayoutParams(langLp);
rightCol.addView(topBtns); rightCol.addView(btnLang);
headerRow.addView(leftCol); headerRow.addView(rightCol);
main.addView(headerRow);
    // Navigation Tab đẩy ra đầu dòng kèm icon
    navMain = new LinearLayout(this);
    navMain.setOrientation(LinearLayout.HORIZONTAL); navMain.setPadding(0, 0, 0, 40);
        Button btnNavCond = createNavBtn(T("🎯 CONDITIONS", "ĐIỀU KIỆN"));
        Button btnNavEco = createNavBtn(" 🎭ECOSYSTEM");
        navMain.addView(btnNavCond); navMain.addView(btnNavEco); main.addView(navMain);

        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); buildConditionsSpace();
        pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); buildEcosystemSpace();

        main.addView(pageDesign); main.addView(pageConditions); main.addView(pageEcosystem);
        scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        Button btnUpdate = createCircleBtn("U", "#333333"); btnUpdate.setTextSize(20); btnUpdate.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i); });
        Button btnPremium = new Button(this); btnPremium.setText("PREMIUM");
btnPremium.setTextColor(Color.BLACK);
btnPremium.setTextSize(13.5f); // Chuẩn hóa kích thước mốc Premium
btnPremium.setBackground(getRounded("#00E5FF", 100f));
btnPremium.setOnClickListener(v -> showPremiumDialog());
LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -1);
pLp.setMargins(10, 0, 10, 0); btnPremium.setLayoutParams(pLp);
btnPremium.setPadding(35, 0, 35, 0);

View spacer = new View(this); spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));

fab = new Button(this); fab.setText("+NEW EB"); fab.setTextColor(Color.BLACK);
fab.setBackground(getRounded("#00E5FF", 100f)); fab.setTag("fab");
fab.setTextSize(13.5f); // Giảm từ 16f về đúng 13.5f bằng với Premium!
LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-2, 135);
fLp.setMargins(10, 0, 10, 0); fab.setLayoutParams(fLp); fab.setPadding(55, 0, 55, 0);

fab.setOnClickListener(v -> {
if (currentMainTab == 1) { // Đang ở Condition Space
        openRuleBuilderDialog(null, -1, -1, "");
    } else if (currentMainTab == 2) { // Đang ở Ecosystem Space
        String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
        String newId = addDynamicId(listKey);
        if (ecoType == 0) openIntentEditorV2(newId);
        else if (ecoType == 1) openTileEditorV2(newId);
        else openMacroEditorV2(newId);
    }
});
        Button btnDesign = createCircleBtn("⚙️", "#333333"); btnDesign.setTag("btnDesign");
        btnDesign.setOnClickListener(v -> { if(pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); btnDesign.setText("⚙️"); btnDesign.setBackground(getRounded("#333333", 100f)); } else { openDesignSpace(); btnDesign.setText("⬅"); btnDesign.setBackground(getRounded("#D32F2F", 100f)); } });
        bottomBar.addView(btnUpdate); bottomBar.addView(btnPremium); bottomBar.addView(spacer); bottomBar.addView(fab); bottomBar.addView(btnDesign);
        rootLayout.addView(bottomBar);
btnNavCond.setOnClickListener(v -> switchMainTab(1, btnNavCond, btnNavEco));
btnNavEco.setOnClickListener(v -> switchMainTab(2, btnNavCond, btnNavEco));
switchMainTab(1, btnNavCond, btnNavEco);
        setContentView(rootLayout);
    }
private void switchMainTab(int idx, Button b1, Button b2) { 
    currentMainTab = idx; refreshPreview(); navMain.setVisibility(View.VISIBLE);
    pageDesign.setVisibility(View.GONE); 
    pageConditions.setVisibility(idx==1?View.VISIBLE:View.GONE);
    pageEcosystem.setVisibility(idx==2?View.VISIBLE:View.GONE);
    updateFabVisibility();
    b1.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); b1.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY);
    b2.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); b2.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY);
    if(idx==1) renderRulesList();
    if(idx==2) renderEcosystem();
}
    // ==================== KHÔNG GIAN ĐIỀU KIỆN ====================
    private void buildConditionsSpace() {
    LinearLayout tabContainer = new LinearLayout(this);
    tabContainer.setOrientation(LinearLayout.HORIZONTAL);
    tabContainer.setPadding(0, 0, 0, 20);
btnLock = createTabBtn("LOCK");
btnHomacc = createTabBtn("HOMACC");
btnHome = createTabBtn("HOMEB");
btnVolKey = createTabBtn("VOLKEY");

LinearLayout.LayoutParams pMargR = new LinearLayout.LayoutParams(0, -2, 1f);
pMargR.setMargins(0, 0, 10, 0);
btnLock.setLayoutParams(pMargR);
LinearLayout.LayoutParams pMargR2 = new LinearLayout.LayoutParams(0, -2, 1f);
pMargR2.setMargins(0, 0, 10, 0);
btnHomacc.setLayoutParams(pMargR2);
LinearLayout.LayoutParams pMargR3 = new LinearLayout.LayoutParams(0, -2, 1f);
pMargR3.setMargins(0, 0, 10, 0);
btnHome.setLayoutParams(pMargR3);
btnVolKey.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

tabContainer.addView(btnLock);
tabContainer.addView(btnHomacc);
tabContainer.addView(btnHome);
tabContainer.addView(btnVolKey);
pageConditions.addView(tabContainer);
    listRules = new LinearLayout(this);
    listRules.setOrientation(LinearLayout.VERTICAL);
    pageConditions.addView(listRules);
btnLock.setOnClickListener(v -> {
    currentGesTab = 0; refreshPreview();
    btnLock.setBackground(getRounded("#00E5FF", 20f)); btnLock.setTextColor(Color.BLACK);
    btnHomacc.setBackground(getRounded("#333333", 20f)); btnHomacc.setTextColor(Color.WHITE);
    btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE);
    btnVolKey.setBackground(getRounded("#222222", 20f)); btnVolKey.setTextColor(Color.WHITE);
    updateFabVisibility(); renderRulesList();
});
btnHomacc.setOnClickListener(v -> {
    currentGesTab = 1; refreshPreview();
    btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE);
    btnHomacc.setBackground(getRounded("#7C4DFF", 20f)); btnHomacc.setTextColor(Color.BLACK);
    btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE);
    btnVolKey.setBackground(getRounded("#222222", 20f)); btnVolKey.setTextColor(Color.WHITE);
    updateFabVisibility(); renderRulesList();
});
btnHome.setOnClickListener(v -> {
    currentGesTab = 2; refreshPreview();
    btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE);
    btnHomacc.setBackground(getRounded("#333333", 20f)); btnHomacc.setTextColor(Color.WHITE);
    btnHome.setBackground(getRounded("#00E5FF", 20f)); btnHome.setTextColor(Color.BLACK);
    btnVolKey.setBackground(getRounded("#222222", 20f)); btnVolKey.setTextColor(Color.WHITE);
    updateFabVisibility(); renderRulesList();
});
btnVolKey.setOnClickListener(v -> {
    currentGesTab = 3; refreshPreview();
    btnLock.setBackground(getRounded("#222222", 20f)); btnLock.setTextColor(Color.WHITE);
    btnHomacc.setBackground(getRounded("#333333", 20f)); btnHomacc.setTextColor(Color.WHITE);
    btnHome.setBackground(getRounded("#222222", 20f)); btnHome.setTextColor(Color.WHITE);
    btnVolKey.setBackground(getRounded("#FFC107", 20f)); btnVolKey.setTextColor(Color.BLACK);
    updateFabVisibility(); renderRulesList();
});
btnHome.performClick();
}

    private void renderRulesList() {
    listRules.removeAllViews();
    final boolean isVolKeyMode = (currentGesTab == 3);
    String prefix = isVolKeyMode ? "volkey_" : (currentGesTab == 0 ? "lock_" : (currentGesTab == 1 ? "homacc_" : "home_"));
    String[] compsUsed = isVolKeyMode ? VOLKEY_COMPS : ALL_COMP_KEYS;
    String[] compNamesUsed = isVolKeyMode ? VOLKEY_COMP_NAMES : ALL_COMP_NAMES;
    String[] gesturesUsed = isVolKeyMode ? VOLKEY_GESTURES : C_GESTURES;
    String[] gestureNamesUsed = isVolKeyMode ? VOLKEY_GESTURE_NAMES : C_GESTURE_NAMES;
    String[] actKeysUsed = isVolKeyMode ? getVolKeyActKeys() : ACT_KEYS;
    String[] actLabsUsed = isVolKeyMode ? getVolKeyActLabs() : ACT_LABS;
    LinearLayout currentRow = null; int count = 0;
    for (int c = 0; c < compsUsed.length; c++) {
        for (int g = 0; g < gesturesUsed.length; g++) {
            String key = prefix + compsUsed[c] + "_" + gesturesUsed[g];
String action = prefs.getString(key, "NONE");
if (!action.equals("NONE")) {
    if (count % 2 == 0) { 
        currentRow = new LinearLayout(this);
        currentRow.setOrientation(LinearLayout.HORIZONTAL);
        currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        listRules.addView(currentRow); 
    }
    
    // Thẻ Condition bọc ngoài, thu bé lại một chút để nhét vừa 2 cột
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setBackground(getRounded("#202124", 24f)); 
    card.setPadding(15, 24, 10, 24);
    // Giảm Margin để có thêm diện tích vẽ chữ
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
    lp.setMargins(6, 6, 6, 6); 
    card.setLayoutParams(lp);

    // Cột 1 (Trái cùng): Icon Option (Rung/Animation) - Khôi phục và tăng size
    LinearLayout optCol = new LinearLayout(this);
    optCol.setOrientation(LinearLayout.VERTICAL);
    optCol.setGravity(Gravity.CENTER);
    optCol.setPadding(0, 0, 15, 0);
    TextView tIcons = new TextView(this);
    tIcons.setText((prefs.getBoolean(key+"_vib", true) ? "📳\n" : "") +
                   (prefs.getBoolean(key+"_anim", true) ? "✨" : ""));
    tIcons.setTextSize(16); // Tăng cỡ chữ lên 2 mức, bỏ Typeface.BOLD
    optCol.addView(tIcons);

    // Cột 2 (Giữa): Thông tin Component, Gesture, Action
    LinearLayout infoCol = new LinearLayout(this);
    infoCol.setOrientation(LinearLayout.VERTICAL);
    infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    TextView tCond = new TextView(this); 
    tCond.setText(compNamesUsed[c]); 
    tCond.setTextColor(Color.parseColor("#E8EAED")); 
    tCond.setTextSize(16); // Tăng lên 16, Font chữ thường
    
    TextView tGest = new TextView(this);
    tGest.setText(gestureNamesUsed[g]);
    tGest.setTextColor(Color.parseColor("#9AA0A6"));
    tGest.setTextSize(12); tGest.setPadding(0, 5, 0, 5);

    TextView tAct = new TextView(this); 
    String[] acts = action.split(",");
    StringBuilder actName = new StringBuilder();
    for(String a : acts) {
        String at = a.trim();
        if (at.equals("LAUNCH_APP")) {
            if(actName.length() > 0) actName.append(" + ");
            actName.append(getAppLabelCached(prefs.getString(key + "_launch_pkg", "")));
            continue;
        }
        for(int i = 0; i < actKeysUsed.length; i++) { 
            if(actKeysUsed[i].equals(at)) {
                if(actName.length() > 0) actName.append(" + "); 
                actName.append(actLabsUsed[i]); 
            } 
        }
    }
    tAct.setText(actName.toString().isEmpty() ? "Lỗi" : actName.toString());
tAct.setTextColor(Color.parseColor("#8AB4F8"));
tAct.setTextSize(16f); // Giảm nhẹ về 16f và ép Truncate để chống lẹm dòng
tAct.setMaxLines(1);
tAct.setEllipsize(android.text.TextUtils.TruncateAt.END);
tCond.setMaxLines(1); tCond.setEllipsize(android.text.TextUtils.TruncateAt.END);
tGest.setMaxLines(1); tGest.setEllipsize(android.text.TextUtils.TruncateAt.END);
infoCol.addView(tCond); infoCol.addView(tGest); infoCol.addView(tAct);

// Cột 3 (Phải cùng): Switch, Copy
LinearLayout ctrlCol = new LinearLayout(this);
ctrlCol.setOrientation(LinearLayout.VERTICAL);
ctrlCol.setGravity(Gravity.CENTER_HORIZONTAL);
Switch swOn = new Switch(this);
swOn.setChecked(prefs.getBoolean(key + "_on", true));
swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean(key + "_on", chk).apply());
swOn.setPadding(0, 0, 0, 10);
final int finalC = c; final int finalG = g; final String finalActs = action;

// Nút COPY phóng to +1.5 đơn vị (từ 11 lên 12.5sp), bố cục chống lẹm tuyệt đối
Button btnCopy = new Button(this); btnCopy.setText("COPY");
btnCopy.setBackground(getRounded("#303134", 14f));
btnCopy.setTextColor(Color.WHITE);
btnCopy.setTextSize(12.5f); // +1.5 đơn vị rõ ràng
btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
btnCopy.setPadding(12, 10, 12, 10);
LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
btnLp.setMargins(0, 8, 0, 0); btnCopy.setLayoutParams(btnLp);
btnCopy.setMinimumHeight(88); // Đảm bảo độ cao chạm an toàn theo chuẩn Material Design
btnCopy.setOnClickListener(v -> openRuleBuilderDialog(null, finalC, finalG, finalActs));
ctrlCol.addView(swOn); ctrlCol.addView(btnCopy);
    card.addView(optCol); card.addView(infoCol); card.addView(ctrlCol);

    // THUẬT TOÁN UX: CHẠM 1 LẦN -> MỞ EDIT DIALOG
    card.setOnClickListener(v -> openRuleBuilderDialog(key, finalC, finalG, ""));

    // CHẠM GIỮ -> XÓA
    card.setOnLongClickListener(v -> { 
        new AlertDialog.Builder(this).setTitle(T("Delete?", "Xóa?"))
            .setPositiveButton("XÓA", (d,w) -> {
                prefs.edit().putString(key, "NONE").apply(); 
                if(isVolKeyMode) syncVolumeService(); 
                renderRulesList();
            }).setNegativeButton("HỦY", null).show(); 
        return true; 
    });

    currentRow.addView(card); count++;
}
            }
        }
        if(count % 2 != 0 && currentRow != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); currentRow.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText(T("No rules yet.\nPress + NEW EB to create.", "Chưa có quy tắc nào.\nBấm + NEW EB để tạo.")); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listRules.addView(empty); }
    }
private void renderVolKeyRules() {
    listRules.removeAllViews();
    String[] vKeys  = {"up_tap","down_tap","up_long","down_long"};
    String[] vNames = {"Nhấn Volume Up","Nhấn Volume Down","Giữ Volume Up","Giữ Volume Down"};
    for (int idx=0; idx<vKeys.length; idx++) {
        final String key = "volkey_" + vKeys[idx];
        final String vName = vNames[idx]; // ← THÊM DÒNG NÀY 
        String action = prefs.getString(key, "NONE");
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#1E1E1E", 25f)); card.setPadding(35,35,35,35);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(15,15,15,15); card.setLayoutParams(lp);
        TextView t1 = new TextView(this); t1.setText(vNames[idx]); t1.setTextColor(Color.parseColor("#FFC107")); t1.setTextSize(15);
        TextView t2 = new TextView(this); t2.setText(getActionLabelSmart(action, prefs.getString(key + "_launch_pkg", ""))); t2.setTextColor(Color.parseColor("#00E5FF")); t2.setTextSize(13); t2.setPadding(0,10,0,10);
        card.addView(t1); card.addView(t2);
        card.setOnClickListener(v -> openVolKeyActionPicker(key, vName)); // ← đổi vNames[idx] thành vName 
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("Xoá?").setPositiveButton("XOÁ", (d,w)->{
                prefs.edit().putString(key, "NONE").apply(); syncVolumeService(); renderVolKeyRules();
            }).setNegativeButton("HỦY", null).show();
            return true;
        });
        listRules.addView(card);
    }
    TextView note = new TextView(this);
    note.setText("⚠ Chỉ hoạt động khi MÀN HÌNH TẮT. Khi màn sáng, phím Âm lượng hoạt động bình thường.\nMỗi phím chỉ chạy 1 hành động.");
    note.setTextColor(Color.GRAY); note.setTextSize(12); note.setPadding(20,20,20,20);
    listRules.addView(note);
}

private void openVolKeyActionPicker(String key, String title) {
    reloadActionLabels();
    new AlertDialog.Builder(this).setTitle(title)
        .setSingleChoiceItems(ACT_LABS, -1, (d, which) -> {
            prefs.edit().putString(key, ACT_KEYS[which]).apply();
            d.dismiss();
            if (ACT_KEYS[which].equals("LAUNCH_APP")) {
                showSingleAppPickerDialogCallback(pkg -> {
                    prefs.edit().putString(key + "_launch_pkg", pkg).apply();
                    syncVolumeService(); renderVolKeyRules();
                });
            } else {
                syncVolumeService(); renderVolKeyRules();
            }
        }).setNegativeButton("HỦY", null).show();
}
private void syncVolumeService() {
    boolean need = VolumeButtonService.hasAnyRule(prefs);
    Intent i = new Intent(this, VolumeButtonService.class);
    if (need && !VolumeButtonService.isRunning) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    } else if (!need && VolumeButtonService.isRunning) {
        stopService(i);
    }
}
   // Vân tay chỉ hỗ trợ 4 hướng swipe — ẩn các cử chỉ không khả dụng để tránh
// người dùng gán nhầm (tap/long/diag/hold sẽ KHÔNG BAO GIỜ được phần cứng gửi lên)
private void updateGestureVisibilityForFingerprint(int compIdx, ArrayList<CheckBox> boxes) {
    boolean isFingerprint = ALL_COMP_KEYS[compIdx].equals("fingerprint");
    for (int i = 0; i < boxes.size() && i < C_GESTURES.length; i++) {
        String g = C_GESTURES[i];
        boolean allowed = g.equals("up") || g.equals("down") || g.equals("left") || g.equals("right");
        if (isFingerprint) {
            boxes.get(i).setVisibility(allowed ? View.VISIBLE : View.GONE);
            if (!allowed) boxes.get(i).setChecked(false); // bỏ tick nếu đang chọn nhầm gesture không hỗ trợ
        } else {
            boxes.get(i).setVisibility(View.VISIBLE);
        }
    }
}
    private void openRuleBuilderDialog(String editKey, int preComp, int preGes, String copyActs) { Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen); d.setContentView(buildRuleEditor(d, editKey, preComp, preGes, copyActs)); d.show(); }

    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes, String copyActs) {
        reloadActionLabels();
        final boolean isVolKeyMode = (currentGesTab == 3);
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

        // V19.12.3.6.10: component hiển thị tùy theo tab (Lock không có vân tay,
        // VOLKEY chỉ có Volume Up/Down)
        final java.util.ArrayList<Integer> visibleIdx = new java.util.ArrayList<>();
        final String[] compNamesShown;
        if (isVolKeyMode) {
            compNamesShown = VOLKEY_COMP_NAMES;
        } else {
            for (int ci=0; ci<ALL_COMP_KEYS.length; ci++) {
                if (ALL_COMP_KEYS[ci].equals("fingerprint") && currentGesTab == 0) continue; // Lock: bỏ vân tay
                visibleIdx.add(ci);
            }
            compNamesShown = new String[visibleIdx.size()];
            for (int vi=0; vi<visibleIdx.size(); vi++) compNamesShown[vi] = ALL_COMP_NAMES[visibleIdx.get(vi)];
        }
        Spinner spComp = createSpinner(); spComp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, compNamesShown));
        if (isVolKeyMode) {
            spComp.setSelection(preComp != -1 ? preComp : 0);
            spComp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){selectedComp[0] = pos;}public void onNothingSelected(AdapterView<?> p){}});
        } else {
            int initPos = 0;
            for (int vi=0; vi<visibleIdx.size(); vi++) if (visibleIdx.get(vi) == selectedComp[0]) { initPos = vi; break; }
            spComp.setSelection(initPos);
            // SAU: thêm cập nhật hiển thị gesture ngay khi đổi component
spComp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
    public void onItemSelected(AdapterView<?> p, View v, int pos, long id){
        selectedComp[0] = visibleIdx.get(pos);
        updateGestureVisibilityForFingerprint(selectedComp[0], gestureBoxes);
    }
    public void onNothingSelected(AdapterView<?> p){}
});
        }
        vTrig.addView(spComp);

        TextView tvG = new TextView(this); tvG.setText(T("\n2. CHOOSE GESTURES (OR logic)", "\n2. CHỌN CỬ CHỈ (Được chọn nhiều - Lệnh OR)")); tvG.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvG);
        final String[] gesturesShown = isVolKeyMode ? VOLKEY_GESTURES : C_GESTURES;
        final String[] gestureNamesShown = isVolKeyMode ? VOLKEY_GESTURE_NAMES : C_GESTURE_NAMES;
        for (int i=0; i<gesturesShown.length; i++) { CheckBox cb = new CheckBox(this); cb.setText(gestureNamesShown[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0,20,0,20); if(preGes != -1 && i == preGes) cb.setChecked(true); gestureBoxes.add(cb); vTrig.addView(cb); }
        // Gọi ngay sau khi tạo xong gestureBoxes để áp dụng đúng trạng thái ban đầu
updateGestureVisibilityForFingerprint(selectedComp[0], gestureBoxes);
        LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE);
        TextView tvA = new TextView(this); tvA.setText(T("CHOOSE ACTIONS (Multi-select)", "CHỌN HÀNH ĐỘNG THỰC THI (Được chọn nhiều)")); tvA.setTextColor(Color.parseColor("#00E5FF")); tvA.setPadding(0,0,0,20); vAct.addView(tvA);
        
       final String[] actKeysUsed = isVolKeyMode ? getVolKeyActKeys() : ACT_KEYS;
final String[] actLabsUsed = isVolKeyMode ? getVolKeyActLabs() : ACT_LABS;
String savedActs = editKey != null ? prefs.getString(editKey, "") : copyActs;
String[] savedArray = savedActs.split(",");

final boolean[] launchAppSelected = { false };
final String[] launchAppPkg = { editKey != null ? prefs.getString(editKey + "_launch_pkg", "") : "" };
final boolean[] shortcutSelected = { false };
final String[] shortcutId = { "" };

for (String sa : savedArray) {
    if (sa.trim().equals("LAUNCH_APP")) launchAppSelected[0] = true;
    if (sa.trim().equals("RUN_SHORTCUT")) shortcutSelected[0] = true;
}
String savedShortcutId = editKey != null ? prefs.getString(editKey + "_shortcut_id", "") : "";
if (shortcutSelected[0] && !savedShortcutId.isEmpty()) shortcutId[0] = savedShortcutId;

// KIỂM TRA ĐIỀU KIỆN: Nếu KHÔNG phải không gian LOCK (currentGesTab != 0) thì mới sinh View Launch App & Shortcut
// Kiểm tra không gian: Khóa màn hình hoặc Phím âm lượng (VolKey)
boolean isLockSpace = (currentGesTab == 0 && !isVolKeyMode) || (editKey != null && editKey.startsWith("lock_"));

// ĐÃ SỬA: khai báo selectedActs và SYS_ITEMS ra NGOÀI khối if,
// để mọi nơi phía sau (kể cả bSave.setOnClickListener) đều truy cập được.
final java.util.LinkedHashSet<String> selectedActs = new java.util.LinkedHashSet<>();
for (String sa : savedArray) {
    String t = sa.trim();
    if (!t.equals("LAUNCH_APP") && !t.equals("RUN_SHORTCUT") && !t.isEmpty()) selectedActs.add(t);
}
// --- [CODE MỚI THAY THẾ - TỐI ƯU PIXEL 2 XL] ---
List<String[]> SYS_ITEMS = buildItemsForKeys(new String[]{
"BACK","HOME", "RECENTS", "SCREEN_OFF","FLASH","POWER_DIALOG", "VOLUME",
"SCREENSHOT", "CAMERA","NOTIFICATIONS","TOGGLE_ACC","TOGGLE_OVERLAY",
"TOGGLE_MORSE", "VOICE_RECORD"
}, actKeysUsed, actLabsUsed);

// YÊU CẦU 1: Tách riêng Launch App/Shortcut (chỉ cho Homeb sáng màn)
// Tiện ích/Intents/Macros được phép dùng trên cả HOMEB và LOCK SPACE
if (!isLockSpace && !isVolKeyMode) {
    // 1. HỘP LAUNCH APP (Khóa trên màn Lock vì cần unlock OS)
    LinearLayout launchAppCard = new LinearLayout(this);
    launchAppCard.setOrientation(LinearLayout.VERTICAL);
    launchAppCard.setBackground(getRounded("#1A2C3A", 20f));
    launchAppCard.setPadding(30, 30, 30, 30);
    LinearLayout.LayoutParams lacLp = new LinearLayout.LayoutParams(-1, -2);
    lacLp.setMargins(0, 0, 0, 20);
    launchAppCard.setLayoutParams(lacLp);
    LinearLayout lacRow = new LinearLayout(this);
    lacRow.setOrientation(LinearLayout.HORIZONTAL);
    lacRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvLacTitle = new TextView(this); tvLacTitle.setText("🚀 " + T("Launch App", "Mở Ứng Dụng"));
    tvLacTitle.setTextColor(Color.WHITE); tvLacTitle.setTextSize(14.5f);
    tvLacTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    tvLacTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Switch swLaunchApp = new Switch(this);
    swLaunchApp.setChecked(launchAppSelected[0]);
    swLaunchApp.setOnCheckedChangeListener((b, c) -> launchAppSelected[0] = c);
    lacRow.addView(tvLacTitle); lacRow.addView(swLaunchApp);
    launchAppCard.addView(lacRow);
    TextView tvChosenApp = new TextView(this);
    tvChosenApp.setTextColor(Color.parseColor("#00E5FF")); tvChosenApp.setPadding(0, 10, 0, 15);
    tvChosenApp.setText(getAppLabelCached(launchAppPkg[0]));
    launchAppCard.addView(tvChosenApp);
    Button btnPickLaunchApp = new Button(this); btnPickLaunchApp.setText("📱 " + T("CHOOSE APP", "CHỌN APP"));
    btnPickLaunchApp.setBackground(getRounded("#00E5FF", 20f));
    btnPickLaunchApp.setTextColor(Color.BLACK); btnPickLaunchApp.setTextSize(13f);
    btnPickLaunchApp.setOnClickListener(v -> showSingleAppPickerDialogCallback(pkg -> { launchAppPkg[0] = pkg; tvChosenApp.setText(getAppLabelCached(pkg)); swLaunchApp.setChecked(true); }));
    launchAppCard.addView(btnPickLaunchApp);
    vAct.addView(launchAppCard);

    // 2. HỘP RUN SHORTCUT
    LinearLayout shortcutCard = new LinearLayout(this);
    shortcutCard.setOrientation(LinearLayout.VERTICAL);
    shortcutCard.setBackground(getRounded("#2A1A3A", 20f));
    shortcutCard.setPadding(30, 30, 30, 30);
    LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(-1, -2);
    scLp.setMargins(0, 0, 0, 20);
    shortcutCard.setLayoutParams(scLp);
    LinearLayout scRow = new LinearLayout(this);
    scRow.setOrientation(LinearLayout.HORIZONTAL);
    scRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvScTitle = new TextView(this); tvScTitle.setText("🔗 " + T("Run Shortcut", "Chạy Shortcut"));
    tvScTitle.setTextColor(Color.WHITE); tvScTitle.setTextSize(14.5f);
    tvScTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    tvScTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Switch swShortcut = new Switch(this); swShortcut.setChecked(shortcutSelected[0]);
    swShortcut.setOnCheckedChangeListener((b, c) -> shortcutSelected[0] = c);
    scRow.addView(tvScTitle); scRow.addView(swShortcut);
    shortcutCard.addView(scRow);
    TextView tvChosenSc = new TextView(this);
    tvChosenSc.setTextColor(Color.parseColor("#00E5FF")); tvChosenSc.setPadding(0, 10, 0, 15);
    tvChosenSc.setText(shortcutId[0].isEmpty() ? T("(Not selected)", "(Chưa chọn)") : prefs.getString("shortcut_" + shortcutId[0] + "_name", "?"));
    shortcutCard.addView(tvChosenSc);
    Button btnPickShortcut = new Button(this); btnPickShortcut.setText("⚡ " + T("CHOOSE SHORTCUT", "CHỌN SHORTCUT"));
    btnPickShortcut.setBackground(getRounded("#7C4DFF", 20f));
    btnPickShortcut.setTextColor(Color.WHITE); btnPickShortcut.setTextSize(13f);
    btnPickShortcut.setOnClickListener(v -> showShortcutPickerDialog((id, name) -> { shortcutId[0] = id; tvChosenSc.setText(name); swShortcut.setChecked(true); }));
    shortcutCard.addView(btnPickShortcut);
    vAct.addView(shortcutCard);
} else {
    // Zero-RAM: Không tải 2 hộp App/Shortcut cho Lock và VolKey
    launchAppSelected[0] = false;
    shortcutSelected[0] = false;
}

// MỤC SYSTEM: Luôn hiển thị ở mọi không gian
vAct.addView(buildActionCategoryCard("SYSTEM", "⚙️", SYS_ITEMS, selectedActs, "#4CAF50"));

// YÊU CẦU 1 (Cốt lõi): UTILITIES / INTENTS / MACROS mở khóa cho CẢ HOMEB và LOCK
// Chỉ khóa với VolKey (phím cứng khi tắt màn không nên chạy Macro/Intent nặng)
if (!isVolKeyMode) {
    List<String[]> UTIL_ITEMS = buildItemsForKeys(new String[]{"YTDL_DOWNLOAD"}, actKeysUsed, actLabsUsed);
    List<String[]> INTENT_ITEMS = buildItemsForPrefix("INTENT_", actKeysUsed, actLabsUsed);
    List<String[]> MACRO_ITEMS = buildItemsForPrefix("MACRO_", actKeysUsed, actLabsUsed);
    vAct.addView(buildActionCategoryCard("UTILITIES", "🛠️", UTIL_ITEMS, selectedActs, "#FF9800"));
    vAct.addView(buildActionCategoryCard("INTENTS", "⚡", INTENT_ITEMS, selectedActs, "#D32F2F"));
    vAct.addView(buildActionCategoryCard("MACROS", "🤖", MACRO_ITEMS, selectedActs, "#2196F3"));
}
// --- [KẾT THÚC CODE MỚI] ---
LinearLayout vOpt = new LinearLayout(this); vOpt.setOrientation(LinearLayout.VERTICAL); vOpt.setVisibility(View.GONE);
        cbVib.setText(T("Haptic Feedback", "Bật Rung (Haptic Feedback)")); cbVib.setTextColor(Color.WHITE); cbVib.setChecked(editKey == null || prefs.getBoolean(editKey+"_vib", true)); vOpt.addView(cbVib);
        cbAnim.setText(T("Show Animation", "Bật Hiệu ứng Ánh sáng (Animation)")); cbAnim.setTextColor(Color.WHITE); cbAnim.setChecked(editKey == null || prefs.getBoolean(editKey+"_anim", true));
        if (!isVolKeyMode) vOpt.addView(cbAnim); else cbAnim.setChecked(false); // Màn tắt: không có overlay để chớp sáng

        content.addView(vTrig); content.addView(vAct); content.addView(vOpt);

        View.OnClickListener tabClick = v -> { bTrig.setBackground(getRounded(v==bTrig?"#00E5FF":"#222222", 15f)); bTrig.setTextColor(v==bTrig?Color.BLACK:Color.WHITE); bAct.setBackground(getRounded(v==bAct?"#00E5FF":"#222222", 15f)); bAct.setTextColor(v==bAct?Color.BLACK:Color.WHITE); bOpt.setBackground(getRounded(v==bOpt?"#00E5FF":"#222222", 15f)); bOpt.setTextColor(v==bOpt?Color.BLACK:Color.WHITE); vTrig.setVisibility(v==bTrig?View.VISIBLE:View.GONE); vAct.setVisibility(v==bAct?View.VISIBLE:View.GONE); vOpt.setVisibility(v==bOpt?View.VISIBLE:View.GONE); }; bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick); bOpt.setOnClickListener(tabClick); bTrig.performClick();

        LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL);
        Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY")); bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText(T("SAVE RULE", "LƯU QUY TẮC")); bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp);
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

        bCancel.setOnClickListener(v -> dialog.dismiss());
        bSave.setOnClickListener(v -> {
            ArrayList<String> acts = new ArrayList<>();
acts.addAll(selectedActs); // gom từ 4 card SYSTEM/UTILITIES/INTENTS/MACROS
if (launchAppSelected[0]) {
    if (launchAppPkg[0].isEmpty()) { Toast.makeText(this, T("Pick an app first!", "Hãy chọn 1 app trước!"), Toast.LENGTH_SHORT).show(); return; }
    acts.add("LAUNCH_APP");
}
     if (shortcutSelected[0]) {
    if (shortcutId[0].isEmpty()) { Toast.makeText(this, T("Pick a shortcut first!","Hãy chọn 1 shortcut trước!"), Toast.LENGTH_SHORT).show(); return; }
    acts.add("RUN_SHORTCUT");
}
            if(acts.isEmpty()) { Toast.makeText(this, T("Select at least 1 Action!", "Hãy chọn ít nhất 1 Hành động!"), Toast.LENGTH_SHORT).show(); return; }
            String joinedActions = TextUtils.join(",", acts); 
            String prefix = isVolKeyMode ? "volkey_" : (currentGesTab == 0 ? "lock_" : (currentGesTab == 1 ? "homacc_" : "home_"));
String compKey = isVolKeyMode ? VOLKEY_COMPS[selectedComp[0]] : ALL_COMP_KEYS[selectedComp[0]];
String[] gesturesUsedSave = isVolKeyMode ? VOLKEY_GESTURES : C_GESTURES;
boolean hasChecked = false;
            if(editKey != null && preGes != -1) prefs.edit().putString(editKey, "NONE").apply();
            for(int i=0; i<gestureBoxes.size(); i++) {
               if(gestureBoxes.get(i).isChecked()) {
hasChecked = true; String finalKey = prefix + compKey + "_" + gesturesUsedSave[i];
prefs.edit()
     .putString(finalKey, joinedActions)
     .putBoolean(finalKey+"_vib", cbVib.isChecked())
     .putBoolean(finalKey+"_anim", cbAnim.isChecked())
     .putString(finalKey+"_launch_pkg", launchAppPkg[0])
     .putString(finalKey+"_shortcut_id", shortcutId[0])
     .apply();
}
            }
            if(!hasChecked) { Toast.makeText(this, T("Select at least 1 Trigger!", "Hãy chọn ít nhất 1 Cử chỉ!"), Toast.LENGTH_SHORT).show(); return; }
            if (isVolKeyMode) syncVolumeService();
            renderRulesList(); dialog.dismiss();
        });
        return root;
    }
     // Xây 1 drawer chứa checkbox action cho 1 nhóm — LAZY: chỉ add checkbox thật sự
// vào drawer khi lần đầu người dùng bấm mở (tiết kiệm object allocation lúc mở dialog,
// quan trọng vì Intent/Macro có thể lên tới hàng chục item không giới hạn).
private LinearLayout buildActionCategoryDrawer(String title, String[] groupKeys,
        String[] actKeysUsed, String[] actLabsUsed, String[] savedArray,
        ArrayList<CheckBox> actionBoxes, ArrayList<String> actionBoxKeys) {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(20, 10, 20, 20);
    final boolean[] inflated = {false};
    LinearLayout drawer = createDrawer(title, content);
    // Hook vào header đã có sẵn trong createDrawer() để lazy-inflate lúc mở lần đầu
    View header = drawer.getChildAt(0);
    View.OnClickListener original = null; // createDrawer tự gắn listener nội bộ, ta thêm lazy-fill qua content addView 1 lần
    if (!inflated[0]) {
        inflated[0] = true;
        for (String gk : groupKeys) {
            int idx = -1;
            for (int i = 0; i < actKeysUsed.length; i++) if (actKeysUsed[i].equals(gk)) { idx = i; break; }
            if (idx == -1) continue; // key không tồn tại ở tab hiện tại (vd SCREEN_ON ngoài VOLKEY)
            CheckBox cb = new CheckBox(this);
            cb.setText(actLabsUsed[idx]); cb.setTextColor(Color.WHITE); cb.setPadding(0, 15, 0, 15);
            boolean checked = false;
            for (String sa : savedArray) if (sa.trim().equals(gk)) { checked = true; break; }
            cb.setChecked(checked);
            actionBoxes.add(cb); actionBoxKeys.add(gk);
            content.addView(cb);
        }
    }
    return drawer;
}

private LinearLayout buildActionCategoryDrawerByPrefix(String title, String prefix,
        String[] actKeysUsed, String[] actLabsUsed, String[] savedArray,
        ArrayList<CheckBox> actionBoxes, ArrayList<String> actionBoxKeys) {
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(20, 10, 20, 20);
    for (int i = 0; i < actKeysUsed.length; i++) {
        if (!actKeysUsed[i].startsWith(prefix)) continue;
        CheckBox cb = new CheckBox(this);
        cb.setText(actLabsUsed[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0, 15, 0, 15);
        boolean checked = false;
        for (String sa : savedArray) if (sa.trim().equals(actKeysUsed[i])) { checked = true; break; }
        cb.setChecked(checked);
        actionBoxes.add(cb); actionBoxKeys.add(actKeysUsed[i]);
        content.addView(cb);
    }
    return createDrawer(title, content);
}
    // ==================== ACTION CATEGORY CARDS (thay cho Drawer) ====================
// Battery/RAM Pixel 2XL: card chỉ có 3 view nhẹ (tiêu đề, đếm số, nút) — KHÔNG
// tạo checkbox nào cho tới khi user bấm "CHỌN". Dialog picker bị destroy hoàn
// toàn khi đóng (GC thu hồi), khác hẳn Drawer cũ vốn luôn giữ toàn bộ checkbox
// trong RAM ngay cả khi đang cuộn ẩn (chỉ setVisibility GONE, không giải phóng).

private List<String[]> buildItemsForKeys(String[] keys, String[] actKeysUsed, String[] actLabsUsed) {
    List<String[]> out = new ArrayList<>();
    for (String gk : keys) {
        for (int i = 0; i < actKeysUsed.length; i++) {
            if (actKeysUsed[i].equals(gk)) { out.add(new String[]{actLabsUsed[i], gk}); break; }
        }
    }
    return out;
}

private List<String[]> buildItemsForPrefix(String prefix, String[] actKeysUsed, String[] actLabsUsed) {
    List<String[]> out = new ArrayList<>();
    for (int i = 0; i < actKeysUsed.length; i++) {
        if (actKeysUsed[i].startsWith(prefix)) out.add(new String[]{actLabsUsed[i], actKeysUsed[i]});
    }
    return out;
}

private LinearLayout buildActionCategoryCard(String title, String emoji, List<String[]> items, java.util.LinkedHashSet<String> selectedSet, String colorHex) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(getRounded(colorHex, 20f));
    card.setPadding(30, 30, 30, 30);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, 20);
    card.setLayoutParams(lp);

    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvTitle = new TextView(this);
    tvTitle.setText(emoji + " " + title);
    tvTitle.setTextColor(Color.WHITE);
    tvTitle.setTextSize(14.5f);
    tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    row.addView(tvTitle);
    card.addView(row);

    TextView tvCount = new TextView(this);
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setPadding(0, 10, 0, 15);
    Runnable updateCount = () -> {
        int cnt = 0;
        for (String[] it : items) if (selectedSet.contains(it[1])) cnt++;
        tvCount.setText(cnt == 0 ? T("(Not selected)", "(Chưa chọn)") : "⚡ " + cnt + " " + T("selected", "hành động đã chọn"));
    };
    updateCount.run();
    card.addView(tvCount);

    Button btnPick = new Button(this);
    btnPick.setText("⚡ " + T("CHOOSE ACTIONS", "CHỌN HÀNH ĐỘNG"));
    btnPick.setBackground(getRounded("#00000066", 20f));
    btnPick.setTextColor(Color.WHITE);
    btnPick.setTextSize(13f); // xem lỗi thứ 2 bên dưới
    btnPick.setOnClickListener(v -> showActionCategoryPicker(title, items, selectedSet, updateCount));
    card.addView(btnPick);

    return card;
}
// Dialog picker DÙNG CHUNG cho cả 4 category — có ô tìm kiếm + multi-select,
// y hệt pattern showPanelMultiPicker() đã có sẵn, để đồng bộ trải nghiệm.
private void showActionCategoryPicker(String title, List<String[]> items,
        java.util.LinkedHashSet<String> selectedSet, Runnable onChange) {
    // Làm việc trên bản sao — chỉ apply khi bấm LƯU, tránh sửa dở dang khi HỦY
    final java.util.LinkedHashSet<String> working = new java.util.LinkedHashSet<>(selectedSet);

    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 80, 30, 30);

    TextView tvTitle = new TextView(this); tvTitle.setText(title);
    tvTitle.setTextColor(Color.parseColor("#00E5FF")); tvTitle.setTextSize(18); tvTitle.setPadding(0, 0, 0, 20);
    root.addView(tvTitle);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...", "Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30, 25, 30, 25);
    root.addView(etSearch);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    final List<String[]> shown = new ArrayList<>();
    final Runnable[] refreshHolder = new Runnable[1];
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20, 22, 20, 22);
            String[] item = shown.get(p);
            CheckBox cb = new CheckBox(MainActivity.this);
            cb.setChecked(working.contains(item[1])); cb.setClickable(false);
            TextView tv = new TextView(MainActivity.this);
            tv.setText(item[0]); tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(cb); row.addView(tv);
            row.setOnClickListener(v -> {
                if (working.contains(item[1])) working.remove(item[1]); else working.add(item[1]);
                refreshHolder[0].run();
            });
            return row;
        }
    };
    lv.setAdapter(adapter);

    Runnable doRefresh = () -> {
        String q = etSearch.getText().toString().trim().toLowerCase();
        shown.clear();
        List<String[]> selSorted = new ArrayList<>();
        List<String[]> rest = new ArrayList<>();
        for (String[] it : items) {
            if (!q.isEmpty() && !it[0].toLowerCase().contains(q)) continue;
            if (working.contains(it[1])) selSorted.add(it); else rest.add(it);
        }
        shown.addAll(selSorted); shown.addAll(rest);
        adapter.notifyDataSetChanged();
    };
    refreshHolder[0] = doRefresh;
    etSearch.addTextChangedListener(new android.text.TextWatcher() {
        public void afterTextChanged(android.text.Editable s) { doRefresh.run(); }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
    });
    doRefresh.run();

    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0, 20, 0, 0);
    Button bCancel = new Button(this); bCancel.setText(T("CANCEL", "HỦY")); bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Button bSave = new Button(this); bSave.setText(T("SAVE", "LƯU")); bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f); slp.setMargins(20, 0, 0, 0); bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        selectedSet.clear();
        selectedSet.addAll(working);
        onChange.run();
        d.dismiss();
    });

    d.setContentView(root); d.show();
}
    // ==================== KHÔNG GIAN HỆ SINH THÁI (ECOSYSTEM) ====================
    private void buildEcosystemSpace() {
    // TẦNG 1: Nút Không Gian System Behavior bao bọc các tính năng hệ thống (Ngay trên 3 nút Intents/QS/Macros)
    Button btnSystemBehavior = new Button(this);
    btnSystemBehavior.setText(T("⚙️ SYSTEM BEHAVIOR (BLACKLIST / YTDL / STORAGE)", "⚙️ HÀNH VI HỆ THỐNG (BLACKLIST / YTDL / LƯU TRỮ)"));
    btnSystemBehavior.setBackground(getRounded("#202124", 30f));
    btnSystemBehavior.setTextColor(Color.parseColor("#00E5FF"));
    btnSystemBehavior.setTextSize(13.5f);
    btnSystemBehavior.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    LinearLayout.LayoutParams sysLp = new LinearLayout.LayoutParams(-1, -2);
    sysLp.setMargins(0, 0, 0, 20);
    btnSystemBehavior.setLayoutParams(sysLp);
    pageEcosystem.addView(btnSystemBehavior);

    // TẦNG 2: 3 Nút Không Gian Intents / QS Tiles / Macros
    LinearLayout ecoNav = new LinearLayout(this);
    ecoNav.setOrientation(LinearLayout.HORIZONTAL);
    ecoNav.setPadding(0, 0, 0, 35);
    Button btnIntents = new Button(this); btnIntents.setText("INTENTS");
    btnIntents.setBackground(getRounded("#D32F2F", 40f)); btnIntents.setTextColor(Color.WHITE); btnIntents.setTextSize(13.5f);
    Button btnTiles = new Button(this); btnTiles.setText("QS TILES");
    btnTiles.setBackground(getRounded("#4CAF50", 40f)); btnTiles.setTextColor(Color.WHITE); btnTiles.setTextSize(13.5f);
    Button btnMacros = new Button(this); btnMacros.setText("MACROS");
    btnMacros.setBackground(getRounded("#2196F3", 40f)); btnMacros.setTextColor(Color.WHITE); btnMacros.setTextSize(13.5f);
    
    LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, -2, 1f);
    btnLp.setMargins(6, 0, 6, 0);
    btnIntents.setLayoutParams(btnLp); btnTiles.setLayoutParams(btnLp); btnMacros.setLayoutParams(btnLp);
    ecoNav.addView(btnIntents); ecoNav.addView(btnTiles); ecoNav.addView(btnMacros);
    pageEcosystem.addView(ecoNav);
    ecoContainer = new LinearLayout(this);
    ecoContainer.setOrientation(LinearLayout.VERTICAL);
    pageEcosystem.addView(ecoContainer);
    // Thuật toán bật/tắt (Toggle) che lấp hoàn toàn 3 nút không gian bên dưới
    btnSystemBehavior.setOnClickListener(v -> {
        if (ecoType == 5) {
            ecoType = 0; // Bấm lần 2: Đóng lại, quay về tab Intents mặc định
            ecoNav.setVisibility(View.VISIBLE); // Hiện lại 3 nút Intents/QS/Macros
        } else {
            ecoType = 5; // Bấm lần 1: Mở không gian System Behavior
            ecoNav.setVisibility(View.GONE); // Che lấp (giải phóng GPU) 3 nút bên dưới
        }
        updateFabVisibility();
        renderEcosystem();
    });
    btnIntents.setOnClickListener(v -> { ecoType = 0; ecoNav.setVisibility(View.VISIBLE); updateFabVisibility(); renderEcosystem(); });
    btnTiles.setOnClickListener(v -> { ecoType = 1; ecoNav.setVisibility(View.VISIBLE); updateFabVisibility(); renderEcosystem(); });
    btnMacros.setOnClickListener(v -> { ecoType = 2; ecoNav.setVisibility(View.VISIBLE); updateFabVisibility(); renderEcosystem(); });
    btnTiles.setOnClickListener(v -> { ecoType = 1; updateFabVisibility(); renderEcosystem(); });
    btnMacros.setOnClickListener(v -> { ecoType = 2; updateFabVisibility(); renderEcosystem(); });
}
    // ==================== DANH SÁCH ĐỘNG (KHÔNG GIỚI HẠN SỐ LƯỢNG) ====================
// Thay cho kiểu "i1_.. i15_" cố định — dùng JSON array chứa list các ID (UUID rút gọn).
// Mỗi item vẫn lưu field riêng theo prefix "intent_<id>_..." như cũ để không phải
// đổi hết logic đọc/ghi field, chỉ đổi cách LIỆT KÊ và cách SINH ID MỚI.
private List<String> getDynamicIds(String listKey) {
    String csv = prefs.getString(listKey, "");
    List<String> out = new ArrayList<>();
    if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
    return out;
}
private String addDynamicId(String listKey) {
    String id = java.util.UUID.randomUUID().toString().substring(0, 8);
    List<String> ids = getDynamicIds(listKey);
    ids.add(id);
    prefs.edit().putString(listKey, TextUtils.join(",", ids)).apply();
    return id;
}
private void removeDynamicId(String listKey, String id) {
    List<String> ids = getDynamicIds(listKey);
    ids.remove(id);
    prefs.edit().putString(listKey, TextUtils.join(",", ids)).apply();
}
    private void renderEcosystem() {
    ecoContainer.removeAllViews();
   if (ecoType == 0 || ecoType == 1 || ecoType == 2) {
    String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
    String prefixBase = ecoType == 0 ? "intent_" : (ecoType == 1 ? "tilev2_" : "macro_");
    List<String> ids = getDynamicIds(listKey);

    LinearLayout currentRow = null;
    int count = 0;

    for (String id : ids) {
        // Render 3 Card trên 1 hàng (3 cột)
        if (count % 3 == 0) {
            currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            ecoContainer.addView(currentRow);
        }

        String name = ecoType == 0 ? prefs.getString(prefixBase+id+"_name", "Intent")
                    : ecoType == 1 ? prefs.getString(prefixBase+id+"_label", "Tile")
                    : prefs.getString(prefixBase+id+"_name", "Macro");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#202124", 20f));
        card.setPadding(15, 20, 15, 20);
        // Tối ưu margin cho 3 cột trên Pixel 2XL
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(6, 6, 6, 6);
        card.setLayoutParams(lp);

        // Hàng 1: Tên và Nút Switch
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        
        // --- [CODE MỚI THAY THẾ - TỐI ƯU PIXEL 2 XL] ---
TextView tvTitle = new TextView(this);
tvTitle.setText(name);
tvTitle.setTextColor(Color.WHITE);
tvTitle.setTextSize(14f);
tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
tvTitle.setMaxLines(1);
tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

Switch swOn = new Switch(this);
swOn.setChecked(prefs.getBoolean(prefixBase + id + "_en", true));
swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean(prefixBase + id + "_en", chk).apply());
swOn.setScaleX(0.85f); swOn.setScaleY(0.85f);

r1.addView(tvTitle); r1.addView(swOn);

// YÊU CẦU 2: Thêm hàng giao diện quản lý Quick Settings Tile (Chỉ hiện khi ở tab QS TILES)
if (ecoType == 1) {
    LinearLayout qsRow = new LinearLayout(this);
    qsRow.setOrientation(LinearLayout.HORIZONTAL);
    qsRow.setGravity(Gravity.CENTER_VERTICAL);
    qsRow.setPadding(0, 8, 0, 4);

    // Kiểm tra Data Pack này đã được gán vào Slot QS nào chưa (Zero-I/O, quét từ cache)
    int boundSlot = -1;
    for (int s = 1; s <= 30; s++) {
        if (prefs.getString("tile_slot_" + s + "_id", "").equals(id)) {
            boundSlot = s;
            break;
        }
    }

    CheckBox cbShowTile = new CheckBox(this);
    cbShowTile.setText("Hiện QS ");
    cbShowTile.setTextColor(Color.parseColor("#00E5FF"));
    cbShowTile.setTextSize(11.5f);
    cbShowTile.setChecked(prefs.getBoolean("tile_active_" + id, false));
    cbShowTile.setOnCheckedChangeListener((vw, chk) -> {
        prefs.edit().putBoolean("tile_active_" + id, chk).apply();
        Toast.makeText(this, chk ? "Đã ghim Tile lên Quick Settings" : "Đã ẩn khỏi Quick Settings", Toast.LENGTH_SHORT).show();
    });

    TextView tvQsStatus = new TextView(this);
    tvQsStatus.setTextSize(10.5f);
    if (boundSlot != -1) {
        tvQsStatus.setText("★ [Đã đưa lên Slot " + boundSlot + "]");
        tvQsStatus.setTextColor(Color.parseColor("#00E5FF")); // Màu Neon sáng rõ
    } else {
        tvQsStatus.setText("☆ [Chưa đưa lên QS]");
        tvQsStatus.setTextColor(Color.parseColor("#777777")); // Màu xám tối đỡ tốn LED OLED
    }
    tvQsStatus.setGravity(Gravity.RIGHT);
    tvQsStatus.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    qsRow.addView(cbShowTile);
    qsRow.addView(tvQsStatus);
    card.addView(qsRow, 1); // Chèn ngay dưới hàng tên (r1), trên nút Copy (r2)
}
// --- [KẾT THÚC CODE MỚI] ---
// Hàng 2: Nút Copy chuẩn hóa chống lẹm cho màn hình 18:9
LinearLayout r2 = new LinearLayout(this);
r2.setOrientation(LinearLayout.HORIZONTAL);
r2.setPadding(0, 12, 0, 0);
final String finalId = id; final int finalType = ecoType;

Button btnCopy = new Button(this); btnCopy.setText("COPY");
btnCopy.setBackground(getRounded("#303134", 14f));
btnCopy.setTextColor(Color.WHITE);
btnCopy.setTextSize(12.5f); // Phóng to +1.5 đơn vị (từ 11 lên 12.5sp)
btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
btnCopy.setPadding(10, 12, 10, 12);
// Loại bỏ fix height 95 dễ gây lẹm chữ, chuyển sang WRAP_CONTENT kèm MinHeight
LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
cpLp.setMargins(0, 0, 0, 0); btnCopy.setLayoutParams(cpLp);
btnCopy.setMinimumHeight(88);
btnCopy.setOnClickListener(v -> {
    String newId = addDynamicId(listKey);
            prefs.edit().putString(prefixBase+newId+"_name", name + " Copy").apply();
            if (finalType == 0) openIntentEditorV2(newId);
            else if (finalType == 1) openTileEditorV2(newId);
            else openMacroEditorV2(newId);
        });

        // Xóa nút Sửa
        r2.addView(btnCopy);
        card.addView(r1); card.addView(r2);

        // THUẬT TOÁN UX: CHẠM 1 LẦN -> MỞ EDIT DIALOG
        card.setOnClickListener(v -> {
            if (finalType == 0) openIntentEditorV2(finalId);
            else if (finalType == 1) openTileEditorV2(finalId);
            else openMacroEditorV2(finalId);
        });

        // CHẠM GIỮ -> XÓA
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(T("Delete?", "Xóa?"))
                .setPositiveButton("XÓA", (d,w) -> {
                    removeDynamicId(listKey, finalId);
                    renderEcosystem();
                }).setNegativeButton("HỦY", null).show();
            return true;
        });

        currentRow.addView(card); count++;
    }
    
    // Đệm thêm view rỗng nếu hàng cuối không đủ 3 thẻ
    while (count % 3 != 0 && currentRow != null) {
        View dummy = new View(this);
        dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        currentRow.addView(dummy);
        count++;
    }
} else if (ecoType == 3) {
    // Không gian Storage: Nút Scan đã chuyển xuống FAB, chỉ giữ hiển thị text
    long lastScanTs = prefs.getLong("storage_scan_ts", 0);
    TextView tvInfo = new TextView(this);
    tvInfo.setTextColor(Color.parseColor("#9AA0A6")); tvInfo.setPadding(0,0,0,20);
    tvInfo.setText(lastScanTs == 0 ? T("Chưa quét lần nào", "Chưa quét lần nào")
            : T("Lần quét gần nhất: ", "Lần quét gần nhất: ") +
            android.text.format.DateFormat.format("HH:mm dd/MM", lastScanTs));
    ecoContainer.addView(tvInfo);
    renderCachedStorageList();
} else if (ecoType == 4) {
    TextView tvNote = new TextView(this);
    tvNote.setText("Ghi âm sẽ tự dừng nếu phát hiện Quay màn hình hoặc app khác đang dùng mic.\nFile lưu tại: Music/EdgeBar - mở bằng Files by Google.");
    tvNote.setTextColor(Color.parseColor("#9AA0A6")); tvNote.setTextSize(12);
    tvNote.setPadding(0, 20, 0, 0);
    ecoContainer.addView(tvNote);
} else if (ecoType == 5) {
    // KHÔNG GIAN SYSTEM BEHAVIOR: Chỉ sinh View khi bấm vào nút, Zero-RAM khi ở tab khác
    LinearLayout secSys = new LinearLayout(this);
    secSys.setOrientation(LinearLayout.VERTICAL);
    secSys.addView(createSectionTitle(T("SYSTEM BEHAVIOR", "HÀNH VI HỆ THỐNG (Zero-RAM Overhead)")));
    
    CheckBox cbKbd = new CheckBox(this); cbKbd.setText(T("Auto-hide on Keyboard", "Tự ẩn khi hiện Bàn Phím"));
    cbKbd.setTextColor(Color.WHITE); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true));
    cbKbd.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("avoid_kbd", c).apply());
    secSys.addView(cbKbd);

    secSys.addView(createSectionTitle("APP LIST (Quản lý ứng dụng)"));
    LinearLayout appListRow = new LinearLayout(this);
    appListRow.setOrientation(LinearLayout.HORIZONTAL);
    Button btnPickBlacklist = new Button(this);
    btnPickBlacklist.setText("🚫 BLACKLIST"); btnPickBlacklist.setBackground(getRounded("#D32F2F", 20f)); btnPickBlacklist.setTextColor(Color.WHITE);
    btnPickBlacklist.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    btnPickBlacklist.setOnClickListener(v -> showPanelMultiPicker("blacklist", true));
    
    Button btnPickLocklist = new Button(this);
    btnPickLocklist.setText("🔒 LOCKLIST"); btnPickLocklist.setBackground(getRounded("#00E5FF", 20f)); btnPickLocklist.setTextColor(Color.BLACK);
    LinearLayout.LayoutParams lpLock = new LinearLayout.LayoutParams(0, -2, 1f); lpLock.setMargins(15, 0, 0, 0);
    btnPickLocklist.setLayoutParams(lpLock);
    btnPickLocklist.setOnClickListener(v -> showAppPickerDialog());
    
    appListRow.addView(btnPickBlacklist); appListRow.addView(btnPickLocklist);
    secSys.addView(appListRow);
    ecoContainer.addView(wrapCard(secSys));

    // Thẻ YTDLnis
    addYTDLDesign(ecoContainer);

    // 2 Nút Storage / Ghi âm: Nền xám #202124 giống YTDLnis, Chữ màu xanh #00E5FF
    LinearLayout toolRow = new LinearLayout(this);
    toolRow.setOrientation(LinearLayout.HORIZONTAL);
    toolRow.setPadding(0, 10, 0, 30);
    
    Button btnStorage = new Button(this); btnStorage.setText("💾 STORAGE");
    btnStorage.setBackground(getRounded("#202124", 30f)); btnStorage.setTextColor(Color.parseColor("#00E5FF")); btnStorage.setTextSize(13.5f);
    btnStorage.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    btnStorage.setOnClickListener(v -> { ecoType = 3; updateFabVisibility(); renderEcosystem(); });
    
    Button btnRecorder = new Button(this); btnRecorder.setText("🎙️ GHI ÂM");
    btnRecorder.setBackground(getRounded("#202124", 30f)); btnRecorder.setTextColor(Color.parseColor("#00E5FF")); btnRecorder.setTextSize(13.5f);
    LinearLayout.LayoutParams lpRec = new LinearLayout.LayoutParams(0, -2, 1f); lpRec.setMargins(15, 0, 0, 0);
    btnRecorder.setLayoutParams(lpRec);
    btnRecorder.setOnClickListener(v -> { ecoType = 4; updateFabVisibility(); renderEcosystem(); });
    
    toolRow.addView(btnStorage); toolRow.addView(btnRecorder);
    ecoContainer.addView(toolRow);
}
}
    private String getActionLabel(String actionKey) {
        for (int i=0; i<ACT_KEYS.length; i++) {
            if (ACT_KEYS[i].equals(actionKey)) return ACT_LABS[i];
        }
        return actionKey;
    }
    // THÊM MỚI — dùng khi cần hiện TÊN APP thay vì nhãn tĩnh "Mở Ứng dụng"
private String getActionLabelSmart(String actionKey, String launchPkg) {
    if ("LAUNCH_APP".equals(actionKey)) {
        return "🚀 " + getAppLabelCached(launchPkg);
    }
    return getActionLabel(actionKey);
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
private static final int[] ACT_AUTO_ICON = {
    // NONE,BACK,HOME,RECENTS,SCREEN_OFF,FLASH,POWER_DIALOG,VOLUME,
    // SCREENSHOT,CAMERA,NOTIFICATIONS,TOGGLE_ACC,TOGGLE_OVERLAY,
    // TOGGLE_MORSE,YTDL_DOWNLOAD,VOICE_RECORD,LAUNCH_APP
    0, 4, 4, 5, 2, 18, 10, 7, 17, 3, 9, 2, 16, 2, 8, 6, 19,
    11,11,11,11,11,11,11,11,11,11,11,11,11,11,11,
    10,10,10,10,10
};
// Pool 20 icon: index phải khớp với ICON_POOL trong Tile1..15.java
private static final String[] TILE_ICON_NAMES = {
    "La Bàn 🧭", "Kính Lúp 🔍", "Ổ Khóa 🔒", "Camera 📷", "Home 🏠",
    "Play ▶", "Micro 🎤", "Âm Lượng 🔊", "Chia Sẻ 📤", "Thông Tin ℹ️",
    "Cài Đặt ⚙️", "Gửi 📨", "Chỉnh Sửa ✏️", "Xóa 🗑️", "Thêm ➕",
    "Đóng ✖️", "Upload ⬆️", "Xem 👁️", "Yêu Thích ⭐", "Vị Trí 📍"
};

private void openTileEditor(int idx) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(40, 120, 40, 40);

    ScrollView scroll = new ScrollView(this);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content);
    root.addView(scroll);
    final int finalIdx = idx;

    // --- TÊN TILE ---
    TextView tvLabelHint = new TextView(this);
    tvLabelHint.setText("Tên hiển thị trên QS Tile:");
    tvLabelHint.setTextColor(Color.parseColor("#00E5FF"));
    tvLabelHint.setPadding(0, 0, 0, 10);
    content.addView(tvLabelHint);

    EditText etLabel = createEcoInput(
        "VD: Đèn Pin, Chụp màn...",
        idx > 0 ? prefs.getString("tile_" + idx + "_label", "Tile " + idx) : ""
    );
    content.addView(etLabel);

    // --- ACTION ---
    TextView tvActionHint = new TextView(this);
    tvActionHint.setText("\nHành động:");
    tvActionHint.setTextColor(Color.parseColor("#00E5FF"));
    content.addView(tvActionHint);

    Spinner sp = createSpinner();
    sp.setAdapter(new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, ACT_LABS));
    if (idx > 0) {
        String cur = prefs.getString("tile_" + idx + "_act", "NONE");
        for (int i = 0; i < ACT_KEYS.length; i++)
            if (ACT_KEYS[i].equals(cur)) { sp.setSelection(i); break; }
    }
    content.addView(sp);

    // --- ICON ---
    TextView tvIconHint = new TextView(this);
    tvIconHint.setText("\nIcon:");
    tvIconHint.setTextColor(Color.parseColor("#00E5FF"));
    content.addView(tvIconHint);

    // Checkbox auto-icon
    CheckBox cbAutoIcon = new CheckBox(this);
    cbAutoIcon.setText("🤖 Tự chọn icon theo hành động");
    cbAutoIcon.setTextColor(Color.WHITE);
    cbAutoIcon.setChecked(idx <= 0 || prefs.getBoolean("tile_" + idx + "_auto_icon", true));
    content.addView(cbAutoIcon);

    // === APP BEAM: hiện khi action = LAUNCH_APP ===
    LinearLayout rowTileApp = new LinearLayout(this);
    rowTileApp.setOrientation(LinearLayout.HORIZONTAL);
    rowTileApp.setVisibility(View.GONE);
    EditText etTileApp = createEcoInput("Package (com.zalo...)",
        idx > 0 ? prefs.getString("tile_" + idx + "_launch_pkg", "") : "");
    etTileApp.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Button btnPickTileApp = new Button(this);
    btnPickTileApp.setText("📱 PICK APP");
    btnPickTileApp.setBackground(getRounded("#00E5FF", 20f));
    btnPickTileApp.setTextColor(Color.BLACK);
    btnPickTileApp.setOnClickListener(v -> showSingleAppPickerDialog(etTileApp));
    rowTileApp.addView(etTileApp);
    rowTileApp.addView(btnPickTileApp);
    content.addView(rowTileApp);

    Spinner spIcon = createSpinner();
    spIcon.setAdapter(new ArrayAdapter<>(this,
        android.R.layout.simple_spinner_dropdown_item, TILE_ICON_NAMES));
    int savedIconIdx = idx > 0 ? prefs.getInt("tile_" + idx + "_icon_idx", 0) : 0;
    spIcon.setSelection(savedIconIdx);
    // Spinner icon ẩn khi auto
    spIcon.setVisibility(cbAutoIcon.isChecked() ? View.GONE : View.VISIBLE);
    cbAutoIcon.setOnCheckedChangeListener((v, chk) ->
        spIcon.setVisibility(chk ? View.GONE : View.VISIBLE));

    // Auto-update icon spinner khi đổi action
    sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            boolean isLaunchApp = pos < ACT_KEYS.length && ACT_KEYS[pos].equals("LAUNCH_APP");
            rowTileApp.setVisibility(isLaunchApp ? View.VISIBLE : View.GONE);
            if (cbAutoIcon.isChecked()) {
                int autoIdx = (pos < ACT_AUTO_ICON.length) ? ACT_AUTO_ICON[pos] : 0;
                spIcon.setSelection(autoIdx);
                if (etLabel.getText().toString().trim().isEmpty() && pos < ACT_LABS.length) {
                    etLabel.setHint("Gợi ý: " + ACT_LABS[pos]);
                }
            }
        }
        public void onNothingSelected(AdapterView<?> p) {}
    });
    // Gọi 1 lần lúc mở dialog để set đúng trạng thái ban đầu nếu đang edit tile LAUNCH_APP sẵn có
    if (idx > 0 && prefs.getString("tile_" + idx + "_act", "NONE").equals("LAUNCH_APP")) {
        rowTileApp.setVisibility(View.VISIBLE);
    }
    content.addView(spIcon);

    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setPadding(0, 40, 0, 0);
    Button bCancel = new Button(this);
    bCancel.setText("HỦY");
    bCancel.setBackground(getRounded("#333333", 20f));
    bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Button bSave = new Button(this);
    bSave.setText("LƯU");
    bSave.setBackground(getRounded("#4CAF50", 20f));
    bSave.setTextColor(Color.WHITE);
    bSave.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    footer.addView(bCancel);
    footer.addView(bSave);
    root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        String labelText = etLabel.getText().toString().trim();
        boolean autoIcon = cbAutoIcon.isChecked();
        int actionPos = sp.getSelectedItemPosition();
        int iconIdx = autoIcon
            ? ((actionPos < ACT_AUTO_ICON.length) ? ACT_AUTO_ICON[actionPos] : 0)
            : spIcon.getSelectedItemPosition();
        // Tên mặc định = tên action nếu để trống
        if (labelText.isEmpty() && actionPos < ACT_LABS.length) {
            labelText = ACT_LABS[actionPos];
        }
        final String finalLabel = labelText;
        final int finalIconIdx = iconIdx;

        if (finalIdx == 0) {
            int newIdx = -1;
            for (int i = 1; i <= 15; i++)
                if (prefs.getString("tile_" + i + "_act", "NONE").equals("NONE")) {
                    newIdx = i; break;
                }
            if (newIdx == -1) {
                Toast.makeText(this, "Đã đủ 15 QS Tile!", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                .putString("tile_" + newIdx + "_act", ACT_KEYS[actionPos])
                .putString("tile_" + newIdx + "_label", finalLabel)
                .putInt("tile_" + newIdx + "_icon_idx", finalIconIdx)
                .putBoolean("tile_" + newIdx + "_auto_icon", autoIcon)
                .putString("tile_" + newIdx + "_launch_pkg", etTileApp.getText().toString())
                .apply();
        } else {
            prefs.edit()
                .putString("tile_" + finalIdx + "_act", ACT_KEYS[actionPos])
                .putString("tile_" + finalIdx + "_label", finalLabel)
                .putInt("tile_" + finalIdx + "_icon_idx", finalIconIdx)
                .putBoolean("tile_" + finalIdx + "_auto_icon", autoIcon)
                .putString("tile_" + finalIdx + "_launch_pkg", etTileApp.getText().toString())
                .apply();
        }
        sendBroadcast(new Intent("com.manhmoc.edgebar.TILE_CONFIG_CHANGED"));
        renderEcosystem();
        d.dismiss();
    });
    d.setContentView(root);
    d.show();
}
    // THÊM 2 hàm sau openTileEditor():
private void runDeepStorageScan() {
    Toast.makeText(this, "Đang quét, chờ vài giây...", Toast.LENGTH_SHORT).show();
    new Thread(() -> {
        List<StorageScanner.AppStorageInfo> list = StorageScanner.scanAll(this);
        // Nén xuống JSON gọn, chỉ lưu top 50 app nặng nhất để tiết kiệm RAM/dung lượng prefs
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (int i=0; i<Math.min(50, list.size()); i++) {
                StorageScanner.AppStorageInfo a = list.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("pkg", a.pkg); o.put("label", a.label);
                o.put("bytes", a.totalBytes); o.put("island", a.isIsland);
                arr.put(o);
            }
            prefs.edit().putString("storage_scan_data", arr.toString())
                .putLong("storage_scan_ts", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {}
        runOnUiThread(() -> { renderEcosystem(); Toast.makeText(this, "Quét xong!", Toast.LENGTH_SHORT).show(); });
    }).start();
}

private void renderCachedStorageList() {
    try {
        String json = prefs.getString("storage_scan_data", "");
        if (json.isEmpty()) return;
        org.json.JSONArray arr = new org.json.JSONArray(json);
        
        LinearLayout currentRow = null;
        for (int i = 0; i < arr.length(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                ecoContainer.addView(currentRow);
            }
            
            org.json.JSONObject o = arr.getJSONObject(i);
            String subtitle = StorageScanner.formatSize(o.getLong("bytes")) + (o.getBoolean("island") ? " [Island]" : "");
            
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getRounded("#202124", 20f));
            card.setPadding(30, 24, 30, 24);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(10, 10, 10, 10);
            card.setLayoutParams(lp);
            
            TextView tvTitle = new TextView(this); 
            tvTitle.setText(o.getString("label"));
            tvTitle.setTextColor(Color.parseColor("#E8EAED")); 
            tvTitle.setTextSize(13);
            
            TextView tvSub = new TextView(this); 
            tvSub.setText(subtitle);
            tvSub.setTextColor(Color.parseColor("#9AA0A6")); 
            tvSub.setTextSize(11); tvSub.setPadding(0, 5, 0, 0);
            
            card.addView(tvTitle); card.addView(tvSub);
            currentRow.addView(card);
        }
        
        if (arr.length() % 2 != 0 && currentRow != null) { 
            View dummy = new View(this);
            dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            currentRow.addView(dummy); 
        }
    } catch (Exception ignored) {}
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
    private void openIntentEditorV2(String id) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
    ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
    LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
    EditText etName = createEcoInput("Tên gợi nhớ", prefs.getString("intent_"+id+"_name",""));
    EditText etAct = createEcoInput("Action", prefs.getString("intent_"+id+"_act",""));
    EditText etPkg = createEcoInput("Package", prefs.getString("intent_"+id+"_pkg",""));
    EditText etCls = createEcoInput("Class Name", prefs.getString("intent_"+id+"_cls",""));
    EditText etData = createEcoInput("Data URI", prefs.getString("intent_"+id+"_data",""));
    EditText etCat = createEcoInput("Categories", prefs.getString("intent_"+id+"_cat",""));
    EditText etFlags = createEcoInput("Flags", prefs.getString("intent_"+id+"_flags",""));
    CheckBox cbBr = new CheckBox(this); cbBr.setText("Send as Broadcast"); cbBr.setTextColor(Color.WHITE); cbBr.setChecked(prefs.getBoolean("intent_"+id+"_br", true));
    content.addView(etName); content.addView(etAct); content.addView(etPkg); content.addView(etCls); content.addView(etData); content.addView(etCat); content.addView(etFlags); content.addView(cbBr);
    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
    Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        prefs.edit()
            .putString("intent_"+id+"_name", etName.getText().toString())
            .putString("intent_"+id+"_act", etAct.getText().toString())
            .putString("intent_"+id+"_pkg", etPkg.getText().toString())
            .putString("intent_"+id+"_cls", etCls.getText().toString())
            .putString("intent_"+id+"_data", etData.getText().toString())
            .putString("intent_"+id+"_cat", etCat.getText().toString())
            .putString("intent_"+id+"_flags", etFlags.getText().toString())
            .putBoolean("intent_"+id+"_br", cbBr.isChecked())
            .apply();
        renderEcosystem(); d.dismiss();
    });
    d.setContentView(root); d.show();
}

private void openMacroEditorV2(String id) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
    ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
    LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);
    EditText etName = createEcoInput("Tên gợi nhớ", prefs.getString("macro_"+id+"_name",""));
    EditText etSvcs = createEcoInput("Services (com.pkg/.Class)", prefs.getString("macro_"+id+"_svcs",""));
    content.addView(etName); content.addView(etSvcs);
    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
    Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        prefs.edit()
            .putString("macro_"+id+"_name", etName.getText().toString())
            .putString("macro_"+id+"_svcs", etSvcs.getText().toString())
            .apply();
        renderEcosystem(); d.dismiss();
    });
    d.setContentView(root); d.show();
}

private void openTileEditorV2(String id) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(40,120,40,40);
    ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
    LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); root.addView(scroll);

    TextView tvLabelHint = new TextView(this); tvLabelHint.setText("Tên hiển thị trên QS Tile:"); tvLabelHint.setTextColor(Color.parseColor("#00E5FF")); tvLabelHint.setPadding(0,0,0,10);
    content.addView(tvLabelHint);
    EditText etLabel = createEcoInput("VD: Đèn Pin, Chụp màn...", prefs.getString("tilev2_"+id+"_label", "Tile"));
    content.addView(etLabel);

    TextView tvActionHint = new TextView(this); tvActionHint.setText("\nHành động:"); tvActionHint.setTextColor(Color.parseColor("#00E5FF"));
    content.addView(tvActionHint);
    Spinner sp = createSpinner();
    sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS));
    String curAct = prefs.getString("tilev2_"+id+"_act", "NONE");
    for (int i = 0; i < ACT_KEYS.length; i++) if (ACT_KEYS[i].equals(curAct)) { sp.setSelection(i); break; }
    content.addView(sp);

    LinearLayout rowTileApp = new LinearLayout(this); rowTileApp.setOrientation(LinearLayout.HORIZONTAL);
    rowTileApp.setVisibility(curAct.equals("LAUNCH_APP") ? View.VISIBLE : View.GONE);
    EditText etTileApp = createEcoInput("Package (com.zalo...)", prefs.getString("tilev2_"+id+"_launch_pkg", ""));
    etTileApp.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Button btnPickTileApp = new Button(this); btnPickTileApp.setText("📱 PICK APP");
    btnPickTileApp.setBackground(getRounded("#00E5FF", 20f)); btnPickTileApp.setTextColor(Color.BLACK);
    btnPickTileApp.setOnClickListener(v -> showSingleAppPickerDialog(etTileApp));
    rowTileApp.addView(etTileApp); rowTileApp.addView(btnPickTileApp);
    content.addView(rowTileApp);
    sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        public void onItemSelected(AdapterView<?> p, View v, int pos, long id2) {
            rowTileApp.setVisibility(pos < ACT_KEYS.length && ACT_KEYS[pos].equals("LAUNCH_APP") ? View.VISIBLE : View.GONE);
        }
        public void onNothingSelected(AdapterView<?> p) {}
    });

    // Gán vào 1 trong 30 slot QS Tile tĩnh — chỉ hiện slot còn trống hoặc đang thuộc về chính id này
    TextView tvSlotHint = new TextView(this); tvSlotHint.setText("\nGán vào QS Tile số:"); tvSlotHint.setTextColor(Color.parseColor("#00E5FF"));
    content.addView(tvSlotHint);
    List<Integer> freeSlots = new ArrayList<>();
    int curSlotPos = -1;
    for (int s = 1; s <= 30; s++) {
        String occupied = prefs.getString("tile_slot_"+s+"_id", "");
        if (occupied.isEmpty() || occupied.equals(id)) {
            if (occupied.equals(id)) curSlotPos = freeSlots.size();
            freeSlots.add(s);
        }
    }
    String[] slotNames = new String[freeSlots.size()];
    for (int i = 0; i < freeSlots.size(); i++) slotNames[i] = "Slot " + freeSlots.get(i);
    Spinner spSlot = createSpinner();
    spSlot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, slotNames));
    if (curSlotPos >= 0) spSlot.setSelection(curSlotPos);
    content.addView(spSlot);

    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,40,0,0);
    Button bCancel = new Button(this); bCancel.setText("HỦY"); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText("LƯU"); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); bSave.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        if (freeSlots.isEmpty()) { Toast.makeText(this, "Đã hết Slot QS Tile (30/30)!", Toast.LENGTH_SHORT).show(); return; }
        int chosenSlot = freeSlots.get(spSlot.getSelectedItemPosition());
        String labelText = etLabel.getText().toString().trim();
        int actionPos = sp.getSelectedItemPosition();
        if (labelText.isEmpty()) labelText = ACT_LABS[actionPos];
        prefs.edit()
            .putString("tilev2_"+id+"_label", labelText)
            .putString("tilev2_"+id+"_act", ACT_KEYS[actionPos])
            .putString("tilev2_"+id+"_launch_pkg", etTileApp.getText().toString())
            .putString("tile_slot_"+chosenSlot+"_id", id)
            .apply();
        sendBroadcast(new Intent("com.manhmoc.edgebar.TILE_CONFIG_CHANGED"));
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
    pageDesign.addView(createSectionTitle(T("CORE DESIGN (COLOR/SIZE)", "THIẾT KẾ CỐT LÕI (MÀU/KÍCH THƯỚC)")));
    LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL);
    btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); 
btnEditHome = new Button(this); btnEditHome.setText("HOMEB"); LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP); 
btnEditHomacc = new Button(this); btnEditHomacc.setText("HOMACC"); btnEditHomacc.setLayoutParams(mP);
btnEditMorse = new Button(this); btnEditMorse.setText("MORSOS"); btnEditMorse.setLayoutParams(mP); 
btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0);
        
        btnEditLock.setOnClickListener(v -> { designTabState=0; refreshPreview(); updateVisTabs(); renderSliders(); }); 
btnEditHome.setOnClickListener(v -> { designTabState=1; refreshPreview(); updateVisTabs(); renderSliders(); }); 
btnEditHomacc.setOnClickListener(v -> { designTabState=4; refreshPreview(); updateVisTabs(); renderSliders(); });
btnEditMorse.setOnClickListener(v -> { designTabState=2; refreshPreview(); updateVisTabs(); renderSliders(); }); 
btnEditAnim.setOnClickListener(v -> { designTabState=3; refreshPreview(); updateVisTabs(); renderSliders(); });
toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditHomacc); toggleRow.addView(btnEditMorse); toggleRow.addView(btnEditAnim);
        pageDesign.addView(toggleRow);

        // HÀNG 2 — chỉ có PANEL, đặt riêng hàng để dễ mở rộng thêm tab về sau
        // --- [CODE MỚI THAY THẾ - TỐI ƯU PIXEL 2 XL] ---
// HÀNG 2 - PANEL và CẤU HÌNH DESIGN (Chia đôi tỷ lệ 1:1 phẳng)
LinearLayout toggleRow2 = new LinearLayout(this);
toggleRow2.setOrientation(LinearLayout.HORIZONTAL);
toggleRow2.setPadding(0, 15, 0, 0);

btnEditPanel = new Button(this); btnEditPanel.setText("PANEL");
LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, -2, 1f);
pLp.setMargins(0, 0, 5, 0);
btnEditPanel.setLayoutParams(pLp);
btnEditPanel.setOnClickListener(v -> { designTabState = 5; refreshPreview(); updateVisTabs(); renderSliders(); });

// YÊU CẦU 3: Thêm nút thẻ không gian "Cấu hình Design"
Button btnEditDesignConfig = new Button(this);
btnEditDesignConfig.setText("CẤU HÌNH DESIGN");
btnEditDesignConfig.setTag("btnEditDesignConfig");
LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, -2, 1f);
cLp.setMargins(5, 0, 0, 0);
btnEditDesignConfig.setLayoutParams(cLp);
btnEditDesignConfig.setOnClickListener(v -> { designTabState = 6; refreshPreview(); updateVisTabs(); renderSliders(); });

toggleRow2.addView(btnEditPanel);
toggleRow2.addView(btnEditDesignConfig);
pageDesign.addView(toggleRow2);
pageDesign.addView(designSliderContainer);
btnEditHome.performClick();
// --- [KẾT THÚC CODE MỚI] ---
    }
// --- [CODE MỚI THAY THẾ - TỐI ƯU PIXEL 2 XL] ---
private void updateVisTabs() {
    btnEditLock.setBackground(getRounded(designTabState == 0 ? "#00E5FF" : "#222222", 20f));
    btnEditLock.setTextColor(designTabState == 0 ? Color.BLACK : Color.WHITE);
    btnEditHome.setBackground(getRounded(designTabState == 1 ? "#00E5FF" : "#222222", 20f));
    btnEditHome.setTextColor(designTabState == 1 ? Color.BLACK : Color.WHITE);
    btnEditHomacc.setBackground(getRounded(designTabState == 4 ? "#7C4DFF" : "#333333", 20f));
    btnEditHomacc.setTextColor(Color.WHITE);
    btnEditMorse.setBackground(getRounded(designTabState == 2 ? "#00E5FF" : "#222222", 20f));
    btnEditMorse.setTextColor(designTabState == 2 ? Color.BLACK : Color.WHITE);
    btnEditAnim.setBackground(getRounded(designTabState == 3 ? "#00E5FF" : "#222222", 20f));
    btnEditAnim.setTextColor(designTabState == 3 ? Color.BLACK : Color.WHITE);
    btnEditPanel.setBackground(getRounded(designTabState == 5 ? "#00E5FF" : "#222222", 20f));
    btnEditPanel.setTextColor(designTabState == 5 ? Color.BLACK : Color.WHITE);
    
    // YÊU CẦU 3: Cập nhật màu nút CẤU HÌNH DESIGN (designTabState == 6)
    Button btnCfg = pageDesign.findViewWithTag("btnEditDesignConfig");
    if (btnCfg != null) {
        btnCfg.setBackground(getRounded(designTabState == 6 ? "#00E5FF" : "#222222", 20f));
        btnCfg.setTextColor(designTabState == 6 ? Color.BLACK : Color.WHITE);
    }
}

private void renderSliders() {
    designSliderContainer.removeAllViews();
    if (designTabState == 5) { renderPanelDesign(); return; }
    
    // YÊU CẦU 3: Điều hướng sang không gian Cấu hình Design (Zero-RAM Overhead)
    if (designTabState == 6) { renderDesignConfigSpace(); return; }
    
    // ===== TAB HOMACC (designTabState == 4)
// --- [KẾT THÚC CODE MỚI] ---
    // ===== TAB HOMACC (designTabState == 4) =====
    if (designTabState == 4) {
        String prefix = "homacc_";
        designSliderContainer.addView(createSectionTitle("🟣 HOMACC - OVERLAY TRỢ NĂNG HOME"));

        // [THÊM] thay bằng nút kết nối QsAccHomeTile thật sự:
LinearLayout homaccCtrlRow = new LinearLayout(this);
homaccCtrlRow.setOrientation(LinearLayout.HORIZONTAL);
LinearLayout.LayoutParams hcLp = new LinearLayout.LayoutParams(-1,-2);
hcLp.setMargins(0,0,0,20);
homaccCtrlRow.setLayoutParams(hcLp);

Button btnToggleHomacc = new Button(this);
// Hiển thị trạng thái thực tế của AccHome
boolean accHomeOn = AccessibleHomeService.isRunning;
btnToggleHomacc.setText(accHomeOn ? "🟣 TẮT HOMACC" : "🟣 BẬT HOMACC");
btnToggleHomacc.setBackground(getRounded(accHomeOn ? "#7C4DFF" : "#333333", 20f));
btnToggleHomacc.setTextColor(Color.WHITE);
btnToggleHomacc.setPadding(0,30,0,30);
btnToggleHomacc.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

btnToggleHomacc.setOnClickListener(v -> {
    // Kết nối trực tiếp với logic QsAccHomeTile — KHÔNG qua SYNC_STATE
    if (!AccessibleHomeService.isRunning) {
        // Kiểm tra Accessibility đã bật chưa
        String accSvcs = android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean accOn = accSvcs != null && accSvcs.contains(
            getPackageName() + "/" + EdgeBarService.class.getName());
        if (!accOn) {
            Toast.makeText(this,
                "Cần bật Trợ Năng trước!", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
            .putBoolean("shortcut_acc_home_on", true)
            .putBoolean("shortcut_home_on", false)
            .apply();
        sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        Intent accIntent = new Intent(this, AccessibleHomeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(accIntent);
        else startService(accIntent);
        btnToggleHomacc.setText("🟣 TẮT HOMACC");
        btnToggleHomacc.setBackground(getRounded("#7C4DFF", 20f));
    } else {
        prefs.edit()
            .putBoolean("shortcut_acc_home_on", false)
            .apply();
        sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF"));
        stopService(new Intent(this, AccessibleHomeService.class));
        sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        btnToggleHomacc.setText("🟣 BẬT HOMACC");
        btnToggleHomacc.setBackground(getRounded("#333333", 20f));
    }
});

Button btnRefreshHomacc = new Button(this);
btnRefreshHomacc.setText("🔄 REFRESH");
btnRefreshHomacc.setBackground(getRounded("#455A64", 20f));
btnRefreshHomacc.setTextColor(Color.WHITE);
btnRefreshHomacc.setPadding(0,30,0,30);
LinearLayout.LayoutParams rfLp = new LinearLayout.LayoutParams(0,-2,0.6f);
rfLp.setMargins(10,0,0,0);
btnRefreshHomacc.setLayoutParams(rfLp);
btnRefreshHomacc.setOnClickListener(v -> {
    // Vẽ lại Homacc overlay nếu Accessibility đang bật
    sendBroadcast(new Intent("com.manhmoc.edgebar.ACC_HOME_DRAW"));
    Toast.makeText(this, "Đã vẽ lại Homacc overlay!", Toast.LENGTH_SHORT).show();
});

homaccCtrlRow.addView(btnToggleHomacc);
homaccCtrlRow.addView(btnRefreshHomacc);
designSliderContainer.addView(homaccCtrlRow);

// SAU:
TextView tvHomaccLockNote = new TextView(this);
tvHomaccLockNote.setText("🔒 Homacc tự động tắt hoàn toàn khi khoá máy (cố định, không thể tuỳ chỉnh)");
tvHomaccLockNote.setTextColor(Color.parseColor("#777777"));
tvHomaccLockNote.setTextSize(12);
tvHomaccLockNote.setPadding(0, 0, 0, 20);
designSliderContainer.addView(tvHomaccLockNote);
        // EDGE BARS - copy y chang tab HOME nhưng dùng prefix "homacc_"
        designSliderContainer.addView(createSectionTitle("EDGE BARS HOMACC (5 THANH)"));
        String[] bKeys = {"r", "l", "t_r", "t_l", "t_c"};
        String[] bNames = {"Đáy phải", "Đáy trái", "Cạnh Phải", "Cạnh Trái", "Đỉnh giữa"};
        for (int i = 0; i < bKeys.length; i++) {
            LinearLayout drawerContent = new LinearLayout(this);
            drawerContent.setOrientation(LinearLayout.VERTICAL);
            drawerContent.setPadding(30,10,30,30);
            CheckBox cb = new CheckBox(this);
            cb.setText("BẬT: " + bNames[i]);
            cb.setTextColor(Color.parseColor("#7C4DFF"));
            cb.setChecked(prefs.getBoolean(prefix+bKeys[i]+"_en", false));
            final int idx = i;
            final String[] bKeysF = bKeys;
            cb.setOnCheckedChangeListener((vv,c) -> prefs.edit().putBoolean(prefix+bKeysF[idx]+"_en", c).apply());
            drawerContent.addView(cb);
            drawerContent.addView(createComboDropdown("Hiển thị", prefix+bKeys[i]+"_vis_mode",
    new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0));
drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+bKeys[i]+"_pri_mode",
    new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
drawerContent.addView(createSlider("Độ trong suốt", prefix+bKeys[i]+"_alpha", 255, 50));
            drawerContent.addView(createSlider("Chiều ngang", prefix+bKeys[i]+"_w", 3000, 300));
            drawerContent.addView(createSlider("Chiều dọc", prefix+bKeys[i]+"_h", 3000, 60));
            drawerContent.addView(createSlider("Toạ độ X", prefix+bKeys[i]+"_x", 1000, 0));
            drawerContent.addView(createSlider("Toạ độ Y", prefix+bKeys[i]+"_y", 2500, 0));
            designSliderContainer.addView(createDrawer(bNames[i], drawerContent));
        }

        // 4 CORNERS - copy y chang tab HOME nhưng dùng prefix "homacc_"
        designSliderContainer.addView(createSectionTitle("4 FRAME CORNERS HOMACC"));
        String[] cKeys = {"br", "bl", "tr", "tl"};
        String[] cNames = {"Góc đáy phải", "Góc đáy trái", "Góc đỉnh phải", "Góc đỉnh trái"};
        for (int i = 0; i < cKeys.length; i++) {
            LinearLayout drawerContent = new LinearLayout(this);
            drawerContent.setOrientation(LinearLayout.VERTICAL);
            drawerContent.setPadding(30,10,30,30);
            CheckBox cbEn = new CheckBox(this);
            cbEn.setText("BẬT: " + cNames[i]);
            cbEn.setTextColor(Color.parseColor("#7C4DFF"));
            cbEn.setChecked(prefs.getBoolean(prefix+"corner_"+cKeys[i]+"_en", false));
            final int idx = i;
            final String[] cKeysF = cKeys;
            cbEn.setOnCheckedChangeListener((vv,c) -> prefs.edit().putBoolean(prefix+"corner_"+cKeysF[idx]+"_en", c).apply());
            drawerContent.addView(cbEn);
            drawerContent.addView(createComboDropdown("Hiển thị", prefix+"corner_"+cKeys[i]+"_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình (Nháy sáng)", "Ẩn hoàn toàn (Vô hình)"}, 0));
            drawerContent.addView(createComboDropdown("Chế độ Cảm ứng", prefix+"corner_"+cKeys[i]+"_pri_mode", new String[]{"Ưu tiên Edge Bar (Khoá cứng)", "Nhường OS (Xuyên thấu)"}, 0));
            drawerContent.addView(createComboDropdown("Hình dáng Góc", prefix+"corner_"+cKeys[i]+"_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0));
            drawerContent.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix+"corner_"+cKeys[i]+"_w", 2500, 100));
            drawerContent.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix+"corner_"+cKeys[i]+"_h", 2500, 100));
            drawerContent.addView(createSlider("Di chuyển Ngang (X)", prefix+"corner_"+cKeys[i]+"_x", 2500, 0));
            drawerContent.addView(createSlider("Di chuyển Dọc (Y)", prefix+"corner_"+cKeys[i]+"_y", 2500, 0));
            drawerContent.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix+"corner_"+cKeys[i]+"_moon_w", 2500, 100));
            drawerContent.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix+"corner_"+cKeys[i]+"_moon_h", 2500, 100));
            drawerContent.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix+"corner_"+cKeys[i]+"_moon_x", 2500, 1250));
            drawerContent.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix+"corner_"+cKeys[i]+"_moon_y", 2500, 1250));
            drawerContent.addView(createSlider("Độ cong BO VIỀN (Vỏ) (1000=Thẳng)", prefix+"corner_"+cKeys[i]+"_rad", 1000, 80));
            drawerContent.addView(createSlider("Độ cong TRĂNG NON (Lõi) (1000=Thẳng)", prefix+"corner_"+cKeys[i]+"_moon_rad", 1000, 80));
            designSliderContainer.addView(createDrawer(cNames[i], drawerContent));
        }
        LinearLayout globalDrawerAcc = new LinearLayout(this);
        globalDrawerAcc.setOrientation(LinearLayout.VERTICAL);
        globalDrawerAcc.setPadding(30,10,30,30);
        globalDrawerAcc.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500));
        globalDrawerAcc.addView(createSlider("Độ mờ vùng TRĂNG NON", prefix+"corner_moon_alpha", 255, 100));
        globalDrawerAcc.addView(createSlider("Độ mờ VIỀN GÓC", prefix+"corner_stroke_alpha", 255, 200));
        globalDrawerAcc.addView(createSlider("Độ đậm viền", prefix+"corner_thick", 50, 8));
        designSliderContainer.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", globalDrawerAcc));
        return; // Thoát sớm, không chạy code tab khác
    }
    // ===== KẾT THÚC TAB HOMACC =====

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
                // [THÊM] nút kết nối QsMorseTile logic:
boolean morseCurrentOn = prefs.getBoolean("morse_mode_en", false);
Button btnTestM = new Button(this);
btnTestM.setText(morseCurrentOn ? "🔐 TẮT MORSE OS" : "🔐 BẬT MORSE OS");
btnTestM.setBackground(getRounded(morseCurrentOn ? "#E91E63" : "#FFC107", 20f));
btnTestM.setTextColor(morseCurrentOn ? Color.WHITE : Color.BLACK);
LinearLayout.LayoutParams tm = new LinearLayout.LayoutParams(0,-2,1f);
tm.setMargins(0,0,10,20);
btnTestM.setLayoutParams(tm);
btnTestM.setOnClickListener(v -> {
    // Kiểm tra Accessibility — đồng bộ điều kiện với QsMorseTile
    String accSvcs = android.provider.Settings.Secure.getString(
        getContentResolver(),
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    boolean accOn = accSvcs != null && accSvcs.contains(
        getPackageName() + "/" + EdgeBarService.class.getName());
    if (!accOn) {
        Toast.makeText(this,
            "MorseLock cần Trợ Năng đang bật!", Toast.LENGTH_SHORT).show();
        return;
    }
    // Toggle — cùng logic với QsMorseTile.onClick()
    boolean newState = !prefs.getBoolean("morse_mode_en", false);
    prefs.edit().putBoolean("morse_mode_en", newState).apply();
    sendBroadcast(new Intent("com.manhmoc.edgebar.TOGGLE_MORSE"));
    btnTestM.setText(newState ? "🔐 TẮT MORSE OS" : "🔐 BẬT MORSE OS");
    btnTestM.setBackground(getRounded(newState ? "#E91E63" : "#FFC107", 20f));
    btnTestM.setTextColor(newState ? Color.WHITE : Color.BLACK);
    Toast.makeText(this,
        newState ? "Đã bật MorseLock OS" : "Đã tắt MorseLock OS",
        Toast.LENGTH_SHORT).show();
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
 private void renderPanelDesign() {
    LinearLayout tabRow = new LinearLayout(this); tabRow.setOrientation(LinearLayout.HORIZONTAL);
    // MỚI:
Button b1 = createTabBtn("PANEL 1"), b2 = createTabBtn("PANEL 2"), b3 = createTabBtn("PANEL 3");
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,-2,1f); tlp.setMargins(6,0,6,0);
    b1.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); b2.setLayoutParams(tlp); b3.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));

    // panelBody: container RIÊNG cho nội dung panel — rebuild container này
    // thay vì gọi lại renderSliders() (nguyên nhân gây đệ quy vô hạn / ANR)
    LinearLayout panelBody = new LinearLayout(this);
    panelBody.setOrientation(LinearLayout.VERTICAL);

    View.OnClickListener pTab = v -> {
        currentPanelIdx = (v==b1) ? 1 : (v==b2) ? 2 : 3;
        stylePanelTabs(b1, b2, b3);
        buildPanelBody(panelBody);   // ← CHỈ rebuild nội dung, KHÔNG đụng renderSliders()
    };
    b1.setOnClickListener(pTab); b2.setOnClickListener(pTab); b3.setOnClickListener(pTab);
    tabRow.addView(b1); tabRow.addView(b2); tabRow.addView(b3);
    designSliderContainer.addView(tabRow);
    designSliderContainer.addView(panelBody);

    // Khởi tạo lần đầu: style tab + build nội dung, KHÔNG gọi onClick() giả lập
    stylePanelTabs(b1, b2, b3);
    buildPanelBody(panelBody);
 // --- [CODE MỚI THAY THẾ - TỐI ƯU PIXEL 2 XL] ---
TextView tvSwipeHint = new TextView(this);
tvSwipeHint.setText("" + T("Swipe panel to see more items", "Vuốt ngang panel để xem thêm ô") + "→");
tvSwipeHint.setTextColor(Color.GRAY); tvSwipeHint.setTextSize(11f);
tvSwipeHint.setGravity(Gravity.CENTER); tvSwipeHint.setPadding(0, 10, 0, 0);
panelBody.addView(tvSwipeHint);
}

// YÊU CẦU 3: Thuật toán quản lý không gian Cấu hình Design (Quản lý Data Pack)
private int currentDesignCfgTab = 0; // 0 = cấu hình bar, 1 = cấu hình corner
private void renderDesignConfigSpace() {
    designSliderContainer.removeAllViews();
    
    // Thẻ 2 nút ngang hàng nhau (cấu hình bar / cấu hình corner)
    LinearLayout tabRow = new LinearLayout(this);
    tabRow.setOrientation(LinearLayout.HORIZONTAL);
    tabRow.setPadding(0, 10, 0, 25);
    
    Button btnCfgBar = createTabBtn("CẤU HÌNH BAR");
    Button btnCfgCorner = createTabBtn("CẤU HÌNH CORNER");
    
    LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1f);
    tLp.setMargins(5, 0, 5, 0);
    btnCfgBar.setLayoutParams(tLp);
    btnCfgCorner.setLayoutParams(tLp);
    
    tabRow.addView(btnCfgBar);
    tabRow.addView(btnCfgCorner);
    designSliderContainer.addView(tabRow);
    
    // Container chứa nút Format viên thuốc và không gian data pack
    LinearLayout cfgBody = new LinearLayout(this);
    cfgBody.setOrientation(LinearLayout.VERTICAL);
    designSliderContainer.addView(cfgBody);
    
    View.OnClickListener cfgClick = v -> {
        currentDesignCfgTab = (v == btnCfgBar) ? 0 : 1;
        btnCfgBar.setBackground(getRounded(currentDesignCfgTab == 0 ? "#00E5FF" : "#222222", 20f));
        btnCfgBar.setTextColor(currentDesignCfgTab == 0 ? Color.BLACK : Color.WHITE);
        btnCfgCorner.setBackground(getRounded(currentDesignCfgTab == 1 ? "#00E5FF" : "#222222", 20f));
        btnCfgCorner.setTextColor(currentDesignCfgTab == 1 ? Color.BLACK : Color.WHITE);
        
        cfgBody.removeAllViews();
        
        // Tạo nút viên thuốc FormatB / FormatC (Y hệt dáng +NewEB, chuyên thêm vào vị trí trống)
        Button btnFormat = new Button(this);
        btnFormat.setText(currentDesignCfgTab == 0 ? "+ FORMAT B" : "+ FORMAT C");
        btnFormat.setTextColor(Color.BLACK);
        btnFormat.setBackground(getRounded("#00E5FF", 100f)); // Dáng viên thuốc 100f
        btnFormat.setTextSize(13.5f);
        
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, 135);
        fLp.setMargins(10, 10, 10, 20);
        btnFormat.setLayoutParams(fLp);
        btnFormat.setPadding(55, 0, 55, 0);
        
        btnFormat.setOnClickListener(btn -> {
            Toast.makeText(this, "Đã khởi tạo vùng chứa " + (currentDesignCfgTab == 0 ? "Format B" : "Format C") + " (Chuẩn bị kết nối Data Pack)", Toast.LENGTH_SHORT).show();
        });
        
        cfgBody.addView(btnFormat);
        
        // Không gian bên trong tạm thời để trống theo yêu cầu
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("📦 Không gian lưu trữ Data Pack (" + (currentDesignCfgTab == 0 ? "Bar" : "Corner") + ")\n[Tạm thời để trống cho lượt nâng cấp sau]");
        tvEmpty.setTextColor(Color.parseColor("#777777"));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, 80, 0, 0);
        cfgBody.addView(tvEmpty);
    };
    
    btnCfgBar.setOnClickListener(cfgClick);
    btnCfgCorner.setOnClickListener(cfgClick);
    
    // Kích hoạt mặc định vào tab đang chọn
    if (currentDesignCfgTab == 0) btnCfgBar.performClick();
    else btnCfgCorner.performClick();
}

private void stylePanelTabs(Button b1, Button b2, Button b3) {
// --- [KẾT THÚC CODE MỚI] ---
    b1.setBackground(getRounded(currentPanelIdx==1?"#00E5FF":"#222222",15f)); b1.setTextColor(currentPanelIdx==1?Color.BLACK:Color.WHITE);
    b2.setBackground(getRounded(currentPanelIdx==2?"#00E5FF":"#222222",15f)); b2.setTextColor(currentPanelIdx==2?Color.BLACK:Color.WHITE);
    b3.setBackground(getRounded(currentPanelIdx==3?"#00E5FF":"#222222",15f)); b3.setTextColor(currentPanelIdx==3?Color.BLACK:Color.WHITE);
}
    private int currentPanelSubTab = 0; // 0=Handle, 1=Panel, 2=Common (Biến tối ưu RAM)
    private boolean isPanelDrawerOpen = false;
    private boolean isHandleDrawerOpen = false;

private void buildPanelBody(LinearLayout panelBody) {
    panelBody.removeAllViews();
    if (currentPanelIdx < 1 || currentPanelIdx > 3) currentPanelIdx = 1;
    String px = "panel" + currentPanelIdx + "_";
    final String fpx = px;

    panelBody.addView(createSectionTitle("📐 EDGE PANEL " + currentPanelIdx + " ARCHITECT"));

    // 1. KHÔNG GIAN COMMON (Đứng đầu cột, luôn hiện ổn định theo yêu cầu)
    LinearLayout colCommon = newPanelColumn();
    TextView tvCommonTitle = new TextView(this); tvCommonTitle.setText("📦 COMMON & COLLECTIONS");
    tvCommonTitle.setTextColor(Color.parseColor("#4CAF50")); tvCommonTitle.setTextSize(13.5f); tvCommonTitle.setPadding(0, 0, 0, 10);
    colCommon.addView(tvCommonTitle);
    
    CheckBox cbEn = new CheckBox(this); cbEn.setText(T("Enable Panel " + currentPanelIdx, "Bật Panel " + currentPanelIdx));
    cbEn.setTextColor(Color.WHITE); cbEn.setTextSize(13.5f); cbEn.setChecked(prefs.getBoolean(px + "en", false));
    cbEn.setOnCheckedChangeListener((v, c) -> { prefs.edit().putBoolean(fpx + "en", c).apply(); syncPanelService(); });
    colCommon.addView(cbEn);
    colCommon.addView(createComboDropdown(T("Position", "Vị trí"), px + "pos", PANEL_POS_NAMES, 0));
    colCommon.addView(createComboDropdown(T("Color", "Màu"), px + "color_idx", PANEL_COLOR_NAMES, 0));
    colCommon.addView(createMiniSlider(T("Icon Size", "Kích thước Icon"), px + "icon_size", 180, 110));
    colCommon.addView(createMiniSlider(T("Columns (1-9)", "Số cột"), px + "cols", 9, 4));

    LinearLayout pickRow1 = new LinearLayout(this); pickRow1.setOrientation(LinearLayout.HORIZONTAL); pickRow1.setPadding(0, 15, 0, 10);
    int appCount = prefs.getString(px + "apps", "").isEmpty() ? 0 : prefs.getString(px + "apps", "").split(",").length;
    Button btnApps = new Button(this); btnApps.setText("📦 APP (" + appCount + ")");
    btnApps.setBackground(getRounded("#00E5FF", 20f)); btnApps.setTextColor(Color.BLACK); btnApps.setTextSize(13.5f);
    btnApps.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    btnApps.setOnClickListener(v -> showPanelMultiPicker(fpx + "apps", true));

    int actCount = prefs.getString(px + "acts", "").isEmpty() ? 0 : prefs.getString(px + "acts", "").split(",").length;
    Button btnActs = new Button(this); btnActs.setText("⚡ ACTION (" + actCount + ")");
    btnActs.setBackground(getRounded("#E91E63", 20f)); btnActs.setTextColor(Color.WHITE); btnActs.setTextSize(13.5f);
    LinearLayout.LayoutParams lpAct = new LinearLayout.LayoutParams(0, -2, 1f); lpAct.setMargins(15, 0, 0, 0);
    btnActs.setLayoutParams(lpAct);
    btnActs.setOnClickListener(v -> showPanelMultiPicker(fpx + "acts", false));
    pickRow1.addView(btnApps); pickRow1.addView(btnActs);
    colCommon.addView(pickRow1);

    int scCount = prefs.getString(px + "shortcuts", "").isEmpty() ? 0 : prefs.getString(px + "shortcuts", "").split(",").length;
    Button btnShortcuts = new Button(this); btnShortcuts.setText("🔗 COLLECT SHORTCUT (" + scCount + ")");
    btnShortcuts.setBackground(getRounded("#7C4DFF", 20f)); btnShortcuts.setTextColor(Color.WHITE); btnShortcuts.setTextSize(13.5f);
    btnShortcuts.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    btnShortcuts.setOnClickListener(v -> showPanelMultiPicker(fpx + "shortcuts", false));
    colCommon.addView(btnShortcuts);
    panelBody.addView(colCommon);

    // 2. NGĂN KÉO PANEL (Nút bấm mở/đóng, Zero-Waste RAM)
    Button btnDrawerPanel = new Button(this);
    btnDrawerPanel.setText(isPanelDrawerOpen ? "📂 PANEL CONFIG (Chạm để đóng ▲)" : "📁 PANEL CONFIG (Chạm để mở ▼)");
    btnDrawerPanel.setBackground(getRounded("#202124", 25f)); btnDrawerPanel.setTextColor(Color.parseColor("#00E5FF"));
    LinearLayout.LayoutParams lpDp = new LinearLayout.LayoutParams(-1, -2); lpDp.setMargins(0, 15, 0, 5);
    btnDrawerPanel.setLayoutParams(lpDp);
    panelBody.addView(btnDrawerPanel);

    if (isPanelDrawerOpen) {
        LinearLayout colPanel = newPanelColumn();
        String[] iconShapes = {"Tròn", "Google (bo vuông)", "Pebble", "Hệ thống"};
        colPanel.addView(createCycleRow(T("Icon Style", "Kiểu Icon"), px + "icon_shape", iconShapes));
        String[] yesNo = {T("No", "Không"), T("Yes", "Có")};
        colPanel.addView(createCycleRow(T("Show Name", "Hiện Tên"), px + "show_name", yesNo));
        colPanel.addView(createMiniSlider(T("Opacity", "Độ trong suốt"), px + "alpha", 255, 200));
        colPanel.addView(createMiniSlider(T("Length", "Chiều dài"), px + "panel_length", 3000, 700));
        colPanel.addView(createMiniSlider(T("Width", "Bề dày"), px + "size", 2500, 700));
        colPanel.addView(createMiniSlider(T("Corner Radius", "Bo góc"), px + "panel_radius", 60, 24));
        panelBody.addView(colPanel);
    }

    btnDrawerPanel.setOnClickListener(v -> { isPanelDrawerOpen = !isPanelDrawerOpen; buildPanelBody(panelBody); });

    // 3. NGĂN KÉO HANDLE (Nút bấm mở/đóng nằm cuối cột)
    Button btnDrawerHandle = new Button(this);
    btnDrawerHandle.setText(isHandleDrawerOpen ? "📂 HANDLE CONFIG (Chạm để đóng ▲)" : "📁 HANDLE CONFIG (Chạm để mở ▼)");
    btnDrawerHandle.setBackground(getRounded("#202124", 25f)); btnDrawerHandle.setTextColor(Color.parseColor("#FFC107"));
    LinearLayout.LayoutParams lpDh = new LinearLayout.LayoutParams(-1, -2); lpDh.setMargins(0, 10, 0, 15);
    btnDrawerHandle.setLayoutParams(lpDh);
    panelBody.addView(btnDrawerHandle);

    if (isHandleDrawerOpen) {
        LinearLayout colHandle = newPanelColumn();
        colHandle.addView(createCycleRow(T("Visibility", "Chế độ hiện Handle"), px + "vis", new String[]{ T("Local (Design only)", "Cục Bộ (chỉ trong Design)"), T("Global (Everywhere)", "Toàn Cục (mọi nơi)") }));
        colHandle.addView(createMiniSlider(T("Opacity", "Độ trong suốt"), px + "handle_alpha", 255, 255));
        colHandle.addView(createMiniSlider(T("Length", "Chiều dài"), px + "thick", 400, 200));
        colHandle.addView(createMiniSlider(T("Width", "Độ dày"), px + "handle_width", 200, 56));
        colHandle.addView(createMiniSlider(T("Corner Radius", "Bo góc"), px + "handle_radius", 100, 28));
        panelBody.addView(colHandle);
    }

    btnDrawerHandle.setOnClickListener(v -> { isHandleDrawerOpen = !isHandleDrawerOpen; buildPanelBody(panelBody); });
} // <-- THÊM dấu } này để đóng đúng hàm buildPanelBody()
private LinearLayout newPanelColumn() {
    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.setMargins(0, 0, 0, 20); // margin dưới thay vì margin phải
    col.setLayoutParams(lp);
    col.setBackground(getRounded("#1A1A1A", 20f));
    col.setPadding(30, 20, 30, 20);
    return col;
}
// Slider gọn cho layout 2-cột: KHÔNG có nút +/- để tiết kiệm bề ngang.
private LinearLayout createMiniSlider(String t, String k, int max, int def) {
    LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,4,0,4);
    TextView tv = new TextView(this); tv.setTextColor(Color.parseColor("#BBBBBB")); tv.setTextSize(11);
    tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv);

    LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
    Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(16);
    Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(16);
    SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); }
        public void onStartTrackingTouch(SeekBar s){}
        public void onStopTrackingTouch(SeekBar s){}
    });
    btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); });
    btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); });
    row.addView(btnMinus); row.addView(sb); row.addView(btnPlus);
    l.addView(row);
    return l;
}
// isApp=true: multi-select app picker (ghi CSV package name)
// isApp=false: multi-select action picker (ghi CSV action key, dùng ACT_KEYS/ACT_LABS có sẵn)
private void showPanelMultiPicker(String prefKey, boolean isApp) {
    String cur = prefs.getString(prefKey, "");
    final java.util.LinkedHashSet<String> selectedOrder = new java.util.LinkedHashSet<>();
    for (String s : cur.split(",")) if (!s.trim().isEmpty()) selectedOrder.add(s.trim());

    final List<String[]> allItems = new ArrayList<>();
    if (isApp) {
        allItems.addAll(getAppListCached());
    } else {
        reloadActionLabels();
        for (int i = 1; i < ACT_KEYS.length; i++) {
            if (ACT_KEYS[i].equals("LAUNCH_APP")) continue;
            allItems.add(new String[]{ACT_LABS[i], ACT_KEYS[i]});
        }
        String scIds = prefs.getString("shortcut_ids", "");
        if (!scIds.isEmpty()) for (String id : scIds.split(",")) {
            String nm = prefs.getString("shortcut_" + id + "_name", "Shortcut");
            allItems.add(new String[]{"🔗 " + nm, "RUN_SHORTCUT_" + id});
        }
    }
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30,80,30,30);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...","Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
    root.addView(etSearch);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    final List<String[]> shown = new ArrayList<>();
    final Runnable[] refreshHolder = new Runnable[1];
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20,22,20,22);
            String[] item = shown.get(p);
            CheckBox cb = new CheckBox(MainActivity.this);
            cb.setChecked(selectedOrder.contains(item[1]));
            cb.setClickable(false);
            TextView tv = new TextView(MainActivity.this);
            tv.setText(item[0]); tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(cb); row.addView(tv);
            row.setOnClickListener(v -> {
                if (selectedOrder.contains(item[1])) selectedOrder.remove(item[1]);
                else selectedOrder.add(item[1]); // mục vừa chọn -> cuối danh sách đã chọn (thứ tự ưu tiên)
                refreshHolder[0].run(); // nhảy lên đầu ngay lập tức
            });
            return row;
        }
    };
    lv.setAdapter(adapter);

    Runnable doRefresh = () -> {
        String q = etSearch.getText().toString().trim().toLowerCase();
        shown.clear();
        List<String[]> selectedSorted = new ArrayList<>();
        List<String[]> restList = new ArrayList<>();
        for (String key : selectedOrder) {
            for (String[] it : allItems) {
                if (it[1].equals(key) && (q.isEmpty() || it[0].toLowerCase().contains(q))) { selectedSorted.add(it); break; }
            }
        }
        for (String[] it : allItems) {
            if (selectedOrder.contains(it[1])) continue;
            if (!q.isEmpty() && !it[0].toLowerCase().contains(q)) continue;
            restList.add(it);
        }
        shown.addAll(selectedSorted);
        shown.addAll(restList);
        adapter.notifyDataSetChanged();
    };
    refreshHolder[0] = doRefresh;
    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){ doRefresh.run(); }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });
    doRefresh.run();

    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY")); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText(T("SAVE","LƯU")); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        prefs.edit().putString(prefKey, TextUtils.join(",", selectedOrder)).apply();
        syncPanelService(); renderSliders();
        d.dismiss();
    });
    d.setContentView(root); d.show();
}
// CODE MỚI — thay toàn bộ hàm bằng:
private void syncPanelService() {
    sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
}
    // ==================== CÁC HÀM PHỤ TRỢ CHUNG ====================
    private void showAppPickerDialog() {
    List<String[]> combined = getAppListCached();
    String currentLocklist = prefs.getString("locklist", "");
    final java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
    for (String s : currentLocklist.split(",")) if (!s.trim().isEmpty()) selected.add(s.trim());

    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30,80,30,30);

    TextView title = new TextView(this); title.setText(T("Choose App to Lock", "Chọn ứng dụng cần khóa"));
    title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
    root.addView(title);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...","Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
    root.addView(etSearch);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    final List<String[]> shown = new ArrayList<>(combined);
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20,22,20,22);
            String[] item = shown.get(p);
            CheckBox cb = new CheckBox(MainActivity.this);
            cb.setChecked(selected.contains(item[1])); cb.setClickable(false);
            TextView tv = new TextView(MainActivity.this);
            tv.setText(item[0]); tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(cb); row.addView(tv);
            row.setOnClickListener(v -> {
                if (selected.contains(item[1])) selected.remove(item[1]); else selected.add(item[1]);
                cb.setChecked(selected.contains(item[1]));
            });
            return row;
        }
    };
    lv.setAdapter(adapter);

    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){
            String q = s.toString().trim().toLowerCase();
            shown.clear();
            for (String[] it : combined) if (q.isEmpty() || it[0].toLowerCase().contains(q)) shown.add(it);
            adapter.notifyDataSetChanged();
        }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });

    LinearLayout footer = new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY")); bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText(T("SAVE","LƯU")); bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0); bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        String newLocklist = TextUtils.join(",", selected);
        prefs.edit().putString("locklist", newLocklist).apply();
        try {
            ViewGroup rootView = (ViewGroup) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            EditText et = rootView.findViewWithTag("locklist_input");
            if (et != null) et.setText(newLocklist);
        } catch (Exception ignored) {}
        d.dismiss();
    });

    d.setContentView(root); d.show();
}
    // GIỮ NGUYÊN bản cũ showSingleAppPickerDialog(EditText target) để không phá VOLKEY/TILE cũ,
// nhưng đổi phần thân để dùng cache thay vì quét lại mỗi lần:
private void showSingleAppPickerDialog(EditText target) {
    showSingleAppPickerDialogCallback(target::setText);
}

// THÊM MỚI — bản chuẩn dùng callback, thay hẳn kiểu "dummy EditText" đang bug ở VolKey
private void showSingleAppPickerDialogCallback(java.util.function.Consumer<String> onPicked) {
    List<String[]> combined = getAppListCached();
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30,80,30,30);

    TextView title = new TextView(this); title.setText(T("Choose one app", "Chọn 1 ứng dụng"));
    title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
    root.addView(title);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...","Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
    root.addView(etSearch);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    final List<String[]> shown = new ArrayList<>(combined);
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            TextView tv = new TextView(MainActivity.this);
            tv.setText(shown.get(p)[0]); tv.setTextColor(Color.WHITE); tv.setTextSize(15);
            tv.setPadding(20,26,20,26);
            return tv;
        }
    };
    lv.setAdapter(adapter);
    lv.setOnItemClickListener((parent, v, position, id) -> {
        onPicked.accept(shown.get(position)[1]);
        d.dismiss();
    });

    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){
            String q = s.toString().trim().toLowerCase();
            shown.clear();
            for (String[] it : combined) if (q.isEmpty() || it[0].toLowerCase().contains(q)) shown.add(it);
            adapter.notifyDataSetChanged();
        }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });

    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY"));
    bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setOnClickListener(v -> d.dismiss());
    root.addView(bCancel);

    d.setContentView(root); d.show();
}
    // ==================== SHORTCUT SCANNER (giống Tasker "Choose Shortcut") ====================
private java.util.function.BiConsumer<String, String> pendingShortcutCallback = null; // (id, name) -> ...

private void showShortcutPickerDialog(java.util.function.BiConsumer<String,String> onPicked) {
    List<android.content.pm.ResolveInfo> providers = ShortcutScanner.getProviders(this);
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30,80,30,30);

    TextView title = new TextView(this); title.setText(T("Choose a Shortcut Provider","Chọn một ứng dụng"));
    title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
    root.addView(title);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...","Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
    root.addView(etSearch);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    PackageManager pm = getPackageManager();
    final List<android.content.pm.ResolveInfo> shown = new ArrayList<>(providers);
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20,22,20,22);
            android.content.pm.ResolveInfo ri = shown.get(p);
            // ImageView icon — lazy, chỉ decode khi row thực sự hiển thị (ListView tự tái sử dụng view)
            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageDrawable(ri.loadIcon(pm));
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(80, 80);
            ivLp.setMargins(0,0,20,0); iv.setLayoutParams(ivLp);
            TextView tv = new TextView(MainActivity.this);
            tv.setText(ri.loadLabel(pm)); tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            row.addView(iv); row.addView(tv);
            return row;
        }
    };
    lv.setAdapter(adapter);

    lv.setOnItemClickListener((parent, v, position, id) -> {
        android.content.pm.ResolveInfo ri = shown.get(position);
        Intent createIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createIntent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
        pendingShortcutCallback = onPicked;
        try { startActivityForResult(createIntent, 104); }
        catch (Exception e) { Toast.makeText(this, T("Cannot open this app","Không thể mở app này"), Toast.LENGTH_SHORT).show(); }
        d.dismiss();
    });

    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){
            String q = s.toString().trim().toLowerCase();
            shown.clear();
            for (android.content.pm.ResolveInfo ri : providers)
                if (q.isEmpty() || ri.loadLabel(pm).toString().toLowerCase().contains(q)) shown.add(ri);
            adapter.notifyDataSetChanged();
        }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });

    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY"));
    bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setOnClickListener(v -> d.dismiss());
    root.addView(bCancel);

    d.setContentView(root); d.show();
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
        Button btnSave = new Button(this); btnSave.setText("💾 LƯU LINK");
    btnSave.setBackground(getRounded("#4CAF50", 20f)); btnSave.setTextColor(Color.WHITE);
    // Thêm margin phải (right margin = 20) để tách rời nút Lưu link khỏi nút Tải ngay
    LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, -2, 1f);
    lpSave.setMargins(0, 0, 20, 0);
    btnSave.setLayoutParams(lpSave);
    
    Button btnDownload = new Button(this); btnDownload.setText("📥 TẢI NGAY");
    btnDownload.setBackground(getRounded("#00E5FF", 20f)); btnDownload.setTextColor(Color.BLACK);
    btnDownload.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    
    btnRow.addView(btnSave); btnRow.addView(btnDownload);
        ytdlDrawer.addView(btnRow);
        parent.addView(createDrawer("YTDL DOWNLOADER", ytdlDrawer));
    }
    private void addPanelDesign(LinearLayout parent) {
    LinearLayout panelDrawer = new LinearLayout(this); panelDrawer.setOrientation(LinearLayout.VERTICAL);
    panelDrawer.setPadding(30,20,30,20); panelDrawer.setBackground(getRounded("#222222", 20f));
    TextView title = new TextView(this); title.setText("📱 EDGE PANEL");
    title.setTextColor(Color.parseColor("#00E5FF")); title.setPadding(0,0,0,20);
    panelDrawer.addView(title);

    CheckBox cbEn = new CheckBox(this); cbEn.setText("Bật Edge Panel");
    cbEn.setTextColor(Color.WHITE); cbEn.setChecked(prefs.getBoolean("panel_en", false));
    cbEn.setOnCheckedChangeListener((v, c) -> {
        prefs.edit().putBoolean("panel_en", c).apply();
        Intent i = new Intent(this, SidePanelService.class);
        if (c) { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i); }
        else stopService(i);
    });
    panelDrawer.addView(cbEn);

    panelDrawer.addView(createComboDropdown("Vị trí", "panel_side_idx",
        new String[]{"Phải", "Trái"}, 0)); // lưu ý: cần map idx→"left"/"right" khi save, xem ghi chú dưới

    Button btnPickPanelApps = new Button(this);
    btnPickPanelApps.setText("📱 CHỌN APP CHO PANEL");
    btnPickPanelApps.setBackground(getRounded("#00E5FF", 20f)); btnPickPanelApps.setTextColor(Color.BLACK);
    btnPickPanelApps.setOnClickListener(v -> showPanelAppPicker());
    panelDrawer.addView(btnPickPanelApps);

    panelDrawer.addView(createComboDropdown("Kiểu Icon", "panel_icon_shape",
        new String[]{"Tròn", "Vuông bo góc", "Bubble méo"}, 0));
    panelDrawer.addView(createSlider("Số cột", "panel_columns", 6, 4));

    parent.addView(createDrawer("EDGE PANEL (Kiểu Samsung)", panelDrawer));
}

// Multi-select app picker riêng cho panel — tái dùng logic showAppPickerDialog()
// nhưng ghi vào "panel_apps" thay vì "locklist"
private void showPanelAppPicker() {
    List<String[]> combined = getAppListCached();
    String cur = prefs.getString("panel_apps", "");
    boolean[] checked = new boolean[combined.size()];
    String[] names = new String[combined.size()];
    for (int i=0; i<combined.size(); i++) {
        names[i] = combined.get(i)[0];
        checked[i] = cur.contains(combined.get(i)[1]);
    }
    new AlertDialog.Builder(this).setTitle("Chọn app cho Panel")
        .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
        .setPositiveButton("LƯU", (d, w) -> {
            List<String> sel = new ArrayList<>();
            for (int i=0; i<combined.size(); i++) if (checked[i]) sel.add(combined.get(i)[1]);
            prefs.edit().putString("panel_apps", TextUtils.join(",", sel)).apply();
            sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
        }).setNegativeButton("HỦY", null).show();
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
        String t = T("ADB COMMANDS:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow", 
        "🔧 LỆNH ADB CỐT LÕI (Cấp 1 lần trọn đời):\n\n1. Quyền ghi Cài đặt bảo mật:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\n\n2. Quyền vẽ Lớp phủ (Tàng hình AppOps):\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow\n\n🚀 TĂNG TỐC BẰNG ADB (chạy 1 lần):\nadb shell settings put global window_animation_scale 0\nadb shell settings put global transition_animation_scale 0\nadb shell settings put global animator_duration_scale 0"); 
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
private Button createSystemBtn(String text, String bgHex, String textHex) {
    Button b = new Button(this); b.setText(text);
    b.setBackground(getRounded(bgHex, 20f));
    b.setTextColor(Color.parseColor(textHex)); 
    b.setTextSize(13.5f); // Tăng từ 12f lên 13.5f đồng bộ toàn hệ thống
    b.setPadding(10, 0, 10, 0); // Kèm padding tối ưu để không bị chèn chữ
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
    lp.setMargins(4, 0, 4, 0); b.setLayoutParams(lp);
    return b;
}
    private Button createNavBtn(String t) {
    Button b = new Button(this);
    b.setText(t);
    b.setTextSize(16); // Đã tăng +2
    // Đã bỏ in đậm hoàn toàn
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
    lp.setMargins(5, 0, 5, 0);
    b.setLayoutParams(lp);
    return b;
}
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    private TextView createSectionTitle(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout createCycleRow(String title, String key, String[] states) {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(0, 12, 0, 22);
    TextView tv = new TextView(this); tv.setText(title);
    tv.setTextColor(Color.parseColor("#E91E63"));
    tv.setTextSize(14f); // Tăng chữ nhãn
    tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    
    // Nút bấm Icon Style / Show Name được phóng to rõ ràng cho tay người dùng Pixel 2 XL
    TextView tvVal = new TextView(this);
    tvVal.setTextColor(Color.parseColor("#00E5FF"));
    tvVal.setTextSize(14.5f); // Phóng to font chữ (+1.5sp)
    tvVal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    int cur = prefs.getInt(key, 0);
    tvVal.setText("  " + states[cur % states.length] + "  ");
    tvVal.setPadding(40, 22, 40, 22); // Tăng diện tích chạm an toàn
    tvVal.setMinimumHeight(100); // Phóng to khối nút
    tvVal.setGravity(Gravity.CENTER);
    tvVal.setBackground(getRounded("#2C2C2C", 25f));
    tvVal.setOnClickListener(v -> {
        int next = (prefs.getInt(key, 0) + 1) % states.length;
        prefs.edit().putInt(key, next).apply();
        tvVal.setText("  " + states[next] + "  ");
    });
    l.addView(tv); l.addView(tvVal); return l;
}
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
