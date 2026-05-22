package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.media.MediaRecorder; import android.os.*; import android.provider.Settings; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
import android.view.WindowManager; import android.view.View; import android.graphics.Color; import android.graphics.PixelFormat; import android.view.Gravity; import android.animation.ValueAnimator;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private boolean isCountingDown = false;
    private Vibrator vibrator; private WindowManager wm; private View redEdge; private ValueAnimator breathAnim;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() { super.onCreate(); vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "TEST_BREATH".equals(intent.getAction())) {
            showBreath(true); handler.postDelayed(() -> showBreath(false), 6000); return START_NOT_STICKY;
        }
        if (isRecording || isCountingDown) { stopRecording(); stopSelf(); } 
        else {
            isCountingDown = true;
            Toast.makeText(this, "Ghi âm bắt đầu sau 3 giây...", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> { isCountingDown = false; startRecording(); }, 3000);
        }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "EdgeBar_Records");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "EB_Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp4";
            File audioFile = new File(dir, fileName);
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            int kbps = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE).getInt("rec_kbps", 128);
            recorder.setAudioEncodingBitRate(kbps * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true;
            
            if(vibrator != null) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel("EB_REC", "EdgeBar Stealth", NotificationManager.IMPORTANCE_LOW);
                chan.setSound(null, null); chan.enableVibration(false);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if(manager != null) manager.createNotificationChannel(chan);
            }
            Notification n = new Notification.Builder(this, "EB_REC").setContentTitle("EB Core").setContentText("System running...").setSmallIcon(android.R.drawable.ic_dialog_info).build();
            startForeground(1912, n);
            showBreath(true);
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic: " + e.getMessage(), Toast.LENGTH_SHORT).show(); stopSelf(); }
    }

    private void showBreath(boolean show) {
        if (show) {
            if (redEdge == null && Settings.canDrawOverlays(this)) {
                wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                redEdge = new View(this);
                SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
                int cIdx = prefs.getInt("rec_color", 0);
                redEdge.setBackgroundColor(cIdx == 0 ? Color.RED : (cIdx == 1 ? Color.GREEN : Color.BLUE));
                
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, 8,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.TOP; wm.addView(redEdge, params);
                
                int dur = prefs.getInt("rec_anim_dur", 1200); int gap = prefs.getInt("rec_anim_gap", 800);
                breathAnim = ValueAnimator.ofFloat(0f, 1f); breathAnim.setDuration(dur + gap); breathAnim.setRepeatCount(ValueAnimator.INFINITE);
                breathAnim.addUpdateListener(anim -> {
                    float p = (float) anim.getAnimatedValue(); float ratio = (float) dur / (dur + gap);
                    if (p <= ratio) { float subP = p / ratio; redEdge.setAlpha(0.1f + 0.7f * (float)Math.sin(subP * Math.PI)); } 
                    else { redEdge.setAlpha(0f); }
                });
                breathAnim.start();
            }
        } else {
            if (breathAnim != null) { breathAnim.cancel(); breathAnim = null; }
            if (wm != null && redEdge != null) { try { wm.removeView(redEdge); } catch(Exception e){} redEdge = null; }
        }
    }

    private void stopRecording() {
        if(recorder != null) { try { recorder.stop(); recorder.release(); } catch(Exception e){} recorder = null; }
        isRecording = false; isCountingDown = false; showBreath(false);
        if(vibrator != null) vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));
        Toast.makeText(this, "Đã lưu bản ghi âm (Documents)!", Toast.LENGTH_SHORT).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
