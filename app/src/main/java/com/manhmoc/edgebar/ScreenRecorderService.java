package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.widget.Toast;

public class ScreenRecorderService extends Service {
    public static boolean isRunning = false;
    private static final long MAX_DURATION_MS = 30 * 60 * 1000L; // 30 phút, tránh file khổng lồ

    private MediaProjection mediaProjection;
    private MediaRecorder recorder;
    private VirtualDisplay virtualDisplay;
    private ParcelFileDescriptor pfd;
    private Uri pendingUri;
    private long startTimeMs;
    private long pausedAccumMs = 0, lastPauseStartMs = 0;
    public static boolean isPaused = false;
    private int prevShowTouches = -1; // giá trị gốc để khôi phục sau khi ghi xong
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable, maxGuard;
    private android.media.session.MediaSession dummySession;

    public static final String ACTION_PAUSE_TOGGLE = "com.manhmoc.edgebar.SCREENREC_PAUSE_TOGGLE";
    public static final String ACTION_STOP = "com.manhmoc.edgebar.SCREENREC_STOP";
    public static final String ACTION_STOP_AND_PLAY = "com.manhmoc.edgebar.SCREENREC_STOP_PLAY"; // Thêm dòng này
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || "STOP".equals(action)) { stopRecording(false); return START_NOT_STICKY; }
        if (ACTION_STOP_AND_PLAY.equals(action)) { stopRecording(true); return START_NOT_STICKY; }
        if (isRunning || intent == null) return START_NOT_STICKY;
        startForegroundNotif(0);

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");
        if (data == null) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }

        // [FIX ANDROID 14] Bắt buộc đăng ký Callback TRƯỚC khi gọi createVirtualDisplay(),
        // nếu không hệ thống sẽ ném IllegalStateException khiến quay màn hình thất bại âm thầm.
        // onStop() cũng cần thiết vì Android 14 có thể tự dừng projection (VD: user tắt qua
        // notification hệ thống) — lúc đó phải dọn dẹp service theo, tránh giữ tài nguyên "ma".
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopRecording(false); }
        }, new Handler(Looper.getMainLooper()));

        if (!startRecorder()) {
            Toast.makeText(this, "Không thể bắt đầu quay màn hình", Toast.LENGTH_SHORT).show();
            stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }
        isRunning = true;
        startTimeMs = System.currentTimeMillis();
        startTimerNotif();
        maxGuard = () -> stopRecording(false);
        h.postDelayed(maxGuard, MAX_DURATION_MS);
        return START_NOT_STICKY;
    }

    private boolean startRecorder() {
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        boolean audioOn = prefs.getBoolean("screenrec_audio_en", false);
        boolean showTouches = prefs.getBoolean("screenrec_showtouches_en", true);
        try {
            try {
                prevShowTouches = android.provider.Settings.System.getInt(
                    getContentResolver(), "show_touches", 0);
                android.provider.Settings.System.putInt(
                    getContentResolver(), "show_touches", showTouches ? 1 : 0);
            } catch (Exception ignored) {} // cần WRITE_SECURE_SETTINGS, đã có sẵn trong app

            String fileName = "EdgeBar_" + System.currentTimeMillis() + ".mp4";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EdgeBar");
                cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            }
            pendingUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (pendingUri == null) return false;

            pfd = getContentResolver().openFileDescriptor(pendingUri, "w");
            if (pfd == null) return false;

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int width = metrics.widthPixels, height = metrics.heightPixels, density = metrics.densityDpi;

            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (audioOn) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (audioOn) recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(6_000_000);
            recorder.setOutputFile(pfd.getFileDescriptor());
            recorder.prepare();
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "EdgeBarRecord", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.getSurface(), null, null);

            recorder.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
