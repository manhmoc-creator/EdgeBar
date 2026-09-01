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

public class VolumeButtonService extends Service {
    public static boolean isRunning = false;

    private MediaSession mediaSession;
    private SharedPreferences prefs;
    private BroadcastReceiver screenReceiver;
    private final Handler h = new Handler(Looper.getMainLooper());
    
    // --- THUẬT TOÁN ĐIỀU KHIỂN VOLKEY SIÊU NHẠY ---
    // MỚI
private static final long DEBOUNCE_MS = 60; // Lọc nhiễu cơ học của nút cứng
private static final long MAX_WAIT_MS = 650; // Chờ nhịp 2 tối đa — đủ thời gian bấm 2 phím vật lý khác nhau

    private int pendingKey = 0; // 0: rảnh, 1: up, -1: down
    private long lastPhysicalEventMs = 0;
    private Runnable actionRunnable = null;
    
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
                kaWakeLock.acquire(3000); 
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

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        if (!startForegroundQuiet()) return; 
        
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String act = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(act)) {
                    if (mediaSession != null) mediaSession.setActive(true);
                    startKeepAlive();
                } else if (Intent.ACTION_SCREEN_ON.equals(act) || Intent.ACTION_USER_PRESENT.equals(act)) {
                    stopKeepAlive();
                    if (mediaSession != null) mediaSession.setActive(false);
                    resetBurst();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, f);
        
        mediaSession = new MediaSession(this, "EdgeBarVolKey");
        mediaSession.setCallback(new MediaSession.Callback() {});
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        
        VolumeProvider provider = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, 10, 5) {
            @Override public void onAdjustVolume(int direction) {
                setCurrentVolume(5);
                if (direction > 0) handleSide(true);
                else if (direction < 0) handleSide(false);
            }
        };

        mediaSession.setPlaybackToRemote(provider);
        mediaSession.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build());  
        
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean screenOffNow = pm != null && !pm.isInteractive();
        mediaSession.setActive(screenOffNow && !MyPlaylistService.isRunning);
        isRunning = true;
    }

    private boolean isRuleSet(String key) {
        return !prefs.getString(key, "NONE").equals("NONE");
    }

    private void handleSide(boolean isUp) {
        h.post(() -> handleSideInternal(isUp));
    }

    private void handleSideInternal(boolean isUp) {
        int currentKey = isUp ? 1 : -1;
        long now = android.os.SystemClock.elapsedRealtime();

        if (currentKey == pendingKey && (now - lastPhysicalEventMs) < DEBOUNCE_MS) {
            return; 
        }
        lastPhysicalEventMs = now;

        String prefix = isUp ? "volkey_up" : "volkey_down";
        boolean hasDtap = isRuleSet(prefix + "_dtap");
        boolean hasCombo = isRuleSet(prefix + "_combo");

        if (pendingKey == 0) {
            pendingKey = currentKey;
            vibrateAck(); 

            if (!hasDtap && !hasCombo) {
                executeAndResetState(prefix + "_tap");
            } else {
                actionRunnable = () -> executeAndResetState(prefix + "_tap");
                h.postDelayed(actionRunnable, MAX_WAIT_MS);
            }
        } else {
            if (actionRunnable != null) {
                h.removeCallbacks(actionRunnable);
                actionRunnable = null;
            }

            if (pendingKey == currentKey) {
                executeAndResetState(prefix + "_dtap");
            } else {
                String comboActionKey = (pendingKey == 1) ? "volkey_up_combo" : "volkey_down_combo";
                executeAndResetState(comboActionKey);
            }
        }
    }

    private void executeAndResetState(String prefKey) {
        pendingKey = 0;
        actionRunnable = null;
        fire(prefKey);
    }

    private void resetBurst() {
        if (actionRunnable != null) {
            h.removeCallbacks(actionRunnable);
            actionRunnable = null;
        }
        pendingKey = 0;
        lastPhysicalEventMs = 0;
    }

    private void vibrateAck() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(12, 40));
            else v.vibrate(12);
        } catch (Exception ignored) {}
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
            NotificationChannel c = new NotificationChannel(cid, "Phím Âm Lượng (Màn tắt)", NotificationManager.IMPORTANCE_MIN);
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
        stopKeepAlive();
        resetBurst();
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    public static boolean hasAnyRule(SharedPreferences p) {
        String[] keys = {
            "volkey_up_tap", "volkey_up_dtap", "volkey_up_combo",
            "volkey_down_tap", "volkey_down_dtap", "volkey_down_combo"
        };
        for (String k : keys) if (!p.getString(k, "NONE").equals("NONE")) return true;
        return false;
    }
}
