      package com.manhmoc.edgebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;

/**
 * V19.12.3.6.37 — Toggle + Pause/Resume + Notification actions + broadcast tick
 * cho chỉ báo ghi âm (chấm đỏ) ở EdgeBarService/HomescreenService.
 * Pause/Resume dùng MediaRecorder.pause()/resume() (API 24+, minSdk app=26 nên luôn có).
 */
public class VoiceRecorderService extends Service {
    public static boolean isRunning = false;
    public static boolean isPaused = false;
    private static final long MAX_DURATION_MS = 60 * 60 * 1000L; // 60 phút

    public static final String ACTION_TOGGLE = "com.manhmoc.edgebar.VOICEREC_TOGGLE";
    public static final String ACTION_PAUSE_TOGGLE = "com.manhmoc.edgebar.VOICEREC_PAUSE_TOGGLE";
    public static final String ACTION_STOP = "com.manhmoc.edgebar.VOICEREC_STOP";
    public static final String ACTION_STOP_AND_PLAY = "com.manhmoc.edgebar.VOICEREC_STOP_PLAY"; // Thêm dòng này
    public static final String ACTION_OPEN_CURRENT = "com.manhmoc.edgebar.VOICEREC_OPEN_CURRENT"; // [MỚI] chạm vào notif để mở bản ghi
    public static final String TICK_ACTION = "com.manhmoc.edgebar.VOICE_REC_TICK";

