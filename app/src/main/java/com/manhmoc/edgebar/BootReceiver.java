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
    }
}
