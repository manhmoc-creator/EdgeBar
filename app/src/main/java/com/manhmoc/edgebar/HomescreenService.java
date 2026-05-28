package com.manhmoc.edgebar;
import android.app.Service; import android.content.Intent; import android.os.IBinder;
public class HomescreenService extends Service {
    public static boolean isRunning = false;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { isRunning = true; return START_STICKY; }
    @Override public void onCreate() { isRunning = true; }
    @Override public void onDestroy() { isRunning = false; }
}
