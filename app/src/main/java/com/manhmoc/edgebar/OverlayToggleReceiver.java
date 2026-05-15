package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.widget.Toast;
public class OverlayToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Intent sInt = new Intent(c, HomescreenService.class);
        if (HomescreenService.isRunning) { c.stopService(sInt); Toast.makeText(c, "Đã TẮT Lớp phủ ADB", Toast.LENGTH_SHORT).show(); } 
        else { if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(sInt); else c.startService(sInt); Toast.makeText(c, "Đã BẬT Lớp phủ ADB", Toast.LENGTH_SHORT).show(); }
    }
}
