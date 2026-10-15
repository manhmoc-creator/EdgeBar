package com.manhmoc.edgebar;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Lưu danh sách thiết bị đã pairing thành công qua PIN 9 số.
 *  Mỗi thiết bị có hạn tin cậy 7 ngày — hết hạn thì bị coi là CHƯA pairing,
 *  phải xác thực lại (vân tay/PIN) để gia hạn. */
public class RemoteDeviceStore {
    private static final String KEY = "remote_paired_devices";
    public static final long TRUST_PERIOD_MS = 7L * 24 * 60 * 60 * 1000; // 7 ngày
    public static final long REMIND_BEFORE_MS = 12L * 60 * 60 * 1000;   // nhắc trước 12h hết hạn

    public static class Device {
        public String mac, name, pubKeyB64;
        public long pairedAtMs, trustedUntilMs;
        public boolean isExpired() { return System.currentTimeMillis() > trustedUntilMs; }
        public boolean needsReminder() { return !isExpired() && trustedUntilMs - System.currentTimeMillis() < REMIND_BEFORE_MS; }
    }

    public static List<Device> loadAll(SharedPreferences prefs) {
        List<Device> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Device d = new Device();
                d.mac = o.getString("mac");
                d.name = o.optString("name", "Thiết bị lạ");
                d.pubKeyB64 = o.optString("pubKey", "");
                d.pairedAtMs = o.optLong("pairedAt", 0);
                d.trustedUntilMs = o.optLong("trustedUntil", 0);
                out.add(d);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveAll(SharedPreferences prefs, List<Device> list) {
        JSONArray arr = new JSONArray();
        for (Device d : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("mac", d.mac); o.put("name", d.name); o.put("pubKey", d.pubKeyB64);
                o.put("pairedAt", d.pairedAtMs); o.put("trustedUntil", d.trustedUntilMs);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    /** Gọi khi vừa pairing xong HOẶC vừa xác thực lại thành công. */
    public static void upsertTrusted(SharedPreferences prefs, String mac, String name, String pubKeyB64) {
        List<Device> list = loadAll(prefs);
        Device d = null;
        for (Device x : list) if (x.mac.equals(mac)) { d = x; break; }
        if (d == null) { d = new Device(); d.mac = mac; d.pairedAtMs = System.currentTimeMillis(); list.add(d); }
        d.name = name; d.pubKeyB64 = pubKeyB64;
        d.trustedUntilMs = System.currentTimeMillis() + TRUST_PERIOD_MS;
        saveAll(prefs, list);
    }

    /** Kill switch — xoá sạch 1 thiết bị, dùng khi bấm "Đây không phải tôi" hoặc hết hạn không gia hạn. */
    public static void revoke(SharedPreferences prefs, String mac) {
        List<Device> list = loadAll(prefs);
        list.removeIf(d -> d.mac.equals(mac));
        saveAll(prefs, list);
    }

    /** Kill switch toàn bộ — xoá hết thiết bị đã pairing. */
    public static void revokeAll(SharedPreferences prefs) {
        prefs.edit().remove(KEY).apply();
    }

    public static Device find(SharedPreferences prefs, String mac) {
        for (Device d : loadAll(prefs)) if (d.mac.equals(mac)) return d;
        return null;
    }
}