    private MediaRecorder recorder;
    private PowerManager.WakeLock wakeLock;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable maxDurationGuard;
    private long startTimeMs = 0;
    private long pausedAccumMs = 0;
    private long lastPauseStartMs = 0;
    private Uri pendingUri = null;
    private android.os.ParcelFileDescriptor pfd = null;
    private android.media.session.MediaSession dummySession;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // BẮT BUỘC gọi ngay lập tức, trước mọi xử lý khác (yêu cầu cứng của Android FGS)
        startForegroundNotif();
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) { stopRecording(false); return START_NOT_STICKY; }
        if (ACTION_STOP_AND_PLAY.equals(action)) { stopRecording(true); return START_NOT_STICKY; }
        if (ACTION_OPEN_CURRENT.equals(action)) { openCurrentFile(); return START_NOT_STICKY; } // [MỚI]

        if (ACTION_PAUSE_TOGGLE.equals(action)) {
            if (!isRunning) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }
            if (isPaused) resumeRecording(); else pauseRecording();
            return START_NOT_STICKY;
        }

        // ACTION_TOGGLE hoặc không có action
        if (isRunning) { stopRecording(false); return START_NOT_STICKY; }

        if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)) {
            stopForeground(true); stopSelf(); return START_NOT_STICKY;
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
            pendingUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
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
            pausedAccumMs = 0;
            isRunning = true;
            isPaused = false;

            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EdgeBar:VoiceRec");
            wakeLock.acquire(MAX_DURATION_MS + 5000);

            startTicker();
            maxDurationGuard = () -> stopRecording(false);
            timerHandler.postDelayed(maxDurationGuard, MAX_DURATION_MS);
            broadcastTick("RECORDING", 0);
        } catch (Exception e) {
            cleanupFailedRecording();
            stopForeground(true);
            stopSelf();
        }
    }

    private void pauseRecording() {
        if (Build.VERSION.SDK_INT < 24 || recorder == null) return;
        try {
            recorder.pause();
            isPaused = true;
            lastPauseStartMs = System.currentTimeMillis();
            updateNotif(getElapsedSec(), true);
            broadcastTick("PAUSED", getElapsedSec());
        } catch (Exception ignored) {}
    }

    private void resumeRecording() {
        if (Build.VERSION.SDK_INT < 24 || recorder == null) return;
        try {
            recorder.resume();
            isPaused = false;
            pausedAccumMs += System.currentTimeMillis() - lastPauseStartMs;
            updateNotif(getElapsedSec(), false);
            broadcastTick("RECORDING", getElapsedSec());
        } catch (Exception ignored) {}
    }

    private long getElapsedSec() {
        long now = isPaused ? lastPauseStartMs : System.currentTimeMillis();
        return Math.max(0, (now - startTimeMs - pausedAccumMs) / 1000);
    }

    private void startTicker() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isRunning) return;
                if (!isPaused) {
                    long sec = getElapsedSec();
                    updateNotif(sec, false);
                    broadcastTick("RECORDING", sec);
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    public void stopRecording(boolean playAfter) {
        if (!isRunning) { stopForeground(true); stopSelf(); return; }
        isRunning = false;
        isPaused = false;

        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (maxDurationGuard != null) timerHandler.removeCallbacks(maxDurationGuard);

        try { if (recorder != null) { recorder.stop(); recorder.release(); } } catch (Exception ignored) {}
        recorder = null;
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        pfd = null;

        Uri finalUri = pendingUri; // Giữ lại Uri trước khi reset
        if (pendingUri != null && Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Audio.Media.IS_PENDING, 0);
            try { getContentResolver().update(pendingUri, cv, null, null); } catch (Exception ignored) {}
        }
        pendingUri = null;

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;

        broadcastTick("STOPPED", 0);
        if (dummySession != null) { dummySession.setActive(false); dummySession.release(); dummySession = null; }
        
        // Mở file nếu user bấm nút "Dừng & Nghe"
        if (playAfter && finalUri != null) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(finalUri, "audio/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                i.setPackage("com.google.android.apps.nbu.files");
                startActivity(i);
            } catch (Exception e) {
                try {
                    Intent i2 = new Intent(Intent.ACTION_VIEW);
                    i2.setDataAndType(finalUri, "audio/*");
                    i2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(Intent.createChooser(i2, "Mở bằng").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {}
            }
        }
        
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
        isRunning = false; isPaused = false;
    }

    private void broadcastTick(String state, long sec) {
        Intent i = new Intent(TICK_ACTION);
        i.putExtra("state", state);
        i.putExtra("elapsed_sec", sec);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }

    private PendingIntent actionPI(String action) {
        Intent i = new Intent(this, VoiceRecorderService.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    // [MỚI] Zero-RAM: chạy đúng lúc user chạm notif, không giữ Thread/Handler nào chờ sẵn.
    private void openCurrentFile() {
        if (pendingUri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(pendingUri, "audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setPackage("com.google.android.apps.nbu.files");
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(Intent.ACTION_VIEW);
                i2.setDataAndType(pendingUri, "audio/*");
                i2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(i2, "Mở bằng").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
        }
    }
    private void startForegroundNotif() {
    String cid = "eb_voice_rec_v2";
    NotificationChannel c = new NotificationChannel(cid, "Ghi âm EdgeBar", NotificationManager.IMPORTANCE_LOW);
    c.setSound(null, null);
    c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    getSystemService(NotificationManager.class).createNotificationChannel(c);

    if (dummySession == null) {
            dummySession = new android.media.session.MediaSession(this, "DummySession");
            dummySession.setActive(true);
            
            // TẠO NỀN GRADIENT MIDNIGHT NEON CHỨA ICON
            android.graphics.Bitmap artBmp = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(artBmp);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new android.graphics.LinearGradient(0, 0, 256, 256,
                    new int[]{
                        android.graphics.Color.parseColor("#1A237E"), // Deep Blue
                        android.graphics.Color.parseColor("#7B1FA2"), // Purple
                        android.graphics.Color.parseColor("#03A9F4")  // Neon Blue
                    },
                    null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, 256, 256, 32, 32, paint);

            // Vẽ icon app lên trên cái nền neon đó
            android.graphics.drawable.Drawable d = getDrawable(R.drawable.ic_launcher);
            d.setBounds(32, 32, 224, 224);
            d.draw(canvas);

            dummySession.setMetadata(new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "EdgeBar Recorder")
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Đang hoạt động...")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, MAX_DURATION_MS)
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, artBmp)
                .build());
        }
    dummySession.setPlaybackState(new android.media.session.PlaybackState.Builder()
        .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE | android.media.session.PlaybackState.ACTION_STOP)
        .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build());
        Notification n = new Notification.Builder(this, cid)
            .setContentTitle("🔴 Đang ghi âm — 00:00")
            .setContentText("EdgeBar Voice")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .addAction(android.R.drawable.ic_media_previous, "Dừng", actionPI(ACTION_STOP)) // Nút Trái
            .addAction(isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                    isPaused ? "Tiếp Tục" : "Tạm Dừng", actionPI(ACTION_PAUSE_TOGGLE)) // Nút Giữa
            .addAction(android.R.drawable.ic_media_ff, "Dừng & Nghe", actionPI(ACTION_STOP_AND_PLAY)) // Nút Phải
            .setContentIntent(actionPI(ACTION_OPEN_CURRENT)) 
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(dummySession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    if (Build.VERSION.SDK_INT >= 29)
        startForeground(93, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    else startForeground(93, n);
}

private void updateNotif(long sec, boolean paused) {
    String time = String.format("%02d:%02d", sec / 60, sec % 60);
    String cid = "eb_voice_rec_v2";

    if (dummySession != null) {
        dummySession.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE | android.media.session.PlaybackState.ACTION_STOP)
            .setState(paused ? android.media.session.PlaybackState.STATE_PAUSED : android.media.session.PlaybackState.STATE_PLAYING, sec * 1000, paused ? 0f : 1f).build());
    }
        Notification n = new Notification.Builder(this, cid)
            .setContentTitle((paused ? "⏸️ Đã tạm dừng — " : "🔴 Đang ghi âm — ") + time)
            .setContentText("EdgeBar Voice")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .addAction(android.R.drawable.ic_media_previous, "Dừng", actionPI(ACTION_STOP)) // Nút Trái
            .addAction(paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                    paused ? "Tiếp Tục" : "Tạm Dừng", actionPI(ACTION_PAUSE_TOGGLE)) // Nút Giữa
            .addAction(android.R.drawable.ic_media_ff, "Dừng & Nghe", actionPI(ACTION_STOP_AND_PLAY)) // Nút Phải
            .setContentIntent(actionPI(ACTION_OPEN_CURRENT)) 
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(dummySession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    getSystemService(NotificationManager.class).notify(93, n);
}
    @Override public void onDestroy() {
        if (isRunning) stopRecording(false);
        super.onDestroy();
    }
}
