package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.media.MediaRecorder; import android.os.*; import android.provider.Settings; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
import android.view.WindowManager; import android.view.View; import android.graphics.Color; import android.graphics.PixelFormat; import android.view.Gravity; import android.animation.ObjectAnimator;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private Vibrator vibrator;
    private WindowManager wm; private View redEdge; private Handler pulseHandler = new Handler();
    private SharedPreferences prefs;

    @Override public void onCreate() { super.onCreate(); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); }
    
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if ("TEST_BREATH".equals(intent.getAction())) { testBreathAnim(); return START_NOT_STICKY; }
        if (isRecording) { stopRecording(); stopSelf(); } 
        else { Toast.makeText(this, "Bắt đầu ghi âm sau 3 giây...", Toast.LENGTH_SHORT).show(); new Handler(Looper.getMainLooper()).postDelayed(this::startRecording, 3000); }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        if(isRecording) return;
        try {
            // Lưu ra thư mục Music gốc của điện thoại để Files by Google thấy được dễ dàng
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "EB_Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
            File audioFile = new File(dir, fileName);
            
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(prefs.getInt("rec_kbps", 128) * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true;
            
            if(vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)); // Rung báo hiệu bắt đầu
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { NotificationChannel chan = new NotificationChannel("EB_REC", "EdgeBar Recorder", NotificationManager.IMPORTANCE_LOW); NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); if(manager != null) manager.createNotificationChannel(chan); }
            Notification n = new Notification.Builder(this, "EB_REC").setContentTitle("Edge Bar").setContentText("Đang ghi âm ngầm...").setSmallIcon(android.R.drawable.ic_dialog_info).build();
            startForeground(1912, n);
            showBreath();
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic: " + e.getMessage(), Toast.LENGTH_LONG).show(); stopSelf(); }
    }

    private void stopRecording() {
        if(recorder != null) { try { recorder.stop(); recorder.release(); } catch(Exception e){} recorder = null; }
        isRecording = false; hideBreath();
        if(vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));
        Toast.makeText(this, "Đã lưu bản ghi âm vào thư mục Music!", Toast.LENGTH_LONG).show();
    }

    private Runnable pulseRunnable = new Runnable() {
        @Override public void run() {
            if (redEdge != null) {
                ObjectAnimator a = ObjectAnimator.ofFloat(redEdge, "alpha", 0f, 1f, 0f);
                a.setDuration(1500); a.start(); // Sáng lên rồi mờ đi trong 1.5s
                pulseHandler.postDelayed(this, 1500 + prefs.getInt("breath_interval", 2000));
            }
        }
    };

    private void showBreath() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        redEdge = new View(this); redEdge.setAlpha(0f);
        
        int colorType = prefs.getInt("breath_color", 0);
        if(colorType == 0) redEdge.setBackgroundColor(Color.RED);
        else if (colorType == 1) redEdge.setBackgroundColor(Color.GREEN);
        else redEdge.setBackgroundColor(Color.parseColor("#00E5FF")); // Lam (Cyan)

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, 8,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, // HIỆN TRÊN MÀN KHOÁ
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP; wm.addView(redEdge, params);
        pulseHandler.post(pulseRunnable);
    }

    private void hideBreath() {
        pulseHandler.removeCallbacksAndMessages(null);
        if(wm != null && redEdge != null) { try { wm.removeView(redEdge); } catch(Exception e){} redEdge = null; }
    }

    private void testBreathAnim() {
        showBreath(); Toast.makeText(this, "Thử hiệu ứng trong 6 giây...", Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> { hideBreath(); stopSelf(); }, 6000);
    }

    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
