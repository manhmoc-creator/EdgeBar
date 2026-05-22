package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.media.MediaRecorder; import android.os.*; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private Vibrator vibrator;
    @Override public void onCreate() { super.onCreate(); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) { stopRecording(); stopSelf(); } else { startRecording(); }
        return START_NOT_STICKY;
    }
    private void startRecording() {
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "EdgeBar_Records");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "EB_Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
            File audioFile = new File(dir, fileName);
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            int kbps = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("rec_kbps", 128);
            recorder.setAudioEncodingBitRate(kbps * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true;
            if(vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            
            // FIX CRASH: Bắt buộc tạo Notification Channel cho Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel("EB_REC", "EdgeBar Recorder", NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if(manager != null) manager.createNotificationChannel(chan);
            }
            
            Notification n = new Notification.Builder(this, "EB_REC").setContentTitle("Edge Bar").setContentText("Đang ghi âm ngầm...").setSmallIcon(android.R.drawable.ic_dialog_info).build();
            startForeground(1912, n);
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic: " + e.getMessage(), Toast.LENGTH_SHORT).show(); stopSelf(); }
    }
    private void stopRecording() {
        if(recorder != null) { try { recorder.stop(); recorder.release(); } catch(Exception e){} recorder = null; }
        isRecording = false; if(vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));
        Toast.makeText(this, "Đã lưu bản ghi âm!", Toast.LENGTH_SHORT).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
