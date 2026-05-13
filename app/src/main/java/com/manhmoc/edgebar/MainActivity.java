package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final String[] ACT_KEYS = {"NONE", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "QR", "NOTIFICATIONS", "INTENT_1", "INTENT_2", "INTENT_3"};
    private final String[] ACT_LABS = {"Không có", "Tắt màn hình", "Đèn pin", "Menu nguồn", "Âm lượng", "Chụp màn hình", "Camera an toàn", "Google Lens (QR)", "Thông báo", "Gửi Intent 1", "Gửi Intent 2", "Gửi Intent 3"};
    
    private LinearLayout tabBoth, tabLock, tabHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.parseColor("#121212"));
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,40,40,100);

        TextView title = new TextView(this); title.setText("⚙️ EdgeBar v9 - Endgame"); title.setTextColor(Color.WHITE); title.setTextSize(24); title.setPadding(0,0,0,40); main.addView(title);

        // Nút chọn Chế độ chạy
        main.addView(createSection("CHỌN ĐIỀU KIỆN HOẠT ĐỘNG CHÍNH"));
        Spinner mode = createSpinner(); mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Áp dụng Cả hai", "Chỉ chạy Lockscreen", "Chỉ chạy Homescreen"}));
        mode.setSelection(prefs.getInt("master_mode", 0));
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { prefs.edit().putInt("master_mode", pos).apply(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        main.addView(mode);

        // Thiết kế Tab
        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 40, 0, 20);
        Button btnBoth = createTabBtn("CẢ HAI"); Button btnLock = createTabBtn("LOCKSCREEN"); Button btnHome = createTabBtn("HOMESCREEN");
        tabContainer.addView(btnBoth); tabContainer.addView(btnLock); tabContainer.addView(btnHome);
        main.addView(tabContainer);

        tabBoth = createConfigPage("both"); tabLock = createConfigPage("lock"); tabHome = createConfigPage("home");
        main.addView(tabBoth); main.addView(tabLock); main.addView(tabHome);

        // Logic chuyển Tab
        btnBoth.setOnClickListener(v -> switchTab(0));
        btnLock.setOnClickListener(v -> switchTab(1));
        btnHome.setOnClickListener(v -> switchTab(2));
        switchTab(0); // Mặc định mở tab 1

        // Cấu hình Intent 1, 2, 3
        main.addView(createSection("🔧 CẤU HÌNH INTENT ENGINE"));
        for (int i = 1; i <= 3; i++) {
            main.addView(createSection("Slot Intent " + i));
            main.addView(createInput("Action (VD: android.intent.action.VIEW)", "i"+i+"_act"));
            main.addView(createInput("Package (VD: com.google.android.apps.maps)", "i"+i+"_pkg"));
            main.addView(createInput("Class Name", "i"+i+"_cls"));
            main.addView(createInput("Data URI", "i"+i+"_data"));
            main.addView(createInput("Categories", "i"+i+"_cat"));
            main.addView(createInput("Flags (Số Integer)", "i"+i+"_flags"));
            CheckBox cb = new CheckBox(this); cb.setText("Gửi dạng Broadcast Receiver"); cb.setTextColor(Color.WHITE);
            cb.setChecked(prefs.getBoolean("i"+i+"_br", true));
            final int idx = i;
            cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("i"+idx+"_br", c).apply());
            main.addView(cb);
        }

        scroll.addView(main); setContentView(scroll);
    }

    private void switchTab(int index) {
        tabBoth.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabLock.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabHome.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    private Button createTabBtn(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        b.setBackgroundColor(Color.parseColor("#333333")); return b;
    }

    private LinearLayout createConfigPage(String prefix) {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.addView(createSection("CỬ CHỈ TRÁI (" + prefix.toUpperCase() + ")"));
        page.addView(createRow("1 Chạm", prefix+"_l_tap")); page.addView(createRow("2 Chạm", prefix+"_l_dtap"));
        page.addView(createRow("Nhấn giữ", prefix+"_l_long")); page.addView(createRow("Vuốt Lên", prefix+"_l_up"));
        page.addView(createRow("Vuốt Xuống", prefix+"_l_down")); page.addView(createRow("Vuốt Trái", prefix+"_l_left"));
        page.addView(createRow("Vuốt Phải", prefix+"_l_right"));

        page.addView(createSection("CỬ CHỈ PHẢI (" + prefix.toUpperCase() + ")"));
        page.addView(createRow("1 Chạm", prefix+"_r_tap")); page.addView(createRow("2 Chạm", prefix+"_r_dtap"));
        page.addView(createRow("Nhấn giữ", prefix+"_r_long")); page.addView(createRow("Vuốt Lên", prefix+"_r_up"));
        page.addView(createRow("Vuốt Xuống", prefix+"_r_down")); page.addView(createRow("Vuốt Trái", prefix+"_r_left"));
        page.addView(createRow("Vuốt Phải", prefix+"_r_right"));
        return page;
    }

    private TextView createSection(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.GREEN); tv.setPadding(0,40,0,10); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); GradientDrawable g = new GradientDrawable(); g.setColor(Color.DKGRAY); g.setCornerRadius(10); sp.setBackground(g); sp.setPadding(10,10,10,10); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    
    private LinearLayout createRow(String t, String k) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,10,0,10);
        TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS));
        String v = prefs.getString(k,"NONE"); for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(v)) s.setSelection(i);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString(k,ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}});
        s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); l.addView(tv); l.addView(s); return l;
    }
}
