package com.manhmoc.edgebar;
import android.app.*; import android.content.*; import android.graphics.*; import android.media.*; import android.os.*; import android.view.*; import android.widget.*; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false;
    private WindowManager wm; private View breathView; private CountDownTimer timer;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1912, new Notification.Builder(this, "EB_REC_CHAN").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Edge Bar Phantom").build());
        timer = new CountDownTimer(3000, 1000) {
            public void onTick(long millis) { Toast.makeText(RecorderService.this, "Ghi âm sau: " + (millis/1000 + 1), Toast.LENGTH_SHORT).show(); }
            public void onFinish() { startRecording(); }
        }.start();
        return START_STICKY;
    }

    private void startRecording() {
        try {
            SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            File outFile = new File(musicDir, "EB_" + System.currentTimeMillis() + ".m4a");
            recorder.setOutputFile(outFile.getAbsolutePath()); recorder.prepare(); recorder.start();
            isRecording = true; showBreathingUI(prefs);
        } catch (Exception e) { stopSelf(); }
    }

    private void showBreathingUI(SharedPreferences prefs) {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new View(this);
        // Lấy cấu hình từ thanh kéo của user
        int thick = prefs.getInt("breath_thick", 12); int color = Color.parseColor("#00E5FF");
        GradientDrawable d = new GradientDrawable(); d.setShape(prefs.getBoolean("breath_is_circle", false) ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        d.setStroke(thick, color); breathView.setBackground(d);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(prefs.getInt("breath_w", 300), prefs.getInt("breath_h", 300), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        wm.addView(breathView, params);
    }

    @Override public void onDestroy() {
        if (timer != null) timer.cancel();
        if (wm != null && breathView != null) wm.removeView(breathView);
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) {} }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
