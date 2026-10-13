package com.manhmoc.edgebar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    // Lấy run thành công mới nhất của workflow "Build APK"
    private static final String API_URL =
        "https://api.github.com/repos/manhmoc-creator/EdgeBar/actions/runs?per_page=1&status=success";

    // Throttle: chỉ gọi API mỗi 6 giờ / lần (tiết kiệm pin + tránh rate limit)
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    public interface Callback {
        void onResult(int latestBuildNumber, boolean hasUpdate);
    }

    public static void check(Context ctx, SharedPreferences prefs, Callback cb) {
        long lastCheck = prefs.getLong("update_last_check_ms", 0);
        long now = System.currentTimeMillis();

        // Nếu chưa đủ 6h -> trả kết quả đã cache, không gọi mạng
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            int cached   = prefs.getInt("update_latest_build", 0);
            int lastSeen = prefs.getInt("update_last_seen_build", 0);
            if (cached > 0 && cb != null) cb.onResult(cached, cached > lastSeen);
            return;
        }
        prefs.edit().putLong("update_last_check_ms", now).apply();

        new Thread(() -> {
            int latest = 0;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "EdgeBar-App");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray runs = json.getJSONArray("workflow_runs");
                    if (runs.length() > 0) {
                        latest = runs.getJSONObject(0).getInt("run_number");
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}

            final int fLatest = latest;
            if (fLatest > 0) prefs.edit().putInt("update_latest_build", fLatest).apply();

            int lastSeen = prefs.getInt("update_last_seen_build", 0);
            final boolean hasUpdate = fLatest > 0 && fLatest > lastSeen;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (cb != null) cb.onResult(fLatest, hasUpdate);
            });
        }).start();
    }

    /** Đánh dấu đã xem build mới nhất — gọi khi user bấm nút Update. */
    public static void markSeen(SharedPreferences prefs) {
        int latest = prefs.getInt("update_latest_build", 0);
        if (latest > 0) prefs.edit().putInt("update_last_seen_build", latest).apply();
    }
}
