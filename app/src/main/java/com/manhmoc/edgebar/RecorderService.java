package com.manhmoc.edgebar;
import android.animation.ObjectAnimator; import android.animation.ValueAnimator; import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint; import android.graphics.PixelFormat; import android.graphics.RectF; import android.media.MediaRecorder; import android.os.Build; import android.os.Environment; import android.os.Handler; import android.os.IBinder; import android.os.Looper; import android.provider.Settings; import android.view.Gravity; import android.view.View; import android.view.WindowManager; import android.widget.Toast; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false;
    private WindowManager wm; private BreathView breathView; private ObjectAnimator breathAnim;
    private int countdown = 3; private int recSeconds = 0;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate(); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel chan = new NotificationChannel("EB_REC_CHAN", "Voice Recorder", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "EB_REC_CHAN") : new Notification.Builder(this);
        startForeground(1912, builder.setContentTitle("Edge Bar Phantom").setContentText("Chuẩn bị ghi âm...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build());
        countdown = 3; startCountdown();
        return START_STICKY;
    }

    private void startCountdown() {
        if(countdown > 0) {
            Toast.makeText(this, "Ghi âm sau: " + countdown + "s", Toast.LENGTH_SHORT).show();
            countdown--; handler.postDelayed(this::startCountdown, 1000);
        } else startRecording();
    }

    private void startRecording() {
        if (isRecording) return;
        try {
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC); if (!musicDir.exists()) musicDir.mkdirs();
            File outFile = new File(musicDir, "EdgeBar_Record_" + System.currentTimeMillis() + ".m4a");
            recorder.setOutputFile(outFile.getAbsolutePath()); recorder.prepare(); recorder.start();
            isRecording = true; showBreathingUI(); startTimer();
            Toast.makeText(this, "🔴 Đang ghi âm...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Lỗi Mic!", Toast.LENGTH_SHORT).show(); stopSelf(); }
    }

    private void startTimer() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if(isRecording && breathView != null) {
                    recSeconds++; int m = recSeconds / 60; int s = recSeconds % 60;
                    breathView.setTime(String.format("%02d:%02d", m, s));
                    handler.postDelayed(this, 1000);
                }
            }
        }, 1000);
    }

    private void showBreathingUI() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new BreathView(this, prefs);
        int shape = prefs.getInt("breath_shape", 0); int w = prefs.getInt("breath_w", 0); int h = prefs.getInt("breath_h", 0); int dotSize = prefs.getInt("breath_dot_size", 40); int bX = prefs.getInt("breath_dot_x", 0); int bY = prefs.getInt("breath_dot_y", 0);
        
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (shape == 0) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; // Edge thì ko touch
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(shape == 1 ? (dotSize*2 + 250) : (w > 0 ? w : WindowManager.LayoutParams.MATCH_PARENT), shape == 1 ? (dotSize*2 + 100) : (h > 0 ? h : WindowManager.LayoutParams.MATCH_PARENT), Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE, flags, PixelFormat.TRANSLUCENT);
        if (shape == 1) { 
            params.gravity = Gravity.TOP | Gravity.LEFT; params.x = bX; params.y = bY;
            breathView.setOnClickListener(v -> stopSelf()); // V19.12.2.12: Bấm vào Dot để dừng
        } else { params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; }
        
        breathAnim = ObjectAnimator.ofFloat(breathView, "animAlpha", 0.1f, 1f, 0.1f);
        breathAnim.setDuration(prefs.getInt("breath_delay", 1500)); breathAnim.setRepeatCount(ValueAnimator.INFINITE); breathAnim.start();
        wm.addView(breathView, params);
    }

    @Override public void onDestroy() {
        if (breathAnim != null) breathAnim.cancel();
        if (wm != null && breathView != null) { wm.removeView(breathView); breathView = null; }
        if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception e) {} recorder = null; }
        isRecording = false; Toast.makeText(this, "⏹ Đã lưu Âm Thanh!", Toast.LENGTH_LONG).show();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    
    public static class BreathView extends View {
        private Paint pDraw, pText; public float animAlpha = 0f; private int shape; 
        private float w, h, thick, dotSize; private String timeStr = "00:00"; private SharedPreferences p;
        private String[] COLOR_KEYS = {"WHITE", "NEON", "CYBERPUNK", "LAVA", "OCEAN", "MATRIX", "SUNSET", "GOOGLE", "AURORA", "ABYSS", "FOREST", "FLAME", "MIDNIGHT", "TROPICAL", "CANDY"}; 
        private String[] COLOR_VALS = {"#FFFFFF", "#00FFFF", "#FFD700", "#FF4500", "#00BFFF", "#00FF00", "#FF8C00", "#EA4335", "#00E5FF", "#1DE9B6", "#4CAF50", "#FF9800", "#03A9F4", "#8BC34A", "#F06292"};
        public BreathView(Context c, SharedPreferences pr) { super(c); this.p = pr; pDraw = new Paint(Paint.ANTI_ALIAS_FLAG); pDraw.setStyle(Paint.Style.STROKE); pDraw.setStrokeCap(Paint.Cap.ROUND); pText = new Paint(Paint.ANTI_ALIAS_FLAG); pText.setColor(Color.WHITE); pText.setTextSize(40f); pText.setShadowLayer(5, 0, 0, Color.BLACK); shape = p.getInt("breath_shape", 0); w = p.getInt("breath_w", 0); h = p.getInt("breath_h", 0); thick = p.getInt("breath_thick", 12); dotSize = p.getInt("breath_dot_size", 40); pDraw.setStrokeWidth(thick); String cTheme = p.getString("anim_color", "WHITE"); int color = Color.WHITE; for(int i=0; i<COLOR_KEYS.length; i++) if(COLOR_KEYS[i].equals(cTheme)) color = Color.parseColor(COLOR_VALS[i]); if(shape == 1) { pDraw.setStyle(Paint.Style.FILL); pDraw.setColor(color); } else { pDraw.setColor(color); } }
        public void setAnimAlpha(float a) { this.animAlpha = a; invalidate(); } public void setTime(String t) { this.timeStr = t; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); pDraw.setAlpha((int)(animAlpha * p.getInt("breath_alpha", 255))); float cx = getWidth() / 2f; float cy = getHeight() / 2f; if (shape == 1) { canvas.drawCircle(dotSize + 20, dotSize + 20, dotSize, pDraw); canvas.drawText(timeStr, dotSize*2 + 40, dotSize + 35, pText); } else { float drawW = (w > 0) ? w : getWidth(); float drawH = (h > 0) ? h : getHeight(); float off = thick / 2f; float left = (getWidth() - drawW) / 2f + off; float top = (getHeight() - drawH) / 2f + off; canvas.drawRoundRect(new RectF(left, top, left + drawW - 2*off, top + drawH - 2*off), 40, 40, pDraw); canvas.drawText(timeStr, cx - 40, top + drawH + 50, pText); } }
    }
}
