package com.manhmoc.edgebar;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;
import android.util.Base64;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.util.List;
import java.util.UUID;

/** Pairing 2 máy bằng PIN 9 số do máy A sinh ra, máy B tự gõ bằng tay.
 *  Trao đổi public key qua GATT NGAY TRONG LÚC pairing rồi disconnect —
 *  không giữ kết nối sau khi xong. */
public class RemoteDevicePairing {
    SERVICE_UUID = UUID.fromString("7a9e2c10-0001-4edb-a100-0000edbe0001");
CHAR_PIN_CHECK = UUID.fromString("7a9e2c10-0002-4edb-a100-0000edbe0001");
CHAR_PUBKEY    = UUID.fromString("7a9e2c10-0003-4edb-a100-0000edbe0001");
CHAR_CMD       = UUID.fromString("7a9e2c10-0004-4edb-a100-0000edbe0001");
    private final Context ctx;
    private final SharedPreferences prefs;
    private final BluetoothManager btMgr;
    private BluetoothLeScanner scanner;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private ScanCallback pendingScanCb;
    private final Handler h = new Handler(Looper.getMainLooper());

    public RemoteDevicePairing(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = ctx.getSharedPreferences("EdgeBarPrefs", Context.MODE_PRIVATE);
        this.btMgr = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public interface PairResult { void onPaired(String mac, String name); void onFailed(String reason); }

    /** MÁY A: sinh PIN 9 số, bật advertise + GATT server trong tối đa 120s để chờ máy B nhập đúng PIN. */
    public String startAsAdvertiser(PairResult cb) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return null; }
        String pin = generatePin9();

        advertiser = adapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setTimeout(120_000).setConnectable(true).build();
        AdvertiseData data = new AdvertiseData.Builder()
            .addServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();

        gattServer = btMgr.openGattServer(ctx, new PairingGattCallback(pin, cb));
        BluetoothGattService svc = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR_PIN_CHECK,
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        svc.addCharacteristic(new BluetoothGattCharacteristic(CHAR_PUBKEY,
            BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ));
        gattServer.addService(svc);

        advertiser.startAdvertising(settings, data, new AdvertiseCallback() {
            @Override public void onStartFailure(int errorCode) { cb.onFailed("Không bật được quảng bá BLE: " + errorCode); }
        });

        h.postDelayed(this::stop, 120_000); // tự tắt sau 2 phút nếu không ai pairing — không giữ radio mở vô thời hạn
        return pin;
    }

    /** MÁY B: quét theo Service UUID riêng (lọc phần cứng, rẻ pin), kết nối khi tìm thấy, gửi PIN đã gõ. */
    public void startAsScanner(String enteredPin, PairResult cb) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) { cb.onFailed("Bluetooth đang tắt"); return; }
        scanner = adapter.getBluetoothLeScanner();
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new android.os.ParcelUuid(SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        pendingScanCb = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                scanner.stopScan(this); // tìm thấy -> dừng quét ngay, chuyển sang connect 1 lần duy nhất
                connectAndSendPin(result.getDevice(), enteredPin, cb);
            }
        };
        scanner.startScan(java.util.Collections.singletonList(filter), settings, pendingScanCb);
        h.postDelayed(() -> { if (pendingScanCb != null) { scanner.stopScan(pendingScanCb); cb.onFailed("Không tìm thấy thiết bị — đảm bảo 2 máy để gần nhau"); } }, 30_000);
    }

    private void connectAndSendPin(BluetoothDevice device, String pin, PairResult cb) {
        device.connectGatt(ctx, false, new BluetoothGattCallback() {
            @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices();
            }
            @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                BluetoothGattCharacteristic c = gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_PIN_CHECK);
                c.setValue(pin.getBytes());
                gatt.writeCharacteristic(c);
            }
            @Override public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                if (status != BluetoothGatt.GATT_SUCCESS) { cb.onFailed("Sai PIN"); gatt.disconnect(); return; }
                gatt.readCharacteristic(gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_PUBKEY));
            }
            @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic ch, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    String pubKey = Base64.encodeToString(ch.getValue(), Base64.NO_WRAP);
                    RemoteDeviceStore.upsertTrusted(prefs, device.getAddress(), device.getName(), pubKey);
                    cb.onPaired(device.getAddress(), device.getName());
                }
                gatt.disconnect(); // XONG NGAY — không giữ kết nối sau pairing
            }
        });
    }

    private class PairingGattCallback extends BluetoothGattServerCallback {
        private final String expectedPin;
        private final PairResult cb;
        PairingGattCallback(String pin, PairResult cb) { this.expectedPin = pin; this.cb = cb; }

        @Override public void onCharacteristicWriteRequest(BluetoothDevice device, int reqId, BluetoothGattCharacteristic ch,
                boolean prepared, boolean respond, int offset, byte[] value) {
            boolean ok = expectedPin.equals(new String(value));
            if (respond) gattServer.sendResponse(device, reqId, ok ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE, 0, null);
            if (ok) {
                KeyPair kp = genKeyPair();
                String name = android.provider.Settings.Global.getString(ctx.getContentResolver(), "device_name");
                RemoteDeviceStore.upsertTrusted(prefs, device.getAddress(), device.getName(), Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP));
                cb.onPaired(device.getAddress(), device.getName());
                stop(); // pairing xong -> ngắt advertise ngay, không chờ hết 120s
            }
        }
        @Override public void onCharacteristicReadRequest(BluetoothDevice device, int reqId, int offset, BluetoothGattCharacteristic ch) {
            gattServer.sendResponse(device, reqId, BluetoothGatt.GATT_SUCCESS, 0, genKeyPair().getPublic().getEncoded());
        }
    }

    private KeyPair genKeyPair() {
        try { KeyPairGenerator g = KeyPairGenerator.getInstance("EC"); g.initialize(256); return g.generateKeyPair(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private String generatePin9() {
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) sb.append(r.nextInt(10));
        return sb.toString();
    }

    public void stop() {
        try { if (advertiser != null) advertiser.stopAdvertising(new AdvertiseCallback() {}); } catch (Exception ignored) {}
        try { if (scanner != null && pendingScanCb != null) scanner.stopScan(pendingScanCb); } catch (Exception ignored) {}
        try { if (gattServer != null) gattServer.close(); } catch (Exception ignored) {}
        advertiser = null; scanner = null; gattServer = null; pendingScanCb = null;
    }
}
