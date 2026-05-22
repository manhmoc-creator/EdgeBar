package com.manhmoc.edgebar;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Context; import android.content.Intent; import android.media.MediaRecorder; import android.os.Build; import android.os.IBinder; import android.widget.Toast; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder;
    private boolean isRecording = false;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel chan = new NotificationChannel("EB_REC_CHAN", "Voice Recorder", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // BẮT BUỘC: Gọi startForeground trong vòng 5s để OS không giật sập App
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "EB_REC_CHAN") : new Notification.Builder(this);
        Notification notification = builder.setContentTitle("Edge Bar Phantom").setContentText("Đang thu âm ẩn...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();
        startForeground(1912, notification);
        startRecording();
        return START_STICKY;
    }

    private void startRecording() {
        if (isRecording) return;
        try {
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000); recorder.setAudioSamplingRate(44100);
            File outFile = new File(getExternalFilesDir(null), "PhantomRecord_" + System.currentTimeMillis() + ".m4a");
            recorder.setOutputFile(outFile.getAbsolutePath());
            recorder.prepare(); recorder.start();
            isRecording = true; Toast.makeText(this, "🔴 Đang ghi âm: " + outFile.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi Mic: Cấp quyền ghi âm chưa?", Toast.LENGTH_LONG).show(); stopSelf();
        }
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) { e.printStackTrace(); } recorder = null; }
        isRecording = false; Toast.makeText(this, "⏹ Đã lưu tệp ghi âm!", Toast.LENGTH_SHORT).show();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
