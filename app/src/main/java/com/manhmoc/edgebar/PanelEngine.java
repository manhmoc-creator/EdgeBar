package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.graphics.*;
import android.graphics.drawable.Drawable; import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable; // [MỚI] để nạp icon Shortcut từ file PNG
import android.os.*; import android.view.*; import android.view.animation.*; import android.widget.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class PanelEngine {
    private Context ctx; private WindowManager wm; private SharedPreferences prefs;
    private boolean isAnyMode; // true = EdgeBarService (Lock+Homacc), false = HomescreenService (Homeb)
    private KeyguardManager km;
    // [THAY] Map theo UUID thay vì mảng cố định 3 phần tử — số Panel không giới hạn,
    // RAM chỉ tốn đúng bằng số Panel THẬT SỰ đang bật (không cấp phát dư slot rỗng).
    private final Map<String, View> handles = new HashMap<>();
    private final Map<String, LinearLayout> panels = new HashMap<>();
    private final Map<String, Boolean> panelOpen = new HashMap<>();
    private final Map<String, String> lastSignature = new HashMap<>();
    private final Map<String, Boolean> forceTestOn = new HashMap<>();
    private final Map<String, Integer> lastIconSizeCache = new HashMap<>();
    private final Map<String, AtomicInteger> renderGen = new HashMap<>();
    private static final int ICON_CACHE_LIMIT = 80;
    private static final LinkedHashMap<String, Drawable> iconCache =
        new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) { return size() > ICON_CACHE_LIMIT; }
        };
    private static final java.util.Map<String,String> labelCache = new java.util.HashMap<>();
    private static final java.util.Map<String,String> ACT_LABEL_MAP = new java.util.HashMap<>();
    static {
        ACT_LABEL_MAP.put("FLASH","Đèn pin"); ACT_LABEL_MAP.put("SCREEN_OFF","Tắt màn hình");
        ACT_LABEL_MAP.put("SCREENSHOT","Chụp màn hình"); ACT_LABEL_MAP.put("CAMERA","Camera");
        ACT_LABEL_MAP.put("VOLUME","Âm lượng"); ACT_LABEL_MAP.put("NOTIFICATIONS","Thông báo");
        ACT_LABEL_MAP.put("BACK","Quay lại"); ACT_LABEL_MAP.put("HOME","Màn chính");
        ACT_LABEL_MAP.put("RECENTS","Đa nhiệm"); ACT_LABEL_MAP.put("VOICE_RECORD","Ghi âm");
        ACT_LABEL_MAP.put("TOGGLE_MORSE","Khóa Morse");
    }
    // [MỚI] Icon hệ thống gần đúng nhất với màn "Chỉnh sửa lối tắt" của Android (ảnh mẫu).
    // Dùng thẳng android.R.drawable có sẵn trong OS — 0 tài nguyên thêm, 0 dung lượng APK.
    private static final java.util.Map<String, Integer> ACT_ICON_RES = new java.util.HashMap<>();
    static {
        ACT_ICON_RES.put("BACK", android.R.drawable.ic_media_rew);
        ACT_ICON_RES.put("HOME", android.R.drawable.ic_menu_compass);
        ACT_ICON_RES.put("RECENTS", android.R.drawable.ic_menu_recent_history);
        ACT_ICON_RES.put("SCREEN_OFF", android.R.drawable.ic_lock_lock);
        ACT_ICON_RES.put("POWER_DIALOG", android.R.drawable.ic_lock_power_off);
        ACT_ICON_RES.put("VOLUME", android.R.drawable.ic_lock_silent_mode_off);
        ACT_ICON_RES.put("SCREENSHOT", android.R.drawable.ic_menu_camera);
        ACT_ICON_RES.put("CAMERA", android.R.drawable.ic_menu_camera);
        ACT_ICON_RES.put("NOTIFICATIONS", android.R.drawable.ic_dialog_email);
        ACT_ICON_RES.put("VOICE_RECORD", android.R.drawable.ic_btn_speak_now);
        ACT_ICON_RES.put("TOGGLE_MORSE", android.R.drawable.ic_lock_idle_lock);
        ACT_ICON_RES.put("TOGGLE_ACC", android.R.drawable.ic_menu_manage);
ACT_ICON_RES.put("TOGGLE_OVERLAY", android.R.drawable.ic_menu_view);
ACT_ICON_RES.put("YTDL_DOWNLOAD", android.R.drawable.ic_menu_upload);
    }
    // [MỚI] Bộ icon nội bộ cho user tự gán vào từng Action.
    // TODO: thay các android.R.drawable.* dưới đây bằng @drawable/ic_custom_XX
    // của 70+ icon riêng khi đã bỏ file VectorDrawable vào res/drawable.
    static final int[] SYSTEM_ICON_POOL = {
        android.R.drawable.ic_menu_compass, android.R.drawable.ic_menu_search,
        android.R.drawable.ic_lock_idle_lock, android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_crop, android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_send, android.R.drawable.ic_media_next,
        android.R.drawable.ic_menu_share, android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_menu_manage, android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_delete, android.R.drawable.ic_menu_add,
        android.R.drawable.ic_menu_close_clear_cancel, android.R.drawable.ic_menu_upload,
        android.R.drawable.ic_menu_view, android.R.drawable.star_on,
        android.R.drawable.ic_menu_mylocation, android.R.drawable.ic_dialog_email
    };
        // [MỚI] 81 icon vector tự đóng gói siêu nhẹ, đã xóa thuộc tính tint
    // Danh sách TÊN file (string) thay vì hằng số R.drawable cứng —
    // tránh lỗi "cannot find symbol" khi thiếu icon trong res/drawable.
    private static final String[] CUSTOM_ICON_NAMES = {
    "accessible_menu_24px", "all_inclusive_24px", "alternate_email_24px",
    "anchor_24px", "android_24px", "android_wifi_3_bar_lock_24px",
    "api_24px", "battery_charging_full_24px", "bomb_24px",
    "bubble_chart_24px", "chess_knight_24px", "circles_ext_24px",
    "construction_24px", "content_cut_24px", "crop_24px",
    "cycle_24px", "dark_mode_24px", "data_usage_24px",
    "deceased_24px", "deployed_code_24px", "directions_boat_24px",
    "directions_run_24px", "distance_24px", "diversity_2_24px",
    "ecg_heart_24px", "eda_24px", "edit_24px",
    "elderly_24px", "emoji_food_beverage_24px", "explore_24px",
    "eyeglasses_2_24px", "fertile_24px", "file_present_24px",
    "fingerprint_24px", "flag_24px", "flare_24px",
    "flash_on_24px", "flashlight_on_24px", "flight_24px",
    "hand_bones_24px", "handyman_24px", "headphones_24px",
    "heart_broken_24px", "help_24px", "key_vertical_24px",
    "keyboard_24px", "keyboard_command_key_24px", "light_mode_24px",
    "lightbulb_24px", "link_2_24px", "local_police_24px",
    "mail_24px", "matter_24px", "menstrual_health_24px",
    "mobile_info_24px", "mobile_lock_portrait_24px", "mobile_question_24px",
    "mode_heat_24px", "mode_off_on_24px", "movie_24px",
    "music_note_24px", "music_note_2_24px", "nest_protect_24px",
    "notifications_active_24px", "pet_supplies_24px", "phone_in_talk_24px",
    "photo_camera_24px", "planet_24px", "play_arrow_24px",
    "qr_code_scanner_24px", "recycling_24px", "rocket_launch_24px",
    "routine_24px", "schedule_24px", "search_24px",
    "security_24px", "settings_24px", "settings_accessibility_24px",
    "settings_bluetooth_24px", "settings_heart_24px", "settings_power_24px",
    "settings_voice_24px", "share_location_24px", "sim_card_24px",
    "snowflake_24px", "sports_martial_arts_24px", "sports_motorsports_24px",
    "sports_soccer_24px", "star_24px", "stat_0_24px",
    "stream_24px", "support_24px", "token_24px",
    "touch_app_24px", "translate_24px", "two_wheeler_24px",
    "visibility_24px", "volume_up_24px", "warning_24px",
    "work_24px"
};
    // Cache resolve ID runtime — chỉ tính 1 lần, không tốn thêm CPU về sau.
    private static int[] customIconPoolCache = null;
    static int[] getCustomIconPool(Context ctx) {
        if (customIconPoolCache != null) return customIconPoolCache;
        List<Integer> ids = new ArrayList<>();
        for (String name : CUSTOM_ICON_NAMES) {
            int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
            if (id != 0) ids.add(id); // bỏ qua icon không tồn tại, không crash
        }
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        customIconPoolCache = arr;
        return arr;
    }
    public PanelEngine(Context ctx, WindowManager wm, SharedPreferences prefs, boolean isAnyMode) {
    this.ctx = ctx; this.wm = wm; this.prefs = prefs; this.isAnyMode = isAnyMode;
    this.km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
    // [FIX PANEL MA] Hệ panel1_/panel2_/panel3_ cũ đã bị thay bằng Data Pack
    // Panel (pack_panel_*) nhưng UI không còn màn hình nào chỉnh panel1_en/
    // panel2_en/panel3_en nữa. Nếu còn sót giá trị true từ bản cũ, panel sẽ
    // hiện vĩnh viễn vì không cách nào tắt qua giao diện. Dọn 1 lần, an toàn
    // tuyệt đối vì không còn UI nào dùng các key này.
    SharedPreferences.Editor cleanup = prefs.edit();
    boolean hasLegacy = false;
    for (int i = 1; i <= 3; i++) {
        if (prefs.getBoolean("panel" + i + "_en", false)) {
            cleanup.putBoolean("panel" + i + "_en", false);
            hasLegacy = true;
        }
    }
    if (hasLegacy) cleanup.apply();
}
    /** Gọi mỗi khi lock state / accessibility state đổi — decide xem instance này
     *  (Lock hay Homacc, tùy trạng thái) có được phép giữ panel hay không. */
    public void rebuildAll() {
        // Duyệt đúng danh sách Panel THẬT đang tồn tại — Panel bị xóa khỏi pack_panel_ids
        // sẽ tự động bị dọn View ở dưới (không rebuild lại) → tự giải phóng RAM.
        java.util.Set<String> liveIds = new java.util.HashSet<>(getDynamicIds("pack_panel_ids"));
        for (String id : liveIds) rebuildOne(id);
        // Dọn View mồ côi (Panel đã bị xóa nhưng View vẫn còn trong Map)
        for (String orphan : new java.util.ArrayList<>(panels.keySet()))
            if (!liveIds.contains(orphan)) removePanelBody(orphan);
        for (String orphan : new java.util.ArrayList<>(handles.keySet()))
            if (!liveIds.contains(orphan)) removeHandle(orphan);
    }
    private List<String> getDynamicIds(String listKey) {
        String csv = prefs.getString(listKey, "");
        List<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
    private void rebuildOne(String id) {
    String sig = computeSignature(id);
    boolean sigChanged = !sig.equals(lastSignature.get(id));

    boolean shouldPanel = shouldPanelBodyExistNow(id);
    boolean panelExists = panels.get(id) != null;
    if (panelExists != shouldPanel || (panelExists && sigChanged)) {
        removePanelBody(id);
        if (shouldPanel) buildPanelBody(id);
    }

    boolean shouldHandle = shouldHandleExistNow(id);
    boolean handleExists = handles.get(id) != null;
    if (handleExists != shouldHandle || (handleExists && sigChanged)) {
        removeHandle(id);
        if (shouldHandle) buildHandle(id);
    }

    lastSignature.put(id, sig);
}
    /** Gọi từ prefListener khi 1 key panelN_xxx đổi — quyết định rebuild nặng hay update nhẹ. */
    public void onPrefChanged(String key) {
        // [MỚI] Icon shortcut đổi -> vẽ lại grid của mọi Panel đang hiện, KHÔNG cần
        // biết panel nào chứa shortcut đó (rẻ hơn dò tìm, vì Panel số lượng nhỏ).
        if (key != null && key.startsWith("shortcut_") && key.endsWith("_icon_override")) {
            for (String pid : new java.util.ArrayList<>(panels.keySet())) renderPanelGrid(pid);
            return;
        }
        if (key == null || !key.startsWith("pack_panel_")) return;
        String rest = key.substring("pack_panel_".length());
        int sep = rest.indexOf('_');
        if (sep <= 0) return;
        String id = rest.substring(0, sep);
        // [MỚI] Đổi icon riêng cho 1 action -> chỉ vẽ lại lưới icon, không tháo/dựng
        // lại Handle+Panel (đỡ tốn 1 lần removeView/addView WindowManager).
        if (key.contains("_icon_override_")) { renderPanelGrid(id); return; }
        boolean structural = key.endsWith("_apps") || key.endsWith("_acts") || key.endsWith("_cols")
    || key.endsWith("_icon_shape") || key.endsWith("_show_name") || key.endsWith("_en")
    || key.endsWith("_vis") || key.endsWith("_pos") || key.endsWith("_color_idx")
    || key.endsWith("_preview_handle");
if (structural) { rebuildOne(id); return; }
liveUpdateCosmetic(id);
    }
    /** Update tại chỗ (KHÔNG removeView/addView) cho opacity/length/width/radius/icon size. */
    private void liveUpdateCosmetic(String id) {
        View handle = handles.get(id); LinearLayout panel = panels.get(id);
        if (handle == null && panel == null) return; // chưa build -> để rebuildAll() lo sau
        String px = "pack_panel_" + id + "_";
        int color = parsePanelColor(prefs.getInt(px + "color_idx", 0));
        String edge = posToEdge(prefs.getInt(px + "pos", 0));

        if (handle != null) {
            int handleAlpha = prefs.getInt(px + "handle_alpha", 255);
            float handleR = prefs.getInt(px + "handle_radius", 28);
            int thick = prefs.getInt(px + "thick", 40);
            int handleWidth = prefs.getInt(px + "handle_width", 56);
            GradientDrawable hgd = new GradientDrawable();
            hgd.setColor(Color.argb(handleAlpha, Color.red(color), Color.green(color), Color.blue(color)));
            float[] hr = edge.equals("left") ? new float[]{0,0,handleR,handleR,handleR,handleR,0,0}
                : edge.equals("right") ? new float[]{handleR,handleR,0,0,0,0,handleR,handleR}
                : new float[]{handleR,handleR,handleR,handleR,0,0,0,0};
            hgd.setCornerRadii(hr);
            handle.setBackground(hgd);
            int handleLength = Math.max(80, thick);
            int hw = edge.equals("bottom") ? handleLength : handleWidth;
            int hh = edge.equals("bottom") ? handleWidth : handleLength;
            WindowManager.LayoutParams hp = (WindowManager.LayoutParams) handle.getLayoutParams();
            if (hp.width != hw || hp.height != hh) {
                hp.width = hw; hp.height = hh;
                try { wm.updateViewLayout(handle, hp); } catch (Exception ignored) {}
            }
        }
        if (panel != null) {
            int alpha = prefs.getInt(px + "alpha", 200);
            float panelR = prefs.getInt(px + "panel_radius", 24);
            int size = prefs.getInt(px + "size", 700);
            int iconSize = prefs.getInt(px + "icon_size", 110);

            GradientDrawable pgd = new GradientDrawable();
            pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
            pgd.setStroke(4, Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            pgd.setCornerRadius(panelR);
            panel.setBackground(pgd);

            int cols = Math.max(1, prefs.getInt(px + "cols", 4));
            int itemCount = Math.max(1, csvToList(prefs.getString(px+"apps","")).size()
                + csvToList(prefs.getString(px+"acts","")).size());
            int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
            int cellPx = iconSize + CELL_EXTRA;
            int contentMain = (edge.equals("bottom") ? cols : rows) * cellPx + 48;
            int userLength = prefs.getInt(px + "panel_length", contentMain);
            int mainAxis = Math.max(userLength, contentMain);
            boolean showNameOn = prefs.getInt(px+"show_name", 0) == 1;
int labelExtra = showNameOn ? 40 : 0;
int cross = Math.max(size, iconSize + 48 + labelExtra);
            WindowManager.LayoutParams pp = (WindowManager.LayoutParams) panel.getLayoutParams();
            if (edge.equals("bottom")) {
                pp.height = cross;
                pp.width = Math.min(mainAxis, ctx.getResources().getDisplayMetrics().widthPixels);
            } else {
                pp.width = cross;
                pp.height = Math.min(mainAxis, ctx.getResources().getDisplayMetrics().heightPixels);
            }
            try { wm.updateViewLayout(panel, pp); } catch (Exception ignored) {}
            Integer cachedSize = lastIconSizeCache.get(id);
            if (cachedSize == null || cachedSize != iconSize) {
                lastIconSizeCache.put(id, iconSize);
                renderPanelGrid(id); // chỉ vẽ lại grid con, không đụng handle/panel view
            }
        }
    }
private String computeSignature(String id) {
    String px = "pack_panel_" + id + "_";
    Boolean forceTest = forceTestOn.get(id);
    return prefs.getBoolean(px+"en", false) + "|" + prefs.getInt(px+"vis", 0) + "|"
        + prefs.getInt(px+"pos", 0) + "|" + prefs.getInt(px+"color_idx", 0) + "|"
        + prefs.getInt(px+"size", 700) + "|" + prefs.getInt(px+"thick", 40) + "|"
        + prefs.getInt(px+"alpha", 200) + "|" + prefs.getInt(px+"handle_alpha", 255) + "|"
        + prefs.getInt(px+"handle_radius", 28) + "|" + prefs.getInt(px+"panel_radius", 24) + "|"
        + prefs.getInt(px+"icon_size", 110) + "|" + prefs.getInt(px+"cols", 4) + "|"
        + prefs.getInt(px+"icon_shape", 0) + "|" + prefs.getInt(px+"show_name", 0) + "|"
        + prefs.getString(px+"apps","") + "|" + prefs.getString(px+"acts","") + "|"
        + (forceTest != null && forceTest);
}
// PANEL BODY: luôn theo đúng vòng đời Lock/Home, KHÔNG phụ thuộc vis nữa
private boolean shouldPanelBodyExistNow(String id) {
    String px = "pack_panel_" + id + "_";
    if (!prefs.getBoolean(px+"en", false)) return false;

    boolean locked = km != null && km.isKeyguardLocked();
    if (isAnyMode) {
        if (!locked && !AccessibleHomeService.isRunning) return false;
    } else {
        if (locked) return false;
    }
    return true;
}
// HANDLE: Cục Bộ chỉ hiện trong Design; Toàn Cục hiện như panel
private boolean shouldHandleExistNow(String id) {
    String px = "pack_panel_" + id + "_";
    int visMode = prefs.getInt(px+"vis", 0);
    // [FIX] Cục Bộ (visMode==0) là chế độ XEM TRƯỚC lúc setup — phải hoạt
    // động ĐỘC LẬP với Enable, vì lúc đang dựng Panel người dùng thường
    // CHƯA bật Enable. Bắt buộc "en" ở đây là nguyên nhân khiến tick
    // Preview Handle không có tác dụng gì.
    if (visMode == 0) {
        return prefs.getBoolean(px+"preview_handle", false);
    }
    // Toàn Cục (visMode==1) là hành vi production thật -> vẫn cần Enable
    // để Handle không tự ý xuất hiện ngoài ý muốn khi user chưa bật Pack.
    if (!prefs.getBoolean(px+"en", false)) return false;
    return shouldPanelBodyExistNow(id);
}
// Gọi từ Activity (qua broadcast) khi bật/tắt checkbox TEST
public void setForceTest(String id, boolean on) {
    forceTestOn.put(id, on);
    rebuildOne(id);
}
    private boolean shouldOwnPanelNow() {
        if (!isAnyMode) return true; // Homeb: luôn được phép (chỉ cần unlock, check riêng bên dưới)
        // isAnyMode=true dùng chung cho Lock + Homacc trong EdgeBarService.
        // Loại trừ lẫn nhau y hệt logic bars/corners: locked -> Lock giữ panel,
        // unlocked + Homacc chạy -> Homacc giữ panel. Không bao giờ cả hai cùng lúc.
        return true; // panel Lock vs Homacc tự phân biệt qua px+"owner" bên dưới nếu cần mở rộng
    }
    private void buildHandle(String id) {
    String px = "pack_panel_" + id + "_";
    int pos = prefs.getInt(px+"pos", 0);
    int color = parsePanelColor(prefs.getInt(px+"color_idx", 0));
    String edge = posToEdge(pos);
    int gravity = posToGravity(pos);
    int thick = prefs.getInt(px+"thick", 40);
    int handleAlpha = prefs.getInt(px+"handle_alpha", 255);
    float handleR = prefs.getInt(px+"handle_radius", 28);
    int handleWidth = prefs.getInt(px+"handle_width", 56);
    int wmType = isAnyMode
        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

    View handle = new View(ctx);
    GradientDrawable hgd = new GradientDrawable();
    hgd.setColor(Color.argb(handleAlpha, Color.red(color), Color.green(color), Color.blue(color)));
    float[] hr = edge.equals("left") ? new float[]{0,0,handleR,handleR,handleR,handleR,0,0}
        : edge.equals("right") ? new float[]{handleR,handleR,0,0,0,0,handleR,handleR}
        : new float[]{handleR,handleR,handleR,handleR,0,0,0,0};
    hgd.setCornerRadii(hr);
    handle.setBackground(hgd);
    int handleLength = Math.max(80, thick);
    int hw = edge.equals("bottom") ? handleLength : handleWidth;
    int hh = edge.equals("bottom") ? handleWidth : handleLength;
    WindowManager.LayoutParams hp = new WindowManager.LayoutParams(hw, hh, wmType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
        PixelFormat.TRANSLUCENT);
    hp.gravity = gravity;
    try { wm.addView(handle, hp); handles.put(id, handle); } catch (Exception e) { return; }
    final String fId = id;
    handle.setOnClickListener(v -> togglePanel(fId));
}
private void buildPanelBody(String id) {
    String px = "pack_panel_" + id + "_";
    int pos = prefs.getInt(px+"pos", 0);
    int color = parsePanelColor(prefs.getInt(px+"color_idx", 0));
    String edge = posToEdge(pos);
    int size = prefs.getInt(px+"size", 700);
    int alpha = prefs.getInt(px+"alpha", 200);
    float panelR = prefs.getInt(px+"panel_radius", 24);
    int wmType = isAnyMode
        ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

    LinearLayout panel = new LinearLayout(ctx);
panel.setOrientation(LinearLayout.VERTICAL);
// [FIX] Căn giữa nội dung trong khung Panel — trước đây thiếu dòng này khiến
// lưới icon dồn về góc trên khi Panel đặt dọc (trái/phải), tạo cảm giác
// lệch hẳn xuống nửa trên, mất cân bằng trên/dưới.
panel.setGravity(Gravity.CENTER);
GradientDrawable pgd = new GradientDrawable();
    pgd.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
    pgd.setStroke(4, Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
    pgd.setCornerRadius(panelR);
    panel.setBackground(pgd);
    panel.setVisibility(View.GONE);

    // [FIX] Thiếu "shortcuts" trong tổng số ô -> Panel bị đo kích thước thiếu chỗ
// (hoặc Shortcut không có ô nào để hiện ra).
int itemCount = csvToList(prefs.getString(px+"apps","")).size() +
                csvToList(prefs.getString(px+"acts","")).size() +
                csvToList(prefs.getString(px+"shortcuts","")).size();
    if (itemCount == 0) itemCount = 1;
    int cols = Math.max(1, Math.min(prefs.getInt(px+"cols", 4), itemCount));
    int rows = Math.max(1, (int) Math.ceil(itemCount / (float) cols));
    int iconSize = prefs.getInt(px+"icon_size", 110);
    int cellPx = iconSize + CELL_EXTRA;
    int panelPadding = 48;
    int contentMain = (edge.equals("bottom") ? cols : rows) * cellPx + panelPadding;
    int userLength = prefs.getInt(px+"panel_length", contentMain);
    int mainAxis = Math.max(userLength, contentMain);
    boolean showNameOn = prefs.getInt(px+"show_name", 0) == 1;
    int labelExtra = showNameOn ? 40 : 0;
    int crossFixed = Math.max(size, iconSize + panelPadding + labelExtra);
    int pw = edge.equals("bottom") ? Math.min(mainAxis, ctx.getResources().getDisplayMetrics().widthPixels) : crossFixed;
    int ph = edge.equals("bottom") ? crossFixed : Math.min(mainAxis, ctx.getResources().getDisplayMetrics().heightPixels);
    WindowManager.LayoutParams pp = new WindowManager.LayoutParams(pw, ph, wmType,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        | (isAnyMode ? WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED : 0),
        PixelFormat.TRANSLUCENT);
    pp.gravity = edge.equals("left") ? (Gravity.LEFT|Gravity.CENTER_VERTICAL)
               : edge.equals("right") ? (Gravity.RIGHT|Gravity.CENTER_VERTICAL)
               : (Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
    try { wm.addView(panel, pp); panels.put(id, panel); } catch (Exception e) { return; }
    final String fId = id;
    panel.setOnTouchListener((v,e) -> { closePanel(fId); return true; });

    lastIconSizeCache.put(id, iconSize);
    renderPanelGrid(id);
}
    // Hằng số DÙNG CHUNG giữa renderPanelGrid() và buildPanelIfEnabled() —
// đảm bảo kích thước panel tính toán KHỚP 100% với kích thước cell thực vẽ,
// đây chính là fix gốc cho lỗi lệch trái/phải khi đổi icon size.
private static final int CELL_INNER_PAD = 16; // đệm 8px mỗi bên quanh icon
private static final int CELL_MARGIN   = 16;  // margin 8px mỗi bên giữa các cell
private static final int CELL_EXTRA    = CELL_INNER_PAD + CELL_MARGIN; // = 32
private void renderPanelGrid(String id) {
    String px = "pack_panel_" + id + "_";
    LinearLayout panel = panels.get(id);
    if (panel == null) return;
    renderGen.putIfAbsent(id, new AtomicInteger(0));
    final int myGen = renderGen.get(id).incrementAndGet();
    int cols = Math.max(1, prefs.getInt(px+"cols", 4));
    int iconSize = prefs.getInt(px+"icon_size", 110);
    int cellSize = iconSize + CELL_INNER_PAD;

    // Dùng LinearLayout nhiều HÀNG thay GridLayout — mỗi hàng Gravity.CENTER_HORIZONTAL
    // để hàng cuối (thiếu ô) tự canh giữa, KHÔNG dồn về bên trái như GridLayout mặc định
    // (đây là nguyên nhân "bên phải trống nhiều hơn bên trái").
    LinearLayout gridContainer = new LinearLayout(ctx);
    gridContainer.setOrientation(LinearLayout.VERTICAL);
    gridContainer.setGravity(Gravity.CENTER_HORIZONTAL);
    gridContainer.setPadding(16, 24, 16, 24);
    panel.addView(gridContainer);
    List<String> apps = csvToList(prefs.getString(px+"apps", ""));
List<String> acts = csvToList(prefs.getString(px+"acts", ""));
List<String> shortcuts = csvToList(prefs.getString(px+"shortcuts", ""));
new Thread(() -> {
    List<Object[]> loaded = new ArrayList<>();
    for (String pkg : apps) {
        if (renderGen.get(id).get() != myGen) return;
        Drawable d = getCachedIcon(pkg);
        if (d != null) loaded.add(new Object[]{d, "APP", pkg});
    }
    if (renderGen.get(id).get() != myGen) return;
    for (String act : acts) loaded.add(new Object[]{null, "ACT", act});
    if (renderGen.get(id).get() != myGen) return;
    // [FIX] scKey là UUID THUẦN (showPanelMultiPicker lưu vậy) — bắt buộc thêm tiền tố
    // "RUN_SHORTCUT_" ở đây thì buildCell() mới rơi đúng nhánh RUN_SHORTCUT_ (lấy icon
    // thật qua getCachedShortcutIcon + gửi đúng IPC), thay vì rơi vào nhánh Action mặc
    // định và luôn hiện icon ⚡.
    for (String scKey : shortcuts) loaded.add(new Object[]{null, "ACT", "RUN_SHORTCUT_" + scKey});
    if (renderGen.get(id).get() != myGen) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (renderGen.get(id).get() != myGen || panels.get(id) != panel) return;
            LinearLayout row = null;
            for (int i = 0; i < loaded.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(ctx);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_HORIZONTAL);
                    gridContainer.addView(row);
                }
                Object[] item = loaded.get(i);
                View cell = buildCell(px, (String) item[1], item[0], (String) item[2]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellSize, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(8, 8, 8, 8);
                cell.setLayoutParams(lp);
                row.addView(cell);
            }
        });
    }).start();
}
    private Drawable getCachedIcon(String pkg) {
        synchronized (iconCache) { Drawable c = iconCache.get(pkg); if (c != null) return c; }
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            synchronized (iconCache) { iconCache.put(pkg, d); }
            return d;
        } catch (Exception e) { return null; }
    }
   private static final LinkedHashMap<String, Drawable> shortcutIconCache =
        new LinkedHashMap<String, Drawable>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, Drawable> e) { return size() > ICON_CACHE_LIMIT; }
        };
    // ===== MẶT NẠ HÌNH DẠNG ICON (thay Outline.setConvexPath — không cắt được hình lõm
