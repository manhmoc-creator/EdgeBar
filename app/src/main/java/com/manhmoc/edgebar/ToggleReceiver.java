package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.provider.Settings; import android.os.Build;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if ("com.manhmoc.edgebar.TOGGLE_LOCK".equals(i.getAction())) {
            toggleAcc(c, c.getPackageName() + "/" + EdgeBarService.class.getName());
        } else if ("com.manhmoc.edgebar.TOGGLE_HOME".equals(i.getAction())) {
            Intent s = new Intent(c, HomescreenService.class); if (HomescreenService.isRunning) c.stopService(s); else { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s); }
        } else if ("com.manhmoc.edgebar.TOGGLE_MACRO".equals(i.getAction())) {
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
        } else if ("com.manhmoc.edgebar.TOGGLE_APP".equals(i.getAction())) {
            String data = i.getDataString();
            if (data != null && data.startsWith("acc://")) toggleAcc(c, data.substring(6));
        }
    }
    private void toggleAcc(Context c, String mySvc) {
        try { String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES); if (cur == null) cur = ""; boolean en = cur.contains(mySvc); if (en) cur = cur.replace(":" + mySvc, "").replace(mySvc + ":", "").replace(mySvc, ""); else cur = cur.isEmpty() ? mySvc : cur + ":" + mySvc; Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cur); Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1"); } catch (Exception e) {}
    }
}
