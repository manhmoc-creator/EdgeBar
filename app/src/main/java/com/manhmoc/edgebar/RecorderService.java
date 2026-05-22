package com.manhmoc.edgebar;
import android.animation.ObjectAnimator; import android.animation.ValueAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Color; import android.graphics.PixelFormat; import android.graphics.drawable.GradientDrawable; import android.media.MediaRecorder; import android.os.Build; import android.os.Environment; import android.os.Handler; import android.os.IBinder; import android.provider.Settings; import android.view.View; import android.view.WindowManager; import android.widget.Toast; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false;
    private WindowManager wm; private View breathView; private ObjectAnimator breathAnim;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel chan = new NotificationChannel("EB_REC_CHAN", "Voice Recorder", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "EB_REC_CHAN") : new Notification.Builder(this);
        Notification notification = builder.setContentTitle("Edge Bar Phantom").setContentText("Chuẩn bị ghi âm...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build();
        startForeground(1912, notification);
        
        Toast.makeText(this, "Đếm ngược 3 giây...", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(this::startRecording, 3000);
        return START_STICKY;
    }

    private void startRecording() {
        if (isRecording) return;
        try {
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000); recorder.setAudioSamplingRate(44100);
            
            // V19.12.2.7: Lưu ở thư mục Music công khai để Files by Google dễ dàng quét thấy
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!musicDir.exists()) musicDir.mkdirs();
            File outFile = new File(musicDir, "EdgeBar_Record_" + System.currentTimeMillis() + ".m4a");
            
            recorder.setOutputFile(outFile.getAbsolutePath()); recorder.prepare(); recorder.start();
            isRecording = true; showBreathingEdge();
            Toast.makeText(this, "🔴 Đang ghi âm ẩn...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi Mic: Cấp quyền ghi âm chưa?", Toast.LENGTH_LONG).show(); stopSelf();
        }
    }

    private void showBreathingEdge() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new View(this);
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.TRANSPARENT);
        SharedPreferences prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        int colorIdx = prefs.getInt("breath_color", 0); int[] colors = {Color.RED, Color.GREEN, Color.CYAN};
        border.setStroke(18, colors[colorIdx % 3]); breathView.setBackground(border);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        breathAnim = ObjectAnimator.ofFloat(breathView, "alpha", 0f, 1f, 0f);
        breathAnim.setDuration(prefs.getInt("breath_delay", 2500));
        breathAnim.setRepeatCount(ValueAnimator.INFINITE); breathAnim.start();
        wm.addView(breathView, params);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (breathAnim != null) breathAnim.cancel();
        if (wm != null && breathView != null) { wm.removeView(breathView); breathView = null; }
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) { e.printStackTrace(); } recorder = null; }
        isRecording = false; Toast.makeText(this, "⏹ Đã lưu vào mục Âm Thanh (Music)!", Toast.LENGTH_LONG).show();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