// như Pebble/Rough/Pentacle, đây là nguyên nhân 4/5 style không đổi hình trước đây) =====
private static final int MASKED_ICON_CACHE_LIMIT = 60;
private static final LinkedHashMap<String, Bitmap> maskedIconCache =
    new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > MASKED_ICON_CACHE_LIMIT; }
    };
private static final Map<String, Path> shapePathCache = new HashMap<>();

private Path getShapePath(int shape, int size) {
    String key = shape + "_" + size;
    Path p = shapePathCache.get(key);
    if (p != null) return p;
    switch (shape) {
        case 1: p = buildSquirclePath(size); break;
        case 2: p = buildPebblePath(size); break;
        case 3: p = buildRoughPath(size); break;
        case 4: p = buildRoundedPentagon(size); break;
        default: p = new Path(); p.addOval(0, 0, size, size, Path.Direction.CW); break;
    }
    shapePathCache.put(key, p);
    return p;
}
/** Đo bounding box thật của Path rồi scale+căn giữa để lấp đầy khung size x size
 *  (giữ nguyên tỉ lệ, không méo hình) — làm cho Pentacle/Pebble/Rough to bằng
 *  Squircle/Circle, vốn đã tự chạm mép khung sẵn. */
private Path normalizeToFullSize(Path path, int size) {
    RectF b = new RectF();
    path.computeBounds(b, true);
    float w = b.width(), h = b.height();
    if (w <= 0 || h <= 0) return path;
    float scale = size / Math.max(w, h);
    Matrix m = new Matrix();
    m.postTranslate(-b.left, -b.top);
    m.postScale(scale, scale);
    float scaledW = w * scale, scaledH = h * scale;
    m.postTranslate((size - scaledW) / 2f, (size - scaledH) / 2f);
    path.transform(m);
    return path;
}
/** Cắt Bitmap nội dung theo đúng Path bằng Porter-Duff SRC_IN — cắt chính xác với
 *  MỌI hình kể cả lõm, viền chống răng cưa thật sự (không như Outline). */
