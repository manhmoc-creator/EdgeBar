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
 * Kiểu Wave Up: dùng sensor WAKE-UP (sensor hub tự đánh thức CPU), KHÔNG giữ WakeLock
 * thường trực. Chỉ đăng ký sensor khi màn TẮT.
 * Đếm 1 lần vẫy = tay che rồi RÚT RA trong <= MAX_NEAR_MS (che lâu = túi/áp mặt -> bỏ qua).
 */
public class ProximityWaveService extends Service {
    public static boolean isRunning = false;
    private static final String TAG = "EdgeBar_Prox";

    private static final long MAX_NEAR_MS = 900;     // che lâu hơn = không phải vẫy
    private static final long WAVE_GAP_MS = 650;     // im lặng bấy lâu sau nhịp cuối thì chốt số lần vẫy
    private static final long ARM_GRACE_MS = 1000;   // bỏ qua nhiễu do tay/nút nguồn ngay lúc tắt màn
    private static final long POCKET_HOLD_MS = 20000;
    private static final float NEAR_CM = 3f;

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
    private int maxWaveNeeded = 1;
    private boolean lastNear = false;
    private long nearSinceMs = 0;
    private long armedAtMs = 0;

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

    /** Số lần vẫy lớn nhất mà user đã gán (1..4). Đạt mức này là chạy ngay, khỏi chờ. */
    private int computeMaxWave() {
        int max = 1;
        for (String rawId : prefs.getString("sensor_prox_pack_ids", "").split(",")) {
            String id = rawId.trim();
            if (id.isEmpty()) continue;
            String px = "sensor_prox_pack_" + id + "_";
            if (!prefs.getBoolean(px + "en", false)) continue;
            try { max = Math.max(max, Integer.parseInt(prefs.getString(px + "gesture", "wave1").substring(4))); }
            catch (Exception ignored) {}
        }
        return Math.min(max, 4);
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

        Log.d(TAG, "Service created, interactive=" + (pm != null && pm.isInteractive()));
        if (pm != null && !pm.isInteractive()) registerSensors();
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
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            isRunning = false; stopSelf(); return false;
        }
    }

    // ---------- WAKELOCK NGẮN HẠN (chỉ giữ CPU đúng khoảng cần chạy timer) ----------
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
        proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true); // bản wake-up
        boolean wakeUp = proxSensor != null;
        if (proxSensor == null) proxSensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proxSensor == null) { Log.w(TAG, "Máy không có cảm biến tiệm cận"); return; }
        proxMax = proxSensor.getMaximumRange();

        boolean ok = false;
        try {
            ok = sm.registerListener(proxListener, proxSensor, SensorManager.SENSOR_DELAY_NORMAL, h);
        } catch (Exception e) { Log.w(TAG, "register prox failed", e); }
        if (!ok) { Log.w(TAG, "registerListener trả về false"); return; }

        if (!wakeUp) {
            // Sensor không-wake-up chỉ nhận event khi CPU thức -> bắt buộc giữ wakelock (tốn pin)
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
        maxWaveNeeded = computeMaxWave();
        armedAtMs = SystemClock.elapsedRealtime() + ARM_GRACE_MS;
        Log.d(TAG, "REGISTERED wakeUp=" + wakeUp + " max=" + proxMax + " maxWaveNeeded=" + maxWaveNeeded);
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

    // ---------- VẪY TAY: đếm khi tay RÚT RA ----------
    private final SensorEventListener proxListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (e.values.length == 0) return;
            long now = SystemClock.elapsedRealtime();
            boolean near = e.values[0] < Math.min(proxMax, NEAR_CM);

            if (near) {
                if (!lastNear) {
                    lastNear = true;
                    nearSinceMs = now;
                    h.removeCallbacks(commitRunnable); // đang vẫy tiếp -> hoãn chốt
                }
                return;
            }
            if (!lastNear) return;           // far -> far: bỏ qua
            lastNear = false;
            long dur = now - nearSinceMs;

            // Không phải vẫy: che quá lâu / che từ lúc vừa tắt màn / đang trong túi hoặc đang đi
            if (nearSinceMs < armedAtMs || dur > MAX_NEAR_MS || now < pocketBlockUntilMs) {
                Log.d(TAG, "ignore near dur=" + dur);
                waveCount = 0;
                h.removeCallbacks(commitRunnable);
                return;
            }
            waveCount++;
            Log.d(TAG, "wave #" + waveCount + " dur=" + dur);
            h.removeCallbacks(commitRunnable);
            if (waveCount >= maxWaveNeeded) {     // đủ số lần tối đa đã gán -> chạy NGAY
                commitRunnable.run();
            } else {
                holdCpu(WAVE_GAP_MS + 400);       // chỉ giữ CPU ~1 giây
                h.postDelayed(commitRunnable, WAVE_GAP_MS);
            }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    private final Runnable commitRunnable = () -> {
        int n = Math.min(waveCount, 4);
        waveCount = 0;
        if (n > 0) fireWave(n);
    };

    // ---------- POCKET MODE ----------
    private void armSigMotion() {
        if (sm == null || sigSensor == null) return;
        try { sm.requestTriggerSensor(sigTrigger, sigSensor); } catch (Exception ignored) {}
    }
    private final TriggerEventListener sigTrigger = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent event) {
            h.post(() -> {
                if (!registered) return;
                long now = SystemClock.elapsedRealtime();
                if (now < pocketBlockUntilMs) pocketBlockUntilMs = now + POCKET_HOLD_MS;
                else startStepWindow();
                armSigMotion();
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

    // ---------- CHẠY HÀNH ĐỘNG ----------
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

            final String[] acts = action.split(",");
            boolean screenOff = pm != null && !pm.isInteractive();
            boolean needScreen = false, hasScreenOn = false;
            for (String a : acts) {
                String t = a.trim();
                if (SCREEN_REQUIRED.contains(t)) needScreen = true;
                if (t.equals("SCREEN_ON")) hasScreenOn = true;
            }

            holdCpu(2000 + acts.length * 120L); // đủ cho delay 350ms + chuỗi action
            if (prefs.getBoolean(px + "vib", true)) vibrate(prefs.getInt("vib_dur", 30));

            if (screenOff && (needScreen || hasScreenOn)) {
                try {
                    PowerManager.WakeLock w = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "EdgeBar:ProxWake");
                    w.acquire(3000);
                } catch (Exception ignored) {}
                if (hasScreenOn && acts.length == 1) { Log.d(TAG, "FIRE " + want + " -> SCREEN_ON"); return; }
            }

            final boolean animOk = !screenOff || needScreen;
            Runnable doFire = () -> {
                int delay = 0;
                for (String actRaw : acts) {
                    final String act = actRaw.trim();
                    if (act.isEmpty()) continue;
                    h.postDelayed(() -> {
                        if (animOk && prefs.getBoolean(px + "anim", true))
                            sendBroadcast(new Intent("com.manhmoc.edgebar.TEST_ANIM").setPackage(getPackageName()));
                        Intent ipc = new Intent("com.manhmoc.edgebar.IPC_ACTION");
                        ipc.putExtra("act", act);
                        if ("LAUNCH_APP".equals(act)) ipc.putExtra("launch_pkg", prefs.getString(px + "launch_pkg", ""));
                        if ("RUN_SHORTCUT".equals(act)) ipc.putExtra("shortcut_id", prefs.getString(px + "shortcut_id", ""));
                        sendBroadcast(ipc);
                    }, delay);
                    delay += 120;
                }
            };
            if (screenOff && needScreen) h.postDelayed(doFire, 350); else doFire.run();

            Log.d(TAG, "FIRE " + want + " -> " + action);
            return;
        }
        Log.d(TAG, "Không có rule cho " + want);
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
