     package com.manhmoc.edgebar;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class HomebDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }
    // [FIX] Trả về null cho phép hệ thống tắt Device Admin NGAY khi cần (VD: lúc cài đè
    // APK mới), không chờ người dùng xác nhận dialog — đây là nguyên nhân cài đè hay treo/fail.
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return null;
    }
}