private Bitmap maskBitmapToShape(Bitmap content, int shape, int size) {
    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    canvas.drawPath(getShapePath(shape, size), maskPaint);
    maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(content, 0, 0, maskPaint);
    return result;
}

/** Vẽ nền màu (nếu có) + icon/emoji rồi cắt theo hình đã chọn. Cache theo
 *  (cacheKey, shape, size, cover, backdropColor) — chỉ vẽ lại khi style/kích thước
 *  thực sự đổi, không tốn CPU khi cuộn danh sách.
 *  cover=true: phóng to 1.28x che kín 4 góc (dùng cho icon App).
 *  cover=false: thu nhỏ 0.8x, chừa đệm quanh (dùng cho icon Action/Shortcut). */
private Bitmap getStyledIconBitmap(String cacheKey, Drawable icon, String emoji, int shape, int size, int backdropColor, boolean cover) {
    String key = cacheKey + "_" + shape + "_" + size + "_" + cover + "_" + backdropColor;
    synchronized (maskedIconCache) {
        Bitmap cached = maskedIconCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
    }
    Bitmap content = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas cc = new Canvas(content);
    if (backdropColor != 0) cc.drawColor(backdropColor);
    if (icon != null) {
        int targetSize = Math.round(size * (cover ? 1.28f : 0.8f));
        int off = (size - targetSize) / 2;
        icon.setBounds(off, off, off + targetSize, off + targetSize);
        icon.draw(cc);
    } else if (emoji != null) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size * 0.5f); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.WHITE);
        Paint.FontMetrics fm = p.getFontMetrics();
        cc.drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2, p);
    }
    Bitmap result = maskBitmapToShape(content, shape, size);
    content.recycle();
    synchronized (maskedIconCache) { maskedIconCache.put(key, result); }
    return result;
}
    private Drawable getCachedShortcutIcon(String scId) {
        Drawable overrideIcon = getShortcutIconOverride(scId);
        if (overrideIcon != null) return overrideIcon;
        synchronized (shortcutIconCache) {
            Drawable c = shortcutIconCache.get(scId);
            if (c != null) return c;
        }
        try {
            String path = prefs.getString("shortcut_" + scId + "_icon_path", "");
            if (path.isEmpty()) return null;
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) return null;
            Drawable d = new BitmapDrawable(ctx.getResources(), bmp);
            synchronized (shortcutIconCache) { shortcutIconCache.put(scId, d); }
            return d;
        } catch (Exception e) { return null; }
    }
    private Drawable getShortcutIconOverride(String scId) {
        String val = prefs.getString("shortcut_" + scId + "_icon_override", "");
        if (val.isEmpty()) return null;
        try {
            if (val.startsWith("app:")) return getCachedIcon(val.substring(4));
            if (val.startsWith("poolc:")) {
                int idx = Integer.parseInt(val.substring(6));
                int[] pool = getCustomIconPool(ctx);
                if (idx >= 0 && idx < pool.length) return ctx.getDrawable(pool[idx]);
            } else if (val.startsWith("pool:")) {
                int idx = Integer.parseInt(val.substring(5));
                if (idx >= 0 && idx < SYSTEM_ICON_POOL.length) return ctx.getDrawable(SYSTEM_ICON_POOL[idx]);
            }
        } catch (Exception ignored) {}
        return null;
    }
    private Drawable getIconOverride(String panelId, String actionKey) {
        String key = "pack_panel_" + panelId + "_icon_override_" + actionKey;
        String val = prefs.getString(key, "");
        if (val.isEmpty()) return null;
        try {
            if (val.startsWith("app:")) return getCachedIcon(val.substring(4));
            if (val.startsWith("poolc:")) {
                int idx = Integer.parseInt(val.substring(6));
                int[] pool = getCustomIconPool(ctx);
                if (idx >= 0 && idx < pool.length) return ctx.getDrawable(pool[idx]);
            } else if (val.startsWith("pool:")) {
                int idx = Integer.parseInt(val.substring(5));
                if (idx >= 0 && idx < SYSTEM_ICON_POOL.length) return ctx.getDrawable(SYSTEM_ICON_POOL[idx]);
            }
        } catch (Exception ignored) {}
        return null;
    }
    private String getCachedAppLabel(String pkg) {
        String c = labelCache.get(pkg); if (c != null) return c;
        try {
            String l = ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(pkg,0)).toString();
            labelCache.put(pkg, l); return l;
        } catch (Exception e) { return pkg; }
    }
    private String getActionLabelForPanel(String key) {
    if (key.startsWith("RUN_SHORTCUT_")) {
        String scId = key.substring("RUN_SHORTCUT_".length());
        return prefs.getString("shortcut_" + scId + "_name", "Shortcut");
    }
    if (key.startsWith("INTENT_")) return prefs.getString("intent_" + key.substring(7) + "_name", "Intent");
    if (key.startsWith("MACRO_")) return prefs.getString("macro_" + key.substring(6) + "_name", "Macro");
    String l = ACT_LABEL_MAP.get(key);
    return l != null ? l : key;
}
// Ngũ giác bo góc mềm — dùng Outline.setConvexPath() để clip, KHÔNG cần custom Drawable
// riêng, tận dụng luôn backdrop trắng sẵn có -> nhẹ GPU, không thêm object vẽ nào.
// [FIX] Toạ độ Q-curve LẤY ĐÚNG từ path SVG mẫu PENTACLE (viewBox 500x500) —
// trước đây tự tính ngũ giác đều bo góc, giờ khớp chính xác dáng "giọt lệ 5 cạnh"
// của file mẫu. Scale theo iconSize/500. Tên hàm giữ nguyên để không phải sửa
// mọi nơi gọi buildRoundedPentagon(...).
private Path buildRoundedPentagon(int size) {
    Path path = new Path();
    float s = size / 500f;
    path.moveTo(229.46f * s, 84.93f * s);
    path.quadTo(250f * s, 70f * s, 270.54f * s, 84.93f * s);
    path.quadTo(339.13f * s, 127.35f * s, 400.66f * s, 179.47f * s);
    path.quadTo(421.2f * s, 194.4f * s, 413.35f * s, 218.54f * s);
    path.quadTo(394.21f * s, 296.85f * s, 363.65f * s, 371.46f * s);
    path.quadTo(355.8f * s, 395.6f * s, 330.41f * s, 395.6f * s);
    path.quadTo(250f * s, 401.6f * s, 169.59f * s, 395.6f * s);
    path.quadTo(144.2f * s, 395.6f * s, 136.35f * s, 371.46f * s);
    path.quadTo(105.79f * s, 296.85f * s, 86.65f * s, 218.54f * s);
    path.quadTo(78.8f * s, 194.4f * s, 99.34f * s, 179.47f * s);
    path.quadTo(160.87f * s, 127.35f * s, 229.46f * s, 84.93f * s);
    path.close();
    return normalizeToFullSize(path, size);
}
   // [MỚI] Squircle đúng thuật toán superellipse (|x|^n+|y|^n=1, n=5) — khớp hình
