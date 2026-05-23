package com.manhmoc.edgebar;
import android.content.Context; import android.content.Intent; import android.media.MediaRecorder; import android.os.Environment; import android.widget.Toast; import java.io.File; import java.text.SimpleDateFormat; import java.util.Date;
public class MicHelper {
    private static MediaRecorder rec; public static boolean isRec = false; private static String path;
    public static void toggle(Context c) {
        if(isRec) {
            try { rec.stop(); rec.release(); isRec = false; Toast.makeText(c, "✅ Đã lưu: " + path, Toast.LENGTH_LONG).show(); c.sendBroadcast(new Intent("com.manhmoc.edgebar.STOP_BREATH")); } catch(Exception e){}
        } else {
            try {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "EdgeBar"); if(!dir.exists()) dir.mkdirs();
                path = new File(dir, "Record_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".m4a").getAbsolutePath();
                rec = new MediaRecorder(); rec.setAudioSource(MediaRecorder.AudioSource.MIC); rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); rec.setOutputFile(path); rec.prepare(); rec.start(); isRec = true;
                Toast.makeText(c, "🔴 Bắt đầu thu âm ngầm...", Toast.LENGTH_SHORT).show(); c.sendBroadcast(new Intent("com.manhmoc.edgebar.START_BREATH"));
            } catch(Exception e) { Toast.makeText(c, "Lỗi Mic hoặc Quyền!", Toast.LENGTH_SHORT).show(); }
        }
    }
}
