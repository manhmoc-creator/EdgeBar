package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.media.MediaRecorder; import android.os.*; import android.provider.Settings; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
import android.view.WindowManager; import android.view.View; import android.graphics.Color; import android.graphics.PixelFormat; import android.view.Gravity; import android.animation.ObjectAnimator; import android.animation.ValueAnimator;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private Vibrator vibrator;
    private WindowManager wm; private View redEdge; private Handler breathHandler = new Handler(Looper.getMainLooper()); private Runnable breathTask;

    @Override public void onCreate() { super.onCreate(); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) { stopRecording(); stopSelf(); } 
        else {
            Toast.makeText(this, "🎙 Bắt đầu ghi âm sau 3 giây...", Toast.LENGTH_SHORT).show();
            new Handler(Looper.getMainLooper()).postDelayed(this::startRecording, 3000);
        }
        return START_NOT_STICKY;
    }
    
    private void startRecording() {
        if (isRecording) return;
        try {
            // Lưu vào Public Music Folder để Files by Google nhận diện
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "EB_Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
            File audioFile = new File(dir, fileName);
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            int kbps = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("rec_kbps", 128);
            recorder.setAudioEncodingBitRate(kbps * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true;
            if(vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel("EB_REC", "EdgeBar Recorder", NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if(manager != null) manager.createNotificationChannel(chan);
            }
            Notification n = new Notification.Builder(this, "EB_REC").setContentTitle("Edge Bar").setContentText("Đang ghi âm ngầm...").setSmallIcon(android.R.drawable.ic_dialog_info).build();
            startForeground(1912, n);

            if (Settings.canDrawOverlays(this)) {
                wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                redEdge = new View(this); 
                SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                int colorIdx = prefs.getInt("breath_color", 0);
                int color = colorIdx == 0 ? Color.RED : (colorIdx == 1 ? Color.GREEN : Color.CYAN);
                redEdge.setBackgroundColor(color); redEdge.setAlpha(0f);
                
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, 6, 
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP; wm.addView(redEdge, params);
                
                int delay = prefs.getInt("breath_delay", 2500);
                breathTask = new Runnable() {
                    @Override public void run() {
                        if(!isRecording) return;
                        ObjectAnimator anim = ObjectAnimator.ofFloat(redEdge, "alpha", 0f, 0.7f, 0f);
                        anim.setDuration(1500); anim.start();
                        breathHandler.postDelayed(this, 1500 + delay);
                    }
                };
                breathHandler.post(breathTask);
            }
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic: " + e.getMessage(), Toast.LENGTH_SHORT).show(); stopSelf(); }
    }
    
    private void stopRecording() {
        if(recorder != null) { try { recorder.stop(); recorder.release(); } catch(Exception e){} recorder = null; }
        isRecording = false; if(vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));
        if(breathHandler != null && breathTask != null) breathHandler.removeCallbacks(breathTask);
        if(wm != null && redEdge != null) { try { wm.removeView(redEdge); } catch(Exception e){} redEdge = null; }
        Toast.makeText(this, "Đã lưu bản ghi âm vào Music/EdgeBar_Records!", Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
