package com.manhmoc.edgebar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;

/** [MỚI] Trung chuyển cho App Shortcuts (giữ icon Edge Bar ngoài Home).
 *  4 slot cố định, mỗi slot user gán 1 action Homeb-compatible trong
 *  Frontier > ngăn kéo "Cử chỉ Icon App". Activity này KHÔNG hiện UI —
 *  đọc slot -> bắn IPC_ACTION -> finish() ngay, Zero-RAM sau khi chạy xong. */
public class ShortcutTrampolineActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String shortcutId = getIntent().getStringExtra(Intent.EXTRA_SHORTCUT_ID);
        // Android 7.1-7.x không có EXTRA_SHORTCUT_ID cho static shortcut khi mở qua
        // ACTION_VIEW trực tiếp -> fallback đọc từ ShortcutManager reportShortcutUsed API
        // thực tế trên các máy hiện đại (minSdk 26) luôn có EXTRA_SHORTCUT_ID.
        if (shortcutId == null) shortcutId = "";

        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        String act = prefs.getString("appicon_" + shortcutId + "_act", "NONE");

        if (!act.equals("NONE") && !act.isEmpty()) {
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
