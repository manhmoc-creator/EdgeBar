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
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Cảm biến vẫy tay (Proximity) — chạy ĐỘC LẬP với Trợ năng/Homeb.
 * Sensor CHỈ được đăng ký khi màn tắt. Không polling, không thread nền.
 */
public class ProximityWaveService extends Service {
    public static boolean isRunning = false;
    private static final String TAG = "EdgeBar_Prox";
    private static final long WAVE_WINDOW_MS = 2500;
    private static final long WAVE_MIN_GAP_MS = 120;
    private static final long POCKET_HOLD_MS = 20000;

    private static final java.util.Set<String> SCREEN_REQUIRED = new java.util.HashSet<>(java.util.Arrays.asList(
        "CAMERA", "SCREENSHOT", "POWER_DIALOG", "NOTIFICATIONS", "QUICK_SETTINGS", "SCAN_QR"));

    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private PowerManager pm;
    private SensorManager sm;
    private Sensor proxSensor, sigSensor, stepSensor;
    private float proxMax = 5f;
    private boolean registered = false;
    private PowerManager.WakeLock wl;
    private long wlUntilMs = 0;

    private int waveCount = 0;
    private boolean lastNear = false;
    private long lastWaveMs = 0;

    private long pocketBlockUntilMs = 0;
    private int stepCount = 0;

