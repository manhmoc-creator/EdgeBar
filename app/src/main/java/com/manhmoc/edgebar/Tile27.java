package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
/**
 * V19.12.3.6.8 THE ETERNAL EGO — Tile27
 * Label + Icon động từ prefs. Auto-icon theo action.
 * ZERO RAM: Icon.createWithResource() chỉ lưu resource ID (4 bytes).
 * Receiver chỉ active khi QS panel mở → ZERO background battery drain.
 */
public class Tile27 extends TileService {

    // ICON_POOL phải khớp với TILE_ICON_NAMES trong MainActivity
    private static final int[] ICON_POOL = {
        // ===== 20 icon gốc (GIỮ NGUYÊN index) =====
        android.R.drawable.ic_menu_compass,
        android.R.drawable.ic_menu_search,
        android.R.drawable.ic_lock_idle_lock,
        android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_crop,
        android.R.drawable.ic_media_play,
        android.R.drawable.ic_menu_send,
        android.R.drawable.ic_media_next,
        android.R.drawable.ic_menu_share,
        android.R.drawable.ic_menu_info_details,
        android.R.drawable.ic_menu_manage,
        android.R.drawable.ic_menu_call, // [FIX] thay icon trùng ic_menu_send
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_delete,
        android.R.drawable.ic_menu_add,
        android.R.drawable.ic_menu_close_clear_cancel,
        android.R.drawable.ic_menu_upload,
        android.R.drawable.ic_menu_view,
        android.R.drawable.star_on,
        android.R.drawable.ic_menu_mylocation,
        // ===== [MỚI] Nhóm 1: Nét thanh mảnh, chỉ viền =====
        android.R.drawable.ic_menu_agenda,
        android.R.drawable.ic_menu_always_landscape_portrait,
        android.R.drawable.ic_menu_day,
        android.R.drawable.ic_menu_directions,
        android.R.drawable.ic_menu_gallery,
        android.R.drawable.ic_menu_help,
        android.R.drawable.ic_menu_mapmode,
        android.R.drawable.ic_menu_month,
        android.R.drawable.ic_menu_more,
        android.R.drawable.ic_menu_preferences,
        android.R.drawable.ic_menu_recent_history,
        android.R.drawable.ic_menu_revert,
        android.R.drawable.ic_menu_rotate,
        android.R.drawable.ic_menu_save,
        android.R.drawable.ic_menu_sort_alphabetically,
        android.R.drawable.ic_menu_sort_by_size,
        android.R.drawable.ic_menu_today,
        android.R.drawable.ic_menu_zoom,
        android.R.drawable.ic_lock_idle_alarm,
        android.R.drawable.ic_lock_idle_charging,
        android.R.drawable.ic_lock_idle_low_battery,
        android.R.drawable.ic_lock_silent_mode,
        android.R.drawable.ic_lock_silent_mode_off,
        android.R.drawable.ic_lock_airplane_mode,
        android.R.drawable.ic_lock_airplane_mode_off,
        // ===== [MỚI] Nhóm 2: Đổ bóng / xám mờ =====
        android.R.drawable.stat_sys_download,
        android.R.drawable.stat_sys_download_done,
        android.R.drawable.stat_sys_upload,
        android.R.drawable.stat_sys_upload_done,
        android.R.drawable.stat_notify_chat,
        android.R.drawable.stat_notify_error,
        android.R.drawable.stat_notify_missed_call,
        android.R.drawable.stat_notify_sync,
        android.R.drawable.stat_notify_sync_noanim,
        android.R.drawable.stat_notify_voicemail,
        // ===== [MỚI] Nhóm 3: Rỗng / nửa =====
        android.R.drawable.star_off,
        android.R.drawable.btn_star_big_off,
        android.R.drawable.rate_star_big_off,
        android.R.drawable.rate_star_big_half,
        // ===== [MỚI] Nhóm 4: Điều hướng cơ bản =====
        android.R.drawable.arrow_down_float,
        android.R.drawable.arrow_up_float,
        android.R.drawable.ic_input_delete,
        android.R.drawable.ic_input_get,
        // ===== [MỚI] Nhóm 5: Đậm / khối đặc =====
        android.R.drawable.ic_media_ff,
        android.R.drawable.ic_media_rew,
        android.R.drawable.ic_media_previous,
        android.R.drawable.ic_media_pause,
        android.R.drawable.presence_online,
        android.R.drawable.presence_busy,
        android.R.drawable.presence_audio_online,
        android.R.drawable.presence_video_online,
        android.R.drawable.ic_btn_speak_now,
        android.R.drawable.ic_lock_lock,
        android.R.drawable.ic_secure,
        android.R.drawable.ic_lock_power_off,
        android.R.drawable.ic_delete,
        android.R.drawable.ic_input_add,
        android.R.drawable.ic_dialog_alert,
        android.R.drawable.stat_sys_warning,
        android.R.drawable.ic_dialog_email,
        android.R.drawable.ic_dialog_info,
        android.R.drawable.ic_dialog_dialer,
        android.R.drawable.btn_star_big_on,
        android.R.drawable.rate_star_big_on,
        android.R.drawable.sym_def_app_icon,
        android.R.drawable.sym_action_call,
        android.R.drawable.sym_action_chat,
        android.R.drawable.ic_dialog_map
    };


