package com.manhmoc.edgebar;

import android.bluetooth.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteCommandSender {
    public interface SendResult { void onSent(); void onFailed(String reason); }

    public static void send(Context ctx, String mac, final String command, final SendResult cb) {
        Context app = ctx.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        RemoteDeviceStore.Device d = RemoteDeviceStore.find(prefs, mac);
        if (d == null) { cb.onFailed("Thiết bị chưa pairing"); return; }
        if (d.isExpired()) { cb.onFailed("Hết hạn tin cậy — cần xác thực lại"); return; }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return; }

        final Handler main = new Handler(Looper.getMainLooper());
        final AtomicBoolean done = new AtomicBoolean(false);
        final BluetoothGatt[] g = new BluetoothGatt[1];
        final Runnable timeoutRun = () -> {
            if (done.compareAndSet(false, true)) { closeQuiet(g[0]); cb.onFailed("Timeout — thiết bị không phản hồi"); }
        };

        try {
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            main.postDelayed(timeoutRun, 8000);
            g[0] = device.connectGatt(app, false, new BluetoothGattCallback() {
                private void end(BluetoothGatt gatt, String err) {
                    if (!done.compareAndSet(false, true)) return;
                    main.removeCallbacks(timeoutRun);
                    closeQuiet(gatt);
                    if (err == null) cb.onSent(); else cb.onFailed(err);
                }
                @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) gatt.discoverServices();
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) end(gatt, "Không kết nối được (status " + status + ")");
                }
                @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    BluetoothGattService svc = gatt.getService(RemoteDevicePairing.SERVICE_UUID);
                    BluetoothGattCharacteristic c = svc == null ? null : svc.getCharacteristic(RemoteDevicePairing.CHAR_CMD);
                    if (status != BluetoothGatt.GATT_SUCCESS || c == null) { end(gatt, "Thiết bị không hỗ trợ"); return; }
                    c.setValue(command.getBytes());
                    gatt.writeCharacteristic(c);
                }
                @Override public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                    end(gatt, status == BluetoothGatt.GATT_SUCCESS ? null : "Gửi lệnh thất bại");
                }
            }, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException se) {
            main.removeCallbacks(timeoutRun);
            if (done.compareAndSet(false, true)) cb.onFailed("Chưa được cấp quyền Bluetooth");
        }
    }

    private static void closeQuiet(BluetoothGatt gatt) {
        if (gatt == null) return;
        try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {}
    }

    public static final String CMD_WIFI_ON = "WIFI_ON", CMD_WIFI_OFF = "WIFI_OFF";
    public static final String CMD_HOTSPOT_ON = "HOTSPOT_ON", CMD_HOTSPOT_OFF = "HOTSPOT_OFF";
    public static final String CMD_BT_ON = "BT_ON", CMD_BT_OFF = "BT_OFF";
}
