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
    private String currentForegroundPkg = "";
    private final Handler keepAliveHandler = new Handler();
private Runnable keepAliveRunnable;
private static final long KEEP_ALIVE_INTERVAL_MS = 25000;

private void startKeepAlive() {
    stopKeepAlive();
    keepAliveRunnable = () -> {
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
        }
        keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_INTERVAL_MS);
    };
    keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_INTERVAL_MS);
}
private void stopKeepAlive() { if (keepAliveRunnable != null) keepAliveHandler.removeCallbacks(keepAliveRunnable); }
    private static final long HELD_MS_THRESHOLD = 550;
    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
if (!startForegroundQuiet()) return; // FGS bị hệ thống từ chối → thoát êm, không crash dây chuyền
screenReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if (Intent.ACTION_SCREEN_OFF.equals(act)) {
    if (mediaSession != null) mediaSession.setActive(true);
    startKeepAlive();
} else if (Intent.ACTION_SCREEN_ON.equals(act)) {
    stopKeepAlive();
    // V19.12.3.6.13: màn sáng = trả quyền cho OS ngay lập tức, kể cả
    // đang ở màn khoá. Chỉ giữ quyền khi màn HẲN tắt.
    if (mediaSession != null) mediaSession.setActive(false);
    resetBurst();
} else if (Intent.ACTION_USER_PRESENT.equals(act)) {
    stopKeepAlive();
            // Mở khoá thật sự. Chỉ tắt nếu KHÔNG đứng ở Home — đứng ở Home vẫn giữ active
            // THAY bằng: mở khoá xong là tắt tuyệt đối, không xét đang đứng ở đâu
if (mediaSession != null) mediaSession.setActive(false);
resetBurst();
    }
    }
};
IntentFilter f = new IntentFilter();
f.addAction(Intent.ACTION_SCREEN_OFF);
f.addAction(Intent.ACTION_SCREEN_ON);
f.addAction(Intent.ACTION_USER_PRESENT);
f.addAction("com.manhmoc.edgebar.SYNC_STATE");
if (Build.VERSION.SDK_INT >= 33)
    registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
else registerReceiver(screenReceiver, f);
        // MediaSession + VolumeProvider ABSOLUTE: AudioService định tuyến phím Volume
        // vào onAdjustVolume() thay vì chỉnh âm lượng thật — API công khai, không cần root,
        // hoạt động cả khi màn tắt vì độc lập với UI.
        mediaSession = new MediaSession(this, "EdgeBarVolKey");
mediaSession.setCallback(new MediaSession.Callback() {});
mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
VolumeProvider provider = new VolumeProvider(
        VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 10, 5) {
    @Override public void onAdjustVolume(int direction) {
        setCurrentVolume(5);
        if (direction > 0) handleSide(true);
        else if (direction < 0) handleSide(false);
    }
};
mediaSession.setPlaybackToRemote(provider);
mediaSession.setPlaybackState(new PlaybackState.Builder()
        .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());	
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
boolean screenOffNow = pm != null && !pm.isInteractive();
// V19.12.3.6.13: CHỈ dựa vào trạng thái màn hình — bỏ hẳn KeyguardManager,
// vì màn sáng luôn phải trả quyền cho OS bất kể có khoá hay không.
// (Cũng bớt 1 lệnh Binder call lúc khởi động service — nhẹ pin hơn)
mediaSession.setActive(screenOffNow);
isRunning = true;
    }
    private void resetBurst() {
    upBurst = 0; downBurst = 0;
    upLongFired = false; downLongFired = false;
}
private Runnable upLongTimeout, downLongTimeout;

private void handleSide(boolean isUp) {
    int burst = (isUp ? upBurst : downBurst) + 1;
    if (isUp) upBurst = burst; else downBurst = burst;

    if (burst == 1) {
        // Lần bấm đầu của burst — hẹn giờ long-press TUYỆT ĐỐI (550ms) kể từ đây,
        // KHÔNG phụ thuộc onAdjustVolume() có gọi lặp thêm hay không. Firmware/driver
        // không đảm bảo gửi key-repeat cho remote volume provider, nên đếm burst >= 5
        // như code cũ có thể không bao giờ đạt được dù đang giữ phím thật.
        Runnable timeout = () -> {
            boolean already = isUp ? upLongFired : downLongFired;
            if (!already) {
                if (isUp) upLongFired = true; else downLongFired = true;
                fire("volkey_" + (isUp ? "up" : "down") + "_long");
            }
        };
        if (isUp) upLongTimeout = timeout; else downLongTimeout = timeout;
        h.postDelayed(timeout, HELD_MS_THRESHOLD);
    }

    Runnable prev = isUp ? upEndCheck : downEndCheck;
    if (prev != null) h.removeCallbacks(prev);
    Runnable check = () -> {
        // Buông phím — huỷ hẹn giờ long-press nếu chưa kịp bắn
        Runnable pendingLong = isUp ? upLongTimeout : downLongTimeout;
        if (pendingLong != null) h.removeCallbacks(pendingLong);

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
        String act = action.split(",")[0].trim();
Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
if (act.startsWith("RUN_SHORTCUT_")) {
    ipc.putExtra("act", "RUN_SHORTCUT");
    ipc.putExtra("shortcut_id", act.substring("RUN_SHORTCUT_".length()));
} else {
    ipc.putExtra("act", act);
}
sendBroadcast(ipc);
    }
    private boolean startForegroundQuiet() {
    try {
        String cid = "eb_volkey";
        NotificationChannel c = new NotificationChannel(cid, "Phím Âm Lượng (Màn tắt)",
                NotificationManager.IMPORTANCE_MIN);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("VolKey")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(91, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(91, n);
        return true;
    } catch (Exception e) {
        isRunning = false;
        stopSelf();
        return false;
    }
}
    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        isRunning = false;
        // SAU:
stopKeepAlive();
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
