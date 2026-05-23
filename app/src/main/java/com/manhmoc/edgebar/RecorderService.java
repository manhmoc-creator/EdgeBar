package com.manhmoc.edgebar;
import android.animation.AnimatorSet; import android.animation.ObjectAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.graphics.RectF; import android.media.MediaRecorder; import android.os.Build; import android.os.CountDownTimer; import android.os.Environment; import android.os.Handler; import android.os.IBinder; import android.os.Looper; import android.provider.Settings; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false;
    private WindowManager wm; private BreathView breathView; private AnimatorSet breathAnim;
    private SharedPreferences prefs; private long startTime = 0; 
    private Handler timerHandler = new Handler(Looper.getMainLooper()); 
    private Handler breathHandler = new Handler(Looper.getMainLooper());

    private class BreathView extends View {
        private Paint pDraw, pText; public float animAlpha = 0.1f; private int shape; 
        private float w, h, thick, dotSize; private String timeStr = "00:00"; private int color;

        public BreathView(Context c) { 
            super(c); pDraw = new Paint(); pDraw.setAntiAlias(true); pDraw.setStyle(Paint.Style.STROKE); pDraw.setStrokeCap(Paint.Cap.ROUND); 
            pText = new Paint(); pText.setAntiAlias(true); pText.setColor(Color.WHITE); pText.setTextSize(35f); pText.setShadowLayer(5, 0, 0, Color.BLACK);
            shape = prefs.getInt("breath_shape", 0); w = prefs.getInt("breath_w", 0); h = prefs.getInt("breath_h", 0);
            thick = prefs.getInt("breath_thick", 12); dotSize = prefs.getInt("breath_dot_size", 40); pDraw.setStrokeWidth(thick);
            int colorIdx = prefs.getInt("breath_color", 0);
            color = colorIdx == 0 ? Color.RED : (colorIdx == 1 ? Color.GREEN : Color.BLUE);
            if(shape == 1) { pDraw.setStyle(Paint.Style.FILL); } pDraw.setColor(color);
        }
        public void setAnimAlpha(float a) { this.animAlpha = a; invalidate(); }
        public void setTime(String t) { this.timeStr = t; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); pDraw.setAlpha((int)(animAlpha * prefs.getInt("breath_alpha", 255)));
            float cx = getWidth() / 2f; float cy = getHeight() / 2f;
            if (shape == 1) { 
                canvas.drawCircle(cx, cy, dotSize, pDraw); canvas.drawText(timeStr, cx + dotSize + 15, cy + 12, pText);
            } else { 
                float drawW = (w > 0) ? w : getWidth(); float drawH = (h > 0) ? h : getHeight();
                float off = thick / 2f; float left = (getWidth() - drawW) / 2f + off; float top = (getHeight() - drawH) / 2f + off;
                canvas.drawRoundRect(new RectF(left, top, left + drawW - 2*off, top + drawH - 2*off), 40, 40, pDraw); canvas.drawText(timeStr, cx - 40, top + drawH + 40, pText);
            }
        }
    }

    @Override public void onCreate() { super.onCreate(); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); if (Build.VERSION.SDK_INT >= 26) { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("EB_REC", "Voice Recorder", NotificationManager.IMPORTANCE_LOW)); } }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRecording) { stopRecording(); stopSelf(); } 
        else {
            startForeground(1912, (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "EB_REC") : new Notification.Builder(this)).setContentTitle("Edge Bar").setContentText("Chuẩn bị ghi âm...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build());
            new CountDownTimer(3000, 1000) {
                public void onTick(long millis) { Toast.makeText(RecorderService.this, "🎙 " + (millis / 1000 + 1) + "...", Toast.LENGTH_SHORT).show(); }
                public void onFinish() { startRecording(); }
            }.start();
        } return START_STICKY;
    }

    private void startRecording() {
        if (isRecording) return;
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records"); if (!dir.exists()) dir.mkdirs();
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(prefs.getInt("rec_kbps", 128) * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(new File(dir, "EB_REC_" + System.currentTimeMillis() + ".m4a").getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true; startTime = System.currentTimeMillis();
            try{((android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception e){}
            showBreathingEdge();
        } catch (Exception e) { Toast.makeText(this, "Lỗi Phần Cứng Mic!", Toast.LENGTH_SHORT).show(); stopSelf(); }
    }

    private void showBreathingEdge() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new BreathView(this);
        int shape = prefs.getInt("breath_shape", 0); int w = prefs.getInt("breath_w", 0); int h = prefs.getInt("breath_h", 0); int dotSize = prefs.getInt("breath_dot_size", 40); int bX = prefs.getInt("breath_dot_x", 0); int bY = prefs.getInt("breath_dot_y", 0);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            shape == 1 ? (dotSize*2 + 150) : (w > 0 ? w : -1), shape == 1 ? (dotSize*2) : (h > 0 ? h : 100),
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        );
        if (shape == 1) { params.gravity = Gravity.TOP | Gravity.LEFT; params.x = bX; params.y = bY; } else { params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; }
        breathView.setOnTouchListener((v, event) -> { if (event.getAction() == MotionEvent.ACTION_DOWN) { stopRecording(); stopSelf(); return true; } return false; });
        wm.addView(breathView, params); startBreathLoop();
        
        timerHandler.post(new Runnable() {
            @Override public void run() {
                if(!isRecording) return; long e = System.currentTimeMillis() - startTime; breathView.setTime(String.format("%02d:%02d", (e/1000)/60, (e/1000)%60)); timerHandler.postDelayed(this, 1000);
            }
        });
    }

    private void startBreathLoop() {
        if(breathView == null) return;
        int speed = prefs.getInt("anim_dur", 1500); int delay = prefs.getInt("breath_delay", 1500);
        breathAnim = new AnimatorSet();
        ObjectAnimator up = ObjectAnimator.ofFloat(breathView, "animAlpha", 0.1f, 1f); up.setDuration(speed/2);
        ObjectAnimator down = ObjectAnimator.ofFloat(breathView, "animAlpha", 1f, 0.1f); down.setDuration(speed/2);
        breathAnim.playSequentially(up, down);
        breathAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { if(isRecording && breathView != null) breathHandler.postDelayed(() -> { if(breathAnim != null) breathAnim.start(); }, delay); }
        }); breathAnim.start();
    }

    private void stopRecording() {
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) {} recorder = null; }
        isRecording = false; timerHandler.removeCallbacksAndMessages(null); breathHandler.removeCallbacksAndMessages(null);
        if (breathAnim != null) breathAnim.cancel(); if (wm != null && breathView != null) { try{wm.removeView(breathView);}catch(Exception e){} breathView = null; }
        try{((android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));}catch(Exception e){}
        Toast.makeText(this, "⏹ Đã lưu vào mục Âm Thanh (Music)!", Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
