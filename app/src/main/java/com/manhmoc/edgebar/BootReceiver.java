       package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        SharedPreferences prefs = c.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        if (VolumeButtonService.hasAnyRule(prefs)) {
            Intent svc = new Intent(c, VolumeButtonService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
            else c.startService(svc);
        }
        boolean anyPanel = prefs.getBoolean("panel1_en", false) || prefs.getBoolean("panel2_en", false) || prefs.getBoolean("panel3_en", false);
if (anyPanel) {
    Intent svc2 = new Intent(c, SidePanelService.class);
    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc2);
    else c.startService(svc2);
        }
        if (prefs.getBoolean("panel_en", false)) {
    Intent svc = new Intent(c, SidePanelService.class);
    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(svc);
    else c.startService(svc);
     }
   }
}