private void pauseRecording() {
        if (Build.VERSION.SDK_INT < 24 || recorder == null || isPaused) return;
        try {
            recorder.pause();
            isPaused = true;
            lastPauseStartMs = System.currentTimeMillis();
            broadcastRecTick("PAUSED", getElapsedSec());
            startForegroundNotif(getElapsedSec());
        } catch (Exception ignored) {}
    }
    private void resumeRecording() {
        if (Build.VERSION.SDK_INT < 24 || recorder == null || !isPaused) return;
        try {
            recorder.resume();
            isPaused = false;
            pausedAccumMs += System.currentTimeMillis() - lastPauseStartMs;
            broadcastRecTick("RECORDING", getElapsedSec());
        } catch (Exception ignored) {}
    }
    private long getElapsedSec() {
        long now = isPaused ? lastPauseStartMs : System.currentTimeMillis();
        return Math.max(0, (now - startTimeMs - pausedAccumMs) / 1000);
    }
    // [DÙNG CHUNG] tận dụng đúng chỉ báo ghi âm (chấm đỏ + mm:ss) đã có sẵn
    private void broadcastRecTick(String state, long sec) {
        Intent i = new Intent(VoiceRecorderService.TICK_ACTION);
        i.putExtra("state", state);
        i.putExtra("elapsed_sec", sec);
        i.setPackage(getPackageName());
        sendBroadcast(i);
    }
    private void startTimerNotif() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isRunning) return;
                if (!isPaused) {
                    long sec = getElapsedSec();
                    startForegroundNotif(sec);
                    broadcastRecTick("RECORDING", sec);
                }
                h.postDelayed(this, 1000);
            }
        };
        h.post(timerRunnable);
        broadcastRecTick("RECORDING", 0);
    }
    private void stopRecording(boolean playAfter) {
        if (!isRunning) { stopForeground(true); stopSelf(); return; }
        isRunning = false;
        isPaused = false;
        broadcastRecTick("STOPPED", 0);
        if (prevShowTouches != -1) {
            try { android.provider.Settings.System.putInt(getContentResolver(), "show_touches", prevShowTouches); } catch (Exception ignored) {}
            prevShowTouches = -1;
        }
        if (timerRunnable != null) h.removeCallbacks(timerRunnable);
        if (maxGuard != null) h.removeCallbacks(maxGuard);

        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;

        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        virtualDisplay = null;

        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        pfd = null;

        Uri finalUri = pendingUri; // Giữ Uri lại
        if (pendingUri != null && Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.IS_PENDING, 0);
            try { getContentResolver().update(pendingUri, cv, null, null); } catch (Exception ignored) {}
        }
        pendingUri = null;

        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception ignored) {}
        mediaProjection = null;

        if (dummySession != null) { dummySession.setActive(false); dummySession.release(); dummySession = null; }
        Toast.makeText(this, "Đã lưu video quay màn hình", Toast.LENGTH_SHORT).show();

        // Mở file nếu user bấm "Dừng & Xem"
        if (playAfter && finalUri != null) {
            try {
                Intent openIntent = new Intent(Intent.ACTION_VIEW);
                openIntent.setDataAndType(finalUri, "video/*");
                openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(openIntent);
            } catch (Exception ignored) {}
        }

        stopForeground(true);
        stopSelf();
    }
private PendingIntent contentTapPI() {
    Intent i = new Intent(Intent.ACTION_VIEW);
    if (pendingUri != null) i.setDataAndType(pendingUri, "video/*");
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    return PendingIntent.getActivity(this, 9102, i, flags);
}
    private android.app.PendingIntent screenRecActionPI(String action) {
        Intent i = new Intent(this, ScreenRecorderService.class);
        i.setAction(action);
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
        return android.app.PendingIntent.getService(this, action.hashCode(), i, flags);
    }
    private void startForegroundNotif(long sec) {
    String cid = "eb_screen_rec_v2";
    NotificationChannel c = new NotificationChannel(cid, "Quay màn hình", NotificationManager.IMPORTANCE_LOW);
    c.setSound(null, null);
    c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    getSystemService(NotificationManager.class).createNotificationChannel(c);

    if (dummySession == null) {
            dummySession = new android.media.session.MediaSession(this, "DummySession");
            dummySession.setActive(true);
            
            // TẠO NỀN GRADIENT ĐỎ CAM NHẸ NHÀNG CHỨA ICON
            android.graphics.Bitmap artBmp = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(artBmp);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new android.graphics.LinearGradient(0, 0, 256, 256,
                    android.graphics.Color.parseColor("#FF5252"), // Đỏ san hô nhẹ
                    android.graphics.Color.parseColor("#FFAB40"), // Cam hổ phách nhẹ
                    android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0, 0, 256, 256, 32, 32, paint);

            // Vẽ icon app lên trên cái nền đó
            android.graphics.drawable.Drawable d = getDrawable(R.drawable.ic_launcher);
            d.setBounds(32, 32, 224, 224);
            d.draw(canvas);

            dummySession.setMetadata(new android.media.MediaMetadata.Builder()
                .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "EdgeBar Screen")
                .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Đang quay màn hình...")
                .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, MAX_DURATION_MS)
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, artBmp)
                .build());
        }
    dummySession.setPlaybackState(new android.media.session.PlaybackState.Builder()
        .setActions(android.media.session.PlaybackState.ACTION_PLAY_PAUSE | android.media.session.PlaybackState.ACTION_STOP)
        .setState(isPaused ? android.media.session.PlaybackState.STATE_PAUSED : android.media.session.PlaybackState.STATE_PLAYING, sec * 1000, isPaused ? 0f : 1f).build());

    String time = String.format("%02d:%02d", sec / 60, sec % 60);
        Notification n = new Notification.Builder(this, cid)
            .setContentTitle((isPaused ? "⏸️ Đã tạm dừng — " : "🔴 Đang quay màn hình — ") + time)
            .setContentText("EdgeBar Screen")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(contentTapPI())
            .addAction(android.R.drawable.ic_media_next, "Dừng", screenRecActionPI(ACTION_STOP)) // Nút Trái
            .addAction(isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                    isPaused ? "Tiếp Tục" : "Tạm Dừng", screenRecActionPI(ACTION_PAUSE_TOGGLE)) // Nút Giữa
            .addAction(android.R.drawable.ic_media_ff, "Dừng & Xem", screenRecActionPI(ACTION_STOP_AND_PLAY)) // Nút Phải
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(dummySession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2))
            .build();
    if (Build.VERSION.SDK_INT >= 29)
        startForeground(94, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    else
        startForeground(94, n);
}
    @Override public void onDestroy() {
        if (isRunning) stopRecording(false);
        super.onDestroy();
    }
}