    private BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
    String act = intent.getAction();
    // [MỚI] Vừa cập nhật đè bản mới xong — ép mọi service liên quan tới
    // Panel dừng hẳn rồi khởi động lại sạch, để không còn tiến trình cũ
    // nào giữ View "mồ côi" trên WindowManager.
    if ("android.intent.action.MY_PACKAGE_REPLACED".equals(act)) {
        try { context.stopService(new Intent(context, HomescreenService.class)); } catch (Exception ignored) {}
        SharedPreferences prefs = context.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        boolean wasHomeOn = prefs.getBoolean("home_mode_en", false);
        if (wasHomeOn) {
            Intent i = new Intent(context, HomescreenService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
        }
        // AccessibilityService (EdgeBarService) KHÔNG thể tự start/stop bằng
        // code — chỉ hệ thống mới bind được. Đây là lúc duy nhất nên báo cho
        // người dùng biết cần thao tác tay.
        android.widget.Toast.makeText(context,
            "Vừa cập nhật xong! Nếu Handle/Panel/Bar/Corner không phản hồi, vào Cài đặt > Trợ năng, tắt rồi bật lại Edge Bar Trợ Năng.",
            android.widget.Toast.LENGTH_LONG).show();
        return;
    }
    }
    };
    @Override public void onStartListening() {
        try {
            registerReceiver(configReceiver,
                new IntentFilter("com.manhmoc.edgebar.TILE_CONFIG_CHANGED"));
        } catch (Exception ignored) {}
        updateTileUI();
    }

    @Override public void onStopListening() {
        try { unregisterReceiver(configReceiver); } catch (Exception ignored) {}
    }

    private int autoIconForAct(String act) {
if (act == null) return 1;
if (act.equals("LAUNCH_APP")) return 18; // star_on
if (act.equals("RUN_SHORTCUT")) return 6; // send
if (act.startsWith("INTENT_")) return 7; // next
if (act.startsWith("MACRO_")) return 5; // play
if (act.startsWith("PANEL_")) return 10; // manage
switch (act) {
case "BACK": return 1; // search
case "HOME": return 4; // crop
case "RECENTS": return 17; // view
case "SCREEN_OFF": return 2; // lock
case "FLASH": return 0; // compass
case "POWER_DIALOG": return 15; // close_clear_cancel
case "VOLUME": return 9; // info_details
case "SCREENSHOT": return 4; // crop
case "CAMERA": return 3; // camera
case "NOTIFICATIONS": return 8; // share
case "TOGGLE_ACC": return 10; // manage
case "TOGGLE_OVERLAY": return 17; // view
case "YTDL_DOWNLOAD": return 16; // upload
case "VOICE_RECORD": return 6; // send
case "SPLIT_SCREEN": return 12; // edit
default: return 1;
}
}
private void updateTileUI() {
    Tile t = getQsTile();
    if (t == null) return;
    SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String boundId = prefs.getString("tile_slot_27_id", "");
    if (boundId.isEmpty()) {
        t.setLabel("Tile 27 (trống)");
        t.setState(Tile.STATE_INACTIVE);
        t.updateTile();
        return;
    }
    String action = prefs.getString("tilev2_" + boundId + "_act", "NONE");
    String label = prefs.getString("tilev2_" + boundId + "_label", "Tile 27");
    t.setLabel(label);
    int manualIconIdx = prefs.getInt("tilev2_" + boundId + "_icon_idx", -1);
    int iconIdx = (manualIconIdx >= 0 && manualIconIdx < ICON_POOL.length) ? manualIconIdx : autoIconForAct(action);
    t.setIcon(Icon.createWithResource(this, ICON_POOL[iconIdx]));
    t.setState(action.equals("NONE") ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
}

@Override public void onClick() {
    super.onClick();
    SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
    String boundId = prefs.getString("tile_slot_27_id", "");
    if (!boundId.isEmpty()) {
        String act = prefs.getString("tilev2_" + boundId + "_act", "NONE");
        if (!act.equals("NONE")) {
            Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
            if (act.equals("LAUNCH_APP")) {
                ipc.putExtra("act", "LAUNCH_APP");
                ipc.putExtra("launch_pkg", prefs.getString("tilev2_" + boundId + "_launch_pkg", ""));
            } else if (act.equals("RUN_SHORTCUT")) {
                ipc.putExtra("act", "RUN_SHORTCUT_" + prefs.getString("tilev2_" + boundId + "_shortcut_id", ""));
            } else {
                ipc.putExtra("act", act);
            }
            sendBroadcast(ipc);
        }
    }
    Tile t = getQsTile();
    if (t != null) { t.setState(Tile.STATE_INACTIVE); t.updateTile(); }
}
}
