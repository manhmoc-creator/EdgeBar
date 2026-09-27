package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
public class ShortcutTrampolineActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
String shortcutId = getIntent().getStringExtra("eb_shortcut_id");
if (shortcutId == null) shortcutId = getIntent().getStringExtra(Intent.EXTRA_SHORTCUT_ID); // dự phòng
if (shortcutId == null) shortcutId = "";

        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String act = prefs.getString("appicon_" + shortcutId + "_act", "NONE");

        if (!act.equals("NONE") && !act.isEmpty()) {
    if (act.equals("OPEN_APP_UI")) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(open);
    } else {
        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        if (act.equals("LAUNCH_APP")) {
            ipc.putExtra("act", "LAUNCH_APP");
            ipc.putExtra("launch_pkg", prefs.getString("appicon_" + shortcutId + "_launch_pkg", ""));
        } else if (act.startsWith("RUN_SHORTCUT_")) {
            ipc.putExtra("act", "RUN_SHORTCUT");
            ipc.putExtra("shortcut_id", act.substring("RUN_SHORTCUT_".length()));
        } else {
            ipc.putExtra("act", act);
        }
        sendBroadcast(ipc);
    }

    if (Build.VERSION.SDK_INT >= 25) {
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm != null) sm.reportShortcutUsed(shortcutId);
        } catch (Exception ignored) {}
    }
}
        finish();
        overridePendingTransition(0, 0);
    }
}
