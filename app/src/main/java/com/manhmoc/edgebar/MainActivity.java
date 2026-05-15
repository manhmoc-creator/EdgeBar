package com.manhmoc.edgebar;
import android.app.Activity; import android.app.AlertDialog; import android.app.DownloadManager; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.content.SharedPreferences; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.os.Bundle; import android.os.Environment; import android.provider.Settings; import android.net.Uri; import android.view.Gravity; import android.view.View; import android.widget.*; import org.json.JSONObject; import java.util.Iterator; import java.net.HttpURLConnection; import java.net.URL; import java.io.InputStreamReader; import java.io.BufferedReader;

public class MainActivity extends Activity {
    private SharedPreferences prefs; private String[] ACT_KEYS = new String[24]; private String[] ACT_LABS = new String[24];
    private final String[] BARS = {"l", "r", "t_l", "t_r", "t_c"}; private final String[] BAR_NAMES = {"LEFT BOTTOM", "RIGHT BOTTOM", "LEFT TOP", "RIGHT TOP", "CENTER TOP"}; 
    private final String[] GESTURES = {"tap", "dtap", "long", "up", "down", "left", "right", "up_hold", "down_hold", "left_hold", "right_hold"}; 
    private final String[] GESTURE_NAMES = {"Tap", "Double Tap", "Long Press", "Swipe Up", "Swipe Down", "Swipe Left", "Swipe Right", "Up + Hold", "Down + Hold", "Left + Hold", "Right + Hold"};
    private final String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET"};
    private final String[] COLOR_NAMES = {"Pure White", "Neon (Pink-Cyan)", "Cyberpunk (Purple-Gold)", "Lava (Red-Orange)", "Ocean (Blue-Cyan)", "Matrix (Green)", "Sunset (Purple-Orange)"};

    private LinearLayout pageDesign, pageGestures, pageIntents, designSliderContainer; private Button btnNavDes, btnNavGes, btnNavInt;
    private LinearLayout tabBoth, tabLock, tabHome; private Button btnBoth, btnLock, btnHome, btnEditLock, btnEditHome, btnEditAnim;
    private int designTabState = 0; 
    private final String CURRENT_VERSION = "V19.4"; // CẬP NHẬT LÊN 19.4

