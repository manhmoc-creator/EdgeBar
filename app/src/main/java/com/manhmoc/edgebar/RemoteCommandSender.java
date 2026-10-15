package com.manhmoc.edgebar;

import android.bluetooth.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/** Mỗi lần bấm nút QS -> connect, gửi đúng 1 lệnh, disconnect ngay.
 *  KHÔNG giữ GATT connection giữa các lần bấm -> tiết kiệm pin thật sự. */
public class RemoteCommandSender {
    private static final UUID SERVICE_UUID = UUID.fromString("7a9e2c10-0001-4edb-a100-edgebarpairing");
    private static final UUID CHAR_CMD = UUID.fromString("7a9e2c10-0004-4edb-a100-edgebarpairing");

    public interface SendResult { void onSent(); void onFailed(String reason); }

    public static void send(Context ctx, String mac, String command, SendResult cb) {
        SharedPreferences prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        RemoteDeviceStore.Device d = RemoteDeviceStore.find(prefs, mac);
        if (d == null) { cb.onFailed("Thiết bị chưa pairing"); return; }
        if (d.isExpired()) { cb.onFailed("Hết hạn tin cậy — cần xác thực lại"); return; }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return; }
        BluetoothDevice device = adapter.getRemoteDevice(mac);

        final BluetoothGatt[] gattHolder = new BluetoothGatt[1];
        final Handler timeout = new Handler(Looper.getMainLooper());
        Runnable giveUp = () -> { if (gattHolder[0] != null) gattHolder[0].disconnect(); cb.onFailed("Timeout — thiết bị không phản hồi"); };
        timeout.postDelayed(giveUp, 8000);

        gattHolder[0] = device.connectGatt(ctx, false, new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices();
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt.close(); // giải phóng ngay khi xong
            }
            @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattService svc = gatt.getService(SERVICE_UUID);
                if (svc == null) { timeout.removeCallbacks(giveUp); cb.onFailed("Thiết bị không hỗ trợ"); gatt.disconnect(); return; }
                BluetoothGattCharacteristic c = svc.getCharacteristic(CHAR_CMD);
                c.setValue(command.getBytes());
                gatt.writeCharacteristic(c);
            }
            @Override public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                timeout.removeCallbacks(giveUp);
                if (status == BluetoothGatt.GATT_SUCCESS) cb.onSent(); else cb.onFailed("Gửi lệnh thất bại");
                gatt.disconnect(); // XONG NGAY — không giữ kết nối
            }
        });
    }

    // Các lệnh chuẩn — máy nhận (EdgeBar bên kia) tự nghe qua GATT server và đối chiếu whitelist trước khi thực thi
    public static final String CMD_WIFI_ON = "WIFI_ON", CMD_WIFI_OFF = "WIFI_OFF";
    public static final String CMD_HOTSPOT_ON = "HOTSPOT_ON", CMD_HOTSPOT_OFF = "HOTSPOT_OFF";
    public static final String CMD_BT_ON = "BT_ON", CMD_BT_OFF = "BT_OFF";
}
