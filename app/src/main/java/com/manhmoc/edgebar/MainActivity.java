package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.os.Bundle; import android.view.*; import android.widget.*; import java.util.ArrayList;

public class MainActivity extends Activity {
    private LinearLayout pageConditions, pageEcosystem, pageIntents, pageTiles, pageMacros;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        
        // Navigation Tab
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        Button b1 = new Button(this); b1.setText("ĐIỀU KIỆN"); b1.setOnClickListener(v -> switchTab(1));
        Button b2 = new Button(this); b2.setText("HỆ SINH THÁI"); b2.setOnClickListener(v -> switchTab(2));
        nav.addView(b1); nav.addView(b2); root.addView(nav);

        // Content
        pageConditions = new LinearLayout(this); pageConditions.setOrientation(LinearLayout.VERTICAL); root.addView(pageConditions);
        pageEcosystem = new LinearLayout(this); pageEcosystem.setOrientation(LinearLayout.VERTICAL); pageEcosystem.setVisibility(View.GONE); root.addView(pageEcosystem);
        
        // Ecosystem Inner Tabs
        LinearLayout ecoTabs = new LinearLayout(this);
        Button bI = new Button(this); bI.setText("INTENTS"); bI.setOnClickListener(v -> switchEco(0));
        Button bQ = new Button(this); bQ.setText("QS TILES"); bQ.setOnClickListener(v -> switchEco(1));
        Button bM = new Button(this); bM.setText("MACROS"); bM.setOnClickListener(v -> switchEco(2));
        ecoTabs.addView(bI); ecoTabs.addView(bQ); ecoTabs.addView(bM); pageEcosystem.addView(ecoTabs);

        pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL); pageEcosystem.addView(pageIntents);
        pageTiles = new LinearLayout(this); pageTiles.setOrientation(LinearLayout.VERTICAL); pageTiles.setVisibility(View.GONE); pageEcosystem.addView(pageTiles);
        pageMacros = new LinearLayout(this); pageMacros.setOrientation(LinearLayout.VERTICAL); pageMacros.setVisibility(View.GONE); pageEcosystem.addView(pageMacros);
        
        setContentView(root);
        switchTab(1);
    }
    
    private void switchTab(int tab) {
        pageConditions.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        pageEcosystem.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }
    
    private void switchEco(int tab) {
        pageIntents.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        pageTiles.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        pageMacros.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
    }
}
