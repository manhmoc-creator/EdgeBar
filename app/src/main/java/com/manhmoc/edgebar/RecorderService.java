package com.manhmoc.edgebar;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.BroadcastReceiver; import android.content.Context; import android.content.Intent; import android.content.IntentFilter; import android.media.MediaRecorder; import android.os.Environment; import android.os.IBinder; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder rec; private String path; private boolean isRecording = false;
    private BroadcastReceiver stopRc = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { if("com.manhmoc.edgebar.STOP_BREATH".equals(i.getAction())) stopSelf(); } };
    
    @Override public void onCreate() { super.onCreate(); 
        if(android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(stopRc, new IntentFilter("com.manhmoc.edgebar.STOP_BREATH"), Context.RECEIVER_NOT_EXPORTED); 
        else registerReceiver(stopRc, new IntentFilter("com.manhmoc.edgebar.STOP_BREATH")); 
    }
    
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) return START_STICKY;
        try {
            String cid = "rec_chan"; NotificationChannel c = new NotificationChannel(cid, "Ghi âm ẩn", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c);
            Notification n = new Notification.Builder(this, cid).setContentTitle("Đang ghi âm bí mật...").setSmallIcon(android.R.drawable.ic_menu_crop).build(); startForeground(3, n);

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar"); if(!dir.exists()) dir.mkdirs();
            path = new File(dir, "Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".m4a").getAbsolutePath();
            rec = new MediaRecorder(); rec.setAudioSource(MediaRecorder.AudioSource.MIC); rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); rec.setOutputFile(path); rec.prepare(); rec.start(); isRecording = true;
            Toast.makeText(this, "🔴 Đang ghi âm ngầm...\nLưu tại: Music/EdgeBar", Toast.LENGTH_LONG).show(); sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH"));
        } catch(Exception e) { Toast.makeText(this, "Lỗi Mic hoặc Quyền!", Toast.LENGTH_SHORT).show(); stopSelf(); } return START_STICKY;
    }
    @Override public void onDestroy() { super.onDestroy(); try { unregisterReceiver(stopRc); }catch(Exception e){} try { if(rec != null && isRecording) { rec.stop(); rec.release(); isRecording = false; Toast.makeText(this, "✅ Đã lưu: " + path, Toast.LENGTH_LONG).show(); sendBroadcast(new Intent("com.manhmoc.edgebar.STOP_BREATH")); } } catch(Exception e){} }
    @Override public IBinder onBind(Intent i) { return null; }
}
