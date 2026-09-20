package com.manhmoc.edgebar;

import android.app.Activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình Widget — nay dùng ĐÚNG ngôn ngữ thị giác với mục "Common Settings"
 * của Bubble: mỗi cử chỉ (TAP/DTAP) là 1 card chuẩn (optCol | infoCol | ctrlCol),
 * optCol hiện icon cử chỉ + icon options đang bật (📳✨), ctrlCol có nút TEST,
 * tap vào card mở picker giàu 6 nhóm category (SYSTEM/UTILITIES/APP/SHORTCUT/
 * PANEL/INTENT/MACRO) giống Bubble rule editor.
 *
 * Schema prefs KHÔNG đổi — vẫn giữ đúng "widget_<wid>_<gesture>_act/_pkg/_shortcut_id"
 * để EdgeBarWidgetProvider.java không cần sửa.
 */
public class WidgetConfigActivity extends Activity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private SharedPreferences prefs;
    private LinearLayout gestureBox;

    private static final String[] GESTURE_KEYS = {"tap", "dtap"};
    private static final String[] GESTURE_LABELS = {"TAP", "DOUBLE-TAP"};
    private static final String[] GESTURE_ICONS = {"👆", "✌️"};

    private static final String[] SYS_KEYS = {
        "BACK","HOME","RECENTS","SCREEN_OFF","FLASH","POWER_DIALOG","VOLUME",
        "SCREENSHOT","CAMERA","NOTIFICATIONS","QUICK_SETTINGS","SPLIT_SCREEN",
        "SCREEN_RECORD","AUTO_ROTATE_TOGGLE","TRIGGER_ACC_MENU_2F"
    };
    private static final String[] SYS_LABELS = {
        "Quay lại","Màn chính","Đa nhiệm","Tắt màn hình","Đèn pin","Menu nguồn",
        "Âm lượng","Chụp màn hình","Camera","Thông báo","Cài đặt nhanh",
        "Chia đôi màn","Quay màn hình","Tự động xoay","Giả lập 2 ngón"
    };
    private static final String[] UTL_KEYS = {
        "HIDE_SOME_OVERLAY","SHOW_ALL_OVERLAY","TOGGLE_OVERLAY","TOGGLE_RECORD",
        "PAUSE_RECORD","YTDL_DOWNLOAD","TOGGLE_WORK_PROFILE","OPEN_STORAGE_SCAN",
        "SCAN_QR","PLAY_MY_PLAYLIST"
    };
    private static final String[] UTL_LABELS = {
        "Ẩn một số overlay","Hồi sinh overlay","Bật/tắt Trợ năng",
        "Bật/tắt Ghi âm","Dừng/Tiếp Ghi âm","Tải YTDLnis",
        "Bật/tắt Hồ sơ CV","Quét dung lượng","Quét QR","Phát My Playlist"
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setResult(RESULT_CANCELED);
        Bundle ex = getIntent().getExtras();
        if (ex != null) widgetId = ex.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        buildUI();
    }

    private void buildUI() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.parseColor("#121212"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 80, 30, 30);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("⚡ Cấu hình Edge Bar Widget");
        tvTitle.setTextColor(Color.parseColor("#8AB4F8"));
        tvTitle.setTextSize(18);
        tvTitle.setPadding(0, 0, 0, 8);
        root.addView(tvTitle);

        TextView tvNote = new TextView(this);
        tvNote.setText("• Widget trong suốt 100%\n"
            + "• Chạm 1 lần / 2 lần để gọi hành động\n"
            + "• Nhấn giữ widget → Launcher tự xử lý (kéo/resize/xoá)\n"
            + "• Trượt ngoài widget → OS/Launcher như bình thường");
        tvNote.setTextColor(Color.parseColor("#9AA0A6"));
        tvNote.setTextSize(11.5f);
        tvNote.setPadding(0, 0, 0, 24);
        root.addView(tvNote);

        // ─── GESTURE CARDS ───
        root.addView(createSectionTitle("⚙️ CỬ CHỈ WIDGET"));
        gestureBox = new LinearLayout(this);
        gestureBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(gestureBox);
        renderGestureCards();

        // ─── OPTIONS (per-widget) ───
        root.addView(createSectionTitle("🔧 TÙY CHỌN"));

        CheckBox cbVib = new CheckBox(this);
        cbVib.setText("Rung khi kích hoạt (Haptic)");
        cbVib.setTextColor(Color.WHITE);
        cbVib.setChecked(prefs.getBoolean("widget_" + widgetId + "_vib", true));
        cbVib.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean("widget_" + widgetId + "_vib", c).apply();
            renderGestureCards();
        });
        root.addView(cbVib);
        CheckBox cbSnd = new CheckBox(this);
