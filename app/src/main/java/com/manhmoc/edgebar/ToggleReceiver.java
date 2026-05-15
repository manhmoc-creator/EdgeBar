package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.provider.Settings; import android.widget.Toast;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try { String srv = "com.manhmoc.edgebar.v17/com.manhmoc.edgebar.EdgeBarService"; String en = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); if (en == null) en = ""; boolean isEn = en.contains(srv);
            if (isEn) { en = en.replace(srv, "").replace("::", ":"); if (en.endsWith(":")) en = en.substring(0, en.length() - 1); } else { en = en.isEmpty() ? srv : en + ":" + srv; Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1); }
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, en); Toast.makeText(c, isEn ? "Đã TẮT Trợ Năng" : "Đã BẬT Trợ Năng", Toast.LENGTH_SHORT).show();
            if (Settings.canDrawOverlays(c) && !HomescreenService.isRunning) { Intent sInt = new Intent(c, HomescreenService.class); if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(sInt); else c.startService(sInt); }
        } catch (Exception e) { Toast.makeText(c, "LỖI ADB", Toast.LENGTH_LONG).show(); }
    }
}