    private GradientDrawable getRounded(String hexColor, float radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(hexColor)); g.setCornerRadius(radius); return g; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String[] bK = {"NONE", "SCREEN_OFF", "FLASH", "POWER_DIALOG", "VOLUME", "SCREENSHOT", "CAMERA", "QR", "NOTIFICATIONS"}; String[] bL = {"None", "Screen Off", "Flashlight", "Power Menu", "Volume Panel", "Screenshot", "Camera", "Google Lens", "Notifications"};
        for(int i=0; i<9; i++) { ACT_KEYS[i]=bK[i]; ACT_LABS[i]=bL[i]; } for(int i=1; i<=15; i++) { ACT_KEYS[8+i]="INTENT_"+i; ACT_LABS[8+i]="Fire Intent "+i; }

        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.parseColor("#121212")); LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL); main.setPadding(40,40,40,100);
        
        // TITLE & IN-APP UPDATE BUTTON
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0,0,0,30);
        TextView title = new TextView(this); title.setText("⚙️ Edge Bar " + CURRENT_VERSION + "\nThe Automator"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button btnUpdate = new Button(this); btnUpdate.setText("CHECK\nUPDATE"); btnUpdate.setTextColor(Color.parseColor("#00E5FF")); 
        btnUpdate.setBackground(getRounded("#1E1E1E", 25f)); btnUpdate.setPadding(30,20,30,20);
        btnUpdate.setOnClickListener(v -> checkUpdate()); header.addView(title); header.addView(btnUpdate); main.addView(header);
        
        if (!Settings.canDrawOverlays(this)) { Button btnReq = new Button(this); btnReq.setText("⚠️ GRANT OVERLAY PERMISSION"); btnReq.setBackground(getRounded("#D32F2F", 25f)); btnReq.setTextColor(Color.WHITE); btnReq.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))); main.addView(btnReq); }

        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(0, 0, 0, 40);
        btnNavDes = createNavBtn("DESIGN"); btnNavGes = createNavBtn("GESTURES"); btnNavInt = createNavBtn("INTENTS");
        nav.addView(btnNavDes); nav.addView(btnNavGes); nav.addView(btnNavInt); main.addView(nav);
        pageDesign = new LinearLayout(this); pageDesign.setOrientation(LinearLayout.VERTICAL); pageGestures = new LinearLayout(this); pageGestures.setOrientation(LinearLayout.VERTICAL); pageIntents = new LinearLayout(this); pageIntents.setOrientation(LinearLayout.VERTICAL);

        LinearLayout backupRow = new LinearLayout(this); backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnBackup = new Button(this); btnBackup.setText("💾 BACKUP"); btnBackup.setBackground(getRounded("#2E7D32", 20f)); btnBackup.setTextColor(Color.WHITE); 
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f); bp.setMargins(0,0,15,0); btnBackup.setLayoutParams(bp);
        Button btnRestore = new Button(this); btnRestore.setText("📂 RESTORE"); btnRestore.setBackground(getRounded("#EF6C00", 20f)); btnRestore.setTextColor(Color.WHITE); 
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); rp.setMargins(15,0,0,0); btnRestore.setLayoutParams(rp);
        btnBackup.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE, "EdgeBar_Backup.txt"); startActivityForResult(i, 101); });
        btnRestore.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i, 102); });
        backupRow.addView(btnBackup); backupRow.addView(btnRestore); pageDesign.addView(wrapCard(backupRow));

        LinearLayout secSys = new LinearLayout(this); secSys.setOrientation(LinearLayout.VERTICAL);
        secSys.addView(createSection("SYSTEM BEHAVIOR"));
        CheckBox cbKbd = new CheckBox(this); cbKbd.setText("Auto-hide when Gboard is active"); cbKbd.setTextColor(Color.WHITE); cbKbd.setChecked(prefs.getBoolean("avoid_kbd", true)); cbKbd.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("avoid_kbd", c).apply()); secSys.addView(cbKbd);
        secSys.addView(createSection("BLACKLIST (Auto-hide Overlay)")); secSys.addView(createInput("Package Names (comma separated)", "blacklist"));
        CheckBox cbLockC = new CheckBox(this); cbLockC.setText("Enable Corners on LOCK"); cbLockC.setTextColor(Color.WHITE); cbLockC.setChecked(prefs.getBoolean("lock_corner_en", true)); cbLockC.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("lock_corner_en", c).apply()); secSys.addView(cbLockC);
        CheckBox cbHomeC = new CheckBox(this); cbHomeC.setText("Enable Corners on HOME"); cbHomeC.setTextColor(Color.WHITE); cbHomeC.setChecked(prefs.getBoolean("home_corner_en", true)); cbHomeC.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("home_corner_en", c).apply()); secSys.addView(cbHomeC);
        pageDesign.addView(wrapCard(secSys));

        LinearLayout secVisual = new LinearLayout(this); secVisual.setOrientation(LinearLayout.VERTICAL);
        secVisual.addView(createSection("VISUAL TUNING"));
        LinearLayout toggleRow = new LinearLayout(this); toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        btnEditLock = new Button(this); btnEditLock.setText("LOCK"); btnEditLock.setLayoutParams(bp);
        btnEditHome = new Button(this); btnEditHome.setText("HOME"); 
        LinearLayout.LayoutParams mP = new LinearLayout.LayoutParams(0, -2, 1f); mP.setMargins(10,0,10,0); btnEditHome.setLayoutParams(mP);
        btnEditAnim = new Button(this); btnEditAnim.setText("ANIMA"); btnEditAnim.setLayoutParams(rp);
        designSliderContainer = new LinearLayout(this); designSliderContainer.setOrientation(LinearLayout.VERTICAL); designSliderContainer.setPadding(0,20,0,0);
        
        btnEditLock.setOnClickListener(v -> { designTabState=0; updateVisTabs(); renderSliders(); });
        btnEditHome.setOnClickListener(v -> { designTabState=1; updateVisTabs(); renderSliders(); });
        btnEditAnim.setOnClickListener(v -> { designTabState=2; updateVisTabs(); renderSliders(); });
        toggleRow.addView(btnEditLock); toggleRow.addView(btnEditHome); toggleRow.addView(btnEditAnim); secVisual.addView(toggleRow); secVisual.addView(designSliderContainer);
        pageDesign.addView(wrapCard(secVisual)); btnEditLock.performClick();

        LinearLayout tabContainer = new LinearLayout(this); tabContainer.setOrientation(LinearLayout.HORIZONTAL); tabContainer.setPadding(0, 20, 0, 20); 
        btnBoth = createTabBtn("BOTH"); btnLock = createTabBtn("LOCK"); btnHome = createTabBtn("HOME");
        btnBoth.setLayoutParams(bp); btnLock.setLayoutParams(mP); btnHome.setLayoutParams(rp); tabContainer.addView(btnBoth); tabContainer.addView(btnLock); tabContainer.addView(btnHome); pageGestures.addView(tabContainer);
        tabBoth = createConfigPage("both"); tabLock = createConfigPage("lock"); tabHome = createConfigPage("home"); pageGestures.addView(tabBoth); pageGestures.addView(tabLock); pageGestures.addView(tabHome); btnBoth.setOnClickListener(v -> switchGesTab(0)); btnLock.setOnClickListener(v -> switchGesTab(1)); btnHome.setOnClickListener(v -> switchGesTab(2)); switchGesTab(0);

        for (int i = 1; i <= 15; i++) { LinearLayout sInt = new LinearLayout(this); sInt.setOrientation(LinearLayout.VERTICAL); sInt.addView(createSection("Intent Slot " + i)); sInt.addView(createInput("Action", "i"+i+"_act")); sInt.addView(createInput("Package", "i"+i+"_pkg")); sInt.addView(createInput("Class Name", "i"+i+"_cls")); sInt.addView(createInput("Data URI", "i"+i+"_data")); sInt.addView(createInput("Categories", "i"+i+"_cat")); sInt.addView(createInput("Flags", "i"+i+"_flags")); CheckBox cb = new CheckBox(this); cb.setText("Send as Broadcast"); cb.setTextColor(Color.WHITE); cb.setChecked(prefs.getBoolean("i"+i+"_br", true)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean("i"+idx+"_br", c).apply()); sInt.addView(cb); pageIntents.addView(wrapCard(sInt)); }
        main.addView(pageDesign); main.addView(pageGestures); main.addView(pageIntents); btnNavDes.setOnClickListener(v->switchMainTab(0)); btnNavGes.setOnClickListener(v->switchMainTab(1)); btnNavInt.setOnClickListener(v->switchMainTab(2)); switchMainTab(1);
        scroll.addView(main); setContentView(scroll);
    }
    
    private void updateVisTabs() {
        btnEditLock.setBackground(getRounded(designTabState==0 ? "#00E5FF" : "#222222", 20f)); btnEditLock.setTextColor(designTabState==0 ? Color.BLACK : Color.WHITE);
        btnEditHome.setBackground(getRounded(designTabState==1 ? "#00E5FF" : "#222222", 20f)); btnEditHome.setTextColor(designTabState==1 ? Color.BLACK : Color.WHITE);
        btnEditAnim.setBackground(getRounded(designTabState==2 ? "#00E5FF" : "#222222", 20f)); btnEditAnim.setTextColor(designTabState==2 ? Color.BLACK : Color.WHITE);
    }

    private void checkUpdate() {
        Toast.makeText(this, "Checking GitHub...", Toast.LENGTH_SHORT).show();
        new Thread(() -> { try {
            URL url = new URL("https://api.github.com/repos/manhmoc-creator/EdgeBar/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(); conn.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line; while((line=reader.readLine())!=null) sb.append(line); reader.close();
            JSONObject json = new JSONObject(sb.toString()); String latestTag = json.getString("tag_name"); 
            String downloadUrl = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
            runOnUiThread(() -> {
                if(!latestTag.contains(CURRENT_VERSION)) {
                    new AlertDialog.Builder(this).setTitle("Update Available!").setMessage("Latest version is " + latestTag + ".\nDownload & Install now?").setPositiveButton("YES", (d,w) -> downloadAndInstall(downloadUrl)).setNegativeButton("NO", null).show();
                } else { Toast.makeText(this, "You are on the latest version!", Toast.LENGTH_LONG).show(); }
            });
        } catch(Exception e) { runOnUiThread(() -> Toast.makeText(this, "Check failed. Please visit GitHub manually.", Toast.LENGTH_LONG).show()); } }).start();
    }

    private void downloadAndInstall(String url) {
        Toast.makeText(this, "Downloading in background...", Toast.LENGTH_LONG).show();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EdgeBar_Update.apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    try {
                        Uri apkUri = manager.getUriForDownloadedFile(downloadId);
                        Intent install = new Intent(Intent.ACTION_VIEW); install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(install);
                    } catch (Exception e) { Toast.makeText(MainActivity.this, "Download complete. Please open your File Manager to install.", Toast.LENGTH_LONG).show(); }
                    try { unregisterReceiver(this); } catch(Exception e){}
                }
            }
        };
        if(android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override public void onActivityResult(int req, int res, Intent data) { super.onActivityResult(req, res, data); if(res == RESULT_OK && data != null && data.getData() != null) { try { if(req == 101) { java.io.OutputStream os = getContentResolver().openOutputStream(data.getData()); os.write(new JSONObject(prefs.getAll()).toString().getBytes()); os.close(); Toast.makeText(this, "Backup Saved!", Toast.LENGTH_SHORT).show(); } else if(req == 102) { java.io.InputStream is = getContentResolver().openInputStream(data.getData()); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is)); StringBuilder s = new StringBuilder(); String line; while((line=r.readLine())!=null) s.append(line); r.close(); JSONObject j = new JSONObject(s.toString()); SharedPreferences.Editor ed = prefs.edit(); Iterator<String> k = j.keys(); while(k.hasNext()) { String key = k.next(); Object v = j.get(key); if(v instanceof Boolean) ed.putBoolean(key, (Boolean)v); else if (v instanceof Integer) ed.putInt(key, (Integer)v); else if (v instanceof String) ed.putString(key, (String)v); } ed.apply(); Toast.makeText(this, "Restored! Restart Edge Bar to apply.", Toast.LENGTH_LONG).show(); } } catch(Exception e) { Toast.makeText(this, "File Error", Toast.LENGTH_LONG).show(); } } }

    private void renderSliders() { designSliderContainer.removeAllViews(); if(designTabState == 2) { LinearLayout lC = new LinearLayout(this); lC.setOrientation(LinearLayout.HORIZONTAL); lC.setPadding(0,10,0,10); TextView tC = new TextView(this); tC.setText("Theme:"); tC.setTextColor(Color.WHITE); tC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sC = createSpinner(); sC.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, COLOR_NAMES)); String curC = prefs.getString("anim_color", "WHITE"); for(int i=0;i<COLOR_KEYS.length;i++) if(COLOR_KEYS[i].equals(curC)) sC.setSelection(i); sC.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString("anim_color",COLOR_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); sC.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lC.addView(tC); lC.addView(sC); designSliderContainer.addView(lC); LinearLayout lS = new LinearLayout(this); lS.setOrientation(LinearLayout.HORIZONTAL); lS.setPadding(0,10,0,10); TextView tS = new TextView(this); tS.setText("Run Style:"); tS.setTextColor(Color.WHITE); tS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner sS = createSpinner(); sS.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Fade (Blink)", "Running Light (25%)", "Running Light (50%)"})); sS.setSelection(prefs.getInt("anim_style", 0)); sS.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putInt("anim_style", pos).apply();}public void onNothingSelected(AdapterView<?> p){}}); sS.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); lS.addView(tS); lS.addView(sS); designSliderContainer.addView(lS); designSliderContainer.addView(createSlider("Stroke Thickness", "anim_thick", 50, 12)); designSliderContainer.addView(createSlider("Corner Radius", "anim_rad", 100, 40)); designSliderContainer.addView(createSlider("Duration (ms)", "anim_dur", 3000, 1500)); } else { String prefix = designTabState == 1 ? "home_" : "lock_"; for(int i=0; i<5; i++) { CheckBox cb = new CheckBox(this); cb.setText("ENABLE: " + BAR_NAMES[i]); cb.setTextColor(Color.parseColor("#4CAF50")); cb.setChecked(prefs.getBoolean(prefix+BARS[i]+"_en", i < 2)); final int idx = i; cb.setOnCheckedChangeListener((v,c) -> prefs.edit().putBoolean(prefix+BARS[idx]+"_en", c).apply()); designSliderContainer.addView(cb); designSliderContainer.addView(createSlider("Opacity", prefix+BARS[i]+"_alpha", 255, 50)); designSliderContainer.addView(createSlider("Width", prefix+BARS[i]+"_w", 1400, 300)); designSliderContainer.addView(createSlider("Height", prefix+BARS[i]+"_h", 1400, 60)); designSliderContainer.addView(createSlider("Pos X", prefix+BARS[i]+"_x", 1000, 0)); designSliderContainer.addView(createSlider("Pos Y", prefix+BARS[i]+"_y", 1000, 0)); } } }

    private LinearLayout wrapCard(View content) { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(getRounded("#1E1E1E", 40f)); card.setPadding(40,40,40,40); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,40); card.setLayoutParams(lp); card.addView(content); return card; }
    private void switchMainTab(int idx) { pageDesign.setVisibility(idx==0?View.VISIBLE:View.GONE); pageGestures.setVisibility(idx==1?View.VISIBLE:View.GONE); pageIntents.setVisibility(idx==2?View.VISIBLE:View.GONE); btnNavDes.setBackground(getRounded(idx==0?"#222222":"#00000000", 20f)); btnNavDes.setTextColor(idx==0?Color.parseColor("#00E5FF"):Color.GRAY); btnNavGes.setBackground(getRounded(idx==1?"#222222":"#00000000", 20f)); btnNavGes.setTextColor(idx==1?Color.parseColor("#00E5FF"):Color.GRAY); btnNavInt.setBackground(getRounded(idx==2?"#222222":"#00000000", 20f)); btnNavInt.setTextColor(idx==2?Color.parseColor("#00E5FF"):Color.GRAY); }
    private void switchGesTab(int idx) { tabBoth.setVisibility(idx==0?View.VISIBLE:View.GONE); tabLock.setVisibility(idx==1?View.VISIBLE:View.GONE); tabHome.setVisibility(idx==2?View.VISIBLE:View.GONE); btnBoth.setBackground(getRounded(idx==0?"#00E5FF":"#222222", 20f)); btnBoth.setTextColor(idx==0?Color.BLACK:Color.WHITE); btnLock.setBackground(getRounded(idx==1?"#00E5FF":"#222222", 20f)); btnLock.setTextColor(idx==1?Color.BLACK:Color.WHITE); btnHome.setBackground(getRounded(idx==2?"#00E5FF":"#222222", 20f)); btnHome.setTextColor(idx==2?Color.BLACK:Color.WHITE); }
    private Button createNavBtn(String t) { Button b = new Button(this); b.setText(t); b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f)); return b; }
    private Button createTabBtn(String t) { Button b = new Button(this); b.setText(t); return b; }
    
    private LinearLayout createSlider(String t, String k, int max, int def) { 
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setTextColor(Color.WHITE); tv.setText(t + ": " + prefs.getInt(k, def)); l.addView(tv);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        Button btnMinus = new Button(this); btnMinus.setText("-"); btnMinus.setTextColor(Color.parseColor("#BBBBBB")); btnMinus.setBackgroundColor(Color.TRANSPARENT); btnMinus.setTextSize(20);
        Button btnPlus = new Button(this); btnPlus.setText("+"); btnPlus.setTextColor(Color.parseColor("#BBBBBB")); btnPlus.setBackgroundColor(Color.TRANSPARENT); btnPlus.setTextSize(20);
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(prefs.getInt(k, def)); sb.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s, int p, boolean b){ tv.setText(t + ": " + p); prefs.edit().putInt(k, p).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} }); 
        btnMinus.setOnClickListener(v -> { int p = sb.getProgress(); if(p>0) sb.setProgress(p-1); }); btnPlus.setOnClickListener(v -> { int p = sb.getProgress(); if(p<max) sb.setProgress(p+1); });
        row.addView(btnMinus); row.addView(sb); row.addView(btnPlus); l.addView(row); return l; 
    }

    private LinearLayout createConfigPage(String prefix) { LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); for(int i=0; i<5; i++) { LinearLayout sec = new LinearLayout(this); sec.setOrientation(LinearLayout.VERTICAL); sec.addView(createSection(BAR_NAMES[i])); for(int j=0; j<GESTURES.length; j++) sec.addView(createRow(GESTURE_NAMES[j], prefix + "_" + BARS[i] + "_" + GESTURES[j])); page.addView(wrapCard(sec)); } LinearLayout secC = new LinearLayout(this); secC.setOrientation(LinearLayout.VERTICAL); secC.addView(createSection("ASSISTANT CORNERS")); secC.addView(createRow("Left Corner - Swipe", prefix+"_l_corner")); secC.addView(createRow("Right Corner - Swipe", prefix+"_r_corner")); page.addView(wrapCard(secC)); return page; }
    private TextView createSection(String s) { TextView tv = new TextView(this); tv.setText(s); tv.setTextColor(Color.parseColor("#00E5FF")); tv.setPadding(0,10,0,20); return tv; }
    private Spinner createSpinner() { Spinner sp = new Spinner(this); sp.setBackground(getRounded("#2C2C2C", 20f)); sp.setPadding(20,20,20,20); return sp; }
    private EditText createInput(String h, String k) { EditText et = new EditText(this); et.setHint(h); et.setHintTextColor(Color.GRAY); et.setTextColor(Color.WHITE); et.setText(prefs.getString(k,"")); et.setBackground(getRounded("#2C2C2C", 20f)); et.setPadding(30,30,30,30); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,10,0,10); et.setLayoutParams(lp); et.addTextChangedListener(new android.text.TextWatcher(){public void afterTextChanged(android.text.Editable s){prefs.edit().putString(k,s.toString()).apply();}public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}}); return et; }
    private LinearLayout createRow(String t, String k) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setPadding(0,10,0,10); TextView tv = new TextView(this); tv.setText(t); tv.setTextColor(Color.WHITE); tv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f)); Spinner s = createSpinner(); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ACT_LABS)); String v = prefs.getString(k,"NONE"); for(int i=0;i<ACT_KEYS.length;i++) if(ACT_KEYS[i].equals(v)) s.setSelection(i); s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p, View v, int pos, long id){prefs.edit().putString(k,ACT_KEYS[pos]).apply();}public void onNothingSelected(AdapterView<?> p){}}); s.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1.5f)); CheckBox animCb = new CheckBox(this); animCb.setText("✨"); animCb.setTextColor(Color.YELLOW); animCb.setChecked(prefs.getBoolean(k+"_anim", true)); animCb.setOnCheckedChangeListener((v_,c)->prefs.edit().putBoolean(k+"_anim", c).apply()); l.addView(tv); l.addView(s); l.addView(animCb); return l; }
}
