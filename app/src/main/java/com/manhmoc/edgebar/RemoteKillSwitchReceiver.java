package com.manhmoc.edgebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Bấm nút trên notification -> thu hồi NGAY LẬP TỨC toàn bộ thiết bị đã pairing,
 *  tắt hẳn service nhận lệnh. Không thể hoàn tác từ xa — chỉ người cầm máy mới
 *  bật lại được bằng cách pairing mới từ đầu. */
public class RemoteKillSwitchReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        RemoteDeviceStore.revokeAll(prefs);
        ctx.stopService(new Intent(ctx, RemoteCommandReceiverService.class));

        android.widget.Toast.makeText(ctx,
            "Đã thu hồi toàn bộ thiết bị Remote Devices. Cần pairing lại từ đầu để dùng lại.",
            android.widget.Toast.LENGTH_LONG).show();
    }
}