// dạng file mẫu SQUIRCLE (bo góc mềm, không phải RoundRect 0.15 như code cũ).
// 90 điểm là đủ mượt cho icon panel, không tốn thêm CPU đáng kể so với RoundRect cũ.
private Path buildSquirclePath(int size) {
    Path path = new Path();
    float cx = size / 2f, cy = size / 2f, r = size / 2f;
    float n = 5f;
    int steps = 72;
    for (int i = 0; i <= steps; i++) {
        double t = (Math.PI * 2 * i) / steps;
        double ct = Math.cos(t), st = Math.sin(t);
        float x = (float) (cx + r * Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / n));
        float y = (float) (cy + r * Math.signum(st) * Math.pow(Math.abs(st), 2.0 / n));
        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
    }
    path.close();
    return path;
}
   // Pebble — 1 góc to, 3 góc nhỏ (kiểu Material You "pebble"). Path chỉ build lại khi
// kích thước icon đổi (Android cache Outline) — rẻ ngang RoundRect, không tốn thêm CPU.
// [FIX] Toạ độ Q-curve LẤY ĐÚNG từ path SVG mẫu PEBBLE (viewBox 480x480), đã lật
// ngang (mirror x = 480-x) để khớp CSS "transform: scaleX(-1)" mà file mẫu áp dụng
// lên đúng chiều hiển thị thật. Scale theo iconSize/480.
private Path buildPebblePath(int size) {
    Path path = new Path();
    float s = size / 480f;
    path.moveTo(93f * s, 319.5f * s);
    path.quadTo(148f * s, 399f * s, 247f * s, 411.5f * s);
    path.quadTo(346f * s, 424f * s, 388f * s, 332f * s);
    path.quadTo(430f * s, 240f * s, 384f * s, 155f * s);
    path.quadTo(338f * s, 70f * s, 241.5f * s, 72.5f * s);
    path.quadTo(145f * s, 75f * s, 91.5f * s, 157.5f * s);
    path.quadTo(38f * s, 240f * s, 93f * s, 319.5f * s);
    path.close();
    return normalizeToFullSize(path, size);
}
// Rough — viền lởm chởm kiểu "xé giấy". Toạ độ CỐ ĐỊNH (không Random) nên mọi icon
// Rough trong cùng Panel vẽ giống hệt nhau, Zero jitter/Zero alloc thêm mỗi lần render.
// [FIX] Toạ độ ĐẦY ĐỦ lấy đúng từ path SVG mẫu ROUGH (viewBox 500x500) — trước đây
// chỉ có 20 điểm tự vẽ tay xấp xỉ, giờ dùng nguyên bộ ~100 điểm gốc để đúng hình
// "xé giấy" như file mẫu. Scale theo iconSize/500.
private static final float[][] ROUGH_PTS_500 = {
    {407.18f,250.00f},{411.17f,256.33f},{412.91f,262.82f},{413.20f,269.32f},{415.70f,276.24f},
    {419.13f,283.64f},{421.58f,291.19f},{417.25f,297.17f},{414.81f,303.55f},{411.09f,309.43f},
    {408.39f,315.61f},{406.27f,322.04f},{403.78f,328.35f},{401.76f,334.99f},{400.91f,342.48f},
    {398.48f,349.21f},{393.48f,354.25f},{386.58f,357.67f},{381.53f,362.34f},{376.74f,367.16f},
    {369.11f,369.11f},{362.49f,371.69f},{355.79f,373.87f},{350.49f,377.47f},{344.79f,380.46f},
    {339.78f,384.37f},{334.84f,388.45f},{330.69f,394.08f},{324.56f,396.33f},{317.81f,397.08f},
    {312.30f,400.42f},{306.20f,402.34f},{300.89f,406.63f},{295.34f,410.76f},{288.56f,410.63f},
    {281.79f,409.82f},{275.48f,410.86f},{269.26f,412.70f},{262.90f,413.93f},{256.19f,407.67f},
    {250.00f,402.60f},{244.17f,398.47f},{238.49f,396.30f},{233.13f,392.56f},{227.35f,393.00f},
    {221.50f,393.28f},{216.56f,389.29f},{211.16f,387.71f},{206.13f,385.03f},{200.10f,385.25f},
    {193.09f,387.40f},{186.16f,388.47f},{178.30f,390.71f},{173.60f,386.42f},{168.08f,383.68f},
    {161.97f,381.74f},{156.08f,379.27f},{153.49f,372.42f},{151.07f,365.84f},{149.01f,359.25f},
    {146.32f,353.68f},{142.45f,349.42f},{137.41f,346.16f},{132.87f,342.34f},{126.18f,339.96f},
    {118.74f,337.71f},{111.53f,334.85f},{102.25f,332.74f},{92.70f,330.15f},{88.17f,324.60f},
    {82.05f,319.57f},{82.48f,311.80f},{83.78f,304.01f},{84.58f,296.65f},{84.39f,289.76f},
    {84.28f,282.96f},{84.87f,276.15f},{83.30f,269.73f},{82.13f,263.21f},{80.15f,256.67f},
    {78.46f,250.00f},{75.73f,243.15f},{73.57f,236.11f},{70.61f,228.77f},{67.47f,221.09f},
    {63.88f,212.98f},{61.64f,204.78f},{62.83f,197.21f},{65.69f,190.11f},{68.26f,182.95f},
    {70.44f,175.62f},{74.30f,169.00f},{77.78f,162.25f},{84.26f,157.18f},{91.31f,152.76f},
    {99.53f,149.46f},{105.88f,145.29f},{112.23f,141.39f},{118.57f,137.74f},{125.18f,134.61f},
    {130.59f,130.59f},{135.04f,125.64f},{140.22f,121.46f},{145.67f,117.66f},{149.88f,112.19f},
    {154.50f,107.08f},{162.64f,107.44f},{169.92f,107.01f},{176.75f,106.24f},{183.03f,104.73f},
    {189.82f,104.70f},{196.95f,106.19f},{203.40f,106.59f},{209.64f,106.88f},{215.29f,105.44f},
    {220.97f,104.04f},{226.34f,100.65f},{231.97f,97.63f},{237.61f,92.63f},{243.64f,88.22f},
    {250.00f,82.72f},{256.81f,76.75f},{264.09f,71.02f},{272.02f,63.99f},{279.19f,65.70f},
    {286.43f,66.83f},{293.74f,67.82f},{300.83f,69.76f},{307.90f,71.80f},{313.43f,78.07f},
    {318.32f,85.07f},{322.71f,92.27f},{327.77f,97.36f},{331.93f,103.71f},{335.69f,110.16f},
    {340.84f,114.05f},{344.85f,119.44f},{349.49f,123.80f},{355.19f,126.84f},{360.49f,130.48f},
    {367.61f,132.39f},{374.45f,134.96f},{380.48f,138.56f},{388.67f,140.68f},{395.93f,143.98f},
    {404.95f,146.46f},{412.78f,150.25f},{416.08f,156.99f},{419.72f,163.52f},{416.90f,173.06f},
    {413.79f,182.16f},{410.78f,190.68f},{407.95f,198.68f},{405.08f,206.26f},{403.86f,213.06f},
    {401.96f,219.77f},{400.88f,226.10f},{401.10f,232.12f},{402.37f,238.01f},{404.41f,243.93f}
};
private Path buildRoughPath(int size) {
    Path path = new Path();
    float s = size / 500f;
    for (int i = 0; i < ROUGH_PTS_500.length; i++) {
        float x = ROUGH_PTS_500[i][0] * s, y = ROUGH_PTS_500[i][1] * s;
        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
    }
    path.close();
    return normalizeToFullSize(path, size);
}
        private View wrapIconCell(String px, Drawable icon, String emoji, String cacheKey, View.OnClickListener onClick, String label) {
    int iconSize = prefs.getInt(px + "icon_size", 110);
    LinearLayout box = new LinearLayout(ctx); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);

    int shape = prefs.getInt(px + "icon_shape", 0);
    boolean useSystemMask = shape == 5 && icon != null
        && Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;

    if (useSystemMask) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(icon);
        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        box.addView(iv);
    } else {
        int effectiveShape = (shape == 5) ? 0 : shape;
        int backdropColor = Color.argb(230, 60, 64, 67);
        Bitmap styled = getStyledIconBitmap(cacheKey, icon, icon == null ? emoji : null, effectiveShape, iconSize, backdropColor, false);
        ImageView iv = new ImageView(ctx);
        iv.setImageBitmap(styled);
        iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        box.addView(iv);
    }

    boolean showName = prefs.getInt(px + "show_name", 0) == 1;
    if (showName && label != null) {
        TextView tvLabel = new TextView(ctx); tvLabel.setText(label); tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(9); tvLabel.setMaxLines(1); tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(iconSize, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(tvLabel);
    }
    box.setOnClickListener(onClick);
    return box;
}
private View wrapAppIconCell(String px, Drawable icon, String cacheKey, View.OnClickListener onClick, String label) {
    int iconSize = prefs.getInt(px + "icon_size", 110);
    LinearLayout box = new LinearLayout(ctx);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);

    int shape = prefs.getInt(px + "icon_shape", 0);
    boolean isAdaptive = Build.VERSION.SDK_INT >= 26 && icon instanceof AdaptiveIconDrawable;

    ImageView iv = new ImageView(ctx);
    iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));

    if (shape == 5 && isAdaptive) {
        // Icon Adaptive thật của launcher -> giữ nguyên hình dạng hệ thống, KHÔNG cắt lại
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
    } else {
        // System + icon vuông sắc cạnh (MB Bank, Beta Cinema...) -> ép Circle + phóng to.
        // Các style khác (Squircle/Pebble/Rough/Pentacle) cũng luôn phóng to 1.28x cho
        // icon App để che kín 4 góc, đúng yêu cầu.
        int effectiveShape = (shape == 5) ? 0 : shape;
        Bitmap styled = getStyledIconBitmap(cacheKey, icon, null, effectiveShape, iconSize, 0, true);
        iv.setImageBitmap(styled);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
    }
    box.addView(iv);

    boolean showName = prefs.getInt(px + "show_name", 0) == 1;
    if (showName && label != null) {
        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(9);
        tvLabel.setMaxLines(1);
        tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(iconSize, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(tvLabel);
    }
    box.setOnClickListener(onClick);
    return box;
}
    private View buildCell(String px, String type, Object payload, String ref) {
    String panelId = px.startsWith("pack_panel_") ? px.substring("pack_panel_".length(), px.length()-1) : "";
    if (type.equals("APP")) {
        return wrapAppIconCell(px, (Drawable) payload, ref, v -> {
            Intent li = ctx.getPackageManager().getLaunchIntentForPackage(ref);
            if (li != null) { li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(li); }
            closeAllPanels();
        }, getCachedAppLabel(ref));
    } else if (ref.startsWith("RUN_SHORTCUT_")) {
        String scId = ref.substring("RUN_SHORTCUT_".length());
        Drawable icon = getCachedShortcutIcon(scId);
        String label = prefs.getString("shortcut_" + scId + "_name", "Shortcut");
        return wrapIconCell(px, icon, icon == null ? "🔗" : null, "sc_" + scId, v -> {
            try {
                String uri = prefs.getString("shortcut_" + scId + "_intent_uri", "");
                if (!uri.isEmpty()) {
                    Intent scIntent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                    scIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(scIntent);
                }
            } catch (Exception ignored) {}
            closeAllPanels();
        }, label);
    } else {
        String label = getActionLabelForPanel(ref);
        Drawable overrideIcon = getIconOverride(panelId, ref);
        Integer resId = ACT_ICON_RES.get(ref);
        Drawable sysIcon = overrideIcon != null ? overrideIcon
            : (resId != null ? ctx.getDrawable(resId) : null);
        if (overrideIcon == null && sysIcon != null) {
            sysIcon = sysIcon.mutate();
            sysIcon.setTint(Color.WHITE);
        }
        return wrapIconCell(px, sysIcon, sysIcon == null ? actEmoji(ref) : null, "act_" + panelId + "_" + ref, v -> {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            ipc.putExtra("act", ref);
            ctx.sendBroadcast(ipc);
            closeAllPanels();
        }, label);
    }
}
    private String actEmoji(String key) {
    if (key.startsWith("RUN_SHORTCUT_")) return "🔗";
    if (key.startsWith("INTENT_")) return "⚡";
    if (key.startsWith("MACRO_")) return "🤖";
    switch (key) {
            case "FLASH": return "🔦"; case "SCREEN_OFF": return "📴"; case "SCREENSHOT": return "📸";
            case "CAMERA": return "📷"; case "VOLUME": return "🔊"; case "NOTIFICATIONS": return "🔔";
            case "BACK": return "⬅️"; case "HOME": return "🏠"; case "RECENTS": return "🗂️";
            case "VOICE_RECORD": return "🎙️"; case "TOGGLE_MORSE": return "🔐"; default: return "⚡";
        }
    }

    private List<String> csvToList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }
    private int parsePanelColor(int idx) {
        String[] hex = {"#607D8B","#78909C","#90A4AE","#455A64","#5C6BC0","#4DB6AC","#B0BEC5","#37474F"};
        try { return Color.parseColor(hex[Math.max(0, Math.min(hex.length-1, idx))]); }
        catch (Exception e) { return Color.parseColor("#607D8B"); }
    }
    private String posToEdge(int pos) { if (pos <= 2) return "bottom"; if (pos <= 5) return "left"; return "right"; }
    private int posToGravity(int pos) {
        switch (pos) {
            case 0: return Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; case 1: return Gravity.BOTTOM|Gravity.LEFT;
            case 2: return Gravity.BOTTOM|Gravity.RIGHT; case 3: return Gravity.LEFT|Gravity.TOP;
            case 4: return Gravity.LEFT|Gravity.CENTER_VERTICAL; case 5: return Gravity.LEFT|Gravity.BOTTOM;
            case 6: return Gravity.RIGHT|Gravity.TOP; case 7: return Gravity.RIGHT|Gravity.CENTER_VERTICAL;
            default: return Gravity.RIGHT|Gravity.BOTTOM;
        }
    }

    public void togglePanel(String id) {
    // Panel body luôn phải tồn tại (theo shouldPanelBodyExistNow), không phụ thuộc Handle
    if (panels.get(id) == null && shouldPanelBodyExistNow(id)) buildPanelBody(id);
    if (panels.get(id) == null) return; // chưa đủ điều kiện (chưa bật/đang sai trạng thái khoá)
    Boolean open = panelOpen.get(id);
    if (open != null && open) closePanel(id); else openPanel(id);
}
    private void openPanel(String id) {
        LinearLayout panel = panels.get(id);
        if (panel == null) return;
        panelOpen.put(id, true);
        panel.setVisibility(View.VISIBLE);
        View handle = handles.get(id);
        if (handle != null) handle.setVisibility(View.GONE);
        String px = "pack_panel_" + id + "_";
        String edge = posToEdge(prefs.getInt(px+"pos", 0));
        Animation anim = edge.equals("bottom")
            ? new TranslateAnimation(0,0, prefs.getInt(px+"size",500), 0)
            : new TranslateAnimation(edge.equals("left") ? -prefs.getInt(px+"size",500) : prefs.getInt(px+"size",500), 0, 0, 0);
        anim.setDuration(200);
        panel.startAnimation(anim);
    }
    private void closePanel(String id) {
        LinearLayout panel = panels.get(id);
        Boolean open = panelOpen.get(id);
        if (panel == null || open == null || !open) return;
        panelOpen.put(id, false);
        panel.setVisibility(View.GONE);
        View handle = handles.get(id);
        if (handle != null) handle.setVisibility(View.VISIBLE);
    }
    private void closeAllPanels() { for (String id : new java.util.ArrayList<>(panels.keySet())) closePanel(id); }
    private void removeHandle(String id) {
    View handle = handles.get(id);
    try { if (handle != null) wm.removeView(handle); } catch (Exception ignored) {}
    handles.remove(id);
}
private void removePanelBody(String id) {
    AtomicInteger gen = renderGen.get(id);
    if (gen != null) gen.incrementAndGet();
    LinearLayout panel = panels.get(id);
    try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
    panels.remove(id); panelOpen.remove(id);
  }
}
