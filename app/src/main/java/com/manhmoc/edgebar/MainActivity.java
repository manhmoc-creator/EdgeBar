package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.WindowManager;
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
    
    private String[] ACT_KEYS = new String[60]; private String[] ACT_LABS = new String[60];
    private String[] BARS = {"b_c", "r", "l", "r_u", "r_c", "r_d", "t_c", "t_r", "t_l", "l_u", "l_c", "l_d"}; private String[] BAR_NAMES; 
    private String[] CORNERS = {"br", "bl", "tr", "tl"}; private String[] CORNER_NAMES;
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; private String[] COLOR_NAMES;
private String[] ALL_COMP_KEYS = {"r", "l", "t_r", "t_l", "t_c", "b_c", "l_c", "r_c", "r_u", "r_d", "l_u", "l_d", "corner_br",
"corner_bl", "corner_tr", "corner_tl", "fingerprint"};
private String[] ALL_COMP_NAMES;
private String[] VOLKEY_COMPS = {"up", "down"};
private String[] VOLKEY_COMP_NAMES;
private String[] VOLKEY_GESTURES = {"tap", "dtap", "long"};
private String[] VOLKEY_GESTURE_NAMES;
private String[] M_BARS = {"r", "l", "t_r", "t_l", "t_c", "m_b_c", "m_mid_t", "m_mid_b"};
private String[] M_BAR_NAMES;
private String[] C_GESTURES = {
    "tap", "dtap", "long", 
    "up", "down", "left", "right", "diag",
    "up_hold", "down_hold", "left_hold", "right_hold", "diag_hold",
    "up_down", "down_up", "left_right", "right_left",
    "hold_up", "hold_down", "hold_left", "hold_right"
}; 
private String[] C_GESTURE_NAMES = {
    "1 Chạm", "2 Chạm", "Nhấn Giữ (Long)", 
    "Vuốt Lên", "Vuốt Xuống", "Vuốt Trái", "Vuốt Phải", "Vuốt Chéo",
    "Vuốt rồi Giữ Lên", "Vuốt rồi Giữ Xuống", "Vuốt rồi Giữ Trái", "Vuốt rồi Giữ Phải", "Vuốt rồi Giữ Chéo",
    "Combo: Lên - Xuống", "Combo: Xuống - Lên", "Combo: Trái - Phải", "Combo: Phải - Trái",
    "Gài số: Giữ + Vuốt Lên", "Gài số: Giữ + Vuốt Xuống", "Gài số: Giữ + Vuốt Trái", "Gài số: Giữ + Vuốt Phải"
};

// [TỐI ƯU PIN/RAM] Throttle ghi prefs khi kéo Slider — leading-edge throttle +
    // write bắt buộc lúc nhả tay. Giảm số lần apply() từ "mỗi pixel kéo" (40-100+ lần
    // mỗi lần vuốt) xuống tối đa ~16 lần/giây, vẫn giữ cảm giác preview real-time.
    private final Handler sliderPrefHandler = new Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<String, Long> sliderLastWriteMs = new java.util.HashMap<>();
    private final java.util.Map<String, Runnable> sliderPendingRunnable = new java.util.HashMap<>();
    private static final long SLIDER_WRITE_THROTTLE_MS = 60;
private LinearLayout pageDesign, pageConditions, pageEcosystem, listRules, designSliderContainer, navMain;
private LinearLayout condBackRow;
private LinearLayout designTopBackRow, designSpaceMenu, designBackRow;
private TextView tvDesignSubTitle;
    private LinearLayout pageMainMenu, pageEcoShowcase, pageSystemSpace; // [MỚI] màn chính 9-mục
    private String[] PANEL_COLOR_KEYS = {"SLATE","STEEL","MIST","GRAPHITE","INDIGO_MIST","TEAL_GREY","COOL_ASH","DEEP_BLUE"};
private String[] PANEL_COLOR_HEX  = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
private String[] PANEL_COLOR_NAMES; // set trong reloadActionLabels()
private String[] PANEL_POS_NAMES;   // 9 vị trí, set trong reloadActionLabels()
// THAY BẰNG:
private LinearLayout btnEditAnim, btnEditPanel;
private LinearLayout gesMenuContainer, gesSubHeader;
private TextView tvGesSubTitle;
private int currentPanelIdx = 1; // 1-3, panel nào đang được chỉnh trong tab PANEL
private boolean panelSelectMode = false;
private boolean trashSelectMode = false;
private java.util.Set<String> trashSelectedItems = new java.util.LinkedHashSet<>();
private java.util.Set<String> panelSelectedItems = new java.util.LinkedHashSet<>();
// [MỚI] Multi-select cho Intent/QS Tile/Macro trong Ecosystem
private boolean ecoSelectMode = false;
private java.util.Set<String> ecoSelectedItems = new java.util.LinkedHashSet<>();
// [MỚI] Multi-select cho danh sách ghi âm (voice recordings)
private boolean voiceSelectMode = false;
private java.util.Set<String> voiceSelectedItems = new java.util.LinkedHashSet<>();

// [THÊM MỚI] Multi-select cho danh sách ghi màn hình (screen recordings)
private boolean videoSelectMode = false;
private java.util.Set<String> videoSelectedItems = new java.util.LinkedHashSet<>();

// [MỚI] Multi-select cho danh sách My Playlist
private boolean myPlSelectMode = false;
private java.util.Set<String> myPlSelectedItems = new java.util.LinkedHashSet<>();
private ImageButton fab;
private EditText etNavSearch;
private List<Object[]> searchIndexCache;
private android.widget.ListPopupWindow searchPopup;

private View livePreviewOverlay;
private WindowManager.LayoutParams livePreviewLp;
    private int designTabState = 0;
    private boolean recIndicatorTestOn = false;
    private int currentMainTab = 1; private int currentGesTab = 0; private int frontierSubTab = 0;
private LinearLayout frontierBodyContainer, frontierBackRowRef;
private boolean frontierSpaceBuilt = false;
// [MỚI] Callback "lùi 1 cấp" hiện tại — Back ở Nav Bar ưu tiên gọi cái này trước khi
// rơi về logic mặc định. Mỗi màn con tự gán khi mở, tự trả về cấp trước khi lùi.
private final java.util.ArrayDeque<Runnable> navBackStack = new java.util.ArrayDeque<>();
private Runnable currentLevelBackAction = null;
    // [MULTI-SELECT FRONTIER] Zero-RAM khi không dùng — chỉ 1 boolean + 1 Set rỗng
    private boolean frontierSelectMode = false;
    private java.util.Set<String> frontierSelectedItems = new java.util.LinkedHashSet<>();
    // MỚI: multi-select cho Pattern (prule) bên trong 1 Data Pack
private boolean prulesSelectMode = false;
private java.util.Set<String> prulesSelectedItems = new java.util.LinkedHashSet<>();
    private final String CURRENT_VERSION = "☠️ 19.12.3.6.42";
    private RelativeLayout rootLayout;
    private Button btnDeviceAdmin;
    private Button btnWriteSettings; // MỚI
    private Button btnNotifListener; // MỚI — quyền Notification Access cho PLAY_LAST_MUSIC
    private static final int REQ_UNINSTALL_CONFIRM = 9930;
    private int ecoType = 0;
    private int soundMediaSubTab = -1; // -1 = menu chọn Ghi âm/Ghi màn hình, 0 = Ghi âm, 1 = Ghi màn hình
    private LinearLayout ecoContainer;
        // THÊM 2 field static này ngay dưới khai báo ecoContainer:
private static List<String[]> cachedAppList = null; // mỗi phần tử: {name, pkg}
private static long cachedAppListTs = 0;
private static final long APP_LIST_CACHE_MS = 5 * 60 * 1000; // 5 phút
private static final java.util.Map<String,String> appLabelCache = new java.util.HashMap<>();
private ImageButton createIconCircleBtn(int resId, String color) {
    ImageButton b = new ImageButton(this);
    b.setImageResource(resId);
    // [FIX] Đồng bộ màu icon Nav Bar với icon 9 mục Menu chính (ACCENT_COLOR = #8AB4F8),
    // thay vì trắng thuần như trước.
    b.setColorFilter(Color.parseColor(ACCENT_COLOR));
    b.setBackground(getRounded(color, 100f));
    b.setScaleType(ImageView.ScaleType.FIT_CENTER);
    // [FIX] Giảm padding 34 -> 22 để icon to/dày ra, khớp kích thước ~81px như
    // icon đầu 9 mục (khung 130px, padding 22 mỗi bên -> icon còn lại ~86px).
    b.setPadding(22,22,22,22);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130);
    lp.setMargins(10, 0, 10, 0);
    b.setLayoutParams(lp);
    return b;
}
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
// [MỚI] List RIÊNG cho Panel — mã hoá ref app Island thành "pkg#ISL#<serial>" để
// biết chính xác cần mở/lấy icon từ profile nào. Không dùng chung getAppListCached()
// (return pkg trần) vì Blacklist/LockList/QR-Bank so khớp trực tiếp packageName sự
// kiện hệ thống, không hiểu định dạng có hậu tố — tách riêng để không phá vỡ chỗ đó.
private static final String ISLAND_SEP = "#ISL#";
private boolean isIslandRef(String ref) { return ref != null && ref.contains(ISLAND_SEP); }
private String islandRefPkg(String ref) { int i = ref.indexOf(ISLAND_SEP); return i < 0 ? ref : ref.substring(0, i); }
private long islandRefSerial(String ref) {
    int i = ref.indexOf(ISLAND_SEP);
    if (i < 0) return -1;
    try { return Long.parseLong(ref.substring(i + ISLAND_SEP.length())); } catch (Exception e) { return -1; }
}

private static List<String[]> cachedPanelAppList = null;
private static long cachedPanelAppListTs = 0;

private List<String[]> getPanelAppListCached() {
    long now = System.currentTimeMillis();
    if (cachedPanelAppList != null && (now - cachedPanelAppListTs) < APP_LIST_CACHE_MS) return cachedPanelAppList;
    android.os.UserManager um = (android.os.UserManager) getSystemService(Context.USER_SERVICE);
    android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
    List<String[]> combined = new ArrayList<>();
    try {
        for (android.os.UserHandle profile : um.getUserProfiles()) {
            boolean island = !profile.equals(android.os.Process.myUserHandle());
            long serial = island ? um.getSerialNumberForUser(profile) : -1;
            for (android.content.pm.LauncherActivityInfo info : la.getActivityList(null, profile)) {
                String pkg = info.getApplicationInfo().packageName;
                String ref = island ? (pkg + ISLAND_SEP + serial) : pkg;
                String name = info.getLabel().toString() + (island ? " [Island]" : "");
                combined.add(new String[]{name, ref});
            }
        }
    } catch (Exception ignored) {}
    combined.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
    cachedPanelAppList = combined; cachedPanelAppListTs = now;
    return combined;
}
    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    // [MỚI] Chuẩn hoá kích thước hiển thị của Icon Hệ Thống — các icon android.R.drawable.*
// có tỉ lệ glyph/canvas rất khác nhau, khiến xếp cạnh nhau "cái to cái nhỏ" dù cùng
// 1 khung ImageView. Hàm này dò vùng pixel KHÔNG trong suốt, cắt sát viền, rồi phóng
// lại đồng đều cho mọi icon — KHÔNG ép màu, giữ nguyên màu gốc để user thấy đúng.
private Bitmap normalizeIconBitmap(android.graphics.drawable.Drawable d, int targetSize, float contentScale) {
    if (d == null) return null;
    try {
        int srcSize = targetSize * 3;
        Bitmap raw = Bitmap.createBitmap(srcSize, srcSize, Bitmap.Config.ARGB_8888);
        Canvas rawCanvas = new Canvas(raw);
        android.graphics.drawable.Drawable dm = d.mutate();
        dm.setBounds(0, 0, srcSize, srcSize);
        dm.draw(rawCanvas);

        int left = srcSize, top = srcSize, right = 0, bottom = 0;
        int[] pixels = new int[srcSize * srcSize];
        raw.getPixels(pixels, 0, srcSize, 0, 0, srcSize, srcSize);
        for (int y = 0; y < srcSize; y++) {
            int rowBase = y * srcSize;
            for (int x = 0; x < srcSize; x++) {
                if (((pixels[rowBase + x] >>> 24) & 0xFF) > 10) {
                    if (x < left) left = x; if (x > right) right = x;
                    if (y < top) top = y; if (y > bottom) bottom = y;
                }
            }
        }
        if (right <= left || bottom <= top) { raw.recycle(); return null; }

        Bitmap cropped = Bitmap.createBitmap(raw, left, top, right - left + 1, bottom - top + 1);
        raw.recycle();

        Bitmap out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas outCanvas = new Canvas(out);
        int drawSize = Math.round(targetSize * contentScale);
        float scale = Math.min((float) drawSize / cropped.getWidth(), (float) drawSize / cropped.getHeight());
        int dw = Math.round(cropped.getWidth() * scale);
        int dh = Math.round(cropped.getHeight() * scale);
        android.graphics.Rect dst = new android.graphics.Rect((targetSize - dw) / 2, (targetSize - dh) / 2,
                (targetSize - dw) / 2 + dw, (targetSize - dh) / 2 + dh);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        outCanvas.drawBitmap(cropped, null, dst, p);
        cropped.recycle();
        return out;
    } catch (Exception e) { return null; }
}
    private void refreshPreview() { 
        // [FIX] Bỏ điều kiện inFrontierLock/Home/Homacc để không hồi sinh full overlay
        // khi người dùng chỉ mới lướt xem danh sách ở menu Frontier. 
        // Overlays sẽ chỉ hiển thị độc lập khi người dùng thực sự ấn vào chỉnh sửa từng Data Pack.
        boolean pLock = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==0)
            || (currentMainTab==1 && currentGesTab==0); 
        boolean pHomacc = (pageDesign != null && pageDesign.getVisibility()==View.VISIBLE && designTabState==4);
        boolean pHome = false; 
        
        SharedPreferences.Editor ed = prefs.edit();
        ed.putBoolean("preview_lock", pLock)
          .putBoolean("preview_homacc", pHomacc)
          .putBoolean("preview_home", pHome);

        // Hồi sinh toàn bộ bar/corner của không gian đang được xem trước (Xoá cờ manual_hide)
        if (pLock) {
            for (String b : BARS) ed.putBoolean("lock_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("lock_corner_" + cn + "_manual_hide", false);
        }
        if (pHome) {
            for (String b : BARS) ed.putBoolean("home_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("home_corner_" + cn + "_manual_hide", false);
        }
        if (pHomacc) {
            for (String b : BARS) ed.putBoolean("homacc_" + b + "_manual_hide", false);
            for (String cn : CORNERS) ed.putBoolean("homacc_corner_" + cn + "_manual_hide", false);
        }
        ed.apply();
        
        Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); 
    }


    private boolean isNotifListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }
    @Override protected void onResume() {
        super.onResume();
        refreshPreview();
        checkPendingStorageScan();
        if (btnWriteSettings != null) {
            btnWriteSettings.setVisibility(android.provider.Settings.System.canWrite(this) ? View.GONE : View.VISIBLE);
        }
        if (btnDeviceAdmin != null) {
            android.app.admin.DevicePolicyManager dpmR =
                (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName adminR =
                new android.content.ComponentName(this, HomebDeviceAdminReceiver.class);
            btnDeviceAdmin.setVisibility(dpmR.isAdminActive(adminR) ? View.GONE : View.VISIBLE);
        }
        if (btnNotifListener != null) {
            btnNotifListener.setVisibility(isNotifListenerEnabled() ? View.GONE : View.VISIBLE);
        }
    }
    @Override protected void onPause() { super.onPause(); prefs.edit().putBoolean("preview_lock", false).putBoolean("preview_homacc", false).putBoolean("preview_home", false).apply(); Intent i = new Intent("com.manhmoc.edgebar.SYNC_STATE"); sendBroadcast(i); }
    private void reloadActionLabels() {
// [XÓA] OPEN_PANEL_1/2/3 — Panel giờ liệt kê động qua nút "PANEL" (buildDynamicPackItems).
String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF",
        "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA",
        "NOTIFICATIONS", "QUICK_SETTINGS", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD", "TOGGLE_RECORD",
        "LAUNCH_APP", "SPLIT_SCREEN", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE",
        "PAUSE_RECORD", "OPEN_STORAGE_SCAN", "SCAN_QR", "TOGGLE_WORK_PROFILE", "PLAY_MY_PLAYLIST",
                // [MỚI] Ẩn/Hồi sinh overlay + 8 action Giả Lập Cử Chỉ (TRIGGER_*)
        "HIDE_SOME_OVERLAY", "SHOW_ALL_OVERLAY",
        "TRIGGER_TAP", "TRIGGER_DTAP", "TRIGGER_LONG",
        "TRIGGER_UP", "TRIGGER_DOWN", "TRIGGER_LEFT", "TRIGGER_RIGHT",
        "TRIGGER_DIAG"};


String[] bL = {T("None", "Không có"), T("Back", "Quay lại"), T("Home", "Màn chính"),
        T("Recents", "Đa nhiệm"), T("Screen Off", "Tắt màn hình"), T("Flashlight", "Đèn pin"),
        T("Power Menu", "Menu Nguồn"), T("Volume", "Âm Lượng"), T("Screenshot", "Chụp màn hình"), "Camera", T("Notifications", "Mở Thông Báo"), T("Quick Settings", "Bảng Cài Đặt Nhanh"), T("Toggle Overlay (Trợ năng)", "Bật/Tắt Trợ Năng (Homeb ⇄ Overlay)"), "YTDLnis", T("Toggle Voice Record", "Bật/Tắt Ghi Âm"),
        T("Launch App", "Mở Ứng dụng"), T("Split Screen", "Chia đôi màn hình"), T("Screen Record", "Quay màn hình"), T("Auto-Rotate Toggle", "Bật/Tắt Tự Động Xoay"),
        T("Pause/Resume Recording", "Tạm Dừng/Tiếp Tục Ghi Âm"), T("Storage Scan", "Quét Dung Lượng"), T("Scan QR", "Quét QR"),
         T("Toggle Island (Work Profile)", "Bật/Tắt Đảo (Island)"), T("Play My Playlist", "Phát My Playlist"),
                T("Hide Some Overlay", "Ẩn Một Số Bar/Corner"),
        T("Show All Overlay", "Hồi Sinh Toàn Bộ Bar/Corner"),
        T("Trigger: Tap", "Giả Lập: Chạm"), T("Trigger: Double Tap", "Giả Lập: Chạm Đúp"), T("Trigger: Long Press", "Giả Lập: Giữ"),
        T("Trigger: Swipe Up", "Giả Lập: Vuốt Lên"), T("Trigger: Swipe Down", "Giả Lập: Vuốt Xuống"),
        T("Trigger: Swipe Left", "Giả Lập: Vuốt Trái"), T("Trigger: Swipe Right", "Giả Lập: Vuốt Phải"),
        T("Trigger: Diagonal", "Giả Lập: Chéo")};
for(int i=0; i<bK.length; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; }
// [XÓA] 2 vòng for sinh "INTENT_1".."INTENT_15" và "MACRO_1".."MACRO_5" — đây chính là
// LỖI GỐC (đọc key "intent_1_name" trong khi Intent thật lưu ở "intent_<uuid>_name").
// Đã thay bằng buildDynamicPackItems("intent_ids"/"macro_ids", ...) ở nơi dùng.
// V19.12.3.6.10: bỏ vol_on/vol_off khỏi component chung (đã có không gian VOLKEY riêng)
ALL_COMP_NAMES = new String[]{
    T("Bottom Right", "Thanh Đáy Phải"),
    T("Bottom Left", "Thanh Đáy Trái"),
    T("Top Right", "Thanh Cạnh Phải"),
    T("Top Left", "Thanh Cạnh Trái"),
    T("Top Center", "Thanh Đỉnh Giữa"),
    T("Bottom Center", "Thanh Đáy Giữa"),
    T("Left Center", "Thanh Cạnh Trái Giữa"),
    T("Right Center", "Thanh Cạnh Phải Giữa"),
    T("Right Up", "Thanh Cạnh Phải Trên"),
    T("Right Down", "Thanh Cạnh Phải Dưới"),
    T("Left Up", "Thanh Cạnh Trái Trên"),
    T("Left Down", "Thanh Cạnh Trái Dưới"),
    T("Corner BR", "Góc Viền Đáy Phải"),
    T("Corner BL", "Góc Viền Đáy Trái"),
    T("Corner TR", "Góc Viền Đỉnh Phải"),
    T("Corner TL", "Góc Viền Đỉnh Trái"),
    T("Fingerprint", "Vân Tay")
};
VOLKEY_COMP_NAMES = new String[]{T("Button Up", "Phím Tăng Âm"), T("Button Down", "Phím Giảm Âm")};
VOLKEY_GESTURE_NAMES = new String[]{T("Press Once", "Nhấn 1 Lần"), T("Press Twice", "Nhấn 2 Lần"), T("Hold", "Giữ (Long Press)")};
M_BAR_NAMES = new String[]{T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"), T("Top Right", "Cạnh Phải"), T("Top Left", "Cạnh Trái"), T("Top Center", "Đỉnh giữa"), T("Bottom Center", "Đáy Giữa"), T("Top Half Center", "Trung Tâm Trên"), T("Bottom Half Center", "Trung Tâm Dưới")};
C_GESTURE_NAMES = new String[]{T("Tap", "1 Chạm"), T("Double Tap", "2 Chạm"), T("Long Press", "Nhấn Giữ"), T("Swipe Up", "Vuốt Lên"), T("Swipe Down", "Vuốt Xuống"), T("Swipe Left", "Vuốt Trái"), T("Swipe Right", "Vuốt Phải"), T("Up + Hold", "Vuốt Lên + Giữ"), T("Down + Hold", "Vuốt Xuống + Giữ"), T("Left + Hold", "Vuốt Trái + Giữ"), T("Right + Hold", "Vuốt Phải + Giữ"), T("Diagonal", "Vuốt Chéo"), T("Diagonal + Hold", "Vuốt Chéo + Giữ")};
BAR_NAMES = new String[]{
    T("Bottom Center", "Đáy giữa"), T("Bottom Right", "Đáy phải"), T("Bottom Left", "Đáy trái"),
    T("Right Up", "Phải trên"), T("Right Center", "Phải giữa"), T("Right Down", "Phải dưới"),
    T("Top Center", "Đỉnh giữa"), T("Top Right", "Đỉnh phải"), T("Top Left", "Đỉnh trái"),
    T("Left Up", "Trái trên"), T("Left Center", "Trái giữa"), T("Left Down", "Trái dưới")
};
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
        // [TỐI ƯU PIXEL 2XL] Bỏ chặn data.getData() != null ở cấp cao nhất
        // vì ACTION_CREATE_SHORTCUT trả về data qua ParcelableExtra, getData() luôn null.
        if (res == RESULT_OK && data != null) {
            try {
                if (req == 101 && data.getData() != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                    os.write(new JSONObject(prefs.getAll()).toString().getBytes());
                    os.close();
                    Toast.makeText(this, T("Backup Saved!", "Đã Lưu Cấu Hình Backup!"), Toast.LENGTH_SHORT).show();
                } else if (req == 102 && data.getData() != null) {
                    java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder s = new StringBuilder(); String line;
                    while((line=r.readLine())!=null) s.append(line); r.close();
                    JSONObject j = new JSONObject(s.toString());
                    SharedPreferences.Editor ed = prefs.edit();
                    Iterator<String> k = j.keys();
                    while(k.hasNext()) {
                        String key = k.next(); Object v = j.get(key);
                        if(v instanceof Boolean) ed.putBoolean(key, (Boolean)v);
                        else if (v instanceof Integer) ed.putInt(key, (Integer)v);
                        else if (v instanceof Long) ed.putLong(key, (Long)v);
                        else if (v instanceof String) ed.putString(key, (String)v);
                    }
                    ed.commit(); Toast.makeText(this, T("Restored Successfully!", "Đã Khôi Phục Cấu Hình!"), Toast.LENGTH_LONG).show(); recreate();
} else if (req == REQ_PICK_SONGS) {
    List<Uri> picked = new ArrayList<>();
    if (data.getClipData() != null) {
        for (int k = 0; k < data.getClipData().getItemCount(); k++)
            picked.add(data.getClipData().getItemAt(k).getUri());
    } else if (data.getData() != null) {
        picked.add(data.getData());
    }
    List<String> ids = getDynamicIds("myplaylist_ids");
    SharedPreferences.Editor ed = prefs.edit();
    int added = 0;
    for (Uri u : picked) {
        try {
            getContentResolver().takePersistableUriPermission(u,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        String name = queryDisplayName(u);
        ed.putString("myplaylist_" + id + "_uri", u.toString());
        ed.putString("myplaylist_" + id + "_name", name);
        ids.add(id);
        added++;
    }
    ed.putString("myplaylist_ids", TextUtils.join(",", ids)).apply();
    if (added > 0) { Toast.makeText(this, T("Added "+added+" songs","Đã thêm "+added+" bài"), Toast.LENGTH_SHORT).show(); renderEcosystem(); }
                } else if (req == 104) {
                    try {
                        Intent shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
                        String shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
                        if (shortcutIntent == null) {
                            Toast.makeText(this, "Shortcut không hợp lệ!", Toast.LENGTH_SHORT).show();
} else if (req == REQ_UNINSTALL_CONFIRM) {
    doRevokeAdminAndUninstall();
} else {
                            String id = java.util.UUID.randomUUID().toString().substring(0, 8);
                            String iconPath = "";
                            Bitmap bmp = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
                            if (bmp != null) iconPath = ShortcutScanner.saveIconToFile(this, bmp, id);
                            String uri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME);
                            
                            // [THUẬT TOÁN MỚI] Tách bạch danh sách lưu trữ shortcut của Panel và Frontier
                            boolean isPanelSc = prefs.getBoolean("is_panel_shortcut_pending", false);
                            String targetList = isPanelSc ? "panel_shortcut_ids" : "shortcut_ids";
                            
                            String curIds = prefs.getString(targetList, "");
                            String newIds = curIds.isEmpty() ? id : curIds + "," + id;
                            prefs.edit()
                                .putString("shortcut_" + id + "_name", shortcutName == null ? "Shortcut" : shortcutName)
                                .putString("shortcut_" + id + "_intent_uri", uri)
                                .putString("shortcut_" + id + "_icon_path", iconPath)
                                .putString(targetList, newIds)
                                .putBoolean("is_panel_shortcut_pending", false) // Giải phóng RAM cờ
                                .apply();
                            if (pendingShortcutCallback != null) {
                                pendingShortcutCallback.accept(id, shortcutName == null ? "Shortcut" : shortcutName);
                            }
                            pendingShortcutCallback = null;
                            Toast.makeText(this, T("Shortcut saved!", "Đã lưu Shortcut!"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) { 
                        prefs.edit().putBoolean("is_panel_shortcut_pending", false).apply();
                        Toast.makeText(this, "Lỗi lưu Shortcut!", Toast.LENGTH_SHORT).show(); 
                    }
                }
            } catch(Exception e) { 
                prefs.edit().putBoolean("is_panel_shortcut_pending", false).apply();
                Toast.makeText(this, "IO Error!", Toast.LENGTH_LONG).show(); 
            }
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
    } else if (requestCode == 202) {
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, T("Camera permission granted!", "Đã cấp quyền Camera!"), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, T("Camera permission needed for QR scanning!", "Cần quyền Camera để quét QR!"), Toast.LENGTH_SHORT).show();
        }
        recreate();
    } else if (requestCode == 203) {
        if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Đã cấp quyền! Có thể phát My Playlist.", Toast.LENGTH_SHORT).show();
        }
        recreate();
    }
}
    private void performBack() {
    if (frontierSelectMode) { frontierSelectMode = false; frontierSelectedItems.clear(); renderRulesList(); return; }
    if (ecoSelectMode) { ecoSelectMode = false; ecoSelectedItems.clear(); renderEcosystem(); return; }
    if (panelSelectMode) { panelSelectMode = false; panelSelectedItems.clear(); renderPanelDesign(); return; }
    if (trashSelectMode) { trashSelectMode = false; trashSelectedItems.clear(); renderEcosystem(); return; }
    if (voiceSelectMode) { voiceSelectMode = false; voiceSelectedItems.clear(); renderEcosystem(); return; }
    // [THÊM DÒNG NÀY]
    if (videoSelectMode) { videoSelectMode = false; videoSelectedItems.clear(); renderEcosystem(); return; }
    if (myPlSelectMode) { myPlSelectMode = false; myPlSelectedItems.clear(); renderEcosystem(); return; }

    if (!navBackStack.isEmpty()) { navBackStack.pop().run(); return; }
    if (pageDesign != null && pageDesign.getVisibility() == View.VISIBLE) { closeDesignSpace(); return; }
    if ((pageConditions != null && pageConditions.getVisibility() == View.VISIBLE)
        || (pageEcosystem != null && pageEcosystem.getVisibility() == View.VISIBLE)
        || (pageEcoShowcase != null && pageEcoShowcase.getVisibility() == View.VISIBLE)
        || (pageSystemSpace != null && pageSystemSpace.getVisibility() == View.VISIBLE)) {
        showMainMenu(); return;
    }
    finish();
}

@Override public void onBackPressed() {
    performBack();
}

// ==================== TEST ACTION (thử nghiệm nhanh, không cần Save) ====================
// Dùng chung cơ chế IPC_ACTION đã có sẵn — đúng đường mà Rule/Panel/Tile thật sự chạy.
// Zero Service mới, hoạt động ở mọi trạng thái Lock/Homeb/Homacc vì cả 2 Service đều nghe.
private void fireTestAction(String actKey, String launchPkg, String shortcutId) {
    if (actKey == null || actKey.trim().isEmpty() || actKey.equals("NONE")) {
        Toast.makeText(this, T("Pick an action first!", "Hãy chọn hành động trước!"), Toast.LENGTH_SHORT).show();
        return;
    }
    String at = actKey.trim();
    Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
    if (at.equals("LAUNCH_APP")) {
        if (launchPkg == null || launchPkg.isEmpty()) {
            Toast.makeText(this, T("Pick an app first!", "Hãy chọn app trước!"), Toast.LENGTH_SHORT).show();
            return;
        }
        ipc.putExtra("act", "LAUNCH_APP"); ipc.putExtra("launch_pkg", launchPkg);
    } else if (at.equals("RUN_SHORTCUT")) {
        if (shortcutId == null || shortcutId.isEmpty()) {
            Toast.makeText(this, T("Pick a shortcut first!", "Hãy chọn shortcut trước!"), Toast.LENGTH_SHORT).show();
            return;
        }
        ipc.putExtra("act", "RUN_SHORTCUT"); ipc.putExtra("shortcut_id", shortcutId);
    } else if (at.startsWith("RUN_SHORTCUT_")) {
        ipc.putExtra("act", "RUN_SHORTCUT"); ipc.putExtra("shortcut_id", at.substring("RUN_SHORTCUT_".length()));
    } else {
        ipc.putExtra("act", at);
    }
    sendBroadcast(ipc);
    Toast.makeText(this, "▶ " + T("Testing: ", "Đang thử: ") + getActionLabelSmart(at, launchPkg), Toast.LENGTH_SHORT).show();
}

// 1 Rule có thể gán nhiều Action -> thử lần lượt, cách nhau 120ms (Zero Thread mới)
private void fireTestActions(java.util.Collection<String> acts, String launchPkg, String shortcutId) {
    if (acts == null || acts.isEmpty()) {
        Toast.makeText(this, T("Select at least 1 Action!", "Hãy chọn ít nhất 1 hành động!"), Toast.LENGTH_SHORT).show();
        return;
    }
    int delay = 0;
    for (String a : acts) {
        final String fa = a;
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> fireTestAction(fa, launchPkg, shortcutId), delay);
        delay += 120;
    }
}
// ==================== DRAG-TO-REORDER DÙNG CHUNG ====================
// Nhấn giữ 1 card đã build sẵn -> kéo đổi vị trí với card khác trong cùng
// list (áp dụng mọi lưới 2/3 cột). KHÔNG tạo View mới lúc kéo — chỉ hoán
// đổi vị trí trong `order` rồi gọi lại `rerender` 1 lần khi thả tay, nên
// Zero-RAM overhead ngoài lúc user thực sự đang kéo.
private void attachDragReorder(View card, List<String> order, String key, Runnable rerender) {
    final float[] startXY = new float[2];
    final boolean[] dragging = {false};
    card.setOnTouchListener((v, e) -> {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startXY[0] = e.getRawX(); startXY[1] = e.getRawY();
                return false; // để onClick/onLongClick của card vẫn hoạt động bình thường
            case MotionEvent.ACTION_MOVE:
                if (!dragging[0]) return false;
                // Tìm card khác đang bị đè lên bởi điểm chạm hiện tại
                ViewGroup parent = (ViewGroup) v.getParent().getParent(); // row -> grid container
                if (parent == null) return true;
                View target = findCardUnderTouch(parent, e.getRawX(), e.getRawY(), v);
                if (target != null) {
                    String myId = (String) v.getTag(); String otherId = (String) target.getTag();
                    if (myId != null && otherId != null) {
                        int i1 = order.indexOf(myId), i2 = order.indexOf(otherId);
                        if (i1 >= 0 && i2 >= 0 && i1 != i2) {
                            java.util.Collections.swap(order, i1, i2);
                            prefs.edit().putString(key, TextUtils.join(",", order)).apply();
                            rerender.run();
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging[0] = false;
                return false;
        }
        return false;
    });
    card.setOnLongClickListener(v -> { dragging[0] = true; v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); return true; });
}
// Danh sách thứ tự hiển thị các rule đã gán (persist riêng, không ảnh hưởng logic gán action)
private List<String> ruleOrderForKey(String prefix, String[] comps, String[] gestures) {
    List<String> saved = getDynamicIds("rule_order_" + prefix);
    List<String> real = new ArrayList<>();
    for (String c : comps) for (String g : gestures)
        if (!prefs.getString(prefix + c + "_" + g, "NONE").equals("NONE"))
            real.add(prefix + c + "_" + g);
    // giữ đúng thứ tự đã lưu, thêm rule mới vào cuối, bỏ rule đã xoá
    List<String> ordered = new ArrayList<>();
    for (String s : saved) if (real.contains(s)) ordered.add(s);
    for (String r : real) if (!ordered.contains(r)) ordered.add(r);
    return ordered;
}
private View findCardUnderTouch(ViewGroup grid, float rawX, float rawY, View exclude) {
    int[] loc = new int[2];
    for (int r = 0; r < grid.getChildCount(); r++) {
        View rowOrCard = grid.getChildAt(r);
        if (!(rowOrCard instanceof ViewGroup)) continue;
        ViewGroup row = (ViewGroup) rowOrCard;
        for (int c = 0; c < row.getChildCount(); c++) {
            View cell = row.getChildAt(c);
            if (cell == exclude || cell.getTag() == null) continue;
            cell.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + cell.getWidth()
                    && rawY >= loc[1] && rawY <= loc[1] + cell.getHeight()) return cell;
        }
    }
    return null;
}
    private void closeDesignSpace() {
    pageDesign.setVisibility(View.GONE);
    showMainMenu();
    }
private void openDesignSpace() { 
    currentMainTab = 0; refreshPreview();
    pageMainMenu.setVisibility(View.GONE); pageConditions.setVisibility(View.GONE);
    pageEcosystem.setVisibility(View.GONE); pageEcoShowcase.setVisibility(View.GONE); pageSystemSpace.setVisibility(View.GONE);
    pageDesign.setVisibility(View.VISIBLE); updateFabVisibility();
    // Về đúng Menu chính của hiển thị thay vì lao thẳng vào Anima
    designSpaceMenu.setVisibility(View.VISIBLE);
    designSliderContainer.setVisibility(View.GONE);
    designBackRow.setVisibility(View.GONE);
}
    // V19.12.3.6.10: FAB "+NEW EB" hiện ở mọi tab Điều kiện (kể cả LOCK) —
// riêng option vân tay đã bị loại khỏi component list của LOCK ngay trong
// buildRuleEditor(), nên không cần ẩn cả nút.
private void updateFabVisibility() {
        if (fab == null) return;
        
        // Xử lý hiển thị FAB (Viên thuốc) cho toàn bộ không gian Design - Tối ưu RAM Pixel 2 XL
if (currentMainTab == 0) {
    fab.setVisibility(View.VISIBLE);
    if (designTabState == 5) {
    fab.setOnClickListener(v -> {
        String newId = addDynamicId("pack_panel_ids");
        // [FIX] Bật sẵn "_en" ngay khi tạo — hộp thoại openDataPackEditor(type==2)
        // không còn công tắc Enable bên trong nên Panel mới tạo không có cách nào
        // được bật, khiến Panel Body không bao giờ xuất hiện dù Handle đã hoạt động.
        // Người dùng vẫn tắt được sau này bằng Switch trên card ở màn danh sách.
        prefs.edit().putBoolean("pack_panel_" + newId + "_en", true).apply();
        openDataPackEditor(2, newId);
    });
} else {
    fab.setOnClickListener(v -> openEmptyPillDialog());
}
    } else if (currentMainTab == 1) { // Condition Space
        if (currentGesTab == 5) { // FRONTIER — nút tròn compass thay cho "+NEW EB"
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> {
                if (frontierSubTab == 1) ensureHomeServiceForPreview();
                showCallPTDropdownFrontier();
            });
        } else {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(v -> openRuleBuilderDialog(null, -1, -1, ""));
        }
        } else if (currentMainTab == 2) { // Ecosystem Space
        fab.setVisibility(View.VISIBLE);
        if (ecoType == 0 || ecoType == 1 || ecoType == 2) {
            fab.setOnClickListener(v -> {
                String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
                String newId = addDynamicId(listKey);
                if (ecoType == 0) openIntentEditorV2(newId);
                else if (ecoType == 1) openTileEditorV2(newId);
                else openMacroEditorV2(newId);
            });
        } else if (ecoType == 3) {
            fab.setOnClickListener(v -> runDeepStorageScan());
        } else if (ecoType == 4) {
    if (soundMediaSubTab == -1) {
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> onBackPressed());
    } else if (soundMediaSubTab == 1) {
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> onBackPressed());
    } else if (soundMediaSubTab == 2) {
        // Không gian My Playlist: FAB dùng để mở Files by Google chọn bài hát
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> pickSongsForMyPlaylist());
    } else {
                boolean recOn = VoiceRecorderService.isRunning;
                boolean recPaused = VoiceRecorderService.isPaused;
                fab.setOnClickListener(v -> {
                    if (!recOn) {
                        if (android.content.pm.PackageManager.PERMISSION_GRANTED != checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
                            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 201);
                            return;
                        }
                        Intent i = new Intent(this, VoiceRecorderService.class);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    } else if (recPaused) {
                        Intent i = new Intent(this, VoiceRecorderService.class);
                        i.setAction(VoiceRecorderService.ACTION_PAUSE_TOGGLE);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                    } else {
                        Intent i = new Intent(this, VoiceRecorderService.class);
                        i.setAction(VoiceRecorderService.ACTION_STOP);
                        startService(i);
                    }
                    new Handler().postDelayed(this::updateFabVisibility, 300);
                });
            }
        } else {
            fab.setOnClickListener(v -> showPremiumDialog());
        }
    } else if (currentMainTab == -1 || currentMainTab == -2 || currentMainTab == -3) { 
        // Hiện FAB ở Màn Chính, Màn Hệ Sinh Thái, Màn Hệ Thống để mở mục Premium
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> showPremiumDialog());
    } else {
        fab.setVisibility(View.GONE);
    }
}
    // [ĐỔI HÀNH VI] Không còn liệt kê pack có sẵn từ PIECE — giờ tạo Bar/Corner
    // MỚI trực tiếp ngay trong Frontier, tự động gán vào applied_packs của
    // không gian đang đứng (Lock/Homeb/Homacc), rồi mở thẳng Format Bar/Corner.
    private void showCallPTDropdownFrontier() {
    String[] options = {"📊 " + T("Create Bar", "Tạo Bar"), "📐 " + T("Create Corner", "Tạo Corner")};
    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(T("New Data Pack", "Tạo Data Pack mới"))
        .setItems(options, (d, which) -> {
            String prefix = frontierSubTab==0 ? "lock_" : frontierSubTab==1 ? "home_" : "homacc_";
            String listKey = prefix + "applied_packs";
            java.util.List<String> currentPacks = getDynamicIds(listKey);
            int type = which;
            String newId = addDynamicId(type == 0 ? "pack_bar_ids" : "pack_corner_ids");
            String itemKey = (type == 0 ? "bar_" : "corner_") + newId;
            currentPacks.add(itemKey);
            prefs.edit().putString(listKey, TextUtils.join(",", currentPacks)).apply();
            // [FIX] Không mở Dialog mới NGAY trong callback của AlertDialog đang
            // tự-dismiss — 2 cửa sổ tranh focus cùng lúc khiến IME (Gboard) không
            // được hệ thống gọi lên. Dời việc mở editor sang sau khi dialog cũ
            // đã dismiss hẳn (post vào hàng đợi UI thread, độ trễ không cảm nhận được).
            new Handler(android.os.Looper.getMainLooper()).post(() -> openDataPackEditor(type, newId));
        }).show();
}
    private Button createCircleBtn(String icon, String color) { Button b = new Button(this); b.setText(icon); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setBackground(getRounded(color, 100f)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(130, 130); lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp); return b; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);syncVolumeService();updateFabVisibility();isVi = prefs.getBoolean("lang_vi", true); reloadActionLabels();syncAllTileComponentsOnBoot();
// [FIX CRASH] Dọn các icon_idx cũ bị lưu sai (>= 20) từ bug TILE_ICON_NAMES trước đây —
// không xoá lựa chọn của người dùng (vẫn giữ đúng icon vì QS_ICON_POOL vẫn còn icon đó),
// chỉ đảm bảo không còn dữ liệu nào có thể làm vỡ mảng nữa ở bất kỳ chỗ nào khác.
{
    java.util.Map<String, ?> allPrefs = prefs.getAll();
    SharedPreferences.Editor fixEd = null;
    for (String k : allPrefs.keySet()) {
        if (k.startsWith("tilev2_") && k.endsWith("_icon_idx")) {
            Object v = allPrefs.get(k);
            if (v instanceof Integer && (Integer) v >= 20) {
                // Không cần xoá — chỉ cần TILE_ICON_NAMES không còn bị index theo giá trị này nữa
                // (đã fix ở refreshIconLabel), nên giữ nguyên index thật để hiện đúng icon.
            }
        }
    }
}
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
LinearLayout headerRow = new LinearLayout(this);
headerRow.setOrientation(LinearLayout.HORIZONTAL);
headerRow.setPadding(0, 0, 0, 45);
headerRow.setGravity(Gravity.CENTER_VERTICAL);

// Cột trái: Tên App và Version (Phóng to lên 18sp)
LinearLayout leftCol = new LinearLayout(this);
leftCol.setOrientation(LinearLayout.VERTICAL);
leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
TextView title = new TextView(this);
title.setText(CURRENT_VERSION);
title.setTextColor(Color.parseColor("#E8EAED"));
title.setTextSize(20f); // Phóng to thêm 2 đơn vị
title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
title.setSingleLine(true);
leftCol.addView(title);

// Cột phải: Gộp nút Update và Uninstall nằm ngang nhau
LinearLayout rightCol = new LinearLayout(this);
rightCol.setOrientation(LinearLayout.HORIZONTAL);
rightCol.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.35f));

Button btnUninstallTop = createSystemBtn("Uninstall", "#D32F2F", "#FFFFFF");
btnUninstallTop.setTextSize(14f);
btnUninstallTop.setPadding(24, 12, 24, 12); // Tăng đệm dọc để chữ không lẹm đáy
LinearLayout.LayoutParams unTopLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
unTopLp.setMargins(4, 4, 4, 4); // Căn lề an toàn
btnUninstallTop.setLayoutParams(unTopLp);
btnUninstallTop.setMinimumHeight(0);
btnUninstallTop.setOnClickListener(v -> confirmThenUninstallApp());

Button btnUpdateTop = createSystemBtn("Update", "#333333", "#00E5FF");
btnUpdateTop.setTextSize(14f);
btnUpdateTop.setPadding(24, 12, 24, 12); // Tăng đệm dọc
LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
upLp.setMargins(4, 4, 4, 4);
btnUpdateTop.setLayoutParams(upLp);
btnUpdateTop.setMinimumHeight(0);
btnUpdateTop.setOnClickListener(v -> {
    revokeDeviceAdminIfActive();
    Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")); startActivity(i);
});
// Đảo vị trí: Uninstall thêm trước (nằm trái), Update thêm sau (nằm phải)
rightCol.addView(btnUninstallTop);
rightCol.addView(btnUpdateTop);
headerRow.addView(leftCol); headerRow.addView(rightCol);
main.addView(headerRow);
reloadActionLabels();
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText(T("⚠️ GRANT OVERLAY", "⚠️ CẤP QUYỀN LỚP PHỦ")); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

// --- DO NOT DISTURB (DND) --- Xin quyền ghi đè âm lượng (Volume Mapper)
android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
if (android.os.Build.VERSION.SDK_INT >= 23 && !nm.isNotificationPolicyAccessGranted()) {
    Button btnDnd = new Button(this);
    btnDnd.setText("⚠️ CẤP QUYỀN KHÔNG LÀM PHIỀN (DND)\nĐể gọi Screen Off/On bằng phím Âm lượng");
    btnDnd.setBackground(getRounded("#FF9800", 25f));
    btnDnd.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams dndLp = new LinearLayout.LayoutParams(-1, -2);
    dndLp.setMargins(0, 10, 0, 0);
    btnDnd.setLayoutParams(dndLp);
    btnDnd.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
    main.addView(btnDnd);
}
    android.os.PowerManager pmCheck = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
if (Build.VERSION.SDK_INT >= 23 && pmCheck != null
        && !pmCheck.isIgnoringBatteryOptimizations(getPackageName())) {
    Button btnBattery = new Button(this);
    btnBattery.setText("⚠️ TẮT TỐI ƯU HÓA PIN\nGiúp Phím Âm Lượng ổn định hơn khi tắt màn hình");
    btnBattery.setBackground(getRounded("#FF5722", 25f));
    btnBattery.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams battLp = new LinearLayout.LayoutParams(-1, -2);
    battLp.setMargins(0, 10, 0, 0);
    btnBattery.setLayoutParams(battLp);
    btnBattery.setOnClickListener(v -> {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    });
    main.addView(btnBattery);
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
        // --- CAMERA (để dùng Scan QR) ---
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Button btnCamera = new Button(this);
            btnCamera.setText("⚠️ CẤP QUYỀN CAMERA (Quét QR)");
            btnCamera.setBackground(getRounded("#009688", 25f));
            btnCamera.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams camLp = new LinearLayout.LayoutParams(-1, -2);
            camLp.setMargins(0, 10, 0, 0);
            btnCamera.setLayoutParams(camLp);
            btnCamera.setOnClickListener(v ->
                requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 202));
            main.addView(btnCamera);
        }
// --- READ_MEDIA_AUDIO (để dùng Phát My Playlist) ---
        String audioPerm = Build.VERSION.SDK_INT >= 33
            ? android.Manifest.permission.READ_MEDIA_AUDIO
            : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(audioPerm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Button btnAudio = new Button(this);
            btnAudio.setText("⚠️ CẤP QUYỀN NHẠC (Phát My Playlist)");
            btnAudio.setBackground(getRounded("#8BC34A", 25f));
            btnAudio.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams audLp = new LinearLayout.LayoutParams(-1, -2);
            audLp.setMargins(0, 10, 0, 0);
            btnAudio.setLayoutParams(audLp);
            btnAudio.setOnClickListener(v ->
                requestPermissions(new String[]{audioPerm}, 203));
            main.addView(btnAudio);
        }
// --- WRITE_SETTINGS (để dùng Auto-Rotate Toggle) ---
        btnWriteSettings = new Button(this);
        btnWriteSettings.setText("⚠️ CẤP QUYỀN SỬA CÀI ĐẶT HỆ THỐNG (Tự Động Xoay)");
        btnWriteSettings.setBackground(getRounded("#3F51B5", 25f));
        btnWriteSettings.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams wsLp = new LinearLayout.LayoutParams(-1, -2);
        wsLp.setMargins(0, 10, 0, 0);
        btnWriteSettings.setLayoutParams(wsLp);
        btnWriteSettings.setVisibility(android.provider.Settings.System.canWrite(this) ? View.GONE : View.VISIBLE);
        btnWriteSettings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        main.addView(btnWriteSettings);
            // --- DEVICE ADMIN (để Homeb tắt được màn hình, không cần adb) ---
        android.app.admin.DevicePolicyManager dpmCheck =
            (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminCheck =
            new android.content.ComponentName(this, HomebDeviceAdminReceiver.class);
        btnDeviceAdmin = new Button(this);
        btnDeviceAdmin.setText("⚠️ CẤP QUYỀN QUẢN TRỊ THIẾT BỊ\nĐể Homeb tắt được màn hình (Screen Off)");
        btnDeviceAdmin.setBackground(getRounded("#673AB7", 25f));
        btnDeviceAdmin.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams adminLp = new LinearLayout.LayoutParams(-1, -2);
        adminLp.setMargins(0, 10, 0, 0);
        btnDeviceAdmin.setLayoutParams(adminLp);
        btnDeviceAdmin.setVisibility(dpmCheck.isAdminActive(adminCheck) ? View.GONE : View.VISIBLE);
        btnDeviceAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminCheck);
            intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Cấp quyền này để Homeb tắt màn hình bằng nút Screen Off, không cần lệnh adb.");
            startActivity(intent);
        });
        main.addView(btnDeviceAdmin);

        // --- NOTIFICATION LISTENER ACCESS (để dùng PLAY_LAST_MUSIC) ---
        btnNotifListener = new Button(this);
        btnNotifListener.setText("⚠️ CẤP QUYỀN TRUY CẬP THÔNG BÁO\nĐể phát nhạc gần nhất từ Files by Google");
        btnNotifListener.setBackground(getRounded("#009688", 25f));
        btnNotifListener.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams notifLp = new LinearLayout.LayoutParams(-1, -2);
        notifLp.setMargins(0, 10, 0, 0);
        btnNotifListener.setLayoutParams(notifLp);
        btnNotifListener.setVisibility(isNotifListenerEnabled() ? View.GONE : View.VISIBLE);
        btnNotifListener.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        main.addView(btnNotifListener);
    // [XÓA] Ô tìm kiếm cố định đầu màn — cổng vào tìm kiếm giờ CHỈ còn nút 🔍 ở
    // bottomBar (btnSearch), tránh 2 cổng vào trùng chức năng cùng lúc chiếm RAM layout.
    navMain = new LinearLayout(this); navMain.setVisibility(View.GONE); // giữ field cũ, không dùng nữa
    pageMainMenu = new LinearLayout(this); pageMainMenu.setOrientation(LinearLayout.VERTICAL); buildMainMenuList();
    pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageDesign.setVisibility(View.GONE); buildDesignSpace();
    pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); pageConditions.setVisibility(View.GONE); buildConditionsSpace();
    pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); pageEcosystem.setVisibility(View.GONE); buildEcosystemSpace();
    pageEcoShowcase = new LinearLayout(this); pageEcoShowcase.setOrientation(LinearLayout.VERTICAL); pageEcoShowcase.setVisibility(View.GONE); buildEcoShowcaseSpace();
    pageSystemSpace = new LinearLayout(this); pageSystemSpace.setOrientation(LinearLayout.VERTICAL); pageSystemSpace.setVisibility(View.GONE); buildSystemSpace();

    main.addView(pageMainMenu); main.addView(pageDesign); main.addView(pageConditions);
    main.addView(pageEcosystem); main.addView(pageEcoShowcase); main.addView(pageSystemSpace);
    scroll.addView(main); rootLayout.addView(scroll);

        LinearLayout bottomBar = new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL); bottomBar.setGravity(Gravity.CENTER_VERTICAL); bottomBar.setBackground(getRounded("#1E1E1E", 100f)); bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2); bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); bLp.setMargins(40, 0, 40, 60); bottomBar.setLayoutParams(bLp);
        // Đưa nút Back xuống Nav Bar, sử dụng icon hệ thống (ic_menu_revert) để luôn hiển thị an toàn
        ImageButton btnBack = createIconCircleBtn(customIconRes("cycle_24px"), "#333333");
btnBack.setOnClickListener(v -> performBack());
btnBack.setOnLongClickListener(v -> { navBackStack.clear(); showMainMenu(); return true; });
// [FIX] Phóng to vùng chạm thật sự của nút Back — icon chỉ ~86px nhưng touch target
// nên rộng hơn để không bị "trượt" khi chạm hơi lệch mép, đặc biệt lúc thao tác 1 tay.
btnBack.post(() -> {
    android.graphics.Rect rect = new android.graphics.Rect();
    btnBack.getHitRect(rect);
    rect.top -= 24; rect.bottom += 24; rect.left -= 24; rect.right += 24;
    View parentOfBtn = (View) btnBack.getParent();
    parentOfBtn.setTouchDelegate(new android.view.TouchDelegate(rect, btnBack));
});
        etNavSearch = new EditText(this);
        etNavSearch.setHint(T("Search", "Tìm kiếm"));
        etNavSearch.setTextSize(16f);
        etNavSearch.setHintTextColor(Color.GRAY);
        etNavSearch.setTextColor(Color.WHITE);
        etNavSearch.setSingleLine(true);
        etNavSearch.setBackground(getRounded("#2C2C2C", 100f));
        etNavSearch.setPadding(30, 20, 30, 20);
        etNavSearch.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        etNavSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { liveSearchSettings(s.toString()); }
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
        });

        // Đổi icon Compass thành touch_app_24px, cho kích thước to lên (Padding giảm từ 34 -> 24)
        fab = createIconCircleBtn(customIconRes("bubble_chart_24px"), "#333333"); 
fab.setTag("fab");
fab.setPadding(22, 22, 22, 22); // đồng bộ với padding mới trong createIconCircleBtn
        fab.setOnClickListener(v -> {
            if (currentMainTab == 1) {
                openRuleBuilderDialog(null, -1, -1, ""); 
            } else if (currentMainTab == 2) {
                String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
                String newId = addDynamicId(listKey);
                if (ecoType == 0) openIntentEditorV2(newId);
                else if (ecoType == 1) openTileEditorV2(newId);
                else openMacroEditorV2(newId);
            }
        });

        bottomBar.addView(btnBack); bottomBar.addView(etNavSearch); bottomBar.addView(fab);
        rootLayout.addView(bottomBar);
showMainMenu();
        setContentView(rootLayout);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /** [FIX] Kiểm tra tại onResume() thay vì Intent extra + onNewIntent() — onResume()
     *  LUÔN chạy mỗi khi Activity thực sự hiện ra trước mắt user (cold start, warm
     *  restart, hay bring-to-front từ Recents), đảm bảo tuyệt đối luôn nhảy đúng tab
     *  Storage dù app đang ở trạng thái nào. Cờ lưu trong SharedPreferences (Zero-cost
     *  vì "prefs" đã có sẵn trong RAM) thay vì Intent extra dễ lệch thời điểm giữa
     *  các đời máy/ROM khác nhau. */
    private void checkPendingStorageScan() {
    if (!prefs.getBoolean("pending_storage_scan", false)) return;
    prefs.edit().putBoolean("pending_storage_scan", false).apply();
    // [FIX] navCondBtnRef/navEcoBtnRef không còn tồn tại sau khi bỏ nav bar cũ —
    // dùng openEco() (cổng chuẩn hiện tại) để mở thẳng tab Storage.
    openEco(3, false);
    runDeepStorageScan();
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
private void openSpace(int mainTabIdx) {
    currentMainTab = mainTabIdx;
    refreshPreview();
    pageMainMenu.setVisibility(View.GONE);
    pageDesign.setVisibility(View.GONE);
    pageEcoShowcase.setVisibility(View.GONE);
    pageSystemSpace.setVisibility(View.GONE);
    pageConditions.setVisibility(mainTabIdx == 1 ? View.VISIBLE : View.GONE);
    pageEcosystem.setVisibility(mainTabIdx == 2 ? View.VISIBLE : View.GONE);
    updateFabVisibility();
    if (mainTabIdx == 1) renderRulesList();
    if (mainTabIdx == 2) renderEcosystem();
}
private void openEco(int type, boolean showSubNav) {
    ecoType = type;
    if (type == 4) { soundMediaSubTab = -1; navBackStack.clear(); }
    openSpace(2);
    if (ecoMenuContainer != null) ecoMenuContainer.setVisibility(View.GONE);
    if (ecoSubHeader != null) ecoSubHeader.setVisibility(View.VISIBLE);
    if (ecoContainer != null) ecoContainer.setVisibility(View.VISIBLE);
    updateFabVisibility();
    renderEcosystem();
}
private void openEcoShowcase() {
    currentMainTab = -2;
    pageMainMenu.setVisibility(View.GONE); pageDesign.setVisibility(View.GONE);
    pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE);
    pageSystemSpace.setVisibility(View.GONE);
    pageEcoShowcase.setVisibility(View.VISIBLE);
    updateFabVisibility();
}
private void openSystemSpace() {
    currentMainTab = -3;
    pageMainMenu.setVisibility(View.GONE); pageDesign.setVisibility(View.GONE);
    pageConditions.setVisibility(View.GONE); pageEcosystem.setVisibility(View.GONE);
    pageEcoShowcase.setVisibility(View.GONE);
    pageSystemSpace.setVisibility(View.VISIBLE);
    updateFabVisibility();
}
private void showMainMenu() {
navBackStack.clear(); // ← THÊM DÒNG NÀY 
    currentMainTab = -1;
    pageDesign.setVisibility(View.GONE);
    pageConditions.setVisibility(View.GONE);
    pageEcosystem.setVisibility(View.GONE);
    pageEcoShowcase.setVisibility(View.GONE);
    pageSystemSpace.setVisibility(View.GONE);
    pageMainMenu.setVisibility(View.VISIBLE);
    updateFabVisibility();
    refreshPreview();
}
    // ==================== KHÔNG GIAN ĐIỀU KIỆN ====================
    private void buildConditionsSpace() {
    condBackRow = createBackRow(T("Gestures & Touch Zones","Cử chỉ & Vùng chạm"));
    pageConditions.addView(condBackRow);

    gesMenuContainer = new LinearLayout(this);
    gesMenuContainer.setOrientation(LinearLayout.VERTICAL);
    pageConditions.addView(gesMenuContainer);

    gesMenuContainer.addView(createSettingsRow("explore_24px", "Frontier",
        T("Lock · Homeb · Homacc", "Lock · Homeb · Homacc"),
        () -> openGesTab(5, "Frontier")));
    gesMenuContainer.addView(createSettingsRow("fingerprint_24px", "Texture",
        T("Fingerprint Gestures", "Cử chỉ vân tay"),
        () -> openGesTab(4, "Texture")));
    gesMenuContainer.addView(createSettingsRow("volume_up_24px", "VolKey",
        T("Volume Key Gestures", "Cử chỉ phím âm lượng"),
        () -> openGesTab(3, "VolKey")));
     gesMenuContainer.addView(createSettingsRow("flare_24px", "Sensor",
        T("Proximity Sensor", "Cảm biến tiệm cận (0% Battery)"),
        () -> openGesTab(6, "Sensor")));

    gesSubHeader = new LinearLayout(this);
    gesSubHeader.setOrientation(LinearLayout.HORIZONTAL);
    gesSubHeader.setGravity(Gravity.CENTER_VERTICAL);
    gesSubHeader.setPadding(0, 0, 0, 20);
    gesSubHeader.setVisibility(View.GONE);
    // [FIX] Bỏ ImageButton back riêng — chỉ Nav Bar có nút Back.
    tvGesSubTitle = new TextView(this);
    tvGesSubTitle.setTextColor(Color.parseColor("#00E5FF")); tvGesSubTitle.setTextSize(18);
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2); tlp.setMargins(20, 0, 0, 0);
    tvGesSubTitle.setLayoutParams(tlp);
    gesSubHeader.addView(tvGesSubTitle);
    pageConditions.addView(gesSubHeader);

    listRules = new LinearLayout(this);
    listRules.setOrientation(LinearLayout.VERTICAL);
    listRules.setVisibility(View.GONE);
    pageConditions.addView(listRules);
}
private void openGesTab(int tab, String title) {
    currentGesTab = tab;
    refreshPreview();
    if (tab == 5) frontierSpaceBuilt = false; // [FIX] mỗi lần vào lại Frontier -> dựng UI sạch từ đầu
     if (tab == 6) sensorSpaceBuilt = false; // THÊM DÒNG NÀY
    condBackRow.setVisibility(View.GONE);
    gesMenuContainer.setVisibility(View.GONE);
    gesSubHeader.setVisibility(View.VISIBLE);
tvGesSubTitle.setText("");
    listRules.setVisibility(View.VISIBLE);
    updateFabVisibility();
    renderRulesList();
    // [MỚI]
    navBackStack.push(() -> {
    gesMenuContainer.setVisibility(View.VISIBLE);
    gesSubHeader.setVisibility(View.GONE);
    listRules.setVisibility(View.GONE);
    condBackRow.setVisibility(View.VISIBLE);
    updateFabVisibility();
});
}
private String getSpacePrefix() {
    if (currentGesTab == 3) return "volkey_";
    switch (currentGesTab) {
        case 0: return "lock_";
        case 1: return "homacc_";
        case 2: return "home_";
        case 4: return "texture_";
        case 5: return "frontier_";
        default: return "home_";
    }
}
    private void renderRulesList() {
    if (currentGesTab == 5) {
        if (!frontierSpaceBuilt) {
            listRules.removeAllViews();
            buildFrontierSpaceOnce();
        } else if (frontierBackRowRef != null && frontierBackRowRef.getVisibility() == View.VISIBLE) {
            // [FIX] Đang đứng trong 1 không gian con -> CHỈ vẽ lại nội dung Data Pack,
            // KHÔNG dựng lại toàn bộ UI (đây là nguyên nhân gây "nhảy ra ngoài").
            redrawFrontierBody(frontierBodyContainer);
        }
        return;
    }
    if (currentGesTab == 6) {
        if (!sensorSpaceBuilt) {
            listRules.removeAllViews();
            buildSensorSpaceOnce();
        }
        return;
    }
    listRules.removeAllViews();
    final boolean isVolKeyMode = (currentGesTab == 3);
    final boolean isTextureMode = (currentGesTab == 4);
    String prefix = getSpacePrefix();
    String[] compsUsed = isVolKeyMode ? VOLKEY_COMPS : (isTextureMode ? new String[]{"fingerprint"} : ALL_COMP_KEYS);
    String[] compNamesUsed = isVolKeyMode ? VOLKEY_COMP_NAMES : (isTextureMode ? new String[]{T("Fingerprint", "Vân Tay")} : ALL_COMP_NAMES);
    String[] gesturesUsed = isVolKeyMode ? VOLKEY_GESTURES : C_GESTURES;
    String[] gestureNamesUsed = isVolKeyMode ? VOLKEY_GESTURE_NAMES : C_GESTURE_NAMES;
    String[] actKeysUsed = isVolKeyMode ? getVolKeyActKeys() : ACT_KEYS;
    String[] actLabsUsed = isVolKeyMode ? getVolKeyActLabs() : ACT_LABS;
    // Thứ tự hiển thị được lưu riêng, kéo-thả đổi được, tự thêm rule mới vào cuối
    List<String> ruleOrderKeys = new ArrayList<>();
    java.util.Map<String, int[]> keyToCG = new java.util.HashMap<>();
    for (int c = 0; c < compsUsed.length; c++)
        for (int g = 0; g < gesturesUsed.length; g++) {
            String k2 = prefix + compsUsed[c] + "_" + gesturesUsed[g];
            if (!prefs.getString(k2, "NONE").equals("NONE")) {
                keyToCG.put(k2, new int[]{c, g});
            }
        }
    List<String> savedOrder = getDynamicIds("rule_order_" + prefix);
    for (String s : savedOrder) if (keyToCG.containsKey(s)) ruleOrderKeys.add(s);
    for (String s : keyToCG.keySet()) if (!ruleOrderKeys.contains(s)) ruleOrderKeys.add(s);

    LinearLayout currentRow = null; int count = 0;
    for (String key : ruleOrderKeys) {
        int c = keyToCG.get(key)[0], g = keyToCG.get(key)[1];
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
        if (at.equals("RUN_SHORTCUT")) {
            if(actName.length() > 0) actName.append(" + ");
            String scId = prefs.getString(key + "_shortcut_id", "");
            actName.append("🔗 " + prefs.getString("shortcut_" + scId + "_name", "Shortcut"));
            continue;
        }
        if (at.startsWith("PANEL_")) {
            if(actName.length() > 0) actName.append(" + ");
            actName.append("📦 " + prefs.getString("pack_panel_" + at.substring(6) + "_name", "Panel"));
            continue;
        }
        if (at.startsWith("INTENT_")) {
            if(actName.length() > 0) actName.append(" + ");
            actName.append("⚡ " + prefs.getString("intent_" + at.substring(7) + "_name", "Intent"));
            continue;
        }
        if (at.startsWith("MACRO_")) {
            if(actName.length() > 0) actName.append(" + ");
            actName.append("🤖 " + prefs.getString("macro_" + at.substring(6) + "_name", "Macro"));
            continue;
        }
        for(int i = 0; i < actKeysUsed.length; i++) {
            if (actKeysUsed[i] != null && actKeysUsed[i].equals(at)) {
                if(actName.length() > 0) actName.append(" + ");
                actName.append(actLabsUsed[i]);
                break;
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
Button btnCopy = new Button(this); btnCopy.setText("TEST");
btnCopy.setBackground(getRounded("#FFC107", 14f));
btnCopy.setTextColor(Color.BLACK);
btnCopy.setTextSize(12.5f);
btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
btnCopy.setPadding(12, 10, 12, 10);
LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
btnLp.setMargins(0, 8, 0, 0); btnCopy.setLayoutParams(btnLp);
btnCopy.setMinimumHeight(88);
final String finalKeyForTest = key;
btnCopy.setOnClickListener(v -> fireTestActions(java.util.Arrays.asList(action.split(",")),
    prefs.getString(finalKeyForTest + "_launch_pkg", ""), prefs.getString(finalKeyForTest + "_shortcut_id", "")));
ctrlCol.addView(swOn); ctrlCol.addView(btnCopy);
    card.addView(optCol); card.addView(infoCol); card.addView(ctrlCol);
    card.setTag(key);

    // THUẬT TOÁN UX: CHẠM 1 LẦN -> MỞ EDIT DIALOG
    card.setOnClickListener(v -> openRuleBuilderDialog(key, finalC, finalG, ""));
    attachDragReorder(card, ruleOrderKeys, "rule_order_" + prefix, this::renderRulesList);
        // CHẠM GIỮ -> XOÁ QUY TẮC NÀY
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(T("Delete this rule?", "Xoá quy tắc này?"))
                .setPositiveButton(T("DELETE", "XOÁ"), (d,w) -> {
                    prefs.edit().putString(key, "NONE").apply();
                    if (isVolKeyMode) syncVolumeService();
                    renderRulesList();
                }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
            return true;
        });
    currentRow.addView(card); count++;
}
    }
        if(count % 2 != 0 && currentRow != null) { View dummy = new View(this); dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); currentRow.addView(dummy); }
        if(count == 0) { TextView empty = new TextView(this); empty.setText(T("No rules yet.\nPress + NEW EB to create.", "Chưa có quy tắc nào.\nBấm + NEW EB để tạo.")); empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0); listRules.addView(empty); }
    }
// [MỚI] Ngăn kéo tích chọn Bar/Corner sẽ bị ẩn khi action HIDE_SOME_OVERLAY chạy —
// lưu TOÀN CỤC theo prefix (lock_/home_/homacc_), không còn theo từng Rule nữa.
private void addHideTargetCheckboxes(LinearLayout container, String fullPrefKey, String[] keys, String[] names) {
    java.util.Set<String> selected = new java.util.LinkedHashSet<>();
    for (String s : prefs.getString(fullPrefKey, "").split(",")) if (!s.trim().isEmpty()) selected.add(s.trim());
    for (int i = 0; i < keys.length; i++) {
        CheckBox cb = new CheckBox(this);
        cb.setText(names[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0,10,0,10);
        cb.setChecked(selected.contains(keys[i]));
        final String fk = keys[i];
        cb.setOnCheckedChangeListener((v, checked) -> {
            java.util.Set<String> cur = new java.util.LinkedHashSet<>();
            for (String s : prefs.getString(fullPrefKey, "").split(",")) if (!s.trim().isEmpty()) cur.add(s.trim());
            if (checked) cur.add(fk); else cur.remove(fk);
            prefs.edit().putString(fullPrefKey, TextUtils.join(",", cur)).apply();
        });
        container.addView(cb);
    }
}
// Hàm dùng chung cho Frontier — KHÔNG động vào code Design (giữ nguyên để xoá sau).
// Battery/RAM Pixel 2XL: mỗi lần đổi subtab chỉ removeAllViews() 1 container nhỏ,
// KHÔNG đụng tới toàn bộ pageDesign — tránh re-inflate hàng loạt CheckBox/Slider
// không liên quan khi người dùng chỉ đang ở Frontier.
private void renderBarsCornersEditor(LinearLayout container, String prefix,
        String[] bKeys, String[] bNames, boolean isHomaccStyle) {
    LinearLayout gd = new LinearLayout(this);
    gd.setOrientation(LinearLayout.VERTICAL); gd.setPadding(30,10,30,30);
    gd.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"corner_hide_dur", 5000, 2500));
    gd.addView(createSlider("Độ mờ vùng TRĂNG NON", prefix+"corner_moon_alpha", 255, 100));
    gd.addView(createSlider("Độ mờ VIỀN GÓC", prefix+"corner_stroke_alpha", 255, 200));
    gd.addView(createSlider("Độ đậm viền", prefix+"corner_thick", 50, 8));
    
    // [FIX] Bọc Inner Drawer vào một Card sáng màu hơn (#2C2C2C) để nổi bật
    LinearLayout gdHide = new LinearLayout(this);
    gdHide.setOrientation(LinearLayout.VERTICAL); gdHide.setPadding(20,10,20,20);
    String[] cornerHideKeys = new String[CORNERS.length];
    for (int i=0;i<CORNERS.length;i++) cornerHideKeys[i] = "corner_"+CORNERS[i];
    addHideTargetCheckboxes(gdHide, prefix + "corner_hide_targets", cornerHideKeys, CORNER_NAMES);
    LinearLayout hideCornerWrap = new LinearLayout(this);
    hideCornerWrap.setBackground(getRounded("#2C2C2C", 20f));
    LinearLayout.LayoutParams hcLp = new LinearLayout.LayoutParams(-1, -2); hcLp.setMargins(0, 15, 0, 15);
    hideCornerWrap.setLayoutParams(hcLp);
    hideCornerWrap.addView(createDrawer("📁 " + T("Corners to hide","Góc viền cần ẩn") + " (▼)", gdHide));
    gd.addView(hideCornerWrap);
    
    container.addView(createDrawer("TÙY CHỈNH CHUNG GÓC VIỀN", gd));

    LinearLayout bd = new LinearLayout(this);
    bd.setOrientation(LinearLayout.VERTICAL); bd.setPadding(30,10,30,30);
    bd.addView(createSlider(T("Bar Corner Radius","Độ bo tròn Thanh Cạnh"), prefix+"bar_radius", 100, 24));
    bd.addView(createSlider("Thời gian chờ tắt tàng hình (ms)", prefix+"bar_hide_dur", 5000, 2500));
    bd.addView(createSlider(T("Icon Thickness on Bar","Độ đậm Icon trên Bar"), prefix+"bar_icon_alpha", 255, 255));
    bd.addView(createSlider(T("Icon Size on Bar","Kích thước Icon trên Bar"), prefix+"bar_icon_size", 120, 40));
    
    // [FIX] Bọc Inner Drawer cho Bar
    LinearLayout bdHide = new LinearLayout(this);
    bdHide.setOrientation(LinearLayout.VERTICAL); bdHide.setPadding(20,10,20,20);
    addHideTargetCheckboxes(bdHide, prefix + "bar_hide_targets", BARS, BAR_NAMES);
    LinearLayout hideBarWrap = new LinearLayout(this);
    hideBarWrap.setBackground(getRounded("#2C2C2C", 20f));
    hideBarWrap.setLayoutParams(hcLp);
    hideBarWrap.addView(createDrawer("📁 " + T("Bars to hide","Thanh cạnh cần ẩn") + " (▼)", bdHide));
    bd.addView(hideBarWrap);
    
    container.addView(createDrawer("TÙY CHỈNH CHUNG THANH CẠNH", bd));
}
    // [MỚI] Icon cho 13 cử chỉ — CHỈ hiện ở không gian Homacc, áp dụng CHUNG cho mọi
    // Bar/Corner của Homacc (giống Homeb). Thuật toán vẽ animation kéo icon ra theo
    // sóng làm sau — hiện tại chỉ lưu lựa chọn vào prefs.

// [MỚI] UI rỗng chọn icon cho 13 cử chỉ, dùng CHUNG cho mọi Bar/Corner của Homacc.
// Lazy-inflate (giống Panel Config/Handle Config) — 13 dòng chỉ thực sự dựng View
// khi người dùng bấm mở lần đầu, Zero-RAM khi đóng, tiết kiệm cho Pixel 2XL.
private LinearLayout buildGestureIconDrawer() {
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(20, 10, 20, 20);
    body.setVisibility(View.GONE);

    TextView header = new TextView(this);
    header.setText("📁 " + T("ICON FOR 21 GESTURES FRONTIER", "ICON CHO 21 CỬ CHỈ") + " (▼)");
    header.setTextColor(Color.parseColor("#00E5FF"));
    header.setPadding(30, 30, 30, 30);
    header.setTextSize(16);
    header.setBackground(getRounded("#202124", 25f));

    LinearLayout drawer = new LinearLayout(this);
    drawer.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
    dlp.setMargins(0, 15, 0, 5);
    drawer.setLayoutParams(dlp);
    drawer.addView(header);
    drawer.addView(body);

    final boolean[] inflated = {false};
    header.setOnClickListener(v -> {
        boolean willOpen = body.getVisibility() == View.GONE;
        if (willOpen && !inflated[0]) {
            inflated[0] = true;
            inflated[0] = true;
            body.addView(createSlider(T("Jump Icon Size", "Kích thước Icon Nhảy"), "homacc_jump_icon_size", 160, 90));
body.addView(createSlider(T("Jump Icon Opacity", "Độ đậm Icon Nhảy"), "homacc_jump_icon_alpha", 255, 255));
            body.addView(createSlider(T("Jump Up/Fall Duration (ms)", "Thời gian nhảy lên/rơi (ms)"), "homacc_jump_anim_dur", 3000, 1000));
            body.addView(createSlider(T("Jump Hold Duration (ms)","Thời gian giữ trên đỉnh (ms)"), "homacc_jump_hold_ms", 3000, 2000));
            body.addView(createSlider(T("Jump Distance (px)","Khoảng cách nhảy Icon (px)"), "homacc_jump_dist", 400, 160));
            for (int i = 0; i < C_GESTURES.length; i++) {
                final String gKey = "homacc_gesture_icon_" + C_GESTURES[i];
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 10, 0, 10);
                TextView tv = new TextView(this);
                tv.setText(C_GESTURE_NAMES[i]);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(13f);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                Button btnPick = new Button(this);
                btnPick.setBackground(getRounded("#303134", 16f));
                btnPick.setTextColor(Color.parseColor("#00E5FF"));
                btnPick.setTextSize(12f);
                btnPick.setPadding(20, 12, 20, 12);
                Runnable updateLabel = () -> {
                    String v2 = prefs.getString(gKey, "");
                    btnPick.setText(v2.isEmpty() ? T("Choose icon", "Chọn icon") : T("Icon set ✓", "Đã chọn ✓"));
                };
                updateLabel.run();
                // Tái dùng nguyên hàm showIconPickerDialog() đã có sẵn (Apps / System 20 / Custom 100)
                btnPick.setOnClickListener(v2 -> showIconPickerDialog(gKey, updateLabel));
                row.addView(tv); row.addView(btnPick);
                body.addView(row);
            }
        }
        body.setVisibility(willOpen ? View.VISIBLE : View.GONE);
        header.setText((willOpen ? "📂 " : "📁 ")
            + T("ICON FOR 21 GESTURES", "ICON CHO 21 CỬ CHỈ")
            + (willOpen ? " (▲)" : " (▼)"));
        header.setBackground(getRounded(willOpen ? "#333333" : "#202124", 25f));
    });

    return drawer;
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
private void renderAppliedPacksForSpaceInto(LinearLayout container, String prefix, int tabState, boolean isFrontier) {
    String listKey = prefix + "applied_packs";
    java.util.List<String> appliedPacks = getDynamicIds(listKey);
appliedPacks.sort((keyA, keyB) -> {
    boolean isBarA = keyA.startsWith("bar_"), isBarB = keyB.startsWith("bar_");
    String idA = keyA.replace(isBarA ? "bar_" : "corner_", "");
    String idB = keyB.replace(isBarB ? "bar_" : "corner_", "");
    String nameA = prefs.getString((isBarA ? "pack_bar_" : "pack_corner_") + idA + "_name", "");
    String nameB = prefs.getString((isBarB ? "pack_bar_" : "pack_corner_") + idB + "_name", "");
    return naturalCompareName(nameA, nameB);
});
    // [MULTI-SELECT] Thanh công cụ chỉ dựng khi ĐANG ở chế độ chọn nhiều —
    // Zero-RAM lúc bình thường, giống mọi khu vực lazy-inflate khác trong app.
    if (isFrontier && frontierSelectMode) {
        container.addView(buildFrontierSelectionToolbar(listKey, appliedPacks, prefix));
    }

    if (appliedPacks.isEmpty()) {
        if (!isFrontier) return; 
        TextView empty = new TextView(this);
        empty.setText(T("No Data Pack yet.\nTap 'NEW EB' to create Bar/Corner.", "Chưa có Data Pack nào.\nBấm 'NEW EB' để tạo Bar/Corner."));
        empty.setTextColor(Color.parseColor("#777777"));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, 60, 0, 0);
        container.addView(empty);
        return;
    }

    container.addView(createSectionTitle(isFrontier
        ? T("DATA PACK OF THIS SPACE", "DATA PACK CỦA KHÔNG GIAN NÀY")
        : " PACK ĐÃ GỌI TỪ PIECE"));

    LinearLayout currentRow = null;
    int count = 0;
    String[] bPos = {"BC", "R", "L", "RU", "RC", "RD", "TC", "TR", "TL", "LU", "LC", "LD"};
    String[] cPos = {"BR", "BL", "TR", "TL"};

    for (String itemKey : appliedPacks) {
        if (count % 2 == 0) {
            currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
            container.addView(currentRow);
        }

        boolean isBar = itemKey.startsWith("bar_");
        String id = itemKey.replace(isBar ? "bar_" : "corner_", "");
        String packPrefix = isBar ? "pack_bar_" : "pack_corner_";

        // FrameLayout bọc ngoài để có chỗ gắn chấm chọn (●) góc trên-phải khi vào chế độ multi-select
        FrameLayout cardWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wrapLp.setMargins(6, 6, 6, 6);
        cardWrap.setLayoutParams(wrapLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(getRounded("#202124", 24f));
        card.setPadding(15, 24, 10, 24);

        int locIdx = prefs.getInt(packPrefix + id + "_loc", 0);
        int visIdx = prefs.getInt(packPrefix + id + "_vis_mode", 0);
        int priIdx = prefs.getInt(packPrefix + id + "_pri_mode", 0);

        LinearLayout optCol = new LinearLayout(this);
        optCol.setOrientation(LinearLayout.VERTICAL);
        optCol.setGravity(Gravity.CENTER);
        optCol.setPadding(0, 0, 15, 0);
        String visIcon = visIdx == 0 ? "☠️" : (visIdx == 1 ? "👻" : "🕶️");
        String priIcon = priIdx == 0 ? "👆" : "👇";
        String shapeIcon = "";
        if (!isBar) {
            int shapeIdx = prefs.getInt(packPrefix + id + "_shape", 0);
            shapeIcon = "\n" + (shapeIdx == 0 ? "🔲" : (shapeIdx == 1 ? "➖" : "⏸️"));
        }
        TextView tIcons = new TextView(this);
        tIcons.setText(visIcon + "\n" + priIcon + shapeIcon);
        tIcons.setTextSize(15);
        tIcons.setLineSpacing(0, 1.2f);
        tIcons.setGravity(Gravity.CENTER);
        optCol.addView(tIcons);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        String posAbbr = isBar ? (locIdx >= 0 && locIdx < bPos.length ? bPos[locIdx] : "?") : (locIdx >= 0 && locIdx < cPos.length ? cPos[locIdx] : "?");
        TextView tName = new TextView(this);
        tName.setText("[" + posAbbr + "] " + prefs.getString(packPrefix + id + "_name", "Data Pack Mới"));
        tName.setTextColor(Color.parseColor("#E8EAED"));
        tName.setTextSize(16f);
        tName.setMaxLines(1); tName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        StringBuilder sb = new StringBuilder();
        sb.append("A:").append(prefs.getInt(packPrefix + id + "_alpha", 50))
          .append(" W:").append(prefs.getInt(packPrefix + id + "_w", isBar ? 300 : 100))
          .append(" H:").append(prefs.getInt(packPrefix + id + "_h", isBar ? 60 : 100))
          .append(" X:").append(prefs.getInt(packPrefix + id + "_x", 0))
          .append(" Y:").append(prefs.getInt(packPrefix + id + "_y", 0));
        if (!isBar) {
            sb.append("\nmW:").append(prefs.getInt(packPrefix + id + "_moon_w", 100))
              .append(" mH:").append(prefs.getInt(packPrefix + id + "_moon_h", 100))
              .append("\nR:").append(prefs.getInt(packPrefix + id + "_rad", 80));
        }
        TextView tSliders = new TextView(this);
        tSliders.setText(sb.toString());
        tSliders.setTextColor(Color.parseColor("#8AB4F8"));
        tSliders.setTextSize(11f);
        tSliders.setLineSpacing(0, 1.1f);
        tSliders.setPadding(0, 4, 0, 0);

        // [THÊM] Đếm số Pattern (prule_*) đã tạo cho Data Pack này — chỉ đọc lại
        // list CSV đã có sẵn trong SharedPreferences (đã cache trong RAM bởi hệ điều
        // hành), KHÔNG quét file/thư mục nào thêm → Zero I/O phụ trội trên Pixel 2XL.
        int patternCount = getDynamicIds(itemKey + "_pack_rules").size();
        TextView tPatternCount = new TextView(this);
        tPatternCount.setText("🎯 " + T("Patterns: ", "Pattern: ") + patternCount);
        tPatternCount.setTextColor(patternCount > 0 ? Color.parseColor("#4CAF50") : Color.parseColor("#777777"));
        tPatternCount.setTextSize(11f);
        tPatternCount.setPadding(0, 2, 0, 0);

        infoCol.addView(tName); infoCol.addView(tSliders); infoCol.addView(tPatternCount);
        LinearLayout ctrlCol = new LinearLayout(this);
        ctrlCol.setOrientation(LinearLayout.VERTICAL);
        ctrlCol.setGravity(Gravity.CENTER_HORIZONTAL);

        Switch swEn = new Switch(this);
        swEn.setChecked(prefs.getBoolean(prefix + itemKey + "_en", false));
        swEn.setScaleX(0.8f); swEn.setScaleY(0.8f);
        swEn.setOnCheckedChangeListener((sw, chk) -> {
    prefs.edit().putBoolean(prefix + itemKey + "_en", chk).apply();
    if (chk) {
        if (isBar) applyBarPackToSpace(id, prefix); else applyCornerPackToSpace(id, prefix);
    } else {
        if (isBar) disableBarPackFromSpace(id, prefix); else disableCornerPackFromSpace(id, prefix);
    }
});
        swEn.setPadding(0, 0, 0, 6);

        // [FIX] Bỏ nút TEST trên Data Pack MẸ (Frontier) — mẹ gộp nhiều Pattern/nhiều
// Action lại có thể xung đột nhau khi bắn cùng lúc. TEST giờ chỉ còn ở từng
// Pattern con (trong openPackRuleSpace), nơi 1 Pattern = 1 tổ hợp Action rõ ràng.
Button btnCopy = null;
if (!isFrontier) {
    btnCopy = new Button(this);
    btnCopy.setText("SHARE");
    btnCopy.setBackground(getRounded("#7C4DFF", 14f));
    btnCopy.setTextColor(Color.WHITE);
    btnCopy.setTextSize(11f);
    btnCopy.setPadding(10, 8, 10, 8);
    LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-2, -2); cpLp.setMargins(0, 4, 0, 0);
    btnCopy.setLayoutParams(cpLp); btnCopy.setMinimumHeight(64);
    final String fCopyItemKey = itemKey;
    btnCopy.setOnClickListener(v -> {
        java.util.Set<String> one = new java.util.LinkedHashSet<>();
        one.add(fCopyItemKey);
        java.util.Set<String> backup = frontierSelectedItems;
        frontierSelectedItems = one;
        showShareToSpaceDialog();
        frontierSelectedItems = backup;
    });
}
        // [ĐỔI HÀNH VI] Frontier: nút này đổi thành "PATTERN" -> mở kho biến con
        // (openPackRuleSpace), vì giờ chạm 1 lần vào card đã mở thẳng Editor cha rồi.
        final boolean fIsBar = isBar; final String fId = id;
final int fTabState = tabState;
        Button btnEdit = new Button(this);
        btnEdit.setBackground(getRounded("#00E5FF", 14f));
        btnEdit.setTextColor(Color.BLACK);
        btnEdit.setTextSize(11f);
        btnEdit.setPadding(10, 8, 10, 8);
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(-2, -2); edLp.setMargins(0, 4, 0, 0);
        btnEdit.setLayoutParams(edLp); btnEdit.setMinimumHeight(64);
        if (isFrontier) {
            btnEdit.setText("TRIGGER");
            btnEdit.setOnClickListener(v -> openPackRuleSpace(itemKey, fTabState));
        } else {
            btnEdit.setText(T("EDIT", "SỬA"));
            btnEdit.setOnClickListener(v -> openDataPackEditor(fIsBar ? 0 : 1, fId));
        }
        ctrlCol.addView(swEn); if (btnCopy != null) ctrlCol.addView(btnCopy); ctrlCol.addView(btnEdit);
        card.addView(optCol); card.addView(infoCol); card.addView(ctrlCol);
        cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

      final String fItemKey = itemKey;
        card.setTag(fItemKey);
        cardWrap.setTag(fItemKey);
        if (isFrontier) {
            if (frontierSelectMode) {
                // [YÊU CẦU 1] Chấm chọn dời xuống góc dưới-trái — dễ chạm bằng ngón cái hơn
                // khi cầm máy 1 tay trên màn hình lớn Pixel 2XL, không đụng vùng info/control
                // ở giữa và bên phải của card.
                TextView selDot = new TextView(this);
                boolean sel = frontierSelectedItems.contains(fItemKey);
                selDot.setText(sel ? "🔵" : "⚪");
                selDot.setTextSize(18);
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                dotLp.gravity = Gravity.BOTTOM | Gravity.START;
                dotLp.setMargins(10, 0, 0, 6);
                selDot.setLayoutParams(dotLp);
                cardWrap.addView(selDot);
                card.setOnClickListener(v -> {
                    if (frontierSelectedItems.contains(fItemKey)) frontierSelectedItems.remove(fItemKey);
                    else frontierSelectedItems.add(fItemKey);
                    renderRulesList();
                });
                card.setOnLongClickListener(v -> true);
            } else {
                // [ĐỔI HÀNH VI] Chạm 1 lần vào Data Pack ở Frontier -> mở thẳng Editor
                // của chính Data Pack cha (Format Bar/Corner qua openDataPackEditor).
                // Muốn vào kho Pattern con thì bấm nút "PATTERN" ở cột điều khiển bên phải.
                card.setOnClickListener(v -> {
                    String exKey = ensureExclusiveOwnership(fItemKey, prefix);
                    boolean exIsBar = exKey.startsWith("bar_");
                    String exId = exKey.replace(exIsBar ? "bar_" : "corner_", "");
                    if (!exKey.equals(fItemKey)) renderRulesList();
                    openDataPackEditor(exIsBar ? 0 : 1, exId);
                });
                card.setOnLongClickListener(v -> {
    frontierSelectMode = true;
    frontierSelectedItems.clear();
    frontierSelectedItems.add(fItemKey);
    renderRulesList();
    return true;
});
            }
        } else {
            card.setOnClickListener(btn -> openPackRuleSpace(fItemKey, fTabState));
            card.setOnLongClickListener(btn -> {
    new AlertDialog.Builder(this).setTitle("Gỡ Pack này khỏi không gian?")
        .setPositiveButton("GỠ", (d, w) -> {
            if (isBar) disableBarPackFromSpace(id, prefix); else disableCornerPackFromSpace(id, prefix);
            appliedPacks.remove(fItemKey);
            prefs.edit().putString(listKey, android.text.TextUtils.join(",", appliedPacks)).apply();
            renderSliders();
        }).setNegativeButton("HỦY", null).show();
    return true;
});
        }

        attachDragReorder(cardWrap, appliedPacks, listKey, isFrontier ? this::renderRulesList : this::renderSliders);
        currentRow.addView(cardWrap);
        count++;
    }

    if (count % 2 != 0 && currentRow != null) {
        View dummy = new View(this);
        dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        currentRow.addView(dummy);
    }
}
 // [MULTI-SELECT] Thanh công cụ Share/Delete/Cancel — chỉ tồn tại lúc đang chọn nhiều,
// GC thu hồi ngay khi thoát chế độ chọn (frontierSelectMode = false → không addView nữa)
private LinearLayout buildFrontierSelectionToolbar(String listKey, java.util.List<String> appliedPacks, String prefix) {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(frontierSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnShare = new Button(this); btnShare.setText("🔗 " + T("Share", "Chia sẻ"));
    btnShare.setBackground(getRounded("#7C4DFF", 20f)); btnShare.setTextColor(Color.WHITE); btnShare.setTextSize(12.5f);
    btnShare.setOnClickListener(v -> showShareToSpaceDialog());
Button btnDup = new Button(this); btnDup.setText("🧬 " + T("Duplicate", "Nhân bản"));
btnDup.setBackground(getRounded("#7C4DFF", 20f)); btnDup.setTextColor(Color.WHITE); btnDup.setTextSize(12.5f);
LinearLayout.LayoutParams dupLp = new LinearLayout.LayoutParams(-2, -2); dupLp.setMargins(10, 0, 0, 0);
btnDup.setLayoutParams(dupLp);
btnDup.setOnClickListener(v -> {
    for (String key : new java.util.ArrayList<>(frontierSelectedItems)) {
        boolean isBarL = key.startsWith("bar_");
        String idL = key.replace(isBarL ? "bar_" : "corner_", "");
        String newId = cloneDataPack(isBarL, idL);
        String newItemKey = (isBarL ? "bar_" : "corner_") + newId;
        clonePackRules(key, newItemKey);
        appliedPacks.add(newItemKey);
    }
    prefs.edit().putString(listKey, android.text.TextUtils.join(",", appliedPacks)).apply();
    frontierSelectMode = false; frontierSelectedItems.clear();
    renderRulesList();
});
    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", " 💫 Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);
    btnDelete.setOnClickListener(v -> {
    new AlertDialog.Builder(this).setTitle(T("Move to trash?", "Chuyển vào Kho Cũ?"))
        .setPositiveButton(T("MOVE", "CHUYỂN"), (d, w) -> {
            for (String key : new java.util.ArrayList<>(frontierSelectedItems)) moveDataPackToTrash(key);
            frontierSelectMode = false;
            frontierSelectedItems.clear();
            renderRulesList();
        }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
});
    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
    String allKeysListKey = (frontierSubTab==0?"lock_":frontierSubTab==1?"home_":"homacc_") + "applied_packs";
    java.util.Set<String> allKeys = new java.util.LinkedHashSet<>(getDynamicIds(allKeysListKey));
    if (frontierSelectedItems.equals(allKeys)) frontierSelectedItems.clear();
    else { frontierSelectedItems.clear(); frontierSelectedItems.addAll(allKeys); }
    renderRulesList();
});
    bar.addView(tvCount); bar.addView(btnShare); bar.addView(btnDup); bar.addView(btnAll); bar.addView(btnDelete);
    return bar;
}

// "Share" = thêm tham chiếu pack đã chọn vào applied_packs của không gian khác
// (KHÔNG nhân bản pack_bar_/pack_corner_ storage — chỉ dùng chung 1 pack ở nhiều nơi,
// đúng tinh thần "data pack tái sử dụng nhiều chỗ" thay vì tạo file trùng lặp).
private void showShareToSpaceDialog() {
    String[] spaces = {"LOCK", "HOMEB", "HOMACC"};
    String[] prefixes = {"lock_", "home_", "homacc_"};
    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(T("Share to space", "Chia sẻ sang không gian"))
        .setItems(spaces, (d, which) -> {
            String targetPrefix = prefixes[which];
            String targetListKey = targetPrefix + "applied_packs";
            java.util.List<String> targetPacks = getDynamicIds(targetListKey);
            int added = 0;
            for (String itemKey : frontierSelectedItems) {
                // [FIX] Clone thay vì reference — mỗi không gian giữ 1 bản độc lập,
                // đổi tên/action/vị trí ở đây không còn kéo theo không gian nguồn.
                boolean isBar = itemKey.startsWith("bar_");
                String oldId = itemKey.replace(isBar ? "bar_" : "corner_", "");
                String newId = cloneDataPack(isBar, oldId);
                String newItemKey = (isBar ? "bar_" : "corner_") + newId;
                targetPacks.add(newItemKey);
                added++;
            }
            prefs.edit().putString(targetListKey, TextUtils.join(",", targetPacks)).apply();
            frontierSelectMode = false;
            frontierSelectedItems.clear();
            Toast.makeText(this, T("Shared", "Đã chia sẻ") + " " + added + " pack!", Toast.LENGTH_SHORT).show();
            renderRulesList();
        }).show();
}

// [MỚI] Nhân bản toàn bộ Data Pack (Format + toàn bộ Pattern bên trong) sang ID mới
// hoàn toàn — chỉ copy String/Int/Boolean/Float/Long qua SharedPreferences, không
// tạo Thread/Service nào -> Zero RAM overhead ngoài lúc bấm Share.
private String cloneDataPackDeep(String itemKey) {
    boolean isBar = itemKey.startsWith("bar_");
    String oldId = itemKey.replace(isBar ? "bar_" : "corner_", "");
    String packPrefix = isBar ? "pack_bar_" : "pack_corner_";
    String newId = java.util.UUID.randomUUID().toString().substring(0, 8);
    String newItemKey = (isBar ? "bar_" : "corner_") + newId;

    java.util.Map<String, ?> all = prefs.getAll();
    SharedPreferences.Editor ed = prefs.edit();

    String oldPackPrefixFull = packPrefix + oldId + "_";
    for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
        String key = e.getKey();
        if (!key.startsWith(oldPackPrefixFull)) continue;
        String suffix = key.substring(oldPackPrefixFull.length());
        Object v = e.getValue();
        String newKey = packPrefix + newId + "_" + suffix;
        if (v instanceof Boolean) ed.putBoolean(newKey, (Boolean) v);
        else if (v instanceof Integer) ed.putInt(newKey, (Integer) v);
        else if (v instanceof Float) ed.putFloat(newKey, (Float) v);
        else if (v instanceof Long) ed.putLong(newKey, (Long) v);
        else if (v instanceof String) ed.putString(newKey, (String) v);
    }

    java.util.List<String> oldRules = getDynamicIds(itemKey + "_pack_rules");
    java.util.List<String> newRules = new ArrayList<>();
    for (String rId : oldRules) {
        String newRuleId = java.util.UUID.randomUUID().toString().substring(0, 8);
        newRules.add(newRuleId);
        String oldRulePrefixFull = "prule_" + rId + "_";
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(oldRulePrefixFull)) continue;
            String suffix = key.substring(oldRulePrefixFull.length());
            Object v = e.getValue();
            String newKey = "prule_" + newRuleId + "_" + suffix;
            if (v instanceof Boolean) ed.putBoolean(newKey, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(newKey, (Integer) v);
            else if (v instanceof String) ed.putString(newKey, (String) v);
        }
    }
    ed.putString(newItemKey + "_pack_rules", TextUtils.join(",", newRules));
    ed.apply();
    return newItemKey;
}
    private void buildFrontierSpaceOnce() {
    frontierSpaceBuilt = true;
    LinearLayout subTab = new LinearLayout(this);
    subTab.setOrientation(LinearLayout.VERTICAL);
    subTab.setPadding(0, 0, 0, 10);
    listRules.addView(subTab);

    LinearLayout frontierBackRow = new LinearLayout(this);
    frontierBackRow.setOrientation(LinearLayout.HORIZONTAL);
    frontierBackRow.setGravity(Gravity.CENTER_VERTICAL);
    frontierBackRow.setPadding(0, 0, 0, 20);
    frontierBackRow.setVisibility(View.GONE);
    TextView tvFrontierSubTitle = new TextView(this);
    tvFrontierSubTitle.setTextColor(Color.parseColor("#00E5FF")); tvFrontierSubTitle.setTextSize(16);
    LinearLayout.LayoutParams ftlp = new LinearLayout.LayoutParams(-2, -2); ftlp.setMargins(20, 0, 0, 0);
    tvFrontierSubTitle.setLayoutParams(ftlp);
    frontierBackRow.addView(tvFrontierSubTitle);
    listRules.addView(frontierBackRow);
    frontierBackRowRef = frontierBackRow;

    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setVisibility(View.GONE);
    listRules.addView(body);
    frontierBodyContainer = body;

    Object[][] spaces = {
        {"routine_24px", "HOMEB", T("No Accessibility Needed","Không cần Trợ năng"), 1},
        {"accessible_menu_24px", "HOMACC", T("Accessibility On","Có Trợ năng"), 2},
        {"mobile_lock_portrait_24px", "LOCK", T("Lock Screen","Màn hình khoá"), 0},
    };
    for (Object[] space : spaces) {
        final int spaceIdx = (int) space[3];
        final String spaceLabel = (String) space[1];
        LinearLayout row = createSettingsRow((String) space[0], spaceLabel, (String) space[2],
            () -> {
                frontierSubTab = spaceIdx;
                refreshPreview();
                if (spaceIdx == 1) ensureHomeServiceForPreview();
                subTab.setVisibility(View.GONE);
                gesSubHeader.setVisibility(View.GONE);
                frontierBackRow.setVisibility(View.VISIBLE);
                tvFrontierSubTitle.setText(spaceLabel);
                body.setVisibility(View.VISIBLE);
                redrawFrontierBody(body);
                updateFabVisibility();
                navBackStack.push(() -> {
    body.setVisibility(View.GONE);
    frontierBackRow.setVisibility(View.GONE);
    subTab.setVisibility(View.VISIBLE);
    gesSubHeader.setVisibility(View.VISIBLE);
    updateFabVisibility();
});
            });
        subTab.addView(row);
    }
}
private void redrawFrontierBody(LinearLayout body) {
        body.removeAllViews();
        String prefix = frontierSubTab == 0 ? "lock_" : frontierSubTab == 1 ? "home_" : "homacc_";
        // [TỐI ƯU PIXEL 2XL] Đã gỡ bỏ UI 3 nút Reset Space trong Frontier theo yêu cầu.
        renderBarsCornersEditor(body, prefix, BARS, BAR_NAMES, frontierSubTab == 2);
    int packTabState = frontierSubTab==0 ? 0 : frontierSubTab==1 ? 1 : 4;
    renderAppliedPacksForSpaceInto(body, prefix, packTabState, true);
}
// Chỉ khởi động HomescreenService khi thực sự cần xem trước Homeb —
// nếu service đã sống sẵn (do Morse hoặc Homeb thật đang bật) thì không làm
// gì thêm, tránh gọi startForegroundService() thừa (mỗi lần gọi = 1 IPC tốn pin).
private void ensureHomeServiceForPreview() {
    if (!HomescreenService.isRunning) {
        Intent i = new Intent(this, HomescreenService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }
}
    // [TỐI ƯU PIXEL 2XL] Không gian lưu Rule động cho Pack (Hiển thị 2 cột, 2 data pack 1 hàng)
    private void openPackRuleSpace(String appliedItemKey, int tabState) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    // [FIX HOMEB SYSTEM] tabState==1 tương ứng packTabState của HOMEB (xem redrawFrontierBody()).
    // Dùng để lọc bớt action cần Accessibility ở mục SYSTEM khi tạo Pattern cho Data Pack Homeb.
    final boolean isHomebSpace = (tabState == 1);
    prulesSelectMode = false;
    prulesSelectedItems.clear();
    RelativeLayout rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#000000"));

        ScrollView scroll = new ScrollView(this);
        RelativeLayout.LayoutParams rLp = new RelativeLayout.LayoutParams(-1, -1);
        rLp.bottomMargin = 240; // Né thanh Bottom Bar
        scroll.setLayoutParams(rLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(30, 50, 30, 40);

        content.addView(createSectionTitle("KHO RULE CHO PACK: " + appliedItemKey));

        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        // [MỚI] Ô tìm kiếm Pattern — lọc theo text Gesture + Action đang hiển thị trên card
        final String[] searchQuery = {""};

        Runnable[] renderRules = new Runnable[1];
        renderRules[0] = () -> {
            listContainer.removeAllViews();
            String listKey = appliedItemKey + "_pack_rules";
            java.util.List<String> rules = getDynamicIds(listKey);
            rules.sort((rA, rB) -> naturalCompareName(
                formatPruleGestureLabel(rA), formatPruleGestureLabel(rB)));
            if (prulesSelectMode) {
                listContainer.addView(buildPruleSelectionToolbar(appliedItemKey, rules, renderRules));
            }
            java.util.List<String> shownRules = new java.util.ArrayList<>();
            String q = searchQuery[0].trim().toLowerCase();
            for (String rIdF : rules) {
                if (q.isEmpty()) { shownRules.add(rIdF); continue; }
                String hay = (formatPruleGestureLabel(rIdF) + " " + formatPruleActionLabel(rIdF)).toLowerCase();
                if (hay.contains(q)) shownRules.add(rIdF);
            }
            if (rules.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("Chưa có Rule nào.\nBấm 'NEW EB' góc dưới để tạo.");
                empty.setTextColor(Color.GRAY);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, 100, 0, 0);
                listContainer.addView(empty);
                return;
            }
            
            LinearLayout currentRow = null;
            int count = 0;
            for (String rId : shownRules) {
                // Thuật toán chia cột (2 item / 1 hàng)
                if (count % 2 == 0) {
                    currentRow = new LinearLayout(this);
                    currentRow.setOrientation(LinearLayout.HORIZONTAL);
                    currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                    listContainer.addView(currentRow);
                }

                // Thẻ bao ngoài Data Pack
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackground(getRounded("#202124", 24f));
                card.setPadding(15, 24, 10, 24);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                lp.setMargins(6, 6, 6, 6);
                card.setLayoutParams(lp);

                // Cột 1: Icon (Rung/Anim)
                LinearLayout optCol = new LinearLayout(this);
                optCol.setOrientation(LinearLayout.VERTICAL);
                optCol.setGravity(Gravity.CENTER);
                optCol.setPadding(0, 0, 15, 0);
                TextView tIcons = new TextView(this);
                tIcons.setText((prefs.getBoolean("prule_" + rId + "_vib", true) ? "📳\n" : "") +
                               (prefs.getBoolean("prule_" + rId + "_anim", true) ? "✨" : ""));
                tIcons.setTextSize(16);
                optCol.addView(tIcons);

                // Cột 2: Text Gesture & Action
                LinearLayout infoCol = new LinearLayout(this);
                infoCol.setOrientation(LinearLayout.VERTICAL);
                infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                
                TextView tGest = new TextView(this);
tGest.setText(formatPruleGestureLabel(rId));
tGest.setTextColor(Color.parseColor("#9AA0A6"));
tGest.setTextSize(12);
tGest.setPadding(0, 5, 0, 5);
tGest.setMaxLines(1); tGest.setEllipsize(android.text.TextUtils.TruncateAt.END);

TextView tAct = new TextView(this);
tAct.setText(formatPruleActionLabel(rId));
                tAct.setTextColor(Color.parseColor("#8AB4F8"));
                tAct.setTextSize(16f);
                tAct.setMaxLines(1); tAct.setEllipsize(android.text.TextUtils.TruncateAt.END);

                infoCol.addView(tGest);
                infoCol.addView(tAct);

                // Cột 3: Switch & Copy
                LinearLayout ctrlCol = new LinearLayout(this);
                ctrlCol.setOrientation(LinearLayout.VERTICAL);
                ctrlCol.setGravity(Gravity.CENTER_HORIZONTAL);
                
                Switch swOn = new Switch(this);
                swOn.setChecked(prefs.getBoolean("prule_" + rId + "_en", true));
                swOn.setOnCheckedChangeListener((v, chk) -> prefs.edit().putBoolean("prule_" + rId + "_en", chk).apply());
                swOn.setPadding(0, 0, 0, 10);
                
                Button btnCopy = new Button(this);
                btnCopy.setText("TEST");
                btnCopy.setBackground(getRounded("#FFC107", 14f));
                btnCopy.setTextColor(Color.BLACK);
                btnCopy.setTextSize(12.5f);
                btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                btnCopy.setPadding(12, 10, 12, 10);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
                btnLp.setMargins(0, 8, 0, 0);
                btnCopy.setLayoutParams(btnLp);
btnCopy.setMinimumHeight(88);
final String rIdForTest = rId;
btnCopy.setOnClickListener(v -> {
    String acts = prefs.getString("prule_" + rIdForTest + "_acts", "");
    fireTestActions(java.util.Arrays.asList(acts.split(",")),
        prefs.getString("prule_" + rIdForTest + "_launch_pkg", ""),
        prefs.getString("prule_" + rIdForTest + "_shortcut_id", ""));
});
ctrlCol.addView(swOn);
ctrlCol.addView(btnCopy);
                card.addView(optCol);
                card.addView(infoCol);
                card.addView(ctrlCol);

                FrameLayout cardWrap = new FrameLayout(this);
                LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                wrapLp.setMargins(6, 6, 6, 6);
                cardWrap.setLayoutParams(wrapLp);
                cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

                final String fRId = rId;
                cardWrap.setTag(fRId);
                if (prulesSelectMode) {
                    TextView selDot = new TextView(this);
                    boolean sel = prulesSelectedItems.contains(fRId);
                    selDot.setText(sel ? "🔵" : "⚪");
                    selDot.setTextSize(18);
                    FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    dotLp.gravity = Gravity.BOTTOM | Gravity.START;
                    dotLp.setMargins(10, 0, 0, 6);
                    selDot.setLayoutParams(dotLp);
                    cardWrap.addView(selDot);
                    card.setOnClickListener(v -> {
                        if (prulesSelectedItems.contains(fRId)) prulesSelectedItems.remove(fRId);
                        else prulesSelectedItems.add(fRId);
                        renderRules[0].run();
                    });
                    card.setOnLongClickListener(v -> true);
                } else {
                    card.setOnClickListener(v -> openPackRuleEditor(appliedItemKey, fRId, null, renderRules[0], isHomebSpace));
                    card.setOnLongClickListener(v -> {
                        prulesSelectMode = true;
                        prulesSelectedItems.clear();
                        prulesSelectedItems.add(fRId);
                        renderRules[0].run();
                        return true;
                    });
                }

                attachDragReorder(cardWrap, rules, listKey, () -> renderRules[0].run());
                currentRow.addView(cardWrap);
                count++;
            }
            if(count % 2 != 0 && currentRow != null) { 
                View dummy = new View(this);
                dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f));
                currentRow.addView(dummy); 
            }
        };
        renderRules[0].run();

        scroll.addView(content);
        rootLayout.addView(scroll);

        // [FIX] Đồng bộ cấu trúc Nav Bar với màn chính: Back (trái) — Ô tìm kiếm (giữa)
        // — Nút tròn tạo Pattern (phải, dùng icon bubble_chart_24px giống FAB ngoài).
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackground(getRounded("#1E1E1E", 100f));
        bottomBar.setPadding(20, 20, 20, 20);
        RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(-1, -2);
        bLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        bLp.setMargins(40, 0, 40, 60);
        bottomBar.setLayoutParams(bLp);

        ImageButton btnBack = createIconCircleBtn(customIconRes("cycle_24px"), "#333333");
        // [FIX] Gọi d.dismiss() trực tiếp — onBackPressed() của Activity KHÔNG đóng
        // được Dialog này, khiến nút back trông như không hoạt động.
        btnBack.setOnClickListener(v -> {
            if (prulesSelectMode) { prulesSelectMode = false; prulesSelectedItems.clear(); renderRules[0].run(); }
            else d.dismiss();
        });

        EditText etSearchPattern = new EditText(this);
        etSearchPattern.setHint(T("Search", "Tìm kiếm"));
        etSearchPattern.setTextSize(16f);
        etSearchPattern.setHintTextColor(Color.GRAY);
        etSearchPattern.setTextColor(Color.WHITE);
        etSearchPattern.setSingleLine(true);
        etSearchPattern.setBackground(getRounded("#2C2C2C", 100f));
        etSearchPattern.setPadding(30, 20, 30, 20);
        etSearchPattern.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        etSearchPattern.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { searchQuery[0] = s.toString(); renderRules[0].run(); }
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
        });

        ImageButton fabNew = createIconCircleBtn(customIconRes("bubble_chart_24px"), "#333333");
        fabNew.setPadding(22, 22, 22, 22);
        fabNew.setOnClickListener(v -> openPackRuleEditor(appliedItemKey, null, null, renderRules[0], isHomebSpace));

        bottomBar.addView(btnBack);
        bottomBar.addView(etSearchPattern);
        bottomBar.addView(fabNew);
        rootLayout.addView(bottomBar);
        d.setOnDismissListener(dd -> renderRulesList());
        d.setContentView(rootLayout);
        d.setOnKeyListener((dg, keyCode, ev) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && prulesSelectMode) {
                prulesSelectMode = false; prulesSelectedItems.clear(); renderRules[0].run();
                return true;
            }
            return false;
        });
        d.show();
    }

private void showShareTargetPicker(java.util.Set<String> rIdsToShare, String currentItemKey, Runnable onDone) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(30, 80, 30, 30);

    TextView title = new TextView(this);
    title.setText(T("Share pattern(s) to...", "Chia sẻ Pattern sang..."));
    title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
    root.addView(title);

    LinearLayout tabs = new LinearLayout(this);
    tabs.setOrientation(LinearLayout.HORIZONTAL);
    Button bHomeb = createTabBtn("HOMEB"), bHomacc = createTabBtn("HOMACC"), bLock = createTabBtn("LOCK");
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,-2,1f);
    tlp.setMargins(0,0,10,0);
    bHomeb.setLayoutParams(tlp); bHomacc.setLayoutParams(tlp);
    bLock.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    tabs.addView(bHomeb); tabs.addView(bHomacc); tabs.addView(bLock);
    root.addView(tabs);

    TextView tvCount = new TextView(this);
    tvCount.setTextColor(Color.parseColor("#8AB4F8")); tvCount.setTextSize(12f);
    tvCount.setPadding(0, 14, 0, 6);
    root.addView(tvCount);

    ScrollView scroll = new ScrollView(this);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout grid = new LinearLayout(this);
    grid.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(grid);
    root.addView(scroll);

    final java.util.Set<String> selectedTargets = new java.util.LinkedHashSet<>();
    final int[] curTab = {0};
    String[] prefixes = {"home_", "homacc_", "lock_"};

    Runnable[] renderGrid = new Runnable[1];
    Runnable[] styleTabs = new Runnable[1];
    styleTabs[0] = () -> {
        bHomeb.setBackground(getRounded(curTab[0]==0?"#00E5FF":"#222222",20f)); bHomeb.setTextColor(curTab[0]==0?Color.BLACK:Color.WHITE);
        bHomacc.setBackground(getRounded(curTab[0]==1?"#00E5FF":"#222222",20f)); bHomacc.setTextColor(curTab[0]==1?Color.BLACK:Color.WHITE);
        bLock.setBackground(getRounded(curTab[0]==2?"#00E5FF":"#222222",20f)); bLock.setTextColor(curTab[0]==2?Color.BLACK:Color.WHITE);
    };
    renderGrid[0] = () -> {
        grid.removeAllViews();
        tvCount.setText(selectedTargets.size() + " " + T("selected", "đã chọn"));
        String spacePrefix = prefixes[curTab[0]];
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String key : getDynamicIds(spacePrefix + "applied_packs"))
            if (!key.equals(currentItemKey)) items.add(key);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(T("No Data Pack here", "Không có Data Pack nào"));
            empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,60,0,0);
            grid.addView(empty);
            return;
        }
        LinearLayout row = null;
        int count = 0;
        for (String itemKey : items) {
            if (count % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                grid.addView(row);
            }
            boolean isBar = itemKey.startsWith("bar_");
            String id = itemKey.replace(isBar ? "bar_" : "corner_", "");
            String pfx = isBar ? "pack_bar_" : "pack_corner_";
            String name = prefs.getString(pfx + id + "_name", "Data Pack");
            String tag = isBar ? "B" : "C";
            boolean sel = selectedTargets.contains(itemKey);

            FrameLayout cardWrap = new FrameLayout(this);
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            wLp.setMargins(6,6,6,6); cardWrap.setLayoutParams(wLp);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(getRounded(sel ? "#0D4A52" : "#202124", 20f));
            card.setPadding(24, 22, 24, 22);
            TextView tv = new TextView(this);
            tv.setText("[" + tag + "] " + name);
            tv.setTextColor(Color.parseColor("#E8EAED")); tv.setTextSize(13f);
            tv.setMaxLines(2); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(tv);
            cardWrap.addView(card);

            TextView selDot = new TextView(this);
            selDot.setText(sel ? "🔵" : "⚪");
            selDot.setTextSize(16);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.END; dLp.setMargins(0,0,10,6);
            selDot.setLayoutParams(dLp);
            cardWrap.addView(selDot);

            final String fKey = itemKey;
            card.setOnClickListener(v -> {
                if (selectedTargets.contains(fKey)) selectedTargets.remove(fKey);
                else selectedTargets.add(fKey);
                renderGrid[0].run();
            });
            row.addView(cardWrap);
            count++;
        }
        if (count % 2 != 0 && row != null) {
            View dummy = new View(this);
            dummy.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f));
            row.addView(dummy);
        }
    };
    View.OnClickListener tabClick = v -> {
        curTab[0] = v == bHomeb ? 0 : v == bHomacc ? 1 : 2;
        styleTabs[0].run();
        renderGrid[0].run();
    };
    bHomeb.setOnClickListener(tabClick); bHomacc.setOnClickListener(tabClick); bLock.setOnClickListener(tabClick);
    styleTabs[0].run();
    renderGrid[0].run();

    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY"));
    bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText(T("SHARE","CHIA SẺ"));
    bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0);
    bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        if (selectedTargets.isEmpty()) { Toast.makeText(this, T("Pick at least 1 target","Chọn ít nhất 1 nơi nhận"), Toast.LENGTH_SHORT).show(); return; }
        int totalCopied = 0;
        for (String targetItemKey : selectedTargets) {
            String targetListKey = targetItemKey + "_pack_rules";
            java.util.List<String> targetRules = getDynamicIds(targetListKey);
            for (String rId : rIdsToShare) {
                String newId = java.util.UUID.randomUUID().toString().substring(0, 8);
                targetRules.add(newId);
                prefs.edit()
                    .putString("prule_" + newId + "_gestures", prefs.getString("prule_" + rId + "_gestures", ""))
                    .putString("prule_" + newId + "_acts", prefs.getString("prule_" + rId + "_acts", ""))
                    .putString("prule_" + newId + "_launch_pkg", prefs.getString("prule_" + rId + "_launch_pkg", ""))
                    .putString("prule_" + newId + "_shortcut_id", prefs.getString("prule_" + rId + "_shortcut_id", ""))
                    .putBoolean("prule_" + newId + "_vib", prefs.getBoolean("prule_" + rId + "_vib", true))
                    .putBoolean("prule_" + newId + "_anim", prefs.getBoolean("prule_" + rId + "_anim", true))
                    .putBoolean("prule_" + newId + "_en", true)
                    .apply();
                totalCopied++;
            }
            prefs.edit().putString(targetListKey, TextUtils.join(",", targetRules)).apply();
        }
        Toast.makeText(this, T("Shared","Đã chia sẻ") + " " + totalCopied + " → " + selectedTargets.size() + " pack!", Toast.LENGTH_SHORT).show();
        if (onDone != null) onDone.run();
        d.dismiss();
    });

    d.setContentView(root); d.show();
}
     private void showShareRuleToPackDialog(String rId, String currentItemKey, Runnable onDone) {
    java.util.Set<String> one = new java.util.LinkedHashSet<>();
    one.add(rId);
    showShareTargetPicker(one, currentItemKey, onDone);
}
   private LinearLayout buildPruleSelectionToolbar(String appliedItemKey, java.util.List<String> rules, Runnable[] renderRules) {
    String listKey = appliedItemKey + "_pack_rules";
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(prulesSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnShare = new Button(this); btnShare.setText("🔗 " + T("Share", "Chia sẻ"));
    btnShare.setBackground(getRounded("#7C4DFF", 20f)); btnShare.setTextColor(Color.WHITE); btnShare.setTextSize(12.5f);
    btnShare.setOnClickListener(v -> showShareMultipleRulesToPackDialog(prulesSelectedItems, appliedItemKey, () -> {
        prulesSelectMode = false; prulesSelectedItems.clear(); renderRules[0].run();
    }));
Button btnDupP = new Button(this); btnDupP.setText("🧬 " + T("Duplicate", "Nhân bản"));
btnDupP.setBackground(getRounded("#7C4DFF", 20f)); btnDupP.setTextColor(Color.WHITE); btnDupP.setTextSize(12.5f);
LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(-2, -2); dpLp.setMargins(10, 0, 0, 0);
btnDupP.setLayoutParams(dpLp);
btnDupP.setOnClickListener(v -> {
    for (String rId : new java.util.ArrayList<>(prulesSelectedItems)) {
        String newRuleId = java.util.UUID.randomUUID().toString().substring(0, 8);
        rules.add(newRuleId);
        prefs.edit()
            .putString("prule_" + newRuleId + "_gestures", prefs.getString("prule_" + rId + "_gestures", ""))
            .putString("prule_" + newRuleId + "_acts", prefs.getString("prule_" + rId + "_acts", ""))
            .putString("prule_" + newRuleId + "_launch_pkg", prefs.getString("prule_" + rId + "_launch_pkg", ""))
            .putString("prule_" + newRuleId + "_shortcut_id", prefs.getString("prule_" + rId + "_shortcut_id", ""))
            .putBoolean("prule_" + newRuleId + "_vib", prefs.getBoolean("prule_" + rId + "_vib", true))
            .putBoolean("prule_" + newRuleId + "_anim", prefs.getBoolean("prule_" + rId + "_anim", true))
            .putBoolean("prule_" + newRuleId + "_en", true)
            .apply();
    }
    prefs.edit().putString(listKey, android.text.TextUtils.join(",", rules)).apply();
    prulesSelectMode = false; prulesSelectedItems.clear();
    renderRules[0].run();
});
    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);
    btnDelete.setOnClickListener(v -> {
        new AlertDialog.Builder(this).setTitle(T("Delete selected patterns?", "Xóa các Pattern đã chọn?"))
            .setPositiveButton(T("DELETE", "XÓA"), (d, w) -> {
                rules.removeAll(prulesSelectedItems);
                prefs.edit().putString(listKey, android.text.TextUtils.join(",", rules)).apply();
                prulesSelectMode = false;
                prulesSelectedItems.clear();
                renderRules[0].run();
            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
    });

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>(rules);
        if (prulesSelectedItems.equals(allKeys)) prulesSelectedItems.clear();
        else { prulesSelectedItems.clear(); prulesSelectedItems.addAll(allKeys); }
        renderRules[0].run();
    });
    bar.addView(tvCount); bar.addView(btnShare); bar.addView(btnDupP); bar.addView(btnAll); bar.addView(btnDelete);
    return bar;
}
private void showShareMultipleRulesToPackDialog(java.util.Set<String> rIds, String currentItemKey, Runnable onDone) {
    showShareTargetPicker(rIds, currentItemKey, onDone);
}
    // [TỐI ƯU PIXEL 2XL] Trình Editor Rule đặc biệt cho Pack (Không có mục Chọn Component)
    private void openPackRuleEditor(String appliedItemKey, String editId, String copyId, Runnable onRefresh, boolean isHomebSpace) {
    reloadActionLabels();
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(30, 120, 30, 30); // khớp buildRuleEditor — tránh dính status bar

    LinearLayout tabs = new LinearLayout(this);
    tabs.setOrientation(LinearLayout.HORIZONTAL);
    Button bTrig = createTabBtn("TRIGGER"); Button bAct = createTabBtn("ACTION");
    LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, -2, 1f);
    tabLp.setMargins(10, 0, 10, 0);
    bTrig.setLayoutParams(tabLp); bAct.setLayoutParams(tabLp);
    tabs.addView(bTrig); tabs.addView(bAct); root.addView(tabs);

    ScrollView scroll = new ScrollView(this);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(0, 40, 0, 0);
    scroll.addView(content);
    root.addView(scroll);
    String sourceId = editId != null ? editId : copyId;
    LinearLayout vTrig = new LinearLayout(this); vTrig.setOrientation(LinearLayout.VERTICAL);
    // ĐỒNG BỘ với buildRuleEditor() (Homacc/Volkey) — cùng màu #E91E63, cùng padding 20.
    // Pattern KHÔNG có mục "CHỌN COMPONENT" vì Pattern là Rule con nằm BÊN TRONG 1 Data Pack
    // Bar/Corner cụ thể rồi — nó áp dụng cho chính vùng của Pack đó, không cần chọn lại vùng.
    TextView tvG = new TextView(this);
    tvG.setText(T("CHOOSE GESTURES (OR logic)", "1. CHỌN CỬ CHỈ (Được chọn nhiều - Lệnh OR)"));
    tvG.setTextColor(Color.parseColor("#E91E63"));
    vTrig.addView(tvG);

    ArrayList<CheckBox> gestureBoxes = new ArrayList<>();
        String savedGestures = sourceId != null ? prefs.getString("prule_" + sourceId + "_gestures", "") : "";
        // FIX: Chọn độ dài mảng ngắn nhất để đảm bảo không index nào bị lọt ra ngoài (Out of Bounds)
        int safeLimit = Math.min(C_GESTURES.length, C_GESTURE_NAMES.length);
        for (int i = 0; i < safeLimit; i++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(C_GESTURE_NAMES[i]);
            cb.setTextColor(Color.WHITE);
            cb.setPadding(0, 20, 0, 20); // khớp buildRuleEditor
        cb.setChecked(("," + savedGestures + ",").contains("," + C_GESTURES[i] + ","));
        gestureBoxes.add(cb);
        vTrig.addView(cb);
    }
    LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE);
    vAct.addView(createSectionTitle("2. CHỌN HÀNH ĐỘNG (Được chọn nhiều)"));

    String savedActs = sourceId != null ? prefs.getString("prule_" + sourceId + "_acts", "") : "";
    final java.util.LinkedHashSet<String> selectedActs = new java.util.LinkedHashSet<>();
    for (String sa : savedActs.split(",")) if (!sa.trim().isEmpty()) selectedActs.add(sa.trim());

    final boolean[] launchAppSelected = { sourceId != null && selectedActs.contains("LAUNCH_APP") };
    final String[] launchAppPkg = { sourceId != null ? prefs.getString("prule_" + sourceId + "_launch_pkg", "") : "" };
    final boolean[] shortcutSelected = { sourceId != null && selectedActs.contains("RUN_SHORTCUT") };
    final String[] shortcutId = { sourceId != null ? prefs.getString("prule_" + sourceId + "_shortcut_id", "") : "" };

    LinearLayout rowApp = new LinearLayout(this);
    rowApp.setOrientation(LinearLayout.HORIZONTAL);
    rowApp.setGravity(Gravity.CENTER_VERTICAL);
    rowApp.setPadding(0, 0, 0, 20);
    Switch swApp = new Switch(this);
    swApp.setChecked(launchAppSelected[0]);
    swApp.setOnCheckedChangeListener((b,c) -> launchAppSelected[0] = c);
    swApp.setPadding(0, 0, 20, 0);
    Button btnPickApp = new Button(this);
    btnPickApp.setBackground(getRounded("#00E5FF", 20f));
    btnPickApp.setTextColor(Color.BLACK);
    btnPickApp.setTextSize(13.5f);
    btnPickApp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    btnPickApp.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Runnable updateApp = () -> btnPickApp.setText("📱 MỞ APP: " + (launchAppPkg[0].isEmpty() ? "CHƯA CHỌN" : getAppLabelCached(launchAppPkg[0])));
    updateApp.run();
    btnPickApp.setOnClickListener(v -> showSingleAppPickerDialogCallback(pkg -> { launchAppPkg[0] = pkg; swApp.setChecked(true); updateApp.run(); }));
    rowApp.addView(swApp); rowApp.addView(btnPickApp);
    vAct.addView(rowApp);

    LinearLayout rowSc = new LinearLayout(this);
    rowSc.setOrientation(LinearLayout.HORIZONTAL);
    rowSc.setGravity(Gravity.CENTER_VERTICAL);
    rowSc.setPadding(0, 0, 0, 20);
    Switch swSc = new Switch(this);
    swSc.setChecked(shortcutSelected[0]);
    swSc.setOnCheckedChangeListener((b,c) -> shortcutSelected[0] = c);
    swSc.setPadding(0, 0, 20, 0);
    Button btnPickSc = new Button(this);
    btnPickSc.setBackground(getRounded("#7C4DFF", 20f));
    btnPickSc.setTextColor(Color.WHITE);
    btnPickSc.setTextSize(13.5f);
    btnPickSc.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    btnPickSc.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Runnable updateSc = () -> btnPickSc.setText("🔗 SHORTCUT: " + (shortcutId[0].isEmpty() ? "CHƯA CHỌN" : prefs.getString("shortcut_" + shortcutId[0] + "_name", "?")));
    updateSc.run();
    btnPickSc.setOnClickListener(v -> showShortcutPickerDialog((idSc, name) -> { shortcutId[0] = idSc; swSc.setChecked(true); updateSc.run(); }));
    rowSc.addView(swSc); rowSc.addView(btnPickSc);
    vAct.addView(rowSc);
    // [FIX HOMEB SYSTEM] Homeb chạy dưới HomescreenService, KHÔNG có Accessibility —
    // BACK/RECENTS/SCREEN_OFF/POWER_DIALOG/SCREENSHOT/NOTIFICATIONS/SPLIT_SCREEN đều gọi
    // performGlobalAction() nên rơi vào default và không làm gì. Ẩn hẳn khỏi UI Homeb.
    String[] sysKeysForPack = isHomebSpace
        ? new String[]{"HOME", "FLASH", "VOLUME", "CAMERA", "SCREEN_OFF", "SCREENSHOT", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE"}
        : new String[]{"BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "QUICK_SETTINGS", "SPLIT_SCREEN", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE"};
        List<String[]> SYS_ITEMS = buildItemsForKeys(sysKeysForPack, ACT_KEYS, ACT_LABS);
    List<String[]> PANEL_ITEMS = buildDynamicPackItems("pack_panel_ids", "pack_panel_", "PANEL_", "Panel Mới");
    List<String[]> INTENT_ITEMS = buildDynamicPackItems("intent_ids", "intent_", "INTENT_", "Intent");
    List<String[]> MACRO_ITEMS = buildDynamicPackItems("macro_ids", "macro_", "MACRO_", "Macro");
        List<String[]> UTIL_ITEMS = buildItemsForKeys(new String[]{"HIDE_SOME_OVERLAY", "SHOW_ALL_OVERLAY", "TOGGLE_OVERLAY", "TOGGLE_RECORD", "PAUSE_RECORD", "YTDL_DOWNLOAD", "TOGGLE_WORK_PROFILE", "OPEN_STORAGE_SCAN", "SCAN_QR", "PLAY_MY_PLAYLIST"}, ACT_KEYS, ACT_LABS);
        vAct.addView(buildActionCategoryButton("SYSTEM", "⚙️", SYS_ITEMS, selectedActs, "#4CAF50"));
    vAct.addView(buildActionCategoryButton("UTILITIES", "🛠️", UTIL_ITEMS, selectedActs, "#FF9800"));
    if (!isHomebSpace) {
        List<String[]> TRIGGER_ITEMS_PACK = buildItemsForKeys(new String[]{
            "TRIGGER_TAP", "TRIGGER_DTAP", "TRIGGER_LONG",
            "TRIGGER_UP", "TRIGGER_DOWN", "TRIGGER_LEFT", "TRIGGER_RIGHT",
            "TRIGGER_DIAG"
        }, ACT_KEYS, ACT_LABS);
        vAct.addView(buildActionCategoryButton("GESTURES", "🌀", TRIGGER_ITEMS_PACK, selectedActs, "#009688"));
    }
    vAct.addView(buildActionCategoryButton("PANEL", "🗂️", PANEL_ITEMS, selectedActs, "#9C27B0", true));
    vAct.addView(buildActionCategoryButton("INTENTS", "⚡", INTENT_ITEMS, selectedActs, "#D32F2F"));
    vAct.addView(buildActionCategoryButton("MACROS", "🤖", MACRO_ITEMS, selectedActs, "#2196F3"));

    TextView tvOpt = new TextView(this);
    tvOpt.setText(T("\n3. CHOOSE OPTIONS", "\n3. CHỌN TÙY CHỌN"));
    tvOpt.setTextColor(Color.parseColor("#E91E63"));
    vTrig.addView(tvOpt);
    CheckBox cbVib = new CheckBox(this);
    cbVib.setText("Bật Rung (Haptic Feedback)");
    cbVib.setTextColor(Color.WHITE);
    cbVib.setChecked(sourceId == null || prefs.getBoolean("prule_" + sourceId + "_vib", true));
    vTrig.addView(cbVib);

    CheckBox cbAnim = new CheckBox(this);
    cbAnim.setText("Bật Hiệu ứng Ánh sáng (Animation)");
    cbAnim.setTextColor(Color.WHITE);
    cbAnim.setChecked(sourceId == null || prefs.getBoolean("prule_" + sourceId + "_anim", true));
    vTrig.addView(cbAnim);

    content.addView(vTrig); content.addView(vAct);
    View.OnClickListener tabClick = v -> {
        bTrig.setBackground(getRounded(v==bTrig?"#00E5FF":"#222222", 15f));
        bTrig.setTextColor(v==bTrig?Color.BLACK:Color.WHITE);
        bAct.setBackground(getRounded(v==bAct?"#00E5FF":"#222222", 15f));
        bAct.setTextColor(v==bAct?Color.BLACK:Color.WHITE);
        vTrig.setVisibility(v==bTrig?View.VISIBLE:View.GONE);
        vAct.setVisibility(v==bAct?View.VISIBLE:View.GONE);
    };
    bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick);
    bTrig.performClick();

    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setPadding(0, 20, 0, 0);

    Button bCancel = new Button(this);
    bCancel.setText("HỦY");
    bCancel.setBackground(getRounded("#333333", 20f));
    bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button bSave = new Button(this);
    bSave.setText("SAVE RULE");
    bSave.setBackground(getRounded("#4CAF50", 20f));
    bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
    slp.setMargins(20, 0, 0, 0);
    bSave.setLayoutParams(slp);

    footer.addView(bCancel);
    footer.addView(bSave);
    root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        ArrayList<String> gestures = new ArrayList<>();
        for (int i = 0; i < gestureBoxes.size(); i++)
            if (gestureBoxes.get(i).isChecked()) gestures.add(C_GESTURES[i]);

        if (gestures.isEmpty()) {
            Toast.makeText(this, "Hãy chọn ít nhất 1 Cử chỉ!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (launchAppSelected[0]) {
            if (launchAppPkg[0].isEmpty()) { Toast.makeText(this, "Chọn app trước!", Toast.LENGTH_SHORT).show(); return; }
            selectedActs.add("LAUNCH_APP");
        } else { selectedActs.remove("LAUNCH_APP"); }
        if (shortcutSelected[0]) {
            if (shortcutId[0].isEmpty()) { Toast.makeText(this, "Chọn shortcut trước!", Toast.LENGTH_SHORT).show(); return; }
            selectedActs.add("RUN_SHORTCUT");
        } else { selectedActs.remove("RUN_SHORTCUT"); }
        if (selectedActs.isEmpty()) { Toast.makeText(this, "Hãy chọn ít nhất 1 Hành động!", Toast.LENGTH_SHORT).show(); return; }

        String targetId = editId != null ? editId : java.util.UUID.randomUUID().toString().substring(0, 8);
        if (editId == null) {
            String listKey = appliedItemKey + "_pack_rules";
            java.util.List<String> curRules = getDynamicIds(listKey);
            curRules.add(targetId);
            prefs.edit().putString(listKey, android.text.TextUtils.join(",", curRules)).apply();
        }
                prefs.edit()
            .putString("prule_" + targetId + "_gestures", android.text.TextUtils.join(",", gestures))
            .putString("prule_" + targetId + "_acts", android.text.TextUtils.join(",", selectedActs))
            .putString("prule_" + targetId + "_launch_pkg", launchAppPkg[0])
            .putString("prule_" + targetId + "_shortcut_id", shortcutId[0])
            .putBoolean("prule_" + targetId + "_vib", cbVib.isChecked())
            .putBoolean("prule_" + targetId + "_anim", cbAnim.isChecked())
            .putBoolean("prule_" + targetId + "_en", true)
            .apply();
        reapplyPackIfEnabledByItemKey(appliedItemKey); // [MỚI] — đồng bộ action xuống lock_r_tap thật

        if (onRefresh != null) onRefresh.run();
        // [FIX] KHÔNG gọi renderRulesList() ở đây — nó rebuild renderFrontierSpace() và
        // làm UI nhảy về màn chọn không gian. Số đếm Pattern tự cập nhật khi đóng hẳn
        // Kho Pattern nhờ d.setOnDismissListener(dd -> renderRulesList()) trong openPackRuleSpace().
        d.dismiss();
    });
    d.setContentView(root);
    d.show();
}
    // [MỚI] Copy toàn bộ Pattern (gesture→action) của Data Pack xuống ĐÚNG component thật
// (lock_r_tap, home_corner_br_tap...) — đây là chỗ handleAction() thực sự đọc.
// Xóa key cũ trước để tránh "action ma" nếu Pattern bị đổi/xóa gesture.
private void applyPackRulesToSpace(String itemKey, String targetPrefix, String compKey) {
    for (String g : C_GESTURES) prefs.edit().remove(targetPrefix + compKey + "_" + g).apply();
    for (String rId : getDynamicIds(itemKey + "_pack_rules")) {
        if (!prefs.getBoolean("prule_" + rId + "_en", true)) continue;
        String gestures = prefs.getString("prule_" + rId + "_gestures", "");
        String acts = prefs.getString("prule_" + rId + "_acts", "");
        if (gestures.isEmpty() || acts.isEmpty()) continue;
        for (String g : gestures.split(",")) {
            if (g.trim().isEmpty()) continue;
            String finalKey = targetPrefix + compKey + "_" + g.trim();
            prefs.edit()
                .putString(finalKey, acts)
                .putBoolean(finalKey + "_vib", prefs.getBoolean("prule_" + rId + "_vib", true))
                .putBoolean(finalKey + "_anim", prefs.getBoolean("prule_" + rId + "_anim", true))
                .putString(finalKey + "_launch_pkg", prefs.getString("prule_" + rId + "_launch_pkg", ""))
                .putString(finalKey + "_shortcut_id", prefs.getString("prule_" + rId + "_shortcut_id", ""))
                .apply();
        }
    }
}
    private void applyBarPackToSpace(String id, String targetPrefix) {
        String src = "pack_bar_" + id + "_";
        int loc = prefs.getInt(src + "loc", 0);
        if (loc < 0 || loc >= BARS.length) loc = 0;

        // [MỚI] Nếu trước đó pack này từng nằm ở vị trí KHÁC trong CÙNG không gian
        // (VD: user đổi "Chọn vị trí Bar chính" từ Đáy Phải sang Đỉnh Giữa) — tắt & xóa
        // action tại vị trí cũ, tránh để lại 1 Bar "ma" vẫn hiện dù không còn Pack quản lý.
        String lastLocKey = src + "last_loc_" + targetPrefix;
        int lastLoc = prefs.getInt(lastLocKey, -1);
        if (lastLoc != -1 && lastLoc != loc) {
            String oldBarKey = targetPrefix + BARS[lastLoc] + "_";
            prefs.edit().putBoolean(oldBarKey + "en", false).apply();
            for (String g : C_GESTURES) prefs.edit().remove(oldBarKey + g).apply();
        }
        prefs.edit().putInt(lastLocKey, loc).apply();

        String barKey = targetPrefix + BARS[loc] + "_";
        prefs.edit()
            .putBoolean(barKey + "en", true)
            .putInt(barKey + "vis_mode", prefs.getInt(src + "vis_mode", 0))
            .putInt(barKey + "pri_mode", prefs.getInt(src + "pri_mode", 0))
            .putInt(barKey + "alpha", prefs.getInt(src + "alpha", 50))
            .putInt(barKey + "w", prefs.getInt(src + "w", 300))
            .putInt(barKey + "h", prefs.getInt(src + "h", 60))
            .putInt(barKey + "x", prefs.getInt(src + "x", 0))
            .putInt(barKey + "y", prefs.getInt(src + "y", 0))
            .putString(barKey + "icons", prefs.getString(src + "icons", ""))
            // [FIX] KHÔNG ghi snapshot icon_alpha/icon_size nữa -> luôn fallback về
            // key chung "<prefix>bar_icon_size/alpha" => thanh kéo chung áp dụng real-time.
            .remove(barKey + "icon_alpha")
            .remove(barKey + "icon_size")
            .putInt(barKey + "jumpdir", prefs.getInt(src + "jumpdir", 0))
            .apply();
        applyPackRulesToSpace("bar_" + id, targetPrefix, BARS[loc]); // [MỚI]
    }
    private void applyCornerPackToSpace(String id, String targetPrefix) {
        String src = "pack_corner_" + id + "_";
        int loc = prefs.getInt(src + "loc", 0);
        if (loc < 0 || loc >= CORNERS.length) loc = 0;

        // [MỚI] Dọn vị trí Corner cũ nếu user đổi vị trí trong cùng không gian
        String lastLocKey = src + "last_loc_" + targetPrefix;
        int lastLoc = prefs.getInt(lastLocKey, -1);
        if (lastLoc != -1 && lastLoc != loc) {
            String oldCornerKey = targetPrefix + "corner_" + CORNERS[lastLoc] + "_";
            prefs.edit().putBoolean(oldCornerKey + "en", false).apply();
            for (String g : C_GESTURES) prefs.edit().remove(oldCornerKey + g).apply();
        }
        prefs.edit().putInt(lastLocKey, loc).apply();

        String cornerKey = targetPrefix + "corner_" + CORNERS[loc] + "_";
        prefs.edit()
            .putBoolean(cornerKey + "en", true)
            .putInt(cornerKey + "jumpdir", prefs.getInt(src + "jumpdir", 0))
            .putInt(cornerKey + "vis_mode", prefs.getInt(src + "vis_mode", 0))
            .putInt(cornerKey + "pri_mode", prefs.getInt(src + "pri_mode", 0))
            .putInt(cornerKey + "shape", prefs.getInt(src + "shape", 0))
            .putInt(cornerKey + "w", prefs.getInt(src + "w", 100))
            .putInt(cornerKey + "h", prefs.getInt(src + "h", 100))
            .putInt(cornerKey + "x", prefs.getInt(src + "x", 0))
            .putInt(cornerKey + "y", prefs.getInt(src + "y", 0))
            .putInt(cornerKey + "moon_w", prefs.getInt(src + "moon_w", 100))
            .putInt(cornerKey + "moon_h", prefs.getInt(src + "moon_h", 100))
            .putInt(cornerKey + "moon_x", prefs.getInt(src + "moon_x", 1250))
            .putInt(cornerKey + "moon_y", prefs.getInt(src + "moon_y", 1250))
            .putInt(cornerKey + "rad", prefs.getInt(src + "rad", 80))
            .putInt(cornerKey + "moon_rad", prefs.getInt(src + "moon_rad", 80))
            .apply();

        applyPackRulesToSpace("corner_" + id, targetPrefix, "corner_" + CORNERS[loc]); // [MỚI]
    }
    private void disableBarPackFromSpace(String id, String targetPrefix) {
    int loc = prefs.getInt("pack_bar_" + id + "_loc", 0);
    if (loc < 0 || loc >= BARS.length) loc = 0;
    prefs.edit().putBoolean(targetPrefix + BARS[loc] + "_en", false).apply();
}

private void disableCornerPackFromSpace(String id, String targetPrefix) {
    int loc = prefs.getInt("pack_corner_" + id + "_loc", 0);
    if (loc < 0 || loc >= CORNERS.length) loc = 0;
    prefs.edit().putBoolean(targetPrefix + "corner_" + CORNERS[loc] + "_en", false).apply();
}
    // [MỚI] Với 1 Data Pack (type 0=Bar, 1=Corner), quét cả 3 không gian Lock/Home/Homacc —
// nếu Pack ĐANG được áp dụng (applied_packs) VÀ đang bật (Enable), đồng bộ lại giá trị
// mới nhất xuống không gian thật. Chỉ chạy khi bấm SAVE — event-driven, không tốn CPU nền.
private void reapplyPackIfEnabled(int type, String id) {
    if (type == 2) return; // Panel dùng cơ chế khác (đọc trực tiếp qua PanelEngine), không cần
    String itemKey = (type == 0 ? "bar_" : "corner_") + id;
    for (String spacePrefix : new String[]{"lock_", "home_", "homacc_"}) {
        boolean applied = getDynamicIds(spacePrefix + "applied_packs").contains(itemKey);
        boolean enabled = prefs.getBoolean(spacePrefix + itemKey + "_en", false);
        if (applied && enabled) {
            if (type == 0) applyBarPackToSpace(id, spacePrefix);
            else applyCornerPackToSpace(id, spacePrefix);
        }
    }
}

// Biến thể nhận itemKey ("bar_xxx"/"corner_xxx") — dùng trong openPackRuleEditor
private void reapplyPackIfEnabledByItemKey(String itemKey) {
    boolean isBar = itemKey.startsWith("bar_");
    String id = itemKey.replace(isBar ? "bar_" : "corner_", "");
    reapplyPackIfEnabled(isBar ? 0 : 1, id);
}
    private void openRuleBuilderDialogNoComp(String copyActs) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        d.setContentView(buildRuleEditor(d, null, -1, -1, copyActs));
        d.show();
    }
    private View buildRuleEditor(Dialog dialog, String editKey, int preComp, int preGes, String copyActs) {
        reloadActionLabels();
        final boolean isVolKeyMode = (currentGesTab == 3);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 120, 30, 30);
        
        LinearLayout tabs = new LinearLayout(this);
tabs.setOrientation (LinearLayout.HORIZONTAL);
Button bTrig = createTabBtn("TRIGGER"); Button bAct = createTabBtn("ACTION");
LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,-2,1f);
tabLp.setMargins (10,0,10,0);
bTrig.setLayoutParams(tabLp); bAct.setLayoutParams(tabLp);
tabs.addView(bTrig); tabs.addView(bAct); root.addView(tabs);
        ScrollView scroll = new ScrollView(this); scroll.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(0,40,0,0); scroll.addView(content); root.addView(scroll);

        final int[] selectedComp = {preComp != -1 ? preComp : 0}; 
        ArrayList<CheckBox> gestureBoxes = new ArrayList<>(); ArrayList<CheckBox> actionBoxes = new ArrayList<>();
        // V19.12.3.6.23: lưu key gesture thực tế song song với gestureBoxes —
        // bắt buộc vì Texture giờ KHÔNG tạo đủ 13 checkbox nữa nên không thể
        // suy ra gesture qua index cứng như trước.
        ArrayList<String> gestureKeys = new ArrayList<>();
        // V19.12.3.6.23 FIX: chuyển khai báo isTextureMode lên ĐÂY — biến này
        // được dùng bên trong spComp.setOnItemSelectedListener() phía dưới,
        // nên phải khai báo trước điểm sử dụng đầu tiên (Java yêu cầu forward
        // reference hợp lệ cho local variable, kể cả khi dùng trong anonymous class).
        final boolean isTextureMode = (currentGesTab == 4);
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
        if (currentGesTab == 4 && !ALL_COMP_KEYS[ci].equals("fingerprint")) continue; // Texture: Xóa tất cả, chỉ giữ lại vân tay
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
        // Texture đã filter checkbox từ lúc tạo — không gọi lại hàm này
        // để tránh lệch index (function cũ đọc C_GESTURES[i] theo vị trí
        // trong gestureBoxes, sai hoàn toàn khi list đã bị rút gọn)
        if (!isTextureMode) updateGestureVisibilityForFingerprint(selectedComp[0], gestureBoxes);
    }
    public void onNothingSelected(AdapterView<?> p){}
});
        }
        vTrig.addView(spComp);

        TextView tvG = new TextView(this); tvG.setText(T("\n2. CHOOSE GESTURES (OR logic)", "\n2. CHỌN CỬ CHỈ (Được chọn nhiều - Lệnh OR)")); tvG.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvG);
        final String[] gesturesShown = isVolKeyMode ? VOLKEY_GESTURES : C_GESTURES;
        final String[] gestureNamesShown = isVolKeyMode ? VOLKEY_GESTURE_NAMES : C_GESTURE_NAMES;
        // V19.12.3.6.23: Texture (vân tay) chỉ hỗ trợ 4 hướng vuốt — phần cứng
        // KHÔNG BAO GIỜ gửi lên tap/dtap/long/hold/diag. Thay vì tạo đủ 13
        // CheckBox rồi ẩn (updateGestureVisibilityForFingerprint cũ), giờ
        // KHÔNG allocate các CheckBox thừa ngay từ đầu — tiết kiệm RAM/CPU
        // inflate mỗi lần dialog này được mở trên Pixel 2XL.
        for (int i=0; i<gesturesShown.length; i++) {
            if (isTextureMode) {
                String gCheck = gesturesShown[i];
                boolean allowed = gCheck.equals("up") || gCheck.equals("down") || gCheck.equals("left") || gCheck.equals("right");
                if (!allowed) continue;
            }
            CheckBox cb = new CheckBox(this); cb.setText(gestureNamesShown[i]); cb.setTextColor(Color.WHITE); cb.setPadding(0,20,0,20); if(preGes != -1 && i == preGes) cb.setChecked(true); gestureBoxes.add(cb); gestureKeys.add(gesturesShown[i]); vTrig.addView(cb);
        }
        // Texture đã lọc sẵn ở trên nên bỏ qua — tránh gọi hàm này với danh sách
        // đã bị rút gọn (nếu không sẽ ẩn/tick nhầm checkbox do lệch index)
        if (!isTextureMode) updateGestureVisibilityForFingerprint(selectedComp[0], gestureBoxes);

        // [MỤC 3] Nhãn "3. CHỌN TÙY CHỌN" — đồng bộ style với mục 2 phía trên,
        // trước đây thiếu dòng tiêu đề này ở mọi tab khác (Lock/Home/Homacc...).
        TextView tvOpt = new TextView(this); tvOpt.setText(T("\n3. CHOOSE OPTIONS", "\n3. CHỌN TÙY CHỌN")); tvOpt.setTextColor(Color.parseColor("#E91E63")); vTrig.addView(tvOpt);

        LinearLayout vAct = new LinearLayout(this); vAct.setOrientation(LinearLayout.VERTICAL); vAct.setVisibility(View.GONE);
        TextView tvA = new TextView(this); tvA.setText(T("CHOOSE ACTIONS (Multi-select)", "CHỌN HÀNH ĐỘNG THỰC THI (Được chọn nhiều)")); tvA.setTextColor(Color.parseColor("#00E5FF")); tvA.setPadding(0,0,0,20); vAct.addView(tvA);
        
       final String[] actKeysUsed = isVolKeyMode ? getVolKeyActKeys() : ACT_KEYS;
final String[] actLabsUsed = isVolKeyMode ? getVolKeyActLabs() : ACT_LABS;
String savedActs = editKey != null ? prefs.getString(editKey, "") : copyActs;
String[] savedArray = savedActs.split(",");

final boolean[] launchAppSelected = { false };
final String[] launchAppPkg = { editKey != null ? prefs.getString(editKey + "_launch_pkg", "") : "" };
final String[] hideTargets = { editKey != null ? prefs.getString(editKey + "_hide_targets", "") : "" };
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
// Khôi phục lại đặc quyền cho VolKey Mode có thể dùng SCREEN_ON
        // [FIX] Homeb (currentGesTab==2, prefix "home_") chạy dưới HomescreenService — KHÔNG có
        // Accessibility, nên BACK/RECENTS/SCREEN_OFF/POWER_DIALOG/SCREENSHOT/NOTIFICATIONS/
        // SPLIT_SCREEN (đều gọi performGlobalAction()) sẽ rơi vào nhánh default và không chạy gì.
        // Ẩn hẳn khỏi UI Homeb để tránh cảm giác "chọn được nhưng bấm không có tác dụng".
        boolean isHomebSpace = (currentGesTab == 2);
        String[] sysKeys;
        if (isVolKeyMode) {
            sysKeys = new String[]{"BACK", "HOME", "RECENTS", "SCREEN_OFF", "SCREEN_ON", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "QUICK_SETTINGS", "SPLIT_SCREEN", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE"};
        } else if (isHomebSpace) {
            sysKeys = new String[]{"HOME", "FLASH", "VOLUME", "CAMERA", "SCREEN_OFF", "SCREENSHOT", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE"};
        } else {
            sysKeys = new String[]{"BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "QUICK_SETTINGS", "SPLIT_SCREEN", "SCREEN_RECORD", "AUTO_ROTATE_TOGGLE"};
        }
                List<String[]> SYS_ITEMS = buildItemsForKeys(sysKeys, actKeysUsed, actLabsUsed);
                List<String[]> PANEL_ITEMS = buildDynamicPackItems("pack_panel_ids", "pack_panel_", "PANEL_", "Panel Mới");
        vAct.addView(buildActionCategoryButton("SYSTEM", "⚙️", SYS_ITEMS, selectedActs, "#4CAF50"));
                // Chỉ hiện nhóm lệnh Giả lập Cử chỉ (Cần Trợ năng) nếu KHÔNG ở trong Homeb
        if (!isVolKeyMode && !isHomebSpace) {
            List<String[]> TRIGGER_ITEMS = buildItemsForKeys(new String[]{
                "TRIGGER_TAP", "TRIGGER_DTAP", "TRIGGER_LONG",
                "TRIGGER_UP", "TRIGGER_DOWN", "TRIGGER_LEFT", "TRIGGER_RIGHT",
                "TRIGGER_DIAG"
            }, actKeysUsed, actLabsUsed);
            vAct.addView(buildActionCategoryButton("GESTURES", "🌀", TRIGGER_ITEMS, selectedActs, "#009688"));
        }
        if (!isLockSpace && !isVolKeyMode) {
            LinearLayout rowApp = new LinearLayout(this);
            rowApp.setOrientation(LinearLayout.HORIZONTAL);
            rowApp.setGravity(Gravity.CENTER_VERTICAL);
            rowApp.setPadding(0, 0, 0, 20);
            Switch swApp = new Switch(this);
            swApp.setChecked(launchAppSelected[0]);
            swApp.setOnCheckedChangeListener((b,c) -> launchAppSelected[0] = c);
            swApp.setPadding(0, 0, 20, 0);
            Button btnPickApp = new Button(this);
            btnPickApp.setBackground(getRounded("#00E5FF", 20f));
            btnPickApp.setTextColor(Color.BLACK);
            btnPickApp.setTextSize(13.5f);
            btnPickApp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            btnPickApp.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            Runnable updateApp = () -> btnPickApp.setText("📱 MỞ APP: " + (launchAppPkg[0].isEmpty() ? "CHƯA CHỌN" : getAppLabelCached(launchAppPkg[0])));
            updateApp.run();
            btnPickApp.setOnClickListener(v -> showSingleAppPickerDialogCallback(pkg -> { launchAppPkg[0] = pkg; swApp.setChecked(true); updateApp.run(); }));
            rowApp.addView(swApp); rowApp.addView(btnPickApp);
            vAct.addView(rowApp);

            LinearLayout rowSc = new LinearLayout(this);
            rowSc.setOrientation(LinearLayout.HORIZONTAL);
            rowSc.setGravity(Gravity.CENTER_VERTICAL);
            rowSc.setPadding(0, 0, 0, 20);
            Switch swSc = new Switch(this);
            swSc.setChecked(shortcutSelected[0]);
            swSc.setOnCheckedChangeListener((b,c) -> shortcutSelected[0] = c);
            swSc.setPadding(0, 0, 20, 0);
            Button btnPickSc = new Button(this);
            btnPickSc.setBackground(getRounded("#7C4DFF", 20f));
            btnPickSc.setTextColor(Color.WHITE);
            btnPickSc.setTextSize(13.5f);
            btnPickSc.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            btnPickSc.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            Runnable updateSc = () -> btnPickSc.setText("🔗 SHORTCUT: " + (shortcutId[0].isEmpty() ? "CHƯA CHỌN" : prefs.getString("shortcut_" + shortcutId[0] + "_name", "?")));
            updateSc.run();
            btnPickSc.setOnClickListener(v -> showShortcutPickerDialog((idSc, name) -> { shortcutId[0] = idSc; swSc.setChecked(true); updateSc.run(); }));
            rowSc.addView(swSc); rowSc.addView(btnPickSc);
            vAct.addView(rowSc);
        } else {
            launchAppSelected[0] = false;
            shortcutSelected[0] = false;
        }
        if (isVolKeyMode) {
            // [MỚI] VolKey: chỉ hiện Action nào hoạt động tốt lúc màn tắt (không cần
            // mở UI/dialog) — TOGGLE_WORK_PROFILE bị loại vì key này chưa từng được
            // khai báo trong mảng ACT_KEYS/ACT_LABS nên chọn cũng không hiện ra được.
           List<String[]> VOLKEY_UTIL_ITEMS = buildItemsForKeys(
                new String[]{"TOGGLE_RECORD", "PAUSE_RECORD", "TOGGLE_OVERLAY", "PLAY_MY_PLAYLIST"},
                actKeysUsed, actLabsUsed);
            vAct.addView(buildActionCategoryButton("UTILITIES", "🛠️", VOLKEY_UTIL_ITEMS, selectedActs, "#FF9800"));
        } else {
                        List<String[]> UTIL_ITEMS = buildItemsForKeys(new String[]{"HIDE_SOME_OVERLAY", "SHOW_ALL_OVERLAY", "TOGGLE_OVERLAY", "TOGGLE_RECORD", "PAUSE_RECORD", "YTDL_DOWNLOAD", "TOGGLE_WORK_PROFILE", "OPEN_STORAGE_SCAN", "SCAN_QR", "PLAY_MY_PLAYLIST"}, actKeysUsed, actLabsUsed);
            List<String[]> INTENT_ITEMS = buildDynamicPackItems("intent_ids", "intent_", "INTENT_", "Intent");
            List<String[]> MACRO_ITEMS = buildDynamicPackItems("macro_ids", "macro_", "MACRO_", "Macro");
            vAct.addView(buildActionCategoryButton("UTILITIES", "🛠️", UTIL_ITEMS, selectedActs, "#FF9800"));
            vAct.addView(buildActionCategoryButton("PANEL", "🗂️", PANEL_ITEMS, selectedActs, "#9C27B0", true));
            vAct.addView(buildActionCategoryButton("INTENTS", "⚡", INTENT_ITEMS, selectedActs, "#D32F2F"));
            vAct.addView(buildActionCategoryButton("MACROS", "🤖", MACRO_ITEMS, selectedActs, "#2196F3"));
        }
cbVib.setText(T("Haptic Feedback", "Bật Rung (Haptic Feedback)"));
cbVib.setTextColor(Color.WHITE); cbVib.setChecked(editKey == null ||
prefs.getBoolean(editKey+"_vib", true)); vTrig.addView(cbVib);
cbAnim.setText(T("Show Animation", "Bật Hiệu ứng Ánh sáng (Animation)"));
cbAnim.setTextColor(Color.WHITE); cbAnim.setChecked(editKey == null ||
prefs.getBoolean(editKey+"_anim", true));
if (!isVolKeyMode) vTrig.addView(cbAnim); else cbAnim.setChecked(false);

content.addView(vTrig); content.addView(vAct);
View.OnClickListener tabClick = v -> {
bTrig.setBackground(getRounded(v==bTrig?"#00E5FF":"#222222", 15f));
bTrig.setTextColor(v==bTrig?Color.BLACK:Color.WHITE);
bAct.setBackground(getRounded(v==bAct?"#00E5FF":"#222222", 15f));
bAct.setTextColor(v==bAct?Color.BLACK:Color.WHITE);
vTrig.setVisibility(v==bTrig?View.VISIBLE:View.GONE);
vAct.setVisibility(v==bAct?View.VISIBLE:View.GONE);
};
bTrig.setOnClickListener(tabClick); bAct.setOnClickListener(tabClick);
bTrig.performClick();

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
            // V19.12.3.6.30: THUẬT TOÁN 1 CỬ CHỈ <-> NHIỀU HÀNH ĐỘNG
            // Đếm số cử chỉ đang được tick ở tab TRIGGER
            int checkedGestureCount = 0;
            for (CheckBox gb : gestureBoxes) if (gb.isChecked()) checkedGestureCount++;
            if (checkedGestureCount >= 2 && acts.size() > 1) {
                Toast.makeText(this, T(
                    "You picked " + checkedGestureCount + " gestures — only 1 action allowed. Uncheck extra actions, or split into separate gestures.",
                    "Bạn đang chọn " + checkedGestureCount + " cử chỉ — chỉ được phép 1 hành động. Hãy bỏ bớt hành động, hoặc tách thành từng cử chỉ riêng."
                ), Toast.LENGTH_LONG).show();
                return;
            }

            // V19.12.3.6.40: App / Shortcut vẫn giới hạn 1 trong 2, nhưng NHIỀU Panel
            // được phép chọn cùng lúc (chúng có thể hiển thị song song) — chỉ tính là
            // "1 giao diện" nếu CÓ Panel được chọn, không nhân theo số lượng Panel.
            int interfaceCount = 0;
            if (launchAppSelected[0]) interfaceCount++;
            if (shortcutSelected[0]) interfaceCount++;
            boolean hasPanelSelected = false;
            for (String a : acts) if (a.startsWith("PANEL_")) { hasPanelSelected = true; break; }
            if (hasPanelSelected) interfaceCount++;
            if (interfaceCount > 1) {
                Toast.makeText(this, T(
                    "Only one of App / Shortcut can combine with Panel(s) at a time.",
                    "Chỉ được chọn 1 trong 2: App / Shortcut cùng lúc với (các) Panel."
                ), Toast.LENGTH_LONG).show();
                return;
            }

            String joinedActions = TextUtils.join(",", acts);
            String prefix = getSpacePrefix();
String compKey = isVolKeyMode ? VOLKEY_COMPS[selectedComp[0]] : ALL_COMP_KEYS[selectedComp[0]];
boolean hasChecked = false;
            if(editKey != null && preGes != -1) prefs.edit().putString(editKey, "NONE").apply();
            for(int i=0; i<gestureBoxes.size(); i++) {
               if(gestureBoxes.get(i).isChecked()) {
// V19.12.3.6.23: dùng gestureKeys.get(i) thay vì gesturesUsedSave[i] —
// bắt buộc vì Texture không còn tạo đủ 13 checkbox theo đúng thứ tự C_GESTURES nữa
hasChecked = true; String finalKey = prefix + compKey + "_" + gestureKeys.get(i);
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
            // [FIX CRASH VOLKEY "NEW EB"] actKeysUsed có các ô null (phần Intent/Macro
            // cũ không còn điền tĩnh nữa) — thiếu guard null khiến "SCREEN_ON" (chỉ có
            // ở VolKey, nằm sau vùng null) làm .equals() ném NullPointerException.
            if (actKeysUsed[i] != null && actKeysUsed[i].equals(gk)) {
                out.add(new String[]{actLabsUsed[i], gk});
                break;
            }
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
private Button buildActionCategoryButton(String title, String emoji, List<String[]> items, java.util.LinkedHashSet<String> selectedSet, String colorHex) {
    return buildActionCategoryButton(title, emoji, items, selectedSet, colorHex, false);
}
// [MỚI] allowMulti=true -> cho phép chọn NHIỀU item trong 1 nút (dùng cho PANEL, vì
// nhiều Panel có thể cùng hiển thị song song). false = giữ nguyên hành vi cũ (1 item).
private Button buildActionCategoryButton(String title, String emoji, List<String[]> items, java.util.LinkedHashSet<String> selectedSet, String colorHex, boolean allowMulti) {
        Button btnPick = new Button(this);
        btnPick.setBackground(getRounded(colorHex, 20f));
        btnPick.setTextColor(Color.WHITE);
        btnPick.setTextSize(13.5f);
        btnPick.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 20);
        btnPick.setLayoutParams(lp);

        Runnable updateCount = () -> {
            int cnt = 0;
            for (String[] it : items) if (selectedSet.contains(it[1])) cnt++;
            btnPick.setText(emoji + " " + title + (cnt > 0 ? " (" + cnt + ")" : ""));
        };
        updateCount.run();

        btnPick.setOnClickListener(v -> showActionCategoryPicker(title, items, selectedSet, updateCount, allowMulti));
        return btnPick;
    }
// Picker CHỈ CHỌN 1 ACTION cho QS Tile — khác buildActionCategoryButton (multi-select cho Rule)
private Button singleActionCategoryBtn(String title, String color, List<String[]> items,
        String[] chosenAct, String[] chosenPkg, String[] chosenScId, Runnable onChange) {
    Button b = new Button(this);
    b.setBackground(getRounded(color, 20f)); b.setTextColor(Color.WHITE);
    b.setText(title);
    b.setOnClickListener(v -> {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30,80,30,30);
        ListView lv = new ListView(this);
        lv.setLayoutParams(new LinearLayout.LayoutParams(-1,-1));
        BaseAdapter ad = new BaseAdapter() {
            public int getCount(){ return items.size(); }
            public Object getItem(int p){ return items.get(p); }
            public long getItemId(int p){ return p; }
            public View getView(int p, View cv, ViewGroup parent){
                TextView tv = new TextView(MainActivity.this);
                tv.setText(items.get(p)[0]); tv.setTextColor(Color.WHITE); tv.setTextSize(15);
                tv.setPadding(20,26,20,26);
                return tv;
            }
        };
        lv.setAdapter(ad);
        lv.setOnItemClickListener((p,v2,pos,id2) -> {
            chosenAct[0] = items.get(pos)[1]; chosenPkg[0]=""; chosenScId[0]="";
            onChange.run(); d.dismiss();
        });
        root.addView(lv);
        d.setContentView(root); d.show();
    });
    return b;
}

// Tên hiển thị = tên action đã chọn — dùng lại làm tvCurrent VÀ label lưu xuống tilev2_<id>_label
private String resolveTileActionLabel(String act, String pkg, String scId) {
    if (act == null || act.equals("NONE")) return T("(None)","(Chưa chọn)");
    if (act.equals("LAUNCH_APP")) return "📱 " + getAppLabelCached(pkg);
    if (act.equals("RUN_SHORTCUT")) return "🔗 " + prefs.getString("shortcut_"+scId+"_name","Shortcut");
    if (act.startsWith("INTENT_")) return "⚡ " + prefs.getString("intent_"+act.substring(7)+"_name","Intent");
    if (act.startsWith("MACRO_")) return "🤖 " + prefs.getString("macro_"+act.substring(6)+"_name","Macro");
    return getActionLabel(act);
}
// Dialog picker DÙNG CHUNG cho cả 4 category — có ô tìm kiếm + multi-select,
// y hệt pattern showPanelMultiPicker() đã có sẵn, để đồng bộ trải nghiệm.
private void showActionCategoryPicker(String title, List<String[]> items,
        java.util.LinkedHashSet<String> selectedSet, Runnable onChange) {
    showActionCategoryPicker(title, items, selectedSet, onChange, false);
}
// [MỚI] allowMulti=true: checkbox chọn nhiều Panel cùng lúc — không giới hạn 1 như trước,
// vì nhiều Panel có thể được gọi ra cùng bởi 1 cử chỉ (chúng tự xếp chồng khi mở, xem
// PanelEngine.bringPanelToFront()). Zero-RAM khi đóng dialog: mọi object bị GC thu hồi
// ngay, không giữ Thread/Timer nào — đúng tinh thần tối ưu Pixel 2XL của toàn bộ codebase.
private void showActionCategoryPicker(String title, List<String[]> items,
        java.util.LinkedHashSet<String> selectedSet, Runnable onChange, boolean allowMulti) {
    java.util.Set<String> categoryKeys = new java.util.HashSet<>();
    for (String[] it : items) categoryKeys.add(it[1]);
    final java.util.LinkedHashSet<String> working = new java.util.LinkedHashSet<>();
    for (String s : selectedSet) if (categoryKeys.contains(s)) working.add(s);

    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212")); root.setPadding(30, 80, 30, 30);

    TextView tvTitle = new TextView(this); tvTitle.setText(title);
    tvTitle.setTextColor(Color.parseColor("#00E5FF")); tvTitle.setTextSize(18); tvTitle.setPadding(0, 0, 0, 20);
    root.addView(tvTitle);

    TextView tvHint = new TextView(this);
    tvHint.setText(allowMulti
        ? T("You can select multiple Panels", "Có thể chọn nhiều Panel cùng lúc")
        : T("Only 1 action allowed in this button", "Chỉ được chọn 1 hành động trong nút này"));
    tvHint.setTextColor(Color.parseColor("#9AA0A6")); tvHint.setTextSize(11f); tvHint.setPadding(0, 0, 0, 10);
    root.addView(tvHint);

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
            boolean checked = working.contains(item[1]);
            android.widget.CompoundButton cb = allowMulti ? new CheckBox(MainActivity.this) : new RadioButton(MainActivity.this);
            cb.setChecked(checked); cb.setClickable(false);
            TextView tv = new TextView(MainActivity.this);
            tv.setText(item[0]); tv.setTextColor(Color.WHITE);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(cb); row.addView(tv);
            row.setOnClickListener(v -> {
                if (allowMulti) {
                    if (working.contains(item[1])) working.remove(item[1]); else working.add(item[1]);
                } else {
                    working.clear();
                    if (!checked) working.add(item[1]); // bấm lại item đang chọn -> bỏ chọn
                }
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
        selectedSet.removeAll(categoryKeys);
        selectedSet.addAll(working);
        onChange.run();
        d.dismiss();
    });

    d.setContentView(root); d.show();
}
private void buildMainMenuList() {
    pageMainMenu.removeAllViews();
    Object[][] items = {
    {"touch_app_24px", T("Gestures & Touch Zones","Cử chỉ & Vùng chạm"), "Frontier · Texture · VolKey", (Runnable)() -> openSpace(1)},
    {"light_mode_24px", T("Display","Hiển thị"), "Anima · Lenap · " + T("Language","Ngôn ngữ"), (Runnable)this::openDesignSpace},
    {"flash_on_24px", T("Custom Actions","Hành động tùy chỉnh"), "Intents · QS Tiles · Macros", (Runnable)this::openEcosystemMenu},
    {"file_present_24px", T("Storage","Bộ nhớ"), T("Storage Scan","Quét dung lượng"), (Runnable)() -> openEco(3, false)},
    {"music_note_24px", T("Sound & Media","Âm thanh & Media"), T("Voice Recording · Screen Recording · My Playlist","Ghi âm · Quay màn hình · Danh sách phát"), (Runnable)() -> openEco(4, false)},
    {"security_24px", T("Security","Bảo mật"), "Blacklist · Locklist", (Runnable)() -> openEco(5, false)},
    {"routine_24px", T("Ecosystem","Hệ sinh thái"), "YTDLnis · Island", (Runnable)this::openEcoShowcase},
    {"settings_24px", T("System","Hệ thống"), T("Backup · Restore · Update · Trash · QR Scan · Permissions","Sao lưu · Khôi phục · Nâng cấp · Kho cũ · Quét QR · Quyền"), (Runnable)this::openSystemSpace},
    {"help_24px", T("Infomation","Giới thiệu về Edge Bar"), "Premium", (Runnable)this::showPremiumDialog},
};
    for (Object[] it : items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(getRounded("#161616", 20f));
        row.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, 0, 0, 14);
        row.setLayoutParams(rlp);

        ImageView tvIcon = makeMenuIcon((String) it[0], 81);
        LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) tvIcon.getLayoutParams();
        ilp.setMargins(0, 0, 25, 0);
        row.addView(tvIcon);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView tvTitle = new TextView(this);
        tvTitle.setText((String) it[1]); tvTitle.setTextColor(Color.parseColor("#E8EAED")); tvTitle.setTextSize(16f);
        TextView tvSub = new TextView(this);
        tvSub.setText((String) it[2]); tvSub.setTextColor(Color.parseColor("#9AA0A6")); tvSub.setTextSize(11.5f); tvSub.setPadding(0,4,0,0);
        col.addView(tvTitle); col.addView(tvSub);
        row.addView(col);

        TextView tvChevron = new TextView(this);
        tvChevron.setText("›"); tvChevron.setTextColor(Color.parseColor("#5F6368")); tvChevron.setTextSize(20);
        row.addView(tvChevron);

        Runnable action = (Runnable) it[3];
        row.setOnClickListener(v -> action.run());
        pageMainMenu.addView(row);
    }
}
private LinearLayout createBackRow(String title) {
    // Chỉ trả về 1 layout tàng hình để không gian nào gọi đến hàm này cũng không hiện nút Back nữa.
    // Nút Back trên Nav Bar dưới cùng đã lo nhiệm vụ này.
    LinearLayout row = new LinearLayout(this);
    row.setVisibility(View.GONE);
    return row;
}
private LinearLayout createSettingsRow(String icon, String title, String sub, Runnable onClick) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setBackground(getRounded("#161616", 20f));
    row.setPadding(30, 30, 30, 30);
    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
    rlp.setMargins(0, 0, 0, 14);
    row.setLayoutParams(rlp);

    ImageView tvIcon = makeMenuIcon(icon, 81);
        LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) tvIcon.getLayoutParams();
        ilp.setMargins(0, 0, 25, 0);
        row.addView(tvIcon);

    LinearLayout col = new LinearLayout(this);
    col.setOrientation(LinearLayout.VERTICAL);
    col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    TextView tvTitle = new TextView(this);
    tvTitle.setText(title); tvTitle.setTextColor(Color.parseColor("#E8EAED")); tvTitle.setTextSize(16.5f);
    tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    TextView tvSub = new TextView(this);
    tvSub.setText(sub); tvSub.setTextColor(Color.parseColor("#9AA0A6")); tvSub.setTextSize(12f); tvSub.setPadding(0,4,0,0);
    col.addView(tvTitle); col.addView(tvSub);
    row.addView(col);

    TextView tvChevron = new TextView(this);
    tvChevron.setText("›"); tvChevron.setTextColor(Color.parseColor("#5F6368")); tvChevron.setTextSize(22);
    row.addView(tvChevron);

    row.setOnClickListener(v -> onClick.run());
    return row;
}
private LinearLayout buildShowcaseItem(String icon, String title, String sub, Runnable onClick) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    TextView tvIcon = new TextView(this); tvIcon.setText(icon); tvIcon.setTextSize(22);
    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2); ilp.setMargins(0, 0, 20, 0);
    tvIcon.setLayoutParams(ilp);
    LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
    col.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    TextView tvT = new TextView(this); tvT.setText(title); tvT.setTextColor(Color.WHITE); tvT.setTextSize(15);
    TextView tvS = new TextView(this); tvS.setText(sub); tvS.setTextColor(Color.parseColor("#9AA0A6")); tvS.setTextSize(11.5f);
    col.addView(tvT); col.addView(tvS);
    row.addView(tvIcon); row.addView(col);
    row.setOnClickListener(v -> onClick.run());
    return row;
}

private void buildEcoShowcaseSpace() {
    pageEcoShowcase.addView(createBackRow(T("Ecosystem","Hệ sinh thái")));
    pageEcoShowcase.addView(wrapCard(buildShowcaseItem("🎵", "YTDLnis",
        T("Quick Music/Video Download","Tải nhạc/video nhanh"), this::showYTDLDialog)));
    pageEcoShowcase.addView(wrapCard(buildShowcaseItem("🏝️", "Island",
        T("Toggle Island (Work Profile)","Bật/Tắt Đảo (Island)"), () -> {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.putExtra("act", "TOGGLE_WORK_PROFILE");
            sendBroadcast(ipc);
            Toast.makeText(this, T("Toggled","Đã bật/tắt"), Toast.LENGTH_SHORT).show();
        })));
}

private void buildSystemSpace() {
    pageSystemSpace.addView(createBackRow(T("System","Hệ thống")));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("💾", T("Backup","Sao lưu"),
        T("Export Config To Json","Xuất cấu hình ra Json"), () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup_" + System.currentTimeMillis() + ".json");
            startActivityForResult(i, 101);
        })));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("📁", T("Restore","Khôi phục"),
        T("Import Config From Json","Nạp cấu hình từ Json"), () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            startActivityForResult(i, 102);
        })));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("🔄", T("Update","Cập nhật"),
        "GitHub Actions", () -> {
            revokeDeviceAdminIfActive();
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/manhmoc-creator/EdgeBar/actions")));
        })));
    // [FIX] showSubNav = false — Kho cũ cũng không cần thanh tab Custom Actions
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("🗑️", T("Trash","Kho cũ"),
        T("Restore Or Permanently Delete","Khôi phục hoặc xóa vĩnh viễn"), () -> openEco(6, false))));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("🔳", T("Scan QR","Quét QR"), "",
        () -> startActivity(new Intent(this, QrScanActivity.class)))));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("🏦", T("QR Banks","QR Ngân hàng"),
        T("Choose Bank Apps For VietQR","Chọn app ngân hàng cho VietQR"),
        () -> showPanelMultiPicker("qr_bank_apps", true))));
    pageSystemSpace.addView(wrapCard(buildShowcaseItem("🔑", T("Permissions","Quyền cần cấp"),
        T("Grant Remaining Permissions","Cấp các quyền còn thiếu"),
        () -> Toast.makeText(this, T("Scroll to top of this screen","Cuộn lên đầu màn hình chính"), Toast.LENGTH_LONG).show())));
}
    // ==================== KHÔNG GIAN HÀNH ĐỘNG TUỲ CHỈNH (CHỈ INTENTS/QS TILES/MACROS) ====================
    // [ĐỔI] Bỏ hẳn nút "SYSTEM BEHAVIOR" và "KHO CŨ" khỏi đây — Blacklist/Locklist đã có
    // cổng vào riêng ở mục "Bảo mật", YTDL/QR Bank/Storage/Ghi âm/Kho cũ đã có cổng vào
    // riêng ở "Hệ sinh thái"/"Bộ nhớ"/"Âm thanh & Media"/"Hệ thống". Mỗi tính năng chỉ còn
    // ĐÚNG 1 nơi truy cập -> ít View trùng lặp phải dựng, đỡ RAM/pin trên Pixel 2XL.
    private void openEcosystemMenu() {
    openSpace(2);
    if (ecoMenuContainer != null) ecoMenuContainer.setVisibility(View.VISIBLE);
    if (ecoSubHeader != null) ecoSubHeader.setVisibility(View.GONE);
    if (ecoContainer != null) ecoContainer.setVisibility(View.GONE);
    updateFabVisibility();
}
    private LinearLayout ecoMenuContainer, ecoSubHeader;
private TextView tvEcoSubTitle;

private void buildEcosystemSpace() {
    pageEcosystem.addView(createBackRow(T("Custom Actions","Hành động tùy chỉnh")));

    ecoMenuContainer = new LinearLayout(this);
    ecoMenuContainer.setOrientation(LinearLayout.VERTICAL);
    pageEcosystem.addView(ecoMenuContainer);

    ecoMenuContainer.addView(createSettingsRow("flash_on_24px", "Intents", T("Custom Scripts","Các kịch bản tùy chỉnh"), () -> openEcoSubTab(0, "Intents")));
    ecoMenuContainer.addView(createSettingsRow("routine_24px", "QS Tiles", T("Quick Settings Tiles","Các phím cài đặt nhanh"), () -> openEcoSubTab(1, "QS Tiles")));
    ecoMenuContainer.addView(createSettingsRow("memory_24px", "Macros", T("Multi-action Chains","Chuỗi hành động đa nhiệm"), () -> openEcoSubTab(2, "Macros")));

    ecoSubHeader = new LinearLayout(this);
    ecoSubHeader.setOrientation(LinearLayout.HORIZONTAL);
    ecoSubHeader.setGravity(Gravity.CENTER_VERTICAL);
    ecoSubHeader.setPadding(0, 0, 0, 20);
    ecoSubHeader.setVisibility(View.GONE);
    tvEcoSubTitle = new TextView(this);
    tvEcoSubTitle.setTextColor(Color.parseColor("#00E5FF")); tvEcoSubTitle.setTextSize(16);
    LinearLayout.LayoutParams etlp = new LinearLayout.LayoutParams(-2, -2); etlp.setMargins(20, 0, 0, 0);
    tvEcoSubTitle.setLayoutParams(etlp);
    ecoSubHeader.addView(tvEcoSubTitle);
    pageEcosystem.addView(ecoSubHeader);

    ecoContainer = new LinearLayout(this);
    ecoContainer.setOrientation(LinearLayout.VERTICAL);
    ecoContainer.setVisibility(View.GONE);
    pageEcosystem.addView(ecoContainer);
}

private void openEcoSubTab(int type, String title) {
    ecoType = type;
    ecoMenuContainer.setVisibility(View.GONE);
    ecoSubHeader.setVisibility(View.VISIBLE);
    tvEcoSubTitle.setText(title);
    ecoContainer.setVisibility(View.VISIBLE);
    updateFabVisibility();
    renderEcosystem();
    navBackStack.push(() -> {
    ecoContainer.setVisibility(View.GONE);
    ecoSubHeader.setVisibility(View.GONE);
    ecoMenuContainer.setVisibility(View.VISIBLE);
    updateFabVisibility();
});
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
// [FIX BUILD ERROR] Hàm dùng chung để parse chuỗi CSV -> List<String>,
// dùng trong showCombinedPanelPicker(). Logic giống hệt getDynamicIds()
// nhưng không phụ thuộc SharedPreferences (nhận thẳng chuỗi CSV).
private List<String> csvToList(String csv) {
    List<String> out = new ArrayList<>();
    if (csv == null || csv.isEmpty()) return out;
    for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
    return out;
}
private static final java.util.regex.Pattern NUM_CHUNK_PACK =
    java.util.regex.Pattern.compile("\\d+|\\D+");

private int naturalCompareName(String a, String b) {
    java.util.regex.Matcher ma = NUM_CHUNK_PACK.matcher(a);
    java.util.regex.Matcher mb = NUM_CHUNK_PACK.matcher(b);
    while (ma.find() && mb.find()) {
        String ca = ma.group(), cb = mb.group();
        int cmp = (Character.isDigit(ca.charAt(0)) && Character.isDigit(cb.charAt(0)))
            ? Long.compare(Long.parseLong(ca), Long.parseLong(cb))
            : ca.compareToIgnoreCase(cb);
        if (cmp != 0) return cmp;
    }
    return a.length() - b.length();
}
/** Trả về {label, actionKey} cho 1 loại Data Pack động (Panel/Intent/Macro).
 *  actionKey = "PANEL_"+uuid / "INTENT_"+uuid / "MACRO_"+uuid — id là UUID thật của
 *  Data Pack, KHÔNG phải số thứ tự cố định. Vì list này không giới hạn số lượng,
 *  RAM chỉ tốn đúng bằng số Pack user thực sự tạo — không cấp phát dư cho 15/5 slot rỗng. */
private List<String[]> buildDynamicPackItems(String listKey, String namePrefix, String actionPrefix, String fallback) {
    List<String[]> out = new ArrayList<>();
    for (String id : getDynamicIds(listKey)) {
        String name = prefs.getString(namePrefix + id + "_name", fallback);
        out.add(new String[]{ name, actionPrefix + id });
    }
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
// [PIXEL 2XL OPT] Nhân bản TOÀN BỘ field của 1 Data Pack sang ID mới bằng
// DUY NHẤT 1 SharedPreferences.Editor rồi apply() 1 lần — gộp 9-16 lệnh ghi
// thành 1 lần flush, thay vì gọi prefs.edit()...apply() lặp lại nhiều lần
// (mỗi apply() là 1 lần ghi bất đồng bộ, gộp lại giảm số lần OS phải wake
// I/O thread). Bản sao có ID riêng 100% -> sửa/đổi tên/xóa không đụng bản gốc.
private String cloneDataPack(boolean isBar, String srcId) {
    String listKey = isBar ? "pack_bar_ids" : "pack_corner_ids";
    String packPrefix = isBar ? "pack_bar_" : "pack_corner_";
    String newId = addDynamicId(listKey);
    String src = packPrefix + srcId + "_";
    String dst = packPrefix + newId + "_";
    SharedPreferences.Editor ed = prefs.edit();
    ed.putString(dst + "name", prefs.getString(src + "name", "Data Pack") + " (Copy)");
    ed.putInt(dst + "loc", prefs.getInt(src + "loc", 0));
    ed.putInt(dst + "vis_mode", prefs.getInt(src + "vis_mode", 0));
    ed.putInt(dst + "pri_mode", prefs.getInt(src + "pri_mode", 0));
    ed.putInt(dst + "alpha", prefs.getInt(src + "alpha", 50));
    ed.putInt(dst + "w", prefs.getInt(src + "w", isBar ? 300 : 100));
    ed.putInt(dst + "h", prefs.getInt(src + "h", isBar ? 60 : 100));
    ed.putInt(dst + "x", prefs.getInt(src + "x", 0));
    ed.putInt(dst + "y", prefs.getInt(src + "y", 0));
    if (!isBar) {
        ed.putInt(dst + "shape", prefs.getInt(src + "shape", 0));
        ed.putInt(dst + "moon_w", prefs.getInt(src + "moon_w", 100));
        ed.putInt(dst + "moon_h", prefs.getInt(src + "moon_h", 100));
        ed.putInt(dst + "moon_x", prefs.getInt(src + "moon_x", 1250));
        ed.putInt(dst + "moon_y", prefs.getInt(src + "moon_y", 1250));
        ed.putInt(dst + "rad", prefs.getInt(src + "rad", 80));
        ed.putInt(dst + "moon_rad", prefs.getInt(src + "moon_rad", 80));
    }
    ed.apply();
    return newId;
}
// ==================== KHO CŨ (RECYCLE BIN) ====================
// itemKey dạng "panel_<id>" / "bar_<id>" / "corner_<id>"
// [MỚI] Xác định loại + id từ itemKey — dùng chung cho mọi thao tác Trash.
// Không cấp phát thêm object nào ngoài String.substring() — Zero-RAM overhead.
private String trashType(String itemKey) {
        if (itemKey.startsWith("panel_")) return "panel";
        if (itemKey.startsWith("bar_")) return "bar";
        if (itemKey.startsWith("corner_")) return "corner";
        if (itemKey.startsWith("intent_")) return "intent";
        if (itemKey.startsWith("tilev2_")) return "tilev2";
        if (itemKey.startsWith("macro_")) return "macro";
        if (itemKey.startsWith("myplaylist_")) return "myplaylist";
        if (itemKey.startsWith("shortcut_")) return "shortcut";
        return "";
    }
    private String trashId(String itemKey) {
        return itemKey.substring(itemKey.indexOf('_') + 1);
    }
    private String trashDisplayName(String type, String id) {
        switch (type) {
            case "panel": return prefs.getString("pack_panel_" + id + "_name", "Data Pack");
            case "bar": return prefs.getString("pack_bar_" + id + "_name", "Data Pack");
            case "corner": return prefs.getString("pack_corner_" + id + "_name", "Data Pack");
            case "intent": return prefs.getString("intent_" + id + "_name", "Intent");
            case "tilev2": return prefs.getString("tilev2_" + id + "_label", "Tile");
            case "macro": return prefs.getString("macro_" + id + "_name", "Macro");
            case "myplaylist": return prefs.getString("myplaylist_" + id + "_name", "Song");
            case "shortcut": return prefs.getString("shortcut_" + id + "_name", "Shortcut");
            default: return "Data Pack";
        }
    }
 private static final long TRASH_EXPIRY_MS = 15L * 24 * 60 * 60 * 1000; // 15 ngày

// [MỚI] Chỉ quét khi user THỰC SỰ mở tab Kho Cũ — không Handler/Timer chạy nền,
// Zero-CPU/Zero-pin lúc không ai xem màn Ecosystem > KHO CŨ.
private void cleanExpiredTrash() {
    java.util.List<String> trash = getDynamicIds("trash_pack_ids");
    if (trash.isEmpty()) return;
    long now = System.currentTimeMillis();
    java.util.List<String> expired = new java.util.ArrayList<>();
    for (String itemKey : trash) {
        long ts;
        try {
            ts = prefs.getLong("trash_" + itemKey + "_ts", 0);
        } catch (ClassCastException cce) {
            // Dữ liệu cũ bị lưu sai kiểu (Integer thay vì Long) — tự sửa lại,
            // giống hệt cách xử lý cho storage_scan_ts ở renderEcosystem().
            ts = prefs.getInt("trash_" + itemKey + "_ts", 0);
            prefs.edit().putLong("trash_" + itemKey + "_ts", ts).apply();
        }
        // Dữ liệu cũ từ bản trước chưa có mốc giờ -> gán NGAY BÂY GIỜ thay vì xóa
        // luôn, tránh mất dữ liệu người dùng đột ngột ngay sau khi cập nhật app.
        if (ts == 0) { prefs.edit().putLong("trash_" + itemKey + "_ts", now).apply(); continue; }
        if (now - ts >= TRASH_EXPIRY_MS) expired.add(itemKey);
    }
    for (String itemKey : expired) permanentlyDeleteDataPack(itemKey);
}
private void moveDataPackToTrash(String itemKey) {
    String type = trashType(itemKey);
    String id = trashId(itemKey);
    switch (type) {
        case "panel":
            removeDynamicId("pack_panel_ids", id);
            break;
        case "bar":
        case "corner":
            removeDynamicId(type.equals("bar") ? "pack_bar_ids" : "pack_corner_ids", id);
            for (String px : new String[]{"lock_", "home_", "homacc_"}) {
                if (type.equals("bar")) disableBarPackFromSpace(id, px); else disableCornerPackFromSpace(id, px);
                java.util.List<String> ap = getDynamicIds(px + "applied_packs");
                ap.remove(itemKey);
                prefs.edit().putString(px + "applied_packs", TextUtils.join(",", ap)).apply();
            }
            break;
        case "intent":
            removeDynamicId("intent_ids", id);
            break;
        case "tilev2":
            removeDynamicId("tile_ids_v2", id);
            // Gỡ khỏi QS Slot đang gán (nếu có) — tránh Tile "ma" vẫn ghim trên thanh QS
            for (int s = 1; s <= 30; s++) {
                if (prefs.getString("tile_slot_" + s + "_id", "").equals(id)) {
                    prefs.edit().remove("tile_slot_" + s + "_id").apply();
                    setTileComponentEnabled(s, false);
                }
            }
            break;
        case "macro":
            removeDynamicId("macro_ids", id);
            break;
        case "myplaylist":
            removeDynamicId("myplaylist_ids", id);
            break;
        case "shortcut":
            removeDynamicId("shortcut_ids", id);
            removeDynamicId("panel_shortcut_ids", id);
            break;
        default: return;
    }
    java.util.List<String> trash = getDynamicIds("trash_pack_ids");
    if (!trash.contains(itemKey)) trash.add(itemKey);
    prefs.edit()
        .putString("trash_pack_ids", TextUtils.join(",", trash))
        .putLong("trash_" + itemKey + "_ts", System.currentTimeMillis()) // MỚI: mốc giờ để tính hạn 15 ngày
        .apply();
    sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
}
private void restoreDataPackFromTrash(String itemKey) {
    java.util.List<String> trash = getDynamicIds("trash_pack_ids");
    trash.remove(itemKey);
    prefs.edit()
        .putString("trash_pack_ids", TextUtils.join(",", trash))
        .remove("trash_" + itemKey + "_ts") // MỚI: dọn mốc giờ khi đã khôi phục
        .apply();
    String type = trashType(itemKey);
    String id = trashId(itemKey);
    String listKey;
    switch (type) {
        case "panel": listKey = "pack_panel_ids"; break;
        case "bar": listKey = "pack_bar_ids"; break;
        case "corner": listKey = "pack_corner_ids"; break;
        case "intent": listKey = "intent_ids"; break;
        case "tilev2": listKey = "tile_ids_v2"; break;
        case "macro": listKey = "macro_ids"; break;
        case "myplaylist": listKey = "myplaylist_ids"; break;
        case "shortcut": 
            listKey = "shortcut_ids"; 
            java.util.List<String> panelScs = getDynamicIds("panel_shortcut_ids");
            if (!panelScs.contains(id)) panelScs.add(id);
            prefs.edit().putString("panel_shortcut_ids", android.text.TextUtils.join(",", panelScs)).apply();
            break;
        default: return;
    }
    java.util.List<String> ids = getDynamicIds(listKey);
    if (!ids.contains(id)) ids.add(id);
    prefs.edit().putString(listKey, TextUtils.join(",", ids)).apply();
    // Bar/Corner khôi phục về kho chung, không tự gắn lại vào Lock/Home/Homacc —
    // giữ nguyên hành vi an toàn cũ (tránh gắn nhầm).
    sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
}

private void permanentlyDeleteDataPack(String itemKey) {
    java.util.List<String> trash = getDynamicIds("trash_pack_ids");
    trash.remove(itemKey);
    prefs.edit()
        .putString("trash_pack_ids", TextUtils.join(",", trash))
        .remove("trash_" + itemKey + "_ts") // MỚI: dọn mốc giờ khi xóa vĩnh viễn
        .apply();
    String type = trashType(itemKey);
    String id = trashId(itemKey);
    String prefix;
    switch (type) {
        case "panel": prefix = "pack_panel_"; break;
        case "bar": prefix = "pack_bar_"; break;
        case "corner": prefix = "pack_corner_"; break;
        case "intent": prefix = "intent_"; break;
        case "tilev2": prefix = "tilev2_"; break;
        case "macro": prefix = "macro_"; break;
        case "myplaylist": prefix = "myplaylist_"; break;
        case "shortcut": prefix = "shortcut_"; break;
        default: return;
    }
    java.util.Map<String, ?> all = prefs.getAll();
    SharedPreferences.Editor ed = prefs.edit();
    String fullPrefix = prefix + id + "_";
    for (String k : all.keySet()) if (k.startsWith(fullPrefix)) ed.remove(k);
    if (type.equals("shortcut")) {
        String iconPath = prefs.getString("shortcut_" + id + "_icon_path", "");
        if (!iconPath.isEmpty()) ShortcutScanner.deleteIconFile(iconPath);
    }
    if (type.equals("bar") || type.equals("corner")) {
        java.util.List<String> rules = getDynamicIds(itemKey + "_pack_rules");
        for (String rId : rules) {
            for (String k : all.keySet()) if (k.startsWith("prule_" + rId + "_")) ed.remove(k);
        }
        ed.remove(itemKey + "_pack_rules");
    }
    ed.apply();
}
// [PIXEL 2XL OPT] Nhân bản list Trigger (prule_*) sang Data Pack mới, mỗi
// Trigger con nhận UUID riêng — early-return ngay nếu rỗng để không cấp
// phát ArrayList/Editor vô ích khi Pack cha chưa có Trigger nào.
private void clonePackRules(String srcItemKey, String dstItemKey) {
    List<String> srcRules = getDynamicIds(srcItemKey + "_pack_rules");
    if (srcRules.isEmpty()) return;
    List<String> dstRules = new ArrayList<>();
    for (String rId : srcRules) {
        String newRuleId = java.util.UUID.randomUUID().toString().substring(0, 8);
        dstRules.add(newRuleId);
        prefs.edit()
            .putString("prule_" + newRuleId + "_gestures", prefs.getString("prule_" + rId + "_gestures", ""))
            .putString("prule_" + newRuleId + "_acts", prefs.getString("prule_" + rId + "_acts", ""))
            .putString("prule_" + newRuleId + "_launch_pkg", prefs.getString("prule_" + rId + "_launch_pkg", ""))
            .putString("prule_" + newRuleId + "_shortcut_id", prefs.getString("prule_" + rId + "_shortcut_id", ""))
            .putBoolean("prule_" + newRuleId + "_vib", prefs.getBoolean("prule_" + rId + "_vib", true))
            .putBoolean("prule_" + newRuleId + "_anim", prefs.getBoolean("prule_" + rId + "_anim", true))
            .putBoolean("prule_" + newRuleId + "_en", prefs.getBoolean("prule_" + rId + "_en", true))
            .apply();
    }
    prefs.edit().putString(dstItemKey + "_pack_rules", TextUtils.join(",", dstRules)).apply();
}
private LinearLayout buildEcoSelectionToolbar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(ecoSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnDup = new Button(this); btnDup.setText("🧬 " + T("Duplicate", "Nhân bản"));
    btnDup.setBackground(getRounded("#7C4DFF", 20f)); btnDup.setTextColor(Color.WHITE); btnDup.setTextSize(12.5f);
    btnDup.setOnClickListener(v -> {
        String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
        String prefixBase = ecoType == 0 ? "intent_" : (ecoType == 1 ? "tilev2_" : "macro_");
        for (String itemKey : new java.util.ArrayList<>(ecoSelectedItems)) {
            String id = itemKey.substring(itemKey.indexOf('_') + 1);
            String newId = addDynamicId(listKey);
            java.util.Map<String, ?> all = prefs.getAll();
            SharedPreferences.Editor ed = prefs.edit();
            String oldPrefix = prefixBase + id + "_";
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                if (!e.getKey().startsWith(oldPrefix)) continue;
                String newKey = prefixBase + newId + "_" + e.getKey().substring(oldPrefix.length());
                Object v2 = e.getValue();
                if (v2 instanceof Boolean) ed.putBoolean(newKey, (Boolean) v2);
                else if (v2 instanceof Integer) ed.putInt(newKey, (Integer) v2);
                else if (v2 instanceof String) ed.putString(newKey, (String) v2);
            }
            ed.apply();
        }
        ecoSelectMode = false; ecoSelectedItems.clear();
        renderEcosystem();
        Toast.makeText(this, T("Duplicated!", "Đã nhân bản!"), Toast.LENGTH_SHORT).show();
    });

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
        String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
        String prefixBase = ecoType == 0 ? "intent_" : (ecoType == 1 ? "tilev2_" : "macro_");
        List<String> ids = getDynamicIds(listKey);
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>();
        for (String id : ids) allKeys.add(prefixBase + id);
        if (ecoSelectedItems.equals(allKeys)) ecoSelectedItems.clear();
        else { ecoSelectedItems.clear(); ecoSelectedItems.addAll(allKeys); }
        renderEcosystem();
    });

    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 0, 0);
    btnDelete.setLayoutParams(delLp);
    btnDelete.setOnClickListener(v -> {
        new AlertDialog.Builder(this).setTitle(T("Move to trash?", "Chuyển vào Kho Cũ?"))
            .setPositiveButton(T("MOVE", "CHUYỂN"), (d, w) -> {
                for (String itemKey : new java.util.ArrayList<>(ecoSelectedItems)) moveDataPackToTrash(itemKey);
                ecoSelectMode = false;
                ecoSelectedItems.clear();
                renderEcosystem();
            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
    });

    bar.addView(tvCount); bar.addView(btnDup); bar.addView(btnAll); bar.addView(btnDelete);
    return bar;
}
    private void renderEcosystem() {
    // [FIX CRASH] Chủ động bỏ focus + ẩn bàn phím TRƯỚC khi xoá View — chặn
    // đứng mọi trường hợp EditText (như ô số thứ tự My Playlist) đang giữ
    // focus bị removeAllViews() xoá giữa chừng, gây NPE trong hệ Focus/IME.
    try {
        View cur = getCurrentFocus();
        if (cur != null) {
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(cur.getWindowToken(), 0);
            cur.clearFocus();
        }
    } catch (Exception ignored) {}
    ecoContainer.removeAllViews();
   if (ecoType == 0 || ecoType == 1 || ecoType == 2) {
        String listKey = ecoType == 0 ? "intent_ids" : (ecoType == 1 ? "tile_ids_v2" : "macro_ids");
        String prefixBase = ecoType == 0 ? "intent_" : (ecoType == 1 ? "tilev2_" : "macro_");
        List<String> ids = getDynamicIds(listKey);

        // THÊM ĐOẠN NÀY ĐỂ HIỆN THANH KÉO SIZE CHO QS TILES
        if (ecoType == 1) {
            LinearLayout qsCfgBody = new LinearLayout(this);
            qsCfgBody.setOrientation(LinearLayout.VERTICAL);
            qsCfgBody.setPadding(20, 10, 20, 20);
            qsCfgBody.addView(createSlider("Kích thước Icon QS Tile (%)", "qs_global_icon_scale", 100, 77));
            ecoContainer.addView(createDrawer("⚙️ TÙY CHỈNH CHUNG QS TILES", qsCfgBody));
        }
    if (ecoSelectMode) ecoContainer.addView(buildEcoSelectionToolbar());

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

        FrameLayout cardWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wrapLp.setMargins(6, 6, 6, 6);
        cardWrap.setLayoutParams(wrapLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#202124", 20f));
        card.setPadding(15, 20, 15, 20);
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

// FIX CRASH: Thêm r1 vào card TRƯỚC — để card có sẵn ≥1 child,
// nhờ đó addView(qsRow) phía dưới không còn bị lỗi index vượt quá childCount.
card.addView(r1);

// YÊU CẦU 2: Ô chọn CÓ/KHÔNG hiện QS Tile, báo cáo Data Pack Status & Tối ưu RAM
                    if (ecoType == 1) {
                        LinearLayout qsRow = new LinearLayout(this);
                        qsRow.setOrientation(LinearLayout.HORIZONTAL);
                        qsRow.setGravity(Gravity.CENTER_VERTICAL);
                        qsRow.setPadding(0, 8, 0, 4);

                        int boundSlot = -1;
                        for (int s = 1; s <= 30; s++) {
                            if (prefs.getString("tile_slot_" + s + "_id", "").equals(id)) {
                                boundSlot = s; break;
                            }
                        }

                        final int finalBoundSlot = boundSlot;
CheckBox cbShowTile = new CheckBox(this);
cbShowTile.setText("Hiện QS");
cbShowTile.setTextColor(Color.parseColor("#00E5FF"));
cbShowTile.setTextSize(11.5f);
cbShowTile.setEnabled(finalBoundSlot != -1);
cbShowTile.setChecked(finalBoundSlot != -1 && prefs.getBoolean("tile_active_" + id, false));
cbShowTile.setOnCheckedChangeListener((vw, chk) -> {
    if (finalBoundSlot == -1) {
        Toast.makeText(this, "Hãy gán Data Pack vào 1 Slot trước!", Toast.LENGTH_SHORT).show();
        cbShowTile.setChecked(false);
        return;
    }
    prefs.edit().putBoolean("tile_active_" + id, chk).apply();
    setTileComponentEnabled(finalBoundSlot, chk);
    Toast.makeText(this, chk ? "Đã ghim Tile lên QS" : "Đã ẩn khỏi QS", Toast.LENGTH_SHORT).show();
    sendBroadcast(new Intent("com.manhmoc.edgebar.TILE_CONFIG_CHANGED"));
});
                        TextView tvQsStatus = new TextView(this);
                        tvQsStatus.setTextSize(10.5f);
                        if (boundSlot != -1) {
                            tvQsStatus.setText(" [SLOT " + boundSlot + "]");
                            tvQsStatus.setTextColor(Color.parseColor("#00E5FF")); 
                        } else {
                            tvQsStatus.setText(" [CHƯA GÁN SLOT]");
                            tvQsStatus.setTextColor(Color.parseColor("#D32F2F")); 
                        }
                        tvQsStatus.setGravity(Gravity.RIGHT);
                        tvQsStatus.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

                        qsRow.addView(cbShowTile);
                        qsRow.addView(tvQsStatus);
                        // FIX CRASH: bỏ index cứng "1" — addView() không tham số index
                        // sẽ tự nối vào SAU r1 (đã add ở trên), đúng thứ tự hiển thị
                        // mong muốn (r1 → qsRow → r2) mà không còn out-of-bounds.
                        card.addView(qsRow); 
                    }
// --- [KẾT THÚC CODE MỚI] ---
// Hàng 2: Nút Copy chuẩn hóa chống lẹm cho màn hình 18:9
LinearLayout r2 = new LinearLayout(this);
r2.setOrientation(LinearLayout.HORIZONTAL);
r2.setPadding(0, 12, 0, 0);
final String finalId = id; final int finalType = ecoType;

Button btnCopy = new Button(this); btnCopy.setText("TEST");
btnCopy.setBackground(getRounded("#FFC107", 14f));
btnCopy.setTextColor(Color.BLACK);
btnCopy.setTextSize(12.5f);
btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
btnCopy.setPadding(10, 12, 10, 12);
LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
cpLp.setMargins(0, 0, 0, 0); btnCopy.setLayoutParams(cpLp);
btnCopy.setMinimumHeight(88);
btnCopy.setOnClickListener(v -> {
    if (finalType == 0) {
        fireTestAction("INTENT_" + finalId, "", "");
    } else if (finalType == 1) {
        String act = prefs.getString(prefixBase + finalId + "_act", "NONE");
        fireTestAction(act, prefs.getString(prefixBase + finalId + "_launch_pkg", ""),
            prefs.getString(prefixBase + finalId + "_shortcut_id", ""));
    } else {
        Intent iM = new Intent("com.manhmoc.edgebar.TOGGLE_MACRO");
        iM.putExtra("services", prefs.getString(prefixBase + finalId + "_svcs", ""));
        sendBroadcast(iM);
    }
});
        r2.addView(btnCopy);
        card.addView(r2);

        cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        final String ecoItemKey = (finalType == 0 ? "intent_" : (finalType == 1 ? "tilev2_" : "macro_")) + finalId;

        cardWrap.setTag(finalId);
        if (ecoSelectMode) {
            // Chế độ chọn nhiều: chấm tròn góc dưới-trái, chạm để tick/bỏ tick
            boolean sel = ecoSelectedItems.contains(ecoItemKey);
            TextView selDot = new TextView(this);
selDot.setText(sel ? "🔵" : "⚪");
selDot.setTextSize(16);
FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
dLp.gravity = Gravity.BOTTOM | Gravity.END; dLp.setMargins(0,0,10,6);
selDot.setLayoutParams(dLp);
cardWrap.addView(selDot);
            card.setOnClickListener(v -> {
                if (ecoSelectedItems.contains(ecoItemKey)) ecoSelectedItems.remove(ecoItemKey);
                else ecoSelectedItems.add(ecoItemKey);
                renderEcosystem();
            });
            card.setOnLongClickListener(v -> true);
        } else {
            // THUẬT TOÁN UX: CHẠM 1 LẦN -> MỞ EDIT DIALOG
            card.setOnClickListener(v -> {
                if (finalType == 0) openIntentEditorV2(finalId);
                else if (finalType == 1) openTileEditorV2(finalId);
                else openMacroEditorV2(finalId);
            });
            // CHẠM GIỮ -> VÀO CHẾ ĐỘ CHỌN NHIỀU (thay vì mở dialog xoá 1 gói như trước)
            card.setOnLongClickListener(v -> {
                ecoSelectMode = true;
                ecoSelectedItems.clear();
                ecoSelectedItems.add(ecoItemKey);
                renderEcosystem();
                return true;
            });
        }
        attachDragReorder(cardWrap, ids, listKey, this::renderEcosystem);
        currentRow.addView(cardWrap); count++;
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
    long lastScanTs;
    try {
        lastScanTs = prefs.getLong("storage_scan_ts", 0);
    } catch (ClassCastException cce) {
        // Dữ liệu cũ bị lưu sai kiểu (Integer thay vì Long) do bug Restore trước đây — tự sửa lại
        lastScanTs = prefs.getInt("storage_scan_ts", 0);
        prefs.edit().putLong("storage_scan_ts", lastScanTs).apply();
    }
    TextView tvInfo = new TextView(this);
    tvInfo.setTextColor(Color.parseColor("#9AA0A6")); tvInfo.setPadding(0,0,0,20);
    tvInfo.setText(lastScanTs == 0 ? T("Chưa quét lần nào", "Chưa quét lần nào")
            : T("Lần quét gần nhất: ", "Lần quét gần nhất: ") +
            android.text.format.DateFormat.format("HH:mm dd/MM", lastScanTs));
    ecoContainer.addView(tvInfo);
    renderCachedStorageList();
} else if (ecoType == 4) {
    if (soundMediaSubTab == -1) {
        renderSoundMediaMenu();
    } else if (soundMediaSubTab == 0) {
        renderVoiceRecordSpace();
    } else if (soundMediaSubTab == 1) {
        renderScreenRecordSpace();
    } else {
        renderMyPlaylistSpace();
    }
} else if (ecoType == 5) {
    // Thẻ 1: BlackList
    LinearLayout cardBlacklist = new LinearLayout(this);
    cardBlacklist.setOrientation(LinearLayout.VERTICAL);
    Button btnPickBlacklist = new Button(this);
    btnPickBlacklist.setText("🚫 BLACKLIST"); btnPickBlacklist.setBackground(getRounded("#D32F2F", 20f)); btnPickBlacklist.setTextColor(Color.WHITE);
    btnPickBlacklist.setOnClickListener(v -> showPanelMultiPicker("blacklist", true));
    cardBlacklist.addView(btnPickBlacklist);
    CheckBox cbAutoHomeb = new CheckBox(this);
    cbAutoHomeb.setText(T("Auto-disable Accessibility + switch to Homeb", "Tự tắt Trợ năng + mở Homeb khi vào app"));
    cbAutoHomeb.setTextColor(Color.parseColor("#FFC107"));
    cbAutoHomeb.setChecked(prefs.getBoolean("blacklist_auto_homeb_en", false));
    cbAutoHomeb.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("blacklist_auto_homeb_en", c).apply());
    cbAutoHomeb.setPadding(0, 20, 0, 0);
    cardBlacklist.addView(cbAutoHomeb);
    ecoContainer.addView(wrapCard(cardBlacklist));

    // Thẻ 2: LockList
    LinearLayout cardLocklist = new LinearLayout(this);
    cardLocklist.setOrientation(LinearLayout.VERTICAL);
    Button btnPickLockList = new Button(this);
    btnPickLockList.setText("🔐 LOCKLIST"); btnPickLockList.setBackground(getRounded("#7C4DFF", 20f)); btnPickLockList.setTextColor(Color.WHITE);
    btnPickLockList.setOnClickListener(v -> showPanelMultiPicker("applock_list", true));
    cardLocklist.addView(btnPickLockList);
    cardLocklist.addView(createSlider(T("Lock grace period after leaving app (sec)", "Thời gian ân hạn trước khi khoá lại (giây)"), "applock_grace_sec", 1000, 0));
    ecoContainer.addView(wrapCard(cardLocklist));

    // Thẻ 3: Keyboard (Nút ẩn)
    LinearLayout cardKbd = new LinearLayout(this);
    cardKbd.setOrientation(LinearLayout.VERTICAL);
    CheckBox cbKbd = new CheckBox(this); 
    cbKbd.setText(T("Auto-hide on Keyboard", "Tự ẩn không gian khi hiện Bàn Phím"));
    cbKbd.setTextColor(Color.WHITE); 
    cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true));
    cbKbd.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("avoid_kbd", c).apply());
    cardKbd.addView(cbKbd);
    ecoContainer.addView(wrapCard(cardKbd));
} else if (ecoType == 6) {
    cleanExpiredTrash(); // MỚI: tự dọn pack quá 15 ngày trước khi vẽ danh sách

    LinearLayout secTrash = new LinearLayout(this);
    secTrash.setOrientation(LinearLayout.VERTICAL);
    secTrash.addView(createSectionTitle("🗑️ KHO CŨ (THÙNG RÁC)"));

    List<String> trashIds = getDynamicIds("trash_pack_ids");
    trashIds.sort((keyA, keyB) -> {
        String typeA = trashType(keyA), idA = trashId(keyA);
        String typeB = trashType(keyB), idB = trashId(keyB);
        String nameA = trashDisplayName(typeA, idA);
        String nameB = trashDisplayName(typeB, idB);
        return naturalCompareName(nameA, nameB);
    });
    if (trashIds.isEmpty()) {
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText(T("Trash is empty.", "Thùng rác trống."));
        tvEmpty.setTextColor(Color.parseColor("#777777"));
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(0, 60, 0, 0);
        secTrash.addView(tvEmpty);
        ecoContainer.addView(wrapCard(secTrash));
        return;
    }

    if (trashSelectMode) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, 20);
        TextView tvCount = new TextView(this);
        tvCount.setText(trashSelectedItems.size() + " " + T("selected", "đã chọn"));
        tvCount.setTextColor(Color.parseColor("#00E5FF"));
        tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        Button btnRestore = new Button(this); btnRestore.setText("♻️ " + T("Restore", "Khôi phục"));
        btnRestore.setBackground(getRounded("#4CAF50", 20f)); btnRestore.setTextColor(Color.WHITE); btnRestore.setTextSize(12.5f);
        btnRestore.setOnClickListener(v -> {
            for (String key : new ArrayList<>(trashSelectedItems)) restoreDataPackFromTrash(key);
            trashSelectMode = false; trashSelectedItems.clear();
            renderEcosystem();
        });

        Button btnPerma = new Button(this); btnPerma.setText("🗑️ " + T("Delete forever", "Xóa vĩnh viễn"));
        btnPerma.setBackground(getRounded("#D32F2F", 20f)); btnPerma.setTextColor(Color.WHITE); btnPerma.setTextSize(12.5f);
        LinearLayout.LayoutParams pLp2 = new LinearLayout.LayoutParams(-2, -2); pLp2.setMargins(10, 0, 10, 0);
        btnPerma.setLayoutParams(pLp2);
        btnPerma.setOnClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(T("Delete forever? Cannot be undone.", "Xóa vĩnh viễn? Không thể hoàn tác."))
                .setPositiveButton(T("DELETE", "XÓA"), (d, w) -> {
                    for (String key : new ArrayList<>(trashSelectedItems)) permanentlyDeleteDataPack(key);
                    trashSelectMode = false; trashSelectedItems.clear();
                    renderEcosystem();
                }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
        });
        bar.addView(tvCount); bar.addView(btnRestore); bar.addView(btnPerma);
        secTrash.addView(bar);
    }

    LinearLayout currentTrashRow = null;
    int trashCount = 0;
    for (String itemKey : trashIds) {
        String type = trashType(itemKey);
        String id = trashId(itemKey);
        String typeLabel; String name;
        switch (type) {
            case "panel": typeLabel = "[Panel] "; name = prefs.getString("pack_panel_" + id + "_name", "Data Pack"); break;
            case "bar": typeLabel = "[Bar] "; name = prefs.getString("pack_bar_" + id + "_name", "Data Pack"); break;
            case "corner": typeLabel = "[Corner] "; name = prefs.getString("pack_corner_" + id + "_name", "Data Pack"); break;
            case "intent": typeLabel = "[Intent] "; name = prefs.getString("intent_" + id + "_name", "Intent"); break;
            case "tilev2": typeLabel = "[QS Tile] "; name = prefs.getString("tilev2_" + id + "_label", "Tile"); break;
            case "macro": typeLabel = "[Macro] "; name = prefs.getString("macro_" + id + "_name", "Macro"); break;
            case "myplaylist": typeLabel = "[Song] "; name = prefs.getString("myplaylist_" + id + "_name", "Song"); break;
            case "shortcut": typeLabel = "[Shortcut] "; name = prefs.getString("shortcut_" + id + "_name", "Shortcut"); break;
            default: typeLabel = ""; name = "Data Pack";
        }
        // 2 pack / hàng — dựng row mới mỗi khi đếm chẵn (giống mọi grid khác trong app)
        if (trashCount % 2 == 0) {
            currentTrashRow = new LinearLayout(this);
            currentTrashRow.setOrientation(LinearLayout.HORIZONTAL);
            currentTrashRow.setLayoutParams(new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
            secTrash.addView(currentTrashRow);
        }

        FrameLayout cardWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wLp.setMargins(6, 6, 6, 6);
        cardWrap.setLayoutParams(wLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#2A2A2A", 20f));
        card.setPadding(30, 26, 30, 26);
        TextView tvName = new TextView(this);
        tvName.setText("📦 " + typeLabel + name);
        tvName.setTextColor(Color.parseColor("#CCCCCC"));
        tvName.setTextSize(13f);
        tvName.setMaxLines(2); tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(tvName);
        cardWrap.addView(card);

        final String fKey = itemKey;
        cardWrap.setTag(fKey);
        if (trashSelectMode) {
            TextView selDot = new TextView(this);
            selDot.setText(trashSelectedItems.contains(fKey) ? "🔵" : "⚪");
            selDot.setTextSize(18);
            FrameLayout.LayoutParams dLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dLp.gravity = Gravity.BOTTOM | Gravity.START; dLp.setMargins(10, 0, 0, 6);
            selDot.setLayoutParams(dLp);
            cardWrap.addView(selDot);
            card.setOnClickListener(v -> {
                if (trashSelectedItems.contains(fKey)) trashSelectedItems.remove(fKey); else trashSelectedItems.add(fKey);
                renderEcosystem();
            });
        } else {
            card.setOnLongClickListener(v -> {
                trashSelectMode = true; trashSelectedItems.clear(); trashSelectedItems.add(fKey);
                renderEcosystem();
                return true;
            });
        }
        attachDragReorder(cardWrap, trashIds, "trash_pack_ids", this::renderEcosystem);
        currentTrashRow.addView(cardWrap);
        trashCount++;
    }
    // Hàng cuối lẻ -> đệm 1 view rỗng để card không bị kéo giãn full-width
    if (trashCount % 2 != 0 && currentTrashRow != null) {
        View dummy = new View(this);
        dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        currentTrashRow.addView(dummy);
    }
    ecoContainer.addView(wrapCard(secTrash));
  }
}
    private String getActionLabel(String actionKey) {
        for (int i=0; i<ACT_KEYS.length; i++) {
            if (ACT_KEYS[i] != null && ACT_KEYS[i].equals(actionKey)) return ACT_LABS[i];
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
// Pool 20 icon: index phải khớp với ICON_POOL trong Tile1..15.java
private static final String[] TILE_ICON_NAMES = {
    "La Bàn 🧭", "Kính Lúp 🔍", "Ổ Khóa 🔒", "Camera 📷", "Home 🏠",
    "Play ▶", "Micro 🎤", "Âm Lượng 🔊", "Chia Sẻ 📤", "Thông Tin ℹ️",
    "Cài Đặt ⚙️", "Gửi 📨", "Chỉnh Sửa ✏️", "Xóa 🗑️", "Thêm ➕",
    "Đóng ✖️", "Upload ⬆️", "Xem 👁️", "Yêu Thích ⭐", "Vị Trí 📍"
};
private static final String[] MP_COLOR_HEX = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F","#8D6E63","#26A69A","#EC407A","#7E57C2"};
private int mpColorForId(String id) { return Color.parseColor(MP_COLOR_HEX[Math.abs(id.hashCode()) % MP_COLOR_HEX.length]); }

private void renderMyPlaylistSpace() {
    // [YÊU CẦU 1] Dòng ghi chú đầu không gian - đồng bộ với Voice Recording / Screen Recording
    TextView tvNote = new TextView(this);
    tvNote.setText(T("Songs added from Files by Google.", "Bài hát được thêm từ Files by Google. Nhấn vào mục để mở bằng ứng dụng tương ứng."));
    tvNote.setTextColor(Color.parseColor("#9AA0A6")); tvNote.setTextSize(12);
    tvNote.setPadding(0, 0, 0, 20);
    ecoContainer.addView(tvNote);

    // [YÊU CẦU 2] Ô tìm kiếm - lọc theo tên bài hát. KHÔNG gọi renderEcosystem() mỗi lần
    // gõ phím (tốn công dựng lại Note+Search) — chỉ vẽ lại listContainer bên dưới,
    // tiết kiệm CPU/pin trên Pixel 2XL.
    EditText etSearch = new EditText(this);
    etSearch.setHint(T("Search songs...", "Tìm bài hát..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setSingleLine(true);
    etSearch.setBackground(getRounded("#2C2C2C", 100f));
    etSearch.setPadding(30, 20, 30, 20);
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, -2);
    searchLp.setMargins(0, 0, 0, 20);
    etSearch.setLayoutParams(searchLp);
    ecoContainer.addView(etSearch);

    LinearLayout listContainer = new LinearLayout(this);
    listContainer.setOrientation(LinearLayout.VERTICAL);
    ecoContainer.addView(listContainer);

    renderMyPlaylistList(listContainer, "");
    etSearch.addTextChangedListener(new android.text.TextWatcher() {
        public void afterTextChanged(android.text.Editable s) { renderMyPlaylistList(listContainer, s.toString()); }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });
}

private void renderMyPlaylistList(LinearLayout listContainer, String query) {
    listContainer.removeAllViews();
    List<String> ids = getDynamicIds("myplaylist_ids");
    if (ids.isEmpty()) {
        TextView empty = new TextView(this);
        empty.setText(T("No songs yet.\nTap the round button to add from Files by Google.",
            "Chưa có bài hát nào.\nBấm nút tròn để thêm từ Files by Google."));
        empty.setTextColor(Color.GRAY); empty.setGravity(Gravity.CENTER); empty.setPadding(0,100,0,0);
        listContainer.addView(empty);
        return;
    }

    // [TỐI ƯU PIN/RAM] Lọc tại chỗ bằng string đã cache sẵn trong prefs - KHÔNG I/O,
    // KHÔNG Thread, KHÔNG re-query MediaStore mỗi lần gõ phím.
    String q = query == null ? "" : query.trim().toLowerCase();
    List<String> shownIds = new ArrayList<>();
    for (String id : ids) {
        if (q.isEmpty()) { shownIds.add(id); continue; }
        String name = prefs.getString("myplaylist_" + id + "_name", "").toLowerCase();
        if (name.contains(q)) shownIds.add(id);
    }

    // [YÊU CẦU 3] Thanh công cụ "Chọn nhiều > Tất cả/Xoá" - chỉ dựng khi đang ở chế độ chọn
    if (myPlSelectMode) listContainer.addView(buildMyPlaylistSelectionToolbar(ids));

    for (int pos = 0; pos < shownIds.size(); pos++) {
        String id = shownIds.get(pos);
        String name = prefs.getString("myplaylist_" + id + "_name", "Song");
        String uriStr = prefs.getString("myplaylist_" + id + "_uri", "");

        // [YÊU CẦU: 1 dòng = 1 Data Pack, kích thước đồng nhất]
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(getRounded(String.format("#%06X", (0xFFFFFF & mpColorForId(id))), 20f));
        card.setPadding(28, 0, 28, 0);
        card.setMinimumHeight(120); // mọi card cao bằng nhau, bất kể tên dài/ngắn
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 6, 0, 6);
        card.setLayoutParams(cardLp);

        final String fId = id;
        final boolean canReorder = q.isEmpty() && !myPlSelectMode; // chỉ cho đổi số khi xem full list, không lọc

        if (canReorder) {
            EditText etOrder = new EditText(this);
            etOrder.setText(String.valueOf(pos + 1));
            etOrder.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etOrder.setTextColor(Color.WHITE);
            etOrder.setTextSize(14f);
            etOrder.setGravity(Gravity.CENTER);
            etOrder.setBackground(getRounded("#00000055", 12f));
            etOrder.setPadding(10, 8, 10, 8);
            etOrder.setSingleLine(true);
            LinearLayout.LayoutParams ordLp = new LinearLayout.LayoutParams(90, LinearLayout.LayoutParams.WRAP_CONTENT);
            ordLp.setMargins(0, 0, 20, 0);
            etOrder.setLayoutParams(ordLp);
            etOrder.setOnEditorActionListener((v, actionId, event) -> {
                applyMyPlaylistReorder(fId, etOrder.getText().toString());
                return true;
            });
            etOrder.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) applyMyPlaylistReorder(fId, etOrder.getText().toString());
            });
            card.addView(etOrder);
        }

        TextView tv = new TextView(this);
        tv.setText(" " + name);
        tv.setTextColor(Color.WHITE); tv.setTextSize(14.5f);
        tv.setMaxLines(2); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // [YÊU CẦU: chữ lấp đầy Data Pack] weight=1 chiếm hết chiều ngang còn lại
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tv);
        if (myPlSelectMode) {
            TextView selDot = new TextView(this);
            boolean sel = myPlSelectedItems.contains(fId);
            selDot.setText(sel ? "🔵" : "⚪");
            selDot.setTextSize(16);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(-2, -2);
            dotLp.setMargins(10, 0, 0, 0);
            selDot.setLayoutParams(dotLp);
            card.addView(selDot);
            card.setOnClickListener(v -> {
                if (myPlSelectedItems.contains(fId)) myPlSelectedItems.remove(fId);
                else myPlSelectedItems.add(fId);
                renderMyPlaylistList(listContainer, query);
            });
            card.setOnLongClickListener(v -> true);
        } else {
            card.setOnClickListener(v -> {
                if (uriStr.isEmpty()) { Toast.makeText(this, T("File missing","File không còn tồn tại"), Toast.LENGTH_SHORT).show(); return; }
                try {
                    Intent open = new Intent(Intent.ACTION_VIEW);
                    open.setDataAndType(Uri.parse(uriStr), "audio/*");
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    open.setPackage("com.google.android.apps.nbu.files");
                    startActivity(open);
                } catch (Exception e) {
                    try {
                        Intent open2 = new Intent(Intent.ACTION_VIEW);
                        open2.setDataAndType(Uri.parse(uriStr), "audio/*");
                        open2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(Intent.createChooser(open2, T("Open with","Mở bằng")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) { Toast.makeText(this, T("Cannot open","Không thể mở"), Toast.LENGTH_SHORT).show(); }
                }
            });
            card.setOnLongClickListener(v -> {
                myPlSelectMode = true;
                myPlSelectedItems.clear();
                myPlSelectedItems.add(fId);
                renderMyPlaylistList(listContainer, query);
                return true;
            });
        }
        listContainer.addView(card);
    }
}
// [MỚI] Đổi vị trí bằng cách HOÁN ĐỔI (swap) — bài ở vị trí đích nhảy về đúng
// vị trí cũ của bài đang sửa, các bài khác giữ nguyên chỗ. Ví dụ: đổi bài #1
// thành số 8 -> bài đang ở #8 tự động đổi thành #1 (lên đầu), bài #1 cũ xuống #8.
private void applyMyPlaylistReorder(String id, String typedNum) {
    List<String> ids = getDynamicIds("myplaylist_ids");
    int oldPos = ids.indexOf(id);
    if (oldPos < 0) return;

    int newPos;
    try { newPos = Integer.parseInt(typedNum.trim()) - 1; }
    catch (Exception e) { deferRenderEcosystem(); return; } // số không hợp lệ -> vẽ lại về trạng thái đúng

    newPos = Math.max(0, Math.min(ids.size() - 1, newPos)); // kẹp trong khoảng hợp lệ
    if (newPos == oldPos) { deferRenderEcosystem(); return; }

    java.util.Collections.swap(ids, oldPos, newPos); // HOÁN ĐỔI thay vì remove+insert
    prefs.edit().putString("myplaylist_ids", TextUtils.join(",", ids)).apply();
    deferRenderEcosystem();
}

// [FIX CRASH] KHÔNG được rebuild View (removeAllViews) ngay trong callback
// onEditorAction/onFocusChange của EditText — lúc đó hệ Focus/IME đang thao
// tác dở trên chính EditText sắp bị xoá, gây NullPointerException khi hệ
// thống truy cập mViewFlags của 1 View đã null. Post vào hàng đợi UI thread
// để chạy SAU khi dispatch sự kiện hiện tại kết thúc hẳn — Zero cost thêm
// (chỉ 1 Handler.post rẻ tiền), không Thread/Timer nào giữ lại.
private final Handler ecoDeferHandler = new Handler(android.os.Looper.getMainLooper());
private void deferRenderEcosystem() {
    ecoDeferHandler.post(this::renderEcosystem);
}
private LinearLayout buildMyPlaylistSelectionToolbar(List<String> ids) {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(myPlSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    btnDelete.setOnClickListener(v -> {
        new AlertDialog.Builder(this).setTitle(T("Remove selected songs?", "Xoá các bài đã chọn?"))
            .setPositiveButton(T("REMOVE", "XOÁ"), (d, w) -> {
                for (String id : new java.util.ArrayList<>(myPlSelectedItems)) {
                    prefs.edit().remove("myplaylist_"+id+"_uri").remove("myplaylist_"+id+"_name").apply();
                    removeDynamicId("myplaylist_ids", id);
                }
                myPlSelectMode = false; myPlSelectedItems.clear();
                renderEcosystem();
            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
    });
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    btnAll.setOnClickListener(v -> {
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>(ids);
        if (myPlSelectedItems.equals(allKeys)) myPlSelectedItems.clear();
        else { myPlSelectedItems.clear(); myPlSelectedItems.addAll(allKeys); }
        renderEcosystem();
    });

    bar.addView(tvCount); bar.addView(btnDelete); bar.addView(btnAll);
    return bar;
}
// Kiểm tra Uri có còn truy cập được không — nếu app quản lý file kia đã xoá file
// gốc, ContentResolver.query() sẽ ném lỗi hoặc trả về con trỏ rỗng. Chỉ chạy khi
// user THỰC SỰ mở màn My Playlist (event-driven), không polling nền.
private boolean isPlaylistUriAlive(String uriStr) {
    if (uriStr.isEmpty()) return false;
    try (Cursor c = getContentResolver().query(Uri.parse(uriStr), null, null, null, null)) {
        return c != null && c.moveToFirst();
    } catch (Exception e) { return false; }
}

// Quét toàn bộ myplaylist_ids, bài nào file gốc đã bị xoá thì tự động chuyển
// vào Kho Cũ (dùng chung cơ chế moveDataPackToTrash đã có sẵn). Các bài còn lại
// tự dồn lên đúng thứ tự cũ vì removeDynamicId() chỉ xoá đúng 1 phần tử khỏi CSV.
private void pruneDeadMyPlaylistEntries() {
    List<String> ids = getDynamicIds("myplaylist_ids");
    if (ids.isEmpty()) return;
    boolean anyRemoved = false;
    for (String id : new ArrayList<>(ids)) {
        String uriStr = prefs.getString("myplaylist_" + id + "_uri", "");
        if (!isPlaylistUriAlive(uriStr)) {
            moveDataPackToTrash("myplaylist_" + id);
            anyRemoved = true;
        }
    }
    if (anyRemoved) {
        Toast.makeText(this, T("Some songs were removed — moved to Trash",
            "Một số bài hát đã bị xoá — đã chuyển vào Kho Cũ"), Toast.LENGTH_SHORT).show();
    }
}
 // [MỚI] PHẢI khớp CHÍNH XÁC thứ tự với ICON_POOL trong Tile1..30.java —
// vì autoIconForAct() trả về index dựa trên đúng thứ tự này.
private static final int[] QS_ICON_POOL = {
        // ===== NHẠT (viền mảnh, đơn giản) =====
        android.R.drawable.ic_menu_search, android.R.drawable.ic_menu_compass,
        android.R.drawable.ic_menu_mylocation, android.R.drawable.ic_menu_agenda,
        android.R.drawable.ic_menu_always_landscape_portrait, android.R.drawable.ic_menu_day,
        android.R.drawable.ic_menu_today, android.R.drawable.ic_menu_month,
        android.R.drawable.ic_menu_directions, android.R.drawable.ic_menu_mapmode,
        android.R.drawable.ic_menu_gallery, android.R.drawable.ic_menu_help,
        android.R.drawable.ic_menu_more, android.R.drawable.ic_menu_recent_history,
        android.R.drawable.ic_menu_revert, android.R.drawable.ic_menu_rotate,
        android.R.drawable.ic_menu_save, android.R.drawable.ic_menu_sort_alphabetically,
        android.R.drawable.ic_menu_sort_by_size, android.R.drawable.ic_menu_zoom,
        android.R.drawable.ic_menu_myplaces, android.R.drawable.ic_menu_report_image,
        android.R.drawable.ic_menu_crop, android.R.drawable.ic_menu_send,
        android.R.drawable.ic_menu_share, android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_menu_edit, android.R.drawable.ic_menu_add,
        android.R.drawable.ic_menu_close_clear_cancel, android.R.drawable.ic_menu_view,
        android.R.drawable.arrow_down_float, android.R.drawable.arrow_up_float,
        android.R.drawable.ic_input_delete, android.R.drawable.ic_input_get,
        android.R.drawable.ic_dialog_email, android.R.drawable.ic_dialog_info,
        android.R.drawable.ic_dialog_dialer, android.R.drawable.ic_dialog_map,
        android.R.drawable.ic_lock_idle_alarm, android.R.drawable.ic_lock_idle_charging,
        android.R.drawable.ic_lock_idle_low_battery, android.R.drawable.ic_lock_silent_mode,
        android.R.drawable.ic_lock_silent_mode_off,

        // ===== TRUNG (đổ bóng, xám mờ) =====
        android.R.drawable.ic_menu_camera, android.R.drawable.ic_menu_call,
        android.R.drawable.ic_menu_upload, android.R.drawable.star_on,
        android.R.drawable.star_off, android.R.drawable.btn_star_big_off,
        android.R.drawable.ic_menu_set_as, android.R.drawable.ic_menu_slideshow,
        android.R.drawable.stat_sys_download, android.R.drawable.stat_sys_upload,
        android.R.drawable.stat_notify_chat, android.R.drawable.stat_notify_error,
        android.R.drawable.stat_notify_missed_call, android.R.drawable.stat_notify_sync,
        android.R.drawable.stat_notify_voicemail, android.R.drawable.ic_media_ff,
        android.R.drawable.ic_media_rew, android.R.drawable.ic_media_previous,
        android.R.drawable.ic_media_pause, android.R.drawable.ic_btn_speak_now,
        android.R.drawable.ic_secure, android.R.drawable.ic_lock_power_off,
        android.R.drawable.presence_offline, android.R.drawable.ic_dialog_alert,

        // ===== ĐẬM (khối đặc, nổi bật nhất) =====
        android.R.drawable.ic_lock_idle_lock, android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_manage, android.R.drawable.ic_menu_delete,
        android.R.drawable.ic_lock_lock, android.R.drawable.ic_delete,
        android.R.drawable.ic_input_add, android.R.drawable.stat_sys_warning,
        android.R.drawable.btn_star_big_on, android.R.drawable.presence_online,
        android.R.drawable.presence_busy, android.R.drawable.presence_audio_online,
        android.R.drawable.presence_video_online, android.R.drawable.sym_def_app_icon,
        android.R.drawable.sym_action_call, android.R.drawable.sym_action_chat
    };
private void showQsIconPickerDialog(java.util.function.IntConsumer onPicked) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(Color.parseColor("#121212"));
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(30, 80, 30, 30);
    scroll.addView(page);
    LinearLayout row = null;
    for (int i = 0; i < QS_ICON_POOL.length; i++) {
        if (i % 5 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); page.addView(row); }
        ImageView iv = new ImageView(this);
        Bitmap normBmp = normalizeIconBitmap(getDrawable(QS_ICON_POOL[i]), 140, 0.68f);
        if (normBmp != null) iv.setImageBitmap(normBmp);
        else { iv.setImageResource(QS_ICON_POOL[i]); iv.setPadding(24, 24, 24, 24); }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(0, 140, 1f);
        ivLp.setMargins(6, 6, 6, 6);
        iv.setLayoutParams(ivLp);
        iv.setBackground(getRounded("#202124", 16f));
        final int idxFinal = i;
        iv.setOnClickListener(v -> { onPicked.accept(idxFinal); d.dismiss(); });
        row.addView(iv);
    }
    d.setContentView(scroll); d.show();
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
            final String pkg = o.getString("pkg"); // đã có sẵn từ runDeepStorageScan(), không cần quét lại
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
            tvSub.setText(subtitle + " ›"); // dấu › gợi ý "chạm để mở"
            tvSub.setTextColor(Color.parseColor("#00E5FF")); // đổi màu để báo hiệu có thể bấm
            tvSub.setTextSize(11); tvSub.setPadding(0, 5, 0, 0);
            
            card.addView(tvTitle); card.addView(tvSub);

            // [MỚI] Chạm vào card → mở thẳng App Info system (Settings) của đúng app đó,
            // nơi có sẵn nút "Xóa dữ liệu lưu trữ" / "Xóa bộ nhớ đệm" của Android —
            // KHÔNG tự viết lại UI xóa dữ liệu (tránh permission MANAGE_EXTERNAL_STORAGE
            // hoặc phải root), tận dụng đúng cơ chế hệ thống đã có sẵn = 0 code thêm để
            // maintain, 0 rủi ro crash khi xóa nhầm file hệ thống.
            card.setOnClickListener(v -> {
                try {
                    Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    appInfo.setData(Uri.parse("package:" + pkg));
                    appInfo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(appInfo);
                } catch (Exception ignored) {}
            });

            currentRow.addView(card);
        }
        if (arr.length() % 2 != 0 && currentRow != null) { 
            View dummy = new View(this);
            dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            currentRow.addView(dummy); 
        }
    } catch (Exception ignored) {}
}
private static List<Object[]> cachedVoiceRecList = null; // {Uri, name, sizeBytes, dateSec}
private static long cachedVoiceRecTs = 0;
private static final long VOICE_REC_CACHE_MS = 60 * 1000; // 1 phút — tránh query MediaStore liên tục

private List<Object[]> getVoiceRecListCached(boolean forceRefresh) {
    long now = System.currentTimeMillis();
    if (!forceRefresh && cachedVoiceRecList != null && (now - cachedVoiceRecTs) < VOICE_REC_CACHE_MS)
        return cachedVoiceRecList;
    List<Object[]> out = new ArrayList<>();
    try {
        android.net.Uri collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.SIZE, android.provider.MediaStore.Audio.Media.DATE_ADDED};
        String sel = null; String[] args = null;
        if (Build.VERSION.SDK_INT >= 29) {
            sel = android.provider.MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
            args = new String[]{"Music/EdgeBar%"};
        }
        android.database.Cursor c = getContentResolver().query(collection, proj, sel, args,
            android.provider.MediaStore.Audio.Media.DATE_ADDED + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                long size = c.getLong(2);
                long date = c.getLong(3);
                android.net.Uri itemUri = android.content.ContentUris.withAppendedId(collection, id);
                out.add(new Object[]{itemUri, name, size, date});
            }
            c.close();
        }
    } catch (Exception ignored) {}
    cachedVoiceRecList = out; cachedVoiceRecTs = now;
    return out;
}
private void renderVoiceRecordList() {
    List<Object[]> list = getVoiceRecListCached(false);
    Button btnRefresh = new Button(this);
    btnRefresh.setText("🔄 " + T("Refresh", "Làm mới"));
    btnRefresh.setBackground(getRounded("#202124", 20f));
    btnRefresh.setTextColor(Color.parseColor("#00E5FF"));
    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
    rLp.setMargins(0, 0, 0, 20);
    btnRefresh.setLayoutParams(rLp);
    btnRefresh.setOnClickListener(v -> { getVoiceRecListCached(true); renderEcosystem(); });
    ecoContainer.addView(btnRefresh);

    if (list.isEmpty()) {
        TextView empty = new TextView(this);
        empty.setText(T("No recordings yet.", "Chưa có bản ghi âm nào."));
        empty.setTextColor(Color.parseColor("#777777"));
        empty.setGravity(Gravity.CENTER); empty.setPadding(0, 60, 0, 0);
        ecoContainer.addView(empty);
        return;
    }

    if (voiceSelectMode) ecoContainer.addView(buildVoiceSelectionToolbar());

    LinearLayout currentRow = null;
    for (int i = 0; i < list.size(); i++) {
        if (i % 2 == 0) {
            currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            ecoContainer.addView(currentRow);
        }
        Object[] item = list.get(i);
        android.net.Uri uri = (android.net.Uri) item[0];
        String name = (String) item[1];
        long size = (long) item[2];
        final String uriStr = uri.toString();

        FrameLayout cardWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wrapLp.setMargins(6, 6, 6, 6);
        cardWrap.setLayoutParams(wrapLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#202124", 20f));
        card.setPadding(30, 24, 30, 24);

        TextView tName = new TextView(this);
        tName.setText("🎙️ " + name);
        tName.setTextColor(Color.parseColor("#E8EAED")); tName.setTextSize(13);
        tName.setMaxLines(1); tName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tSize = new TextView(this);
        tSize.setText(StorageScanner.formatSize(size) + "  ›");
        tSize.setTextColor(Color.parseColor("#00E5FF")); tSize.setTextSize(11);
        tSize.setPadding(0, 5, 0, 0);

        card.addView(tName); card.addView(tSize);
        cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        if (voiceSelectMode) {
            TextView selDot = new TextView(this);
            boolean sel = voiceSelectedItems.contains(uriStr);
            selDot.setText(sel ? "🔵" : "⚪");
            selDot.setTextSize(18);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dotLp.gravity = Gravity.BOTTOM | Gravity.START;
            dotLp.setMargins(10, 0, 0, 6);
            selDot.setLayoutParams(dotLp);
            cardWrap.addView(selDot);
            card.setOnClickListener(v -> {
                if (voiceSelectedItems.contains(uriStr)) voiceSelectedItems.remove(uriStr);
                else voiceSelectedItems.add(uriStr);
                renderEcosystem();
            });
            card.setOnLongClickListener(v -> true);
        } else {
            card.setOnClickListener(v -> openInFilesByGoogle(uri));
            card.setOnLongClickListener(v -> {
                voiceSelectMode = true;
                voiceSelectedItems.clear();
                voiceSelectedItems.add(uriStr);
                renderEcosystem();
                return true;
            });
        }
        currentRow.addView(cardWrap);
    }
}

private LinearLayout buildVoiceSelectionToolbar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(voiceSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    btnDelete.setOnClickListener(v -> {
        new AlertDialog.Builder(this).setTitle(T("Delete selected recordings?", "Xoá các bản ghi đã chọn?"))
            .setPositiveButton(T("DELETE", "XOÁ"), (d, w) -> {
                for (String uriStr : new java.util.ArrayList<>(voiceSelectedItems)) {
                    try { getContentResolver().delete(android.net.Uri.parse(uriStr), null, null); } catch (Exception ignored) {}
                }
                voiceSelectMode = false;
                voiceSelectedItems.clear();
                getVoiceRecListCached(true);
                renderEcosystem();
            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
    });
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
        java.util.List<Object[]> list = getVoiceRecListCached(false);
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>();
        for (Object[] item : list) allKeys.add(((android.net.Uri) item[0]).toString());
        if (voiceSelectedItems.equals(allKeys)) voiceSelectedItems.clear();
        else { voiceSelectedItems.clear(); voiceSelectedItems.addAll(allKeys); }
        renderEcosystem();
    });

    bar.addView(tvCount); bar.addView(btnDelete); bar.addView(btnAll);
    return bar;
}
/** Mở đúng file ghi âm trong Files by Google; nếu chưa cài thì fallback sang chooser. */
private void openInFilesByGoogle(android.net.Uri uri) {
    try {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage("com.google.android.apps.nbu.files");
        startActivity(i);
    } catch (Exception e) {
        try {
            Intent i2 = new Intent(Intent.ACTION_VIEW);
            i2.setDataAndType(uri, "audio/*");
            i2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(i2, T("Open with", "Mở bằng")));
        } catch (Exception ignored) {
            Toast.makeText(this, T("Cannot open file", "Không thể mở file"), Toast.LENGTH_SHORT).show();
        }
    }
}
// ==================== [MỚI] MÀN LỰA CHỌN GHI ÂM / GHI MÀN HÌNH ====================
// Cấu trúc giống Gestures & Touch: chạm "Sound & Media" chỉ hiện 1 menu 2 mục,
// chọn mục nào thì MỚI dựng UI của không gian đó — Zero-RAM cho phần chưa chọn.
private void renderSoundMediaMenu() {
    boolean recOn = VoiceRecorderService.isRunning;
    boolean vidOn = ScreenRecorderService.isRunning;
    ecoContainer.addView(createSettingsRow("music_note_2_24px", T("Voice Recording", "Ghi âm"),
        recOn ? T("Recording...", "Đang ghi âm...") : T("Tap to Open", "Chạm để mở"),
        () -> {
            soundMediaSubTab = 0;
            navBackStack.push(() -> { soundMediaSubTab = -1; updateFabVisibility(); renderEcosystem(); });
            updateFabVisibility(); renderEcosystem();
        }));
    ecoContainer.addView(createSettingsRow("movie_24px", T("Screen Recording", "Ghi màn hình"),
        vidOn ? T("Recording...", "Đang ghi màn hình...") : T("Tap to Open", "Chạm để mở"),
        () -> {
            soundMediaSubTab = 1;
            navBackStack.push(() -> { soundMediaSubTab = -1; updateFabVisibility(); renderEcosystem(); });
            updateFabVisibility(); renderEcosystem();
        }));
    ecoContainer.addView(createSettingsRow("music_note_24px", "My Playlist",
        T("Custom Song Order", "Danh sách nhạc tuỳ chỉnh"),
        () -> {
            pruneDeadMyPlaylistEntries(); // kiểm tra file gốc còn sống trước khi vẽ danh sách
            soundMediaSubTab = 2;
            navBackStack.push(() -> { soundMediaSubTab = -1; updateFabVisibility(); renderEcosystem(); });
            updateFabVisibility(); renderEcosystem();
        }));
}
// Không gian Ghi âm — giữ nguyên hành vi cũ (điều khiển qua FAB), chỉ tách khỏi Ghi màn hình.
private void renderVoiceRecordSpace() {
    TextView tvNote = new TextView(this);
    tvNote.setText(T("Files saved in Music/EdgeBar.", "File ghi âm lưu tại Music/EdgeBar. Nhấn vào mục để mở bằng ứng dụng tương ứng."));
    tvNote.setTextColor(Color.parseColor("#9AA0A6")); tvNote.setTextSize(12);
    tvNote.setPadding(0, 0, 0, 20);
    ecoContainer.addView(tvNote);
    renderVoiceRecordList();
}

// Không gian Ghi màn hình — có nút Bắt đầu/Dừng/Tạm dừng riêng, không phụ thuộc FAB.
private void renderScreenRecordSpace() {
    ecoContainer.addView(buildScreenRecordControlCard());

    TextView tvNote = new TextView(this);
    tvNote.setText(T("Files saved in Movies/EdgeBar.", "File video lưu tại Movies/EdgeBar. Nhấn vào mục để mở bằng ứng dụng tương ứng."));
    tvNote.setTextColor(Color.parseColor("#9AA0A6")); tvNote.setTextSize(12);
    tvNote.setPadding(0, 10, 0, 20);
    ecoContainer.addView(tvNote);
    renderScreenRecordList();
}

// ==================== [MỚI] KHÔNG GIAN LƯU BIẾN CHO QUAY MÀN HÌNH ====================
// Card cấu hình (Micro / Hiện vị trí chạm) + nút Bắt đầu-Dừng-Tạm dừng, tách biệt
// hoàn toàn khỏi FAB (FAB vẫn giữ nguyên hành vi cho Ghi âm, không đụng tới).
private LinearLayout buildScreenRecordControlCard() {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(getRounded("#1E1E1E", 25f));
    card.setPadding(35, 30, 35, 30);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, 20);
    card.setLayoutParams(lp);

    TextView tvTitle = new TextView(this);
    tvTitle.setText("🎬 " + T("Screen Recording", "Quay màn hình"));
    tvTitle.setTextColor(Color.parseColor("#E91E63"));
    tvTitle.setTextSize(15f);
    tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    tvTitle.setPadding(0, 0, 0, 15);
    card.addView(tvTitle);

    boolean recOn = ScreenRecorderService.isRunning;
    boolean recPaused = ScreenRecorderService.isPaused;

    CheckBox cbAudio = new CheckBox(this);
    cbAudio.setText(T("Record microphone audio", "Ghi âm (Micro)"));
    cbAudio.setTextColor(Color.WHITE);
    cbAudio.setChecked(prefs.getBoolean("screenrec_audio_en", false));
    cbAudio.setEnabled(!recOn);
    cbAudio.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("screenrec_audio_en", c).apply());
    card.addView(cbAudio);

    CheckBox cbTouches = new CheckBox(this);
    cbTouches.setText(T("Show touch indicator", "Hiển thị vị trí thao tác chạm"));
    cbTouches.setTextColor(Color.WHITE);
    cbTouches.setChecked(prefs.getBoolean("screenrec_showtouches_en", true));
    cbTouches.setEnabled(!recOn);
    cbTouches.setOnCheckedChangeListener((v, c) -> prefs.edit().putBoolean("screenrec_showtouches_en", c).apply());
    cbTouches.setPadding(0, 6, 0, 15);
    card.addView(cbTouches);

    LinearLayout btnRow = new LinearLayout(this);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);

    Button btnMain = new Button(this);
    btnMain.setText(recOn ? "⏹ " + T("STOP", "DỪNG QUAY") : "🔴 " + T("START RECORDING", "BẮT ĐẦU QUAY"));
    btnMain.setBackground(getRounded(recOn ? "#D32F2F" : "#E91E63", 20f));
    btnMain.setTextColor(Color.WHITE);
    btnMain.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    btnMain.setOnClickListener(v -> {
        if (recOn) {
            Intent stop = new Intent(this, ScreenRecorderService.class);
            stop.setAction(ScreenRecorderService.ACTION_STOP);
            startService(stop);
        } else {
            Intent permIntent = new Intent(this, ScreenRecordPermissionActivity.class);
            permIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(permIntent);
        }
        // Trạng thái Service chỉ cập nhật sau khi start/stop thực thi xong -> vẽ lại
        // trễ 500ms để đọc đúng isRunning mới nhất, không cần Handler/Timer chạy nền.
        new Handler(android.os.Looper.getMainLooper()).postDelayed(this::renderEcosystem, 500);
    });
    btnRow.addView(btnMain);

    if (recOn) {
        Button btnPause = new Button(this);
        btnPause.setText(recPaused ? "▶" : "⏸");
        btnPause.setBackground(getRounded("#333333", 20f));
        btnPause.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-2, -2);
        pLp.setMargins(10, 0, 0, 0);
        btnPause.setLayoutParams(pLp);
        btnPause.setOnClickListener(v -> {
            Intent p = new Intent(this, ScreenRecorderService.class);
            p.setAction(ScreenRecorderService.ACTION_PAUSE_TOGGLE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(p); else startService(p);
            new Handler(android.os.Looper.getMainLooper()).postDelayed(this::renderEcosystem, 400);
        });
        btnRow.addView(btnPause);
    }
    card.addView(btnRow);
    return card;
}

// Cache 60 giây (dùng chung hằng số VOICE_REC_CACHE_MS đã có sẵn) — tránh query
// MediaStore.Video liên tục mỗi lần vẽ lại Ecosystem, tiết kiệm I/O trên Pixel 2XL.
private static List<Object[]> cachedVideoRecList = null; // {Uri, name, sizeBytes, dateSec}
private static long cachedVideoRecTs = 0;

private List<Object[]> getVideoRecListCached(boolean forceRefresh) {
    long now = System.currentTimeMillis();
    if (!forceRefresh && cachedVideoRecList != null && (now - cachedVideoRecTs) < VOICE_REC_CACHE_MS)
        return cachedVideoRecList;
    List<Object[]> out = new ArrayList<>();
    try {
        android.net.Uri collection = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {android.provider.MediaStore.Video.Media._ID, android.provider.MediaStore.Video.Media.DISPLAY_NAME,
            android.provider.MediaStore.Video.Media.SIZE, android.provider.MediaStore.Video.Media.DATE_ADDED};
        String sel = null; String[] args = null;
        if (Build.VERSION.SDK_INT >= 29) {
            sel = android.provider.MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
            args = new String[]{"Movies/EdgeBar%"};
        }
        android.database.Cursor c = getContentResolver().query(collection, proj, sel, args,
            android.provider.MediaStore.Video.Media.DATE_ADDED + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String name = c.getString(1);
                long size = c.getLong(2);
                long date = c.getLong(3);
                android.net.Uri itemUri = android.content.ContentUris.withAppendedId(collection, id);
                out.add(new Object[]{itemUri, name, size, date});
            }
            c.close();
        }
    } catch (Exception ignored) {}
    cachedVideoRecList = out; cachedVideoRecTs = now;
    return out;
}

private void renderScreenRecordList() {
    List<Object[]> list = getVideoRecListCached(false);
    if (list.isEmpty()) return;

    Button btnRefresh = new Button(this);
    btnRefresh.setText("🔄 " + T("Refresh videos", "Làm mới video"));
    btnRefresh.setBackground(getRounded("#202124", 20f));
    btnRefresh.setTextColor(Color.parseColor("#E91E63"));
    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
    rLp.setMargins(0, 0, 0, 12);
    btnRefresh.setLayoutParams(rLp);
    btnRefresh.setOnClickListener(v -> { getVideoRecListCached(true); renderEcosystem(); });
    ecoContainer.addView(btnRefresh);

    // [MỚI] Hiển thị Toolbar chọn nhiều nếu đang ở chế độ chọn
    if (videoSelectMode) ecoContainer.addView(buildVideoSelectionToolbar());

    LinearLayout currentRow = null;
    for (int i = 0; i < list.size(); i++) {
        if (i % 2 == 0) {
            currentRow = new LinearLayout(this);
            currentRow.setOrientation(LinearLayout.HORIZONTAL);
            currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            ecoContainer.addView(currentRow);
        }
        Object[] item = list.get(i);
        android.net.Uri uri = (android.net.Uri) item[0];
        String name = (String) item[1];
        long size = (long) item[2];
        final String uriStr = uri.toString();

        FrameLayout cardWrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wrapLp.setMargins(6, 6, 6, 6);
        cardWrap.setLayoutParams(wrapLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getRounded("#202124", 20f));
        card.setPadding(30, 24, 30, 24);

        TextView tName = new TextView(this);
        tName.setText("🎬 " + name);
        tName.setTextColor(Color.parseColor("#E8EAED")); tName.setTextSize(13);
        tName.setMaxLines(1); tName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tSize = new TextView(this);
        tSize.setText(StorageScanner.formatSize(size) + "  ›");
        tSize.setTextColor(Color.parseColor("#E91E63")); tSize.setTextSize(11);
        tSize.setPadding(0, 5, 0, 0);

        card.addView(tName); card.addView(tSize);
        cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // [MỚI] Xử lý Multi-select tương tự Ghi âm
        if (videoSelectMode) {
            TextView selDot = new TextView(this);
            boolean sel = videoSelectedItems.contains(uriStr);
            selDot.setText(sel ? "🔵" : "⚪");
            selDot.setTextSize(18);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            dotLp.gravity = Gravity.BOTTOM | Gravity.START;
            dotLp.setMargins(10, 0, 0, 6);
            selDot.setLayoutParams(dotLp);
            cardWrap.addView(selDot);
            
            card.setOnClickListener(v -> {
                if (videoSelectedItems.contains(uriStr)) videoSelectedItems.remove(uriStr);
                else videoSelectedItems.add(uriStr);
                renderEcosystem();
            });
            card.setOnLongClickListener(v -> true);
        } else {
            card.setOnClickListener(v -> {
                try {
                    Intent openIntent = new Intent(Intent.ACTION_VIEW);
                    openIntent.setDataAndType(uri, "video/*");
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(openIntent);
                } catch (Exception ignored) {
                    Toast.makeText(this, T("Cannot open file", "Không thể mở file"), Toast.LENGTH_SHORT).show();
                }
            });
            card.setOnLongClickListener(v -> {
                videoSelectMode = true;
                videoSelectedItems.clear();
                videoSelectedItems.add(uriStr);
                renderEcosystem();
                return true;
            });
        }
        currentRow.addView(cardWrap);
    }
}

// [MỚI] Toolbar quản lý Xóa / Chọn tất cả cho Screen Record
private LinearLayout buildVideoSelectionToolbar() {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(videoSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    btnDelete.setOnClickListener(v -> {
        new AlertDialog.Builder(this).setTitle(T("Delete selected videos?", "Xoá các video đã chọn?"))
            .setPositiveButton(T("DELETE", "XOÁ"), (d, w) -> {
                for (String uriStr : new java.util.ArrayList<>(videoSelectedItems)) {
                    try { getContentResolver().delete(android.net.Uri.parse(uriStr), null, null); } catch (Exception ignored) {}
                }
                videoSelectMode = false;
                videoSelectedItems.clear();
                getVideoRecListCached(true);
                renderEcosystem();
            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
    });
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
        java.util.List<Object[]> list = getVideoRecListCached(false);
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>();
        for (Object[] item : list) allKeys.add(((android.net.Uri) item[0]).toString());
        if (videoSelectedItems.equals(allKeys)) videoSelectedItems.clear();
        else { videoSelectedItems.clear(); videoSelectedItems.addAll(allKeys); }
        renderEcosystem();
    });

    bar.addView(tvCount); bar.addView(btnDelete); bar.addView(btnAll);
    return bar;
}
// ==================== [KẾT THÚC PHẦN MỚI] ====================

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
    CheckBox cbBr = new CheckBox(this); cbBr.setText("Send as Broadcast"); cbBr.setTextColor(Color.WHITE); cbBr.setChecked(prefs.getBoolean("intent_"+id+"_br", false));
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

    TextView tvActionHint = new TextView(this);
    tvActionHint.setText(T("Action (choose 1 of 6 categories):", "Hành động (chọn 1 trong 6 mục):"));
    tvActionHint.setTextColor(Color.parseColor("#00E5FF"));
    content.addView(tvActionHint);

    final String[] chosenAct = { prefs.getString("tilev2_"+id+"_act", "NONE") };
    final String[] chosenPkg = { prefs.getString("tilev2_"+id+"_launch_pkg", "") };
    final String[] chosenScId = { prefs.getString("tilev2_"+id+"_shortcut_id", "") };

    TextView tvCurrent = new TextView(this);
    tvCurrent.setTextColor(Color.parseColor("#4CAF50"));
    tvCurrent.setPadding(0,10,0,20);
    Runnable refreshCurrent = () -> tvCurrent.setText(
        T("Selected: ", "Đang chọn: ") + resolveTileActionLabel(chosenAct[0], chosenPkg[0], chosenScId[0]));

    Button btnApp = new Button(this); btnApp.setText("📱 APP");
    btnApp.setBackground(getRounded("#00E5FF",20f)); btnApp.setTextColor(Color.BLACK);
    btnApp.setOnClickListener(v -> showSingleAppPickerDialogCallback(pkg -> {
        chosenAct[0] = "LAUNCH_APP"; chosenPkg[0] = pkg; chosenScId[0]="";
        refreshCurrent.run();
    }));

    Button btnSc = new Button(this); btnSc.setText("🔗 SHORTCUT");
    btnSc.setBackground(getRounded("#7C4DFF",20f)); btnSc.setTextColor(Color.WHITE);
    btnSc.setOnClickListener(v -> showShortcutPickerDialog((scId, name) -> {
        chosenAct[0] = "RUN_SHORTCUT"; chosenScId[0] = scId; chosenPkg[0]="";
        refreshCurrent.run();
    }));

    List<String[]> SYS_ITEMS = buildItemsForKeys(new String[]{"BACK","HOME","RECENTS","SCREEN_OFF","FLASH","POWER_DIALOG","VOLUME","SCREENSHOT","CAMERA","NOTIFICATIONS","QUICK_SETTINGS","SPLIT_SCREEN","SCREEN_RECORD","AUTO_ROTATE_TOGGLE"}, ACT_KEYS, ACT_LABS);
    List<String[]> UTIL_ITEMS = buildItemsForKeys(new String[]{"TOGGLE_OVERLAY","TOGGLE_RECORD","PAUSE_RECORD","YTDL_DOWNLOAD","TOGGLE_WORK_PROFILE","OPEN_STORAGE_SCAN","SCAN_QR","PLAY_MY_PLAYLIST"}, ACT_KEYS, ACT_LABS);
    List<String[]> INTENT_ITEMS = buildDynamicPackItems("intent_ids","intent_","INTENT_","Intent");
    List<String[]> MACRO_ITEMS  = buildDynamicPackItems("macro_ids","macro_","MACRO_","Macro");

    Button btnSys = singleActionCategoryBtn("⚙️ SYSTEM","#4CAF50", SYS_ITEMS, chosenAct, chosenPkg, chosenScId, refreshCurrent);
    Button btnUtl = singleActionCategoryBtn("🛠️ UTILITIES","#FF9800", UTIL_ITEMS, chosenAct, chosenPkg, chosenScId, refreshCurrent);
    Button btnInt = singleActionCategoryBtn("⚡ INTENTS","#D32F2F", INTENT_ITEMS, chosenAct, chosenPkg, chosenScId, refreshCurrent);
    Button btnMac = singleActionCategoryBtn("🤖 MACROS","#2196F3", MACRO_ITEMS, chosenAct, chosenPkg, chosenScId, refreshCurrent);

    content.addView(btnApp); content.addView(btnSc);
    content.addView(btnSys); content.addView(btnUtl); content.addView(btnInt); content.addView(btnMac);
    refreshCurrent.run();
    content.addView(tvCurrent);
    final int[] chosenIconIdx = { prefs.getInt("tilev2_"+id+"_icon_idx", -1) };
    TextView tvIconCurrent = new TextView(this);
    tvIconCurrent.setTextColor(Color.parseColor("#FFC107"));
    tvIconCurrent.setPadding(0, 10, 0, 10);
    // [FIX CRASH] TILE_ICON_NAMES chỉ có 20 phần tử trong khi QS_ICON_POOL có 81 icon —
    // index trả về từ showQsIconPickerDialog() có thể >= 20 và làm vỡ mảng
    // (ArrayIndexOutOfBoundsException). Không tra tên theo mảng cũ nữa, chỉ báo
    // trạng thái đã chọn hay chưa — an toàn với MỌI index, không giới hạn kích thước.
    Runnable refreshIconLabel = () -> tvIconCurrent.setText(
        T("Icon: ", "Icon: ") + (chosenIconIdx[0] < 0 ? T("Auto", "Tự động") : T("Custom ✓", "Tuỳ chỉnh ✓")));
    refreshIconLabel.run();
    Button btnPickIcon = new Button(this);
    btnPickIcon.setText("🎨 " + T("CHOOSE ICON", "CHỌN ICON"));
    btnPickIcon.setBackground(getRounded("#FFC107", 20f));
    btnPickIcon.setTextColor(Color.BLACK);
    btnPickIcon.setOnClickListener(v -> showQsIconPickerDialog(idx -> { chosenIconIdx[0] = idx; refreshIconLabel.run(); }));
    content.addView(btnPickIcon);
    content.addView(tvIconCurrent);

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
        if (chosenAct[0].equals("NONE")) { Toast.makeText(this, T("Pick an action first!","Hãy chọn 1 hành động!"), Toast.LENGTH_SHORT).show(); return; }
        if (freeSlots.isEmpty()) { Toast.makeText(this, "Đã hết Slot QS Tile (30/30)!", Toast.LENGTH_SHORT).show(); return; }
        int chosenSlot = freeSlots.get(spSlot.getSelectedItemPosition());
        String autoLabel = resolveTileActionLabel(chosenAct[0], chosenPkg[0], chosenScId[0]);

        for (int s = 1; s <= 30; s++) {
            if (prefs.getString("tile_slot_"+s+"_id", "").equals(id) && s != chosenSlot) {
                prefs.edit().remove("tile_slot_"+s+"_id").apply();
                setTileComponentEnabled(s, false);
            }
        }
        prefs.edit()
            .putString("tilev2_"+id+"_label", autoLabel)
            .putString("tilev2_"+id+"_act", chosenAct[0])
            .putString("tilev2_"+id+"_launch_pkg", chosenPkg[0])
            .putString("tilev2_"+id+"_shortcut_id", chosenScId[0])
            .putInt("tilev2_"+id+"_icon_idx", chosenIconIdx[0])
            .putString("tile_slot_"+chosenSlot+"_id", id)
            .apply();
        setTileComponentEnabled(chosenSlot, prefs.getBoolean("tile_active_" + id, false));
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
    designTopBackRow = createBackRow(T("Display","Hiển thị"));
    pageDesign.addView(designTopBackRow);

    designSpaceMenu = new LinearLayout(this);
    designSpaceMenu.setOrientation(LinearLayout.VERTICAL);

    designBackRow = new LinearLayout(this);
    designBackRow.setOrientation(LinearLayout.HORIZONTAL);
    designBackRow.setGravity(Gravity.CENTER_VERTICAL);
    designBackRow.setPadding(0, 0, 0, 20);
    designBackRow.setVisibility(View.GONE);
    // [FIX] Bỏ ImageButton back riêng.
    tvDesignSubTitle = new TextView(this);
    tvDesignSubTitle.setTextColor(Color.parseColor("#00E5FF")); tvDesignSubTitle.setTextSize(16);
    LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(-2, -2); dtlp.setMargins(20, 0, 0, 0);
    tvDesignSubTitle.setLayoutParams(dtlp);
    designBackRow.addView(tvDesignSubTitle);
    designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0);
    designSliderContainer.setVisibility(View.GONE);

    btnEditAnim = createSettingsRow("flash_on_24px", "ANIMA",
        T("Animation & Recording Indicator", "Hiệu ứng & Chỉ báo ghi âm"),
        () -> openDesignSubSpace(3, "ANIMA"));

    btnEditPanel = createSettingsRow("routine_24px", "LENAP",
        T("Floating Panel Data Packs", "Bảng nút nổi (Data Pack)"),
        () -> openDesignSubSpace(5, "LENAP"));

    LinearLayout btnEditLang = createSettingsRow("translate_24px", "LANGUAGE",
        T("US-English / Tiếng Việt", "US-English / Tiếng Việt"),
        this::showLanguagePicker);

    designSpaceMenu.addView(btnEditAnim);
    designSpaceMenu.addView(btnEditPanel);
    designSpaceMenu.addView(btnEditLang);
    pageDesign.addView(designSpaceMenu);
    pageDesign.addView(designBackRow);
    pageDesign.addView(designSliderContainer);
    }
private void openDesignSubSpace(int tabState, String title) {
    designTabState = tabState;
    if (tabState == 3) ensureHomeServiceForPreview();
    refreshPreview();
    designTopBackRow.setVisibility(View.GONE);
    designSpaceMenu.setVisibility(View.GONE);
    designBackRow.setVisibility(View.VISIBLE);
    tvDesignSubTitle.setText(title);
    designSliderContainer.setVisibility(View.VISIBLE);
    updateFabVisibility();
    renderSliders();
    // [MỚI]
    navBackStack.push(() -> {
    designSliderContainer.setVisibility(View.GONE);
    designBackRow.setVisibility(View.GONE);
    designSpaceMenu.setVisibility(View.VISIBLE);
    designTopBackRow.setVisibility(View.VISIBLE);
    updateFabVisibility();
});
}
private void showLanguagePicker() {
    String[] opts = {"🇺🇸 US - English", "🇻🇳 Tiếng Việt"};
    boolean curVi = prefs.getBoolean("lang_vi", true);
    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(T("Choose Language", "Chọn ngôn ngữ"))
        .setSingleChoiceItems(opts, curVi ? 1 : 0, (d, which) -> {
            prefs.edit().putBoolean("lang_vi", which == 1).apply();
            d.dismiss();
            recreate();
        }).show();
}
private void renderSliders() {
designSliderContainer.removeAllViews();
if (designTabState == 5) { renderPanelDesign(); return; }
    if (designTabState == 3) {
    // DRAWER 1: HIỆU ỨNG CHUNG
    LinearLayout dEffect = new LinearLayout(this);
    dEffect.setOrientation(LinearLayout.VERTICAL); dEffect.setPadding(20,10,20,20);

    Button btnTest = new Button(this); btnTest.setText("▶ THỬ NGAY HIỆU ỨNG");
    btnTest.setBackground(getRounded("#FFC107", 20f)); btnTest.setTextColor(Color.BLACK);
    btnTest.setPadding(0,30,0,30);
    LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1,-2); testLp.setMargins(0,0,0,20);
    btnTest.setLayoutParams(testLp);
    btnTest.setOnClickListener(v -> { Intent i = new Intent("com.manhmoc.edgebar.TEST_ANIM"); i.setPackage(getPackageName()); sendBroadcast(i); Toast.makeText(this, "Playing Animation...", Toast.LENGTH_SHORT).show(); });
    dEffect.addView(btnTest);

    LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10);
    TextView tC = new TextView(this); tC.setText("Chủ đề:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES));
    String curC = prefs.getString("anim_color", "WHITE");
    for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i);
    sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}});
    sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f));
    lC.addView(tC); lC.addView(sC); dEffect.addView(lC);

    LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10);
    TextView tS = new TextView(this); tS.setText("Kiểu chạy:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Nhấp Nháy", "1 Tia sáng nối đuôi", "2 Tia sáng đối xứng", "3 Tia sáng đều nhau"}));
    sS.setSelection(prefs.getInt("anim_style", 0));
    sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}});
    sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f));
    lS.addView(tS); lS.addView(sS); dEffect.addView(lS);

    dEffect.addView(createSlider("Chiều ngang Hiệu ứng (0=Full)", "anim_w", 2000, 0));
    dEffect.addView(createSlider("Chiều dọc Hiệu ứng (0=Full)", "anim_h", 3500, 0));
    dEffect.addView(createSlider("Độ đậm mờ hiệu ứng (Alpha)", "anim_alpha", 255, 255));
    dEffect.addView(createSlider("Độ dày viền", "anim_thick", 50, 12));
    dEffect.addView(createSlider("Thời gian Animation (ms)", "anim_dur", 5000, 1500));
    designSliderContainer.addView(createDrawer("🎬 " + T("EFFECT","HIỆU ỨNG"), dEffect));

    // DRAWER 2: CHỈ BÁO GHI ÂM
    LinearLayout dRec = new LinearLayout(this);
    dRec.setOrientation(LinearLayout.VERTICAL); dRec.setPadding(20,10,20,20);

    Button btnTestRec = new Button(this);
    btnTestRec.setText(recIndicatorTestOn ? "⏹ DONE" : "🔴 TEST ANIMATION RECORD");
    btnTestRec.setBackground(getRounded(recIndicatorTestOn ? "#D32F2F" : "#FFC107", 20f));
    btnTestRec.setTextColor(recIndicatorTestOn ? Color.WHITE : Color.BLACK);
    btnTestRec.setPadding(0,30,0,30);
    LinearLayout.LayoutParams testRecLp = new LinearLayout.LayoutParams(-1,-2); testRecLp.setMargins(0,0,0,20);
    btnTestRec.setLayoutParams(testRecLp);
    btnTestRec.setOnClickListener(v -> {
        recIndicatorTestOn = !recIndicatorTestOn;
        Intent i = new Intent("com.manhmoc.edgebar.TEST_REC_INDICATOR");
        i.setPackage(getPackageName()); i.putExtra("on", recIndicatorTestOn);
        sendBroadcast(i);
        btnTestRec.setText(recIndicatorTestOn ? "⏹ DONE" : "🔴 TEST ANIMATION RECORD");
        btnTestRec.setBackground(getRounded(recIndicatorTestOn ? "#D32F2F" : "#FFC107", 20f));
        btnTestRec.setTextColor(recIndicatorTestOn ? Color.WHITE : Color.BLACK);
    });
    dRec.addView(btnTestRec);
    dRec.addView(createSlider(T("Indicator X position","Vị trí X chỉ báo"), "anim_rec_x", 2000, 1000));
    dRec.addView(createSlider(T("Indicator Y position","Vị trí Y chỉ báo"), "anim_rec_y", 4000, 1000));
    dRec.addView(createSlider(T("Indicator size","Kích thước chỉ báo"), "anim_rec_size", 300, 140));
    dRec.addView(createSlider(T("Indicator Width","Bề rộng chỉ báo"), "anim_rec_width", 800, 260));
    dRec.addView(createSlider(T("Indicator Height","Bề cao chỉ báo"), "anim_rec_height", 300, 90));
    designSliderContainer.addView(createDrawer("🔴 " + T("RECORDING INDICATOR","CHỈ BÁO GHI ÂM"), dRec));

    // DRAWER 3: TÙY CHỌN CHUNG
    LinearLayout dOpt = new LinearLayout(this);
    dOpt.setOrientation(LinearLayout.VERTICAL); dOpt.setPadding(20,10,20,20);
    dOpt.addView(createSlider("Thời gian Vuốt+Giữ (All)", "hold_dur", 2000, 600));
    dOpt.addView(createSlider("Độ rung (ms) (All)", "vib_dur", 100, 30));
    designSliderContainer.addView(createDrawer("⚙️ " + T("GENERAL OPTIONS","TÙY CHỌN CHUNG"), dOpt));

    // DRAWER 4: ICON CHO 13 CỬ CHỈ (đã chuyển hẳn sang Display, không add lại ở Gesture & Touch)
    designSliderContainer.addView(buildGestureIconDrawer());
}
 }
private void renderPanelDesign() {
        designSliderContainer.removeAllViews();
        TextView tvHeader = createSectionTitle("📦 KHO LƯU BIẾN LENAP (DATA PACKS)");
        designSliderContainer.addView(tvHeader);

        LinearLayout globalCfgBody = new LinearLayout(this);
        globalCfgBody.setOrientation(LinearLayout.VERTICAL);
        globalCfgBody.setPadding(20,10,20,20);
        globalCfgBody.addView(createSlider("Độ mờ 120 Icon Hệ Thống/Tùy Chỉnh", "lenap_global_alpha_pool", 255, 255));
        globalCfgBody.addView(createSlider("Kích thước lõi Icon (%)", "lenap_global_icon_scale", 100, 77));
        designSliderContainer.addView(createDrawer("⚙️ TÙY CHỈNH CHUNG LENAP", globalCfgBody));
        // [TỐI ƯU PIXEL 2XL] Đã gỡ bỏ UI nút Reset Lenap theo yêu cầu.
        List<String> ids = getDynamicIds("pack_panel_ids");
ids.sort((idA, idB) -> naturalCompareName(
    prefs.getString("pack_panel_" + idA + "_name", ""),
    prefs.getString("pack_panel_" + idB + "_name", "")));
        if (ids.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Kho biến Panel đang rỗng.\nChạm nút viên thuốc '+ PANEL' góc dưới để tạo mới.");
            tvEmpty.setTextColor(Color.parseColor("#777777"));
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 80, 0, 0);
            designSliderContainer.addView(tvEmpty);
            return;
        }

        if (panelSelectMode) designSliderContainer.addView(buildPanelSelectionToolbar(ids));

        LinearLayout currentRow = null;
        int count = 0;
        String[] POS_ABBR = {"BC", "BL", "BR", "LT", "LC", "LB", "RT", "RC", "RB"};
        for (String id : ids) {
            // Khôi phục logic 2 cột (2 pack / hàng)
            if (count % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                designSliderContainer.addView(currentRow);
            }

            FrameLayout cardWrap = new FrameLayout(this);
            LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            wrapLp.setMargins(6, 6, 6, 6);
            cardWrap.setLayoutParams(wrapLp);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackground(getRounded("#202124", 24f));
            card.setPadding(15, 24, 10, 24);
            // Cột 1 (Trái cùng): Icon Cấu hình (Visibility, Icon Shape, Show Name)
            LinearLayout optCol = new LinearLayout(this);
            optCol.setOrientation(LinearLayout.VERTICAL);
            optCol.setGravity(Gravity.CENTER);
            optCol.setPadding(0, 0, 15, 0);
            
            int iconShape = prefs.getInt("pack_panel_" + id + "_icon_shape", 0);
            int showName = prefs.getInt("pack_panel_" + id + "_show_name", 0);
            int visMode = prefs.getInt("pack_panel_" + id + "_vis", 0);
            
            String strVis = visMode == 1 ? "🌍" : "🎭"; // Toàn cục / Cục bộ
            String strShape = iconShape == 0 ? "⭕" : (iconShape == 1 ? "🔲" : 
(iconShape == 2 ? "☄️" : (iconShape == 3 ? "💥" : (iconShape == 4 ? "⭐" : "⚙️"))));
            String strName = showName == 1 ? "🌕" : "🌑";
            
            TextView tIcons = new TextView(this);
            tIcons.setText(strVis + "\n" + strShape + "\n" + strName);
            tIcons.setTextSize(15);
            tIcons.setLineSpacing(0, 1.2f);
            optCol.addView(tIcons);

            // Cột 2 (Giữa): Info (Tên [Viết tắt vị trí], Thống kê dạng dọc)
            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            
            TextView tName = new TextView(this);
            int posIdx = prefs.getInt("pack_panel_" + id + "_pos", 0);
            String posName = posIdx < POS_ABBR.length ? POS_ABBR[posIdx] : "";
            tName.setText("[" + posName + "] " + prefs.getString("pack_panel_" + id + "_name", "Panel Mới"));
            tName.setTextColor(Color.parseColor("#E8EAED"));
            tName.setTextSize(16);
            tName.setMaxLines(1); tName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            
            String apps = prefs.getString("pack_panel_" + id + "_apps", "");
            String acts = prefs.getString("pack_panel_" + id + "_acts", "");
            String scs = prefs.getString("pack_panel_" + id + "_shortcuts", "");
            int appC = apps.isEmpty() ? 0 : apps.split(",").length;
            int actC = acts.isEmpty() ? 0 : acts.split(",").length;
            int scC = scs.isEmpty() ? 0 : scs.split(",").length;

            TextView tApp = new TextView(this);
            tApp.setText("Apps: " + appC);
            tApp.setTextColor(Color.parseColor("#9AA0A6"));
            tApp.setTextSize(12); tApp.setMaxLines(1);

            TextView tAct = new TextView(this);
            tAct.setText("Acts: " + actC);
            tAct.setTextColor(Color.parseColor("#9AA0A6"));
            tAct.setTextSize(12); tAct.setMaxLines(1);

            TextView tSc = new TextView(this);
            tSc.setText("SCs: " + scC);
            tSc.setTextColor(Color.parseColor("#8AB4F8"));
            tSc.setTextSize(15f); tSc.setMaxLines(1);
            
            infoCol.addView(tName); infoCol.addView(tApp); infoCol.addView(tAct); infoCol.addView(tSc);

            // Cột 3 (Phải cùng): Switch, Copy
            LinearLayout ctrlCol = new LinearLayout(this);
            ctrlCol.setOrientation(LinearLayout.VERTICAL);
            ctrlCol.setGravity(Gravity.CENTER_HORIZONTAL);
            
            Switch swOn = new Switch(this);
            swOn.setChecked(prefs.getBoolean("pack_panel_" + id + "_en", false));
            swOn.setOnCheckedChangeListener((vw, chk) -> {
                prefs.edit().putBoolean("pack_panel_" + id + "_en", chk).apply();
                sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
            });
            swOn.setPadding(0, 0, 0, 10);
            
            Button btnCopy = new Button(this); btnCopy.setText("TEST");
            btnCopy.setBackground(getRounded("#FFC107", 14f));
            btnCopy.setTextColor(Color.BLACK);
            btnCopy.setTextSize(12.5f);
            btnCopy.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            btnCopy.setPadding(12, 4, 12, 10);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-2, -2);
            btnLp.setMargins(0, 8, 0, 0); btnCopy.setLayoutParams(btnLp);
            btnCopy.setMinimumHeight(64);
            final String idForMenu = id;
            btnCopy.setOnClickListener(v -> {
                Intent tIntent = new Intent("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
                tIntent.putExtra("panel_id", idForMenu);
                tIntent.putExtra("on", true);
                sendBroadcast(tIntent);
                Toast.makeText(this, T("Testing panel...", "Đang thử Panel..."), Toast.LENGTH_SHORT).show();
                new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Intent offIntent = new Intent("com.manhmoc.edgebar.PANEL_TEST_TOGGLE");
                    offIntent.putExtra("panel_id", idForMenu);
                    offIntent.putExtra("on", false);
                    sendBroadcast(offIntent);
                }, 4000);
            });
            ctrlCol.addView(swOn); ctrlCol.addView(btnCopy);
            card.addView(optCol); card.addView(infoCol); card.addView(ctrlCol);

            cardWrap.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

           cardWrap.setTag(id);
            if (panelSelectMode) {
                TextView selDot = new TextView(this);
                boolean sel = panelSelectedItems.contains(id);
                selDot.setText(sel ? "🔵" : "⚪");
                selDot.setTextSize(18);
                FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                dotLp.gravity = Gravity.BOTTOM | Gravity.START;
                dotLp.setMargins(10, 0, 0, 6);
                selDot.setLayoutParams(dotLp);
                cardWrap.addView(selDot);
                final String fId = id;
                card.setOnClickListener(v -> {
                    if (panelSelectedItems.contains(fId)) panelSelectedItems.remove(fId);
                    else panelSelectedItems.add(fId);
                    renderPanelDesign();
                });
                card.setOnLongClickListener(v -> true);
            } else {
                card.setOnClickListener(btn -> openDataPackEditor(2, id));
                final String idForLong = id;
                card.setOnLongClickListener(btn -> {
    panelSelectMode = true;
    panelSelectedItems.clear();
    panelSelectedItems.add(idForLong);
    renderPanelDesign();
    return true;
});
            }
            attachDragReorder(cardWrap, ids, "pack_panel_ids", this::renderPanelDesign);
            currentRow.addView(cardWrap);
            count++;
        }
        if (count % 2 != 0 && currentRow != null) {
            View dummy = new View(this);
            dummy.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            currentRow.addView(dummy);
        }
    }
private LinearLayout buildPanelSelectionToolbar(List<String> ids) {
    LinearLayout bar = new LinearLayout(this);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, 0, 0, 20);

    TextView tvCount = new TextView(this);
    tvCount.setText(panelSelectedItems.size() + " " + T("selected", "đã chọn"));
    tvCount.setTextColor(Color.parseColor("#00E5FF"));
    tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

    Button btnDup = new Button(this); btnDup.setText("🧬 " + T("Duplicate", "Nhân bản"));
    btnDup.setBackground(getRounded("#7C4DFF", 20f)); btnDup.setTextColor(Color.WHITE); btnDup.setTextSize(12.5f);
    btnDup.setOnClickListener(v -> {
        String[] fieldsInt = {"pos","color_idx","icon_size","cols","icon_shape","show_name",
            "alpha","panel_length","size","panel_radius","vis","handle_alpha","thick","handle_width","handle_radius"};
        String[] fieldsStr = {"apps","acts","shortcuts"};
        for (String id : new ArrayList<>(panelSelectedItems)) {
            String newId = addDynamicId("pack_panel_ids");
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString("pack_panel_" + newId + "_name", prefs.getString("pack_panel_" + id + "_name", "Panel Pack") + " (Copy)");
            for (String f : fieldsInt) ed.putInt("pack_panel_" + newId + "_" + f, prefs.getInt("pack_panel_" + id + "_" + f, 0));
            for (String f : fieldsStr) ed.putString("pack_panel_" + newId + "_" + f, prefs.getString("pack_panel_" + id + "_" + f, ""));
            ed.putBoolean("pack_panel_" + newId + "_en", false);
            ed.apply();
        }
        panelSelectMode = false; panelSelectedItems.clear();
        renderPanelDesign();
        Toast.makeText(this, T("Duplicated!", "Đã nhân bản!"), Toast.LENGTH_SHORT).show();
    });

    Button btnAll = new Button(this); btnAll.setText(T("All", "Tất cả"));
    btnAll.setBackground(getRounded("#333333", 20f)); btnAll.setTextColor(Color.WHITE); btnAll.setTextSize(12.5f);
    LinearLayout.LayoutParams allLp = new LinearLayout.LayoutParams(-2, -2); allLp.setMargins(10, 0, 10, 0);
    btnAll.setLayoutParams(allLp);
    btnAll.setOnClickListener(v -> {
        if (panelSelectedItems.size() == ids.size()) panelSelectedItems.clear();
        else { panelSelectedItems.clear(); panelSelectedItems.addAll(ids); }
        renderPanelDesign();
    });

    Button btnDelete = new Button(this); btnDelete.setText("🗑️ " + T("Delete", "Xóa"));
    btnDelete.setBackground(getRounded("#D32F2F", 20f)); btnDelete.setTextColor(Color.WHITE); btnDelete.setTextSize(12.5f);
    LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(-2, -2); delLp.setMargins(10, 0, 10, 0);
    btnDelete.setLayoutParams(delLp);
    btnDelete.setOnClickListener(v -> {
    new AlertDialog.Builder(this).setTitle(T("Move to trash?", "Chuyển vào Kho Cũ?"))
        .setPositiveButton(T("MOVE", "CHUYỂN"), (d, w) -> {
            for (String id : panelSelectedItems) moveDataPackToTrash("panel_" + id);
            panelSelectMode = false; panelSelectedItems.clear();
            renderPanelDesign();
        }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
});
    bar.addView(tvCount); bar.addView(btnDup); bar.addView(btnAll); bar.addView(btnDelete);
    return bar;
}
 // [FIX] Nếu 1 Data Pack đang được >1 không gian (Lock/Homeb/Homacc) tham chiếu cùng lúc
// (dữ liệu cũ từ bản trước), tự tách thành bản sao độc lập CHO KHÔNG GIAN ĐANG SỬA trước
// khi mở Editor — đảm bảo sửa ở đây không còn ảnh hưởng không gian khác nữa.
private String ensureExclusiveOwnership(String itemKey, String currentPrefix) {
    int refCount = 0;
    for (String px : new String[]{"lock_","home_","homacc_"}) {
        if (getDynamicIds(px + "applied_packs").contains(itemKey)) refCount++;
    }
    if (refCount <= 1) return itemKey; // đã độc quyền — không cần tách
    boolean isBar = itemKey.startsWith("bar_");
    String oldId = itemKey.replace(isBar ? "bar_" : "corner_", "");
    String newId = cloneDataPack(isBar, oldId);
    String newItemKey = (isBar ? "bar_" : "corner_") + newId;
    clonePackRules(itemKey, newItemKey);
    String listKey = currentPrefix + "applied_packs";
    java.util.List<String> list = getDynamicIds(listKey);
    int idx = list.indexOf(itemKey);
    if (idx >= 0) list.set(idx, newItemKey);
    prefs.edit().putString(listKey, TextUtils.join(",", list)).apply();
    return newItemKey;
}
private void openDataPackEditor(int type, String id) {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(40, 80, 40, 40);
    
    ScrollView scroll = new ScrollView(this);
    scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content);
    root.addView(scroll);
    
    String prefix = type == 0 ? "pack_bar_" : (type == 1 ? "pack_corner_" : "pack_panel_");
        EditText etName = createEcoInput("Tên Data Pack", prefs.getString(prefix +
id + "_name", ""));
    content.addView(etName);

      final SharedPreferences.OnSharedPreferenceChangeListener[]
previewListenerHolder = new
SharedPreferences.OnSharedPreferenceChangeListener[1];

    // V19.12.3.6.30 FIX: overlay live-preview (TYPE_APPLICATION_OVERLAY)
    // đang nổi liên tục ngay khi Dialog mở khiến IME (Gboard) không được
    // hệ thống gọi lên cho etName. Tạm gỡ overlay trong lúc đang gõ tên,
    // vẽ lại ngay khi rời ô nhập — không tốn thêm RAM vì dùng đúng
    // removeLivePreviewOverlay() đã có sẵn, chỉ gọi thêm 1 lần lúc mất focus.
    etName.setOnFocusChangeListener((v, hasFocus) -> {
        if (hasFocus) {
            removeLivePreviewOverlay();
            // [FIX-5] Tạm tắt preview toàn không gian (Lock/Home/Homacc) khi gõ tên —
            // các Bar/Corner ĐÃ BẬT SẴN của không gian đó đang được vẽ dạng
            // TYPE_ACCESSIBILITY_OVERLAY + FLAG_SHOW_WHEN_LOCKED, loại overlay ưu tiên
            // cao có thể chặn Gboard bật lên khi Dialog đang mở phía trên.
            prefs.edit()
                .putBoolean("preview_lock", false)
                .putBoolean("preview_home", false)
                .putBoolean("preview_homacc", false)
                .apply();
            sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        } else {
            if (previewListenerHolder[0] != null) {
                previewListenerHolder[0].onSharedPreferenceChanged(prefs, prefix + id + "_x");
            }
            // Khôi phục lại đúng preview flag của không gian đang đứng (nếu vẫn còn ở Frontier)
            refreshPreview();
        }
    });
        // [TỐI ƯU PIXEL 2XL] Đưa nút Enable lên trên cùng cho cả 3 không gian, xóa nút gắn Pattern thừa thãi
        // Checkbox Enable đã có sẵn trên card ở màn danh sách (renderDataPackList / renderPanelDesign)
// → bỏ hẳn bản trùng này khỏi FormatBar/FormatCorner cho gọn màn hình.
// Panel (type==2) vẫn giữ vì yêu cầu chỉ bỏ ở Bar/Corner.
if (type == 2) {
    // [BỎ UI PREVIEW] Không còn checkbox cho người dùng bấm — Handle của
    // Panel này giờ LUÔN ở trạng thái sẵn sàng hiện ra ngay khi Enable bật,
    // không cần bước "tick xem trước" trung gian nữa.
    prefs.edit().putBoolean(prefix + id + "_preview_handle", true).apply();
}
if (type == 0) {
        int currentLoc = prefs.getInt(prefix + id + "_loc", 0);
        for(int i=0; i<BARS.length; i++) prefs.edit().putBoolean(prefix + id + "_preview_" + BARS[i], false).apply();
        prefs.edit().putBoolean(prefix + id + "_preview_" + BARS[currentLoc], true).apply();
        content.addView(createSectionTitle("CẤU HÌNH BAR (FORMAT B)"));
        LinearLayout locDropdown = createComboDropdown("Chọn vị trí Bar chính", prefix + id + "_loc", BAR_NAMES, 0);
        Spinner locSpinner = (Spinner) locDropdown.getChildAt(1);
        locSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p, View v, int pos, long idx){
                prefs.edit().putInt(prefix + id + "_loc", pos).apply();
                for(int i=0; i<BARS.length; i++) prefs.edit().putBoolean(prefix + id + "_preview_" + BARS[i], false).apply();
                prefs.edit().putBoolean(prefix + id + "_preview_" + BARS[pos], true).apply();
                sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
            }
            public void onNothingSelected(AdapterView<?> p){}
        });
        content.addView(locDropdown);

        previewListenerHolder[0] = (p, k) -> {
            if (k == null || !k.startsWith(prefix + id + "_")) return;
            int loc = prefs.getInt(prefix + id + "_loc", 0);
            boolean previewOn = prefs.getBoolean(prefix + id + "_preview_" + BARS[loc], false);
            if (!previewOn) { removeLivePreviewOverlay(); return; }
            updateLivePreviewBar(loc,
                prefs.getInt(prefix + id + "_alpha", 50),
                prefs.getInt(prefix + id + "_w", 300),
                prefs.getInt(prefix + id + "_h", 60),
                prefs.getInt(prefix + id + "_x", 0),
                prefs.getInt(prefix + id + "_y", 0));
        };
        prefs.registerOnSharedPreferenceChangeListener(previewListenerHolder[0]);
        previewListenerHolder[0].onSharedPreferenceChanged(prefs, prefix + id + "_alpha");
        
        content.addView(createComboDropdown("Hiển thị", prefix + id + "_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình", "Ẩn vô hình"}, 0));
        content.addView(createComboDropdown("Chế độ Cảm ứng", prefix + id + "_pri_mode", new String[]{"Ưu tiên (Khóa cứng)", "Nhường OS (Xuyên thấu)"}, 0));
        content.addView(createComboDropdown(T("Icon Jump Direction (Tap/DTap/Long)","Hướng nhảy Icon (Chạm/2 Chạm/Giữ)"), prefix + id + "_jumpdir",
            new String[]{T("Auto","Tự động"), T("Diagonal Up","Chéo lên"), T("Diagonal Down","Chéo xuống"), T("Straight Up","Thẳng lên"), T("Straight Down","Thẳng xuống"), T("Straight Left","Thẳng trái"), T("Straight Right","Thẳng phải")}, 0));
        content.addView(createSlider("Độ trong suốt", prefix + id + "_alpha", 255, 50));
        content.addView(createSlider("Chiều ngang", prefix + id + "_w", 3000, 300));
        content.addView(createSlider("Chiều dọc", prefix + id + "_h", 3000, 60));
        content.addView(createSlider("Tọa độ X", prefix + id + "_x", 1000, 0));
        content.addView(createSlider("Tọa độ Y", prefix + id + "_y", 3000, 0));

        content.addView(createSectionTitle(T("ICON ON BAR (optional)", "ICON TRÊN BAR (tuỳ chọn)")));
        String iconsStr = prefs.getString(prefix + id + "_icons", "");
        int iconCount = iconsStr.isEmpty() ? 0 : iconsStr.split(",").length;
        Button btnIcons = new Button(this);
        btnIcons.setText(T("CHOOSE ICON (", "CHỌN ICON (") + iconCount + ")");
        btnIcons.setBackground(getRounded("#FFC107", 20f));
        btnIcons.setTextColor(Color.BLACK);
        btnIcons.setOnClickListener(v -> showBarIconMultiPicker(prefix + id + "_icons", () -> {
            String s = prefs.getString(prefix + id + "_icons", "");
            btnIcons.setText(T("CHOOSE ICON (", "CHỌN ICON (") + (s.isEmpty() ? 0 : s.split(",").length) + ")");
        }));
        content.addView(btnIcons);
    } else if (type == 1) {
    String[] cKeys = {"br", "bl", "tr", "tl"};
    // [BỎ UI PREVIEW] Không còn checkbox — Corner luôn xem-trước-sẵn đúng
    // ngay vị trí đang chọn.
    int currentLoc = prefs.getInt(prefix + id + "_loc", 0);
    for(int i=0; i<cKeys.length; i++) prefs.edit().putBoolean(prefix + id + "_preview_" + cKeys[i], false).apply();
    prefs.edit().putBoolean(prefix + id + "_preview_" + cKeys[currentLoc], true).apply();
    content.addView(createSectionTitle("CẤU HÌNH CORNER (FORMAT C)"));
    LinearLayout locDropdown = createComboDropdown("Chọn vị trí Corner chính", prefix + id + "_loc", CORNER_NAMES, 0);
    Spinner locSpinner = (Spinner) locDropdown.getChildAt(1);
    locSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        public void onItemSelected(AdapterView<?> p, View v, int pos, long idx){
            prefs.edit().putInt(prefix + id + "_loc", pos).apply();
            for(int i=0; i<cKeys.length; i++) prefs.edit().putBoolean(prefix + id + "_preview_" + cKeys[i], false).apply();
            prefs.edit().putBoolean(prefix + id + "_preview_" + cKeys[pos], true).apply();
            sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
        }
        public void onNothingSelected(AdapterView<?> p){}
    });
    content.addView(locDropdown);
    // [FIX LIVE PREVIEW] Lắng nghe mọi thay đổi thuộc Data Pack này -> vẽ lại overlay thật
    previewListenerHolder[0] = (p, k) -> {
        if (k == null || !k.startsWith(prefix + id + "_")) return;
        int loc = prefs.getInt(prefix + id + "_loc", 0);
        boolean previewOn = prefs.getBoolean(prefix + id + "_preview_" + cKeys[loc], false);
        if (!previewOn) { removeLivePreviewOverlay(); return; }
        updateLivePreviewCorner(loc, prefix + id + "_");
    };
    prefs.registerOnSharedPreferenceChangeListener(previewListenerHolder[0]);
    previewListenerHolder[0].onSharedPreferenceChanged(prefs, prefix + id + "_x");
    content.addView(createComboDropdown("Hiển thị", prefix + id + "_vis_mode", new String[]{"Hiện hoàn toàn", "Tàng hình", "Ẩn vô hình"}, 0));
        content.addView(createComboDropdown("Chế độ Cảm ứng", prefix + id + "_pri_mode", new String[]{"Ưu tiên (Khóa cứng)", "Nhường OS (Xuyên thấu)"}, 0));
        content.addView(createComboDropdown(T("Icon Jump Direction (Tap/DTap/Long)","Hướng nhảy Icon (Chạm/2 Chạm/Giữ)"), prefix + id + "_jumpdir",
            new String[]{T("Auto","Tự động"), T("Diagonal Up","Chéo lên"), T("Diagonal Down","Chéo xuống"), T("Straight Up","Thẳng lên"), T("Straight Down","Thẳng xuống"), T("Straight Left","Thẳng trái"), T("Straight Right","Thẳng phải")}, 0));
        content.addView(createComboDropdown("Hình dáng Góc", prefix + id + "_shape", new String[]{"Bo Cong", "Thẳng Ngang", "Thẳng Dọc"}, 0));
        content.addView(createSlider("Kéo giãn Ngang Vỏ (X)", prefix + id + "_w", 2500, 100));
        content.addView(createSlider("Kéo giãn Dọc Vỏ (Y)", prefix + id + "_h", 2500, 100));
        content.addView(createSlider("Di chuyển Ngang (X)", prefix + id + "_x", 2500, 0));
        content.addView(createSlider("Di chuyển Dọc (Y)", prefix + id + "_y", 2500, 0));
        content.addView(createSlider("Kéo giãn Ngang Lõi Trăng Non (X)", prefix + id + "_moon_w", 2500, 100));
content.addView(createSlider("Kéo giãn Dọc Lõi Trăng Non (Y)", prefix + id + "_moon_h", 2500, 100));
content.addView(createSlider("Di chuyển Trăng Non Ngang (X) (1250=Giữa)", prefix + id + "_moon_x", 2500, 1250));
content.addView(createSlider("Di chuyển Trăng Non Dọc (Y) (1250=Giữa)", prefix + id + "_moon_y", 2500, 1250));
content.addView(createSlider("Độ cong BO VIÊN", prefix + id + "_rad", 1000, 80));
            content.addView(createSlider("Độ cong TRĂNG NON", prefix + id + "_moon_rad", 1000, 80));
            // [XÓA] 4 slider chung — Frontier đã có Drawer "TÙY CHỈNH CHUNG GÓC VIỀN" áp dụng
            // cho toàn không gian rồi, giữ 2 nơi chỉnh cùng 1 giá trị là dư thừa & gây xung đột.
            // Tối ưu Pixel 2XL: bớt 4x(createSlider = 1 SeekBar + 2 Button + 2 TextView = 5 View)
            // = 20 View object không phải cấp phát mỗi lần mở dialog Format C.
        } else if (type == 2) {
            // Yêu cầu 2 & 4: BÊ NGUYÊN VẸN MỌI THỨ TỪ 3 MỤC Common/Panel Config/Handle Config vào ruột viên thuốc Panel
            content.addView(createSectionTitle("📦 DATA PACK LENAP (CORE CONFIG)"));
            content.addView(createComboDropdown("POSITION (Vị trí Panel)", prefix + id + "_pos", PANEL_POS_NAMES, 0));

            // [FIX #1] LIVE PREVIEW cho Panel — trước đây thiếu hẳn checkbox này,
            // Zero-RAM khi tắt: chỉ addView 1 CheckBox nhẹ, overlay chỉ addView
            // khi bật, gỡ ngay khi tắt/đóng Dialog (dùng chung removeLivePreviewOverlay()
            // đã có sẵn ở cuối openDataPackEditor).
            // --- MỤC 1: COMMON & COLLECTIONS ---
            content.addView(createSectionTitle("1. COMMON & COLLECTIONS"));
            content.addView(createComboDropdown("Color (Màu)", prefix + id + "_color_idx", PANEL_COLOR_NAMES, 0));
            content.addView(createSlider("Icon Size (Kích thước)", prefix + id + "_icon_size", 180, 110));
            content.addView(createSlider("Columns (Số cột 1-9)", prefix + id + "_cols", 9, 4));
            
            String appsStr = prefs.getString(prefix + id + "_apps", "");
            String actsStr = prefs.getString(prefix + id + "_acts", "");
            String scsStr = prefs.getString(prefix + id + "_shortcuts", "");
            int totalCount = (appsStr.isEmpty() ? 0 : appsStr.split(",").length) +
                             (actsStr.isEmpty() ? 0 : actsStr.split(",").length) +
                             (scsStr.isEmpty() ? 0 : scsStr.split(",").length);

            Button btnAddItems = new Button(this);
            btnAddItems.setText("✨ COLLECT (" + totalCount + ")");
            btnAddItems.setBackground(getRounded("#00E5FF", 20f));
            btnAddItems.setTextColor(Color.BLACK);
            btnAddItems.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(-1, 130);
            lpAdd.setMargins(0, 20, 0, 20);
            btnAddItems.setLayoutParams(lpAdd);

            btnAddItems.setOnClickListener(v -> showCombinedPanelPicker(id, () -> {
                String nApps = prefs.getString(prefix + id + "_apps", "");
                String nActs = prefs.getString(prefix + id + "_acts", "");
                String nScs = prefs.getString(prefix + id + "_shortcuts", "");
                int nTotal = (nApps.isEmpty() ? 0 : nApps.split(",").length) +
                             (nActs.isEmpty() ? 0 : nActs.split(",").length) +
                             (nScs.isEmpty() ? 0 : nScs.split(",").length);
                btnAddItems.setText("✨ COLLECT (" + nTotal + ")");
            }));
            content.addView(btnAddItems);
            // --- MỤC 2: PANEL CONFIG (NGĂN KÉO — Lazy Inflate, Zero-RAM khi đóng) ---
LinearLayout panelCfgBody = new LinearLayout(this);
panelCfgBody.setOrientation(LinearLayout.VERTICAL);
panelCfgBody.setPadding(20, 10, 20, 20);
panelCfgBody.setVisibility(View.GONE);

TextView panelCfgHeader = new TextView(this);
panelCfgHeader.setText("📁 PANEL CONFIG (Chạm để mở ▼)");
panelCfgHeader.setTextColor(Color.parseColor("#00E5FF"));
panelCfgHeader.setPadding(30, 30, 30, 30);
panelCfgHeader.setTextSize(16);
panelCfgHeader.setBackground(getRounded("#202124", 25f));

LinearLayout panelCfgDrawer = new LinearLayout(this);
panelCfgDrawer.setOrientation(LinearLayout.VERTICAL);
LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(-1, -2);
pcLp.setMargins(0, 15, 0, 5);
panelCfgDrawer.setLayoutParams(pcLp);
panelCfgDrawer.addView(panelCfgHeader);
panelCfgDrawer.addView(panelCfgBody);
content.addView(panelCfgDrawer);

final boolean[] panelCfgInflated = {false};
panelCfgHeader.setOnClickListener(v -> {
    boolean willOpen = panelCfgBody.getVisibility() == View.GONE;
    if (willOpen && !panelCfgInflated[0]) {
        // Chỉ dựng View đúng 1 lần duy nhất, ngay khi user thực sự mở
        panelCfgInflated[0] = true;
        panelCfgBody.addView(createCycleRow("Icon Style", prefix + id +
"_icon_shape", new String[]{"Circle", "Squircle", "Pebble", "Rough", "Pentacle", "System"}));
        panelCfgBody.addView(createCycleRow("Show Name (Hiện tên)", prefix + id + "_show_name", new String[]{"Không", "Có"}));
        panelCfgBody.addView(createSlider("Opacity (Độ trong suốt)", prefix + id + "_alpha", 255, 200));
        panelCfgBody.addView(createSlider("Length (Chiều dài)", prefix + id + "_panel_length", 3000, 700));
        panelCfgBody.addView(createSlider("Width (Bề dày)", prefix + id + "_size", 2500, 700));
        panelCfgBody.addView(createSlider("Corner Radius (Bo góc)", prefix + id + "_panel_radius", 60, 24));
        panelCfgBody.addView(createSlider(T("Move Horizontal (X) — 500=Center","Di chuyển Ngang (X) — 500=Giữa"), prefix + id + "_offset_x", 1000, 500));
        panelCfgBody.addView(createSlider(T("Move Vertical (Y) — 500=Center","Di chuyển Dọc (Y) — 500=Giữa"), prefix + id + "_offset_y", 1000, 500));
    }
    panelCfgBody.setVisibility(willOpen ? View.VISIBLE : View.GONE);
    panelCfgHeader.setText(willOpen ? "📂 PANEL CONFIG (Chạm để đóng ▲)" : "📁 PANEL CONFIG (Chạm để mở ▼)");
    panelCfgHeader.setBackground(getRounded(willOpen ? "#333333" : "#202124", 25f));
});

// --- MỤC 3: HANDLE CONFIG (NGĂN KÉO — Lazy Inflate, Zero-RAM khi đóng) ---
LinearLayout handleCfgBody = new LinearLayout(this);
handleCfgBody.setOrientation(LinearLayout.VERTICAL);
handleCfgBody.setPadding(20, 10, 20, 20);
handleCfgBody.setVisibility(View.GONE);

TextView handleCfgHeader = new TextView(this);
handleCfgHeader.setText("📁 HANDLE CONFIG (Chạm để mở ▼)");
handleCfgHeader.setTextColor(Color.parseColor("#FFC107"));
handleCfgHeader.setPadding(30, 30, 30, 30);
handleCfgHeader.setTextSize(16);
handleCfgHeader.setBackground(getRounded("#202124", 25f));

LinearLayout handleCfgDrawer = new LinearLayout(this);
handleCfgDrawer.setOrientation(LinearLayout.VERTICAL);
LinearLayout.LayoutParams hcLp2 = new LinearLayout.LayoutParams(-1, -2);
hcLp2.setMargins(0, 10, 0, 15);
handleCfgDrawer.setLayoutParams(hcLp2);
handleCfgDrawer.addView(handleCfgHeader);
handleCfgDrawer.addView(handleCfgBody);
content.addView(handleCfgDrawer);

final boolean[] handleCfgInflated = {false};
handleCfgHeader.setOnClickListener(v -> {
    boolean willOpen = handleCfgBody.getVisibility() == View.GONE;
    if (willOpen && !handleCfgInflated[0]) {
        handleCfgInflated[0] = true;
        handleCfgBody.addView(createCycleRow("Visibility Handle", prefix + id + "_vis", new String[]{"Cục Bộ (chỉ Design)", "Toàn Cục (mọi nơi)"}));
        handleCfgBody.addView(createSlider("Handle Opacity", prefix + id + "_handle_alpha", 255, 255));
        handleCfgBody.addView(createSlider("Handle Length", prefix + id + "_thick", 400, 200));
        handleCfgBody.addView(createSlider("Handle Width", prefix + id + "_handle_width", 200, 56));
        handleCfgBody.addView(createSlider("Handle Radius", prefix + id + "_handle_radius", 100, 28));
    }
    handleCfgBody.setVisibility(willOpen ? View.VISIBLE : View.GONE);
    handleCfgHeader.setText(willOpen ? "📂 HANDLE CONFIG (Chạm để đóng ▲)" : "📁 HANDLE CONFIG (Chạm để mở ▼)");
    handleCfgHeader.setBackground(getRounded(willOpen ? "#333333" : "#202124", 25f));
});
    }
    
    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setPadding(0, 40, 0, 0);
    Button bCancel = new Button(this); bCancel.setText("CANCEL");
    bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    
    Button bSave = new Button(this); bSave.setText("SAVE RULE");
    bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
    slp.setMargins(20, 0, 0, 0); bSave.setLayoutParams(slp);
    
    footer.addView(bCancel); footer.addView(bSave);
    root.addView(footer);
    
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        String name = etName.getText().toString();
        prefs.edit().putString(prefix + id + "_name", name.isEmpty() ? "Data Pack" : name).apply();
        if (type == 2) {
            renderPanelDesign();
            sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
        } else {
            reapplyPackIfEnabled(type, id); // [MỚI] — ĐÂY LÀ DÒNG FIX CHÍNH CỦA BUG
            renderRulesList();
            renderSliders();
        }
        d.dismiss();
    });
    // [FIX LIVE PREVIEW] Hủy listener + gỡ overlay ngay khi đóng Dialog (mọi cách đóng)
    d.setOnDismissListener(dlg -> {
    if (previewListenerHolder[0] != null) prefs.unregisterOnSharedPreferenceChangeListener(previewListenerHolder[0]);
    removeLivePreviewOverlay();
    if (type == 2) {
        // Tự tắt Preview Handle khi rời Editor — tránh Handle Cục Bộ nằm lại
        // vĩnh viễn trên màn hình, tiết kiệm 1 overlay + touch listener trên Pixel 2XL.
        prefs.edit().putBoolean(prefix + id + "_preview_handle", false).apply();
    }
});
    // Ép IME resize đúng theo Dialog fullscreen — một số ROM cần khai báo
    // tường minh, không chỉ dựa vào theme mặc định của Dialog.
    d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    d.setContentView(root); d.show();
}
// Yêu cầu 1: Hàm tạo viên thuốc rỗng ruột cho không gian Lock/Homeb/Homacc/Anima (Zero RAM)
private void openEmptyPillDialog() {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(40, 80, 40, 40);
    
    TextView tvNote = new TextView(this);
    tvNote.setText("💊 VIÊN THUỐC RỖNG (ZERO-RAM OVERHEAD)\n\nRuột viên thuốc ở không gian này được làm trống hoàn toàn theo yêu cầu, chỉ chừa lại 2 nút điều khiển rule để giữ lượng tiêu thụ RAM trên Pixel 2 XL ở mức 0MB.");
    tvNote.setTextColor(Color.parseColor("#777777"));
    tvNote.setTextSize(14f);
    tvNote.setGravity(Gravity.CENTER);
    root.addView(tvNote, new LinearLayout.LayoutParams(-1, 0, 1f));
    
    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setPadding(0, 40, 0, 0);
    Button bCancel = new Button(this); bCancel.setText("CANCEL");
    bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    
    Button bSave = new Button(this); bSave.setText("SAVE RULE");
    bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
    slp.setMargins(20, 0, 0, 0); bSave.setLayoutParams(slp);
    
    footer.addView(bCancel); footer.addView(bSave);
    root.addView(footer);
    
    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        Toast.makeText(this, "Đã lưu Rule rỗng (Zero RAM consumption)!", Toast.LENGTH_SHORT).show();
        d.dismiss();
    });
    d.setContentView(root); d.show();
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
    btnMinus.setOnClickListener(v -> {
    int p = Math.max(0, sb.getProgress() - 1);
    sb.setProgress(p);
    tv.setText(t + ": " + p);
    prefs.edit().putInt(k, p).apply();
    sliderLastWriteMs.put(k, System.currentTimeMillis());
});
btnPlus.setOnClickListener(v -> {
    int p = Math.min(max, sb.getProgress() + 1);
    sb.setProgress(p);
    tv.setText(t + ": " + p);
    prefs.edit().putInt(k, p).apply();
    sliderLastWriteMs.put(k, System.currentTimeMillis());
});
    row.addView(btnMinus); row.addView(sb); row.addView(btnPlus);
    l.addView(row);
    return l;
}
// isApp=true: multi-select app picker (ghi CSV package name)
// isApp=false: multi-select action picker (ghi CSV action key, dùng ACT_KEYS/ACT_LABS có sẵn)
// Gọi phương thức chính nếu không phải là Shortcut
    private void showPanelMultiPicker(String prefKey, boolean isApp) {
    showPanelMultiPicker(prefKey, isApp, false, null);
}
// [FIX] Thêm tham số onSaved — cho phép nơi gọi (3 nút Choose App/Action/Shortcut)
// tự cập nhật chữ trên nút NGAY sau khi Save, không cần đóng cả Dialog Panel.
private void showPanelMultiPicker(String prefKey, boolean isApp, boolean isShortcut) {
    showPanelMultiPicker(prefKey, isApp, isShortcut, null);
}
private void showPanelMultiPicker(String prefKey, boolean isApp, boolean isShortcut, Runnable onSaved) {
        String cur = prefs.getString(prefKey, "");
        final boolean isLockListPicker = isApp && prefKey.equals("applock_list");
        final java.util.Set<String> fastBioSet = new java.util.LinkedHashSet<>();
        if (isLockListPicker) {
            String fb = prefs.getString("applock_fastbio_list", "");
            for (String s : fb.split(",")) if (!s.trim().isEmpty()) fastBioSet.add(s.trim());
        }
        final java.util.List<String> selectedOrder = new java.util.ArrayList<>();
        for (String s : cur.split(",")) { String t = s.trim(); if (!t.isEmpty() && !selectedOrder.contains(t)) selectedOrder.add(t); }
        final List<String[]> allItems = new ArrayList<>();
        if (isApp) {
            // [MỚI] Riêng App của Panel dùng list mang định danh Island; Blacklist/
            // LockList/QR-Bank vẫn dùng list pkg thuần vì chúng so khớp trực tiếp với
            // packageName sự kiện hệ thống (không hiểu định dạng có hậu tố Island).
            boolean isPanelAppPickerNow = prefKey.startsWith("pack_panel_") && prefKey.endsWith("_apps");
            allItems.addAll(isPanelAppPickerNow ? getPanelAppListCached() : getAppListCached());
        } else if (isShortcut) {
            // [THUẬT TOÁN MỚI] Hiển thị danh sách độc lập & Fix lỗi PanelEngine không nhận ID
            String scIds = prefs.getString("panel_shortcut_ids", "");
            if (!scIds.isEmpty()) {
                for (String id : scIds.split(",")) {
                    String nm = prefs.getString("shortcut_" + id + "_name", "Shortcut");
                    allItems.add(new String[]{"🔗 " + nm, id}); // Lưu thuần UUID, bỏ "RUN_SHORTCUT_"
                }
            }
        } else {
    reloadActionLabels();
    for (int i = 1; i < ACT_KEYS.length; i++) {
        if (ACT_KEYS[i] == null || ACT_KEYS[i].equals("LAUNCH_APP")) continue;
        allItems.add(new String[]{ACT_LABS[i], ACT_KEYS[i]});
    }
    // [MỚI] Bổ sung Intent/Macro động — đây là chỗ trước đây bị thiếu khiến
    // Panel không bao giờ gọi được Intent/Macro dù đã có trong Ecosystem.
    // Không tốn thêm I/O: buildDynamicPackItems chỉ đọc lại CSV đã cache sẵn.
    allItems.addAll(buildDynamicPackItems("intent_ids", "intent_", "INTENT_", "Intent"));
    allItems.addAll(buildDynamicPackItems("macro_ids", "macro_", "MACRO_", "Macro"));
}
        // [MỚI] Bật icon-picker riêng cho cả màn "CHỌN ACTION" lẫn "CHỌN APP" của Lenap —
        // không ảnh hưởng Rule Editor (buildRuleEditor dùng showActionCategoryPicker khác hẳn).
        final boolean isPanelActionPicker = !isApp && !isShortcut
            && prefKey.startsWith("pack_panel_") && prefKey.endsWith("_acts");
        final boolean isPanelAppPicker = isApp
            && prefKey.startsWith("pack_panel_") && prefKey.endsWith("_apps");
        final String panelIdForIcons = isPanelActionPicker
            ? prefKey.substring("pack_panel_".length(), prefKey.length() - "_acts".length())
            : (isPanelAppPicker
                ? prefKey.substring("pack_panel_".length(), prefKey.length() - "_apps".length())
                : "");
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(30,80,30,30);
        EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 " + T("Search...","Tìm kiếm..."));
        etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
        etSearch.setBackground(getRounded("#2C2C2C", 20f));
        etSearch.setPadding(30,25,30,25);
        root.addView(etSearch);
        ListView lv = new ListView(this);
        lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        
        final List<String[]> shown = new ArrayList<>();
        final Runnable[] refreshHolder = new Runnable[1];
        final int[] dragFromPos = {-1};
        final boolean[] isDragging = {false};
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(20,22,20,22);
                String[] item = shown.get(p);
                CheckBox cb = new CheckBox(MainActivity.this);
                cb.setChecked(selectedOrder.contains(item[1]));
                cb.setClickable(false);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(item[0]); tv.setTextColor(Color.WHITE);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                row.addView(cb);
                if (isApp) {
                    ImageView ivApp = new ImageView(MainActivity.this);
                    LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(70, 70);
                    ivLp.setMargins(0, 0, 20, 0);
                    ivApp.setLayoutParams(ivLp);
                    loadAppIconInto(item[1], ivApp);
                    row.addView(ivApp);
                }
                row.addView(tv);
                if (isLockListPicker) {
                    Button btnFingerToggle = new Button(MainActivity.this);
                    boolean fbOn = fastBioSet.contains(item[1]);
                    btnFingerToggle.setText(fbOn ? "🔓 Vân tay" : "🔒 PIN");
                    btnFingerToggle.setBackground(getRounded(fbOn ? "#4CAF50" : "#303134", 14f));
                    btnFingerToggle.setTextColor(Color.WHITE);
                    btnFingerToggle.setTextSize(10.5f);
                    btnFingerToggle.setPadding(14, 8, 14, 8);
                    btnFingerToggle.setOnClickListener(v -> {
                        if (fastBioSet.contains(item[1])) fastBioSet.remove(item[1]);
                        else fastBioSet.add(item[1]);
                        prefs.edit().putString("applock_fastbio_list",
                            TextUtils.join(",", fastBioSet)).apply();
                        refreshHolder[0].run();
                    });
                    row.addView(btnFingerToggle);
                }
                if (isPanelActionPicker || isPanelAppPicker) {
                    Button btnEditIcon = new Button(MainActivity.this);
                    btnEditIcon.setText("🖌");
                    btnEditIcon.setBackground(getRounded("#303134", 14f));
                    btnEditIcon.setTextColor(Color.WHITE);
                    btnEditIcon.setPadding(16, 8, 16, 8);
                    btnEditIcon.setOnClickListener(v -> showIconPickerForPanelAction(
                        panelIdForIcons, item[1], () -> refreshHolder[0].run()));
                    row.addView(btnEditIcon);
                }
                if (isShortcut) {
                    Button btnEditScIcon = new Button(MainActivity.this);
                    btnEditScIcon.setText("🖌");
                    btnEditScIcon.setBackground(getRounded("#303134", 14f));
                    btnEditScIcon.setTextColor(Color.WHITE);
                    btnEditScIcon.setPadding(16, 8, 16, 8);
                    final String scIdForIcon = item[1];
                    btnEditScIcon.setOnClickListener(v -> showIconPickerDialog(
                        "shortcut_" + scIdForIcon + "_icon_override", () -> refreshHolder[0].run()));
                    row.addView(btnEditScIcon);

                    Button btnDelSc = new Button(MainActivity.this);
                    btnDelSc.setText("🗑");
                    btnDelSc.setBackground(getRounded("#D32F2F", 14f));
                    btnDelSc.setTextColor(Color.WHITE);
                    btnDelSc.setPadding(16, 8, 16, 8);
                    final String scId = item[1];
                    btnDelSc.setOnClickListener(v -> {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(T("Delete this shortcut?", "Xóa shortcut này?"))
                            .setPositiveButton(T("DELETE", "XÓA"), (d, w) -> {
                                deleteShortcutGlobally(scId);
                                selectedOrder.remove(scId);
                                for (int k = allItems.size() - 1; k >= 0; k--)
                                    if (allItems.get(k)[1].equals(scId)) allItems.remove(k);
                                refreshHolder[0].run();
                            }).setNegativeButton(T("CANCEL", "HỦY"), null).show();
                    });
                    row.addView(btnDelSc);
                }
                row.setOnClickListener(v -> {
                    if (selectedOrder.contains(item[1])) selectedOrder.remove(item[1]);
                    else selectedOrder.add(item[1]);
                    refreshHolder[0].run();
                });
                if (selectedOrder.contains(item[1])) {
                    TextView dragHandle = new TextView(MainActivity.this);
                    dragHandle.setText("☰");
                    dragHandle.setTextColor(Color.parseColor("#8AB4F8"));
                    dragHandle.setTextSize(16);
                    LinearLayout.LayoutParams dhLp = new LinearLayout.LayoutParams(-2, -2);
                    dhLp.setMargins(0, 0, 15, 0);
                    dragHandle.setLayoutParams(dhLp);
                    row.addView(dragHandle, 0);
                    row.setOnLongClickListener(v -> {
                        dragFromPos[0] = p;
                        isDragging[0] = true;
                        return true;
                    });
                }
                return row;
            }
        };
        lv.setAdapter(adapter);

        // [MỚI] Giữ-chạm 1 mục ĐÃ CHỌN để bắt đầu kéo đổi vị trí — chỉ áp dụng cho các
        // mục đã tick (những mục này luôn được xếp đầu danh sách `shown` theo đúng thứ tự
        // selectedOrder, nên hoán đổi trong selectedOrder là đủ để đổi vị trí hiển thị).
        lv.setOnTouchListener((v, e) -> {
            if (!isDragging[0]) return false; // chưa kéo -> để ListView xử lý click/long-click bình thường
            int action = e.getAction();
            if (action == MotionEvent.ACTION_MOVE) {
                int targetPos = lv.pointToPosition((int) e.getX(), (int) e.getY());
                if (targetPos != android.widget.AdapterView.INVALID_POSITION
                        && targetPos != dragFromPos[0] && targetPos < selectedOrder.size()
                        && dragFromPos[0] < selectedOrder.size()) {
                    String moved = selectedOrder.remove(dragFromPos[0]);
                    selectedOrder.add(targetPos, moved);
                    dragFromPos[0] = targetPos;
                    refreshHolder[0].run();
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isDragging[0] = false;
                dragFromPos[0] = -1;
                return true;
            }
            return true;
        });
        if (isShortcut) {
            Button btnNewShortcut = new Button(this);
            btnNewShortcut.setText("➕ " + T("Create new Shortcut", "Tạo Shortcut mới"));
            btnNewShortcut.setBackground(getRounded("#7C4DFF", 20f));
            btnNewShortcut.setTextColor(Color.WHITE);
            btnNewShortcut.setTextSize(13.5f);
            LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(-1, -2);
            nsLp.setMargins(0, 10, 0, 10);
            btnNewShortcut.setLayoutParams(nsLp);
            btnNewShortcut.setOnClickListener(v -> {
                prefs.edit().putBoolean("is_panel_shortcut_pending", true).apply();
                showShortcutPickerDialog((newId, newName) -> {
                    boolean already = false;
                    for (String[] it : allItems) if (it[1].equals(newId)) { already = true; break; }
                    if (!already) allItems.add(new String[]{"🔗 " + newName, newId});
                    selectedOrder.add(newId);
                    refreshHolder[0].run();
                    
                    // [THUẬT TOÁN MỚI] Tự động Lưu & Đồng bộ Panel tức thì (Thời gian thực)
                    prefs.edit().putString(prefKey, TextUtils.join(",", selectedOrder)).apply();
                    syncPanelService();
                    if (onSaved != null) onSaved.run();
                });
            });
            root.addView(btnNewShortcut);
        }
        root.addView(lv);
        
        Runnable doRefresh = () -> {
            String q = etSearch.getText().toString().trim().toLowerCase();
            shown.clear();
            for (String key : selectedOrder) {
                String[] found = null;
                for (String[] it : allItems) if (it[1].equals(key)) { found = it; break; }
                if (found == null) continue;
                if (!q.isEmpty() && !found[0].toLowerCase().contains(q)) continue;
                shown.add(found);
            }
            for (String[] it : allItems) {
                if (selectedOrder.contains(it[1])) continue; 
                if (!q.isEmpty() && !it[0].toLowerCase().contains(q)) continue;
                shown.add(it);
            }
            adapter.notifyDataSetChanged();
        };
        refreshHolder[0] = doRefresh;
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) { doRefresh.run(); }
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
        });
        doRefresh.run();
        
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
        Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY"));
        bCancel.setBackground(getRounded("#333333",20f));
        bCancel.setTextColor(Color.WHITE); bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Button bSave = new Button(this); bSave.setText(T("SAVE","LƯU"));
        bSave.setBackground(getRounded("#4CAF50",20f));
        bSave.setTextColor(Color.WHITE); LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0);
        bSave.setLayoutParams(slp);
        footer.addView(bCancel); footer.addView(bSave); root.addView(footer);
        
        bCancel.setOnClickListener(v -> d.dismiss());
        bSave.setOnClickListener(v -> {
            prefs.edit().putString(prefKey, TextUtils.join(",", selectedOrder)).apply();
            syncPanelService(); 
            renderSliders();
            if (onSaved != null) onSaved.run();
            d.dismiss();
        });
        d.setContentView(root); d.show();
    }
private ScrollView buildIconGridPage(int[] pool, String prefixTag, String prefKey, Dialog d, Runnable onSaved) {
    ScrollView scroll = new ScrollView(this);
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(page);
    LinearLayout row = null;
    for (int i = 0; i < pool.length; i++) {
        if (i % 5 == 0) {
            row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            page.addView(row);
        }
        ImageView iv = new ImageView(this);
        Bitmap normBmp = normalizeIconBitmap(getDrawable(pool[i]), 140, 0.68f);
        if (normBmp != null) iv.setImageBitmap(normBmp);
        else { iv.setImageResource(pool[i]); iv.setPadding(24, 24, 24, 24); }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(0, 140, 1f);
        ivLp.setMargins(6,6,6,6);
        iv.setLayoutParams(ivLp);
        iv.setBackground(getRounded("#202124", 16f));
        final int idxFinal = i;
        iv.setOnClickListener(v -> {
            prefs.edit().putString(prefKey, prefixTag + idxFinal).apply();
            if (onSaved != null) onSaved.run();
            d.dismiss();
        });
        row.addView(iv);
    }
    return scroll;
}
private void showCombinedPanelPicker(String panelId, Runnable onSaved) {
    String prefPrefix = "pack_panel_" + panelId + "_";
    List<String> selApps = new ArrayList<>(csvToList(prefs.getString(prefPrefix + "apps", "")));
    List<String> selActs = new ArrayList<>(csvToList(prefs.getString(prefPrefix + "acts", "")));
    List<String> selScs = new ArrayList<>(csvToList(prefs.getString(prefPrefix + "shortcuts", "")));

    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(30, 80, 30, 30);

    TextView tvTitle = new TextView(this);
    tvTitle.setText("Chỉnh sửa Items trên Panel");
    tvTitle.setTextColor(Color.parseColor("#00E5FF"));
    tvTitle.setTextSize(18); tvTitle.setPadding(0, 0, 0, 20);
    root.addView(tvTitle);

    LinearLayout tabs = new LinearLayout(this);
    tabs.setOrientation(LinearLayout.HORIZONTAL);
    Button bApps = createTabBtn("APP");
    Button bActs = createTabBtn("ACTION");
    Button bScs = createTabBtn("SHORTCUT");
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
    tlp.setMargins(0,0,10,0);
    bApps.setLayoutParams(tlp); bActs.setLayoutParams(tlp);
    bScs.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    tabs.addView(bApps); tabs.addView(bActs); tabs.addView(bScs);
    root.addView(tabs);

    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 Tìm kiếm...");
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f));
    etSearch.setPadding(30, 25, 30, 25);
    LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(-1, -2);
    lpSearch.setMargins(0, 20, 0, 20);
    etSearch.setLayoutParams(lpSearch);
    root.addView(etSearch);

    Button btnNewShortcut = new Button(this);
    btnNewShortcut.setText("➕ Tạo Shortcut mới");
    btnNewShortcut.setBackground(getRounded("#7C4DFF", 20f));
    btnNewShortcut.setTextColor(Color.WHITE);
    btnNewShortcut.setTextSize(13.5f);
    LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(-1, -2);
    nsLp.setMargins(0, 0, 0, 20);
    btnNewShortcut.setLayoutParams(nsLp);
    btnNewShortcut.setVisibility(View.GONE);
    root.addView(btnNewShortcut);

    ListView lv = new ListView(this);
    lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(lv);

    // Chuẩn bị Data cho 3 thẻ (Thêm index phân loại category)
    List<String[]> allApps = new ArrayList<>();
    for (String[] a : getPanelAppListCached()) allApps.add(new String[]{a[0], a[1], "APP"});

    List<String[]> allActs = new ArrayList<>();
    reloadActionLabels();
    List<String> sysList = Arrays.asList("BACK","HOME","RECENTS","SCREEN_OFF","FLASH","POWER_DIALOG","VOLUME","SCREENSHOT","CAMERA","NOTIFICATIONS","QUICK_SETTINGS","SPLIT_SCREEN","SCREEN_RECORD","AUTO_ROTATE_TOGGLE");
    List<String> utlList = Arrays.asList("TOGGLE_OVERLAY","TOGGLE_RECORD","PAUSE_RECORD","YTDL_DOWNLOAD","TOGGLE_WORK_PROFILE","OPEN_STORAGE_SCAN","SCAN_QR","PLAY_MY_PLAYLIST");
    
    for (int i = 1; i < ACT_KEYS.length; i++) {
        if (ACT_KEYS[i] == null || ACT_KEYS[i].equals("LAUNCH_APP")) continue;
        String cat = sysList.contains(ACT_KEYS[i]) ? "SYS" : (utlList.contains(ACT_KEYS[i]) ? "UTL" : "SYS");
        allActs.add(new String[]{ACT_LABS[i], ACT_KEYS[i], cat});
    }
    for (String[] item : buildDynamicPackItems("intent_ids", "intent_", "INTENT_", "Intent")) allActs.add(new String[]{item[0], item[1], "INT"});
    for (String[] item : buildDynamicPackItems("macro_ids", "macro_", "MACRO_", "Macro")) allActs.add(new String[]{item[0], item[1], "INT"});
    
    List<String[]> allScs = new ArrayList<>();
    String scIds = prefs.getString("panel_shortcut_ids", "");
    if (!scIds.isEmpty()) {
        for (String id : scIds.split(",")) {
            String nm = prefs.getString("shortcut_" + id + "_name", "Shortcut");
            allScs.add(new String[]{"🔗 " + nm, id, "SC"});
        }
    }

    final int[] currentTab = {0}; // 0=App, 1=Act, 2=Sc
    final boolean[] actDrawers = {false, false, false}; // Trạng thái mở của 3 ngăn kéo Action (Mặc định đóng)
    final List<String[]> shownList = new ArrayList<>();
    
    final String[] LETTERS = {"-", "A", "B", "C", "D", "E", "F", "G", "H", "I"};
    final String[] ROMANS = {"-", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    Runnable refreshList = () -> {
        shownList.clear();
        String q = etSearch.getText().toString().trim().toLowerCase();
        
        if (currentTab[0] == 0 || currentTab[0] == 2) {
            // APP và SHORTCUT: Phẳng (Flat List)
            List<String[]> source = currentTab[0] == 0 ? allApps : allScs;
            List<String> selectedFilter = currentTab[0] == 0 ? selApps : selScs;
            for (String[] item : source) {
                if (selectedFilter.contains(item[1]) && (q.isEmpty() || item[0].toLowerCase().contains(q)))
                    shownList.add(item);
            }
            for (String[] item : source) {
                if (!selectedFilter.contains(item[1]) && (q.isEmpty() || item[0].toLowerCase().contains(q)))
                    shownList.add(item);
            }
        } else {
            // ACTION: Chế độ Ngăn kéo
            // 1. Đưa tất cả mục đã chọn lên đầu (Luôn mở)
            for (String[] item : allActs) {
                if (selActs.contains(item[1]) && (q.isEmpty() || item[0].toLowerCase().contains(q))) {
                    shownList.add(item);
                }
            }
            // 2. Hiển thị 3 Ngăn Kéo cho các mục CHƯA chọn
            String[] catCodes = {"SYS", "UTL", "INT"};
            String[] catNames = {"⚙️ SYSTEM", "🛠️ UTILITIES", "⚡ INTENTS & MACROS"};
            for (int c = 0; c < 3; c++) {
                boolean isOpen = actDrawers[c] || !q.isEmpty(); // Tìm kiếm tự động mở drawer
                shownList.add(new String[]{catNames[c] + (isOpen ? " (▲)" : " (▼)"), "HEADER_" + c, "HEADER"});
                if (isOpen) {
                    for (String[] item : allActs) {
                        if (!selActs.contains(item[1]) && item[2].equals(catCodes[c]) && (q.isEmpty() || item[0].toLowerCase().contains(q))) {
                            shownList.add(item);
                        }
                    }
                }
            }
        }
        ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
    };

    final int[] dragFromPos = {-1};
    final boolean[] isDragging = {false};

    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return shownList.size(); }
        @Override public Object getItem(int p) { return shownList.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            String[] item = shownList.get(p);
            
            // Xử lý Render Header của Ngăn Kéo Action
            if (item[2].equals("HEADER")) {
                LinearLayout header = new LinearLayout(MainActivity.this);
                header.setPadding(20, 35, 20, 35);
                header.setBackground(getRounded("#161616", 16f));
                LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(-1, -2);
                hLp.setMargins(0, 10, 0, 10);
                header.setLayoutParams(hLp);
                
                TextView tvH = new TextView(MainActivity.this);
                tvH.setText(item[0]);
                tvH.setTextColor(Color.parseColor("#00E5FF"));
                tvH.setTextSize(15f);
                tvH.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                header.addView(tvH);
                
                header.setOnClickListener(v -> {
                    int c = Integer.parseInt(item[1].split("_")[1]);
                    actDrawers[c] = !actDrawers[c];
                    etSearch.clearFocus();
                    refreshList.run();
                });
                return header;
            }

            // Render Item thông thường
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(20, 22, 20, 22);

            String ref = item[1];
            List<String> currentSelList = currentTab[0] == 0 ? selApps : (currentTab[0] == 1 ? selActs : selScs);
            boolean isSelected = currentSelList.contains(ref);

            row.setBackground(getRounded(isSelected ? "#1A3B3F" : "#1A1A1A", 16f));

            // Nút Kéo Thả (Drag Handle) đưa lên ĐẦU TIÊN
            if (isSelected) {
                TextView dragHandle = new TextView(MainActivity.this);
                dragHandle.setText("☰"); 
                dragHandle.setTextColor(Color.parseColor("#8AB4F8"));
                dragHandle.setTextSize(26);
                dragHandle.setPadding(0, 0, 35, 0);
                
                // Đã bỏ lv.requestDisallowInterceptTouchEvent(true) để trả lại luồng Touch cho ListView
                                dragHandle.setOnTouchListener((vh, ev) -> {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            dragFromPos[0] = p; isDragging[0] = true; return true;
                        case MotionEvent.ACTION_MOVE: {
                            if (!isDragging[0]) return true;
                            int[] loc = new int[2]; lv.getLocationOnScreen(loc);
                            int targetPos = lv.pointToPosition(0, (int) ev.getRawY() - loc[1]);
                            List<String> curList = currentTab[0] == 0 ? selApps : (currentTab[0] == 1 ? selActs : selScs);
                            if (targetPos != android.widget.AdapterView.INVALID_POSITION
                                    && targetPos != dragFromPos[0] && targetPos < curList.size()
                                    && dragFromPos[0] < curList.size()) {
                                String moved = curList.remove(dragFromPos[0]);
                                curList.add(targetPos, moved);
                                dragFromPos[0] = targetPos;
                                refreshList.run();
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                            isDragging[0] = false; dragFromPos[0] = -1; return true;
                    }
                    return true;
                });
                row.addView(dragHandle);
            }

            if (currentTab[0] == 0) {
                ImageView ivApp = new ImageView(MainActivity.this);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(70, 70);
                ivLp.setMargins(0, 0, 25, 0);
                ivApp.setLayoutParams(ivLp);
                loadAppIconInto(ref, ivApp);
                row.addView(ivApp);
            }

            TextView tvTitle = new TextView(MainActivity.this);
            tvTitle.setText(item[0]);
            tvTitle.setTextColor(Color.WHITE);
            tvTitle.setMaxLines(2);
            tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tvTitle);

            if (isSelected) {
                LinearLayout controls = new LinearLayout(MainActivity.this);
                controls.setOrientation(LinearLayout.HORIZONTAL);

                String posKey = prefPrefix + "posmap_" + ref;
                String[] savedPos = prefs.getString(posKey, "-,-").split(",");
                String curLetter = savedPos[0];
                String curRoman = savedPos.length > 1 ? savedPos[1] : "-";

                // Hàm tạo nút to 130x130
                java.util.function.Function<String, Button> makeMiniBtn = (text) -> {
                    Button b = new Button(MainActivity.this);
                    b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(16f); // Font chữ to
                    b.setPadding(0, 0, 0, 0); b.setBackground(getRounded("#303134", 20f)); // Bo góc mạnh hơn
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(130, 130); // Kích thước nút to
                    blp.setMargins(10, 0, 10, 0); b.setLayoutParams(blp);
                    return b;
                };

                // Nút Thùng Rác ĐƯỢC CHUYỂN LÊN ĐÂY (Chỉ có ở Shortcut, sẽ được add vào BÊN TRÁI nút Letter)
                if (currentTab[0] == 2) { 
                    Button btnTrashSc = makeMiniBtn.apply("🗑");
                    btnTrashSc.setBackground(getRounded("#D32F2F", 20f));
                    btnTrashSc.setOnClickListener(v -> {
                        new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Xóa shortcut này?")
                            .setMessage("Shortcut này sẽ bị xoá khỏi danh sách hệ thống và chuyển vào Kho Cũ.")
                            .setPositiveButton("XÓA", (dlg, w) -> {
                                moveDataPackToTrash("shortcut_" + ref);
                                currentSelList.remove(ref);
                                for (int k = allScs.size() - 1; k >= 0; k--) {
                                    if (allScs.get(k)[1].equals(ref)) allScs.remove(k);
                                }
                                refreshList.run();
                            }).setNegativeButton("HỦY", null).show();
                    });
                    controls.addView(btnTrashSc);
                }

                // Phím chọn vị trí chữ
                Button btnLetter = makeMiniBtn.apply(curLetter);
                btnLetter.setTextColor(Color.parseColor("#FFC107"));
                btnLetter.setOnClickListener(v -> {
                    new android.app.AlertDialog.Builder(MainActivity.this).setItems(LETTERS, (dlg, which) -> {
                        String updatedPos = LETTERS[which] + "," + curRoman;
                        prefs.edit().putString(posKey, updatedPos).apply();
                        refreshList.run();
                    }).show();
                });
                controls.addView(btnLetter);

                // Phím chọn vị trí số
                Button btnRoman = makeMiniBtn.apply(curRoman);
                btnRoman.setTextColor(Color.parseColor("#4CAF50"));
                btnRoman.setOnClickListener(v -> {
                    new android.app.AlertDialog.Builder(MainActivity.this).setItems(ROMANS, (dlg, which) -> {
                        String updatedPos = curLetter + "," + ROMANS[which];
                        prefs.edit().putString(posKey, updatedPos).apply();
                        refreshList.run();
                    }).show();
                });
                controls.addView(btnRoman);

                // Nút Chổi Cọ (Brush Override Icon)
                if (currentTab[0] == 0 || currentTab[0] == 1) { // APP & ACTION
                    Button btnIcon = makeMiniBtn.apply("🖌");
                    btnIcon.setOnClickListener(v -> showIconPickerForPanelAction(panelId, ref, refreshList));
                    controls.addView(btnIcon);
                } else if (currentTab[0] == 2) { // SHORTCUT
                    Button btnIcon = makeMiniBtn.apply("🖌");
                    btnIcon.setOnClickListener(v -> showIconPickerDialog("shortcut_" + ref + "_icon_override", refreshList));
                    controls.addView(btnIcon);
                }
                
                row.addView(controls);
            }

            // CheckBox chọn mục, thế chỗ Thùng rác (ĐƯA XUỐNG CUỐI CÙNG BÊN PHẢI)
            CheckBox cb = new CheckBox(MainActivity.this);
            cb.setChecked(isSelected);
            cb.setClickable(false);
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-2, -2);
            cbLp.setMargins(20, 0, 0, 0);
            cb.setLayoutParams(cbLp);
            row.addView(cb);

            row.setOnClickListener(v -> {
                if (currentSelList.contains(ref)) {
                    currentSelList.remove(ref);
                    prefs.edit().remove(prefPrefix + "posmap_" + ref).apply();
                } else {
                    currentSelList.add(ref);
                }
                refreshList.run();
            });

            return row;
        }
    };
    lv.setAdapter(adapter);

    btnNewShortcut.setOnClickListener(v -> {
        prefs.edit().putBoolean("is_panel_shortcut_pending", true).apply();
        showShortcutPickerDialog((newId, newName) -> {
            boolean already = false;
            for (String[] it : allScs) if (it[1].equals(newId)) { already = true; break; }
            if (!already) allScs.add(new String[]{"🔗 " + newName, newId, "SC"});
            selScs.add(newId);
            refreshList.run();
        });
    });

    View.OnClickListener tabClick = v -> {
        bApps.setBackground(getRounded(v == bApps ? "#00E5FF" : "#222222", 15f));
        bApps.setTextColor(v == bApps ? Color.BLACK : Color.WHITE);
        bActs.setBackground(getRounded(v == bActs ? "#00E5FF" : "#222222", 15f));
        bActs.setTextColor(v == bActs ? Color.BLACK : Color.WHITE);
        bScs.setBackground(getRounded(v == bScs ? "#00E5FF" : "#222222", 15f));
        bScs.setTextColor(v == bScs ? Color.BLACK : Color.WHITE);
        
        currentTab[0] = (v == bApps) ? 0 : (v == bActs ? 1 : 2);
        btnNewShortcut.setVisibility(v == bScs ? View.VISIBLE : View.GONE);
        etSearch.setText("");
        refreshList.run();
    };
    bApps.setOnClickListener(tabClick); bActs.setOnClickListener(tabClick); bScs.setOnClickListener(tabClick);
    bApps.performClick();

    etSearch.addTextChangedListener(new android.text.TextWatcher() {
        public void afterTextChanged(android.text.Editable s) { refreshList.run(); }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
    });

    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0, 20, 0, 0);
    Button bCancel = new Button(this); bCancel.setText("HỦY");
    bCancel.setBackground(getRounded("#333333", 20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    Button bSave = new Button(this); bSave.setText("LƯU");
    bSave.setBackground(getRounded("#4CAF50", 20f)); bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f); slp.setMargins(20, 0, 0, 0);
    bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        prefs.edit()
            .putString(prefPrefix + "apps", android.text.TextUtils.join(",", selApps))
            .putString(prefPrefix + "acts", android.text.TextUtils.join(",", selActs))
            .putString(prefPrefix + "shortcuts", android.text.TextUtils.join(",", selScs))
            .apply();
        syncPanelService();
        if (onSaved != null) onSaved.run();
        d.dismiss();
    });

    d.setContentView(root); d.show();
}
   private void showBarIconMultiPicker(String prefKey, Runnable onSaved) {
    String cur = prefs.getString(prefKey, "");
    final java.util.List<String> selectedOrder = new java.util.ArrayList<>();
    for (String s : cur.split(",")) { String t = s.trim(); if (!t.isEmpty() && !selectedOrder.contains(t)) selectedOrder.add(t); }

    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(30, 80, 30, 30);

    TextView title = new TextView(this);
    title.setText(T("Choose icons for the bar", "Chọn icon cho thanh"));
    title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
    root.addView(title);

    LinearLayout tabs = new LinearLayout(this);
    tabs.setOrientation(LinearLayout.HORIZONTAL);
    Button bApps = createTabBtn(T("APPS", "APP ĐÃ CÀI"));
    Button bPool = createTabBtn(T("SYSTEM", "HỆ THỐNG"));
    Button bCustom = createTabBtn(T("CUSTOM", "TÙY CHỈNH"));
    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
    tlp.setMargins(0,0,10,0);
    bApps.setLayoutParams(tlp); bPool.setLayoutParams(tlp);
    bCustom.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    tabs.addView(bApps); tabs.addView(bPool); tabs.addView(bCustom);
    root.addView(tabs);

    TextView tvCount = new TextView(this);
    tvCount.setTextColor(Color.parseColor("#8AB4F8")); tvCount.setTextSize(12f);
    tvCount.setPadding(0, 15, 0, 10);
    root.addView(tvCount);

    FrameLayout body = new FrameLayout(this);
    body.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    root.addView(body);

    Runnable[] refreshCount = new Runnable[1];
    refreshCount[0] = () -> tvCount.setText(selectedOrder.size() + " " + T("selected", "đã chọn"));

    // ===== TAB 1: APPS (có ô tìm kiếm + lưới icon thật) =====
    LinearLayout appsPage = new LinearLayout(this);
    appsPage.setOrientation(LinearLayout.VERTICAL);
    EditText etSearch = new EditText(this);
    etSearch.setHint("🔍 " + T("Search...", "Tìm kiếm..."));
    etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
    etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
    appsPage.addView(etSearch);
    ScrollView appsScroll = new ScrollView(this);
    appsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout appsGrid = new LinearLayout(this);
    appsGrid.setOrientation(LinearLayout.VERTICAL);
    appsGrid.setPadding(0, 10, 0, 10);
    appsScroll.addView(appsGrid);
    appsPage.addView(appsScroll);
    List<String[]> allApps = getAppListCached();

    Runnable[] refreshApps = new Runnable[1];
    refreshApps[0] = () -> {
        appsGrid.removeAllViews();
        String q = etSearch.getText().toString().trim().toLowerCase();
        LinearLayout row = null; int count = 0;
        for (String[] app : allApps) {
            if (!q.isEmpty() && !app[0].toLowerCase().contains(q)) continue;
            String ref = "app:" + app[1];
            if (count % 5 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); appsGrid.addView(row); }
            row.addView(buildIconGridCell(ref, () -> { ImageView iv = new ImageView(this); loadAppIconInto(app[1], iv); return iv; },
                selectedOrder, () -> { refreshCount[0].run(); }));
            count++;
        }
    };
    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){ refreshApps[0].run(); }
        public void beforeTextChanged(CharSequence s,int a,int b,int c){}
        public void onTextChanged(CharSequence s,int a,int b,int c){}
    });

    // ===== TAB 2 & 3: SYSTEM / CUSTOM (lưới icon tĩnh) =====
    ScrollView poolScroll = new ScrollView(this);
    LinearLayout poolGrid = new LinearLayout(this); poolGrid.setOrientation(LinearLayout.VERTICAL); poolGrid.setPadding(0,10,0,10);
    poolScroll.addView(poolGrid);
    int[] sysPool = PanelEngine.SYSTEM_ICON_POOL;
    LinearLayout prow = null;
    for (int i = 0; i < sysPool.length; i++) {
        String ref = "pool:" + i;
        if (i % 5 == 0) { prow = new LinearLayout(this); prow.setOrientation(LinearLayout.HORIZONTAL); poolGrid.addView(prow); }
        int resId = sysPool[i];
        prow.addView(buildIconGridCell(ref, () -> {
            ImageView iv = new ImageView(this);
            Bitmap nb = normalizeIconBitmap(getDrawable(resId), 100, 0.68f);
            if (nb != null) iv.setImageBitmap(nb); else iv.setImageResource(resId);
            return iv;
        }, selectedOrder, () -> refreshCount[0].run()));
    }

    ScrollView customScroll = new ScrollView(this);
    LinearLayout customGrid = new LinearLayout(this); customGrid.setOrientation(LinearLayout.VERTICAL); customGrid.setPadding(0,10,0,10);
    customScroll.addView(customGrid);
    int[] customPool = PanelEngine.getCustomIconPool(this);
    LinearLayout crow = null;
    for (int i = 0; i < customPool.length; i++) {
        String ref = "poolc:" + i;
        if (i % 5 == 0) { crow = new LinearLayout(this); crow.setOrientation(LinearLayout.HORIZONTAL); customGrid.addView(crow); }
        int resId = customPool[i];
        crow.addView(buildIconGridCell(ref, () -> {
            ImageView iv = new ImageView(this);
            Bitmap nb = normalizeIconBitmap(getDrawable(resId), 100, 0.68f);
            if (nb != null) iv.setImageBitmap(nb); else iv.setImageResource(resId);
            return iv;
        }, selectedOrder, () -> refreshCount[0].run()));
    }

    body.addView(appsPage); body.addView(poolScroll); body.addView(customScroll);
    poolScroll.setVisibility(View.GONE);
    customScroll.setVisibility(View.GONE);
    refreshApps[0].run();
    refreshCount[0].run();

    View.OnClickListener tabClick = v -> {
        bApps.setBackground(getRounded(v==bApps?"#00E5FF":"#222222", 15f)); bApps.setTextColor(v==bApps?Color.BLACK:Color.WHITE);
        bPool.setBackground(getRounded(v==bPool?"#00E5FF":"#222222", 15f)); bPool.setTextColor(v==bPool?Color.BLACK:Color.WHITE);
        bCustom.setBackground(getRounded(v==bCustom?"#00E5FF":"#222222", 15f)); bCustom.setTextColor(v==bCustom?Color.BLACK:Color.WHITE);
        appsPage.setVisibility(v==bApps?View.VISIBLE:View.GONE);
        poolScroll.setVisibility(v==bPool?View.VISIBLE:View.GONE);
        customScroll.setVisibility(v==bCustom?View.VISIBLE:View.GONE);
    };
    bApps.setOnClickListener(tabClick); bPool.setOnClickListener(tabClick); bCustom.setOnClickListener(tabClick);
    bApps.performClick();

    LinearLayout footer = new LinearLayout(this);
    footer.setOrientation(LinearLayout.HORIZONTAL); footer.setPadding(0,20,0,0);
    Button bCancel = new Button(this); bCancel.setText(T("CANCEL","HỦY"));
    bCancel.setBackground(getRounded("#333333",20f)); bCancel.setTextColor(Color.WHITE);
    bCancel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
    Button bSave = new Button(this); bSave.setText(T("SAVE","LƯU"));
    bSave.setBackground(getRounded("#4CAF50",20f)); bSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,-2,1f); slp.setMargins(20,0,0,0);
    bSave.setLayoutParams(slp);
    footer.addView(bCancel); footer.addView(bSave); root.addView(footer);

    bCancel.setOnClickListener(v -> d.dismiss());
    bSave.setOnClickListener(v -> {
        prefs.edit().putString(prefKey, TextUtils.join(",", selectedOrder)).apply();
        if (onSaved != null) onSaved.run();
        d.dismiss();
    });
    d.setContentView(root); d.show();
}

/** Ô icon vuông trong lưới chọn nhiều: có border xanh + dấu ✓ khi đang được chọn.
 *  iconFactory tạo ImageView chứa icon thật (app icon hoặc icon pool), cache-free
 *  vì mỗi Dialog chỉ tồn tại trong lúc mở, GC thu hồi ngay khi đóng. */
private FrameLayout buildIconGridCell(String ref, java.util.function.Supplier<ImageView> iconFactory,
        java.util.List<String> selectedOrder, Runnable onToggle) {
    FrameLayout cell = new FrameLayout(this);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 140, 1f);
    lp.setMargins(6,6,6,6);
    cell.setLayoutParams(lp);
    boolean sel = selectedOrder.contains(ref);
    cell.setBackground(getRounded(sel ? "#00E5FF" : "#202124", 16f));

    ImageView iv = iconFactory.get();
    FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(-1, -1);
    ivLp.setMargins(20,20,20,20);
    iv.setLayoutParams(ivLp);
    cell.addView(iv);

    if (sel) {
        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextColor(Color.BLACK);
        check.setTextSize(14f);
        check.setBackground(getRounded("#00E5FF", 100f));
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(40, 40);
        cLp.gravity = Gravity.TOP | Gravity.END;
        check.setGravity(Gravity.CENTER);
        check.setLayoutParams(cLp);
        cell.addView(check);
    }

    cell.setOnClickListener(v -> {
        if (selectedOrder.contains(ref)) selectedOrder.remove(ref); else selectedOrder.add(ref);
        onToggle.run();
        // Vẽ lại tại chỗ thay vì render lại toàn bộ Dialog — rẻ, tức thì
        cell.setBackground(getRounded(selectedOrder.contains(ref) ? "#00E5FF" : "#202124", 16f));
        if (selectedOrder.contains(ref) && cell.getChildCount() < 2) {
            TextView check = new TextView(this);
            check.setText("✓"); check.setTextColor(Color.BLACK); check.setTextSize(14f);
            check.setBackground(getRounded("#00E5FF", 100f));
            FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(40, 40);
            cLp.gravity = Gravity.TOP | Gravity.END;
            check.setGravity(Gravity.CENTER);
            check.setLayoutParams(cLp);
            cell.addView(check);
        } else if (!selectedOrder.contains(ref) && cell.getChildCount() > 1) {
            cell.removeViewAt(1);
        }
    });
    return cell;
}
 // [MỚI] Picker icon riêng cho 1 Action trong Panel — 2 tab: Apps đã cài / Bộ icon nội bộ.
    private void showIconPickerForPanelAction(String panelId, String actionKey, Runnable onSaved) {
        showIconPickerDialog("pack_panel_" + panelId + "_icon_override_" + actionKey, onSaved);
    }
    private void showIconPickerDialog(String prefKey, Runnable onSaved) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(30, 80, 30, 30);

        TextView title = new TextView(this);
        title.setText(T("Choose icon for this action", "Chọn icon cho hành động này"));
        title.setTextColor(Color.parseColor("#00E5FF")); title.setTextSize(18); title.setPadding(0,0,0,20);
        root.addView(title);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button bApps = createTabBtn(T("APPS", "APP ĐÃ CÀI"));
        Button bPool = createTabBtn(T("SYSTEM (20)", "HỆ THỐNG (20)"));
        Button bCustom = createTabBtn(T("CUSTOM (100)", "TÙY CHỈNH (100)"));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.setMargins(0,0,10,0);
        bApps.setLayoutParams(tlp);
        bPool.setLayoutParams(tlp);
        bCustom.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        tabs.addView(bApps); tabs.addView(bPool); tabs.addView(bCustom);
        root.addView(tabs);
        FrameLayout body = new FrameLayout(this);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(body);

        LinearLayout appsPage = new LinearLayout(this);
        appsPage.setOrientation(LinearLayout.VERTICAL);
        EditText etSearch = new EditText(this);
        etSearch.setHint("🔍 " + T("Search...", "Tìm kiếm..."));
        etSearch.setHintTextColor(Color.GRAY); etSearch.setTextColor(Color.WHITE);
        etSearch.setBackground(getRounded("#2C2C2C", 20f)); etSearch.setPadding(30,25,30,25);
        appsPage.addView(etSearch);
        ScrollView appsGridScroll = new ScrollView(this);
        appsGridScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout appsGrid = new LinearLayout(this);
        appsGrid.setOrientation(LinearLayout.VERTICAL);
        appsGrid.setPadding(0, 10, 0, 10);
        appsGridScroll.addView(appsGrid);
        appsPage.addView(appsGridScroll);
        List<String[]> allApps = getAppListCached();
        java.util.List<String> dummySelIcon = new java.util.ArrayList<>();
        Runnable[] refreshAppsGrid = new Runnable[1];
        refreshAppsGrid[0] = () -> {
            appsGrid.removeAllViews();
            String q = etSearch.getText().toString().trim().toLowerCase();
            LinearLayout row = null; int count = 0;
            for (String[] app : allApps) {
                if (!q.isEmpty() && !app[0].toLowerCase().contains(q)) continue;
                if (count % 5 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); appsGrid.addView(row); }
                String ref = "app:" + app[1];
                FrameLayout cell = buildIconGridCell(ref, () -> { ImageView iv = new ImageView(this); loadAppIconInto(app[1], iv); return iv; },
                    dummySelIcon, () -> {});
                cell.setOnClickListener(v -> {
                    prefs.edit().putString(prefKey, ref).apply();
                    if (onSaved != null) onSaved.run();
                    d.dismiss();
                });
                row.addView(cell);
                count++;
            }
        };
        refreshAppsGrid[0].run();
        etSearch.addTextChangedListener(new android.text.TextWatcher(){
            public void afterTextChanged(android.text.Editable s){ refreshAppsGrid[0].run(); }
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
        });
        ScrollView poolScroll = buildIconGridPage(PanelEngine.SYSTEM_ICON_POOL, "pool:", prefKey, d, onSaved);
        ScrollView customScroll = buildIconGridPage(PanelEngine.getCustomIconPool(this), "poolc:", prefKey, d, onSaved);
        body.addView(appsPage);
        body.addView(poolScroll);
        body.addView(customScroll);
        poolScroll.setVisibility(View.GONE);
        customScroll.setVisibility(View.GONE);

        View.OnClickListener tabClick = v -> {
            bApps.setBackground(getRounded(v==bApps?"#00E5FF":"#222222", 15f));
            bApps.setTextColor(v==bApps?Color.BLACK:Color.WHITE);
            bPool.setBackground(getRounded(v==bPool?"#00E5FF":"#222222", 15f));
            bPool.setTextColor(v==bPool?Color.BLACK:Color.WHITE);
            bCustom.setBackground(getRounded(v==bCustom?"#00E5FF":"#222222", 15f));
            bCustom.setTextColor(v==bCustom?Color.BLACK:Color.WHITE);
            appsPage.setVisibility(v==bApps?View.VISIBLE:View.GONE);
            poolScroll.setVisibility(v==bPool?View.VISIBLE:View.GONE);
            customScroll.setVisibility(v==bCustom?View.VISIBLE:View.GONE);
        };
        bApps.setOnClickListener(tabClick); bPool.setOnClickListener(tabClick); bCustom.setOnClickListener(tabClick);
        bApps.performClick();
        Button bReset = new Button(this);
        bReset.setText(T("Reset to default icon", "Về icon mặc định"));
        bReset.setBackground(getRounded("#333333", 20f)); bReset.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
        rLp.setMargins(0, 20, 0, 0);
        bReset.setLayoutParams(rLp);
        bReset.setOnClickListener(v -> {
            prefs.edit().remove(prefKey).apply();
            if (onSaved != null) onSaved.run();
            d.dismiss();
        });
        root.addView(bReset);

        d.setContentView(root); d.show();
    }
    // Bật/tắt hẳn Component Tile{N} khỏi Android — không phải chỉ ẩn bằng pref runtime.
// DISABLED thì: (1) tự gỡ khỏi QS nếu đang ghim, (2) biến mất khỏi màn "+",
// (3) Android KHÔNG bind Service đó nữa → tiết kiệm RAM/pin thật sự trên Pixel 2XL.
private void setTileComponentEnabled(int slotNum, boolean enable) {
    if (slotNum < 1 || slotNum > 30) return;
    try {
        ComponentName cn = new ComponentName(this, "com.manhmoc.edgebar.Tile" + slotNum);
        getPackageManager().setComponentEnabledSetting(
            cn,
            enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                   : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        );
    } catch (Exception ignored) {}
}

// Gọi 1 lần lúc mở app — đồng bộ lại đúng trạng thái bật/tắt của cả 30 slot
// (phòng trường hợp restore backup, hoặc lần đầu cài app).
private void syncAllTileComponentsOnBoot() {
    for (int s = 1; s <= 30; s++) {
        String id = prefs.getString("tile_slot_" + s + "_id", "");
        boolean shouldEnable = !id.isEmpty() && prefs.getBoolean("tile_active_" + id, false);
        setTileComponentEnabled(s, shouldEnable);
    }
}
// CODE MỚI — thay toàn bộ hàm bằng:
private void syncPanelService() {
    sendBroadcast(new Intent("com.manhmoc.edgebar.PANEL_CONFIG_CHANGED"));
}
// [MỚI] Cache icon app dạng LRU giới hạn 150 icon — đủ cho mọi danh sách hiển thị,
// tự động giải phóng icon ít dùng nhất khi vượt ngưỡng. Load NỀN (background thread),
// gán vào ImageView qua tag-check để tránh gán nhầm ảnh khi ListView tái sử dụng row.
private static final int APP_ICON_CACHE_LIMIT = 150;
private static final java.util.LinkedHashMap<String, android.graphics.drawable.Drawable> appIconCache =
    new java.util.LinkedHashMap<String, android.graphics.drawable.Drawable>(32, 0.75f, true) {
        protected boolean removeEldestEntry(java.util.Map.Entry<String, android.graphics.drawable.Drawable> e) {
            return size() > APP_ICON_CACHE_LIMIT;
        }
    };
private void loadAppIconInto(String ref, ImageView iv) {
    iv.setTag(ref);
    android.graphics.drawable.Drawable cached;
    synchronized (appIconCache) { cached = appIconCache.get(ref); }
    if (cached != null) { iv.setImageDrawable(cached); return; }
    iv.setImageDrawable(null);
    new Thread(() -> {
        android.graphics.drawable.Drawable d = resolveAppRefIconSync(ref);
        if (d == null) return;
        synchronized (appIconCache) { appIconCache.put(ref, d); }
        runOnUiThread(() -> { if (ref.equals(iv.getTag())) iv.setImageDrawable(d); });
    }).start();
}

// [MỚI] App Island không nằm trong PackageManager của profile chính -> phải tra
// qua LauncherApps với đúng UserHandle, nếu không getApplicationIcon() luôn ném
// NameNotFoundException (đây chính là lý do icon Island trước đây không lên được).
private android.graphics.drawable.Drawable resolveAppRefIconSync(String ref) {
    try {
        if (isIslandRef(ref)) {
            String pkg = islandRefPkg(ref);
            long serial = islandRefSerial(ref);
            android.os.UserManager um = (android.os.UserManager) getSystemService(Context.USER_SERVICE);
            android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            android.os.UserHandle target = um.getUserForSerialNumber(serial);
            if (target == null) return null;
            java.util.List<android.content.pm.LauncherActivityInfo> acts = la.getActivityList(pkg, target);
            return (acts != null && !acts.isEmpty()) ? acts.get(0).getBadgedIcon(0) : null;
        }
        return getPackageManager().getApplicationIcon(ref);
    } catch (Exception e) { return null; }
}
    // ==================== CÁC HÀM PHỤ TRỢ CHUNG ====================
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
    ScrollView gridScroll = new ScrollView(this);
    gridScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
    LinearLayout gridBox = new LinearLayout(this);
    gridBox.setOrientation(LinearLayout.VERTICAL);
    gridBox.setPadding(0, 10, 0, 10);
    gridScroll.addView(gridBox);
    root.addView(gridScroll);

    final List<String[]> shown = new ArrayList<>(combined);
    Runnable[] refreshGridHolder = new Runnable[1];
    java.util.List<String> dummySel = new java.util.ArrayList<>(); // single-select: không giữ trạng thái
    refreshGridHolder[0] = () -> {
        gridBox.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            String[] app = shown.get(i);
            if (i % 5 == 0) { row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); gridBox.addView(row); }
            String ref = "app:" + app[1];
            FrameLayout cell = buildIconGridCell(ref, () -> { ImageView iv = new ImageView(this); loadAppIconInto(app[1], iv); return iv; },
                dummySel, () -> {});
            cell.setOnClickListener(v -> { onPicked.accept(app[1]); d.dismiss(); });
            row.addView(cell);
        }
    };
    refreshGridHolder[0].run();

    etSearch.addTextChangedListener(new android.text.TextWatcher(){
        public void afterTextChanged(android.text.Editable s){
            String q = s.toString().trim().toLowerCase();
            shown.clear();
            for (String[] it : combined) if (q.isEmpty() || it[0].toLowerCase().contains(q)) shown.add(it);
            refreshGridHolder[0].run();
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
// Xóa vĩnh viễn 1 Shortcut khỏi mọi nơi: kho lưu, danh sách Panel/Rule, file icon.
// 1 Editor duy nhất -> apply() 1 lần, đúng chuẩn tối ưu Pixel 2XL của toàn bộ codebase.
private void deleteShortcutGlobally(String id) {
    String iconPath = prefs.getString("shortcut_" + id + "_icon_path", "");
    if (!iconPath.isEmpty()) ShortcutScanner.deleteIconFile(iconPath);
    SharedPreferences.Editor ed = prefs.edit();
    ed.remove("shortcut_" + id + "_name");
    ed.remove("shortcut_" + id + "_intent_uri");
    ed.remove("shortcut_" + id + "_icon_path");
    List<String> panelIds = getDynamicIds("panel_shortcut_ids");
    panelIds.remove(id);
    ed.putString("panel_shortcut_ids", TextUtils.join(",", panelIds));
    List<String> normalIds = getDynamicIds("shortcut_ids");
    normalIds.remove(id);
    ed.putString("shortcut_ids", TextUtils.join(",", normalIds));
    ed.apply();
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
    private void showYTDLDialog() {
    Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.parseColor("#121212"));
    root.setPadding(40, 80, 40, 40);

    TextView title = new TextView(this);
    title.setText("🎵 YTDLnis - TẢI NHẠC/VIDEO");
    title.setTextColor(Color.parseColor("#FFD700"));
    title.setPadding(0, 0, 0, 20);
    root.addView(title);

    EditText etLink = new EditText(this);
    etLink.setHint("Paste link / tên bài hát");
    etLink.setText(prefs.getString("ytdl_last_link", ""));
    etLink.setBackground(getRounded("#2C2C2C", 20f));
    etLink.setPadding(30, 30, 30, 30);
    etLink.setTextColor(Color.WHITE);
    root.addView(etLink);

    LinearLayout btnRow = new LinearLayout(this);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setPadding(0, 20, 0, 0);
    Button btnSave = new Button(this); btnSave.setText("💾 LƯU LINK");
    btnSave.setBackground(getRounded("#4CAF50", 20f)); btnSave.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, -2, 1f);
    lpSave.setMargins(0, 0, 20, 0); btnSave.setLayoutParams(lpSave);
    btnSave.setOnClickListener(v -> {
        prefs.edit().putString("ytdl_last_link", etLink.getText().toString()).apply();
        Toast.makeText(this, T("Link saved!", "Đã lưu link!"), Toast.LENGTH_SHORT).show();
    });

    Button btnDownload = new Button(this); btnDownload.setText("📥 TẢI NGAY");
    btnDownload.setBackground(getRounded("#00E5FF", 20f)); btnDownload.setTextColor(Color.BLACK);
    btnDownload.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
    btnDownload.setOnClickListener(v -> {
        prefs.edit().putString("ytdl_last_link", etLink.getText().toString()).apply();
        sendBroadcast(new Intent("com.manhmoc.edgebar.IPC_ACTION").putExtra("act", "YTDL_DOWNLOAD"));
        d.dismiss();
    });
    btnRow.addView(btnSave); btnRow.addView(btnDownload);
    root.addView(btnRow);

    Button bClose = new Button(this); bClose.setText(T("CLOSE", "ĐÓNG"));
    bClose.setBackground(getRounded("#333333", 20f)); bClose.setTextColor(Color.WHITE);
    LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, -2);
    closeLp.setMargins(0, 20, 0, 0); bClose.setLayoutParams(closeLp);
    bClose.setOnClickListener(v -> d.dismiss());
    root.addView(bClose);

    d.setContentView(root); d.show();
}
private static final int REQ_PICK_SONGS = 105;

private void pickSongsForMyPlaylist() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("audio/*");
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    try { startActivityForResult(Intent.createChooser(i, T("Choose songs","Chọn bài hát")), REQ_PICK_SONGS); }
    catch (Exception e) { Toast.makeText(this, T("No file picker found","Không tìm thấy app chọn file"), Toast.LENGTH_SHORT).show(); }
}
private String queryDisplayName(Uri uri) {
    try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
        if (c != null && c.moveToFirst()) {
            int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (idx >= 0) return c.getString(idx);
        }
    } catch (Exception ignored) {}
    return "Song";
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
        new String[]{"Circle", "Squircle", "Pebble", "Rough", "Pentacle"}, 0));
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
    private void showPremiumDialog() { 
        String t = T("ADB COMMANDS:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow", 
        "🔧 LỆNH ADB CỐT LÕI (Cấp 1 lần trọn đời):\n\n1. Quyền ghi Cài đặt bảo mật:\nadb shell pm grant com.manhmoc.edgebar android.permission.WRITE_SECURE_SETTINGS\n\n2. Quyền vẽ Lớp phủ (Tàng hình AppOps):\nadb shell appops set com.manhmoc.edgebar SYSTEM_ALERT_WINDOW allow\n\n🚀 TĂNG TỐC BẰNG ADB (chạy 1 lần):\nadb shell settings put global window_animation_scale 0\nadb shell settings put global transition_animation_scale 0\nadb shell settings put global animator_duration_scale 0"); 
        ScrollView sv = new ScrollView(this); sv.setPadding(50,50,50,50); TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setTextSize(15f); tv.setLineSpacing(0, 1.3f); sv.addView(tv); 
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("👑 PREMIUM ARCHITECT INFO").setView(sv).setPositiveButton("OK", null).show(); 
    }
// ==================== TÌM KIẾM (thay thế nút bánh răng ở nav bar) ====================
    // [TỐI ƯU PIXEL 2XL] Index tra cứu KHÔNG cần internet, KHÔNG quét runtime — chỉ là
    // 1 List<Object[]>{nhãn, từ khoá, Runnable điều hướng} dựng lại mỗi lần MỞ dialog
    // (rẻ: toàn tham chiếu object có sẵn, không cấp phát nặng). Dialog + adapter bị GC
    // thu hồi hoàn toàn ngay khi đóng — Zero RAM dư thừa so với nav bar cố định cũ.

private List<Object[]> buildSearchIndex() {
    if (searchIndexCache != null) return searchIndexCache;
    List<Object[]> index = new ArrayList<>();
    index.add(new Object[]{T("Frontier - Lock", "Frontier - Khoá màn hình"), "frontier lock khoa man hinh bar corner", (Runnable) () -> {
        openSpace(1); currentGesTab = 5; frontierSubTab = 0; renderRulesList();
    }});
    index.add(new Object[]{T("Frontier - Homeb", "Frontier - Homeb (không Trợ năng)"), "frontier homeb", (Runnable) () -> {
        openSpace(1); currentGesTab = 5; frontierSubTab = 1; renderRulesList();
    }});
    index.add(new Object[]{T("Frontier - Homacc", "Frontier - Homacc (có Trợ năng)"), "frontier homacc", (Runnable) () -> {
        openSpace(1); currentGesTab = 5; frontierSubTab = 2; renderRulesList();
    }});
    index.add(new Object[]{T("Texture (Vân tay)", "Texture - Vân tay"), "texture van tay fingerprint", (Runnable) () -> {
        openSpace(1); currentGesTab = 4; renderRulesList();
    }});
    index.add(new Object[]{T("VolKey (Phím âm lượng)", "VolKey - Phím âm lượng"), "volkey phim am luong volume", (Runnable) () -> {
        openSpace(1); currentGesTab = 3; renderRulesList();
    }});
    index.add(new Object[]{T("Display › Anima","Hiển thị › Anima"), "anima hieu ung animation record vien border icon 13 cu chi", (Runnable) this::openDesignSpace});
    index.add(new Object[]{T("Lenap (Bảng nút nổi)", "Lenap - Bảng nút nổi"), "lenap panel bang nut noi", (Runnable) () -> {
        openDesignSpace(); if (btnEditPanel != null) btnEditPanel.performClick();
    }});
    index.add(new Object[]{"Intents", "intent hanh dong tuy chinh", (Runnable) () -> openEco(0, true)});
    index.add(new Object[]{"QS Tiles", "qs tile o vuong cai dat nhanh", (Runnable) () -> openEco(1, true)});
    index.add(new Object[]{"Macros", "macro chuoi hanh dong", (Runnable) () -> openEco(2, true)});
    index.add(new Object[]{T("Storage (Bộ nhớ)", "Bộ nhớ - Storage"), "bo nho storage dung luong", (Runnable) () -> openEco(3, false)});
    index.add(new Object[]{T("Voice Recording (Ghi âm)", "Ghi âm"), "ghi am voice recording", (Runnable) () -> openEco(4, false)});
    index.add(new Object[]{T("Hệ thống (Blacklist/Locklist)", "Bảo mật - Blacklist/Locklist"), "blacklist locklist bao mat he thong", (Runnable) () -> openEco(5, false)});
    index.add(new Object[]{T("Kho Cũ (Trash)", "Kho Cũ"), "kho cu trash thung rac", (Runnable) () -> openEco(6, false)});
    index.add(new Object[]{"Scan QR", "quet ma qr scan", (Runnable) () -> {
        Intent qr = new Intent(this, QrScanActivity.class);
        qr.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(qr);
    }});
    index.add(new Object[]{T("Backup", "Sao lưu cấu hình"), "backup sao luu", (Runnable) () -> {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup_" + System.currentTimeMillis() + ".json");
        startActivityForResult(i, 101);
    }});
    index.add(new Object[]{T("Restore", "Khôi phục cấu hình"), "restore khoi phuc", (Runnable) () -> {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        startActivityForResult(i, 102);
    }});
    index.add(new Object[]{T("Language / Ngôn ngữ", "Đổi ngôn ngữ"), "language ngon ngu tieng viet english", (Runnable) () -> {
        prefs.edit().putBoolean("lang_vi", !isVi).apply(); recreate();
    }});
    index.add(new Object[]{T("Uninstall Safely", "Gỡ cài đặt an toàn"), "uninstall go cai dat", (Runnable) this::confirmThenUninstallApp});
    searchIndexCache = index;
    return index;
}

// Hàm phụ đo khoảng cách chuỗi
private int levenshtein(String a, String b) {
    int[][] dp = new int[a.length()+1][b.length()+1];
    for (int i=0;i<=a.length();i++) dp[i][0]=i;
    for (int j=0;j<=b.length();j++) dp[0][j]=j;
    for (int i=1;i<=a.length();i++)
        for (int j=1;j<=b.length();j++)
            dp[i][j] = Math.min(Math.min(dp[i-1][j]+1, dp[i][j-1]+1),
                dp[i-1][j-1] + (a.charAt(i-1)==b.charAt(j-1) ? 0 : 1));
    return dp[a.length()][b.length()];
}

private void liveSearchSettings(String query) {
    List<Object[]> all = buildSearchIndex();
    String q = query.trim().toLowerCase();
    if (q.isEmpty()) { if (searchPopup != null && searchPopup.isShowing()) searchPopup.dismiss(); return; }

    // Tách query thành nhiều token, yêu cầu MỌI token đều khớp (AND) — tránh
    // match ngẫu nhiên như trước (chỉ cần 1 ký tự trùng là ra kết quả).
    String[] tokens = q.split("\\s+");
    List<Object[]> exact = new ArrayList<>();   // label bắt đầu bằng query
    List<Object[]> contains = new ArrayList<>(); // mọi token đều xuất hiện trong label+keywords
    for (Object[] item : all) {
        String label = ((String) item[0]).toLowerCase();
        String keys = (String) item[1];
        String haystack = label + " " + keys;
        boolean allTokensMatch = true;
        for (String t : tokens) { if (!haystack.contains(t)) { allTokensMatch = false; break; } }
        if (!allTokensMatch) continue;
        if (label.startsWith(q)) exact.add(item); else contains.add(item);
    }
    List<Object[]> matched = new ArrayList<>();
    matched.addAll(exact);
    matched.addAll(contains);

    // Fuzzy chỉ dùng khi AND-match rỗng, và CHỈ nhận nếu độ lệch đủ nhỏ so với
    // độ dài query (tránh trả bừa kết quả không liên quan như "hhvhjnb").
    if (matched.isEmpty() && q.length() >= 3) {
        Object[] best = null; int bestDist = Integer.MAX_VALUE;
        for (Object[] item : all) {
            int d = levenshtein(q, ((String) item[0]).toLowerCase());
            if (d < bestDist) { bestDist = d; best = item; }
        }
        int maxAllowedDist = Math.max(1, q.length() / 2); // lệch tối đa 50% độ dài query
        if (best != null && bestDist <= maxAllowedDist) matched.add(best);
    }
    if (matched.size() > 8) matched = matched.subList(0, 8); // giới hạn 8 kết quả, đỡ tốn layout/RAM

    if (matched.isEmpty()) { if (searchPopup != null && searchPopup.isShowing()) searchPopup.dismiss(); return; }

    if (searchPopup == null) {
        searchPopup = new android.widget.ListPopupWindow(this);
        searchPopup.setAnchorView(etNavSearch);
        searchPopup.setModal(false);
        searchPopup.setInputMethodMode(android.widget.ListPopupWindow.INPUT_METHOD_NEEDED);
        searchPopup.setBackgroundDrawable(getRounded("#1E1E1E", 24f));
    }
    final List<Object[]> finalMatched = matched;
    BaseAdapter adapter = new BaseAdapter() {
        @Override public int getCount() { return finalMatched.size(); }
        @Override public Object getItem(int p) { return finalMatched.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(30, 22, 30, 22);
            TextView tv = new TextView(MainActivity.this);
            tv.setText((String) finalMatched.get(p)[0]); // đã là breadcrumb "A › B › C" — xem mục buildSearchIndex bên dưới
            tv.setTextColor(Color.WHITE); tv.setTextSize(14.5f);
            row.addView(tv);
            return row;
        }
    };
    searchPopup.setAdapter(adapter);
    searchPopup.setWidth(etNavSearch.getWidth() > 0 ? etNavSearch.getWidth() : 600);
    searchPopup.setOnItemClickListener((parent, v, position, id) -> {
        Runnable action = (Runnable) finalMatched.get(position)[2];
        searchPopup.dismiss();
        etNavSearch.setText("");
        new Handler(android.os.Looper.getMainLooper()).postDelayed(action, 300);
    });
    searchPopup.show();
}
private void confirmThenUninstallApp() {
    if (Build.VERSION.SDK_INT >= 30) {
        android.hardware.biometrics.BiometricPrompt prompt =
            new android.hardware.biometrics.BiometricPrompt.Builder(this)
                .setTitle(T("Confirm to uninstall EdgeBar", "Xác nhận gỡ cài đặt EdgeBar"))
                .setAllowedAuthenticators(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(new android.os.CancellationSignal(), getMainExecutor(),
            new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) {
                    doRevokeAdminAndUninstall();
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    Toast.makeText(MainActivity.this, T("Cancelled", "Đã huỷ"), Toast.LENGTH_SHORT).show();
                }
            });
    } else {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent i = km.createConfirmDeviceCredentialIntent(T("Confirm", "Xác nhận"), null);
        if (i == null) { doRevokeAdminAndUninstall(); return; }
        startActivityForResult(i, REQ_UNINSTALL_CONFIRM);
    }
}
private void revokeDeviceAdminIfActive() {
    try {
        android.app.admin.DevicePolicyManager dpmU =
            (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminU = new android.content.ComponentName(this, HomebDeviceAdminReceiver.class);
        if (dpmU.isAdminActive(adminU)) dpmU.removeActiveAdmin(adminU);
    } catch (Exception ignored) {}
}
private void doRevokeAdminAndUninstall() {
    try {
        android.app.admin.DevicePolicyManager dpmU =
            (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminU = new android.content.ComponentName(this, HomebDeviceAdminReceiver.class);
        if (dpmU.isAdminActive(adminU)) dpmU.removeActiveAdmin(adminU);
    } catch (Exception ignored) {}
    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
    uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(uninstallIntent);
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

// [THIẾT KẾ THỐNG NHẤT] 1 màu nhấn duy nhất cho toàn bộ tab trong app — thay vì
// mỗi nơi tự chọn màu riêng (#00E5FF/#FFC107/#E91E63/#4CAF50 lẫn lộn). Dùng lại
// đúng createTabBtn() đã có, chỉ chuẩn hoá cách tô màu active/inactive.
private static final String ACCENT_COLOR = "#8AB4F8";      // Google Blue - accent chính
private static final String SURFACE_COLOR = "#202124";     // Nền card/nút
private static final String TEXT_MUTED_COLOR = "#9AA0A6";  // Text phụ/icon mờ
private void styleTabActive(Button b, boolean active) {
    b.setBackground(getRounded(active ? ACCENT_COLOR : "#222222", 20f));
    b.setTextColor(active ? Color.BLACK : Color.parseColor("#9AA0A6"));
}
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
private int customIconRes(String name) {
    int id = getResources().getIdentifier(name, "drawable", getPackageName());
    return id;
}
private ImageView makeMenuIcon(String iconName, int sizePx) {
    ImageView iv = new ImageView(this);
    int resId = customIconRes(iconName);
    if (resId != 0) { iv.setImageResource(resId); iv.setColorFilter(Color.parseColor(ACCENT_COLOR)); }
    iv.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
    return iv;
}
    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    private String formatPruleGestureLabel(String rId) {
    String csv = prefs.getString("prule_" + rId + "_gestures", "");
    if (csv.isEmpty()) return T("Tap","Chạm");
    StringBuilder sb = new StringBuilder();
    for (String g : csv.split(",")) {
        String gt = g.trim();
        for (int i = 0; i < C_GESTURES.length; i++) {
            if (C_GESTURES[i].equals(gt)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(C_GESTURE_NAMES[i]);
                break;
            }
        }
    }
    return sb.length() == 0 ? T("Tap","Chạm") : sb.toString();
}
private String formatPruleActionLabel(String rId) {
    String acts = prefs.getString("prule_" + rId + "_acts", "");
    if (acts.isEmpty()) return T("None","Không có");
    StringBuilder sb = new StringBuilder();
    for (String a : acts.split(",")) {
        String at = a.trim();
        if (at.isEmpty()) continue;
        if (at.equals("LAUNCH_APP")) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(getAppLabelCached(prefs.getString("prule_" + rId + "_launch_pkg", "")));
            continue;
        }
        if (at.equals("RUN_SHORTCUT")) {
            if (sb.length() > 0) sb.append(" + ");
            String scId = prefs.getString("prule_" + rId + "_shortcut_id", "");
            sb.append("🔗 " + prefs.getString("shortcut_" + scId + "_name", "Shortcut"));
            continue;
        }
        // [FIX] Panel/Intent/Macro dùng UUID động — không còn nằm trong ACT_KEYS tĩnh
        // nữa nên phải tra tên trực tiếp từ kho pack_panel_*/intent_*/macro_*.
        if (at.startsWith("PANEL_")) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("📦 " + prefs.getString("pack_panel_" + at.substring(6) + "_name", "Panel"));
            continue;
        }
        if (at.startsWith("INTENT_")) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("⚡ " + prefs.getString("intent_" + at.substring(7) + "_name", "Intent"));
            continue;
        }
        if (at.startsWith("MACRO_")) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("🤖 " + prefs.getString("macro_" + at.substring(6) + "_name", "Macro"));
            continue;
        }
        for (int i = 0; i < ACT_KEYS.length; i++) {
            if (ACT_KEYS[i] != null && ACT_KEYS[i].equals(at)) {
                if (sb.length() > 0) sb.append(" + ");
                sb.append(ACT_LABS[i]);
                break;
            }
        }
    }
    return sb.length() == 0 ? T("Error","Lỗi") : sb.toString();
}
    private LinearLayout createSlider(String t, String k, int max, int def) { 
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); 
        TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); 
        Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20); 
        Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20); 
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); 
tv.setOnClickListener(v2 -> {
    EditText et = new EditText(this);
    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    et.setText(String.valueOf(sb.getProgress()));
    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(t)
        .setView(et)
        .setPositiveButton("OK", (d, w) -> {
            try {
                int p = Math.max(0, Math.min(max, Integer.parseInt(et.getText().toString().trim())));
                sb.setProgress(p);
                tv.setText(t + ": " + p);
                Runnable pendingOld = sliderPendingRunnable.remove(k);
                if (pendingOld != null) sliderPrefHandler.removeCallbacks(pendingOld);
                prefs.edit().putInt(k, p).apply();
                sliderLastWriteMs.put(k, System.currentTimeMillis());
            } catch (Exception ignored) {}
        }).setNegativeButton("HỦY", null).show();
});
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s, int p, boolean fromUser){
                tv.setText(t + ": " + p);
                if (!fromUser) return; // bỏ qua sự kiện lập trình gọi setProgress() (VD: nút +/-), chỉ throttle thao tác kéo tay thật
                long now = System.currentTimeMillis();
                Long last = sliderLastWriteMs.get(k);
                Runnable pendingOld = sliderPendingRunnable.get(k);
                if (pendingOld != null) sliderPrefHandler.removeCallbacks(pendingOld);
                if (last == null || now - last >= SLIDER_WRITE_THROTTLE_MS) {
                    // Đủ lâu kể từ lần ghi trước -> ghi ngay, giữ cảm giác preview real-time
                    prefs.edit().putInt(k, p).apply();
                    sliderLastWriteMs.put(k, now);
                } else {
                    // Ghi quá gần lần trước -> hoãn tới đúng mốc throttle tiếp theo thay vì ghi ngay
                    long delay = SLIDER_WRITE_THROTTLE_MS - (now - last);
                    Runnable r = () -> {
                        prefs.edit().putInt(k, p).apply();
                        sliderLastWriteMs.put(k, System.currentTimeMillis());
                    };
                    sliderPendingRunnable.put(k, r);
                    sliderPrefHandler.postDelayed(r, delay);
                }
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){
                // [BẮT BUỘC] Nhả tay -> huỷ throttle đang chờ, ghi NGAY giá trị cuối cùng.
                // Đảm bảo tuyệt đối không mất giá trị dù throttle đang giữ 1 write dở dang.
                Runnable pending = sliderPendingRunnable.remove(k);
                if (pending != null) sliderPrefHandler.removeCallbacks(pending);
                int p = s.getProgress();
                prefs.edit().putInt(k, p).apply();
                sliderLastWriteMs.put(k, System.currentTimeMillis());
            }
        }); 
        btnMinus.setOnClickListener(v -> {
    int p = Math.max(0, sb.getProgress() - 1);
    sb.setProgress(p);
    tv.setText(t + ": " + p);
    // Huỷ mọi write đang chờ throttle (nếu có) — tránh nó ghi đè giá trị cũ
    // lên trên giá trị vừa bấm nút, đúng gốc gây ra lỗi "kéo 21, bấm + lên 27
    // nhưng Lưu lại ra 21".
    Runnable pendingOld = sliderPendingRunnable.remove(k);
    if (pendingOld != null) sliderPrefHandler.removeCallbacks(pendingOld);
    prefs.edit().putInt(k, p).apply();
    sliderLastWriteMs.put(k, System.currentTimeMillis());
});
btnPlus.setOnClickListener(v -> {
    int p = Math.min(max, sb.getProgress() + 1);
    sb.setProgress(p);
    tv.setText(t + ": " + p);
    Runnable pendingOld = sliderPendingRunnable.remove(k);
    if (pendingOld != null) sliderPrefHandler.removeCallbacks(pendingOld);
    prefs.edit().putInt(k, p).apply();
    sliderLastWriteMs.put(k, System.currentTimeMillis());
});
        row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); 
        return l; 
    }
    // ============ [FIX] LIVE PREVIEW THẬT CHO BAR/CORNER DATA PACK ============
    // Overlay chỉ tồn tại trong lúc Dialog Data Pack mở -> add/remove đúng
    // theo vòng đời Dialog, không service, không thread nền -> RAM=0 lúc đóng.
    private void removeLivePreviewOverlay() {
        if (livePreviewOverlay != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(livePreviewOverlay); } catch (Exception ignored) {}
            livePreviewOverlay = null;
        }
    }

    private void updateLivePreviewBar(int barIdx, int alpha, int w, int h, int x, int y) {
        if (!Settings.canDrawOverlays(this)) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int[] gravArr = {
                Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.RIGHT, Gravity.BOTTOM|Gravity.LEFT,
                Gravity.TOP|Gravity.RIGHT, Gravity.CENTER_VERTICAL|Gravity.RIGHT, Gravity.BOTTOM|Gravity.RIGHT,
                Gravity.TOP|Gravity.CENTER_HORIZONTAL, Gravity.TOP|Gravity.RIGHT, Gravity.TOP|Gravity.LEFT,
                Gravity.TOP|Gravity.LEFT, Gravity.CENTER_VERTICAL|Gravity.LEFT, Gravity.BOTTOM|Gravity.LEFT
            };
        // FIX: Kẹp barIdx trong giới hạn mảng để chống crash OutOfBounds
        int safeIdx = Math.max(0, Math.min(barIdx, gravArr.length - 1));
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(alpha, 96, 125, 139));
        gd.setCornerRadius(24f);
        if (livePreviewOverlay == null) {
            livePreviewOverlay = new View(this);
            livePreviewLp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
            livePreviewLp.gravity = gravArr[safeIdx];
            livePreviewLp.x = x; livePreviewLp.y = y;
            livePreviewOverlay.setBackground(gd);
            try { wm.addView(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
        } else {
            livePreviewOverlay.setBackground(gd);
            livePreviewLp.width = w; livePreviewLp.height = h;
            livePreviewLp.x = x; livePreviewLp.y = y;
            livePreviewLp.gravity = gravArr[safeIdx];
            try { wm.updateViewLayout(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
        }
    }
    // ============ [FIX #2] LIVE PREVIEW CHÍNH XÁC CHO CORNER ============
    // Copy nguyên thuật toán vẽ "trăng lưỡi liềm" (2 Path) từ CornerView thật
    // trong EdgeBarService/HomescreenService — Zero sai lệch hình dạng.
    private class LiveCornerPreviewView extends View {
        private Paint pFill, pStroke; private int type; private String ck;
        public LiveCornerPreviewView(Context c, int type, String ck) {
            super(c); this.type = type; this.ck = ck;
            pFill = new Paint(); pFill.setStyle(Paint.Style.FILL); pFill.setAntiAlias(true);
            pStroke = new Paint(); pStroke.setColor(Color.WHITE); pStroke.setStyle(Paint.Style.STROKE);
            pStroke.setAntiAlias(true); pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                pStroke.setStrokeWidth(prefs.getInt(ck+"thick", 8));
                pStroke.setColor(Color.argb(prefs.getInt(ck+"stroke_alpha", 200), 255, 255, 255));
                pFill.setColor(Color.argb(prefs.getInt(ck+"moon_alpha", 100), 96, 125, 139));

                float tw = getWidth(), th = getHeight(), thick = pStroke.getStrokeWidth(), pad = thick/2;
                int shapeMode = prefs.getInt(ck+"shape", 0);
                float sRad = prefs.getInt(ck+"rad", 80) / 1000f; float mRad = prefs.getInt(ck+"moon_rad", 80) / 1000f;
                float sw = prefs.getInt(ck+"w", 100), sh = prefs.getInt(ck+"h", 100);
                float mw = prefs.getInt(ck+"moon_w", 100), mh = prefs.getInt(ck+"moon_h", 100);

                android.graphics.Path moonPath = new android.graphics.Path(), strokePath = new android.graphics.Path();
                float sRootX=0, sRootY=0, sTipX=0, sTipY=0, sCtrlX=0, sCtrlY=0;
                float mRootX=0, mRootY=0, mTipX=0, mTipY=0, mCtrlX=0, mCtrlY=0;

                if (type == 0) { // BR
                    sRootX=tw-pad; sRootY=th-pad; sTipX=tw-sw+pad; sTipY=th-sh+pad;
                    sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=th; mTipX=tw-mw; mTipY=th-mh;
                    mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if (type == 1) { // BL
                    sRootX=pad; sRootY=th-pad; sTipX=sw-pad; sTipY=th-sh+pad;
                    sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY-(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=th; mTipX=mw; mTipY=th-mh;
                    mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY-(1f-mRad)*(mh*0.7f);
                } else if (type == 2) { // TR
                    sRootX=tw-pad; sRootY=pad; sTipX=tw-sw+pad; sTipY=sh-pad;
                    sCtrlX=sRootX-(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=tw; mRootY=0; mTipX=tw-mw; mTipY=mh;
                    mCtrlX=mRootX-(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                } else { // TL
                    sRootX=pad; sRootY=pad; sTipX=sw-pad; sTipY=sh-pad;
                    sCtrlX=sRootX+(1f-sRad)*(sw*0.7f); sCtrlY=sRootY+(1f-sRad)*(sh*0.7f);
                    mRootX=0; mRootY=0; mTipX=mw; mTipY=mh;
                    mCtrlX=mRootX+(1f-mRad)*(mw*0.7f); mCtrlY=mRootY+(1f-mRad)*(mh*0.7f);
                }

                if(shapeMode == 1) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sTipX, sRootY); }
                else if(shapeMode == 2) { strokePath.moveTo(sRootX, sRootY); strokePath.lineTo(sRootX, sTipY); }
                else { strokePath.moveTo(sRootX, sTipY); strokePath.quadTo(sCtrlX, sCtrlY, sTipX, sRootY); }

                if(type==0||type==1) { moonPath.moveTo(mRootX, mTipY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mTipX, mRootY); moonPath.quadTo(mCtrlX, mCtrlY, mRootX, mTipY); }
                else { moonPath.moveTo(mTipX, mRootY); moonPath.lineTo(mRootX, mRootY); moonPath.lineTo(mRootX, mTipY); moonPath.quadTo(mCtrlX, mCtrlY, mTipX, mRootY); }
                moonPath.close();

                canvas.drawPath(strokePath, pStroke);
                float mx = prefs.getInt(ck+"moon_x", 1250) - 1250;
                float my = prefs.getInt(ck+"moon_y", 1250) - 1250;
                canvas.save(); canvas.translate(mx, my); canvas.drawPath(moonPath, pFill); canvas.restore();
            }
} // <--- THÊM DẤU NGOẶC NHỌN NÀY ĐỂ ĐÓNG CLASS 
    private void updateLivePreviewCorner(int cornerIdx, String ck) {
        if (!Settings.canDrawOverlays(this)) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int[] gravArr = {
            Gravity.BOTTOM | Gravity.RIGHT, Gravity.BOTTOM | Gravity.LEFT,
            Gravity.TOP | Gravity.RIGHT, Gravity.TOP | Gravity.LEFT
        };
        int wPref = prefs.getInt(ck+"w", 100), hPref = prefs.getInt(ck+"h", 100);
        int mwPref = prefs.getInt(ck+"moon_w", 100), mhPref = prefs.getInt(ck+"moon_h", 100);
        int mxOffset = Math.abs(prefs.getInt(ck+"moon_x", 1250) - 1250);
        int myOffset = Math.abs(prefs.getInt(ck+"moon_y", 1250) - 1250);
        int cw = Math.max(10, Math.max(wPref, mwPref) + mxOffset);
        int ch = Math.max(10, Math.max(hPref, mhPref) + myOffset);
        int x = prefs.getInt(ck+"x", 0), y = prefs.getInt(ck+"y", 0);

        if (livePreviewOverlay == null || !(livePreviewOverlay instanceof LiveCornerPreviewView)) {
            removeLivePreviewOverlay();
            livePreviewOverlay = new LiveCornerPreviewView(this, cornerIdx, ck);
            livePreviewLp = new WindowManager.LayoutParams(cw, ch,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
            livePreviewLp.gravity = gravArr[cornerIdx];
            livePreviewLp.x = x; livePreviewLp.y = y;
            try { wm.addView(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
        } else {
            livePreviewLp.width = cw; livePreviewLp.height = ch;
            livePreviewLp.x = x; livePreviewLp.y = y;
            livePreviewLp.gravity = gravArr[cornerIdx];
            try { wm.updateViewLayout(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
            livePreviewOverlay.invalidate();
        }
    }
private boolean sensorSpaceBuilt = false;
private LinearLayout sensorBodyContainer, sensorBackRowRef;

private void buildSensorSpaceOnce() {
    sensorSpaceBuilt = true;
    LinearLayout subTab = new LinearLayout(this);
    subTab.setOrientation(LinearLayout.VERTICAL);
    subTab.setPadding(0, 0, 0, 10);
    listRules.addView(subTab);

    LinearLayout sensorBackRow = new LinearLayout(this);
    sensorBackRow.setOrientation(LinearLayout.HORIZONTAL);
    sensorBackRow.setGravity(Gravity.CENTER_VERTICAL);
    sensorBackRow.setPadding(0, 0, 0, 20);
    sensorBackRow.setVisibility(View.GONE);
    TextView tvSubTitle = new TextView(this);
    tvSubTitle.setTextColor(Color.parseColor("#00E5FF")); tvSubTitle.setTextSize(16);
    LinearLayout.LayoutParams ftlp = new LinearLayout.LayoutParams(-2, -2); ftlp.setMargins(20, 0, 0, 0);
    tvSubTitle.setLayoutParams(ftlp);
    sensorBackRow.addView(tvSubTitle);
    listRules.addView(sensorBackRow);
    sensorBackRowRef = sensorBackRow;

    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setVisibility(View.GONE);
    listRules.addView(body);
    sensorBodyContainer = body;

    LinearLayout row = createSettingsRow("flare_24px", "Proximity Sensor", "Cảm biến Tiệm cận", () -> {
        subTab.setVisibility(View.GONE);
        gesSubHeader.setVisibility(View.GONE);
        sensorBackRow.setVisibility(View.VISIBLE);
        tvSubTitle.setText("PROXIMITY SENSOR");
        body.setVisibility(View.VISIBLE);
        
        body.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("Không gian lưu Data Pack cho Cảm biến (Giao diện giữ chỗ)");
        tv.setTextColor(Color.GRAY);
        tv.setPadding(0, 40, 0, 0);
        body.addView(tv);

        updateFabVisibility();
        navBackStack.push(() -> {
            body.setVisibility(View.GONE);
            sensorBackRow.setVisibility(View.GONE);
            subTab.setVisibility(View.VISIBLE);
            gesSubHeader.setVisibility(View.VISIBLE);
            updateFabVisibility();
        });
    });
    subTab.addView(row);
}
    // ============ [FIX #1] LIVE PREVIEW THẬT CHO PANEL DATA PACK ============
    // Vẽ 1 khối chữ nhật đúng vị trí/kích thước/màu/bo góc panel thật —
    // KHÔNG load icon/app, KHÔNG spawn Thread nào -> Zero RAM/CPU thêm so với
    // việc chỉ vẽ Bar/Corner đã có sẵn.
    private void updateLivePreviewPanel(String ck) {
        if (!Settings.canDrawOverlays(this)) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int pos = prefs.getInt(ck + "pos", 0);
        String edge = pos <= 2 ? "bottom" : (pos <= 5 ? "left" : "right");
        int[] gravArr = {
            Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM|Gravity.LEFT, Gravity.BOTTOM|Gravity.RIGHT,
            Gravity.LEFT|Gravity.TOP, Gravity.LEFT|Gravity.CENTER_VERTICAL, Gravity.LEFT|Gravity.BOTTOM,
            Gravity.RIGHT|Gravity.TOP, Gravity.RIGHT|Gravity.CENTER_VERTICAL, Gravity.RIGHT|Gravity.BOTTOM
        };
        int gravity = gravArr[Math.max(0, Math.min(8, pos))];

        int iconSize = prefs.getInt(ck + "icon_size", 110);
        int cols = Math.max(1, prefs.getInt(ck + "cols", 4));
        int alpha = prefs.getInt(ck + "alpha", 200);
        int size = prefs.getInt(ck + "size", 700);
        int panelLength = prefs.getInt(ck + "panel_length", 700);
        int panelRadius = prefs.getInt(ck + "panel_radius", 24);
        int colorIdx = prefs.getInt(ck + "color_idx", 0);

        int cellPx = iconSize + 32;
        int mainAxisContent = (edge.equals("bottom") ? cols : 1) * cellPx + 48;
        int mainAxis = Math.max(panelLength, mainAxisContent);
        int cross = Math.max(size, iconSize + 48);
        int w = edge.equals("bottom") ? mainAxis : cross;
        int h = edge.equals("bottom") ? cross : mainAxis;

        String[] hex = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
        int color;
        try { color = Color.parseColor(hex[Math.max(0, Math.min(hex.length-1, colorIdx))]); }
        catch (Exception e) { color = Color.parseColor("#607D8B"); }

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        gd.setStroke(4, Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)));
        gd.setCornerRadius(panelRadius);

        if (livePreviewOverlay == null || livePreviewOverlay instanceof LiveCornerPreviewView) {
            removeLivePreviewOverlay();
            livePreviewOverlay = new View(this);
            livePreviewLp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
            livePreviewLp.gravity = gravity;
            livePreviewOverlay.setBackground(gd);
            try { wm.addView(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
        } else {
            livePreviewOverlay.setBackground(gd);
            livePreviewLp.width = w; livePreviewLp.height = h;
            livePreviewLp.gravity = gravity;
            try { wm.updateViewLayout(livePreviewOverlay, livePreviewLp); } catch (Exception ignored) {}
        }
    }
}
