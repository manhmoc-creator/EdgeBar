package com.manhmoc.edgebar;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.util.UUID;

/** Service nền: bật GATT server để NHẬN lệnh từ thiết bị đã pairing.
 *  Mọi lệnh đến đều bị đối chiếu whitelist (MAC) + hạn tin cậy (7 ngày)
 *  TRƯỚC KHI thực thi — thiết bị lạ hoặc hết hạn bị từ chối thẳng, không chạy gì cả. */
public class RemoteCommandReceiverService extends Service {
        private static final UUID SERVICE_UUID = RemoteDevicePairing.SERVICE_UUID;
    private static final UUID CHAR_CMD = RemoteDevicePairing.CHAR_CMD;
    private static final String CHANNEL_ID = "eb_remote_cmd";
    private static final int NOTIF_ID = 77;

    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private SharedPreferences prefs;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);

        // Chỉ bật server nếu có ÍT NHẤT 1 thiết bị đã pairing và còn hạn — không bật
        // "chờ sẵn" vô ích khi chưa ai pairing, tiết kiệm pin đúng tinh thần đã thống nhất
        if (!hasAnyTrustedDevice()) { stopSelf(); return; }

        startForegroundPersistentNotif(); // bắt buộc có — không thể ẩn, đúng yêu cầu an toàn
        startGattServer();
    }

    private boolean hasAnyTrustedDevice() {
        for (RemoteDeviceStore.Device d : RemoteDeviceStore.loadAll(prefs))
            if (!d.isExpired()) return true;
        return false;
    }

    private void startGattServer() {
        BluetoothManager btMgr = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { stopSelf(); return; }

        gattServer = btMgr.openGattServer(this, new BluetoothGattServerCallback() {
            @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int reqId,
                    BluetoothGattCharacteristic ch, boolean prepared, boolean respond, int offset, byte[] value) {

                String mac = device.getAddress();
                RemoteDeviceStore.Device trusted = RemoteDeviceStore.find(prefs, mac);

                // ===== ĐIỂM KIỂM AN TOÀN CỐT LÕI =====
                boolean allowed = trusted != null && !trusted.isExpired();
                if (respond) gattServer.sendResponse(device, reqId,
                    allowed ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE, 0, null);
                if (!allowed) {
                    logRejected(mac); // ghi log để user xem lại ai từng cố gửi lệnh mà bị chặn
                    return; // DỪNG — không thực thi gì cả
                }

                String cmd = new String(value);
                executeCommand(cmd, trusted.name);
            }
        });

        BluetoothGattService svc = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR_CMD,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        gattServer.addService(svc);

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser != null) {
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true).build();
            AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();
            advertiser.startAdvertising(settings, data, new AdvertiseCallback() {});
        }
    }

    private void executeCommand(String cmd, String fromDeviceName) {
        try {
            switch (cmd) {
                case RemoteCommandSender.CMD_WIFI_ON:
                case RemoteCommandSender.CMD_WIFI_OFF: {
                    WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
                    // Android 10+ không cho setWifiEnabled() trực tiếp -> mở Panel hệ thống
                    Intent panel = new Intent(android.provider.Settings.Panel.ACTION_WIFI);
                    panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(panel);
                    break;
                }
                case RemoteCommandSender.CMD_BT_ON:
                    BluetoothAdapter.getDefaultAdapter().enable();
                    break;
                case RemoteCommandSender.CMD_BT_OFF:
                    BluetoothAdapter.getDefaultAdapter().disable();
                    break;
                case RemoteCommandSender.CMD_HOTSPOT_ON:
                case RemoteCommandSender.CMD_HOTSPOT_OFF:
                    // Hotspot cần quyền WRITE_SECURE_SETTINGS đã có sẵn trong app (xem showPremiumDialog)
                    toggleHotspot(cmd.equals(RemoteCommandSender.CMD_HOTSPOT_ON));
                    break;
            }
            // Thông báo RÕ RÀNG mỗi lần có lệnh được thực thi — không im lặng chạy ngầm
            notifyCommandExecuted(cmd, fromDeviceName);
        } catch (Exception ignored) {}
    }

    private void toggleHotspot(boolean on) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getSystemService(WIFI_SERVICE);
            java.lang.reflect.Method m = wm.getClass().getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration.class, boolean.class);
            m.invoke(wm, null, on);
        } catch (Exception ignored) {}
    }

    private void logRejected(String mac) {
        SharedPreferences.Editor ed = prefs.edit();
        String log = prefs.getString("remote_rejected_log", "");
        log = mac + "@" + System.currentTimeMillis() + ";" + log;
        ed.putString("remote_rejected_log", log).apply();
    }

    private void startForegroundPersistentNotif() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Remote Devices", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);

        Intent killIntent = new Intent(this, RemoteKillSwitchReceiver.class);
        PendingIntent killPI = PendingIntent.getBroadcast(this, 1, killIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("🔵 Edge Bar — Remote Devices đang bật")
            .setContentText("Máy này có thể nhận lệnh từ thiết bị đã ghép đôi")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true) // KHÔNG thể vuốt xoá — đúng yêu cầu an toàn
            .addAction(0, "⛔ Đây không phải tôi — Thu hồi tất cả", killPI)
            .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIF_ID, n);
    }

        private void notifyCommandExecuted(String cmd, String fromName) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("eb_remote_cmd_evt",
            "Remote Devices - Lệnh đã chạy", NotificationManager.IMPORTANCE_HIGH));
        Notification n = new Notification.Builder(this, "eb_remote_cmd_evt")
            .setContentTitle("Đã thực thi lệnh từ " + fromName)
            .setContentText(cmd)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setAutoCancel(true)
            .build();
        nm.notify((int) System.currentTimeMillis(), n);
    }
    @Override public void onDestroy() {
        try { if (advertiser != null) advertiser.stopAdvertising(new AdvertiseCallback() {}); } catch (Exception ignored) {}
        try { if (gattServer != null) gattServer.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
