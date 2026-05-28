package com.manhmoc.edgebar;
import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent;
import android.provider.Settings; import android.os.Build;
public class ToggleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String action = i.getAction(); if(action==null) return;
        switch(action) {
            case "com.manhmoc.edgebar.TOGGLE_LOCK":
                toggleAcc(c, c.getPackageName()+"/"+EdgeBarService.class.getName()); break;
            case "com.manhmoc.edgebar.TOGGLE_HOME":
                Intent s = new Intent(c, HomescreenService.class);
                if(HomescreenService.isRunning) c.stopService(s);
                else { if(Build.VERSION.SDK_INT>=26) c.startForegroundService(s); else c.startService(s); } break;
            case "com.manhmoc.edgebar.TOGGLE_MACRO":
                String list = i.getStringExtra("services");
                if(list!=null && !list.isEmpty()) {
                    String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    if(cur==null) cur="";
                    for(String sv : list.split(",")) {
                        String ms=sv.trim(); if(ms.isEmpty()) continue;
                        if(cur.contains(ms)) cur=cur.replace(":"+ms,"").replace(ms+":","").replace(ms,"");
                        else cur=cur.isEmpty()?ms:cur+":"+ms;
                    }
                    try { Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,cur);
                          Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,"1"); }catch(Exception e){}
                } break;
            case "com.manhmoc.edgebar.TOGGLE_APP":
                String data=i.getDataString();
                if(data!=null && data.startsWith("acc://")) toggleAcc(c,data.substring(6)); break;
            case "com.manhmoc.edgebar.TOGGLE_MORSE":
                boolean isM=c.getSharedPreferences("EdgeBarPrefs",Context.MODE_PRIVATE).getBoolean("morse_mode_en",false);
                c.getSharedPreferences("EdgeBarPrefs",Context.MODE_PRIVATE).edit().putBoolean("morse_mode_en",!isM).apply();
                c.sendBroadcast(new Intent("com.manhmoc.edgebar.SYNC_STATE")); break;
        }
    }
    private void toggleAcc(Context c, String mySvc) {
        try {
            String cur=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(cur==null) cur="";
            if(cur.contains(mySvc)) cur=cur.replace(":"+mySvc,"").replace(mySvc+":","").replace(mySvc,"");
            else cur=cur.isEmpty()?mySvc:cur+":"+mySvc;
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,cur);
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,"1");
        }catch(Exception e){}
    }
}
