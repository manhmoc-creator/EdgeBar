package com.manhmoc.edgebar;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.List;

/** Chạy định kỳ (qua AlarmManager, không phải Thread nền liên tục) để kiểm
 *  xem có thiết bị nào sắp hết hạn 7 ngày không -> nếu có thì bắn notification
 *  nhắc, và nếu đã hết hạn thật thì tự revoke (không cho "ngầm" tiếp tục dùng
 *  quá hạn mà không re-auth). */
public class RemoteTrustCheckReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "eb_remote_reauth";
    private static final int NOTIF_ID = 78;

    @Override public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        List<RemoteDeviceStore.Device> list = RemoteDeviceStore.loadAll(prefs);
        boolean changed = false;

        for (RemoteDeviceStore.Device d : new java.util.ArrayList<>(list)) {
            if (d.isExpired()) {
                // Hết hạn thật -> tự thu hồi, không âm thầm cho dùng tiếp
                RemoteDeviceStore.revoke(prefs, d.mac);
                changed = true;
                notifyExpired(ctx, d.name);
            } else if (d.needsReminder()) {
                notifyReminder(ctx, d.mac, d.name, d.trustedUntilMs);
            }
        }

        if (changed && list.stream().noneMatch(x -> !x.isExpired())) {
            // Không còn thiết bị nào còn hạn -> tắt luôn service nhận lệnh, đỡ giữ GATT server vô ích
            ctx.stopService(new Intent(ctx, RemoteCommandReceiverService.class));
        }

        scheduleNext(ctx); // tự lên lịch lần kiểm tiếp theo
    }

    private void notifyReminder(Context ctx, String mac, String name, long trustedUntilMs) {
        ensureChannel(ctx);
        Intent reauth = new Intent(ctx, RemoteReauthActivity.class);
        reauth.putExtra("mac", mac);
        reauth.putExtra("name", name);
        reauth.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, mac.hashCode(), reauth,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long hoursLeft = Math.max(1, (trustedUntilMs - System.currentTimeMillis()) / 3600000);
        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
            .setContentTitle("⏰ Sắp hết hạn tin cậy — " + name)
            .setContentText("Còn " + hoursLeft + " giờ. Chạm để xác thực lại (vân tay/PIN).")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        ctx.getSystemService(NotificationManager.class).notify(mac.hashCode(), n);
    }

    private void notifyExpired(Context ctx, String name) {
        ensureChannel(ctx);
        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
            .setContentTitle("🔒 Đã hết hạn — " + name)
            .setContentText("Thiết bị đã bị gỡ khỏi danh sách tin cậy. Cần pairing lại từ đầu.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setAutoCancel(true)
            .build();
        ctx.getSystemService(NotificationManager.class).notify(name.hashCode(), n);
    }

    private void ensureChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Remote Devices - Tái xác thực", NotificationManager.IMPORTANCE_HIGH);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Kiểm mỗi 6 giờ — đủ để không bỏ sót mốc "còn 12h" (REMIND_BEFORE_MS),
     *  dùng setInexactRepeating để hệ thống tự gộp batch, không tốn pin thêm. */
    public static void scheduleNext(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, RemoteTrustCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 601, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 6 * 60 * 60 * 1000L, 6 * 60 * 60 * 1000L, pi);
    }
}
