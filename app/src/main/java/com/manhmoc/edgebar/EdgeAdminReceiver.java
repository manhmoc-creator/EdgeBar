package com.manhmoc.edgebar;

import android.app.admin.DeviceAdminReceiver;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

public class EdgeAdminReceiver extends DeviceAdminReceiver {
    
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Nhập mật khẩu Morse để huỷ quyền Admin. " +
               "Huỷ Admin sẽ làm mất khả năng bảo vệ lớp phủ Morse.";
    }
}
