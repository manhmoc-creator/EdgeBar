package com.manhmoc.edgebar;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
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
    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable, maxGuard;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if ("STOP".equals(action)) { stopRecording(); return START_NOT_STICKY; }

        if (isRunning || intent == null) return START_NOT_STICKY;
        startForegroundNotif(0);

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");
        if (data == null) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) { stopForeground(true); stopSelf(); return START_NOT_STICKY; }

        if (!startRecorder()) {
            Toast.makeText(this, "Không thể bắt đầu quay màn hình", Toast.LENGTH_SHORT).show();
            stopForeground(true); stopSelf(); return START_NOT_STICKY;
        }

        isRunning = true;
        startTimeMs = System.currentTimeMillis();
        startTimerNotif();
        maxGuard = this::stopRecording;
        h.postDelayed(maxGuard, MAX_DURATION_MS);
        return START_NOT_STICKY;
    }

    private boolean startRecorder() {
        try {
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
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
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

    private void startTimerNotif() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isRunning) return;
                long sec = (System.currentTimeMillis() - startTimeMs) / 1000;
                startForegroundNotif(sec);
                h.postDelayed(this, 1000);
            }
        };
        h.post(timerRunnable);
    }

    private void stopRecording() {
        if (!isRunning) { stopForeground(true); stopSelf(); return; }
        isRunning = false;
        if (timerRunnable != null) h.removeCallbacks(timerRunnable);
        if (maxGuard != null) h.removeCallbacks(maxGuard);

        try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;

        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        virtualDisplay = null;

        try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
        pfd = null;

        if (pendingUri != null && Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.IS_PENDING, 0);
            try { getContentResolver().update(pendingUri, cv, null, null); } catch (Exception ignored) {}
        }
        pendingUri = null;

        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception ignored) {}
        mediaProjection = null;

        Toast.makeText(this, "Đã lưu video quay màn hình", Toast.LENGTH_SHORT).show();
        stopForeground(true);
        stopSelf();
    }

    private void startForegroundNotif(long sec) {
        String cid = "eb_screen_rec";
        NotificationChannel c = new NotificationChannel(cid, "Quay màn hình", NotificationManager.IMPORTANCE_LOW);
        c.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        String time = String.format("%02d:%02d", sec / 60, sec % 60);
        Notification n = new Notification.Builder(this, cid)
                .setContentTitle("🔴 Đang quay màn hình — " + time)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(94, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else
            startForeground(94, n);
    }

    @Override public void onDestroy() {
        if (isRunning) stopRecording();
        super.onDestroy();
    }
}