cbSnd.setText("Âm chạm (Touch Sound)");
cbSnd.setTextColor(Color.WHITE);
cbSnd.setChecked(prefs.getBoolean("widget_" + widgetId + "_snd", false));
cbSnd.setOnCheckedChangeListener((v, c) -> {
    prefs.edit().putBoolean("widget_" + widgetId + "_snd", c).apply();
    renderGestureCards();
});
root.addView(cbSnd);

        CheckBox cbAnim = new CheckBox(this);
        cbAnim.setText("Hiệu ứng ánh sáng (Animation)");
        cbAnim.setTextColor(Color.WHITE);
        cbAnim.setChecked(prefs.getBoolean("widget_" + widgetId + "_anim", true));
        cbAnim.setOnCheckedChangeListener((v, c) -> {
            prefs.edit().putBoolean("widget_" + widgetId + "_anim", c).apply();
            renderGestureCards();
        });
        root.addView(cbAnim);
        
        // ─── FOOTER ───
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(-1, -2);
        fLp.setMargins(0, 40, 0, 0);
        footer.setLayoutParams(fLp);

        Button bCancel = new Button(this);
        bCancel.setText("HỦY");
        bCancel.setBackground(getRounded("#333333", 20f));
        bCancel.setTextColor(Color.WHITE);
        bCancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        bCancel.setOnClickListener(v -> finish());

        Button bSave = new Button(this);
        bSave.setText("LƯU");
        bSave.setBackground(getRounded("#4CAF50", 20f));
        bSave.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.setMargins(20, 0, 0, 0);
        bSave.setLayoutParams(slp);
        bSave.setOnClickListener(v -> saveAndFinish());

        footer.addView(bCancel);
        footer.addView(bSave);
        root.addView(footer);

        sc.addView(root);
        setContentView(sc);
    }

    private void renderGestureCards() {
        if (gestureBox == null) return;
        gestureBox.removeAllViews();
        for (int i = 0; i < GESTURE_KEYS.length; i++) {
            gestureBox.addView(buildGestureCard(
                GESTURE_KEYS[i], GESTURE_LABELS[i], GESTURE_ICONS[i]));
        }
    }

    /**
     * Card chuẩn: optCol (icon cử chỉ + 📳✨) | infoCol (tên + action) | ctrlCol (TEST).
     * Cùng ngôn ngữ với buildStdPackCard của Lenap và buildBubbleRuleCard của Bubble.
     */
    private LinearLayout buildGestureCard(String key, String label, String icon) {
        String px = "widget_" + widgetId + "_" + key;
        String act = prefs.getString(px + "_act", "NONE");
        String pkg = prefs.getString(px + "_pkg", "");
        String scId = prefs.getString(px + "_shortcut_id", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#202124"));
        bg.setCornerRadius(24f);
        card.setBackground(bg);
        card.setPadding(15, 24, 10, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 6, 0, 6);
        card.setLayoutParams(lp);

        // optCol
        LinearLayout optCol = new LinearLayout(this);
        optCol.setOrientation(LinearLayout.VERTICAL);
        optCol.setGravity(Gravity.CENTER);
        optCol.setPadding(0, 0, 15, 0);

        TextView tIcon = new TextView(this);
        tIcon.setText(icon);
        tIcon.setTextSize(26);
        tIcon.setGravity(Gravity.CENTER);
        optCol.addView(tIcon);

        boolean vib = prefs.getBoolean("widget_" + widgetId + "_vib", true);
        boolean anim = prefs.getBoolean("widget_" + widgetId + "_anim", true);
        boolean snd = prefs.getBoolean("widget_" + widgetId + "_snd", false);
        if (vib || anim || snd) {
            TextView tOpts = new TextView(this);
            StringBuilder sb = new StringBuilder();
            if (vib) sb.append("📳");
            if (snd) sb.append("🔊");
            if (anim) sb.append("✨");
            tOpts.setText(sb.toString());
            tOpts.setTextSize(10);
            tOpts.setGravity(Gravity.CENTER);
            tOpts.setPadding(0, 3, 0, 0);
            optCol.addView(tOpts);
        }

        // infoCol
        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tTitle = new TextView(this);
        tTitle.setText(label);
        tTitle.setTextColor(Color.parseColor("#E8EAED"));
        tTitle.setTextSize(15f);
        tTitle.setMaxLines(1);
        tTitle.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(tTitle);

        TextView tAct = new TextView(this);
        tAct.setText(buildActionLabel(act, pkg, scId));
        tAct.setTextColor(Color.parseColor("#8AB4F8"));
        tAct.setTextSize(14f);
        tAct.setPadding(0, 5, 0, 0);
        tAct.setMaxLines(2);
        tAct.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(tAct);

        // ctrlCol
        LinearLayout ctrlCol = new LinearLayout(this);
        ctrlCol.setOrientation(LinearLayout.VERTICAL);
        ctrlCol.setGravity(Gravity.CENTER_HORIZONTAL);

        Button btnTest = new Button(this);
        btnTest.setText("TEST");
        btnTest.setBackground(getRounded("#FFC107", 14f));
        btnTest.setTextColor(Color.BLACK);
        btnTest.setTextSize(11f);
        btnTest.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnTest.setPadding(24, 10, 24, 10);
        btnTest.setMinimumHeight(0);
        btnTest.setMinimumWidth(0);
        btnTest.setOnClickListener(v -> fireTest(key));
        ctrlCol.addView(btnTest);

        card.addView(optCol);
        card.addView(infoCol);
        card.addView(ctrlCol);

        final String fKey = key;
        card.setOnClickListener(v -> openActionPicker(fKey));
        return card;
    }

    private String buildActionLabel(String act, String pkg, String scId) {
        if (act == null || act.isEmpty() || "NONE".equals(act))
            return "Chưa chọn — chạm để đặt";
        if ("LAUNCH_APP".equals(act)) {
            String name = pkg;
            try {
                name = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
            } catch (Exception ignored) {}
            return "📱 " + name;
        }
        if (act.startsWith("RUN_SHORTCUT_")) {
            String id = act.substring("RUN_SHORTCUT_".length());
            return "🔗 " + prefs.getString("shortcut_" + id + "_name", "Shortcut");
        }
        if ("RUN_SHORTCUT".equals(act))
            return "🔗 " + prefs.getString("shortcut_" + scId + "_name", "Shortcut");
        if (act.startsWith("PANEL_"))
            return "📦 " + prefs.getString("pack_panel_" + act.substring(6) + "_name", "Panel");
        if (act.startsWith("INTENT_"))
            return "⚡ " + prefs.getString("intent_" + act.substring(7) + "_name", "Intent");
        if (act.startsWith("MACRO_"))
            return "🤖 " + prefs.getString("macro_" + act.substring(6) + "_name", "Macro");
        for (int i = 0; i < SYS_KEYS.length; i++)
            if (SYS_KEYS[i].equals(act)) return SYS_LABELS[i];
        for (int i = 0; i < UTL_KEYS.length; i++)
            if (UTL_KEYS[i].equals(act)) return UTL_LABELS[i];
        return act;
    }

    /** Picker giàu 6 nhóm category — giống Bubble rule editor. */
    private void openActionPicker(String gestureKey) {
        final String px = "widget_" + widgetId + "_" + gestureKey;
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(30, 80, 30, 30);

        TextView title = new TextView(this);
        title.setText("Chọn hành động cho " +
            (gestureKey.equals("tap") ? "TAP" : "DOUBLE-TAP"));
        title.setTextColor(Color.parseColor("#8AB4F8"));
        title.setTextSize(17);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll);

        Button btnClear = new Button(this);
        btnClear.setText("✖ Bỏ chọn (NONE)");
        btnClear.setBackground(getRounded("#333333", 20f));
        btnClear.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
        cLp.setMargins(0, 0, 0, 16);
        btnClear.setLayoutParams(cLp);
        btnClear.setOnClickListener(v -> {
            prefs.edit()
                .putString(px + "_act", "NONE")
                .remove(px + "_pkg")
                .remove(px + "_shortcut_id")
                .apply();
            renderGestureCards();
            d.dismiss();
        });
        content.addView(btnClear);

        Button btnApp = makeCategoryBtn("📱 CHỌN APP", "#8AB4F8", Color.BLACK);
        btnApp.setOnClickListener(v -> {
            d.dismiss();
            showAppPicker(pkg -> {
                prefs.edit()
                    .putString(px + "_act", "LAUNCH_APP")
                    .putString(px + "_pkg", pkg)
                    .remove(px + "_shortcut_id")
                    .apply();
                renderGestureCards();
            });
        });
        content.addView(btnApp);

        Button btnSc = makeCategoryBtn("🔗 CHỌN SHORTCUT", "#7C4DFF", Color.WHITE);
        btnSc.setOnClickListener(v -> {
            d.dismiss();
            showShortcutPicker((idSc, name) -> {
                prefs.edit()
                    .putString(px + "_act", "RUN_SHORTCUT_" + idSc)
                    .putString(px + "_shortcut_id", idSc)
                    .remove(px + "_pkg")
                    .apply();
                renderGestureCards();
            });
        });
        content.addView(btnSc);

        content.addView(buildCategorySection("⚙️ SYSTEM", SYS_KEYS, SYS_LABELS, px, d));
        content.addView(buildCategorySection("🛠️ UTILITIES", UTL_KEYS, UTL_LABELS, px, d));
        content.addView(buildDynamicSection("🗂️ PANEL", "pack_panel_ids",
            "pack_panel_", "PANEL_", px, d));
        content.addView(buildDynamicSection("⚡ INTENT", "intent_ids",
            "intent_", "INTENT_", px, d));
        content.addView(buildDynamicSection("🤖 MACRO", "macro_ids",
            "macro_", "MACRO_", px, d));

        d.setContentView(root);
        d.show();
    }

    private Button makeCategoryBtn(String text, String bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackground(getRounded(bg, 20f));
        b.setTextColor(fg);
        b.setTextSize(13.5f);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout buildCategorySection(String title, String[] keys,
            String[] labels, String px, Dialog parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(getRounded("#1A1A1A", 16f));
        box.setPadding(30, 20, 30, 20);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.setMargins(0, 0, 0, 12);
        box.setLayoutParams(boxLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#8AB4F8"));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        box.addView(tvTitle);

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setBackground(getRounded("#2C2C2C", 16f));
            b.setTextColor(Color.WHITE);
            b.setTextSize(13f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 4, 0, 4);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                prefs.edit()
                    .putString(px + "_act", key)
                    .remove(px + "_pkg")
                    .remove(px + "_shortcut_id")
                    .apply();
                renderGestureCards();
                parent.dismiss();
            });
            box.addView(b);
        }
        return box;
    }

    private LinearLayout buildDynamicSection(String title, String listKey,
            String namePrefix, String actionPrefix, String px, Dialog parent) {
        List<String> ids = new ArrayList<>();
        String csv = prefs.getString(listKey, "");
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) ids.add(s.trim());
        if (ids.isEmpty()) return new LinearLayout(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(getRounded("#1A1A1A", 16f));
        box.setPadding(30, 20, 30, 20);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(-1, -2);
        boxLp.setMargins(0, 0, 0, 12);
        box.setLayoutParams(boxLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#8AB4F8"));
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, 0, 0, 12);
        box.addView(tvTitle);

        for (String id : ids) {
            final String act = actionPrefix + id;
            String name = prefs.getString(namePrefix + id + "_name",
                prefs.getString(namePrefix + id + "_label", "Item"));
            Button b = new Button(this);
            b.setText(name);
            b.setBackground(getRounded("#2C2C2C", 16f));
            b.setTextColor(Color.WHITE);
            b.setTextSize(13f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 4, 0, 4);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                prefs.edit()
                    .putString(px + "_act", act)
                    .remove(px + "_pkg")
                    .remove(px + "_shortcut_id")
                    .apply();
                renderGestureCards();
                parent.dismiss();
            });
            box.addView(b);
        }
        return box;
    }

    private void showAppPicker(java.util.function.Consumer<String> onPicked) {
        List<String[]> all = new ArrayList<>();
        try {
            android.os.UserManager um = (android.os.UserManager) getSystemService(USER_SERVICE);
            android.content.pm.LauncherApps la = (android.content.pm.LauncherApps) getSystemService(LAUNCHER_APPS_SERVICE);
            for (android.os.UserHandle profile : um.getUserProfiles()) {
                boolean island = !profile.equals(android.os.Process.myUserHandle());
                for (android.content.pm.LauncherActivityInfo info : la.getActivityList(null, profile)) {
                    all.add(new String[]{
                        info.getLabel().toString() + (island ? " [Island]" : ""),
                        info.getApplicationInfo().packageName});
                }
            }
        } catch (Exception ignored) {}
        all.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(30, 80, 30, 30);

        EditText etSearch = new EditText(this);
        etSearch.setHint("Tìm ứng dụng…");
        etSearch.setHintTextColor(Color.GRAY);
        etSearch.setTextColor(Color.WHITE);
        etSearch.setBackground(getRounded("#2C2C2C", 20f));
        etSearch.setPadding(30, 25, 30, 25);
        root.addView(etSearch);

        ListView lv = new ListView(this);
        lv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(lv);

        final List<String[]> shown = new ArrayList<>(all);
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount(){ return shown.size(); }
            public Object getItem(int p){ return shown.get(p); }
            public long getItemId(int p){ return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(WidgetConfigActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(20, 22, 20, 22);
                ImageView iv = new ImageView(WidgetConfigActivity.this);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(80, 80);
                ilp.setMargins(0, 0, 20, 0);
                iv.setLayoutParams(ilp);
                try {
                    iv.setImageDrawable(getPackageManager().getApplicationIcon(shown.get(p)[1]));
                } catch (Exception ignored) {}
                TextView tv = new TextView(WidgetConfigActivity.this);
                tv.setText(shown.get(p)[0]);
                tv.setTextColor(Color.WHITE);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(iv); row.addView(tv);
                return row;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            onPicked.accept(shown.get(pos)[1]);
            d.dismiss();
        });
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                String q = s.toString().trim().toLowerCase();
                shown.clear();
                for (String[] it : all)
                    if (q.isEmpty() || it[0].toLowerCase().contains(q)) shown.add(it);
                adapter.notifyDataSetChanged();
            }
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
        });
        d.setContentView(root);
        d.show();
    }

    private void showShortcutPicker(java.util.function.BiConsumer<String,String> onPicked) {
        List<String> ids = new ArrayList<>();
        String csv = prefs.getString("shortcut_ids", "");
        for (String s : csv.split(",")) if (!s.trim().isEmpty()) ids.add(s.trim());
        if (ids.isEmpty()) {
            Toast.makeText(this, "Chưa có Shortcut nào — tạo trong EdgeBar > Custom Actions trước",
                Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++)
            names[i] = prefs.getString("shortcut_" + ids.get(i) + "_name", "Shortcut");

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Chọn Shortcut")
            .setItems(names, (dd, which) -> onPicked.accept(ids.get(which), names[which]))
            .setNegativeButton("HỦY", null)
            .show();
    }

    private void fireTest(String gestureKey) {
        String px = "widget_" + widgetId + "_" + gestureKey;
        String act = prefs.getString(px + "_act", "NONE");
        if ("NONE".equals(act) || act.isEmpty()) {
            Toast.makeText(this, "Chưa chọn hành động", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        ipc.setPackage(getPackageName());
        if ("LAUNCH_APP".equals(act)) {
            ipc.putExtra("act", "LAUNCH_APP");
            ipc.putExtra("launch_pkg", prefs.getString(px + "_pkg", ""));
        } else if (act.startsWith("RUN_SHORTCUT_")) {
            ipc.putExtra("act", "RUN_SHORTCUT");
            ipc.putExtra("shortcut_id", act.substring("RUN_SHORTCUT_".length()));
        } else if ("RUN_SHORTCUT".equals(act)) {
            ipc.putExtra("act", "RUN_SHORTCUT");
            ipc.putExtra("shortcut_id", prefs.getString(px + "_shortcut_id", ""));
        } else {
            ipc.putExtra("act", act);
        }
        sendBroadcast(ipc);
        Toast.makeText(this, "▶ Đang thử", Toast.LENGTH_SHORT).show();
    }

    private void saveAndFinish() {
        Intent upd = new Intent(this, EdgeBarWidgetProvider.class);
        upd.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        upd.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        sendBroadcast(upd);
        Intent res = new Intent();
        res.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, res);
        finish();
    }

    private GradientDrawable getRounded(String hex, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(hex));
        g.setCornerRadius(r);
        return g;
    }
    private TextView createSectionTitle(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextColor(Color.parseColor("#FFC107"));
        tv.setTextSize(14f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 24, 0, 14);
        return tv;
    }
}
