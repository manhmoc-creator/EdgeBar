package com.manhmoc.edgebar;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private final String[] ACT_KEYS = {"NONE", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "QR", "NOTIFICATIONS", "INTENT"};
    private final String[] ACT_LABS = {"Không có", "Tắt màn hình", "Đèn pin", "Menu nguồn", "Âm lượng", "Chụp màn hình", "Camera an toàn", "Quét QR", "Thông báo", "Intent tùy chỉnh"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.BLACK);
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,40,40,100);
        
        TextView t = new TextView(this); t.setText("EdgeBar v8 Settings"); t.setTextColor(Color.WHITE); t.setTextSize(24); t.setPadding(0,0,0,40); main.addView(t);
        
        main.addView(createSection("ĐIỀU KIỆN HOẠT ĐỘNG"));
        Spinner mode = createSpinner(); mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Cả hai", "Chỉ Lockscreen", "Chỉ Homescreen"}));
        mode.setSelection(prefs.getInt("mode", 0));
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) { prefs.edit().putInt("mode", pos).apply(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        main.addView(mode);

        main.addView(createSection("CỬ CHỈ TRÁI / PHẢI"));
        main.addView(createRow("Trái - 1 Chạm", "l_tap")); main.addView(createRow("Trái - 2 Chạm", "l_dtap"));
        main.addView(createRow("Phải - 1 Chạm", "r_tap")); main.addView(createRow("Phải - 2 Chạm", "r_dtap"));

        main.addView(createSection("INTENT ENGINE CONFIG"));
        main.addView(createInput("Action (e.g. android.intent.action.VIEW)", "i_act"));
        main.addView(createInput("Package (e.g. com.google.android.apps.maps)", "i_pkg"));
        main.addView(createInput("Data URI", "i_data"));
        CheckBox cb = new CheckBox(this); cb.setText("Gửi dạng Broadcast"); cb.setTextColor(Color.WHITE);
        cb.setChecked(prefs.getBoolean("i_br", true));
        cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("i_br", c).apply());
        main.addView(cb);

        scroll.addView(main); setContentView(scroll);
    }
    private TextView createSection(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.GREEN); tv.setPadding(0,40,0,10); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); GradientDrawable g = new GradientDrawable(); g.setColor(Color.DKGRAY); g.setCornerRadius(10); sp.setBackground(g); sp.setPadding(10,10,10,10); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout createRow(String t, String k) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,10,0,10);
        TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS));
        String v = prefs.getString(k,"NONE"); for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(v)) s.setSelection(i);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id){prefs.edit().putString(k,ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}});
        s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); l.addView(tv); l.addView(s); return l;
    }
}
