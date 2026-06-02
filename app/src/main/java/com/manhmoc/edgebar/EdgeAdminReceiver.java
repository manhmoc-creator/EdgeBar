package com.manhmoc.edgebar;

import android.app.admin.DeviceAdminReceiver;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;

public class EdgeAdminReceiver extends DeviceAdminReceiver {
    // Khi user cố gỡ quyền Admin → hiện dialog yêu cầu Morse password
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Nhập mật khẩu Morse để huỷ quyền Admin. " +
               "Huỷ Admin sẽ làm mất khả năng bảo vệ lớp phủ Morse.";
    }
}
