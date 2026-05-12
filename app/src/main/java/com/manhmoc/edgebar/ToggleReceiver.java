package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ToggleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Nhận lệnh phát là gạt công tắc Trợ năng luôn!
        ToggleHelper.toggle(context);
    }
}
