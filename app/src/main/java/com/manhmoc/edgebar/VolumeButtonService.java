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
private static final long KEEP_ALIVE_INTERVAL_MS = 12000;
private android.os.PowerManager.WakeLock kaWakeLock;
private void startKeepAlive() {
    stopKeepAlive();
    keepAliveRunnable = () -> {
    try {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        kaWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "EdgeBar:VolKeyAlive");
        kaWakeLock.acquire(3000); // giữ CPU thức đủ lâu để setPlaybackState thực sự được ghi xuống AudioService trước khi Doze cắt
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f).build());
        }
    } catch (Exception ignored) {}
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
// Cửa sổ chờ cú bấm thứ 2 — PHẢI ngắn hơn HELD_MS_THRESHOLD để timer Long Press
// và timer Double-Tap không bao giờ đụng độ nhau nữa (đây là nguyên nhân gốc
// khiến dtap không hoạt động ổn định). Rẻ pin hơn REPEAT_WINDOW_MS cũ vì Handler
// chỉ thức dậy sớm hơn 1 chút, không tạo thêm callback nào so với bản gốc.
private static final long DTAP_GAP_MS = 260;

private void handleSide(boolean isUp) {
    int burst = (isUp ? upBurst : downBurst) + 1;
    if (isUp) upBurst = burst; else downBurst = burst;

    // FIX GỐC: hễ có sự kiện mới tới (kể cả cú bấm thứ 2) là HUỶ NGAY timer Long
    // Press cũ trước tiên. Nhờ vậy khi burst >= 2 xảy ra, Long Press chắc chắn
    // không thể bắn nhầm nữa — chỉ còn đúng 1 con đường thắng.
    Runnable pendingLong = isUp ? upLongTimeout : downLongTimeout;
    if (pendingLong != null) h.removeCallbacks(pendingLong);

    Runnable prevEnd = isUp ? upEndCheck : downEndCheck;
    if (prevEnd != null) h.removeCallbacks(prevEnd);

    if (burst == 1) {
        Runnable timeout = () -> {
            // Chỉ bắn Long khi burst tại thời điểm timeout nổ vẫn còn đúng 1 —
            // nếu có cú bấm thứ 2 xen vào, handleSide() đã huỷ timer này rồi.
            boolean already = isUp ? upLongFired : downLongFired;
            if (!already && (isUp ? upBurst : downBurst) == 1) {
                if (isUp) upLongFired = true; else downLongFired = true;
                fire("volkey_" + (isUp ? "up" : "down") + "_long");
                if (isUp) upBurst = 0; else downBurst = 0;
            }
        };
        if (isUp) upLongTimeout = timeout; else downLongTimeout = timeout;
        h.postDelayed(timeout, HELD_MS_THRESHOLD);
    }

    Runnable check = () -> {
        boolean wasLong = isUp ? upLongFired : downLongFired;
        int finalBurst = isUp ? upBurst : downBurst;
        if (!wasLong && finalBurst > 0) {
            if (finalBurst >= 2) fire("volkey_" + (isUp ? "up" : "down") + "_dtap");
            else fire("volkey_" + (isUp ? "up" : "down") + "_tap");
        }
        if (isUp) { upBurst = 0; upLongFired = false; }
        else { downBurst = 0; downLongFired = false; }
    };
    if (isUp) upEndCheck = check; else downEndCheck = check;
    h.postDelayed(check, DTAP_GAP_MS);
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
