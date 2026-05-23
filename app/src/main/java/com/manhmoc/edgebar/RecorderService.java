package com.manhmoc.edgebar;
import android.animation.*; import android.app.*; import android.content.*; import android.graphics.*; import android.media.MediaRecorder; import android.os.*; import android.provider.Settings; import android.view.*; import android.widget.*; import java.io.File;

public class RecorderService extends Service {
    private MediaRecorder recorder; private boolean isRecording = false;
    private WindowManager wm; public BreathView breathView; private AnimatorSet breathAnim;
    private SharedPreferences prefs; private long startTime = 0; 
    private Handler timerHandler = new Handler(Looper.getMainLooper()); 

    public class BreathView extends View {
        public float animAlpha = 0.1f; private int shape; private float w, h, thick, dotSize; private String timeStr = "00:00"; private int color;
        public BreathView(Context c) { super(c); prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
            shape = prefs.getInt("breath_shape", 0); w = prefs.getInt("breath_w", 0); h = prefs.getInt("breath_h", 0); thick = prefs.getInt("breath_thick", 12); dotSize = prefs.getInt("breath_dot_size", 40);
            int colorIdx = prefs.getInt("breath_color", 0); color = colorIdx == 0 ? Color.RED : (colorIdx == 1 ? Color.GREEN : Color.BLUE);
        }
        public void setAnimAlpha(float a) { this.animAlpha = a; invalidate(); } public void setTime(String t) { this.timeStr = t; invalidate(); }
        @Override protected void onDraw(Canvas canvas) { Paint p = new Paint(); p.setAntiAlias(true); p.setColor(color); p.setAlpha((int)(animAlpha * prefs.getInt("breath_alpha", 255))); if(shape == 1) canvas.drawCircle(getWidth()/2f, getHeight()/2f, dotSize, p); else canvas.drawRect(0,0,getWidth(),getHeight(), p); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) { stopRecording(); stopSelf(); } else { startRecording(); } return START_STICKY;
    }
    private void startRecording() {
        prefs = getSharedPreferences("EdgeBarPrefs", MODE_PRIVATE);
        new CountDownTimer(3000, 1000) {
            public void onTick(long m) { Toast.makeText(RecorderService.this, "🎙 " + (m/1000 + 1), Toast.LENGTH_SHORT).show(); }
            public void onFinish() {
                try {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar_Records"); if(!dir.exists()) dir.mkdirs();
                    recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC); recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    recorder.setOutputFile(new File(dir, "EB_"+System.currentTimeMillis()+".m4a").getAbsolutePath()); recorder.prepare(); recorder.start(); isRecording=true; startTime=System.currentTimeMillis();
                    // UI Breath
                    wm = (WindowManager) getSystemService(WINDOW_SERVICE); breathView = new BreathView(RecorderService.this);
                    WindowManager.LayoutParams p = new WindowManager.LayoutParams(-1, 50, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
                    wm.addView(breathView, p);
                    breathAnim = new AnimatorSet(); ObjectAnimator a1 = ObjectAnimator.ofFloat(breathView, "animAlpha", 0.1f, 1f); a1.setDuration(1000); ObjectAnimator a2 = ObjectAnimator.ofFloat(breathView, "animAlpha", 1f, 0.1f); a2.setDuration(1000); breathAnim.playSequentially(a1, a2); breathAnim.setRepeatCount(ValueAnimator.INFINITE); breathAnim.start();
                } catch(Exception e) { Toast.makeText(RecorderService.this, "Lỗi mic!", Toast.LENGTH_SHORT).show(); }
            }
        }.start();
    }
    private void stopRecording() { if(recorder!=null) { recorder.stop(); recorder.release(); recorder=null; } isRecording=false; if(wm!=null && breathView!=null) wm.removeView(breathView); }
    @Override public IBinder onBind(Intent i) { return null; }
}