    public static boolean hasAnyRule(SharedPreferences p) {
        String csv = p.getString("sensor_prox_pack_ids", "");
        for (String id : csv.split(",")) {
            String t = id.trim();
            if (t.isEmpty()) continue;
            if (p.getBoolean("sensor_prox_pack_" + t + "_en", false)) return true;
        }
        return false;
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!startFg()) return;
        isRunning = true;

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, f);

        if (pm != null && !pm.isInteractive()) registerSensors(); // service sinh ra lúc màn đã tắt
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) registerSensors();
            else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) unregisterSensors();
        }
    };

    private boolean startFg() {
        try {
            String cid = "eb_prox_wave";
            NotificationChannel c = new NotificationChannel(cid, "Cảm biến vẫy tay", NotificationManager.IMPORTANCE_MIN);
            c.setSound(null, null); c.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
            Notification n = new Notification.Builder(this, cid)
                .setContentTitle("Proximity")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(96, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(96, n);
            return true;
        } catch (Exception e) { isRunning = false; stopSelf(); return false; }
    }

    // ---------- WAKELOCK CÓ HẠN (chỉ giữ CPU đúng lúc cần, KHÔNG giữ thường trực) ----------
    private void holdCpu(long ms) {
        try {
            if (wl == null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeBar:ProxWave");
                wl.setReferenceCounted(false);
            }
            long until = SystemClock.elapsedRealtime() + ms;
            if (until <= wlUntilMs) return;
            wlUntilMs = until;
            wl.acquire(ms);
        } catch (Exception ignored) {}
    }
    private void releaseCpu() {
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
        wlUntilMs = 0;
    }

    // ---------- ĐĂNG KÝ / HUỶ SENSOR ----------
    private void registerSensors() {
        if (registered || !hasAnyRule(prefs)) return;
        if (sm == null) sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        // Ưu tiên bản WAKE-UP: sensor tự đánh thức CPU khi có event, khỏi giữ wakelock
        proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
        boolean wakeUp = proxSensor != null;
        if (proxSensor == null) proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proxSensor == null) { Log.w(TAG, "Máy không có cảm biến tiệm cận"); return; }
        proxMax = proxSensor.getMaximumRange();
        try {
            sm.registerListener(proxListener, proxSensor, SensorManager.SENSOR_DELAY_NORMAL, h);
        } catch (Exception e) { Log.w(TAG, "register prox failed", e); return; }

        if (!wakeUp) {
            // Máy hiếm không có wake-up prox: buộc giữ wakelock thì mới nhận được event
            try {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeBar:ProxWave");
                wl.setReferenceCounted(false);
                wl.acquire();
            } catch (Exception ignored) {}
        }

        if (prefs.getBoolean("sensor_pocketmode_en", true) && hasActRecPerm()) {
            try {
                sigSensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
                stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
                armSigMotion();
            } catch (Exception e) { Log.w(TAG, "pocket sensors failed", e); }
        }
        registered = true;
        lastNear = false; waveCount = 0; pocketBlockUntilMs = 0;
        Log.d(TAG, "REGISTERED prox wakeUp=" + wakeUp + " max=" + proxMax);
    }

    private void unregisterSensors() {
        if (!registered) return;
        try { sm.unregisterListener(proxListener); } catch (Exception ignored) {}
        try { sm.unregisterListener(stepListener); } catch (Exception ignored) {}
        try { if (sigSensor != null) sm.cancelTriggerSensor(sigTrigger, sigSensor); } catch (Exception ignored) {}
        h.removeCallbacksAndMessages(null);
        waveCount = 0; lastNear = false; pocketBlockUntilMs = 0; stepCount = 0;
        releaseCpu();
        registered = false;
        Log.d(TAG, "UNREGISTERED");
    }

    private boolean hasActRecPerm() {
        return Build.VERSION.SDK_INT < 29
            || checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    // ---------- VẪY TAY ----------
    private final SensorEventListener proxListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.values.length == 0) return;
            boolean near = e.values[0] < proxMax;
            if (!near) { lastNear = false; return; }
            if (lastNear) return;
            lastNear = true;
            if (SystemClock.elapsedRealtime() < pocketBlockUntilMs) return; // đang trong túi/đang đi
            long now = SystemClock.elapsedRealtime();
            if (now - lastWaveMs < WAVE_MIN_GAP_MS) return;
            lastWaveMs = now;
            waveCount++;
            Log.d(TAG, "near #" + waveCount);
            holdCpu(WAVE_WINDOW_MS + 800); // GIỮ CPU để timer 2.5s chắc chắn chạy
            h.removeCallbacks(commitRunnable);
            h.postDelayed(commitRunnable, WAVE_WINDOW_MS);
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final Runnable commitRunnable = () -> {
        int n = Math.min(waveCount, 4);
        waveCount = 0;
        if (n > 0) { holdCpu(1500); fireWave(n); }
    };

    // ---------- POCKET MODE (tự hết hạn theo thời gian, không kẹt) ----------
    private void armSigMotion() {
        if (sm == null || sigSensor == null) return;
        try { sm.requestTriggerSensor(sigTrigger, sigSensor); } catch (Exception ignored) {}
    }
    private final TriggerEventListener sigTrigger = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent event) {
            h.post(() -> {
                if (!registered) return;
                long now = SystemClock.elapsedRealtime();
                if (now < pocketBlockUntilMs) pocketBlockUntilMs = now + POCKET_HOLD_MS; // đang đi -> gia hạn, khỏi đếm bước
                else startStepWindow();
                armSigMotion(); // one-shot -> phải gắn lại
            });
        }
    };
    private void startStepWindow() {
        if (stepSensor == null) return;
        int windowSec = Math.max(1, prefs.getInt("sensor_pocket_window_sec", 8));
        stepCount = 0;
        holdCpu(windowSec * 1000L + 1000);
        try { sm.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, h); }
        catch (Exception e) { return; }
        h.removeCallbacks(stepEndRunnable);
        h.postDelayed(stepEndRunnable, windowSec * 1000L);
    }
    private final SensorEventListener stepListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) { stepCount++; }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };
    private final Runnable stepEndRunnable = () -> {
        try { sm.unregisterListener(stepListener); } catch (Exception ignored) {}
        if (stepCount >= prefs.getInt("sensor_pocket_step_threshold", 3))
            pocketBlockUntilMs = SystemClock.elapsedRealtime() + POCKET_HOLD_MS;
        stepCount = 0;
    };

    // ---------- THỰC THI ACTION ----------
    private void fireWave(int n) {
        final String want = "wave" + n;
        for (String rawId : prefs.getString("sensor_prox_pack_ids", "").split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) continue;
            final String px = "sensor_prox_pack_" + id + "_";
            if (!prefs.getBoolean(px + "en", false)) continue;
            if (!want.equals(prefs.getString(px + "gesture", ""))) continue;
            String action = prefs.getString(px + "action", "NONE");
            if (action.equals("NONE")) return;
            final String act = action.split(",")[0].trim();

            boolean screenOff = pm != null && !pm.isInteractive();
            boolean needScreen = SCREEN_REQUIRED.contains(act);
            if (prefs.getBoolean(px + "vib", true)) vibrate(prefs.getInt("vib_dur", 30));

            if (screenOff && (needScreen || act.equals("SCREEN_ON"))) {
                try {
                    PowerManager.WakeLock w = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:ProxWake");
                    w.acquire(3000);
                } catch (Exception ignored) {}
                if (act.equals("SCREEN_ON")) return; // bật màn là xong
            }
            final boolean animOk = !screenOff || needScreen; // màn tắt mà vẽ Anima là phí pin
            Runnable doFire = () -> {
                if (animOk && prefs.getBoolean(px + "anim", true))
                    sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName()));
                Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                ipc.putExtra("act", act);
                if ("LAUNCH_APP".equals(act)) ipc.putExtra("launch_pkg", prefs.getString(px + "launch_pkg", ""));
                sendBroadcast(ipc);
            };
            if (screenOff && needScreen) h.postDelayed(doFire, 350); else doFire.run();
            Log.d(TAG, "FIRE " + want + " -> " + act);
            return;
        }
    }

    private void vibrate(int ms) {
        if (ms <= 0) return;
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        isRunning = false;
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        unregisterSensors();
        super.onDestroy();
    }
}
