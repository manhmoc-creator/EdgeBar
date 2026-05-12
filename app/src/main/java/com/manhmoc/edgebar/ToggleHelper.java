package com.manhmoc.edgebar;

import android.content.Context;
import android.provider.Settings;

public class ToggleHelper {
    public static void toggle(Context context) {
        try {
            String serviceString = context.getPackageName() + "/" + EdgeBarService.class.getName();
            String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices == null) enabledServices = "";
            
            boolean isEnabled = enabledServices.contains(serviceString);
            if (isEnabled) {
                enabledServices = enabledServices.replace(serviceString, "").replace("::", ":");
                if (enabledServices.endsWith(":")) enabledServices = enabledServices.substring(0, enabledServices.length() - 1);
            } else {
                if (enabledServices.isEmpty()) enabledServices = serviceString;
                else enabledServices += ":" + serviceString;
                Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServices);
        } catch (Exception e) {}
    }
}
