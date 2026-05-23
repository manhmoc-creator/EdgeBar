package com.manhmoc.edgebar;
import android.Manifest; import android.app.Activity; import android.app.AlertDialog; import android.app.Dialog; import android.content.ClipData; import android.content.ClipboardManager; import android.content.ComponentName; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.content.pm.PackageManager; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.provider.Settings; import android.net.Uri; import android.text.TextUtils; import android.view.Gravity; import android.view.View; import android.widget.*; import java.util.ArrayList;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private boolean isVi;
    private String T(String en, String vi) { return isVi ? vi : en; }
    private String[] ACT_KEYS = new String[35]; private String[] ACT_LABS = new String[35];
    private LinearLayout pageDesign, pageConditions, pageIntents, pageTiles, pageMacros, listRules, designSliderContainer, navMain; 
    private Button fabRule, btnUpdate;

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }
    
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        reloadActionLabels();
        
        RelativeLayout root = new RelativeLayout(this); root.setBackgroundColor(Color.parseColor("#121212"));
        // Giao diện đã tối ưu: Gom 3 nút I/QS/M vào một hàng
        LinearLayout ecoBar = new LinearLayout(this); ecoBar.setOrientation(LinearLayout.HORIZONTAL);
        Button btnI = new Button(this); btnI.setText("INTENTS"); btnI.setOnClickListener(v -> showIntentEditor(-1));
        Button btnQ = new Button(this); btnQ.setText("QS TILES"); btnQ.setOnClickListener(v -> showQsEditor(-1));
        Button btnM = new Button(this); btnM.setText("MACROS"); btnM.setOnClickListener(v -> showMacroEditor(-1));
        ecoBar.addView(btnI); ecoBar.addView(btnQ); ecoBar.addView(btnM);
        root.addView(ecoBar);
        setContentView(root);
    }

    private void reloadActionLabels() {
        String[] bK = {"NONE", "BACK", "HOME", "RECENTS", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "NOTIFICATIONS", "TOGGLE_ACC", "TOGGLE_OVERLAY", "YTDL_DOWNLOAD", "VOICE_RECORD"}; 
        String[] bL = {"None", "Back", "Home", "Recents", "Screen Off", "Flash", "Power", "Volume", "Screenshot", "Camera", "Notifications", "Acc", "Overlay", "YTDL", "Recorder"};
        for(int i=0; i<15; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } 
    }
    
    private void showIntentEditor(int slot) { /* Logic Dialog Intent */ }
    private void showQsEditor(int slot) { /* Logic Dialog QS */ }
    private void showMacroEditor(int slot) { /* Logic Dialog Macro */ }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); return et; }
    private Spinner createSpinner() { return new Spinner(this); }
}
