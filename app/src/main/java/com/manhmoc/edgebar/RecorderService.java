package com.manhmoc.edgebar;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.media.MediaRecorder; import android.os.Environment; import android.os.IBinder; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
public class RecorderService extends Service {
    public static boolean isRunning = false;
    private MediaRecorder rec; private String path;
    private BroadcastReceiver stopCmd = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { stopSelf(); } };
    @Override public void onCreate() { super.onCreate(); if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(stopCmd, new IntentFilter("com.manhmoc.edgebar.STOP_REC_CMD"), Context.RECEIVER_NOT_EXPORTED); else registerReceiver(stopCmd, new IntentFilter("com.manhmoc.edgebar.STOP_REC_CMD")); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRunning) { stopSelf(); return START_NOT_STICKY; }
        try {
            String cid = "rec_chan"; NotificationChannel c = new NotificationChannel(cid, "Ghi âm ẩn", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c);
            Notification n = new Notification.Builder(this, cid).setContentTitle("Đang ghi âm bí mật...").setSmallIcon(android.R.drawable.ic_menu_crop).build(); startForeground(3, n);
            // LƯU VÀO DOWNLOADS THEO YÊU CẦU
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EdgeBar_Records"); if(!dir.exists()) dir.mkdirs();
            path = new File(dir, "Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp3").getAbsolutePath();
            rec = new MediaRecorder(); rec.setAudioSource(MediaRecorder.AudioSource.MIC); rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); rec.setOutputFile(path); rec.prepare(); rec.start(); isRunning = true;
            Toast.makeText(this, "🔴 Đang ghi âm ngầm...\nLưu tại: Downloads/EdgeBar_Records", Toast.LENGTH_SHORT).show(); 
        } catch(Exception e) { Toast.makeText(this, "Lỗi Mic hoặc Quyền!", Toast.LENGTH_SHORT).show(); stopSelf(); } return START_STICKY;
    }
    @Override public void onDestroy() { super.onDestroy(); try{ unregisterReceiver(stopCmd); } catch(Exception e){} try { if(rec != null && isRunning) { rec.stop(); rec.release(); isRunning = false; Toast.makeText(this, "✅ Đã lưu: " + path, Toast.LENGTH_LONG).show(); } } catch(Exception e){} }
    @Override public IBinder onBind(Intent i) { return null; }
}
