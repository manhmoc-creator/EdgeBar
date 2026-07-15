      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * V19.12.3.6.10 GHOST BUTTON PROTOCOL
 * Chiếm phím Volume vật lý bằng MediaSession trick — hoạt động cả khi
 * màn hình tắt vì hệ thống định tuyến phím Volume tới Session đang active
 * ở tầng framework, độc lập với UI (giống cơ chế tai nghe bluetooth
 * điều khiển play/pause khi màn tắt).
 *
 * CHỈ active trong khoảng SCREEN_OFF -> SCREEN_ON/USER_PRESENT.
 * Ngoài khoảng đó session.setActive(false) -> phím Volume trả lại
 * cho hệ thống hoạt động bình thường (nghe nhạc, chỉnh âm lượng thật).
 */
public class VolumeButtonService extends Service {
    public static boolean isRunning = false;

    private MediaSession mediaSession;
    private SharedPreferences prefs;
    private BroadcastReceiver screenReceiver;
    private final Handler h = new Handler(Looper.getMainLooper());

    // V19.12.3.6.10: đếm số lần bấm liên tiếp trong 1 "chuỗi" (burst) để phân biệt
    // tap / dtap / long — MediaSession không cho key-down/up thật, chỉ có
    // onAdjustVolume() gọi lặp, nên phải suy luận qua nhịp gọi.
    private int upBurst = 0, downBurst = 0;
    private boolean upLongFired = false, downLongFired = false;
    private Runnable upEndCheck, downEndCheck;
    private static final long REPEAT_WINDOW_MS = 350;
    private static final int LONG_THRESHOLD = 5;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        startForegroundQuiet(); // BẮT BUỘC gọi trong 5s đầu — thiếu dòng này gây crash ANR

        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean off = Intent.ACTION_SCREEN_OFF.equals(i.getAction());
                // Chỉ chiếm phím Volume khi màn TẮT. Màn sáng: trả lại cho hệ thống.
                if (mediaSession != null) mediaSession.setActive(off);
                if (!off) {
                    upBurst = 0; downBurst = 0;
                    upLongFired = false; downLongFired = false;
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);

        // MediaSession + VolumeProvider ABSOLUTE: AudioService định tuyến phím Volume
        // vào onAdjustVolume() thay vì chỉnh âm lượng thật — API công khai, không cần root,
        // hoạt động cả khi màn tắt vì độc lập với UI.
        mediaSession = new MediaSession(this, "EdgeBarVolKey");
        VolumeProvider provider = new VolumeProvider(
                VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 10, 5) {
            @Override public void onAdjustVolume(int direction) {
                setCurrentVolume(5); // giữ mốc giữa, không cho chạm đáy/trần
                if (direction > 0) handleSide(true);
                else if (direction < 0) handleSide(false);
            }
        };
        mediaSession.setPlaybackToRemote(provider);
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
        mediaSession.setActive(false); // mặc định TẮT — chỉ SCREEN_OFF mới bật qua screenReceiver
        isRunning = true;
    }

    private void handleSide(boolean isUp) {
        int burst = (isUp ? upBurst : downBurst) + 1;
        if (isUp) upBurst = burst; else downBurst = burst;

        boolean longFired = isUp ? upLongFired : downLongFired;
        if (burst >= LONG_THRESHOLD && !longFired) {
            longFired = true;
            if (isUp) upLongFired = true; else downLongFired = true;
            fire("volkey_" + (isUp ? "up" : "down") + "_long");
        }

        Runnable prev = isUp ? upEndCheck : downEndCheck;
        if (prev != null) h.removeCallbacks(prev);
        Runnable check = () -> {
            boolean wasLong = isUp ? upLongFired : downLongFired;
            int finalBurst = isUp ? upBurst : downBurst;
            if (!wasLong) {
                if (finalBurst >= 2) fire("volkey_" + (isUp ? "up" : "down") + "_dtap");
                else fire("volkey_" + (isUp ? "up" : "down") + "_tap");
            }
            if (isUp) { upBurst = 0; upLongFired = false; }
            else { downBurst = 0; downLongFired = false; }
        };
        if (isUp) upEndCheck = check; else downEndCheck = check;
        h.postDelayed(check, REPEAT_WINDOW_MS);
    }
    private void fire(String key) {
        if (!prefs.getBoolean(key + "_on", true)) return;
        String action = prefs.getString(key, "NONE");
        if (action.equals("NONE")) return;
        if (prefs.getBoolean(key + "_vib", true)) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26)
                    v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
                else v.vibrate(25);
            } catch (Exception ignored) {}
        }
        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
        ipc.putExtra("act", action.split(",")[0].trim());
        sendBroadcast(ipc);
    }
    private void startForegroundQuiet() {
        String cid = "eb_volkey";
        NotificationChannel c = new NotificationChannel(cid, "Phím Âm Lượng (Màn tắt)",
                NotificationManager.IMPORTANCE_MIN);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("EdgeBar VolKey")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(91, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(91, n);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        isRunning = false;
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    public static boolean hasAnyRule(SharedPreferences p) {
        String[] keys = {"volkey_up_tap","volkey_up_dtap","volkey_up_long",
                          "volkey_down_tap","volkey_down_dtap","volkey_down_long"};
        for (String k : keys) if (!p.getString(k, "NONE").equals("NONE")) return true;
        return false;
    }
}
