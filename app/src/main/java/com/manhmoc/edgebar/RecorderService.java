package com.manhmoc.edgebar;
import android.animation.ObjectAnimator; import android.animation.ValueAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.graphics.RectF; import android.media.MediaRecorder; import android.os.Build; import android.os.CountDownTimer; import android.os.Environment; import android.os.Handler; import android.os.IBinder; import android.os.Looper; import android.os.Vibrator; import android.os.VibrationEffect; import android.provider.Settings; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.WindowManager; import android.widget.Toast; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false; private boolean isTestMode = false;
    private WindowManager wm; private BreathView breathView; private ObjectAnimator breathAnim;
    private SharedPreferences prefs; private long startTime = 0; private Handler timerHandler = new Handler(Looper.getMainLooper());
    private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"};
    private String[] COLOR_VALS = {"#FFFFFF", "#00FFFF", "#FFD700", "#FF4500", "#00BFFF", "#00FF00", "#FF8C00", "#EA4335", "#00E5FF", "#1DE9B6", "#4CAF50", "#FF9800", "#03A9F4", "#8BC34A", "#F06292"};

    private class BreathView extends View {
        private Paint pDraw, pText; public float animAlpha = 0f; private int shape; 
        private float w, h, thick, dotSize; private String timeStr = "00:00"; private int color;

        public BreathView(Context c) { 
            super(c); pDraw = new Paint(); pDraw.setAntiAlias(true); pDraw.setStyle(Paint.Style.STROKE); pDraw.setStrokeCap(Paint.Cap.ROUND); 
            pText = new Paint(); pText.setAntiAlias(true); pText.setColor(Color.WHITE); pText.setTextSize(38f); pText.setShadowLayer(5, 0, 0, Color.BLACK);
            shape = prefs.getInt("breath_shape", 0); w = prefs.getInt("breath_w", 0); h = prefs.getInt("breath_h", 0);
            thick = prefs.getInt("breath_thick", 12); dotSize = prefs.getInt("breath_dot_size", 40); pDraw.setStrokeWidth(thick);
            
            // V19.12.2.12: Kế thừa 15 màu của Animation Viền
            String cTheme = prefs.getString("anim_color", "WHITE"); color = Color.WHITE; 
            for(int i=0; i<COLOR_KEYS.length; i++) if(COLOR_KEYS[i].equals(cTheme)) color = Color.parseColor(COLOR_VALS[i]);
            if(shape == 1) { pDraw.setStyle(Paint.Style.FILL); pDraw.setColor(color); } else { pDraw.setColor(color); }
        }
        public void setAnimAlpha(float a) { this.animAlpha = a; invalidate(); }
        public void setTime(String t) { this.timeStr = t; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); pDraw.setAlpha((int)(animAlpha * prefs.getInt("breath_alpha", 255)));
            if (shape == 1) { 
                canvas.drawCircle(dotSize + 20, dotSize + 20, dotSize, pDraw); 
                canvas.drawText(timeStr, dotSize*2 + 40, dotSize + 30, pText); 
            } else { 
                float drawW = (w > 0) ? w : getWidth(); float drawH = (h > 0) ? h : getHeight(); float off = thick / 2f; float left = (getWidth() - drawW) / 2f + off; float top = (getHeight() - drawH) / 2f + off; 
                canvas.drawRoundRect(new RectF(left, top, left + drawW - 2*off, top + drawH - 2*off), 40, 40, pDraw); 
                canvas.drawText(timeStr, (getWidth()/2f) - 40, top + drawH + 50, pText); 
            }
        }
    }

    @Override public void onCreate() { super.onCreate(); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE); if (Build.VERSION.SDK_INT >= 26) { ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("EB_REC", "Voice Recorder", NotificationManager.IMPORTANCE_LOW)); } }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("test_mode", false)) {
            if(isRecording) return START_NOT_STICKY; isTestMode = true; isRecording = true; showBreathingEdge();
            new Handler(Looper.getMainLooper()).postDelayed(() -> { stopRecording(); stopSelf(); }, 5000); return START_NOT_STICKY;
        }
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
        if (isRecording && !isTestMode) return;
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records"); if (!dir.exists()) dir.mkdirs();
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); recorder.setAudioEncodingBitRate(prefs.getInt("rec_kbps", 128) * 1000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(new File(dir, "EB_REC_" + System.currentTimeMillis() + ".m4a").getAbsolutePath());
            recorder.prepare(); recorder.start(); isRecording = true; isTestMode = false; startTime = System.currentTimeMillis();
            try{((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception e){}
            showBreathingEdge();
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic!", Toast.LENGTH_SHORT).show(); stopSelf(); }
    }

    private void showBreathingEdge() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new BreathView(this);
        int shape = prefs.getInt("breath_shape", 0); int w = prefs.getInt("breath_w", 0); int h = prefs.getInt("breath_h", 0); int dotSize = prefs.getInt("breath_dot_size", 40); int bX = prefs.getInt("breath_dot_x", 0); int bY = prefs.getInt("breath_dot_y", 0);

        // V19.12.2.12: Nới rộng Bounding Box để chữ không bị lẹm
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(shape == 1 ? (dotSize*2 + 250) : (w > 0 ? w : -1), shape == 1 ? (dotSize*2 + 80) : (h > 0 ? h : 150), Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        if (shape == 1) { params.gravity = Gravity.TOP | Gravity.LEFT; params.x = bX; params.y = bY; } else { params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; }
        
        breathView.setOnTouchListener((v, event) -> { if (event.getAction() == MotionEvent.ACTION_DOWN) { stopRecording(); stopSelf(); return true; } return false; });
        
        breathAnim = ObjectAnimator.ofFloat(breathView, "animAlpha", 0.1f, 1f, 0.1f); breathAnim.setDuration(prefs.getInt("breath_delay", 1500)); breathAnim.setRepeatCount(ValueAnimator.INFINITE); breathAnim.start(); wm.addView(breathView, params);
        
        timerHandler.post(new Runnable() {
            @Override public void run() {
                if(!isRecording) return;
                long e = System.currentTimeMillis() - (startTime == 0 ? System.currentTimeMillis() : startTime); breathView.setTime(isTestMode ? "TEST" : String.format("%02d:%02d", (e/1000)/60, (e/1000)%60));
                timerHandler.postDelayed(this, 1000);
            }
        });
    }

    private void stopRecording() {
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) {} recorder = null; }
        isRecording = false; timerHandler.removeCallbacksAndMessages(null); if (breathAnim != null) breathAnim.cancel();
        if (wm != null && breathView != null) { wm.removeView(breathView); breathView = null; }
        try{((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VibrationEffect.createWaveform(new long[]{0, 50, 100, 50}, -1));}catch(Exception e){}
        if(!isTestMode) Toast.makeText(this, "⏹ Đã lưu vào mục Âm Thanh (Music)!", Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroy() { if(isRecording) stopRecording(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
