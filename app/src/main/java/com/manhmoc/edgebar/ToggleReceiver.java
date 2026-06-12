package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.provider.Settings; import android.os.Build;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action = i.getAction();
        if ("com.manhmoc.edgebar.TOGGLE_LOCK".equals(action)) {
            toggleAcc(c, c.getPackageName() + "/" + EdgeBarService.class.getName());
        } else if ("com.manhmoc.edgebar.TOGGLE_HOME".equals(action)) {
            Intent s = new Intent(c, HomescreenService.class);
            if (HomescreenService.isRunning) c.stopService(s);
            else if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } else if ("com.manhmoc.edgebar.TOGGLE_MACRO".equals(action)) {
            String list = i.getStringExtra("services");
            if (list != null && !list.isEmpty()) {
                String[] svcs = list.split(",");
                String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (cur == null) cur = "";
                for (String s : svcs) {
                    String mySvc = s.trim(); if (mySvc.isEmpty()) continue;
                    boolean en = cur.contains(mySvc);
                    if (en) cur = cur.replace(":" + mySvc, "").replace(mySvc + ":", "").replace(mySvc, "");
                    else cur = cur.isEmpty() ? mySvc : cur + ":" + mySvc;
                }
                try { Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cur); Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1"); } catch (Exception e) {}
            }
        } else if ("com.manhmoc.edgebar.TOGGLE_DUAL_HOME".equals(action)) {
            android.content.SharedPreferences p = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
            boolean oldHomeOn = p.getBoolean("shortcut_home_on", false);
            
            if (oldHomeOn) {
                // Luân phiên: Tắt hiển thị cũ, kích hoạt hiển thị Trợ năng mới
                p.edit().putBoolean("shortcut_home_on", false)
                        .putBoolean("shortcut_acc_home_on", true).apply();
                if (isAccEnabled(c)) {
                    Intent intentAcc = new Intent(c, AccessibleHomeService.class);
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(intentAcc);
                    else c.startService(intentAcc);
                }
            } else {
                // Luân phiên: Kích hoạt hiển thị cũ, tắt hiển thị Trợ năng mới
                p.edit().putBoolean("shortcut_home_on", true)
                        .putBoolean("shortcut_acc_home_on", false).apply();
                c.stopService(new Intent(c, AccessibleHomeService.class));
            }
            // Kích hoạt đồng bộ trạng thái giao diện tức thì không độ trễ
            c.sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE"));
} else if ("com.manhmoc.edgebar.TOGGLE_ACC_HOME_ON".equals(action)) {
    c.stopService(new Intent(c, HomescreenService.class));
    Intent a = new Intent(c, AccessibleHomeService.class);
    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(a);
    else c.startService(a);
} else if ("com.manhmoc.edgebar.TOGGLE_ACC_HOME_OFF".equals(action)) {
    c.stopService(new Intent(c, AccessibleHomeService.class));
        } else if ("com.manhmoc.edgebar.TOGGLE_APP".equals(action)) {
            String data = i.getDataString();
            if (data != null && data.startsWith("acc://")) toggleAcc(c, data.substring(6));
        }
    }
    private boolean isAccEnabled(Context c) {
    String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return s != null && s.contains(c.getPackageName() + "/" + EdgeBarService.class.getName());
}
    private void toggleAcc(Context c, String mySvc) {
    try {
        String cur = Settings.Secure.getString(
            c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (cur == null) cur = "";
        
        // Tách danh sách bằng ":" để tránh xóa nhầm service có tên tương tự
        java.util.List<String> list = new java.util.ArrayList<>(
            java.util.Arrays.asList(cur.split(":")));
        list.removeIf(s -> s.trim().isEmpty());
        
        boolean en = false;
        for (String s : list) { if (s.trim().equals(mySvc)) { en = true; break; } }
        
        if (en) {
            list.removeIf(s -> s.trim().equals(mySvc));
        } else {
            list.add(mySvc);
        }
        
        String newVal = android.text.TextUtils.join(":", list);
        Settings.Secure.putString(c.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newVal);
        // GIỮ ACCESSIBILITY_ENABLED = 1 LUÔN LUÔN, không bao giờ đặt = 0
        Settings.Secure.putString(c.getContentResolver(),
            Settings.Secure.ACCESSIBILITY_ENABLED, "1");
    } catch (Exception e) {}
  }
}
