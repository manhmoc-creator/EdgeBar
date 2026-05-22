package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.media.MediaRecorder; import android.os.*; import android.provider.Settings; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
import android.view.WindowManager; import android.view.View; import android.graphics.Color; import android.graphics.PixelFormat; import android.view.Gravity; import android.animation.ObjectAnimator; import android.animation.ValueAnimator;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private Vibrator vibrator;
    private WindowManager wm; private View redEdge; private Handler breathHandler = new Handler(Looper.getMainLooper()); private Runnable breathTask;

    @Override public void onCreate() { super.onCreate(); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) { stopRecording(); stopSelf(); } 
        else { Toast.makeText(this, "🎙 3...", Toast.LENGTH_SHORT).show(); new Handler(Looper.getMainLooper()).postDelayed(this::startRecording, 3000); }
        return START_NOT_STICKY;
    }
    private void startRecording() {
        if (isRecording) return;
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records");
            if (!dir.exists()) dir.mkdirs();
            File audioFile = new File(dir, "EB_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4");
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("rec_kbps", 128) * 1000); 
            recorder.setAudioSamplingRate(44100); recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true;
            if(vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            
            NotificationChannel chan = new NotificationChannel("EB_REC", "EdgeBar Recorder", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(chan);
            startForeground(1912, new Notification.Builder(this, "EB_REC").setContentTitle("Edge Bar").setContentText("Ghi âm...").setSmallIcon(android.R.drawable.ic_dialog_info).build());

            if (Settings.canDrawOverlays(this)) {
                wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                redEdge = new View(this); 
                int color = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("breath_color", 0) == 0 ? Color.RED : (getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("breath_color", 0) == 1 ? Color.GREEN : Color.CYAN);
                redEdge.setBackgroundColor(color); redEdge.setAlpha(0f);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, 6, Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP; wm.addView(redEdge, params);
                int delay = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("breath_delay", 2500);
                breathTask = new Runnable() { public void run() { if(!isRecording) return; ObjectAnimator a = ObjectAnimator.ofFloat(redEdge, "alpha", 0f, 0.7f, 0f); a.setDuration(1500); a.start(); breathHandler.postDelayed(this, 1500 + delay); } };
                breathHandler.post(breathTask);
            }
        } catch (Exception e) { Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); stopSelf(); }
    }
    private void stopRecording() {
        if(recorder != null) { try { recorder.stop(); recorder.release(); } catch(Exception e){} recorder = null; }
        isRecording = false; if(breathHandler != null && breathTask != null) breathHandler.removeCallbacks(breathTask);
        if(wm != null && redEdge != null) { try { wm.removeView(redEdge); } catch(Exception e){} redEdge = null; }
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
