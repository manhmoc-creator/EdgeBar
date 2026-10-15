package com.manhmoc.edgebar;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class RemoteDevicePairing {
    static final UUID SERVICE_UUID   = UUID.fromString("7a9e2c10-0001-4edb-a100-0000edbe0001");
    static final UUID CHAR_PIN_CHECK = UUID.fromString("7a9e2c10-0002-4edb-a100-0000edbe0001");
    static final UUID CHAR_PUBKEY    = UUID.fromString("7a9e2c10-0003-4edb-a100-0000edbe0001");
    static final UUID CHAR_CMD       = UUID.fromString("7a9e2c10-0004-4edb-a100-0000edbe0001");
    private static final long ADVERTISE_MS = 120_000, SCAN_MS = 30_000;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final BluetoothManager btMgr;
    private final Handler h = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advCb;
    private ScanCallback scanCb;
    private BluetoothGattServer gattServer;
    private BluetoothGatt clientGatt;
    private KeyPair sessionKp;
    private PairResult result;
    private volatile boolean finished;
    private String timeoutMsg = "";
    private final Runnable timeoutRun = () -> fail(timeoutMsg);

    public interface PairResult { void onPaired(String mac, String name); void onFailed(String reason); }

    public RemoteDevicePairing(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        this.btMgr = (BluetoothManager) this.ctx.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    private void fail(String why) {
        if (finished) return;
        finished = true;
        PairResult r = result;
        stop();
        if (r != null) r.onFailed(why);
    }

    private void succeed(String mac, String name, String pubKeyB64, long stopDelayMs) {
        if (finished) return;
        finished = true;
        RemoteDeviceStore.upsertTrusted(prefs, mac, name, pubKeyB64);
        PairResult r = result;
        if (r != null) r.onPaired(mac, name);
        h.removeCallbacks(timeoutRun);
        // UX: máy "Tạo PIN" phải giữ server thêm ~1.5s để máy kia đọc xong public key
        h.postDelayed(this::stop, stopDelayMs);
    }

    // ===================== MÁY TẠO PIN =====================
    public String startAsAdvertiser(PairResult cb) {
        stop(); finished = false; result = cb;
        try {
            BluetoothAdapter adapter = btMgr != null ? btMgr.getAdapter() : null;
            if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return null; }
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                cb.onFailed("Máy này không hỗ trợ quảng bá BLE. Hãy dùng vai trò 'Nhập PIN'.");
                return null;
            }
            final String pin = generatePin9();
            sessionKp = genKeyPair();
            gattServer = btMgr.openGattServer(ctx, new PairingGattCallback(pin));
            if (gattServer == null) { cb.onFailed("Không mở được GATT server"); stop(); return null; }

            BluetoothGattService svc = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
            svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR_PIN_CHECK,
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
            svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR_PUBKEY,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
            gattServer.addService(svc);

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTimeout((int) ADVERTISE_MS).setConnectable(true).build();
            AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();
            advCb = new AdvertiseCallback() {
                @Override public void onStartFailure(int errorCode) { h.post(() -> fail("Không bật được quảng bá BLE: " + errorCode)); }
            };
            advertiser.startAdvertising(settings, data, advCb);

            timeoutMsg = "Hết thời gian chờ ghép đôi";
            h.postDelayed(timeoutRun, ADVERTISE_MS);
            return pin;
        } catch (SecurityException se) {
            cb.onFailed("Chưa được cấp quyền Bluetooth.");
            stop();
            return null;
        } catch (Exception e) {
            cb.onFailed("Lỗi: " + e.getMessage());
            stop();
            return null;
        }
    }

    private class PairingGattCallback extends BluetoothGattServerCallback {
        private final String expectedPin;
        private volatile boolean pinOk;
        PairingGattCallback(String pin) { expectedPin = pin; }

        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int reqId, BluetoothGattCharacteristic ch,
                boolean prepared, boolean respond, int offset, byte[] value) {
            boolean ok = CHAR_PIN_CHECK.equals(ch.getUuid()) && value != null && expectedPin.equals(new String(value));
            pinOk = ok;
            BluetoothGattServer s = gattServer;
            if (respond && s != null) s.sendResponse(device, reqId,
                ok ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE, 0, null);
        }

        @Override public void onCharacteristicReadRequest(BluetoothDevice device, int reqId, int offset, BluetoothGattCharacteristic ch) {
            BluetoothGattServer s = gattServer;
            KeyPair kp = sessionKp;
            if (s == null) return;
            if (!pinOk || kp == null || !CHAR_PUBKEY.equals(ch.getUuid())) {
                s.sendResponse(device, reqId, BluetoothGatt.GATT_FAILURE, 0, null);
                return;
            }
            byte[] key = kp.getPublic().getEncoded();
            byte[] slice = offset < key.length ? Arrays.copyOfRange(key, offset, key.length) : new byte[0];
            s.sendResponse(device, reqId, BluetoothGatt.GATT_SUCCESS, offset, slice);
            if (offset == 0) {
                final String mac = device.getAddress();
                String n = null;
                try { n = device.getName(); } catch (SecurityException ignored) {}
                final String name = n == null ? "Edge Bar" : n;
                h.post(() -> succeed(mac, name, "", 1500));
            }
        }
    }

    // ===================== MÁY NHẬP PIN =====================
    public void startAsScanner(final String enteredPin, PairResult cb) {
        stop(); finished = false; result = cb;
        try {
            BluetoothAdapter adapter = btMgr != null ? btMgr.getAdapter() : null;
            if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { cb.onFailed("Không khởi tạo được bộ quét BLE"); return; }

            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanCb = new ScanCallback() {
                @Override public void onScanResult(int type, ScanResult r) {
                    if (scanCb == null) return;
                    try { scanner.stopScan(this); } catch (Exception ignored) {}
                    scanCb = null;
                    connectAndSendPin(r.getDevice(), enteredPin);
                }
                @Override public void onScanFailed(int errorCode) { h.post(() -> fail("Quét BLE thất bại: " + errorCode)); }
            };
            scanner.startScan(Collections.singletonList(filter), settings, scanCb);
            timeoutMsg = "Không tìm thấy thiết bị — để 2 máy sát nhau và bật Vị trí (GPS)";
            h.postDelayed(timeoutRun, SCAN_MS);
        } catch (SecurityException se) {
            cb.onFailed("Chưa được cấp quyền Bluetooth/Vị trí.");
            stop();
        } catch (Exception e) {
            cb.onFailed("Lỗi: " + e.getMessage());
            stop();
        }
    }

    private void connectAndSendPin(final BluetoothDevice device, final String pin) {
        clientGatt = device.connectGatt(ctx, false, new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    h.post(() -> fail("Mất kết nối khi ghép đôi (status " + status + ")"));
                }
            }
            @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattService svc = gatt.getService(SERVICE_UUID);
                if (status != BluetoothGatt.GATT_SUCCESS || svc == null) { h.post(() -> fail("Máy kia không phải Edge Bar")); return; }
                BluetoothGattCharacteristic c = svc.getCharacteristic(CHAR_PIN_CHECK);
                c.setValue(pin.getBytes());
                gatt.writeCharacteristic(c);
            }
            @Override public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) { h.post(() -> fail("Sai PIN")); return; }
                gatt.readCharacteristic(gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_PUBKEY));
            }
            @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) { h.post(() -> fail("Không đọc được khoá từ máy kia")); return; }
                final String key = Base64.encodeToString(ch.getValue(), Base64.NO_WRAP);
                final String mac = device.getAddress();
                String n = null;
                try { n = device.getName(); } catch (SecurityException ignored) {}
                final String name = n == null ? "Edge Bar" : n;
                h.post(() -> succeed(mac, name, key, 0));
            }
        }, BluetoothDevice.TRANSPORT_LE);
    }

    private KeyPair genKeyPair() {
        try { KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(256); return g.generateKeyPair(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private String generatePin9() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) sb.append(r.nextInt(10));
        return sb.toString();
    }

    public void stop() {
        h.removeCallbacks(timeoutRun);
        try { if (advertiser != null && advCb != null) advertiser.stopAdvertising(advCb); } catch (Exception ignored) {}
        try { if (scanner != null && scanCb != null) scanner.stopScan(scanCb); } catch (Exception ignored) {}
        try { if (clientGatt != null) { clientGatt.disconnect(); clientGatt.close(); } } catch (Exception ignored) {}
        try { if (gattServer != null) gattServer.close(); } catch (Exception ignored) {}
        advertiser = null; advCb = null; scanner = null; scanCb = null;
        clientGatt = null; gattServer = null; sessionKp = null;
    }
}
