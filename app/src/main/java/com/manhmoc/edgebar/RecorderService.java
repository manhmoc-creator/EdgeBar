package com.manhmoc.edgebar;
import android.app.Service; import android.content.Intent; import android.media.MediaRecorder; import android.os.Environment; import android.os.IBinder; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;

public class RecorderService extends Service {
    private MediaRecorder rec; private String path; private boolean isRecording = false;
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRecording) return START_STICKY;
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar");
            if(!dir.exists()) dir.mkdirs();
            path = new File(dir, "Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".mp3").getAbsolutePath();
            rec = new MediaRecorder(); rec.setAudioSource(MediaRecorder.AudioSource.MIC);
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            rec.setOutputFile(path); rec.prepare(); rec.start(); isRecording = true;
            Toast.makeText(this, "🔴 Đang ghi âm ngầm...\nLưu tại: Music/EdgeBar", Toast.LENGTH_LONG).show();
            sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH"));
        } catch(Exception e) { Toast.makeText(this, "Lỗi Mic hoặc Quyền!", Toast.LENGTH_SHORT).show(); stopSelf(); }
        return START_STICKY;
    }
    @Override public void onDestroy() { super.onDestroy(); try { if(rec != null && isRecording) { rec.stop(); rec.release(); isRecording = false; Toast.makeText(this, "✅ Đã lưu: " + path, Toast.LENGTH_LONG).show(); sendBroadcast(new Intent("com.manhmoc.edgebar.STOP_BREATH")); } } catch(Exception e){} }
    @Override public IBinder onBind(Intent i) { return null; }
}
