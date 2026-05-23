package com.manhmoc.edgebar;
import android.app.Notification; import android.app.NotificationChannel; import android.app.NotificationManager; import android.app.Service; import android.content.Intent; import android.os.IBinder; import android.widget.Toast;
public class RecorderService extends Service {
    @Override public void onCreate() { super.onCreate();
        String cid = "eb_rec"; NotificationChannel c = new NotificationChannel(cid, "Edge Bar Ghi Âm", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c);
        Notification n = new Notification.Builder(this, cid).setContentTitle("Đang ghi âm ẩn...").setSmallIcon(android.R.drawable.ic_btn_speak_now).build(); startForeground(3, n);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { Toast.makeText(this, "Đã bật Trình ghi âm ẩn!", Toast.LENGTH_SHORT).show(); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { super.onDestroy(); Toast.makeText(this, "Đã tắt Trình ghi âm!", Toast.LENGTH_SHORT).show(); }
}
