package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.graphics.*; import android.graphics.drawable.*; import android.os.Bundle; import android.view.*; import android.widget.*;

public class MainActivity extends Activity {
    private LinearLayout pageConditions, pageIntents, pageTiles, pageMacros;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        
        // Tab Chính
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        Button b1 = new Button(this); b1.setText("ĐIỀU KIỆN"); b1.setOnClickListener(v -> switchTab(1));
        Button b2 = new Button(this); b2.setText("HỆ SINH THÁI"); b2.setOnClickListener(v -> switchTab(2));
        nav.addView(b1); nav.addView(b2); root.addView(nav);

        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); root.addView(pageConditions);
        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL); pageIntents.setVisibility(View.GONE); root.addView(pageIntents);
        pageTiles = new LinearLayout(this); pageTiles.setOrientation(LinearLayout.VERTICAL); pageTiles.setVisibility(View.GONE); root.addView(pageTiles);
        pageMacros = new LinearLayout(this); pageMacros.setOrientation(LinearLayout.VERTICAL); pageMacros.setVisibility(View.GONE); root.addView(pageMacros);
        
        // YTDL Fix
        EditText et = new EditText(this); et.setHint("Nhập link..."); root.addView(et);
        Button btnYt = new Button(this); btnYt.setText("YTDL"); 
        btnYt.setOnClickListener(v -> { 
            Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, et.getText().toString()); 
            i.setPackage("com.deniscerri.ytdl"); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION); 
            try { startActivity(i); } catch(Exception e) { Toast.makeText(this, "YTDLnis không tồn tại", Toast.LENGTH_SHORT).show(); }
        }); root.addView(btnYt);

        setContentView(root);
    }
    private void switchTab(int t) {
        pageConditions.setVisibility(t == 1 ? View.VISIBLE : View.GONE);
        pageIntents.setVisibility(t == 2 ? View.VISIBLE : View.GONE);
    }
}
