     package com.manhmoc.edgebar;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class HomebDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Tắt quyền Device Admin sẽ vô hiệu hóa action Screen Off của Homeb.";
    }
}
