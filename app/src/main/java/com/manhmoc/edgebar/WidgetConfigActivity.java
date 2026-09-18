package com.manhmoc.edgebar;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private SharedPreferences prefs;
    private String[] KEYS, LABS;
    private Button btnTap, btnDtap;
    private CheckBox cbVib, cbAnim;
    private String tapAct = "NONE", dtapAct = "NONE";
    private String tapPkg = "", dtapPkg = "";
    private String tapScId = "", dtapScId = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Bundle ex = getIntent().getExtras();
        if (ex != null) widgetId = ex.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        tapAct   = prefs.getString("widget_" + widgetId + "_tap_act", "NONE");
        dtapAct  = prefs.getString("widget_" + widgetId + "_dtap_act", "NONE");
        tapPkg   = prefs.getString("widget_" + widgetId + "_tap_pkg", "");
        dtapPkg  = prefs.getString("widget_" + widgetId + "_dtap_pkg", "");
        tapScId  = prefs.getString("widget_" + widgetId + "_tap_shortcut_id", "");
        dtapScId = prefs.getString("widget_" + widgetId + "_dtap_shortcut_id", "");

        KEYS = new String[]{
            "NONE","SCREEN_OFF","BACK","HOME","RECENTS","FLASH","CAMERA",
            "VOLUME","SCREENSHOT","POWER_DIALOG","NOTIFICATIONS","QUICK_SETTINGS",
            "SPLIT_SCREEN","SCREEN_RECORD","AUTO_ROTATE_TOGGLE","TOGGLE_OVERLAY",
            "TOGGLE_RECORD","PAUSE_RECORD","YTDL_DOWNLOAD","TOGGLE_WORK_PROFILE",
            "OPEN_STORAGE_SCAN","SCAN_QR","PLAY_MY_PLAYLIST",
            "LAUNCH_APP","RUN_SHORTCUT"};
        LABS = new String[]{
            "Không có","Tắt màn hình","Quay lại","Màn chính","Đa nhiệm",
            "Đèn pin","Camera","Âm lượng","Chụp màn hình","Menu nguồn","Thông báo",
            "Cài đặt nhanh","Chia đôi màn","Quay màn hình","Tự động xoay","Bật/tắt Trợ năng",
            "Bật/tắt Ghi âm","Dừng/Tiếp Ghi âm","Tải YTDLnis","Bật/tắt Hồ sơ CV",
            "Quét Dung Lượng","Quét QR","Phát My Playlist",
            "Mở ứng dụng","Chạy Shortcut"};
        buildUI();
    }

    private void buildUI() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#121212"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 120, 50, 40);

        TextView t = new TextView(this);
        t.setText("⚡ Cấu hình Edge Bar Widget");
        t.setTextColor(Color.parseColor("#8AB4F8")); t.setTextSize(18);
        t.setPadding(0, 0, 0, 20); root.addView(t);

        TextView note = new TextView(this);
        note.setText("• Widget trong suốt 100%\n"
            + "• Tap + Double-Tap → gọi action như Bar/Corner\n"
            + "• Nhấn giữ widget → Launcher tự resize/mở thùng rác\n"
            + "• Vuốt ngoài widget → hệ thống/Launcher bình thường");
        note.setTextColor(Color.parseColor("#9AA0A6")); note.setTextSize(12);
        note.setPadding(0, 0, 0, 30); root.addView(note);

        TextView labelTap = new TextView(this);
        labelTap.setText("👆 TAP (1 chạm)");
        labelTap.setTextColor(Color.parseColor("#FFC107")); labelTap.setTextSize(14);
        labelTap.setPadding(0, 10, 0, 10); root.addView(labelTap);

        btnTap = new Button(this);
        btnTap.setTextColor(Color.WHITE);
        btnTap.setBackgroundColor(Color.parseColor("#202124"));
        btnTap.setText(buildLabel(tapAct, tapPkg, tapScId));
        btnTap.setOnClickListener(v -> picker(true));
        root.addView(btnTap);

        TextView labelDtap = new TextView(this);
        labelDtap.setText("✌️ DOUBLE-TAP (2 chạm)");
        labelDtap.setTextColor(Color.parseColor("#FFC107")); labelDtap.setTextSize(14);
        labelDtap.setPadding(0, 30, 0, 10); root.addView(labelDtap);

        btnDtap = new Button(this);
        btnDtap.setTextColor(Color.WHITE);
        btnDtap.setBackgroundColor(Color.parseColor("#202124"));
        btnDtap.setText(buildLabel(dtapAct, dtapPkg, dtapScId));
        btnDtap.setOnClickListener(v -> picker(false));
        root.addView(btnDtap);

        TextView labelOpt = new TextView(this);
        labelOpt.setText("⚙️ TÙY CHỌN");
        labelOpt.setTextColor(Color.parseColor("#FFC107")); labelOpt.setTextSize(14);
        labelOpt.setPadding(0, 30, 0, 10); root.addView(labelOpt);

        cbVib = new CheckBox(this);
        cbVib.setText("Rung khi kích hoạt (Haptic)");
        cbVib.setTextColor(Color.WHITE);
        cbVib.setChecked(prefs.getBoolean("widget_" + widgetId + "_vib", true));
        root.addView(cbVib);

        cbAnim = new CheckBox(this);
        cbAnim.setText("Hiệu ứng ánh sáng (Animation)");
        cbAnim.setTextColor(Color.WHITE);
        cbAnim.setChecked(prefs.getBoolean("widget_" + widgetId + "_anim", true));
        root.addView(cbAnim);

        Button save = new Button(this);
        save.setText("LƯU"); save.setTextColor(Color.WHITE);
        save.setBackgroundColor(Color.parseColor("#4CAF50"));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.setMargins(0, 40, 0, 0); save.setLayoutParams(sl);
        save.setOnClickListener(v -> saveAndFinish()); root.addView(save);

        Button cancel = new Button(this);
        cancel.setText("HỦY"); cancel.setTextColor(Color.WHITE);
        cancel.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(0, 20, 0, 0); cancel.setLayoutParams(cl);
        cancel.setOnClickListener(v -> finish()); root.addView(cancel);

        sc.addView(root); setContentView(sc);
    }

    private String buildLabel(String act, String pkg, String scId) {
        String l = labelOf(act);
        if (act.equals("LAUNCH_APP") && !pkg.isEmpty()) l += " → " + pkg;
        if (act.equals("RUN_SHORTCUT") && !scId.isEmpty()) l += " → " + scId;
        return l;
    }

    private String labelOf(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABS[i];
        return key;
    }

    private void picker(boolean isTap) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(isTap ? "Chọn action cho TAP" : "Chọn action cho DOUBLE-TAP")
            .setItems(LABS, (d, w) -> {
                String key = KEYS[w];
                if (key.equals("LAUNCH_APP")) promptPackageName(isTap);
                else if (key.equals("RUN_SHORTCUT")) promptShortcutId(isTap);
                else applyChoice(isTap, key, "", "");
            }).show();
    }

    private void promptPackageName(boolean isTap) {
        EditText et = new EditText(this);
        et.setHint("com.example.app");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(isTap ? tapPkg : dtapPkg);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Nhập package name ứng dụng")
            .setView(et)
            .setPositiveButton("OK", (d, w) ->
                applyChoice(isTap, "LAUNCH_APP", et.getText().toString().trim(), ""))
            .setNegativeButton("HỦY", null).show();
    }

    private void promptShortcutId(boolean isTap) {
        EditText et = new EditText(this);
        et.setHint("shortcut_id (8 ký tự)");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(isTap ? tapScId : dtapScId);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Nhập Shortcut ID đã tạo trong EdgeBar")
            .setView(et)
            .setPositiveButton("OK", (d, w) ->
                applyChoice(isTap, "RUN_SHORTCUT", "", et.getText().toString().trim()))
            .setNegativeButton("HỦY", null).show();
    }

    private void applyChoice(boolean isTap, String act, String pkg, String scId) {
        if (isTap) {
            tapAct = act; tapPkg = pkg; tapScId = scId;
            btnTap.setText(buildLabel(act, pkg, scId));
        } else {
            dtapAct = act; dtapPkg = pkg; dtapScId = scId;
            btnDtap.setText(buildLabel(act, pkg, scId));
        }
    }

    private void saveAndFinish() {
        prefs.edit()
            .putString("widget_" + widgetId + "_tap_act", tapAct)
            .putString("widget_" + widgetId + "_dtap_act", dtapAct)
            .putString("widget_" + widgetId + "_tap_pkg", tapPkg)
            .putString("widget_" + widgetId + "_dtap_pkg", dtapPkg)
            .putString("widget_" + widgetId + "_tap_shortcut_id", tapScId)
            .putString("widget_" + widgetId + "_dtap_shortcut_id", dtapScId)
            .putBoolean("widget_" + widgetId + "_vib", cbVib.isChecked())
            .putBoolean("widget_" + widgetId + "_anim", cbAnim.isChecked())
            .apply();
        Intent upd = new Intent(this, EdgeBarWidgetProvider.class);
        upd.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        upd.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        sendBroadcast(upd);
        Intent res = new Intent();
        res.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, res);
        finish();
    }
}
