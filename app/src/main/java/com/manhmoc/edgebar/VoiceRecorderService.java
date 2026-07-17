      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.List;

/**
 * VoiceRecorderService — SIÊU NHẸ, KHÔNG dùng Accessibility để ghi âm
 * (Accessibility chỉ dùng để bắt trigger double-tap, đã có sẵn ở EdgeBarService).
 *
 * Battery/RAM Pixel 2XL:
 * - MediaRecorder AAC 16kHz mono, không giữ buffer PCM thô trong RAM
 * - PARTIAL_WAKE_LOCK chỉ giữ trong lúc ghi, release() ngay khi dừng
 * - Overlay đếm giờ chỉ 1 TextView, update mỗi 1000ms (không phải mỗi frame)
 * - Giới hạn cứng 60 phút/lần ghi để tránh file khổng lồ + hao pin khi quên tắt
 * - AudioRecordingCallback: phát hiện xung đột với Quay màn hình / app khác
 *   → tự stop(), KHÔNG tranh giành mic
 */
public class VoiceRecorderService extends Service {
    public static boolean isRunning = false;
    private static final long MAX_DURATION_MS = 60 * 60 * 1000L; // 60 phút

    private MediaRecorder recorder;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audioManager;
    private AudioManager.AudioRecordingCallback recCallback;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable maxDurationGuard;
    private long startTimeMs = 0;
    private Uri pendingUri = null;
    private android.os.ParcelFileDescriptor pfd = null;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
    // BẮT BUỘC: gọi startForeground() NGAY LẬP TỨC, trước bất kỳ thao tác nào khác.
    // Đây là yêu cầu cứng của Android — trễ quá 5s sẽ bị hệ thống kill toàn bộ tiến trình
    // (ForegroundServiceDidNotStartInTimeException — không try/catch được).
    startForegroundNotif();

    if (isRunning) {
        stopRecording();
        return START_NOT_STICKY;
    }

    // Kiểm tra quyền TRƯỚC khi đụng vào MediaRecorder — tránh SecurityException giữa chừng
    if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
        stopForeground(true);
        stopSelf();
        return START_NOT_STICKY;
    }

    startRecording();
    return START_NOT_STICKY;
}

private void startRecording() {
    try {
        String fileName = "EdgeBar_" + System.currentTimeMillis() + ".m4a";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        cv.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/EdgeBar");
            cv.put(MediaStore.Audio.Media.IS_PENDING, 1);
        }
        pendingUri = getContentResolver().insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
        if (pendingUri == null) { stopForeground(true); stopSelf(); return; }

        pfd = getContentResolver().openFileDescriptor(pendingUri, "w");
        if (pfd == null) { stopForeground(true); stopSelf(); return; }

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioSamplingRate(16000);
        recorder.setAudioEncodingBitRate(32000);
        recorder.setOutputFile(pfd.getFileDescriptor());
        recorder.prepare();
        recorder.start();

        startTimeMs = System.currentTimeMillis();
        isRunning = true;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeBar:VoiceRec");
        wakeLock.acquire(MAX_DURATION_MS + 5000);

        registerAudioConflictWatcher();
        startTimerOverlay();

        maxDurationGuard = this::stopRecording;
        timerHandler.postDelayed(maxDurationGuard, MAX_DURATION_MS);

    } catch (Exception e) {
        cleanupFailedRecording();
        stopForeground(true);
        stopSelf();
    }
}
    // Phát hiện xung đột: Quay màn hình hoặc app khác vừa mở phiên ghi âm khác
    private void registerAudioConflictWatcher() {
        if (Build.VERSION.SDK_INT < 24) return;
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        recCallback = new AudioManager.AudioRecordingCallback() {
            @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                // configs bao gồm CẢ phiên của chính mình — nếu > 1 nghĩa là có phiên khác
                if (isRunning && configs.size() > 1) {
                    timerHandler.post(() -> {
                        Toast.makeText(VoiceRecorderService.this,
                            "Phát hiện ứng dụng khác đang dùng mic (VD: Quay màn hình) — " +
                            "EdgeBar tự dừng ghi âm để nhường mic.", Toast.LENGTH_LONG).show();
                        stopRecording();
                    });
                }
            }
        };
        audioManager.registerAudioRecordingCallback(recCallback, timerHandler);
    }

    private void startTimerOverlay() {
        // Overlay đơn giản: chấm đỏ + mm:ss, cập nhật 1 lần/giây — KHÔNG animation liên tục
        Intent updateIntent = new Intent("com.manhmoc.edgebar.VOICE_REC_TICK");
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isRunning) return;
                long sec = (System.currentTimeMillis() - startTimeMs) / 1000;
                updateNotif(sec);
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    public void stopRecording() {
        if (!isRunning) return;
        isRunning = false;

        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (maxDurationGuard != null) timerHandler.removeCallbacks(maxDurationGuard);

        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignored) {
            // stop() có thể ném exception nếu ghi quá ngắn (<1s) — file vẫn có thể dùng được, bỏ qua
        }
        recorder = null;

        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        pfd = null;

        if (pendingUri != null && Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.IS_PENDING, 0);
            try { getContentResolver().update(pendingUri, cv, null, null); } catch (Exception ignored) {}
        }
        pendingUri = null;

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;

        if (audioManager != null && recCallback != null) {
            try { audioManager.unregisterAudioRecordingCallback(recCallback); } catch (Exception ignored) {}
        }
        recCallback = null;

        stopForeground(true);
        stopSelf();
    }

    private void cleanupFailedRecording() {
        try { if (recorder != null) { recorder.reset(); recorder.release(); } } catch (Exception ignored) {}
        recorder = null;
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        if (pendingUri != null) {
            try { getContentResolver().delete(pendingUri, null, null); } catch (Exception ignored) {}
        }
        isRunning = false;
    }

    private void startForegroundNotif() {
        String cid = "eb_voice_rec";
        NotificationChannel c = new NotificationChannel(cid, "Ghi âm EdgeBar",
                NotificationManager.IMPORTANCE_LOW);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("🔴 Đang ghi âm — 00:00")
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(93, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else
            startForeground(93, n);
    }

    private void updateNotif(long sec) {
        String time = String.format("%02d:%02d", sec / 60, sec % 60);
        String cid = "eb_voice_rec";
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("🔴 Đang ghi âm — " + time)
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setOngoing(true)
                .build();
        getSystemService(NotificationManager.class).notify(93, n);
    }

    @Override public void onDestroy() {
        if (isRunning) stopRecording();
        super.onDestroy();
    }
}
